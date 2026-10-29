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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * What a SkyBlock item looks like, by its name, for the search suggestions: built from Hypixel's
 * public list of SkyBlock items (material, head texture, glint, leather color), fetched once a
 * session while Network Features is on. The request names nothing about the player.
 *
 * The same list says which items are plain Minecraft ones, which the Auction House's suggestions
 * leave out (see {@link Catalog#vanilla}), what every item is called, and what upgrading one costs,
 * which the Item Value breakdown prices.
 */
public final class ItemIcons {
    private ItemIcons() {}

    private static final String ITEMS = "https://api.hypixel.net/v2/resources/skyblock/items";

    /**
     * How an item looks. The model, when there is one, is a model from Hypixel's resource pack,
     * {@code hypixel_skyblock:item/...}; the material is what the item is underneath, mostly paper,
     * and what it shows as without the pack.
     */
    record Info(String material, int damage, String skin, boolean glowing, String color, String model) {
        Info(String material, int damage, String skin, boolean glowing, String color) {
            this(material, damage, skin, glowing, color, null);
        }
    }

    /**
     * A built item: as it shows with Hypixel's resource pack, and without it. Which one is chosen
     * every frame, as the pack comes and goes with the server.
     */
    private record Icon(ItemStack plain, ItemStack modeled, Identifier model) {
        static final Icon NONE = new Icon(ItemStack.EMPTY, null, null);

        ItemStack get() {
            if (modeled == null) return plain;
            return Minecraft.getInstance().getModelManager().getItemModel(model) instanceof MissingItemModel ? plain : modeled;
        }
    }

    /** An amount of an item - essence is one too, ESSENCE_WITHER - or of coins, where the item is null. */
    public record Cost(String item, long amount) {
        public boolean coins() {
            return item == null;
        }
    }

    /** A gemstone slot, of a type such as SAPPHIRE or COMBAT, and what opening it costs; nothing for one that comes open. */
    public record GemSlot(String type, List<Cost> unlock) {}

    /**
     * What Hypixel's list says about upgrading an item: the cost of each star in order, of turning
     * it into a dungeon item first where that is needed, and of opening each gemstone slot; and the
     * enchantments it comes with.
     */
    public record Upgrades(List<List<Cost>> stars, List<Cost> conversion, List<GemSlot> gemSlots,
                           Map<String, Integer> enchantments) {}

    /**
     * Hypixel's list as far as it is used: how items look by name, every item id, the ids of plain
     * Minecraft items, every item's name by its id, the upgrades of those that have any, and the
     * rarity of every accessory outside the Rift.
     */
    record Catalog(Map<String, Info> byName, Map<String, Info> byId, Set<String> ids, Set<String> vanillaIds,
                   Map<String, String> names, Map<String, Upgrades> upgrades, Map<String, String> accessories) {
        /**
         * Whether the item with SkyCofl's tag is a plain Minecraft item - Diamond Sword, Oak Planks -
         * rather than one of SkyBlock's own. Those cannot be auctioned. A tag Hypixel does not list
         * is one when it has a damage value, "WOOL:0": the way plain items were told apart in 1.8.
         */
        boolean vanilla(String tag) {
            return vanillaIds.contains(tag) || (tag.indexOf(':') >= 0 && !ids.contains(tag));
        }
    }

    private static volatile Catalog catalog = null;
    private static volatile boolean fetching = false;
    private static volatile long retryAt = 0L;
    /** Built items by name; only touched on the render thread. */
    private static final Map<String, Icon> STACKS = new HashMap<>();
    /** Built items by SkyCofl's tag, final ones only; only touched on the render thread. */
    private static final Map<String, Icon> BY_TAG = new HashMap<>();
    /** Hypixel's look for a tag, shown while the repository's file is on its way. */
    private static final Map<String, Icon> INTERIM = new HashMap<>();

    /**
     * The item with SkyCofl's tag, which the suggestions know each name by. A tag that says plainly
     * what it is (an enchanted book, a potion, a 1.8 block such as WOOL:6) is drawn as that. Every
     * other item as the NotEnoughUpdates repository has it ({@link NeuItems}), as Skyblocker does:
     * it has pets, runes and shards, which Hypixel's list leaves out, and is right where Hypixel's
     * list is stale (High Class Archfiend Dice wears the plain dice's skin there). Until its file is
     * in, or if it has none, by the item's id in Hypixel's list; without a tag, or for one nobody
     * knows, by name. Going by the tag also finds the right one of items that share a name.
     */
    public static ItemStack forItem(String tag, String name) {
        if (!AlpakaConfig.instance.allowApiCalls) return ItemStack.EMPTY;
        if (tag == null || tag.isEmpty()) return forName(name);
        Icon cached = BY_TAG.get(tag);
        if (cached != null) return cached.get();
        Info plain = plain(tag, name);
        if (plain != null) return remember(tag, build(plain));
        Info neu = NeuItems.look(tag);
        if (neu != null) {
            INTERIM.remove(tag);
            return remember(tag, build(neu));
        }
        Catalog loaded = catalog();
        if (loaded == null) return ItemStack.EMPTY;
        Info hypixel = loaded.byId().get(tag);
        if (NeuItems.missing(tag)) {
            INTERIM.remove(tag);
            if (hypixel != null) return remember(tag, build(hypixel));
            return remember(tag, forNameIcon(name));
        }
        // Not known yet: asked again on the next frame.
        return hypixel == null ? ItemStack.EMPTY : INTERIM.computeIfAbsent(tag, t -> build(hypixel)).get();
    }

    private static ItemStack remember(String tag, Icon icon) {
        BY_TAG.put(tag, icon);
        return icon.get();
    }

    /**
     * How an item looks that its tag already says: an enchantment on the Bazaar is a book, a potion
     * a potion bottle, and a plain item's tag is its 1.8 material and damage value.
     */
    static Info plain(String tag, String name) {
        if (tag.startsWith("ENCHANTMENT_")) return new Info("enchanted_book", 0, null, false, null);
        if (tag.startsWith("POTION")) {
            boolean splash = name != null && name.toLowerCase(Locale.ROOT).contains("splash");
            return new Info(splash ? "splash_potion" : "potion", 0, null, false, null);
        }
        int colon = tag.indexOf(':');
        if (colon > 0) {
            try {
                return new Info(tag.substring(0, colon), Integer.parseInt(tag.substring(colon + 1)), null, false, null);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** The item for a name, or an empty stack while the list loads or if the name is not in it. */
    public static ItemStack forName(String name) {
        if (!AlpakaConfig.instance.allowApiCalls || name == null) return ItemStack.EMPTY;
        return forNameIcon(name).get();
    }

    private static Icon forNameIcon(String name) {
        Catalog loaded = catalog;
        if (loaded == null || name == null) {
            load();
            return Icon.NONE;
        }
        String key = name.toLowerCase(Locale.ROOT).trim();
        Icon cached = STACKS.get(key);
        if (cached != null) return cached;
        Info info = loaded.byName().get(key);
        Icon icon = info == null ? Icon.NONE : build(info);
        STACKS.put(key, icon);
        return icon;
    }

    /** Hypixel's list, or null while it loads or Network Features is off; asking starts loading it. */
    static Catalog catalog() {
        if (!AlpakaConfig.instance.allowApiCalls) return null;
        Catalog loaded = catalog;
        if (loaded == null) load();
        return loaded;
    }

    /** Whether Hypixel's list is on its way. */
    public static boolean loading() {
        return fetching;
    }

    /**
     * Every SkyBlock item by id with its name, plain Minecraft items left out, for the inventory
     * buttons' icon picker; null while the list loads or Network Features is off.
     */
    public static Map<String, String> skyblockItems() {
        Catalog loaded = catalog();
        if (loaded == null) return null;
        Map<String, String> out = new HashMap<>();
        for (String id : loaded.ids()) {
            if (!loaded.vanillaIds().contains(id)) out.put(id, loaded.names().getOrDefault(id, id));
        }
        return out;
    }

    /** Whether Hypixel's list is there to answer {@link #name} and {@link #upgrades}; asking starts loading it. */
    public static boolean ready() {
        return catalog() != null;
    }

    /** The item's name in Hypixel's list, such as "Wither Blood"; null while the list loads or for an id it does not know. */
    public static String name(String id) {
        Catalog loaded = catalog();
        return loaded == null || id == null ? null : loaded.names().get(id);
    }

    /** Every accessory outside the Rift with its rarity, by id; null while the list loads. */
    public static Map<String, String> accessories() {
        Catalog loaded = catalog();
        return loaded == null ? null : loaded.accessories();
    }

    /** What upgrading the item costs; null while the list loads, or for an item that has no upgrades. */
    public static Upgrades upgrades(String id) {
        Catalog loaded = catalog();
        return loaded == null || id == null ? null : loaded.upgrades().get(id);
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

    /** The item, and where it has a model from Hypixel's resource pack, the item wearing it too. */
    private static Icon build(Info info) {
        ItemStack plain = stack(info);
        Identifier model = info.model() == null ? null : Identifier.tryParse(info.model());
        if (model == null || plain.isEmpty()) return new Icon(plain, null, null);
        ItemStack modeled = plain.copy();
        modeled.set(DataComponents.ITEM_MODEL, model);
        return new Icon(plain, modeled, model);
    }

    private static ItemStack stack(Info info) {
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
        if (fetching || catalog != null || System.currentTimeMillis() < retryAt) return;
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
                            catalog = index(JsonParser.parseReader(reader));
                        }
                    } catch (IOException | RuntimeException e) {
                        AlpakaAddons.LOGGER.warn("Could not read Hypixel's item list", e);
                        retryAt = System.currentTimeMillis() + 120_000L;
                    } finally {
                        fetching = false;
                    }
                });
    }

    /**
     * What each item looks like, by its name in lower case, which are plain ones, what each is called
     * and what upgrading it costs, from Hypixel's answer.
     */
    static Catalog index(JsonElement root) {
        Map<String, Info> map = new HashMap<>();
        Map<String, Info> byId = new HashMap<>();
        Set<String> ids = new HashSet<>();
        Set<String> vanilla = new HashSet<>();
        Map<String, String> names = new HashMap<>();
        Map<String, Upgrades> upgrades = new HashMap<>();
        Map<String, String> accessories = new HashMap<>();
        if (!root.isJsonObject() || !root.getAsJsonObject().has("items")) return new Catalog(map, byId, ids, vanilla, names, upgrades, accessories);
        for (JsonElement element : root.getAsJsonObject().getAsJsonArray("items")) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            String id = string(o, "id");
            String name = string(o, "name").replaceAll("§.", "").trim();
            String material = string(o, "material");
            int damage = o.has("durability") ? o.get("durability").getAsInt() : 0;
            if (!id.isEmpty()) {
                ids.add(id);
                if (isVanilla(id, material, damage, o.has("museum") && o.get("museum").getAsBoolean())) vanilla.add(id);
                if (!name.isEmpty()) names.put(id, name);
                Upgrades up = upgrades(o);
                if (up != null) upgrades.put(id, up);
                // The Rift's accessories only count in the Rift, and none of the profile viewers list them.
                if ("ACCESSORY".equals(string(o, "category")) && !"RIFT".equals(string(o, "origin"))) {
                    String tier = string(o, "tier");
                    accessories.put(id, tier.isEmpty() ? "COMMON" : tier);
                }
            }
            if (name.isEmpty() || material.isEmpty()) continue;
            String skin = null;
            if (o.has("skin") && o.get("skin").isJsonObject()) {
                String value = string(o.getAsJsonObject("skin"), "value");
                if (!value.isEmpty()) skin = value;
            }
            boolean glowing = o.has("glowing") && o.get("glowing").getAsBoolean();
            String color = o.has("color") ? string(o, "color") : null;
            String model = o.has("item_model") ? string(o, "item_model") : null;
            Info info = new Info(material, damage, skin, glowing, color, model == null || model.isEmpty() ? null : model);
            map.putIfAbsent(name.toLowerCase(Locale.ROOT), info);
            if (!id.isEmpty()) byId.put(id, info);
        }
        return new Catalog(map, byId, ids, vanilla, names, upgrades, accessories);
    }

    /** An item's star, dungeon conversion and gemstone slot costs and its own enchantments; null without any. */
    static Upgrades upgrades(JsonObject o) {
        List<List<Cost>> stars = new ArrayList<>();
        if (o.has("upgrade_costs") && o.get("upgrade_costs").isJsonArray()) {
            for (JsonElement star : o.getAsJsonArray("upgrade_costs")) {
                stars.add(star.isJsonArray() ? costs(star.getAsJsonArray()) : List.of());
            }
        }
        List<Cost> conversion = List.of();
        if (o.has("dungeon_item_conversion_cost") && o.get("dungeon_item_conversion_cost").isJsonObject()) {
            // Written without a type: it is always essence.
            Cost essence = cost(o.getAsJsonObject("dungeon_item_conversion_cost"), "ESSENCE");
            if (essence != null) conversion = List.of(essence);
        }
        List<GemSlot> slots = new ArrayList<>();
        if (o.has("gemstone_slots") && o.get("gemstone_slots").isJsonArray()) {
            for (JsonElement slot : o.getAsJsonArray("gemstone_slots")) {
                if (!slot.isJsonObject()) continue;
                JsonObject s = slot.getAsJsonObject();
                String type = string(s, "slot_type");
                if (type.isEmpty()) continue;
                List<Cost> unlock = s.has("costs") && s.get("costs").isJsonArray() ? costs(s.getAsJsonArray("costs")) : List.of();
                slots.add(new GemSlot(type, unlock));
            }
        }
        Map<String, Integer> enchantments = new HashMap<>();
        if (o.has("enchantments") && o.get("enchantments").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("enchantments").entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                    enchantments.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsInt());
                }
            }
        }
        if (stars.isEmpty() && conversion.isEmpty() && slots.isEmpty() && enchantments.isEmpty()) return null;
        return new Upgrades(List.copyOf(stars), conversion, List.copyOf(slots), Map.copyOf(enchantments));
    }

    private static List<Cost> costs(com.google.gson.JsonArray array) {
        List<Cost> out = new ArrayList<>();
        for (JsonElement e : array) {
            if (!e.isJsonObject()) continue;
            Cost cost = cost(e.getAsJsonObject(), string(e.getAsJsonObject(), "type"));
            if (cost != null) out.add(cost);
        }
        return List.copyOf(out);
    }

    /** One cost as Hypixel writes it: essence by its type, an item by its id, or coins. */
    private static Cost cost(JsonObject o, String type) {
        try {
            return switch (type) {
                case "ESSENCE" -> string(o, "essence_type").isEmpty() ? null
                        : new Cost("ESSENCE_" + string(o, "essence_type"), o.get("amount").getAsLong());
                case "ITEM" -> string(o, "item_id").isEmpty() ? null : new Cost(string(o, "item_id"), o.get("amount").getAsLong());
                case "COINS" -> new Cost(null, o.get("coins").getAsLong());
                default -> null;
            };
        } catch (RuntimeException e) {
            // An amount missing or not a number: the cost is left out rather than the whole list.
            return null;
        }
    }

    /**
     * Whether Hypixel lists a plain Minecraft item: one named after its material, DIAMOND_SWORD or
     * WOOL:6, where SkyBlock's own items have names of their own (HYPERION is an iron sword).
     * Museum pieces such as the Nether Star are traded all the same, and every enchanted book on
     * the Auction House is an ENCHANTED_BOOK.
     */
    private static boolean isVanilla(String id, String material, int damage, boolean museum) {
        if (museum || id.equals("ENCHANTED_BOOK")) return false;
        return id.equals(material) || id.equals(material + ":" + damage);
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? "" : e.getAsString();
    }
}
