package net.alpaka.addons.features.itemvalue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.MarketKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * What an item is worth, part by part: the item itself at its lowest BIN or Bazaar price, and every
 * thing put on it since - enchantments, stars, gemstones, books, scrolls, the reforge stone - at what
 * each costs now. The parts add up to the item's value; nothing is discounted.
 *
 * Works from the item's SkyBlock data, the attributes Hypixel keeps on it (in 1.21 the custom data
 * component), handed over as JSON, so the arithmetic can be checked without the game.
 */
public final class ItemValue {
    private ItemValue() {}

    /** What the parts are grouped by, in the order they are listed when two are worth the same. */
    public enum Group {
        BASE("Base Item"),
        STARS("Stars"),
        ENCHANTMENTS("Enchantments"),
        GEMSTONES("Gemstones"),
        UPGRADES("Upgrades"),
        REFORGE("Reforge"),
        COSMETICS("Cosmetics");

        public final String title;

        Group(String title) {
            this.title = title;
        }
    }

    /**
     * One part: what it is, how many, and what one costs now on which market - NaN and null when
     * there is no price for it. {@code id} is the item behind it, whose price history can be shown;
     * null for a part made of several things, such as opening gemstone slots. {@code note} says how a
     * price was worked out where that is not simply the market's.
     */
    public record Part(Group group, String label, String id, double count, double unit, MarketKind market,
                       boolean estimated, String note) {
        public double total() {
            return unit * count;
        }

        public boolean priced() {
            return !Double.isNaN(unit);
        }
    }

    /** An item's parts, the item itself first, and what they come to. */
    public record Breakdown(List<Part> parts) {
        public double total() {
            double sum = 0;
            for (Part part : parts) if (part.priced()) sum += part.total();
            return sum;
        }

        public double total(Group group) {
            double sum = 0;
            for (Part part : parts) if (part.group() == group && part.priced()) sum += part.total();
            return sum;
        }

        /**
         * Whether some part has no price, so the item is worth more than the total. Enchantments do
         * not count: one without a book on sale comes from the enchanting table, or is no longer
         * sold at all, and adds next to nothing either way.
         */
        public boolean incomplete() {
            for (Part part : parts) if (!part.priced() && part.group() != Group.ENCHANTMENTS) return true;
            return false;
        }
    }

    /** Where prices come from: {@link ItemPrices} in the game, a table in the tests. */
    public interface Prices {
        ItemPrices.Quote quote(String id);
    }

    /** What Hypixel's item list says about an item: {@link ItemIcons} in the game. */
    public interface Catalog {
        String name(String id);

        ItemIcons.Upgrades upgrades(String id);
    }

    // ---- What the data means --------------------------------------------------------------------

