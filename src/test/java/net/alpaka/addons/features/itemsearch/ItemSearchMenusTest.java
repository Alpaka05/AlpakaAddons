package net.alpaka.addons.features.itemsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSearchMenusTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Ender Chest (3/9)|ENDER_CHEST|3|",
            "Jumbo Backpack (Slot #12)|BACKPACK|12|",
            "Small Backpack (Slot #1)|BACKPACK|1|",
            "Wardrobe (2/2)|WARDROBE|2|",
            "Accessory Bag (2/4)|ACCESSORY_BAG|2|",
            "Accessory Bag|ACCESSORY_BAG|1|",
            "Personal Vault|VAULT|1|",
            "Mining Sack|SACKS|1|Mining Sack",
            "Enchanted Agronomy Sack|SACKS|1|Enchanted Agronomy Sack",
            "Gemstones Sack (2/2)|SACKS|2|Gemstones Sack",
    })
    void readsTheMenusItKnows(String title, ItemSearchSource source, int page, String label) {
        ItemSearchMenus.Menu menu = ItemSearchMenus.parseTitle(title);
        assertEquals(new ItemSearchMenus.Menu(source, page, label), menu);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {"Sack of Sacks", "Sacks", "Storage", "Chest", "SkyBlock Menu", "Your Bags", "Auctions Browser"})
    void leavesOtherMenusAlone(String title) {
        assertNull(ItemSearchMenus.parseTitle(title));
    }

    @Test
    void readsWhatASackStores() {
        assertEquals(List.of(new ItemSearchMenus.Stored(null, 28183)),
                ItemSearchMenus.parseSackLore(List.of("Item Amount", "Stored: 28,183/60.5k", "", "Click to pickup!")));
        // Nothing stored is nothing to find.
        assertTrue(ItemSearchMenus.parseSackLore(List.of("Stored: 0/60.5k")).isEmpty());
    }

    @Test
    void readsGemstonesPerQuality() {
        List<ItemSearchMenus.Stored> stored = ItemSearchMenus.parseSackLore(List.of(
                " Rough: 78,999 (78,999)", " Flawed: 604 (48,320)", " Fine: 0 (0)"));
        assertEquals(List.of(new ItemSearchMenus.Stored("Rough", 78999), new ItemSearchMenus.Stored("Flawed", 604)), stored);
        assertEquals("Rough Ruby Gemstone", ItemSearchMenus.variantName("Ruby Gemstones", "Rough"));
        assertEquals("Fine Jade Gemstone", ItemSearchMenus.variantName("Rough Jade Gemstone", "Fine"));
        assertEquals("Music Rune II", ItemSearchMenus.variantName("Music Rune", "II"));
    }

    @Test
    void readsSackChanges() {
        assertEquals(new ItemSearchMenus.SackChange(12, "Enchanted Coal", "Mining Sack"),
                ItemSearchMenus.parseSackChange(" +12 Enchanted Coal (Mining Sack)"));
        assertEquals(new ItemSearchMenus.SackChange(-1204, "Fish Bait", "Bait Sack"),
                ItemSearchMenus.parseSackChange("  -1,204 Fish Bait (Bait Sack, Fishing Sack)"));
        assertNull(ItemSearchMenus.parseSackChange("Added items:"));
        assertNull(ItemSearchMenus.parseSackChange("This message can be disabled in the settings."));
    }

    @Test
    void amounts() {
        assertEquals(1204, ItemSearchMenus.parseAmount("1,204"));
        assertEquals(60500, ItemSearchMenus.parseAmount("60.5k"));
        assertEquals(2_000_000, ItemSearchMenus.parseAmount("2M"));
        assertEquals(0, ItemSearchMenus.parseAmount("lots"));
        assertEquals("999", ItemSearchMenus.shortCount(999));
        assertEquals("1.2k", ItemSearchMenus.shortCount(1234));
        assertEquals("28k", ItemSearchMenus.shortCount(28183));
        assertEquals("1m", ItemSearchMenus.shortCount(1_000_000));
    }

    @Test
    void everyWordHasToMatchNameOrLore() {
        List<String> lore = List.of("Gear Score: 620", "Ability: Wither Impact RIGHT CLICK");
        assertTrue(ItemSearchMenus.matches("", "Heroic Hyperion ✪✪✪✪✪", lore));
        assertTrue(ItemSearchMenus.matches("hyperion", "Heroic Hyperion ✪✪✪✪✪", lore));
        assertTrue(ItemSearchMenus.matches("HEROIC wither impact", "Heroic Hyperion ✪✪✪✪✪", lore));
        assertFalse(ItemSearchMenus.matches("hyperion valkyrie", "Heroic Hyperion ✪✪✪✪✪", lore));
    }
}
