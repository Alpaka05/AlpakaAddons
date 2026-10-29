package net.alpaka.addons.features.itemvalue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.features.itemvalue.ItemValue.Breakdown;
import net.alpaka.addons.features.itemvalue.ItemValue.Group;
import net.alpaka.addons.features.itemvalue.ItemValue.Part;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.ItemIcons.Cost;
import net.alpaka.addons.features.market.MarketKind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemValueTest {

    /** Prices as a table: Bazaar products and lowest BINs. */
    private static final class Table implements ItemValue.Prices {
        final Map<String, Double> bazaar = new HashMap<>();
        final Map<String, Double> bins = new HashMap<>();

        Table bz(String id, double price) {
            bazaar.put(id, price);
            return this;
        }

        Table ah(String id, double price) {
            bins.put(id, price);
            return this;
        }

        @Override
        public ItemPrices.Quote quote(String id) {
            if (bazaar.containsKey(id)) return new ItemPrices.Quote(bazaar.get(id), MarketKind.BAZAAR);
            if (bins.containsKey(id)) return new ItemPrices.Quote(bins.get(id), MarketKind.AUCTION_HOUSE);
            return null;
        }
    }

    /** Hypixel's item list, as far as a test needs it. */
    private static final class Listing implements ItemValue.Catalog {
        final Map<String, String> names = new HashMap<>();
        final Map<String, ItemIcons.Upgrades> upgrades = new HashMap<>();

        @Override
        public String name(String id) {
            return names.get(id);
        }

        @Override
        public ItemIcons.Upgrades upgrades(String id) {
            return upgrades.get(id);
        }
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static List<List<Cost>> essence(String type, int... amounts) {
        java.util.ArrayList<List<Cost>> stars = new java.util.ArrayList<>();
        for (int amount : amounts) stars.add(List.of(new Cost("ESSENCE_" + type, amount)));
        return stars;
    }

    private static Part part(Breakdown b, String label) {
        for (Part p : b.parts()) if (p.label().equals(label)) return p;
        return null;
    }

    private static Part partById(Breakdown b, String id) {
        for (Part p : b.parts()) if (id.equals(p.id())) return p;
        return null;
    }

    // ---- A weapon -------------------------------------------------------------------------------

    @Test
    void breaksAWeaponDownIntoEverythingOnIt() {
        Table prices = new Table()
                .ah("HYPERION", 440_000_000)
                .bz("ESSENCE_WITHER", 4_000)
                .bz("FIRST_MASTER_STAR", 10_000_000).bz("SECOND_MASTER_STAR", 20_000_000)
                .bz("THIRD_MASTER_STAR", 40_000_000).bz("FOURTH_MASTER_STAR", 60_000_000).bz("FIFTH_MASTER_STAR", 80_000_000)
                .bz("RECOMBOBULATOR_3000", 7_000_000)
                .bz("HOT_POTATO_BOOK", 100_000).bz("FUMING_POTATO_BOOK", 1_200_000)
                .bz("THE_ART_OF_WAR", 15_000_000)
                .bz("WITHER_BLOOD", 2_500_000)
                .ah("IMPLOSION_SCROLL", 300_000_000).ah("SHADOW_WARP_SCROLL", 290_000_000).ah("WITHER_SHIELD_SCROLL", 280_000_000)
                .bz("ENCHANTMENT_ULTIMATE_WISE_5", 1_500_000)
                .bz("ENCHANTMENT_SHARPNESS_7", 148_000_000)
                .bz("PERFECT_SAPPHIRE_GEM", 16_000_000).bz("PERFECT_JASPER_GEM", 17_000_000)
                .bz("FLAWLESS_SAPPHIRE_GEM", 200_000).bz("FLAWLESS_JASPER_GEM", 300_000);
        Listing listing = new Listing();
        listing.names.put("HYPERION", "Hyperion");
        listing.names.put("ESSENCE_WITHER", "Wither Essence");
        listing.names.put("WITHER_BLOOD", "Wither Blood");
        listing.upgrades.put("HYPERION", new ItemIcons.Upgrades(essence("WITHER", 150, 300, 500, 900, 1500), List.of(),
                List.of(new ItemIcons.GemSlot("SAPPHIRE", List.of(new Cost(null, 250_000), new Cost("FLAWLESS_SAPPHIRE_GEM", 4))),
                        new ItemIcons.GemSlot("COMBAT", List.of(new Cost(null, 250_000), new Cost("FLAWLESS_JASPER_GEM", 1)))),
                Map.of()));

        Breakdown b = ItemValue.of(json("""
                {"id":"HYPERION","modifier":"withered","upgrade_level":10,"rarity_upgrades":1,"hot_potato_count":15,
                 "art_of_war_count":1,"ability_scroll":["IMPLOSION_SCROLL","SHADOW_WARP_SCROLL","WITHER_SHIELD_SCROLL"],
                 "enchantments":{"ultimate_wise":5,"sharpness":7},
                 "gems":{"SAPPHIRE_0":"PERFECT","COMBAT_0":{"quality":"PERFECT","uuid":"x"},"COMBAT_0_gem":"JASPER",
                         "unlocked_slots":["SAPPHIRE_0","COMBAT_0"]}}"""), prices, listing);

        Part base = part(b, "Base Item");
        assertNotNull(base);
        assertEquals(Group.BASE, base.group());
        assertEquals(440_000_000, base.total());
        assertEquals(MarketKind.AUCTION_HOUSE, base.market());

        // Five stars of essence, then five master stars.
        Part essence = partById(b, "ESSENCE_WITHER");
        assertEquals(3350, essence.count());
        assertEquals(13_400_000, essence.total());
        assertEquals(210_000_000, b.total(Group.STARS) - essence.total());

        assertEquals(10, partById(b, "HOT_POTATO_BOOK").count());
        assertEquals(5, partById(b, "FUMING_POTATO_BOOK").count());
        assertEquals(7_000_000, partById(b, "RECOMBOBULATOR_3000").total());
        assertEquals(870_000_000, partById(b, "IMPLOSION_SCROLL").total() + partById(b, "SHADOW_WARP_SCROLL").total()
                + partById(b, "WITHER_SHIELD_SCROLL").total());

        Part reforge = partById(b, "WITHER_BLOOD");
        assertEquals(Group.REFORGE, reforge.group());
        assertEquals("Withered Reforge", reforge.label());

        assertEquals(149_500_000, b.total(Group.ENCHANTMENTS));
        assertEquals("Ultimate Wise V", partById(b, "ENCHANTMENT_ULTIMATE_WISE_5").label());

        // Both gems, and opening both slots: coins and the gemstones each slot took.
        assertEquals(16_000_000, partById(b, "PERFECT_SAPPHIRE_GEM").total());
        assertEquals(17_000_000, partById(b, "PERFECT_JASPER_GEM").total());
        Part slots = part(b, "Unlocked Slots (2)");
        assertNotNull(slots);
        assertEquals(500_000 + 4 * 200_000 + 300_000, slots.total());

        assertFalse(b.incomplete());
        double sum = 0;
        for (Part p : b.parts()) sum += p.total();
        assertEquals(sum, b.total());
    }

    @Test
    void anItemOnTheBazaarIsWorthItsBazaarPrice() {
        Breakdown b = ItemValue.of(json("{\"id\":\"ENCHANTED_DIAMOND\"}"), new Table().bz("ENCHANTED_DIAMOND", 175_000), new Listing());
        assertEquals(1, b.parts().size());
        assertEquals(175_000, b.total());
        assertEquals(MarketKind.BAZAAR, b.parts().get(0).market());
    }

    @Test
    void aPartWithoutAPriceIsListedButNotCounted() {
        Breakdown b = ItemValue.of(json("{\"id\":\"SOULBOUND_THING\",\"rarity_upgrades\":1}"),
                new Table().bz("RECOMBOBULATOR_3000", 7_000_000), new Listing());
        assertFalse(part(b, "Base Item").priced());
        assertTrue(b.incomplete());
        assertEquals(7_000_000, b.total());
    }

    // ---- Stars ----------------------------------------------------------------------------------

    @Test
    void starsTakeWhatEachStarCostsIncludingItemsAndNoMasterStarsPastTen() {
        Listing listing = new Listing();
        List<List<Cost>> stars = new java.util.ArrayList<>(essence("CRIMSON", 30, 35, 40, 45, 50, 55, 60));
        stars.add(List.of(new Cost("ESSENCE_CRIMSON", 70), new Cost("HEAVY_PEARL", 2)));
        stars.add(List.of(new Cost("ESSENCE_CRIMSON", 80), new Cost("HEAVY_PEARL", 3)));
        stars.add(List.of(new Cost("ESSENCE_CRIMSON", 90), new Cost("HEAVY_PEARL", 4)));
        listing.upgrades.put("CRIMSON_CHESTPLATE", new ItemIcons.Upgrades(stars, List.of(), List.of(), Map.of()));
        Breakdown b = ItemValue.of(json("{\"id\":\"CRIMSON_CHESTPLATE\",\"upgrade_level\":9}"),
                new Table().bz("ESSENCE_CRIMSON", 1_000).bz("HEAVY_PEARL", 200_000).bz("FIRST_MASTER_STAR", 1), listing);
        assertEquals(30 + 35 + 40 + 45 + 50 + 55 + 60 + 70 + 80, partById(b, "ESSENCE_CRIMSON").count());
        assertEquals(5, partById(b, "HEAVY_PEARL").count());
        assertNull(partById(b, "FIRST_MASTER_STAR"));
    }

    @Test
    void anItemMadeIntoADungeonItemCountsWhatThatTook() {
        Listing listing = new Listing();
        listing.upgrades.put("TERMINATOR", new ItemIcons.Upgrades(essence("DRAGON", 100, 200, 300, 500, 750),
                List.of(new Cost("ESSENCE_DRAGON", 300)), List.of(), Map.of()));
        Breakdown b = ItemValue.of(json("{\"id\":\"TERMINATOR\",\"upgrade_level\":5}"), new Table().bz("ESSENCE_DRAGON", 1_000), listing);
        Part essence = partById(b, "ESSENCE_DRAGON");
        assertEquals(300 + 100 + 200 + 300 + 500 + 750, essence.count());
        assertNotNull(essence.note());
    }

    // ---- Enchantments ---------------------------------------------------------------------------

    @Test
    void anEnchantedBookIsWorthWhatIsWrittenInIt() {
        Breakdown b = ItemValue.of(json("{\"id\":\"ENCHANTED_BOOK\",\"enchantments\":{\"ultimate_chimera\":5}}"),
                new Table().bz("ENCHANTMENT_ULTIMATE_CHIMERA_5", 390_000_000).ah("ENCHANTED_BOOK", 5), new Listing());
        assertEquals(1, b.parts().size());
        assertEquals("Chimera V", b.parts().get(0).label());
        assertEquals(390_000_000, b.total());
    }

    @Test
    void levelsPastTheBooksCountTheItemThatRaisedThem() {
        Table prices = new Table()
                .bz("ENCHANTMENT_SCAVENGER_5", 300_000).bz("GOLDEN_BOUNTY", 38_000_000)
                .bz("ENCHANTMENT_SMITE_6", 40_000).bz("SEVERED_HAND", 9_800_000)
                .bz("SIL_EX", 3_000_000);
        Breakdown b = ItemValue.of(json("{\"id\":\"X\",\"enchantments\":{\"scavenger\":6,\"smite\":7,\"efficiency\":10}}"),
                prices, new Listing());
        assertEquals(38_300_000, part(b, "Scavenger VI").total());
        assertEquals(9_840_000, part(b, "Smite VII").total());
        // Efficiency V comes from the enchanting table; each level past it took a Silex.
        Part efficiency = part(b, "Efficiency X");
        assertEquals(15_000_000, efficiency.total());
        assertEquals("SIL_EX", efficiency.id());
        assertFalse(efficiency.estimated());
    }

    @Test
    void whatTheItemComesWithIsFree() {
        Listing listing = new Listing();
        listing.upgrades.put("STONK_PICKAXE", new ItemIcons.Upgrades(List.of(), List.of(), List.of(), Map.of("efficiency", 6)));
        Table prices = new Table().bz("SIL_EX", 3_000_000).ah("STONK_PICKAXE", 1_000_000);
        assertNull(part(ItemValue.of(json("{\"id\":\"STONK_PICKAXE\",\"enchantments\":{\"efficiency\":6}}"), prices, listing),
                "Efficiency VI"));
        assertEquals(3_000_000, part(ItemValue.of(json("{\"id\":\"STONK_PICKAXE\",\"enchantments\":{\"efficiency\":7}}"), prices, listing),
                "Efficiency VII").total());
    }

    @Test
    void aMissingLevelIsTwoBooksOfTheOneBelowAndMarkedAsAnEstimate() {
        Breakdown b = ItemValue.of(json("{\"id\":\"X\",\"enchantments\":{\"dragon_hunter\":5}}"),
                new Table().bz("ENCHANTMENT_DRAGON_HUNTER_3", 1_000_000), new Listing());
        Part part = part(b, "Dragon Hunter V");
        assertEquals(4_000_000, part.total());
        assertTrue(part.estimated());
    }

    @Test
    void enchantmentsThatGrowByUseCountTheFirstBook() {
        Breakdown b = ItemValue.of(json("{\"id\":\"X\",\"enchantments\":{\"champion\":10}}"),
                new Table().bz("ENCHANTMENT_CHAMPION_1", 5_000_000), new Listing());
        assertEquals(5_000_000, part(b, "Champion X").total());
    }

    @Test
    void enchantingTableLevelsHaveNoPriceAndLeaveTheTotalWhole() {
        Breakdown b = ItemValue.of(json("{\"id\":\"X\",\"enchantments\":{\"sharpness\":5}}"), new Table().ah("X", 10), new Listing());
        assertFalse(part(b, "Sharpness V").priced());
        assertFalse(b.incomplete());
    }

    @Test
    void writesEnchantmentsAsTheGameDoes() {
        assertEquals("Ultimate Wise V", ItemValue.enchantmentName("ultimate_wise", 5));
        assertEquals("Soul Eater V", ItemValue.enchantmentName("ultimate_soul_eater", 5));
        assertEquals("Prismatic V", ItemValue.enchantmentName("pristine", 5));
        assertEquals("Luck of the Sea VII", ItemValue.enchantmentName("luck_of_the_sea", 7));
        assertEquals("Turbo-Wheat V", ItemValue.enchantmentName("turbo_wheat", 5));
        assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", ItemValue.bookId("ultimate_wise", 5));
        assertEquals("The Art of War", ItemValue.titleCase("THE_ART_OF_WAR"));
        assertEquals("XIV", ItemValue.roman(14));
        // What a graph is titled with: a book by its enchantment, anything else by its name.
        Listing listing = new Listing();
        listing.names.put("SEVERED_HAND", "Severed Hand");
        assertEquals("Champion I", ItemValue.nameOf("ENCHANTMENT_CHAMPION_1", listing));
        assertEquals("Severed Hand", ItemValue.nameOf("SEVERED_HAND", listing));
        assertEquals("Wither Essence", ItemValue.nameOf("ESSENCE_WITHER", listing));
    }

    // ---- Pets and cosmetics ---------------------------------------------------------------------

    @Test
    void aPetIsTheCheapestOfItsKindWithItsItemAndSkin() {
        Table prices = new Table().ah("GOLDEN_DRAGON;4", 626_000_000).ah("PET_ITEM_TIER_BOOST", 128_000_000)
                .ah("PET_SKIN_GOLDEN_DRAGON_ANUBIS", 900_000_000);
        Breakdown b = ItemValue.of(json("""
                {"id":"PET","petInfo":"{\\"type\\":\\"GOLDEN_DRAGON\\",\\"tier\\":\\"LEGENDARY\\",\\"heldItem\\":\\"PET_ITEM_TIER_BOOST\\",\\"skin\\":\\"GOLDEN_DRAGON_ANUBIS\\"}"}"""),
                prices, new Listing());
        assertEquals(626_000_000, part(b, "Base Item").total());
        assertEquals(Group.UPGRADES, partById(b, "PET_ITEM_TIER_BOOST").group());
        assertEquals(Group.COSMETICS, partById(b, "PET_SKIN_GOLDEN_DRAGON_ANUBIS").group());
        assertEquals(1_654_000_000, b.total());
    }

    @Test
    void runesDyesAndReforgesByTheirItems() {
        Table prices = new Table().ah("RUNE_BLOOD_2_3", 400_000).ah("DYE_BONE", 50_000_000).bz("DRAGON_CLAW", 1_000_000);
        Breakdown b = ItemValue.of(json("{\"id\":\"X\",\"runes\":{\"BLOOD_2\":3},\"dye_item\":\"DYE_BONE\",\"modifier\":\"Fabled\"}"),
                prices, new Listing());
        assertEquals("Blood Rune III", partById(b, "RUNE_BLOOD_2_3").label());
        assertEquals(Group.COSMETICS, partById(b, "DYE_BONE").group());
        assertEquals("Fabled Reforge", partById(b, "DRAGON_CLAW").label());
        // A reforge from the Blacksmith takes no stone and is not part of the worth.
        assertTrue(ItemValue.of(json("{\"id\":\"X\",\"modifier\":\"heroic\"}"), prices, new Listing()).parts().stream()
                .noneMatch(p -> p.group() == Group.REFORGE));
    }
}