    /** Reforges that need a stone, by name in lower case without spaces or marks; the stone is used up. */
    private static final Map<String, String> REFORGE_STONES = Map.ofEntries(
            Map.entry("ambered", "AMBER_MATERIAL"),
            Map.entry("ancient", "PRECURSOR_GEAR"),
            Map.entry("auspicious", "ROCK_GEMSTONE"),
            Map.entry("beady", "BEADY_EYES"),
            Map.entry("blazing", "BLAZEN_SPHERE"),
            Map.entry("blessed", "BLESSED_FRUIT"),
            Map.entry("bloodshot", "SHRIVELED_CORNEA"),
            Map.entry("bloodsoaked", "PRESUMED_GALLON_OF_RED_PAINT"),
            Map.entry("blooming", "FLOWERING_BOUQUET"),
            Map.entry("bountiful", "GOLDEN_BALL"),
            Map.entry("bulky", "BULKY_STONE"),
            Map.entry("bustling", "SKYMART_BROCHURE"),
            Map.entry("buzzing", "CLIPPED_WINGS"),
            Map.entry("calcified", "CALCIFIED_HEART"),
            Map.entry("candied", "CANDY_CORN"),
            Map.entry("chomp", "KUUDRA_MANDIBLE"),
            Map.entry("coldfused", "ENTROPY_SUPPRESSOR"),
            Map.entry("cubic", "MOLTEN_CUBE"),
            Map.entry("deepfried", "HASHBROWN"),
            Map.entry("dimensional", "TITANIUM_TESSERACT"),
            Map.entry("dirty", "DIRT_BOTTLE"),
            Map.entry("earthy", "LARGE_WALNUT"),
            Map.entry("empowered", "SADAN_BROOCH"),
            Map.entry("erudite", "DAEDALUS_NOTES"),
            Map.entry("fabled", "DRAGON_CLAW"),
            Map.entry("fanged", "FULL_JAW_FANGING_KIT"),
            Map.entry("festive", "FROZEN_BAUBLE"),
            Map.entry("fleet", "DIAMONITE"),
            Map.entry("fortified", "METEOR_SHARD"),
            Map.entry("fruitful", "ONYX"),
            Map.entry("geometric", "GEOMETRIC_ODDITY"),
            Map.entry("giant", "GIANT_TOOTH"),
            Map.entry("gilded", "MIDAS_JEWEL"),
            Map.entry("glacial", "FRIGID_HUSK"),
            Map.entry("glistening", "SHINY_PRISM"),
            Map.entry("greaterspook", "BOO_STONE"),
            Map.entry("groovy", "MANGROVE_GEM"),
            Map.entry("headstrong", "SALMON_OPAL"),
            Map.entry("heated", "HOT_STUFF"),
            Map.entry("hyper", "ENDSTONE_GEODE"),
            Map.entry("jaded", "JADERALD"),
            Map.entry("jerrys", "JERRY_STONE"),
            Map.entry("loving", "RED_SCARF"),
            Map.entry("lucky", "LUCKY_DICE"),
            Map.entry("lunar", "MOONSTONE"),
            Map.entry("lustrous", "GLEAMING_CRYSTAL"),
            Map.entry("magnetic", "LAPIS_CRYSTAL"),
            Map.entry("majestic", "MORNING_DEW"),
            Map.entry("mantid", "MANTID_CLAW"),
            Map.entry("marshy", "MARSHROOM"),
            Map.entry("mithraic", "PURE_MITHRIL"),
            Map.entry("moil", "MOIL_LOG"),
            Map.entry("moonglade", "MOONGLADE_JEWEL"),
            Map.entry("mossy", "OVERGROWN_GRASS"),
            Map.entry("necrotic", "NECROMANCER_BROOCH"),
            Map.entry("overpriced", "OVERPRICED_DRINK"),
            Map.entry("perfect", "DIAMOND_ATOM"),
            Map.entry("pitchin", "PITCHIN_KOI"),
            Map.entry("precise", "OPTICAL_LENS"),
            Map.entry("refined", "REFINED_AMBER"),
            Map.entry("reinforced", "RARE_DIAMOND"),
            Map.entry("renowned", "DRAGON_HORN"),
            Map.entry("ridiculous", "RED_NOSE"),
            Map.entry("rooted", "BURROWING_SPORES"),
            Map.entry("royal", "DWARVEN_TREASURE"),
            Map.entry("salty", "SALT_CUBE"),
            Map.entry("scraped", "POCKET_ICEBERG"),
            Map.entry("snowy", "TERRY_SNOWGLOBE"),
            Map.entry("spiked", "DRAGON_SCALE"),
            Map.entry("spiritual", "SPIRIT_DECOY"),
            Map.entry("squeaky", "SQUEAKY_TOY"),
            Map.entry("stellar", "PETRIFIED_STARFALL"),
            Map.entry("sticky", "EXTREMELY_MILD_ADHESIVE"),
            Map.entry("stiff", "HARDENED_WOOD"),
            Map.entry("strengthened", "SEARING_STONE"),
            Map.entry("submerged", "DEEP_SEA_ORB"),
            Map.entry("sunny", "SUNSTONE"),
            Map.entry("suspicious", "SUSPICIOUS_VIAL"),
            Map.entry("thorny", "BLOOMING_THORNS"),
            Map.entry("toil", "TOIL_LOG"),
            Map.entry("trashy", "OVERFLOWING_TRASH_CAN"),
            Map.entry("treacherous", "RUSTY_ANCHOR"),
            Map.entry("undead", "PREMIUM_FLESH"),
            Map.entry("warped", "AOTE_STONE"),
            Map.entry("waxed", "BLAZE_WAX"),
            Map.entry("withered", "WITHER_BLOOD"));

    /** An item that takes an enchantment one level past what books go to, from {@code from} on. */
    private record Raise(String item, int from) {}

