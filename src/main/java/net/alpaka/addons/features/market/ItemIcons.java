package net.alpaka.addons.features.market;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * What a SkyBlock item looks like, by its name, for the search suggestions: built from Hypixel's
 * public list of SkyBlock items (material, head texture, glint, leather color), fetched once a
 * session while Network Features is on. The request names nothing about the player.
 */
public final class ItemIcons {
    private ItemIcons() {}

    private static final String ITEMS = "https://api.hypixel.net/v2/resources/skyblock/items";

    record Info(String material, int damage, String skin, boolean glowing, String color) {}

    private static volatile Map<String, Info> byName = null;
    private static volatile boolean fetching = false;
    private static volatile long retryAt = 0L;
    /** Built stacks by name; only touched on the render thread. */
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    /** The item for a name, or an empty stack while the list loads or if the name is not in it. */
    public static ItemStack forName(String name) {
        if (!AlpakaConfig.instance.allowApiCalls || name == null) return ItemStack.EMPTY;
        Map<String, Info> map = byName;
        if (map == null) {
            load();
            return ItemStack.EMPTY;
        }
        String key = name.toLowerCase(Locale.ROOT).trim();
        ItemStack cached = STACKS.get(key);
        if (cached != null) return cached;
        Info info = map.get(key);
        ItemStack stack = info == null ? ItemStack.EMPTY : build(info);
        STACKS.put(key, stack);
        return stack;
    }

    /** A player head wearing the skin with this texture value, as Hypixel's heads carry it. */
    public static ItemStack head(String texture) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, profile(texture));
        return stack;
    }

    /** A profile wearing the skin with this texture value, for a head or a face. */
    public static ResolvableProfile profile(String texture) {
        UUID id = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
        PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
        return ResolvableProfile.createResolved(new GameProfile(id, "alpaka", properties));
    }

    private static ItemStack build(Info info) {
        Identifier id = Identifier.tryParse("minecraft:" + LegacyItems.modernId(info.material(), info.damage()));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        ItemStack stack = item == Items.PLAYER_HEAD && info.skin() != null ? head(info.skin()) : new ItemStack(item);
        if (info.glowing()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (info.color() != null && info.material().toLowerCase(Locale.ROOT).startsWith("leather_")) {
            String[] rgb = info.color().split(",");
            if (rgb.length == 3) {
                try {
                    int color = Integer.parseInt(rgb[0].trim()) << 16 | Integer.parseInt(rgb[1].trim()) << 8 | Integer.parseInt(rgb[2].trim());
                    stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
                } catch (NumberFormatException ignored) {
                    // Left undyed.
                }
            }
        }
        return stack;
    }

    private static synchronized void load() {
        if (fetching || byName != null || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        HttpRequest request = HttpRequest.newBuilder(URI.create(ITEMS))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                // About 4.8 MB as text, under 2 MB packed.
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || response.statusCode() != 200) {
                            AlpakaAddons.LOGGER.warn("Could not load Hypixel's item list ({})",
                                    error != null ? error.toString() : "HTTP " + (response == null ? "?" : response.statusCode()));
                            retryAt = System.currentTimeMillis() + 120_000L;
                            return;
                        }
                        boolean gzip = response.headers().firstValue("Content-Encoding").map(e -> e.contains("gzip")).orElse(false);
                        try (InputStream raw = response.body();
                             InputStream body = gzip ? new GZIPInputStream(raw) : raw;
                             InputStreamReader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                            byName = index(JsonParser.parseReader(reader));
                        }
                    } catch (IOException | RuntimeException e) {
                        AlpakaAddons.LOGGER.warn("Could not read Hypixel's item list", e);
                        retryAt = System.currentTimeMillis() + 120_000L;
                    } finally {
                        fetching = false;
                    }
                });
    }

    /** What each item looks like, by its name in lower case, from Hypixel's answer. */
    static Map<String, Info> index(JsonElement root) {
        Map<String, Info> map = new HashMap<>();
        if (!root.isJsonObject() || !root.getAsJsonObject().has("items")) return map;
        for (JsonElement element : root.getAsJsonObject().getAsJsonArray("items")) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            String name = string(o, "name").replaceAll("§.", "").trim();
            String material = string(o, "material");
            if (name.isEmpty() || material.isEmpty()) continue;
            int damage = o.has("durability") ? o.get("durability").getAsInt() : 0;
            String skin = null;
            if (o.has("skin") && o.get("skin").isJsonObject()) {
                String value = string(o.getAsJsonObject("skin"), "value");
                if (!value.isEmpty()) skin = value;
            }
            boolean glowing = o.has("glowing") && o.get("glowing").getAsBoolean();
            String color = o.has("color") ? string(o, "color") : null;
            map.putIfAbsent(name.toLowerCase(Locale.ROOT), new Info(material, damage, skin, glowing, color));
        }
        return map;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? "" : e.getAsString();
    }
}
