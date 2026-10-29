package net.alpaka.addons.features.leapmenu

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeapMenuTest {
    @Test
    fun `head names lose their rank and colors`() {
        assertEquals("Alpaka05", LeapMenu.headName("§bAlpaka05"))
        assertEquals("Some_Name", LeapMenu.headName("[MVP+] Some_Name"))
        assertEquals("x", LeapMenu.headName("§a[VIP] §ax"))
        assertNull(LeapMenu.headName("§cGo Back To Spirit Leap"))
    }

    @Test
    fun `dead and offline teammates can't be leaped to`() {
        assertFalse(LeapMenu.unavailable(listOf("§7Click to teleport!")))
        assertTrue(LeapMenu.unavailable(listOf("§cThis player is dead!")))
        assertTrue(LeapMenu.unavailable(listOf("§7Status: §cOffline")))
    }

    @Test
    fun `both leap menu titles are recognized`() {
        assertTrue(LeapMenu.isLeapTitle("Spirit Leap"))
        assertTrue(LeapMenu.isLeapTitle("§8Teleport to Player"))
        assertFalse(LeapMenu.isLeapTitle("Spirit Leap Shop"))
    }

    @Test
    fun `classes are matched by their tab list name`() {
        assertEquals(DungeonClass.HEALER, DungeonClass.of("Healer"))
        assertEquals(DungeonClass.BERSERK, DungeonClass.of("Berserk"))
        assertNull(DungeonClass.of("DEAD"))
    }
}
