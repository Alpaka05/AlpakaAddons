package net.alpaka.addons.features.itemsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What Hypixel's menus look like, as text: which titles are a container Item Search remembers, and
 * how a sack says how much it holds. Kept apart from the menus themselves so it can be tested
 * without a game.
 */
public final class ItemSearchMenus {
    private ItemSearchMenus() {}

    /** A menu Item Search reads: the kind, the page or slot number, and a name of its own where it has one. */
    public record Menu(ItemSearchSource source, int page, String label) {}

    private static final Pattern ENDER_CHEST = Pattern.compile("^Ender Chest \\((\\d+)/\\d+\\)$");
    /** "Jumbo Backpack (Slot #12)", and "Jumbo Backpack ✦ (Slot #1)" for an upgraded one. */
    private static final Pattern BACKPACK = Pattern.compile("^.*Backpack.* \\(Slot #(\\d+)\\)$");
    /** The wardrobe, "(1/3) Armor Sets" since Hypixel renamed it, "Wardrobe (1/2)" before. */
    private static final Pattern WARDROBE = Pattern.compile("^(?:\\((\\d+)/\\d+\\) Armor Sets|Wardrobe \\((\\d+)/\\d+\\))$");
    private static final Pattern ACCESSORY_BAG = Pattern.compile("^Accessory Bag(?: \\((\\d+)/\\d+\\))?$");
    /** "Mining Sack", "Enchanted Agronomy Sack", "Gemstones Sack", with a page where it has more than one. */
    private static final Pattern SACK = Pattern.compile("^((?:.+ )?Sack)(?: \\((\\d+)/\\d+\\))?$");

