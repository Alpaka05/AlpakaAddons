package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.features.market.ItemIcons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every SkyBlock item there is, for the search window's "All Items" part: Hypixel's public item list
 * (which {@link ItemIcons} already fetches once a session), plain Minecraft items left out.
 */
public final class ItemCatalog {
    private ItemCatalog() {}

    /** One item of the game: its id, its name and its rarity, such as "LEGENDARY" (null when it has none). */
    public record Item(String id, String name, String tier) {}

    private static List<Item> items = null;
    private static Map<String, String> builtFrom = null;

    /** Every item, or null while Hypixel's list loads or Network Features is off. */
    public static List<Item> all() {
        Map<String, String> names = ItemIcons.skyblockItems();
        if (names == null) return null;
        if (items == null || builtFrom == null || builtFrom.size() != names.size()) {
            List<Item> list = new ArrayList<>(names.size());
            for (Map.Entry<String, String> e : names.entrySet()) {
                list.add(new Item(e.getKey(), e.getValue(), ItemIcons.tier(e.getKey())));
            }
            items = list;
            builtFrom = names;
        }
        return items;
    }

    /** The item with this id, or one named like it; null when there is none. */
    public static Item find(String idOrName) {
        List<Item> all = all();
        if (all == null || idOrName == null) return null;
        String q = idOrName.trim();
        for (Item item : all) if (item.id.equalsIgnoreCase(q.replace(' ', '_'))) return item;
        for (Item item : all) if (item.name.equalsIgnoreCase(q)) return item;
        List<Item> hits = search(all, q, 1);
        return hits.isEmpty() ? null : hits.get(0);
    }

    /**
     * The items matching the query, best first: the name exactly, then names that start with it,
     * then names with a word starting with it, then the rest; shorter names first within each.
     * Every word of the query has to be in the name.
     */
    public static List<Item> search(List<Item> all, String query, int limit) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || all == null) return List.of();
        String[] words = q.split("\\s+");
        List<Scored> hits = new ArrayList<>();
        for (Item item : all) {
            String name = item.name.toLowerCase(Locale.ROOT);
            boolean every = true;
            for (String word : words) {
                if (!name.contains(word)) {
                    every = false;
                    break;
                }
            }
            if (!every) continue;
            int rank;
            if (name.equals(q)) rank = 0;
            else if (name.startsWith(q)) rank = 1;
            else if (name.contains(" " + q)) rank = 2;
            else rank = 3;
            hits.add(new Scored(item, rank));
        }
        hits.sort(Comparator.comparingInt(Scored::rank)
                .thenComparingInt(s -> s.item.name.length())
                .thenComparing(s -> s.item.name));
        List<Item> out = new ArrayList<>(Math.min(limit, hits.size()));
        for (int i = 0; i < hits.size() && i < limit; i++) out.add(hits.get(i).item);
        return out;
    }

    private record Scored(Item item, int rank) {}

    /** A rarity's color, as Hypixel colors the names. */
    public static int tierColor(String tier) {
        if (tier == null) return 0xFFFFFFFF;
        return switch (tier) {
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            case "DIVINE" -> 0xFF55FFFF;
            case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
            case "ULTIMATE", "ADMIN" -> 0xFFAA0000;
            default -> 0xFFFFFFFF;
        };
    }

    /** A rarity as words: "VERY_SPECIAL" as "Very Special". */
    public static String tierLabel(String tier) {
        return tier == null ? "" : ItemRecipes.prettyId(tier);
    }
}