    private static final Map<String, Raise> RAISES = Map.of(
            "efficiency", new Raise("SIL_EX", 6),
            "scavenger", new Raise("GOLDEN_BOUNTY", 6),
            "pesterminator", new Raise("PESTHUNTING_GUIDE", 6),
            "luck_of_the_sea", new Raise("GOLD_BOTTLE_CAP", 7),
            "piscary", new Raise("TROUBLED_BUBBLE", 7),
            "frail", new Raise("SEVERED_PINCER", 7),
            "spiked_hook", new Raise("OCTOPUS_TENDRIL", 7),
            "charm", new Raise("CHAIN_END_TIMES", 6),
            "smite", new Raise("SEVERED_HAND", 7),
            "bane_of_arthropods", new Raise("ENSNARED_SNAIL", 7));

    /** Enchantments that gain their levels by use: only the first level was bought. */
    private static final Set<String> STACKING = Set.of("expertise", "compact", "cultivating", "champion", "hecatomb",
            "toxophilite", "absorb");

    /** Items whose Efficiency grows by use, so no Silex went into it. */
    private static final Set<String> EFFICIENCY_BY_USE = Set.of("PROMISING_SPADE");

    private static final String[] MASTER_STARS = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR",
            "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};

    private static final Set<String> GEM_TYPES = Set.of("RUBY", "AMBER", "TOPAZ", "JADE", "SAPPHIRE", "AMETHYST",
            "JASPER", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT");
    private static final Pattern GEM_SLOT = Pattern.compile("[A-Z]+_\\d+");

    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};

    /** Upgrades counted by a number on the item, and the item each one is. */
    private static final String[][] COUNTED = {
            {"rarity_upgrades", "RECOMBOBULATOR_3000"},
            {"art_of_war_count", "THE_ART_OF_WAR"},
            {"artOfPeaceApplied", "THE_ART_OF_PEACE"},
            {"wood_singularity_count", "WOOD_SINGULARITY"},
            {"tuned_transmission", "TRANSMISSION_TUNER"},
            {"mana_disintegrator_count", "MANA_DISINTEGRATOR"},
            {"farming_for_dummies_count", "FARMING_FOR_DUMMIES"},
            {"polarvoid", "POLARVOID_BOOK"},
            {"bookworm_books", "BOOKWORM_BOOK"},
            {"jalapeno_count", "JALAPENO_BOOK"},
            {"ethermerge", "ETHERWARP_CONDUIT"},
            {"divan_powder_coating", "DIVAN_POWDER_COATING"},
            {"sack_pss", "POCKET_SACK_IN_A_SACK"},
    };

    /** Upgrades named by an id on the item. Drill parts write theirs in lower case. */
    private static final String[] NAMED = {"power_ability_scroll", "drill_part_engine", "drill_part_fuel_tank",
            "drill_part_upgrade_module"};

    // ---- The breakdown --------------------------------------------------------------------------

    /** The item with this SkyBlock data, part by part. */
    public static Breakdown of(JsonObject data, Prices prices, Catalog catalog) {
        Builder b = new Builder(prices, catalog);
        String id = string(data, "id");
        ItemIcons.Upgrades upgrades = id.isEmpty() ? null : catalog.upgrades(id);
        JsonObject pet = petInfo(data);
        if (pet != null) {
            pet(b, pet);
        } else {
            base(b, data, id);
        }
        stars(b, data, upgrades);
        enchantments(b, data, id, upgrades);
        gemstones(b, data, upgrades);
        upgrades(b, data);
        reforge(b, data);
        cosmetics(b, data, id);
        return new Breakdown(List.copyOf(b.parts));
    }

    private static void base(Builder b, JsonObject data, String id) {
        if (id.isEmpty()) return;
        // A book is worth what is written in it, which the enchantments below count.
        if (id.equals("ENCHANTED_BOOK")) return;
        if (id.equals("RUNE") || id.equals("UNIQUE_RUNE")) {
            JsonObject runes = object(data, "runes");
            if (runes != null) {
                for (Map.Entry<String, JsonElement> e : runes.entrySet()) {
                    b.item(Group.BASE, runeId(e.getKey(), integer(e.getValue())), 1, "Base Item", null);
                }
            }
            return;
        }
        b.item(Group.BASE, id, 1, "Base Item", null);
    }

