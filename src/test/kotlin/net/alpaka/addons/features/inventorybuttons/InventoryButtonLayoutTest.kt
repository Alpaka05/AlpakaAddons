package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.config.AlpakaConfig.InventoryButton
import net.alpaka.addons.features.inventorybuttons.InventoryButtonLayout.Box
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Where inventory buttons land, and how they keep their place between panels of different size. */
class InventoryButtonLayoutTest {
    private val inventory = Box(152, 52, 176, 166)
    private val largeChest = Box(152, 24, 176, 222)

    @Test
    fun `a stored position comes back where it was put`() {
        val button = InventoryButton()
        InventoryButtonLayout.store(button, 100, 70, inventory)
        assertArrayEquals(intArrayOf(100, 70), InventoryButtonLayout.place(button, inventory, 480, 270))
    }

    @Test
    fun `a button beside the player's slots stays beside them in a taller chest`() {
        val button = InventoryButton()
        // Left of the bottom of the inventory, next to the hotbar.
        InventoryButtonLayout.store(button, 130, 200, inventory)
        assertFalse(button.anchorRight)
        assertTrue(button.anchorBottom)
        val p = InventoryButtonLayout.place(button, largeChest, 480, 270)
        assertEquals(inventory.bottom - 200, largeChest.bottom - p[1])
    }

    @Test
    fun `a button right of the panel keeps to its right edge`() {
        val button = InventoryButton()
        InventoryButtonLayout.store(button, 332, 60, inventory)
        assertTrue(button.anchorRight)
        assertEquals(4, button.x)
        assertFalse(button.anchorBottom)
    }

    @Test
    fun `a button is kept on the screen`() {
        val button = InventoryButton()
        button.x = -500
        button.y = -500
        assertArrayEquals(intArrayOf(0, 0), InventoryButtonLayout.place(button, inventory, 480, 270))
    }

    @Test
    fun `snapping lines a button up just outside the panel and next to another`() {
        val left = inventory.left - InventoryButtonLayout.SIZE - InventoryButtonLayout.GAP
        val s = InventoryButtonLayout.snap(left + 3, 90, inventory, listOf(intArrayOf(left, 70)))
        assertEquals(left, s[0])
        assertEquals(70 + InventoryButtonLayout.SIZE + InventoryButtonLayout.GAP, s[1])
        assertEquals(1, s[2])
        assertEquals(1, s[3])
    }

    @Test
    fun `nothing near means no snap`() {
        val s = InventoryButtonLayout.snap(20, 20, inventory, emptyList())
        assertArrayEquals(intArrayOf(20, 20, 0, 0), s)
    }

    @Test
    fun `the icon search matches every word`() {
        val all = listOf("minecraft:ender_chest", "minecraft:chest", "minecraft:ender_pearl")
        assertEquals(listOf("minecraft:ender_chest"), InventoryButtonIcons.search(all, "ender chest"))
        assertEquals(all, InventoryButtonIcons.search(all, " "))
    }

    @Test
    fun `the icon search finds SkyBlock ids whatever their case`() {
        val all = listOf("skyblock:ASPECT_OF_THE_VOID", "skyblock:HYPERION", "minecraft:ender_pearl")
        assertEquals(listOf("skyblock:ASPECT_OF_THE_VOID"), InventoryButtonIcons.search(all, "aspect void"))
        assertEquals("HYPERION", InventoryButtonIcons.path("skyblock:HYPERION"))
    }
}
