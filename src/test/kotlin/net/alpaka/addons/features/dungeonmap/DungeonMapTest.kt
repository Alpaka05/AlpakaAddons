package net.alpaka.addons.features.dungeonmap

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DungeonMapTest {
    @Test
    fun `boss transition retains an owned copy and world change discards it`() {
        val snapshot = DungeonMapSnapshot()
        val incoming = ByteArray(128 * 128).also { it[100] = 63 }
        assertTrue(snapshot.update(incoming, false))
        incoming[100] = 18
        assertEquals(63.toByte(), snapshot.colors!![100])
        assertFalse(snapshot.update(incoming, true))
        assertEquals(63.toByte(), snapshot.colors!![100])
        assertFalse(snapshot.update(ByteArray(128 * 128), false))
        assertFalse(snapshot.update(null, false))
        assertFalse(snapshot.update(ByteArray(12), false))
        snapshot.reset()
        assertNull(snapshot.colors)
        assertNull(snapshot.inks)
        assertTrue(snapshot.update(incoming, false))
        assertEquals(18.toByte(), snapshot.colors!![100])
    }

    @Test
    fun `unchanged map does not rebuild texture data`() {
        val snapshot = DungeonMapSnapshot()
        val colors = ByteArray(128 * 128).also { it[100] = 63 }
        assertTrue(snapshot.update(colors, false))
        val revision = snapshot.revision
        assertFalse(snapshot.update(colors.copyOf(), false))
        assertEquals(revision, snapshot.revision)
        colors[101] = 66
        assertTrue(snapshot.update(colors, false))
        assertTrue(snapshot.revision > revision)
    }

    @Test
    fun `room and trap brightness bits are distinct and unsigned door colors survive`() {
        val colors = ByteArray(128 * 128)
        val codes = intArrayOf(63, 62, 66, 74, 82, 85, 119, 34, 255, 0)
        codes.forEachIndexed { index, code -> colors[index] = code.toByte() }
        val inks = DungeonMapPixels.classify(colors)
        assertArrayEquals(arrayOf(DungeonMapInk.ROOM, DungeonMapInk.TRAP, DungeonMapInk.PUZZLE,
            DungeonMapInk.MINIBOSS, DungeonMapInk.FAIRY, DungeonMapInk.UNKNOWN,
            DungeonMapInk.WITHER_DOOR, DungeonMapInk.CLEARED, DungeonMapInk.ORIGINAL,
            DungeonMapInk.ORIGINAL), inks.take(codes.size).toTypedArray())
    }

    @Test
    fun `small checkmarks do not recolor entrance and blood rooms`() {
        val colors = ByteArray(128 * 128)
        for (y in 10..29) for (x in 10..29) colors[y * 128 + x] = 30
        for (y in 40..59) for (x in 40..59) colors[y * 128 + x] = 18
        colors[100 * 128 + 10] = 30
        colors[100 * 128 + 11] = 30
        colors[110 * 128 + 30] = 18
        val inks = DungeonMapPixels.classify(colors)
        assertEquals(DungeonMapInk.ENTRANCE, inks[20 * 128 + 20])
        assertEquals(DungeonMapInk.BLOOD, inks[50 * 128 + 50])
        assertEquals(DungeonMapInk.COMPLETE, inks[100 * 128 + 10])
        assertEquals(DungeonMapInk.FAILED, inks[110 * 128 + 30])
    }

    @Test
    fun `boss terrain and markers use the same projection including negative coordinates`() {
        val view = BossMapProjection(-20.0, 30.0, 128)
        assertEquals(64f, view.mapX(-20.0))
        assertEquals(64f, view.mapY(30.0))
        assertEquals(0f, view.mapX(-84.0))
        assertEquals(128f, view.mapY(94.0))
        for (i in 0 until 64) {
            assertEquals(i * 2f + 1, view.mapX(view.worldX(i).toDouble()))
            assertEquals(i * 2f + 1, view.mapY(view.worldZ(i).toDouble()))
        }
    }
}
