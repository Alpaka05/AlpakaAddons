package net.alpaka.addons.features.dungeonmap

/** North stays at the top; the same projection places terrain and players. */
data class BossMapProjection(val centerX: Double, val centerZ: Double, val span: Int) {
    fun mapX(worldX: Double): Float = ((worldX - centerX) * 128.0 / span + 64.0).toFloat()
    fun mapY(worldZ: Double): Float = ((worldZ - centerZ) * 128.0 / span + 64.0).toFloat()
    fun worldX(column: Int): Int = kotlin.math.floor(centerX + ((column + 0.5) / 64.0 - 0.5) * span).toInt()
    fun worldZ(row: Int): Int = kotlin.math.floor(centerZ + ((row + 0.5) / 64.0 - 0.5) * span).toInt()
}
