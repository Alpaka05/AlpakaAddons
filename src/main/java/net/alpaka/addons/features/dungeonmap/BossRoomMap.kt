package net.alpaka.addons.features.dungeonmap

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.material.MapColor
import kotlin.math.floor

/** A local floor slice of the loaded arena, without chunk requests or a fixed floor layout. */
object BossRoomMap {
    var projection: BossMapProjection? = null
        private set
    var pixels: IntArray? = null
        private set
    var revision = 0
        private set

    fun update(client: Minecraft) {
        val level = client.level ?: return
        val player = client.player ?: return
        val cfg = AlpakaConfig.instance
        val view = BossMapProjection(floor(player.x), floor(player.z), cfg.dungeonMapBossRange.toInt().coerceIn(32, 192))
        val feet = floor(player.y).toInt()
        val side = BossMapProjection.TEXTURE
        val margin = BossMapProjection.MARGIN
        val steps = BossMapProjection.STEPS
        // Only a view that follows the player ever shows the margin.
        val sampled = if (cfg.dungeonMapSmoothMarkers) margin else 0
        val result = IntArray(side * side)
        val pos = BlockPos.MutableBlockPos()
        // At most 96x96 samples every half second bound the work even in the large F7 arena. Looking
        // near the player's feet keeps the ceiling from covering the floor on indoor boss maps.
        for (row in -sampled until steps + sampled) for (column in -sampled until steps + sampled) {
            val x = view.worldX(column)
            val z = view.worldZ(row)
            pos.set(x, feet, z)
            if (!level.hasChunk(x shr 4, z shr 4)) continue
            var color = 0
            for (y in feet + 1 downTo feet - 8) {
                pos.set(x, y, z)
                val state = level.getBlockState(pos)
                if (state.isAir) continue
                val mapColor = state.getMapColor(level, pos)
                if (mapColor == MapColor.NONE) continue
                color = if (cfg.dungeonMapBossCustomColors) {
                    if (y >= feet) cfg.dungeonMapBossWallColor else cfg.dungeonMapBossFloorColor
                } else mapColor.calculateARGBColor(if (y >= feet) MapColor.Brightness.HIGH else MapColor.Brightness.NORMAL)
                break
            }
            val index = (row + margin) * 2 * side + (column + margin) * 2
            result[index] = color
            result[index + 1] = color
            result[index + side] = color
            result[index + side + 1] = color
        }
        pixels = result
        projection = view
        revision++
    }

    /** Whether the player has walked far enough from the last sampling to need the next one early. */
    fun drifted(client: Minecraft): Boolean {
        val view = projection ?: return false
        val player = client.player ?: return false
        val limit = view.span / 8.0
        return kotlin.math.abs(player.x - view.centerX) > limit || kotlin.math.abs(player.z - view.centerZ) > limit
    }

    fun reset() {
        pixels = null
        projection = null
        revision++
    }
}
