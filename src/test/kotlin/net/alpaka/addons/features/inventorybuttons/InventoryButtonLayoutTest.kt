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
        val left = inventory.left - InventoryButtonLayout.SIZE - InventoryButtonLayout.GAP_X
        val below = 70 + InventoryButtonLayout.SIZE + InventoryButtonLayout.GAP_Y
        val s = InventoryButtonLayout.snap(left + 3, below - 3, inventory, listOf(intArrayOf(left, 70)))
        assertEquals(left, s[0])
        assertEquals(below, s[1])
        assertEquals(1, s[2])
        assertEquals(1, s[3])
    }

    @Test
    fun `a full row and a full column end flush with the inventory`() {
        val size = InventoryButtonLayout.SIZE
        // Six side by side from the left edge end at the right edge.
        assertEquals(inventory.width, 6 * size + 5 * InventoryButtonLayout.GAP_X)
        // Six stacked from the top edge end at the bottom edge.
        assertEquals(inventory.height, 6 * size + 5 * InventoryButtonLayout.GAP_Y)
    }

    @Test
    fun `a button snapped next to the last of five above the panel ends at its right edge`() {
        val step = InventoryButtonLayout.SIZE + InventoryButtonLayout.GAP_X
        val y = inventory.top - InventoryButtonLayout.SIZE - InventoryButtonLayout.GAP_Y
        val row = (0 until 5).map { intArrayOf(inventory.left + it * step, y) }
        val s = InventoryButtonLayout.snap(inventory.left + 5 * step + 2, y, inventory, row)
        assertEquals(inventory.right - InventoryButtonLayout.SIZE, s[0])
    }

    @Test
    fun `a 22 px row and column keep their places in the grid`() {
        // A row flush with the left edge above the panel, and the old default column beside it.
        fun button(x: Int, y: Int) = InventoryButton().also { it.x = x; it.y = y; it.grid = 22 }
        val row = (0 until 3).map { button(it * 24, -24) }
        val column = (0 until 3).map { button(-26, 4 + it * 24) }
        assertTrue(InventoryButtonLayout.migrate(row + column))
        val size = InventoryButtonLayout.SIZE
        val r = row.map { InventoryButtonLayout.place(it, inventory, 480, 270) }
        assertEquals(listOf(0, 30, 60), r.map { it[0] - inventory.left })
        assertEquals(inventory.top - size - InventoryButtonLayout.GAP_Y, r[0][1])
        val c = column.map { InventoryButtonLayout.place(it, inventory, 480, 270) }
        assertEquals(inventory.left - size - InventoryButtonLayout.GAP_X, c[0][0])
        assertEquals(listOf(4, 32, 60), c.map { it[1] - inventory.top })
    }

    @Test
    fun `over the panel a button still snaps flush with its edge`() {
        val s = InventoryButtonLayout.snap(inventory.left + 2, 100, inventory, emptyList())
        assertEquals(inventory.left, s[0])
    }

    @Test
    fun `nothing near means no snap`() {
        val s = InventoryButtonLayout.snap(20, 10, inventory, emptyList())
        assertArrayEquals(intArrayOf(20, 10, 0, 0), s)
    }

    /** A button as a settings file from before the 22 px buttons has it. */
    private fun oldButton(x: Int, y: Int, right: Boolean, bottom: Boolean = false) = InventoryButton().also {
        it.x = x
        it.y = y
        it.anchorRight = right
        it.anchorBottom = bottom
        it.grid = InventoryButtonLayout.OLD_SIZE
    }

    private fun assertNoOverlap(buttons: List<InventoryButton>, box: Box, screenH: Int = 270) {
        val size = InventoryButtonLayout.SIZE
        val places = buttons.map { InventoryButtonLayout.place(it, box, 480, screenH) }
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
        // Two rows of today's buttons need 56 px above the panel; the smallest screen (480 by 270)
        // leaves 52, so this looks at one a little taller.
        val box = Box(152, 62, 176, 166)
        assertNoOverlap(buttons, box, 290)
        for (b in buttons) {
            assertEquals(InventoryButtonLayout.SIZE, b.grid)
            assertTrue(InventoryButtonLayout.place(b, box, 480, 290)[1] + InventoryButtonLayout.SIZE <= box.top)
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
}
