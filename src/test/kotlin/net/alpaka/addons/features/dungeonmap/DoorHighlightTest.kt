package net.alpaka.addons.features.dungeonmap

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DoorHighlightTest {
    /** A big floor's grid: 16 px rooms from 5 px, cell i centred on 13 + 20i. */
    private fun calibration() = DungeonMapCalibration().apply {
        val inks = Array(DungeonMapPixels.SIZE) { DungeonMapInk.ORIGINAL }
        for (y in 65 until 81) for (x in 45 until 61) inks[y * 128 + x] = DungeonMapInk.ENTRANCE
        assertTrue(measureRooms(inks))
    }

    @Test
    fun `wither and blood doors are read off the gaps between rooms`() {
        val colors = ByteArray(DungeonMapPixels.SIZE)
        // Between cells (0, 0) and (1, 0): black, a wither door. Between (1, 1) and (1, 2): blood red.
        colors[13 * 128 + 23] = 119.toByte()
        colors[43 * 128 + 33] = 18
        // A normal brown door is no door to mark.
        colors[13 * 128 + 43] = 63
        val doors = DoorHighlight.find(colors, calibration())
        assertEquals(2, doors.size)
        val wither = doors.single { !it.blood }
        assertEquals(-169 to -185, wither.x to wither.z)
        val blood = doors.single { it.blood }
        assertEquals(-153 to -137, blood.x to blood.z)
        assertTrue(DoorHighlight.find(colors, DungeonMapCalibration()).isEmpty())
    }

    @Test
    fun `keys come and go with the chat`() {
        DoorHighlight.reset()
        DoorHighlight.onChat("[MVP+] Alpaka05 has obtained Wither Key!")
        DoorHighlight.onChat("A Wither Key was picked up!")
        assertEquals(2, DoorHighlight.witherKeys)
        DoorHighlight.onChat("Alpaka05 opened a WITHER door!")
        assertEquals(1, DoorHighlight.witherKeys)
        assertFalse(DoorHighlight.bloodKey)
        DoorHighlight.onChat("Someone has obtained Blood Key!")
        assertTrue(DoorHighlight.bloodKey)
        DoorHighlight.onChat("The BLOOD DOOR has been opened!")
        assertFalse(DoorHighlight.bloodKey)
        DoorHighlight.reset()
    }

    @Test
    fun `classes have the Leap Menu's colors`() {
        assertEquals(0xFFFF55FF.toInt(), DungeonParty.classColor("Healer"))
        assertEquals(0xFF55FFFF.toInt(), DungeonParty.classColor("Mage"))
        assertNull(DungeonParty.classColor("DEAD"))
        assertNull(DungeonParty.classColor(null))
    }
}