    /** A pet: the cheapest of its kind and rarity, its held item and its skin. */
    private static void pet(Builder b, JsonObject pet) {
        String type = string(pet, "type");
        String tier = string(pet, "tier");
        int rarity = java.util.Arrays.asList(RARITIES).indexOf(tier);
        if (!type.isEmpty() && rarity >= 0) {
            String key = type + ";" + rarity;
            String note = "The cheapest " + titleCase(tier) + " " + titleCase(type) + " on the Auction House, at any level.";
            b.item(Group.BASE, key, 1, "Base Item", note);
        }
        String held = string(pet, "heldItem");
        if (!held.isEmpty()) b.item(Group.UPGRADES, held, 1, null, null);
        String skin = string(pet, "skin");
        if (!skin.isEmpty()) b.item(Group.COSMETICS, "PET_SKIN_" + skin, 1, null, null);
    }

    /**
     * Stars: the essence and items each star took, as the item list prices them, after what made it
     * a dungeon item where it had to become one first; then master stars past the fifth.
     */
    private static void stars(Builder b, JsonObject data, ItemIcons.Upgrades upgrades) {
        int level = Math.max(integer(data, "upgrade_level"), integer(data, "dungeon_item_level"));
        if (level <= 0) return;
        List<List<ItemIcons.Cost>> costs = upgrades == null ? List.of() : upgrades.stars();
        Map<String, Long> items = new LinkedHashMap<>();
        long coins = 0;
        long conversion = 0;
        if (upgrades != null && !costs.isEmpty()) {
            for (ItemIcons.Cost cost : upgrades.conversion()) {
                if (cost.coins()) coins += cost.amount();
                else items.merge(cost.item(), cost.amount(), Long::sum);
                conversion += cost.coins() ? 0 : cost.amount();
            }
        }
        for (int star = 0; star < Math.min(level, costs.size()); star++) {
            for (ItemIcons.Cost cost : costs.get(star)) {
                if (cost.coins()) coins += cost.amount();
                else items.merge(cost.item(), cost.amount(), Long::sum);
            }
        }
        String conversionId = upgrades == null || upgrades.conversion().isEmpty() ? null : upgrades.conversion().get(0).item();
        for (Map.Entry<String, Long> e : items.entrySet()) {
            String note = e.getKey().equals(conversionId) && conversion > 0
                    ? "Includes " + conversion + " for turning it into a dungeon item first." : null;
            b.item(Group.STARS, e.getKey(), e.getValue(), null, note);
        }
        if (coins > 0) b.coins(Group.STARS, "Coins", coins);
        // Dungeon items take five stars of essence and up to five master stars after them. Without
        // the item list there is no telling, so master stars count from the sixth star on.
        if (costs.isEmpty() || costs.size() == 5) {
            for (int star = 5; star < Math.min(level, 10); star++) {
                b.item(Group.STARS, MASTER_STARS[star - 5], 1, null, null);
            }
        }
    }

    private static void enchantments(Builder b, JsonObject data, String id, ItemIcons.Upgrades upgrades) {
        JsonObject enchantments = object(data, "enchantments");
        if (enchantments == null) return;
        for (Map.Entry<String, JsonElement> e : enchantments.entrySet()) {
            String name = e.getKey().toLowerCase(Locale.ROOT);
            int level = integer(e.getValue());
            if (level <= 0) continue;
            Integer own = upgrades == null ? null : upgrades.enchantments().get(name);
            // What the item comes with cost nothing.
            if (own != null && own >= level) continue;
            enchantment(b, id, name, level, own == null ? 0 : own);
        }
    }

