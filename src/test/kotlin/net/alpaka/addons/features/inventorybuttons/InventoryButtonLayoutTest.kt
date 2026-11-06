package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaConfig.InventoryButton
import net.alpaka.addons.features.inventorybuttons.InventoryButtonLayout.Box
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
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

    @AfterEach
    fun resetConfig() {
        AlpakaConfig.instance = AlpakaConfig()
    }

    private fun button(x: Int, y: Int, right: Boolean, bottom: Boolean = false) = InventoryButton().also {
        it.x = x
        it.y = y
        it.anchorRight = right
        it.anchorBottom = bottom
    }

    private fun useVanilla(buttons: List<InventoryButton>) {
        AlpakaConfig.instance.inventoryButtonsStyle = InventoryButtonsFeature.STYLE_VANILLA
        AlpakaConfig.instance.inventoryButtons = ArrayList(buttons)
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
    fun `larger vanilla buttons in a row above the panel don't run into each other`() {
        // Two rows above the inventory, the left half kept to the left edge, the right half to the right.
        val buttons = listOf(
            button(0, -40, false), button(20, -40, false), button(40, -40, false), button(60, -40, false),
            button(-96, -40, true), button(-76, -40, true),
            button(0, -20, false), button(20, -20, false), button(40, -20, false), button(60, -20, false),
            button(-96, -20, true), button(-76, -20, true),
        )
        useVanilla(buttons)
        assertNoOverlap(buttons, inventory)
        // Still clear of the panel.
        for (b in buttons) assertTrue(InventoryButtonLayout.place(b, inventory, 480, 270)[1] + InventoryButtonLayout.SIZE <= inventory.top)
    }

    @Test
    fun `the default column stays beside the panel's top with larger vanilla buttons`() {
        val buttons = InventoryButton.defaults()
        useVanilla(buttons)
        assertNoOverlap(buttons, inventory)
        val first = InventoryButtonLayout.place(buttons[0], inventory, 480, 270)
        assertTrue(first[0] + InventoryButtonLayout.SIZE <= inventory.left)
        assertTrue(first[0] + InventoryButtonLayout.SIZE >= inventory.left - 6)
        assertTrue(first[1] in inventory.top..inventory.top + 6)
    }

    @Test
    fun `a row running past the panel's corner and a column beside it keep apart`() {
        val buttons = listOf(
            button(140, -20, false), button(160, -20, false), button(180, -20, false),
            button(4, 0, true), button(4, 20, true), button(4, -20, true),
        )
        useVanilla(buttons.take(3))
        assertNoOverlap(buttons.take(3), inventory)
        useVanilla(buttons.drop(3))
        assertNoOverlap(buttons.drop(3), inventory)
    }

    @Test
    fun `a vanilla button comes back within a pixel of where it was put`() {
        val buttons = InventoryButton.defaults()
        useVanilla(buttons)
        for ((x, y) in listOf(100 to 20, 120 to 30, 300 to 200, 340 to 60, 130 to 120)) {
            val b = InventoryButton()
            InventoryButtonLayout.store(b, x, y, inventory)
            // Positions are stored at the smaller base size, so a larger button can be off by one.
            val p = InventoryButtonLayout.place(b, inventory, 480, 270)
            assertTrue(kotlin.math.abs(p[0] - x) <= 1 && kotlin.math.abs(p[1] - y) <= 1, "at $x, $y: ${p.toList()}")
        }
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
