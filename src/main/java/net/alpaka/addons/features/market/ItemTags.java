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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    /** Display names for the search suggestions: Bazaar products, and what is sold on the Auction House. */
    private static volatile List<String> bazaarNames = null;
    private static volatile List<String> auctionNames = null;
    /** SkyCofl's tags for each Auction House name; some names are shared, "Cactus" being a block and a head. */
    private static volatile Map<String, List<String>> auctionTags = null;
    /** The Auction House's names without plain Minecraft items, and what they were worked out from. Render thread only. */
    private static List<String> auctionSuggestions = null;
    private static List<String> allSuggestions = null;
    private static Object suggestionsFrom = null;
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

    /**
     * The names to suggest while searching this market, or every market's for none. Null while
     * SkyCofl's list is not loaded yet; asking starts loading it.
     *
     * The Auction House's leave out plain Minecraft items, which cannot be auctioned, so a
     * suggestion never leads to an empty page. Which those are, Hypixel's item list says; while it
     * loads, null too, and should it not come, every name is offered. The Bazaar's are its products,
     * plain Wheat and Coal among them, which it does sell.
     */
    public static List<String> names(MarketKind kind) {
        List<String> bazaar = bazaarNames;
        if (bazaar == null) {
            load();
            return null;
        }
        if (kind == MarketKind.BAZAAR) return bazaar;
        ItemIcons.Catalog catalog = ItemIcons.catalog();
        if (catalog == null) {
            if (ItemIcons.loading()) return null;
            return kind == MarketKind.AUCTION_HOUSE ? auctionNames : joined(auctionNames, bazaar);
        }
        if (suggestionsFrom != catalog) {
            auctionSuggestions = withoutVanilla(auctionTags, catalog::vanilla);
            allSuggestions = joined(auctionSuggestions, bazaar);
            suggestionsFrom = catalog;
        }
        return kind == MarketKind.AUCTION_HOUSE ? auctionSuggestions : allSuggestions;
    }

    /**
     * The tag to show a suggested name's item by: this market's first, and of a name the Auction
     * House shares between items, the first that is not a plain Minecraft item, as that is what the
     * suggestion leads to. Null while SkyCofl's list loads, or for a name it does not know, such as
     * a recent search for a part of a name.
     */
    public static String suggestionTag(String name, MarketKind kind) {
        Map<String, String> bazaar = bazaarByName;
        Map<String, String> auction = auctionByName;
        Map<String, List<String>> tags = auctionTags;
        if (name == null || bazaar == null || auction == null || tags == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (kind == MarketKind.BAZAAR && bazaar.containsKey(key)) return bazaar.get(key);
        List<String> shared = tags.get(name);
        if (shared != null && !shared.isEmpty()) {
            ItemIcons.Catalog catalog = ItemIcons.catalog();
            for (String tag : shared) {
                if (catalog == null || !catalog.vanilla(tag)) return tag;
            }
            return shared.get(0);
        }
        String tag = auction.get(key);
        return tag != null ? tag : bazaar.get(key);
    }

    /** The names with at least one tag that is not a plain Minecraft item's. */
    static List<String> withoutVanilla(Map<String, List<String>> tagsByName, java.util.function.Predicate<String> vanilla) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : tagsByName.entrySet()) {
            for (String tag : e.getValue()) {
                if (!vanilla.test(tag)) {
                    out.add(e.getKey());
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    private static List<String> joined(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    /** Whether a list the suggestions need is on its way: SkyCofl's names, or Hypixel's word on which are plain items. */
    public static boolean loading() {
        return fetching || ItemIcons.loading();
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
        Set<String> bazaarList = new LinkedHashSet<>();
        Map<String, List<String>> auctionList = new java.util.LinkedHashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject o = element.getAsJsonObject();
                // A few names carry formatting codes, which neither a search nor a lookup wants.
                String name = string(o, "name").replaceAll("§.", "").trim();
                String tag = string(o, "tag");
                if (name.isEmpty() || tag.isEmpty() || name.equals("null")) continue;
                String key = name.toLowerCase(Locale.ROOT);
                String flags = string(o, "flags");
                if (flags.contains("BAZAAR")) {
                    bazaar.putIfAbsent(key, tag);
                    bazaarList.add(name);
                } else {
                    auction.putIfAbsent(key, tag);
                    // Only what is actually sold there: items that never are would crowd the suggestions.
                    if (flags.contains("AUCTION") || flags.contains("MUSEUM")) {
                        auctionList.computeIfAbsent(name, n -> new ArrayList<>()).add(tag);
                    }
                }
            }
        }
        auctionByName = auction;
        auctionTags = auctionList;
        auctionNames = List.copyOf(auctionList.keySet());
        // Last: it is what tells the lists are there.
        bazaarByName = bazaar;
        bazaarNames = List.copyOf(bazaarList);
    }

    /** The Auction House's names with their tags after {@link #index}, for the tests. */
    static Map<String, List<String>> auctionTags() {
        return auctionTags;
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
