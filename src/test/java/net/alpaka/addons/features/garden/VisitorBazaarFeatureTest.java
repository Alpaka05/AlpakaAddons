package net.alpaka.addons.features.garden;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VisitorBazaarFeatureTest {
    private static final List<String> MISSING_CARROTS = List.of(
            "§7Items Required:",
            " §9Enchanted Golden Carrot §8x13",
            "",
            "§7Rewards:",
            " §8+§397.4k §7Farming XP",
            " §8+§c387 Copper",
            "",
            "§cMissing items to accept!");

    @Test
    void findsTheMissingItemWithoutItsAmount() {
        assertEquals("Enchanted Golden Carrot", VisitorBazaarFeature.missingItem("§aAccept Offer", MISSING_CARROTS));
    }

    @Test
    void takesTheFirstOfSeveralItems() {
        List<String> lore = List.of("§7Items Required:", " §aEnchanted Hay Bale §8x1,280", " §9Box of Seeds", "",
                "§cMissing items to accept!");
        assertEquals("Enchanted Hay Bale", VisitorBazaarFeature.missingItem("Accept Offer", lore));
    }

    @Test
    void leavesAnOfferThatCanBeAcceptedAlone() {
        List<String> lore = List.of("§7Items Required:", " §9Enchanted Golden Carrot §8x13", "", "§eClick to give!");
        assertNull(VisitorBazaarFeature.missingItem("§aAccept Offer", lore));
    }

    @Test
    void leavesOtherItemsAlone() {
        assertNull(VisitorBazaarFeature.missingItem("§cRefuse Offer", MISSING_CARROTS));
    }
}