    /** The menu a title names, or null for one Item Search does not read. Island chests are told by their block, not here. */
    public static Menu parseTitle(String title) {
        if (title == null) return null;
        String t = title.trim();
        Matcher m;
        if ((m = ENDER_CHEST.matcher(t)).matches()) return new Menu(ItemSearchSource.ENDER_CHEST, Integer.parseInt(m.group(1)), null);
        if ((m = BACKPACK.matcher(t)).matches()) return new Menu(ItemSearchSource.BACKPACK, Integer.parseInt(m.group(1)), null);
        if ((m = WARDROBE.matcher(t)).matches()) {
            return new Menu(ItemSearchSource.WARDROBE, Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2)), null);
        }
        if ((m = ACCESSORY_BAG.matcher(t)).matches()) {
            return new Menu(ItemSearchSource.ACCESSORY_BAG, m.group(1) == null ? 1 : Integer.parseInt(m.group(1)), null);
        }
        if (t.equals("Personal Vault")) return new Menu(ItemSearchSource.VAULT, 1, null);
        if ((m = SACK.matcher(t)).matches()) {
            String name = m.group(1);
            // The Sack of Sacks lists the sacks themselves, not what is in them.
            if (name.equals("Sack of Sacks") || name.equals("Sack")) return null;
            return new Menu(ItemSearchSource.SACKS, m.group(2) == null ? 1 : Integer.parseInt(m.group(2)), name);
        }
        return null;
    }

    private static final Pattern STORED = Pattern.compile("^Stored: ([\\d,.]+[kKmMbB]?)/.*$");
    /** A gemstone sack's line per quality: " Rough: 78,999 (78,999)". */
    private static final Pattern GEM_QUALITY = Pattern.compile("^\\s*(Rough|Flawed|Fine|Flawless|Perfect): ([\\d,.]+[kKmMbB]?)(?: \\(.*\\))?$");
    /** A rune sack's line per level: "I: 5/64". */
    private static final Pattern RUNE_LEVEL = Pattern.compile("^\\s*(I{1,3}): ([\\d,.]+[kKmMbB]?)/.*$");

    /**
     * What one sack slot holds, from its lore with the colors taken off. Most say one amount
     * ("Stored: 1,204/60.5k"); gemstones say one per quality and runes one per level, which come out
     * as their own lines here, named by what tells them apart ("Rough", "II"). Empty for a slot that
     * is not an item kept in the sack, and amounts of zero are left out.
     */
    public static List<Stored> parseSackLore(List<String> lore) {
        List<Stored> out = new ArrayList<>();
        for (String raw : lore) {
            String line = raw.trim();
            Matcher m;
            if ((m = STORED.matcher(line)).matches()) {
                long amount = parseAmount(m.group(1));
                if (amount > 0) out.add(new Stored(null, amount));
            } else if ((m = GEM_QUALITY.matcher(raw)).matches()) {
                long amount = parseAmount(m.group(2));
                if (amount > 0) out.add(new Stored(m.group(1), amount));
            } else if ((m = RUNE_LEVEL.matcher(raw)).matches()) {
                long amount = parseAmount(m.group(2));
                if (amount > 0) out.add(new Stored(m.group(1), amount));
            }
        }
        return out;
    }

    /** One amount in a sack: how much, and the quality or level it is of, or null for a plain item. */
    public record Stored(String variant, long amount) {}

    /**
     * The name a gemstone or rune line of a sack is shown under: "Ruby Gemstones" with "Rough" is
     * "Rough Ruby Gemstone", a rune with "II" gets " II" after it.
     */
    public static String variantName(String itemName, String variant) {
        if (variant == null) return itemName;
        if (variant.matches("I{1,3}")) return itemName + " " + variant;
        String gem = itemName.replaceFirst("^(?:Rough|Flawed|Fine|Flawless|Perfect) ", "").replaceFirst(" Gemstones?$", "");
        return variant + " " + gem + " Gemstone";
    }

    /** "1,204" is 1204, "60.5k" is 60500; anything unreadable is 0. */
    public static long parseAmount(String text) {
        if (text == null || text.isEmpty()) return 0;
        String t = text.replace(",", "").toLowerCase(Locale.ROOT);
        double factor = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k') factor = 1e3;
        else if (last == 'm') factor = 1e6;
        else if (last == 'b') factor = 1e9;
        if (factor != 1) t = t.substring(0, t.length() - 1);
        try {
            return Math.round(Double.parseDouble(t) * factor);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** One line of the "[Sacks]" message's hover text: "+12 Enchanted Coal (Mining Sack)". */
    public record SackChange(long delta, String item, String sack) {}

    private static final Pattern SACK_CHANGE = Pattern.compile("^\\s*([+-][\\d,]+) (.+?) \\((.+)\\)\\s*$");

    /** The change a hover line names, or null for a line that is not one (the totals, the hint at the end). */
    public static SackChange parseSackChange(String line) {
        Matcher m = SACK_CHANGE.matcher(line);
        if (!m.matches()) return null;
        long delta;
        try {
            delta = Long.parseLong(m.group(1).replace(",", "").replace("+", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        // "(Mining Sack, Combat Sack)" when it went into more than one; the first is where to look.
        String sack = m.group(3).split(",")[0].trim();
        return new SackChange(delta, m.group(2).trim(), sack);
    }

    /**
     * Whether an item matches what was typed: every word of the query has to be in its name or its
     * lore, in any order and case. An empty query matches everything.
     */
    public static boolean matches(String query, String name, List<String> lore) {
        if (query == null || query.isBlank()) return true;
        String n = name.toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (n.contains(word)) continue;
            boolean found = false;
            for (String line : lore) {
                if (line.toLowerCase(Locale.ROOT).contains(word)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** A count as the grid shows it in a corner: 64, 1.2k, 35m. */
    public static String shortCount(long count) {
        if (count < 1000) return Long.toString(count);
        if (count < 10_000) return trim(String.format(Locale.ROOT, "%.1f", count / 1000.0)) + "k";
        if (count < 1_000_000) return (count / 1000) + "k";
        if (count < 10_000_000) return trim(String.format(Locale.ROOT, "%.1f", count / 1e6)) + "m";
        if (count < 1_000_000_000) return (count / 1_000_000) + "m";
        return trim(String.format(Locale.ROOT, "%.1f", count / 1e9)) + "b";
    }

    private static String trim(String number) {
        return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
    }
}
