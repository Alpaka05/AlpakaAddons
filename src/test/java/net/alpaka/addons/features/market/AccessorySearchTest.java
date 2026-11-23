package net.alpaka.addons.features.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessorySearchTest {
    @Test
    void knowsTheBagByItsTitle() {
        assertTrue(AccessorySearch.isBagTitle("Accessory Bag"));
        assertTrue(AccessorySearch.isBagTitle("Accessory Bag (1/6)"));
        assertFalse(AccessorySearch.isBagTitle("Accessory Bag Thaumaturgy"));
        assertFalse(AccessorySearch.isBagTitle("Auctions Browser"));
        assertFalse(AccessorySearch.isBagTitle(null));
    }
}
