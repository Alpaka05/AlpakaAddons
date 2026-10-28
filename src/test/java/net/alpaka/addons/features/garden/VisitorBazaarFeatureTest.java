package net.alpaka.addons.features.garden;

import net.alpaka.addons.features.garden.VisitorBazaarFeature.Missing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void findsTheMissingItemAndItsAmount() {
        assertEquals(new Missing("Enchanted Cocoa Beans", 364), VisitorBazaarFeature.missing("§aAccept Offer", MISSING_COCOA));
    }

    @Test
    void takesTheFirstOfSeveralItems() {
        List<String> lore = List.of("§7Items Required:", " §aEnchanted Hay Bale §8x1,280", " §9Box of Seeds", "",
                "§cMissing items to accept!");
        assertEquals(new Missing("Enchanted Hay Bale", 1280), VisitorBazaarFeature.missing("Accept Offer", lore));
    }

    @Test
    void anItemWithoutAmountIsOne() {
        List<String> lore = List.of("§7Items Required:", " §9Box of Seeds", "", "§cMissing items to accept!");
        assertEquals(new Missing("Box of Seeds", 1), VisitorBazaarFeature.missing("Accept Offer", lore));
    }

    @Test
    void leavesAnOfferThatCanBeAcceptedAlone() {
        List<String> lore = List.of("§7Items Required:", " §9Enchanted Golden Carrot §8x13", "", "§eClick to give!");
        assertNull(VisitorBazaarFeature.missing("§aAccept Offer", lore));
    }

    @Test
    void leavesOtherItemsAlone() {
        assertNull(VisitorBazaarFeature.missing("§cRefuse Offer", MISSING_COCOA));
    }
}
