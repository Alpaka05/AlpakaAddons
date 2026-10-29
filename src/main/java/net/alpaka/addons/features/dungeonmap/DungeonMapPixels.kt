package net.alpaka.addons.features.dungeonmap

/** The server's packed map colors, including their brightness bits, identify room types. */
enum class DungeonMapInk {
    ORIGINAL, ROOM, ENTRANCE, PUZZLE, TRAP, MINIBOSS, FAIRY, BLOOD, UNKNOWN, WITHER_DOOR,
    CLEARED, COMPLETE, FAILED
}

object DungeonMapPixels {
    const val SIDE = 128
    const val SIZE = SIDE * SIDE

    fun classify(colors: ByteArray): Array<DungeonMapInk> {
        require(colors.size == SIZE)
        val result = Array(SIZE) { index ->
            when (colors[index].toInt() and 255) {
                63 -> DungeonMapInk.ROOM
                30 -> DungeonMapInk.ENTRANCE
                66 -> DungeonMapInk.PUZZLE
                62 -> DungeonMapInk.TRAP
                74 -> DungeonMapInk.MINIBOSS
                82 -> DungeonMapInk.FAIRY
                18 -> DungeonMapInk.BLOOD
                85 -> DungeonMapInk.UNKNOWN
                119 -> DungeonMapInk.WITHER_DOOR
                34 -> DungeonMapInk.CLEARED
                else -> DungeonMapInk.ORIGINAL
            }
        }
        // Hypixel reuses entrance green and blood red for checkmarks. Only the small, isolated
        // components are status marks; recoloring the whole byte value would recolor rooms too.
        val visited = BooleanArray(SIZE)
        val queue = IntArray(SIZE)
        for (start in colors.indices) {
            val color = colors[start].toInt() and 255
            if (visited[start] || (color != 30 && color != 18)) continue
            var head = 0
            var tail = 1
            queue[0] = start
            visited[start] = true
            while (head < tail) {
                val p = queue[head++]
                fun visit(n: Int) {
                    if (!visited[n] && colors[n] == colors[start]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (p % SIDE > 0) visit(p - 1)
                if (p % SIDE < SIDE - 1) visit(p + 1)
                if (p >= SIDE) visit(p - SIDE)
                if (p < SIZE - SIDE) visit(p + SIDE)
            }
            if (tail <= 32) {
                val ink = if (color == 30) DungeonMapInk.COMPLETE else DungeonMapInk.FAILED
                for (i in 0 until tail) result[queue[i]] = ink
            }
        }
        return result
    }
}

/** Owns a copy, so a later server update cannot erase the map retained for the boss fight. */
class DungeonMapSnapshot {
    var colors: ByteArray? = null
        private set
    var inks: Array<DungeonMapInk>? = null
        private set
    var revision: Int = 0
        private set

    fun update(incoming: ByteArray?, inBoss: Boolean): Boolean {
        if (inBoss || incoming == null || incoming.size != DungeonMapPixels.SIZE ||
            incoming.all { (it.toInt() and 255) < 4 } || incoming.contentEquals(colors)) return false
        colors = incoming.copyOf()
        inks = DungeonMapPixels.classify(incoming)
        revision++
        return true
    }

    fun reset() {
        colors = null
        inks = null
        revision++
    }
}