    /**
     * One enchantment: its book on the Bazaar if there is one at that level. Past the books, the item
     * that raises it a level at a time on top of the highest book; for one that grows by use, the
     * first level; otherwise two books of the level below for each level missing, which is how an
     * anvil gets there.
     */
    private static void enchantment(Builder b, String itemId, String name, int level, int own) {
        String label = enchantmentName(name, level);
        String book = bookId(name, level);
        ItemPrices.Quote exact = b.prices.quote(book);
        if (exact != null) {
            b.parts.add(new Part(Group.ENCHANTMENTS, label, book, 1, exact.price(), exact.market(), false, null));
            return;
        }
        Raise raise = RAISES.get(name);
        if (raise != null && level >= raise.from() && !(name.equals("efficiency") && EFFICIENCY_BY_USE.contains(itemId))) {
            int start = Math.max(raise.from() - 1, own);
            int steps = level - start;
            ItemPrices.Quote item = b.prices.quote(raise.item());
            // The level it was raised from: a book, or one from the enchanting table, which is free.
            double[] below = start > own ? bookPrice(b, name, start) : new double[] {0, 0};
            boolean estimated = item == null || below[1] > 0;
            double total = below[0] + (item == null ? 0 : item.price() * steps);
            String note = (below[0] > 0 ? enchantmentName(name, start) + " and " : "") + steps + " × " + b.name(raise.item()) + ".";
            b.parts.add(new Part(Group.ENCHANTMENTS, label, raise.item(), 1, item == null && below[0] == 0 ? Double.NaN : total,
                    item == null ? null : item.market(), estimated, note));
            return;
        }
        if (STACKING.contains(name) && level > 1) {
            ItemPrices.Quote first = b.prices.quote(bookId(name, 1));
            b.parts.add(new Part(Group.ENCHANTMENTS, label, bookId(name, 1), 1, first == null ? Double.NaN : first.price(),
                    first == null ? null : first.market(), false, "Gains its levels by use; counted as the first level's book."));
            return;
        }
        for (int lower = level - 1; lower >= 1; lower--) {
            ItemPrices.Quote q = b.prices.quote(bookId(name, lower));
            if (q == null) continue;
            int books = 1 << Math.min(20, level - lower);
            b.parts.add(new Part(Group.ENCHANTMENTS, label, bookId(name, lower), 1, q.price() * books, q.market(), true,
                    "No " + label + " on the Bazaar: counted as " + books + " × " + enchantmentName(name, lower) + "."));
            return;
        }
        b.parts.add(new Part(Group.ENCHANTMENTS, label, null, 1, Double.NaN, null, false,
                "No book of it on the Bazaar right now; the low levels come from the enchanting table."));
    }

    /**
     * The price of the book at this level, or what the anvil makes of lower ones, and whether it had
     * to be made: {price, 1} then. A level no book is sold at comes from the enchanting table: 0.
     */
    private static double[] bookPrice(Builder b, String name, int level) {
        for (int lower = level; lower >= 1; lower--) {
            ItemPrices.Quote q = b.prices.quote(bookId(name, lower));
            if (q != null) return new double[] {q.price() * (1 << Math.min(20, level - lower)), lower < level ? 1 : 0};
        }
        return new double[] {0, 0};
    }

    /** The gemstones in the item's slots, and what opening the slots that had to be opened cost. */
    private static void gemstones(Builder b, JsonObject data, ItemIcons.Upgrades upgrades) {
        JsonObject gems = object(data, "gems");
        if (gems == null) return;
        Map<String, Integer> found = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : gems.entrySet()) {
            String slot = e.getKey();
            if (!GEM_SLOT.matcher(slot).matches()) continue;
            String quality = gemQuality(e.getValue());
            if (quality == null) continue;
            String prefix = slot.substring(0, slot.lastIndexOf('_'));
            // A slot of one kind holds that kind; a combat or mining slot says what it holds beside it.
            String type = GEM_TYPES.contains(prefix) ? prefix : string(gems, slot + "_gem");
            if (type.isEmpty()) continue;
            found.merge(quality + "_" + type + "_GEM", 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : found.entrySet()) b.item(Group.GEMSTONES, e.getKey(), e.getValue(), null, null);

        JsonArray unlocked = gems.has("unlocked_slots") && gems.get("unlocked_slots").isJsonArray()
                ? gems.getAsJsonArray("unlocked_slots") : null;
        if (unlocked == null || upgrades == null) return;
        // The item list names slots by kind in order: the second Jade slot is JADE_1.
        Map<String, List<ItemIcons.Cost>> bySlot = new HashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (ItemIcons.GemSlot slot : upgrades.gemSlots()) {
            int index = seen.merge(slot.type(), 1, Integer::sum) - 1;
            bySlot.put(slot.type() + "_" + index, slot.unlock());
        }
        List<ItemIcons.Cost> costs = new ArrayList<>();
        int slots = 0;
        for (JsonElement element : unlocked) {
            if (!element.isJsonPrimitive()) continue;
            List<ItemIcons.Cost> unlock = bySlot.get(element.getAsString());
            if (unlock == null || unlock.isEmpty()) continue;
            costs.addAll(unlock);
            slots++;
        }
        if (slots > 0) b.composite(Group.GEMSTONES, slots == 1 ? "Unlocked Slot" : "Unlocked Slots (" + slots + ")", costs);
    }

    private static final Set<String> GEM_QUALITIES = Set.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");

