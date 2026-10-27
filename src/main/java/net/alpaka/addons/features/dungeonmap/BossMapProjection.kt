package net.alpaka.addons.features.dungeonmap

/**
 * North stays at the top; the same projection places terrain and players. [mapX] and [mapY] give
 * the 128 px view. The terrain is sampled [MARGIN] samples further on every side, so the view can
 * follow the player smoothly between two samplings without running off the sampled area.
 */
data class BossMapProjection(val centerX: Double, val centerZ: Double, val span: Int) {
    fun mapX(worldX: Double): Float = ((worldX - centerX) * 128.0 / span + 64.0).toFloat()
    fun mapY(worldZ: Double): Float = ((worldZ - centerZ) * 128.0 / span + 64.0).toFloat()
    /** The block sampled for [column], from -[MARGIN] to [STEPS] + [MARGIN]. */
    fun worldX(column: Int): Int = kotlin.math.floor(centerX + ((column + 0.5) / STEPS - 0.5) * span).toInt()
    fun worldZ(row: Int): Int = kotlin.math.floor(centerZ + ((row + 0.5) / STEPS - 0.5) * span).toInt()

    companion object {
        const val STEPS = 64
        const val MARGIN = 16
        /** Side of the sampled terrain in texture pixels, two per sample. */
        const val TEXTURE = (STEPS + 2 * MARGIN) * 2
    }
}
