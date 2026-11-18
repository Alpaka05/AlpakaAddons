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
        assertEquals(7, InventoryButtonLayout.columns(inventory))
        assertEquals(inventory.left, InventoryButtonLayout.columnX(inventory, 0))
        assertEquals(inventory.right, InventoryButtonLayout.columnX(inventory, 6) + size)
        assertEquals(inventory.bottom, InventoryButtonLayout.rowY(inventory, 6) + size)
        // Every gap across is 3 or 4 px, and the columns beside the panel keep 4 px to it.
        val gaps = (0 until 6).map { InventoryButtonLayout.columnX(inventory, it + 1) - InventoryButtonLayout.columnX(inventory, it) - size }
        assertTrue(gaps.all { it == 3 || it == 4 }, gaps.toString())
        assertEquals(inventory.left - size - 4, InventoryButtonLayout.columnX(inventory, -1))
        assertEquals(inventory.right + 4, InventoryButtonLayout.columnX(inventory, 7))
    }

    @Test
    fun `a button dropped at the end of a row above the panel ends at its right edge`() {
        val y = inventory.top - InventoryButtonLayout.SIZE - InventoryButtonLayout.GAP_Y
        val s = InventoryButtonLayout.snap(inventory.right - InventoryButtonLayout.SIZE - 2, y, inventory, emptyList())
        assertEquals(inventory.right - InventoryButtonLayout.SIZE, s[0])
    }

    @Test
    fun `older rows and columns land in the same cells of the new grid`() {
        fun button(grid: Int, x: Int, y: Int) = InventoryButton().also { it.x = x; it.y = y; it.grid = grid }
        // A row of seven flush with the left edge above the panel, and the default column beside it,
        // as the 22 px buttons had them, and the same row as the 26 px buttons had it, 5 px in.
        val row22 = (0 until 7).map { button(22, it * 24, -24) }
        val column22 = (0 until 3).map { button(22, -26, 4 + it * 24) }
        val row26 = (0 until 6).map { button(26, 5 + it * 30, -56) }
        // The 22 px row of seven after its move to 26 px: the last one past the right edge.
        val row26wide = (0 until 7).map { button(26, it * 30, -84) }
        assertTrue(InventoryButtonLayout.migrate(row22 + column22 + row26 + row26wide))
        assertEquals(
            (0 until 7).map { InventoryButtonLayout.columnX(inventory, it) },
            row26wide.map { InventoryButtonLayout.place(it, inventory, 480, 400)[0] },
        )
        val cols = (0 until 7).map { InventoryButtonLayout.columnX(inventory, it) }
        val r = row22.map { InventoryButtonLayout.place(it, inventory, 480, 270) }
        assertEquals(cols, r.map { it[0] })
        assertTrue(r.all { it[1] == InventoryButtonLayout.rowY(inventory, -1) })
        val c = column22.map { InventoryButtonLayout.place(it, inventory, 480, 270) }
        assertTrue(c.all { it[0] == InventoryButtonLayout.columnX(inventory, -1) })
        assertEquals((0 until 3).map { InventoryButtonLayout.rowY(inventory, it) }, c.map { it[1] })
        val r26 = row26.map { InventoryButtonLayout.place(it, inventory, 480, 270) }
        assertEquals(cols.take(6), r26.map { it[0] })
        assertTrue(r26.all { it[1] == InventoryButtonLayout.rowY(inventory, -2) })
        assertNoOverlap(row22 + column22 + row26, inventory)
    }

    @Test
    fun `over the panel a button still snaps flush with its edge`() {
        val s = InventoryButtonLayout.snap(inventory.left + 2, 100, inventory, emptyList())
        assertEquals(inventory.left, s[0])
    }

    @Test
    fun `nothing near means no snap`() {
        // The grid's columns go on across the whole screen; 11 is halfway between two of them.
        val s = InventoryButtonLayout.snap(11, 10, inventory, emptyList())
        assertArrayEquals(intArrayOf(11, 10, 0, 0), s)
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
}
