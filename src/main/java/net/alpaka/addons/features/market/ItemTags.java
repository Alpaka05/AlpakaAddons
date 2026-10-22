package net.alpaka.addons.features.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.utils.ModVersion;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The tag SkyCofl knows an item by - Hypixel's own item id, such as {@code ENCHANTED_HAY_BALE}.
 *
 * Most items carry it in their data. A menu's display item, the Bazaar's product above all, often
 * does not; then the name is looked up in SkyCofl's list of every item, fetched once a session while
 * Network Features is on.
 */
public final class ItemTags {
    private ItemTags() {}

    private static final String ITEMS = "https://sky.coflnet.com/api/items";

    /** Whether a lookup by name can answer yet: the list is loaded, or there is none to wait for. */
    public enum Lookup { FOUND, LOADING, UNKNOWN }

    public record Tag(Lookup lookup, String tag) {}

    private static volatile Map<String, String> bazaarByName = null;
    private static volatile Map<String, String> auctionByName = null;
    private static volatile boolean fetching = false;
    private static volatile long retryAt = 0L;

    /** The item's tag, from its data if it has one, else from its name. */
    public static Tag of(ItemStack stack, String plainName, MarketKind kind) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            net.minecraft.nbt.CompoundTag nbt = data.copyTag();
            String id = nbt.getStringOr("id", "");
            if (!id.isEmpty()) {
                String tag = expand(id, nbt.getStringOr("petInfo", ""));
                if (tag != null) return new Tag(Lookup.FOUND, tag);
            }
        }
        return byName(plainName, kind);
    }

    /**
     * SkyCofl's tag for an item id. Pets are told apart by their kind ({@code PET_LION}); runes and
     * potions too, which their data does not say plainly enough, so those fall back to the name.
     */
    static String expand(String id, String petInfo) {
        if (id.equals("PET")) return petTag(petInfo);
        if (id.equals("RUNE") || id.equals("POTION")) return null;
        return id;
    }

    /** "PET_LION" from a pet's info, {@code {"type":"LION",...}}. */
    static String petTag(String petInfo) {
        if (petInfo == null || petInfo.isEmpty()) return null;
        try {
            JsonElement root = JsonParser.parseString(petInfo);
            if (!root.isJsonObject()) return null;
            JsonElement type = root.getAsJsonObject().get("type");
            return type == null ? null : "PET_" + type.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Tag byName(String plainName, MarketKind kind) {
        String name = plainName == null ? "" : plainName.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) return new Tag(Lookup.UNKNOWN, null);
        Map<String, String> preferred = kind == MarketKind.BAZAAR ? bazaarByName : auctionByName;
        Map<String, String> other = kind == MarketKind.BAZAAR ? auctionByName : bazaarByName;
        if (preferred == null) {
            load();
            return new Tag(fetching ? Lookup.LOADING : Lookup.UNKNOWN, null);
        }
        String tag = preferred.get(name);
        if (tag == null && other != null) tag = other.get(name);
        return tag == null ? new Tag(Lookup.UNKNOWN, null) : new Tag(Lookup.FOUND, tag);
    }

    private static synchronized void load() {
        if (fetching || bazaarByName != null || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        HttpRequest request = HttpRequest.newBuilder(URI.create(ITEMS))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || response.statusCode() != 200) {
                            AlpakaAddons.LOGGER.warn("Could not load SkyCofl's item list ({})",
                                    error != null ? error.toString() : "HTTP " + (response == null ? "?" : response.statusCode()));
                            retryAt = System.currentTimeMillis() + 120_000L;
                            return;
                        }
                        index(response.body());
                    } catch (RuntimeException e) {
                        AlpakaAddons.LOGGER.warn("Could not read SkyCofl's item list", e);
                        retryAt = System.currentTimeMillis() + 120_000L;
                    } finally {
                        fetching = false;
                    }
                });
    }

    /** Builds the name lookups from SkyCofl's list: one for Bazaar products, one for the rest. */
    static void index(String json) {
        Map<String, String> bazaar = new HashMap<>();
        Map<String, String> auction = new HashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject o = element.getAsJsonObject();
                String name = string(o, "name");
                String tag = string(o, "tag");
                if (name.isEmpty() || tag.isEmpty() || name.equals("null")) continue;
                String key = name.toLowerCase(Locale.ROOT);
                if (string(o, "flags").contains("BAZAAR")) bazaar.putIfAbsent(key, tag);
                else auction.putIfAbsent(key, tag);
            }
        }
        auctionByName = auction;
        bazaarByName = bazaar;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** The tag a name stands for after {@link #index}, for the tests. */
    static String lookup(String name, MarketKind kind) {
        Tag tag = byName(name, kind);
        return tag.tag();
    }
}
