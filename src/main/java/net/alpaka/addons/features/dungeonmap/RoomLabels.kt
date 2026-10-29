package net.alpaka.addons.features.dungeonmap

/** What a room's checkmark says. */
enum class RoomState { NONE, CLEARED, COMPLETE, FAILED }

/** Text over a room, centred on [x], [y] in map pixels, one word per line. */
class RoomLabel(val room: RoomInfo, val x: Float, val y: Float, val lines: List<String>, val state: RoomState)

/**
 * Where each recognized room sits on the map, what its label says, and which checkmark pixels the
 * label covers: the label is colored by the checkmark instead.
 *
 * A room covers one or more cells. Its label sits in the middle of a rectangular room, and on the
 * cell nearest the middle of an L-shaped one, where the middle itself is outside the room. Only
 * rooms the map already shows are labelled, not the grey ones nobody has opened yet, and only once
 * every cell of the room has been recognized: a room reaching past the loaded chunks would
 * otherwise get its label and checkmark cover on part of it.
 */
object RoomLabels {
    /** [cover] maps a map pixel to the pixel whose color it takes instead, -1 to keep its own. */
    class Result(val labels: List<RoomLabel>, val cover: IntArray?)

    /**
     * [rooms] gives the room in a world cell (x + 6 * z), [centerX] and [centerY] the map position
     * of a cell column or row's middle, [size] the drawn room width in pixels.
     */
    fun build(
        inks: Array<DungeonMapInk>, rooms: (Int) -> RoomInfo?, centerX: (Int) -> Float, centerY: (Int) -> Float,
        size: Int, found: (RoomInfo) -> Int?, names: Boolean, secrets: Boolean,
    ): Result {
        val side = DungeonMapPixels.SIDE
        val byRoom = LinkedHashMap<RoomInfo, MutableList<Int>>()
        for (cell in 0 until 36) rooms(cell)?.let { byRoom.getOrPut(it) { ArrayList() } += cell }
        val labels = ArrayList<RoomLabel>()
        var cover: IntArray? = null
        val half = size / 2f
        for ((room, cells) in byRoom) {
            if (room.plain || cells.size < room.cells) continue
            // Read just inside a corner: the middle may hold a checkmark or a question mark.
            val shown = cells.any { cell ->
                val ink = inkAt(inks, centerX(cell % 6) - half + 1, centerY(cell / 6) - half + 1)
                ink != null && ink != DungeonMapInk.ORIGINAL && ink != DungeonMapInk.UNKNOWN
            }
            if (!shown) continue
            val area = area(cells, centerX, centerY, half)
            var state = RoomState.NONE
            area.forEachPixel { state = maxOf(state, stateOf(inks[it])) }
            val lines = ArrayList<String>()
            if (names) lines += room.name.split(' ')
            if (secrets && room.secrets > 0) {
                val count = found(room) ?: if (state == RoomState.COMPLETE) room.secrets else null
                lines += "${count ?: "?"}/${room.secrets}"
            }
            if (lines.isEmpty()) continue
            val meanX = cells.map { centerX(it % 6) }.average().toFloat()
            val meanY = cells.map { centerY(it / 6) }.average().toFloat()
            val (x, y) = if (rectangular(cells)) meanX to meanY else cells
                .map { centerX(it % 6) to centerY(it / 6) }
                .minBy { (cx, cy) -> (cx - meanX) * (cx - meanX) + (cy - meanY) * (cy - meanY) }
            labels += RoomLabel(room, x, y, lines, state)
            // The room's own fill, from just inside a corner of its first cell, far from any mark.
            val first = cells.first()
            val fillX = (centerX(first % 6) - half + 1).toInt()
            val fillY = (centerY(first / 6) - half + 1).toInt()
            if (fillX !in 0 until side || fillY !in 0 until side) continue
            val fill = fillY * side + fillX
            area.forEachPixel { pixel ->
                // The question mark of a room nobody has entered yet is drawn in the wither door's
                // color; no door lies inside a room, so inside one it is always the mark.
                if (stateOf(inks[pixel]) == RoomState.NONE && inks[pixel] != DungeonMapInk.WITHER_DOOR) return@forEachPixel
                val target = cover ?: IntArray(DungeonMapPixels.SIZE) { -1 }.also { cover = it }
                target[pixel] = fill
            }
        }
        return Result(labels, cover)
    }

    private fun stateOf(ink: DungeonMapInk) = when (ink) {
        DungeonMapInk.CLEARED -> RoomState.CLEARED
        DungeonMapInk.COMPLETE -> RoomState.COMPLETE
        DungeonMapInk.FAILED -> RoomState.FAILED
        else -> RoomState.NONE
    }

    private fun inkAt(inks: Array<DungeonMapInk>, x: Float, y: Float): DungeonMapInk? {
        val px = x.toInt()
        val py = y.toInt()
        if (px !in 0 until DungeonMapPixels.SIDE || py !in 0 until DungeonMapPixels.SIDE) return null
        return inks[py * DungeonMapPixels.SIDE + px]
    }

    private fun rectangular(cells: List<Int>): Boolean {
        val xs = cells.map { it % 6 }
        val zs = cells.map { it / 6 }
        return (xs.max() - xs.min() + 1) * (zs.max() - zs.min() + 1) == cells.size
    }

    /** Map pixel rectangles, each as left, top, right and bottom (exclusive), clamped to the map. */
    private class Area {
        private val rects = ArrayList<IntArray>(4)

        fun add(left: Float, top: Float, right: Float, bottom: Float) {
            val side = DungeonMapPixels.SIDE
            rects += intArrayOf(left.toInt().coerceAtLeast(0), top.toInt().coerceAtLeast(0),
                right.toInt().coerceAtMost(side), bottom.toInt().coerceAtMost(side))
        }

        inline fun forEachPixel(action: (Int) -> Unit) {
            for (r in rects) for (y in r[1] until r[3]) for (x in r[0] until r[2]) action(y * DungeonMapPixels.SIDE + x)
        }
    }

    /**
     * The pixels a room covers: its cell squares and the gaps between neighbouring cells, where a
     * room spanning two cells draws its checkmark.
     */
    private fun area(cells: List<Int>, centerX: (Int) -> Float, centerY: (Int) -> Float, half: Float): Area {
        val area = Area()
        if (rectangular(cells)) {
            area.add(cells.minOf { centerX(it % 6) } - half, cells.minOf { centerY(it / 6) } - half,
                cells.maxOf { centerX(it % 6) } + half, cells.maxOf { centerY(it / 6) } + half)
        } else for (cell in cells) {
            val cx = centerX(cell % 6)
            val cy = centerY(cell / 6)
            area.add(cx - half, cy - half, cx + half, cy + half)
            if (cell % 6 < 5 && cell + 1 in cells) area.add(cx + half, cy - half, centerX(cell % 6 + 1) - half, cy + half)
            if (cell + 6 in cells) area.add(cx - half, cy + half, cx + half, centerY(cell / 6 + 1) - half)
        }
        return area
    }
}