    /** A gemstone's quality, written plainly or, for one with an id of its own, inside an object. */
    private static String gemQuality(JsonElement value) {
        String quality = value.isJsonPrimitive() ? value.getAsString()
                : value.isJsonObject() ? string(value.getAsJsonObject(), "quality") : "";
        return GEM_QUALITIES.contains(quality) ? quality : null;
    }

    /** Books, scrolls and the other things applied to the item. */
    private static void upgrades(Builder b, JsonObject data) {
        int potatoes = integer(data, "hot_potato_count");
        if (potatoes > 0) {
            b.item(Group.UPGRADES, "HOT_POTATO_BOOK", Math.min(potatoes, 10), null, null);
            if (potatoes > 10) b.item(Group.UPGRADES, "FUMING_POTATO_BOOK", potatoes - 10, null, null);
        }
        for (String[] counted : COUNTED) {
            int count = integer(data, counted[0]);
            if (count > 0) b.item(Group.UPGRADES, counted[1], count, null, null);
        }
        // The book counts kills from the moment it is applied.
        if (data.has("stats_book")) b.item(Group.UPGRADES, "BOOK_OF_STATS", 1, null, null);
        if (data.has("ability_scroll") && data.get("ability_scroll").isJsonArray()) {
            for (JsonElement scroll : data.getAsJsonArray("ability_scroll")) {
                if (scroll.isJsonPrimitive()) b.item(Group.UPGRADES, scroll.getAsString(), 1, null, null);
            }
        }
        for (String key : NAMED) {
            String id = string(data, key);
            if (!id.isEmpty()) b.item(Group.UPGRADES, id.toUpperCase(Locale.ROOT), 1, null, null);
        }
        String enrichment = string(data, "talisman_enrichment");
        if (!enrichment.isEmpty()) {
            b.item(Group.UPGRADES, "TALISMAN_ENRICHMENT_" + enrichment.toUpperCase(Locale.ROOT), 1, null, null);
        }
    }

