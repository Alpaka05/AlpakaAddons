package net.alpaka.addons.features.dungeonmap

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DungeonMapPlayersTest {
    @Test
    fun `party lines give name and class with ranks, emblems and dead players`() {
        assertEquals(PartyMember("AzureAaron", "EMPTY"), DungeonParty.parse("[303] AzureAaron ⚡ (EMPTY)"))
        assertEquals(PartyMember("Some_One", "Mage"), DungeonParty.parse("  [12] Some_One (Mage XL)  "))
        assertEquals(PartyMember("Name", "Tank"), DungeonParty.parse("[220] [MVP+] Name ♲ (Tank V)"))
        assertEquals(PartyMember("Ghost", "DEAD"), DungeonParty.parse("[99] Ghost (DEAD)"))
        assertEquals(PartyMember("Zero", "Healer"), DungeonParty.parse("[1] Zero (Healer 0)"))
        assertEquals(PartyMember("Arabic", "Archer"), DungeonParty.parse("[400] Arabic (Archer 50)"))
        assertFalse(DungeonParty.parse("[99] Ghost (DEAD)")!!.alive)
        assertFalse(DungeonParty.parse("[303] AzureAaron ⚡ (EMPTY)")!!.alive)
        assertTrue(DungeonParty.parse("[12] Some_One (Mage XL)")!!.alive)
        assertNull(DungeonParty.parse("Party (5)"))
        assertNull(DungeonParty.parse(" Ultimate: READY"))
        assertNull(DungeonParty.parse("[12] Someone (Crypts 3)"))
    }

    @Test
    fun `party keeps the tab order and each name once`() {
        val members = DungeonParty.parseAll(listOf("Party (3)", "[1] B (Mage I)", " Ultimate: READY", "[2] A (DEAD)",
            "[3] C (Tank II)", "[1] b (Mage I)"))
        assertEquals(listOf("B", "A", "C"), members.map { it.name })
    }

    /** A blank map with the entrance room drawn at [left], [top], [size] wide. */
    private fun entrance(left: Int, top: Int, size: Int): Array<DungeonMapInk> {
        val inks = Array(DungeonMapPixels.SIZE) { DungeonMapInk.ORIGINAL }
        for (y in top until top + size) for (x in left until left + size) inks[y * 128 + x] = DungeonMapInk.ENTRANCE
        // The door into the entrance, 4 px wide, must not be taken for the room.
        for (y in top - 4 until top) for (x in left + 6 until left + 10) inks[y * 128 + x] = DungeonMapInk.ENTRANCE
        return inks
    }

    @Test
    fun `room centers land on drawn room centers on big floors`() {
        val calibration = DungeonMapCalibration()
        assertFalse(calibration.ready)
        // F5 to F7: the first room at 5 px, 16 px rooms, the entrance in cell (2, 3).
        assertTrue(calibration.measureRooms(entrance(45, 65, 16)))
        assertTrue(calibration.ready)
        assertEquals(0.625, calibration.scale)
        assertEquals(53f, calibration.mapX(-185.0 + 2 * 32), 1e-4f)
        assertEquals(73f, calibration.mapY(-185.0 + 3 * 32), 1e-4f)
        assertEquals(13f, calibration.mapX(-185.0), 1e-4f)
    }

    @Test
    fun `small floors start their first room at 22 px`() {
        val calibration = DungeonMapCalibration()
        // The entrance room is in cell (1, 0); 44 % 22 is 0, which means 22 here.
        assertTrue(calibration.measureRooms(entrance(44, 22, 18)))
        assertEquals(22 / 32.0, calibration.scale)
        assertEquals(53f, calibration.mapX(-153.0), 1e-4f)
        assertEquals(31f, calibration.mapY(-185.0), 1e-4f)
    }

    @Test
    fun `own marker corrects the room rule only when it disagrees clearly`() {
        val close = DungeonMapCalibration().apply { measureRooms(entrance(45, 65, 16)) }
        repeat(10) { close.observe(-121.0, -89.0, 53.5f, 73.5f) }
        assertEquals(53f, close.mapX(-121.0), 1e-4f)

        val off = DungeonMapCalibration().apply { measureRooms(entrance(45, 65, 16)) }
        repeat(10) { off.observe(-121.0, -89.0, 63f, 73f) }
        assertEquals(63f, off.mapX(-121.0), 1e-4f)
        assertEquals(73f, off.mapY(-89.0), 1e-4f)
    }

    @Test
    fun `without an entrance the marker alone calibrates, and stray readings are ignored`() {
        val calibration = DungeonMapCalibration()
        calibration.observe(0.0, 0.0, 10f, 10f)
        assertFalse(calibration.ready)
        calibration.assumeFloor("F2")
        assertEquals(22 / 32.0, calibration.scale)
        calibration.observe(-100.0, -100.0, 40f, 50f)
        assertTrue(calibration.ready)
        assertEquals(40f + 32 * 22 / 32f, calibration.mapX(-68.0), 1e-4f)
        repeat(9) { calibration.observe(-100.0, -100.0, 40f, 50f) }
        calibration.observe(-100.0, -100.0, 90f, 50f)
        assertEquals(40f, calibration.mapX(-100.0), 1e-4f)
        // A floor guess never replaces a measured scale.
        calibration.assumeFloor("F7")
        assertEquals(22 / 32.0, calibration.scale)
    }

    @Test
    fun `markers pair with teammates in tab order`() {
        val markers = listOf(MapMarker(10f, 10f, 0f), MapMarker(50f, 50f, 0f))
        assertEquals(mapOf(0 to "A", 1 to "B"), MarkerAssignment.assign(markers, listOf("A", "B", "C"), emptyMap()))
    }

    @Test
    fun `teammates in range claim the marker beside them, the rest keep their order`() {
        val markers = listOf(MapMarker(10f, 10f, 0f), MapMarker(50f, 50f, 0f), MapMarker(90f, 90f, 0f))
        // The tab says A, B, C, but B stands at the first marker.
        val owners = MarkerAssignment.assign(markers, listOf("A", "B", "C"), mapOf("B" to MapMarker(11f, 12f, 0f)))
        assertEquals(mapOf(0 to "B", 1 to "A", 2 to "C"), owners)
        // Without a marker next to B the live positions are not trusted, and the order decides.
        val far = MarkerAssignment.assign(markers, listOf("A", "B", "C"), mapOf("B" to MapMarker(30f, 120f, 0f)))
        assertEquals(mapOf(0 to "A", 1 to "B", 2 to "C"), far)
    }

    @Test
    fun `markers glide at the pace they arrive in`() {
        val track = MarkerTrack()
        track.glide(MapMarker(10f, 10f, 0f), 0)
        assertEquals(10f, track.x)
        track.glide(MapMarker(12f, 10f, 0f), 100)
        track.glide(MapMarker(12f, 10f, 0f), 150)
        assertEquals(11f, track.x, 1e-4f)
        track.glide(MapMarker(12f, 10f, 0f), 200)
        assertEquals(12f, track.x, 1e-4f)
        // A long pause is not stretched over the next step.
        track.glide(MapMarker(14f, 10f, 0f), 5000)
        track.glide(MapMarker(14f, 10f, 0f), 5150)
        assertEquals(13f, track.x, 1e-4f)
    }

    @Test
    fun `rotation takes the short way and teleports are not animated`() {
        val track = MarkerTrack()
        track.glide(MapMarker(10f, 10f, 337.5f), 0)
        track.glide(MapMarker(10f, 10f, 22.5f), 100)
        track.glide(MapMarker(10f, 10f, 22.5f), 150)
        assertEquals(0f, ((track.yaw % 360f) + 360f) % 360f, 1e-3f)
        track.glide(MapMarker(100f, 100f, 22.5f), 300)
        assertEquals(100f, track.x)
    }

    @Test
    fun `switching between live position and marker blends instead of jumping`() {
        val track = MarkerTrack()
        track.glide(MapMarker(0f, 0f, 0f), 0)
        track.follow(10f, 0f, 0f, 1000)
        assertEquals(0f, track.x, 1e-4f)
        track.follow(10f, 0f, 0f, 1075)
        assertEquals(5f, track.x, 1e-4f)
        track.follow(12f, 0f, 0f, 1150)
        assertEquals(12f, track.x, 1e-4f)
        track.glide(MapMarker(4f, 0f, 0f), 1200)
        assertEquals(12f, track.x, 1e-4f)
        track.glide(MapMarker(4f, 0f, 0f), 1350)
        assertEquals(4f, track.x, 1e-4f)
    }

    @Test
    fun `a player turning during the blend never makes the head spin the other way`() {
        val track = MarkerTrack()
        track.glide(MapMarker(0f, 0f, 0f), 0)
        // Facing 0 on the map, the live player at 170 and turning fast past 180.
        var last = 0f
        for (step in 0..15) {
            val live = 170f + step * 20f
            track.follow(0f, 0f, live, 1000L + step * 10)
            assertTrue(kotlin.math.abs(track.yaw - last) < 60f, "jumped from $last to ${track.yaw}")
            last = track.yaw
        }
        assertEquals(470f, track.yaw, 1e-3f)
    }

    @Test
    fun `boss terrain is sampled with a margin around the view`() {
        val view = BossMapProjection(0.0, 0.0, 128)
        assertEquals(192, BossMapProjection.TEXTURE)
        // Sample -16 sits 32 px left of the view, the last one 32 px right of it.
        assertEquals(-31f, view.mapX(view.worldX(-BossMapProjection.MARGIN).toDouble()))
        assertEquals(159f, view.mapX(view.worldX(BossMapProjection.STEPS + BossMapProjection.MARGIN - 1).toDouble()))
    }
}
