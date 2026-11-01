package net.alpaka.addons.features.dungeonmap

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.material.MapColor
import kotlin.math.floor

/**
 * A top-down picture of the loaded arena, one pixel per sampled block, in the blocks' own map colors
 * and shaded by height the way a vanilla map is, so walls, stairs, pits and platforms read as they
 * look in the world. Nothing is requested from the server and no floor layout is assumed.
 */
object BossRoomMap {
    var projection: BossMapProjection? = null
        private set
    var pixels: IntArray? = null
        private set
    var revision = 0
        private set
    /** Drawn and refilled on the client thread, so one buffer serves every sampling. */
    private val buffer = IntArray(BossMapProjection.TEXTURE * BossMapProjection.TEXTURE)
    private val heights = IntArray(BossMapProjection.TEXTURE)
    /** The floor last stood on, so a jump or a fall into a pit doesn't redraw the arena from the air. */
    private var groundY = Int.MIN_VALUE

    /** Blocks above the feet that still count as the room: anything solid higher up is a ceiling. */
    private const val HEADROOM = 6
    /** How far below the feet the floor of a pit or a lower platform is still drawn. */
    private const val DEPTH = 24
    private const val NOTHING = Int.MIN_VALUE
    /** The map color of the block [surface] found last. */
    private var found = MapColor.NONE

    fun update(client: Minecraft) {
        val level = client.level ?: return
        val player = client.player ?: return
        val cfg = AlpakaConfig.instance
        val view = BossMapProjection(floor(player.x), floor(player.z), cfg.dungeonMapBossRange.toInt().coerceIn(32, 192))
        val standing = floor(player.y + 0.01).toInt()
        if (player.onGround() || groundY == Int.MIN_VALUE || kotlin.math.abs(standing - groundY) > 12) groundY = standing
        val feet = groundY
        val side = BossMapProjection.TEXTURE
        val margin = BossMapProjection.MARGIN
        val steps = BossMapProjection.STEPS
        // Only a view that follows the player ever shows the margin.
        val sampled = if (cfg.dungeonMapSmoothMarkers) margin else 0
        val result = buffer.also { it.fill(0) }
        heights.fill(NOTHING)
        val pos = BlockPos.MutableBlockPos()
        // At most 192x192 columns every half second, each read straight from its chunk section and
        // skipping empty sections, bound the work even in the large F7 arena.
        for (row in -sampled until steps + sampled) for (column in -sampled until steps + sampled) {
            val x = view.worldX(column)
            val z = view.worldZ(row)
            val cell = column + margin
            val north = heights[cell]
            // The client level claims to have every chunk; its chunk cache knows which arrived.
            val chunk = if (level.chunkSource.hasChunk(x shr 4, z shr 4))
                level.chunkSource.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false) else null
            val y = if (chunk == null) NOTHING else surface(chunk, x, z, feet, pos)
            heights[cell] = y
            if (y == NOTHING) continue
            result[(row + margin) * side + cell] = shade(found, y, north, feet, x + z)
        }
        pixels = result
        projection = view
        revision++
    }

    /**
     * The top block of the column as seen from the player's level. Starting a few blocks above the
     * feet and looking past the first solid layer with air under it keeps the ceiling of an indoor
     * arena from covering the floor.
     */
    private fun surface(chunk: LevelChunk, x: Int, z: Int, feet: Int, pos: BlockPos.MutableBlockPos): Int {
        val bottom = maxOf(feet - DEPTH, chunk.minY)
        var y = minOf(feet + HEADROOM, chunk.maxY)
        var open = false
        while (y >= bottom) {
            val index = chunk.getSectionIndex(y)
            val section = chunk.getSection(index)
            if (section.hasOnlyAir()) {
                open = true
                // Straight on to the top of the next section down.
                y = (chunk.getSectionYFromSectionIndex(index) shl 4) - 1
                continue
            }
            val state = section.getBlockState(x and 15, y and 15, z and 15)
            // Barriers and light blocks have no map color and stay as see-through as in the world.
            val color = if (state.isAir) MapColor.NONE else state.getMapColor(chunk, pos.set(x, y, z))
            if (color != MapColor.NONE) {
                found = color
                // Solid where the player's legs would be: a wall, even when it reaches into the ceiling.
                if (open || y <= feet) return y
            } else open = true
            y--
        }
        return NOTHING
    }

    /**
     * Vanilla's map shading: lighter where the ground rises towards the south, darker where it drops,
     * plus a faint checker on flat ground. Walls stay light and pits fade with depth.
     */
    private fun shade(color: MapColor, y: Int, north: Int, feet: Int, parity: Int): Int {
        val brightness = when {
            y >= feet -> MapColor.Brightness.HIGH
            north == NOTHING -> MapColor.Brightness.NORMAL
            else -> {
                val d = (y - north) * 0.8 + ((parity and 1) - 0.5) * 0.4
                when {
                    d > 0.6 -> MapColor.Brightness.HIGH
                    d < -0.6 -> MapColor.Brightness.LOW
                    else -> MapColor.Brightness.NORMAL
                }
            }
        }
        val argb = color.calculateARGBColor(brightness)
        val below = feet - 1 - y
        if (below <= 2) return argb
        val keep = 1.0 - 0.5 * minOf(below, DEPTH) / DEPTH
        val r = ((argb shr 16 and 255) * keep).toInt()
        val g = ((argb shr 8 and 255) * keep).toInt()
        val b = ((argb and 255) * keep).toInt()
        return (argb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
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
        groundY = Int.MIN_VALUE
        revision++
    }
}
