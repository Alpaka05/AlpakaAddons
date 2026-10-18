package net.alpaka.addons.features.inventoryhud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InventoryHudVisibilityTest {

    @Test
    fun `always visible wins over item change`() {
        assertEquals(InventoryHudVisibility.ALWAYS, InventoryHudVisibility.fromLegacy(true, true))
        assertEquals(InventoryHudVisibility.ALWAYS, InventoryHudVisibility.fromLegacy(true, false))
    }

    @Test
    fun `item change alone`() {
        assertEquals(InventoryHudVisibility.ITEM_CHANGE, InventoryHudVisibility.fromLegacy(false, true))
        assertEquals(InventoryHudVisibility.ITEM_CHANGE, InventoryHudVisibility.fromLegacy(null, true))
    }

    @Test
    fun `both off means key press`() {
        assertEquals(InventoryHudVisibility.KEY_PRESS, InventoryHudVisibility.fromLegacy(false, false))
    }

    @Test
    fun `names line up with the indices`() {
        assertEquals("Always Visible", InventoryHudVisibility.NAMES[InventoryHudVisibility.ALWAYS])
        assertEquals("On Item Change", InventoryHudVisibility.NAMES[InventoryHudVisibility.ITEM_CHANGE])
        assertEquals("On Key Press", InventoryHudVisibility.NAMES[InventoryHudVisibility.KEY_PRESS])
    }
}
