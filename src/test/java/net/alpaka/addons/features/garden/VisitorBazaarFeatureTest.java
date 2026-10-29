package net.alpaka.addons.features.garden;

import net.alpaka.addons.features.garden.VisitorBazaarFeature.Pick;
import net.alpaka.addons.features.garden.VisitorBazaarFeature.Required;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitorBazaarFeatureTest {
    private static final List<String> MISSING_COCOA = List.of(
            "§7Items Required:",
            " §aEnchanted Cocoa Beans §8x364",
            "",
            "§7Rewards:",
            " §8+§311.6k §7Farming XP",
            " §8+§c24 Copper",
            "",
            "§cMissing items to accept!");

    private static final List<Required> TWO = List.of(
            new Required("Enchanted Hay Bale", 1280), new Required("Box of Seeds", 3));

    @Test
    void readsTheRequiredItemsAndAmounts() {
        assertEquals(List.of(new Required("Enchanted Cocoa Beans", 364)),
                VisitorBazaarFeature.offer("§aAccept Offer", MISSING_COCOA));
        List<String> lore = List.of("§7Items Required:", " §aEnchanted Hay Bale §8x1,280", " §9Box of Seeds §8x3", "",
                "§cMissing items to accept!");
        assertEquals(TWO, VisitorBazaarFeature.offer("Accept Offer", lore));
    }

    @Test
    void anItemWithoutAmountIsOne() {
        List<String> lore = List.of("§7Items Required:", " §9Box of Seeds", "", "§cMissing items to accept!");
        assertEquals(List.of(new Required("Box of Seeds", 1)), VisitorBazaarFeature.offer("Accept Offer", lore));
    }

    @Test
    void leavesAnOfferThatCanBeAcceptedAlone() {
        List<String> lore = List.of("§7Items Required:", " §9Enchanted Golden Carrot §8x13", "", "§eClick to give!");
        assertNull(VisitorBazaarFeature.offer("§aAccept Offer", lore));
    }

    @Test
    void leavesOtherItemsAlone() {
        assertNull(VisitorBazaarFeature.offer("§cRefuse Offer", MISSING_COCOA));
    }

    @Test
    void opensTheFirstItemAtFirst() {
        assertEquals(new Pick("Enchanted Hay Bale", 1280), VisitorBazaarFeature.pick(TWO, item -> 0));
    }

    @Test
    void skipsAnItemTheInventoryHoldsEnoughOf() {
        Map<String, Integer> held = Map.of("Enchanted Hay Bale", 1280);
        assertEquals(new Pick("Box of Seeds", 3), VisitorBazaarFeature.pick(TWO, item -> held.getOrDefault(item, 0)));
    }

    @Test
    void copiesOnlyWhatIsStillNeeded() {
        Map<String, Integer> held = Map.of("Enchanted Hay Bale", 1000);
        assertEquals(new Pick("Enchanted Hay Bale", 280), VisitorBazaarFeature.pick(TWO, item -> held.getOrDefault(item, 0)));
    }

    @Test
    void staysOnTheFirstItemUntilTheInventoryHoldsEnough() {
        Map<String, Integer> held = Map.of("Enchanted Hay Bale", 1279);
        assertEquals(new Pick("Enchanted Hay Bale", 1), VisitorBazaarFeature.pick(TWO, item -> held.getOrDefault(item, 0)));
    }

    @Test
    void opensTheFirstItemWhenTheInventoryHoldsEverything() {
        assertEquals(new Pick("Enchanted Hay Bale", 1280), VisitorBazaarFeature.pick(TWO, item -> 5000));
    }

    @Test
    void readsAnIronmanProfileFromTheTabList() {
        assertTrue(VisitorBazaarFeature.isIronman(List.of("Info", "Profile: Kiwi ♲"), "Alpaka05"));
        assertTrue(VisitorBazaarFeature.isIronman(List.of("[312] Alpaka05 ♲"), "Alpaka05"));
    }

    @Test
    void aNormalProfileIsNoIronman() {
        assertFalse(VisitorBazaarFeature.isIronman(List.of("Profile: Kiwi", "[312] Alpaka05"), "Alpaka05"));
        // Someone else on the island playing Ironman doesn't make the own profile one.
        assertFalse(VisitorBazaarFeature.isIronman(List.of("Profile: Kiwi", "[188] Notch ♲", "[312] Alpaka05"), "Alpaka05"));
        assertFalse(VisitorBazaarFeature.isIronman(List.of("[188] Alpaka05x ♲", "[312] Alpaka05"), "Alpaka05"));
    }
}
