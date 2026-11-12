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

    /** A button as a settings file from before the 22 px buttons has it. */
    private fun oldButton(x: Int, y: Int, right: Boolean, bottom: Boolean = false) = InventoryButton().also {
        it.x = x
        it.y = y
        it.anchorRight = right
        it.anchorBottom = bottom
        it.grid = InventoryButtonLayout.OLD_SIZE
    }

    private fun assertNoOverlap(buttons: List<InventoryButton>, box: Box) {
        val size = InventoryButtonLayout.SIZE
        val places = buttons.map { InventoryButtonLayout.place(it, box, 480, 270) }
        for (i in places.indices) for (j in i + 1 until places.size) {
            val a = places[i]
            val b = places[j]
            val overlap = a[0] < b[0] + size && b[0] < a[0] + size && a[1] < b[1] + size && b[1] < a[1] + size
            assertFalse(overlap, "buttons $i and $j overlap at ${a.toList()} and ${b.toList()}")
        }
    }

    @Test
    fun `old rows above the panel move apart without running into each other`() {
        // Two rows above the inventory, the left half kept to the left edge, the right half to the right.
        val buttons = listOf(
            oldButton(0, -40, false), oldButton(20, -40, false), oldButton(40, -40, false), oldButton(60, -40, false),
            oldButton(-96, -40, true), oldButton(-76, -40, true),
            oldButton(0, -20, false), oldButton(20, -20, false), oldButton(40, -20, false), oldButton(60, -20, false),
            oldButton(-96, -20, true), oldButton(-76, -20, true),
        )
        assertTrue(InventoryButtonLayout.migrate(buttons))
        assertNoOverlap(buttons, inventory)
        for (b in buttons) {
            assertEquals(InventoryButtonLayout.SIZE, b.grid)
            assertTrue(InventoryButtonLayout.place(b, inventory, 480, 270)[1] + InventoryButtonLayout.SIZE <= inventory.top)
        }
        assertFalse(InventoryButtonLayout.migrate(buttons), "moved only once")
    }

    @Test
    fun `the old default column stays beside the panel's top`() {
        val old = (0 until 5).map { oldButton(-22, 4 + it * 20, false) }
        InventoryButtonLayout.migrate(old)
        assertNoOverlap(old, inventory)
        val first = InventoryButtonLayout.place(old[0], inventory, 480, 270)
        assertTrue(first[0] + InventoryButtonLayout.SIZE <= inventory.left)
        assertTrue(first[0] + InventoryButtonLayout.SIZE >= inventory.left - 6)
        assertTrue(first[1] in inventory.top..inventory.top + 6)
    }

    @Test
    fun `a row running past the panel's corner and a column beside it keep apart`() {
        val row = listOf(oldButton(140, -20, false), oldButton(160, -20, false), oldButton(180, -20, false))
        InventoryButtonLayout.migrate(row)
        assertNoOverlap(row, inventory)
        val column = listOf(oldButton(4, 0, true), oldButton(4, 20, true), oldButton(4, -20, true))
        InventoryButtonLayout.migrate(column)
        assertNoOverlap(column, inventory)
    }

    @Test
    fun `new buttons are never moved`() {
        val buttons = InventoryButton.defaults()
        val before = buttons.map { it.x to it.y }
        assertFalse(InventoryButtonLayout.migrate(buttons))
        assertEquals(before, buttons.map { it.x to it.y })
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

    @Test
    fun `a row above the panel is centred on it`() {
        val step = InventoryButtonLayout.SIZE + InventoryButtonLayout.GAP
        // Three buttons flush with the panel's left edge, above it.
        val y = inventory.top - InventoryButtonLayout.SIZE - InventoryButtonLayout.GAP
        val positions = (0 until 3).map { intArrayOf(inventory.left + it * step, y) }
        val row = InventoryButtonLayout.rowOf(positions, 1)
        assertEquals(listOf(0, 1, 2), row)
        val shift = InventoryButtonLayout.centerShift(positions, row, inventory)
        val left = positions[0][0] + shift
        val right = positions[2][0] + shift + InventoryButtonLayout.SIZE
        assertEquals(left - inventory.left, inventory.right - right)
    }

    @Test
    fun `a button further along is not part of the row`() {
        val y = inventory.bottom + InventoryButtonLayout.GAP
        val positions = listOf(intArrayOf(160, y), intArrayOf(184, y), intArrayOf(260, y))
        assertEquals(listOf(0, 1), InventoryButtonLayout.rowOf(positions, 0))
        assertEquals(listOf(2), InventoryButtonLayout.rowOf(positions, 2))
    }

    @Test
    fun `a single button and a column beside the panel stay where they are`() {
        val above = listOf(intArrayOf(inventory.left, inventory.top - 30))
        assertEquals(0, InventoryButtonLayout.centerShift(above, listOf(0), inventory))
        // Two buttons side by side left of the panel, level with it.
        val beside = listOf(intArrayOf(100, 80), intArrayOf(124, 80))
        assertEquals(0, InventoryButtonLayout.centerShift(beside, listOf(0, 1), inventory))
    }
}
