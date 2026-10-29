package net.alpaka.addons.features.dungeonmap

/**
 * The mod's own map colors (Map Colors: Alpaka). Every element stays apart from the others and from
 * a dark or blurred background: Hypixel's black wither doors and question marks vanished on it.
 */
object DungeonMapColors {
    const val ROOM = 0xFFB78A60.toInt()
    const val ENTRANCE = 0xFF4DBB75.toInt()
    const val PUZZLE = 0xFFB86CE0.toInt()
    const val TRAP = 0xFFE89B45.toInt()
    const val MINIBOSS = 0xFFF3D55B.toInt()
    const val FAIRY = 0xFFF08BBD.toInt()
    const val BLOOD = 0xFFE15959.toInt()
    const val UNKNOWN = 0xFF555D68.toInt()
    const val WITHER_DOOR = 0xFF9AA2B4.toInt()
    const val CLEARED = 0xFFFFFFFF.toInt()
    const val COMPLETE = 0xFF55E68B.toInt()
    const val FAILED = 0xFFFF5555.toInt()
    const val BOSS_FLOOR = 0xFF687981.toInt()
    const val BOSS_WALL = 0xFFB8C5CC.toInt()

    /** Indexed by [DungeonMapInk] ordinal; [DungeonMapInk.ORIGINAL] keeps the map's own color. */
    val BY_INK = intArrayOf(0, ROOM, ENTRANCE, PUZZLE, TRAP, MINIBOSS, FAIRY, BLOOD, UNKNOWN, WITHER_DOOR,
        CLEARED, COMPLETE, FAILED)
}
