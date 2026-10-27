package net.alpaka.addons.features.dungeonmap

import kotlin.math.abs

/**
 * Where a world position lands on the Magical Map, so that players in render distance can be drawn
 * from their live position instead of the coarse markers.
 *
 * The map draws each 32-block cell of the dungeon as a room of 16 px (18 px on the small floors) with
 * a 4 px gap, so the scale follows from the entrance room's width. The cells start at world -200, and
 * the map's first room sits at the entrance's pixel modulo the pitch (the rule Odin and Catlas use).
 * Mapping a room's world center onto the drawn room's center keeps players inside their room.
 *
 * The player's own marker checks that rule: while the player stands still, it should sit where the
 * rooms put them. If it is several pixels away, the rule is wrong for this map, and the offset read
 * off the marker is used instead.
 */
class DungeonMapCalibration {
    /** Map pixels per block, 0 until known. */
    var scale = 0.0
        private set
    private var roomX = Double.NaN
    private var roomY = Double.NaN
    private var sumX = 0.0
    private var sumY = 0.0
    private var samples = 0
    private var misses = 0

    val hasRooms get() = !roomX.isNaN()
    val ready get() = scale > 0 && (hasRooms || samples > 0)

    fun mapX(worldX: Double) = (worldX * scale + offset(roomX, sumX)).toFloat()
    fun mapY(worldZ: Double) = (worldZ * scale + offset(roomY, sumY)).toFloat()

    private fun offset(room: Double, sum: Double) = when {
        samples == 0 -> room
        room.isNaN() -> sum / samples
        samples >= TRUSTED && abs(sum / samples - room) > DISAGREE -> sum / samples
        else -> room
    }

    /** Reads the room size and grid off the entrance room; false while the map does not show it. */
    fun measureRooms(inks: Array<DungeonMapInk>): Boolean {
        val side = DungeonMapPixels.SIDE
        for (row in 0 until side) {
            var column = 0
            while (column < side) {
                if (inks[row * side + column] != DungeonMapInk.ENTRANCE) {
                    column++
                    continue
                }
                val start = column
                while (column < side && inks[row * side + column] == DungeonMapInk.ENTRANCE) column++
                // The first run this wide is the room's top edge; a door into the entrance is 4 px.
                val size = column - start
                if (size == 16 || size == 18) {
                    useRooms(start, row, size)
                    return true
                }
            }
        }
        return false
    }

    /** Until the entrance is found, the floor gives the scale: rooms are 18 px up to floor 3. */
    fun assumeFloor(floor: String?) {
        if (scale > 0) return
        val small = (floor?.drop(1)?.toIntOrNull() ?: 0) <= 3
        scale = (if (small) 22 else 20) / 32.0
    }

    private fun useRooms(left: Int, top: Int, size: Int) {
        val pitch = size + 4
        setScale(pitch / 32.0)
        // 22 px is where the first room starts on the small floors, and 22 % 22 is 0.
        val startX = (left % pitch).let { if (it == 0) pitch else it }
        val startY = (top % pitch).let { if (it == 0) pitch else it }
        roomX = startX + size / 2.0 + ROOM_CENTER * scale
        roomY = startY + size / 2.0 + ROOM_CENTER * scale
    }

    private fun setScale(value: Double) {
        // Readings are stored as offsets, which depend on the scale.
        if (value != scale) clearReadings()
        scale = value
    }

    /** The own marker while the player has stood still long enough for it to catch up. */
    fun observe(worldX: Double, worldZ: Double, markerX: Float, markerY: Float) {
        if (scale == 0.0) return
        val x = markerX - worldX * scale
        val y = markerY - worldZ * scale
        if (samples >= TRUSTED && (abs(x - sumX / samples) > OUTLIER || abs(y - sumY / samples) > OUTLIER)) {
            // One stray reading is ignored; a long run of them means the map changed, so relearn.
            if (++misses >= RELEARN) clearReadings()
            return
        }
        misses = 0
        if (samples == MAX_SAMPLES) {
            sumX -= sumX / samples
            sumY -= sumY / samples
            samples--
        }
        sumX += x
        sumY += y
        samples++
    }

    private fun clearReadings() {
        sumX = 0.0
        sumY = 0.0
        samples = 0
        misses = 0
    }

    fun reset() {
        scale = 0.0
        roomX = Double.NaN
        roomY = Double.NaN
        clearReadings()
    }

    private companion object {
        /** Cell 0 spans world -200 to -170, so its center is at -185. */
        const val ROOM_CENTER = 185.0
        const val TRUSTED = 5
        const val DISAGREE = 3.0
        const val OUTLIER = 3.0
        const val RELEARN = 40
        const val MAX_SAMPLES = 200
    }
}