    /** The reforge stone, for a reforge that takes one; a reforge from the Blacksmith leaves nothing to sell. */
    private static void reforge(Builder b, JsonObject data) {
        String modifier = string(data, "modifier");
        if (modifier.isEmpty()) return;
        String stone = REFORGE_STONES.get(modifier.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""));
        if (stone == null) return;
        b.item(Group.REFORGE, stone, 1, titleCase(modifier.replace('_', ' ')) + " Reforge", "Applied with " + b.name(stone) + ".");
    }

    private static void cosmetics(Builder b, JsonObject data, String id) {
        String dye = string(data, "dye_item");
        if (!dye.isEmpty()) b.item(Group.COSMETICS, dye, 1, null, null);
        String skin = string(data, "skin");
        if (!skin.isEmpty()) {
            // Written as the skin item's id, or without the _SKIN it ends in.
            String skinId = b.prices.quote(skin) == null && b.prices.quote(skin + "_SKIN") != null ? skin + "_SKIN" : skin;
            b.item(Group.COSMETICS, skinId, 1, null, null);
        }
        JsonObject runes = object(data, "runes");
        if (runes != null && !id.equals("RUNE") && !id.equals("UNIQUE_RUNE")) {
            for (Map.Entry<String, JsonElement> e : runes.entrySet()) {
                b.item(Group.COSMETICS, runeId(e.getKey(), integer(e.getValue())), 1, runeName(e.getKey(), integer(e.getValue())), null);
            }
        }
    }

    // ---- Names ----------------------------------------------------------------------------------

    /**
     * What an id is called: a book by its enchantment, "Champion I"; anything else by its name in
     * Hypixel's list, or one made from the id.
     */
    public static String nameOf(String id, Catalog catalog) {
        java.util.regex.Matcher book = BOOK.matcher(id);
        if (book.matches()) return enchantmentName(book.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(book.group(2)));
        return new Builder(null, catalog).name(id);
    }

    private static final Pattern BOOK = Pattern.compile("ENCHANTMENT_(.+)_(\\d+)");

    /** "ENCHANTMENT_ULTIMATE_WISE_5", the Bazaar's name for a book. */
    static String bookId(String name, int level) {
        return "ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_" + level;
    }

    /** "Ultimate Wise V", "Soul Eater V", "Prismatic V": how the game writes an enchantment. */
    static String enchantmentName(String name, int level) {
        String n = name.toLowerCase(Locale.ROOT);
        // Ultimate enchantments are written without the word, but for the two named after it.
        if (n.startsWith("ultimate_") && !n.equals("ultimate_wise") && !n.equals("ultimate_jerry")) n = n.substring(9);
        String title = switch (n) {
            case "pristine" -> "Prismatic";
            case "triple_strike" -> "Triple-Strike";
            default -> n.startsWith("turbo_") ? "Turbo-" + titleCase(n.substring(6)) : titleCase(n.replace('_', ' '));
        };
        return title + " " + roman(level);
    }

    private static String runeId(String type, int level) {
        return "RUNE_" + type.toUpperCase(Locale.ROOT) + "_" + level;
    }

    /** "Blood Rune III" for the rune BLOOD_2 at level 3; the number in its type only tells versions apart. */
    private static String runeName(String type, int level) {
        return titleCase(type.replaceAll("_\\d+$", "").replace('_', ' ')) + " Rune " + roman(level);
    }

    static String roman(int n) {
        if (n <= 0 || n >= 40) return Integer.toString(n);
        String[] tens = {"", "X", "XX", "XXX"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[n / 10] + ones[n % 10];
    }

    /** "THE_ART_OF_WAR" or "art of war" as "The Art of War". */
    static String titleCase(String text) {
        String[] words = text.replace('_', ' ').trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            boolean small = i > 0 && (w.equals("of") || w.equals("the") || w.equals("and") || w.equals("in") || w.equals("a"));
            out.append(small ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return out.toString();
    }

    // ---- Reading the data -----------------------------------------------------------------------

    /** The pet's own data, which Hypixel keeps as a JSON text inside the item's. */
    private static JsonObject petInfo(JsonObject data) {
        if (!data.has("petInfo")) return null;
        JsonElement info = data.get("petInfo");
        try {
            if (info.isJsonPrimitive()) info = JsonParser.parseString(info.getAsString());
        } catch (RuntimeException e) {
            return null;
        }
        return info.isJsonObject() ? info.getAsJsonObject() : null;
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static int integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null ? 0 : integer(e);
    }

    private static int integer(JsonElement e) {
        if (!e.isJsonPrimitive()) return 0;
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : Integer.parseInt(e.getAsString().trim());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /** Collects the parts as they are priced. */
    private static final class Builder {
        final Prices prices;
        final Catalog catalog;
        final List<Part> parts = new ArrayList<>();

        Builder(Prices prices, Catalog catalog) {
            this.prices = prices;
            this.catalog = catalog;
        }

        /** {@code count} of one item at its price now, under its own name unless given another. */
        void item(Group group, String id, double count, String label, String note) {
            ItemPrices.Quote q = prices.quote(id);
            parts.add(new Part(group, label != null ? label : name(id), id, count,
                    q == null ? Double.NaN : q.price(), q == null ? null : q.market(), false, note));
        }

        void coins(Group group, String label, long amount) {
            parts.add(new Part(group, label, null, 1, amount, null, false, null));
        }

        /** One part made of several costs, such as opening slots; what has no price is left out of it. */
        void composite(Group group, String label, List<ItemIcons.Cost> costs) {
            double total = 0;
            boolean missing = false;
            Map<String, Long> items = new LinkedHashMap<>();
            long coins = 0;
            for (ItemIcons.Cost cost : costs) {
                if (cost.coins()) {
                    coins += cost.amount();
                    continue;
                }
                items.merge(cost.item(), cost.amount(), Long::sum);
            }
            StringBuilder note = new StringBuilder();
            if (coins > 0) {
                total += coins;
                note.append(net.alpaka.addons.features.market.Coins.full(coins)).append(" coins");
            }
            for (Map.Entry<String, Long> e : items.entrySet()) {
                ItemPrices.Quote q = prices.quote(e.getKey());
                if (q == null) missing = true;
                else total += q.price() * e.getValue();
                if (note.length() > 0) note.append(", ");
                note.append(e.getValue()).append(" × ").append(name(e.getKey()));
            }
            note.append('.');
            parts.add(new Part(group, label, null, 1, total, null, missing, note.toString()));
        }

        /** The item's name in Hypixel's list, or one made from its id. */
        String name(String id) {
            String name = catalog.name(id);
            if (name != null && !name.isBlank()) return name;
            if (id.startsWith("ESSENCE_")) return titleCase(id.substring(8)) + " Essence";
            if (id.startsWith("PET_SKIN_")) return titleCase(id.substring(9)) + " Skin";
            if (id.contains(";")) return titleCase(id.substring(0, id.indexOf(';')));
            return titleCase(id);
        }
    }
}
