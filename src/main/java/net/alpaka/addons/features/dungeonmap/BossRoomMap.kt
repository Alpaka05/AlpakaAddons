package net.alpaka.addons.features.dungeonmap

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.status.ChunkStatus
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
    /** Drawn and refilled on the client thread, so one buffer serves every sampling. */
    private val buffer = IntArray(BossMapProjection.TEXTURE * BossMapProjection.TEXTURE)

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
        val result = buffer.also { it.fill(0) }
        val pos = BlockPos.MutableBlockPos()
        // At most 96x96 samples every half second bound the work even in the large F7 arena. Looking
        // near the player's feet keeps the ceiling from covering the floor on indoor boss maps.
        for (row in -sampled until steps + sampled) for (column in -sampled until steps + sampled) {
            val x = view.worldX(column)
            val z = view.worldZ(row)
            pos.set(x, feet, z)
            // The client level claims to have every chunk; its chunk cache knows which arrived.
            // Looked up once for the column rather than once per block of it.
            val chunk = level.chunkSource.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false) ?: continue
            var color = 0
            for (y in feet + 1 downTo feet - 8) {
                pos.set(x, y, z)
                // What level.getBlockState gives: out of the build height both read as air.
                val state = chunk.getBlockState(pos)
                if (state.isAir) continue
                val mapColor = state.getMapColor(level, pos)
                if (mapColor == MapColor.NONE) continue
                color = if (cfg.dungeonMapCustomColors) {
                    if (y >= feet) DungeonMapColors.BOSS_WALL else DungeonMapColors.BOSS_FLOOR
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
        // A new revision makes the renderer rewrite and upload the whole texture; standing still in
        // an arena that does not change, the samples come out the same every time.
        if (!hasPrevious || !result.contentEquals(previous)) {
            result.copyInto(previous)
            hasPrevious = true
            revision++
        }
    }

    /** The samples behind the current revision, to tell whether a new sampling changed anything. */
    private val previous = IntArray(BossMapProjection.TEXTURE * BossMapProjection.TEXTURE)
    private var hasPrevious = false

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
        hasPrevious = false
        revision++
    }
}
