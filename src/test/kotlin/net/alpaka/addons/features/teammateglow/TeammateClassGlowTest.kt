package net.alpaka.addons.features.teammateglow

import net.alpaka.addons.features.dungeonmap.DungeonParty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TeammateClassGlowTest {
    @AfterEach
    fun forget() = TeammateClassGlow.reset()

    private fun party(vararg lines: String) = DungeonParty.parseAll(lines.toList())

    @Test
    fun `each teammate gets the color of their class`() {
        TeammateClassGlow.remember(party("[303] AzureAaron ⚡ (Mage XL)", "[220] [MVP+] Name ♲ (Tank V)",
            "[12] Some_One (Archer 50)", "[1] Zero (Healer 0)", "[400] Bers (Berserk XX)"))
        assertEquals(0xFF55FFFF.toInt(), TeammateClassGlow.colorOf("AzureAaron"))
        assertEquals(0xFF2EB82E.toInt(), TeammateClassGlow.colorOf("Name"))
        assertEquals(0xFFFFAA00.toInt(), TeammateClassGlow.colorOf("Some_One"))
        assertEquals(0xFFFF55FF.toInt(), TeammateClassGlow.colorOf("Zero"))
        assertEquals(0xFFD83A3A.toInt(), TeammateClassGlow.colorOf("Bers"))
    }

    @Test
    fun `names match whatever their case`() {
        TeammateClassGlow.remember(party("[12] Some_One (Mage XL)"))
        assertEquals(0xFF55FFFF.toInt(), TeammateClassGlow.colorOf("some_one"))
    }

    @Test
    fun `a dead teammate keeps the class read before`() {
        TeammateClassGlow.remember(party("[12] Ghost (Healer XL)"))
        TeammateClassGlow.remember(party("[12] Ghost (DEAD)"))
        assertEquals(0xFFFF55FF.toInt(), TeammateClassGlow.colorOf("Ghost"))
    }

    @Test
    fun `a glowing teammate gets their class color and anyone else keeps theirs`() {
        TeammateClassGlow.remember(party("[1] Zero (Healer 0)"))
        assertEquals(0xFFFF55FF.toInt(), TeammateClassGlow.recolor(0xFFFFFFFF.toInt(), "Zero"))
        assertEquals(0xFFFFAA00.toInt(), TeammateClassGlow.recolor(0xFFFFAA00.toInt(), "Stranger"))
    }

    @Test
    fun `a player without an outline never gets one`() {
        TeammateClassGlow.remember(party("[1] Zero (Healer 0)"))
        assertEquals(0, TeammateClassGlow.recolor(0, "Zero"))
    }

    @Test
    fun `no color before the run gives classes, or for someone not in the party`() {
        TeammateClassGlow.remember(party("[303] AzureAaron ⚡ (EMPTY)"))
        assertNull(TeammateClassGlow.colorOf("AzureAaron"))
        assertNull(TeammateClassGlow.colorOf("Stranger"))
    }
}
