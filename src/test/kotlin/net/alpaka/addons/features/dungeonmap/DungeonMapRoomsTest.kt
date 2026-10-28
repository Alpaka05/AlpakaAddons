package net.alpaka.addons.features.dungeonmap

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DungeonMapRoomsTest {
    @Test
    fun `an empty column hashes to the core Odin ignores`() {
        val (core, roof) = DungeonRooms.core { "minecraft:air" }
        assertEquals(-318865360, core)
        assertEquals(0, roof)
    }

    @Test
    fun `the core writes the column from the roof down, without chests and planks`() {
        val column = mapOf(120 to "minecraft:gold_block", 100 to "minecraft:stone", 69 to "minecraft:chest",
            68 to "minecraft:oak_planks", 67 to "minecraft:bedrock", 66 to "minecraft:bedrock")
        val (core, roof) = DungeonRooms.core { y -> column[y] ?: "minecraft:air" }
        // Gold above the roof counts as air; two layers of bedrock below 69 end the column.
        val expected = "0".repeat(40) + "Block{minecraft:stone}" + "Block{minecraft:air}".repeat(30) +
            "Block{minecraft:bedrock}".repeat(2) + "0".repeat(54)
        assertEquals(expected.hashCode(), core)
        assertEquals(100, roof)
    }

    @Test
    fun `the room table knows every core`() {
        val table = DungeonRooms.loadTable()
        assertEquals(272, table.size)
        assertEquals(RoomInfo("Admin", "NORMAL", 0), table[518379920])
        assertEquals("Ice Path", table[-145184586]?.name)
        assertEquals(6, table[1591923028]?.secrets)
    }

    @Test
    fun `action bar counts go to the room the player is in, and only upwards`() {
        DungeonRooms.reset()
        val atlas = RoomInfo("Atlas", "NORMAL", 6)
        val beams = RoomInfo("Beams", "NORMAL", 2)
        DungeonRooms.recognize(0, atlas, 80)
        DungeonRooms.recognize(1, beams, 80)
        DungeonRooms.track(-185, 69, -185)
        DungeonRooms.onActionBar("1,234/1,234❤     2/6 Secrets     500/500✎ Mana")
        assertEquals(2, DungeonRooms.found(atlas))
        DungeonRooms.onActionBar("3/6 Secrets")
        DungeonRooms.onActionBar("1/6 Secrets")
        assertEquals(3, DungeonRooms.found(atlas))
        // Into Beams while the bar still shows Atlas: that line is skipped, the next is checked by its total.
        DungeonRooms.track(-153, 69, -185)
        DungeonRooms.onActionBar("3/6 Secrets")
        DungeonRooms.onActionBar("3/6 Secrets")
        assertNull(DungeonRooms.found(beams))
        DungeonRooms.onActionBar("1/2 Secrets")
        assertEquals(1, DungeonRooms.found(beams))
        // Standing on Atlas' roof is not being in Atlas.
        DungeonRooms.track(-185, 75, -185)
        DungeonRooms.onActionBar("2/2 Secrets")
        assertEquals(2, DungeonRooms.found(beams))
        assertEquals(3, DungeonRooms.found(atlas))
        DungeonRooms.complete(atlas)
        assertEquals(6, DungeonRooms.found(atlas))
        DungeonRooms.reset()
        assertNull(DungeonRooms.found(atlas))
    }

    @Test
    fun `the current room follows the player, also over its roof, and is gone outside the grid`() {
        DungeonRooms.reset()
        val atlas = RoomInfo("Atlas", "NORMAL", 6)
        DungeonRooms.recognize(0, atlas, 80)
        DungeonRooms.track(-185, 69, -185)
        assertEquals(atlas, DungeonRooms.here)
        DungeonRooms.track(-185, 90, -185)
        assertEquals(atlas, DungeonRooms.here)
        // A cell nothing has been recognized in, and a spot outside the dungeon.
        DungeonRooms.track(-153, 69, -185)
        assertNull(DungeonRooms.here)
        DungeonRooms.track(-185, 69, -185)
        DungeonRooms.track(-300, 69, -185)
        assertNull(DungeonRooms.here)
        DungeonRooms.reset()
    }

    /** A Hypixel-like map: 16 px rooms 20 px apart from 5 px, cell (x, z) at 5 + 20x, 5 + 20z. */
    private class SampleMap {
        val inks = Array(DungeonMapPixels.SIZE) { DungeonMapInk.ORIGINAL }
        fun fill(left: Int, top: Int, right: Int, bottom: Int, ink: DungeonMapInk) {
            for (y in top until bottom) for (x in left until right) inks[y * 128 + x] = ink
        }
        fun cell(x: Int, z: Int, ink: DungeonMapInk) = fill(5 + 20 * x, 5 + 20 * z, 21 + 20 * x, 21 + 20 * z, ink)
    }

    private val flag = RoomInfo("Big Red Flag", "NORMAL", 2, cells = 2)
    private val altar = RoomInfo("Altar", "NORMAL", 6, cells = 3)
    private val gray = RoomInfo("Andesite", "NORMAL", 2)
    private val fairy = RoomInfo("Fairy", "FAIRY", 0)
    private val quiz = RoomInfo("Quiz", "PUZZLE", 0)
    private val rooms = mapOf(0 to flag, 1 to flag, 6 to altar, 12 to altar, 13 to altar, 2 to gray, 3 to fairy, 4 to quiz)

    private fun sample() = SampleMap().apply {
        // Big Red Flag: two cells and the gap between them, a white checkmark in the gap.
        cell(0, 0, DungeonMapInk.ROOM)
        cell(1, 0, DungeonMapInk.ROOM)
        fill(21, 5, 25, 21, DungeonMapInk.ROOM)
        fill(22, 12, 24, 14, DungeonMapInk.CLEARED)
        // Altar: an L of three cells with a green checkmark.
        cell(0, 1, DungeonMapInk.ROOM)
        cell(0, 2, DungeonMapInk.ROOM)
        cell(1, 2, DungeonMapInk.ROOM)
        fill(5, 41, 21, 45, DungeonMapInk.ROOM)
        fill(21, 45, 25, 61, DungeonMapInk.ROOM)
        fill(12, 52, 14, 54, DungeonMapInk.COMPLETE)
        cell(2, 0, DungeonMapInk.UNKNOWN)
        // A grey room's question mark must not make it count as shown.
        fill(92 - 40, 12, 94 - 40, 14, DungeonMapInk.WITHER_DOOR)
        cell(3, 0, DungeonMapInk.FAIRY)
        cell(4, 0, DungeonMapInk.PUZZLE)
    }

    private fun build(map: SampleMap, names: Boolean, secrets: Boolean, found: Map<String, Int> = emptyMap(),
                      known: Map<Int, RoomInfo> = rooms) =
        RoomLabels.build(map.inks, { known[it] }, { 13f + 20 * it }, { 13f + 20 * it }, 16, { found[it.name] },
            names, secrets)

    @Test
    fun `labels sit in the middle of a room, or on the cell nearest it for an L`() {
        val result = build(sample(), names = true, secrets = true, found = mapOf("Big Red Flag" to 1))
        val byName = result.labels.associateBy { it.room.name }
        assertEquals(setOf("Big Red Flag", "Altar", "Quiz"), byName.keys)
        val flagLabel = byName.getValue("Big Red Flag")
        assertEquals(23f, flagLabel.x)
        assertEquals(13f, flagLabel.y)
        assertEquals(listOf("Big", "Red", "Flag", "1/2"), flagLabel.lines)
        assertEquals(RoomState.CLEARED, flagLabel.state)
        val altarLabel = byName.getValue("Altar")
        assertEquals(13f to 53f, altarLabel.x to altarLabel.y)
        // A green checkmark means every secret was found, even before the action bar said so.
        assertEquals(listOf("Altar", "6/6"), altarLabel.lines)
        assertEquals(RoomState.COMPLETE, altarLabel.state)
        // A room without secrets gets no count.
        assertEquals(listOf("Quiz"), byName.getValue("Quiz").lines)
    }

    @Test
    fun `secrets alone show an unknown count, and rooms without secrets get no label`() {
        val labels = build(sample(), names = false, secrets = true).labels.associateBy { it.room.name }
        assertEquals(listOf("?/2"), labels.getValue("Big Red Flag").lines)
        assertFalse("Quiz" in labels)
        val nothing = build(sample(), names = false, secrets = false)
        assertTrue(nothing.labels.isEmpty())
        // Without a label the checkmark stays.
        assertNull(nothing.cover)
    }

    @Test
    fun `a labelled room's checkmark takes the room's own fill`() {
        val cover = build(sample(), names = true, secrets = false).cover!!
        val fill = 6 * 128 + 6
        assertEquals(fill, cover[12 * 128 + 22])
        assertEquals(fill, cover[13 * 128 + 23])
        // Altar's first cell is (0, 1), centred on (13, 33), so its fill comes from (6, 26).
        assertEquals(26 * 128 + 6, cover[52 * 128 + 12])
        assertEquals(-1, cover[5 * 128 + 5])
        assertEquals(-1, cover[13 * 128 + 13])
    }

    @Test
    fun `a room waits for all its cells, and an entered-not-yet room's question mark is covered`() {
        // Only one of Big Red Flag's two cells has been recognized: no label, no cover.
        val partial = build(sample(), names = true, secrets = false, known = rooms - 0)
        assertFalse(partial.labels.any { it.room == flag })
        assertEquals(-1, partial.cover!![12 * 128 + 22])
        // Quiz has been seen but not entered: Hypixel puts a question mark in it.
        val map = sample().apply { fill(92, 12, 94, 14, DungeonMapInk.WITHER_DOOR) }
        val result = build(map, names = true, secrets = false)
        assertTrue(result.labels.any { it.room == quiz })
        assertEquals(6 * 128 + 86, result.cover!![12 * 128 + 92])
    }
}
