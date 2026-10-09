package net.alpaka.addons.features.blaze

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerQuestDetector
import net.alpaka.addons.features.slayer.SlayerType
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Marks the Blaze slayer boss's fire pits with a glowing column, so they can be seen without
 * looking at the floor.
 *
 * During the fight the Inferno Demonlord turns patches of the floor into clay, and a moment later
 * flames come up out of them; standing on one when they do costs a lot of health. The patch is easy
 * to miss while watching the boss, so each of its blocks gets a translucent column two blocks high,
 * strongest at the floor and fading out towards the top. A patch reads as one shape: walls are only
 * drawn where a pit block borders a block that is not one.
 *
 * ### What counts as a pit
 *
 * A block that *becomes* clay or terracotta (any colour) while the player's own Blaze boss is up,
 * within [RADIUS] blocks of the player. The change is what matters: the Blaze slayer's zones are
 * built from terracotta, and only a block that was something else a moment ago is a pit. The column
 * stays exactly as long as the block does and goes the moment the server turns it back.
 *
 * Nothing is scanned: the changes are the block updates the server sends this client anyway, read
 * as they arrive (see ClientLevelMixin).
 */
object FirePitFeature {

    /** How far from the player a changed block may be and still count, in blocks. */
    private const val RADIUS = 24.0

    /** Upper bound on tracked pits, so a misread arena rebuild cannot grow this without limit. */
    private const val MAX_PITS = 256

    /** Height of the column above the pit block, in blocks. */
    private const val AURA_HEIGHT = 2.0f

    /** Colour of the column: a hot red-orange. Alpha at the floor; it fades to nothing at the top. */
    private const val RED = 1.0f
    private const val GREEN = 0.28f
    private const val BLUE = 0.08f
    private const val FLOOR_ALPHA = 0.42f

    /** The tint laid over the pit's own top face. */
    private const val TOP_ALPHA = 0.30f

    /** A new pit fades in over this long rather than popping up. */
    private const val FADE_IN_MS = 180L

    /** Lifts the floor tint off the block's top face, so the two do not z-fight. */
    private const val SURFACE_OFFSET = 0.01f

    /** Pit block and the time it appeared, in insertion order. */
    private val pits = LinkedHashMap<BlockPos, Long>()

    /**
     * Whether the feature is running this tick. Worked out once per tick rather than per block
     * update, because a single packet can carry a whole section's worth of changes.
     */
    private var active = false

    /**
     * Runs the feature outside a Blaze boss fight, from "/alpakadebug firepits": the only way to
     * see the column without summoning the boss. Not saved; a restart turns it off.
     */
    private var preview = false

    /** Flips the preview and returns whether it is now on. */
    @JvmStatic
    fun togglePreview(): Boolean {
        preview = !preview
        return preview
    }

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        LevelRenderEvents.COLLECT_SUBMITS.register { submit(it) }
    }

    @JvmStatic
    fun reset() {
        pits.clear()
        active = false
    }

    private fun tick(mc: Minecraft) {
        val cfg = AlpakaConfig.instance
        active = cfg.blazeFirePitsEnabled && mc.level != null && (preview || inOwnBlazeFight())
        if (!active) {
            pits.clear()
            return
        }
        // A pit ends when its block stops being clay. An unloaded chunk reads as air, so pits there
        // go too.
        val level = mc.level ?: return
        pits.keys.removeIf { !isPitBlock(level.getBlockState(it)) }
    }

    private fun inOwnBlazeFight(): Boolean =
        SkyblockUtils.isOnSkyblock() &&
            SlayerQuestDetector.inBossFight &&
            SlayerQuestDetector.currentOrRecent() == SlayerType.BLAZE

    /**
     * A block update from the server, before it is applied. Called from ClientLevelMixin for every
     * block the server changes, so it returns as early as it can.
     */
    @JvmStatic
    fun onServerBlockChange(level: ClientLevel, pos: BlockPos, newState: BlockState) {
        if (!active || !isPitBlock(newState)) return
        if (pits.size >= MAX_PITS || pits.containsKey(pos)) return
        if (isPitBlock(level.getBlockState(pos))) return
        val player = Minecraft.getInstance().player ?: return
        if (player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > RADIUS * RADIUS) return
        // The packet's position may be a mutable one that is reused for the next block.
        pits[pos.immutable()] = System.currentTimeMillis()
    }

    private fun isPitBlock(state: BlockState): Boolean =
        state.`is`(Blocks.CLAY) || state.`is`(BlockTags.TERRACOTTA)

    private fun submit(context: LevelRenderContext) {
        if (pits.isEmpty()) return
        val camera = context.levelState().cameraRenderState.pos
        val poseStack = context.poseStack()
        val now = System.currentTimeMillis()

        // Copied: the geometry is built later in the frame, and the set may change before then.
        val snapshot = pits.entries.map { it.key to fadeIn(now - it.value) }
        val members = HashSet(pits.keys)
        val camX = camera.x
        val camY = camera.y
        val camZ = camera.z
        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, buffer ->
            for ((pos, fade) in snapshot) drawColumn(buffer, pose, pos, fade, members, camX, camY, camZ)
        }
    }

    /** 0..1 strength of a pit that appeared [ageMs] ago, eased out. */
    private fun fadeIn(ageMs: Long): Float {
        val t = (ageMs.toFloat() / FADE_IN_MS).coerceIn(0.0f, 1.0f)
        val inv = 1.0f - t
        return 1.0f - inv * inv * inv
    }

    /**
     * One pit's column: the tint over its top face, and a fading wall on each side that borders
     * something other than a pit. Coordinates are taken relative to the camera in double precision
     * first: far from the world's origin a float cannot hold a block position finely enough, and the
     * column would shake as the camera moves.
     */
    private fun drawColumn(buffer: VertexConsumer, pose: PoseStack.Pose, pos: BlockPos, fade: Float,
                           members: Set<BlockPos>, camX: Double, camY: Double, camZ: Double) {
        val x0 = (pos.x - camX).toFloat()
        val z0 = (pos.z - camZ).toFloat()
        val x1 = x0 + 1.0f
        val z1 = z0 + 1.0f
        val y0 = (pos.y + 1.0 - camY).toFloat() + SURFACE_OFFSET
        val y1 = y0 + AURA_HEIGHT
        val floor = FLOOR_ALPHA * fade

        val top = TOP_ALPHA * fade
        quad(buffer, pose, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, top, top, top, top)

        for (side in Direction.Plane.HORIZONTAL) {
            if (pos.relative(side) in members) continue
            when (side) {
                Direction.NORTH -> wall(buffer, pose, x0, z0, x1, z0, y0, y1, floor)
                Direction.SOUTH -> wall(buffer, pose, x0, z1, x1, z1, y0, y1, floor)
                Direction.WEST -> wall(buffer, pose, x0, z0, x0, z1, y0, y1, floor)
                Direction.EAST -> wall(buffer, pose, x1, z0, x1, z1, y0, y1, floor)
                else -> {}
            }
        }
    }

    /** A vertical wall from (xa, za) to (xb, zb), at [floor] alpha at the bottom and clear at the top. */
    private fun wall(buffer: VertexConsumer, pose: PoseStack.Pose,
                     xa: Float, za: Float, xb: Float, zb: Float, y0: Float, y1: Float, floor: Float) {
        quad(buffer, pose, xa, y0, za, xb, y0, zb, xb, y1, zb, xa, y1, za, floor, floor, 0.0f, 0.0f)
    }

    /**
     * A quad with its own alpha per corner, in both windings: the pit's walls are seen from inside
     * the column as well as from outside, and debug quads may be culled.
     */
    private fun quad(buffer: VertexConsumer, pose: PoseStack.Pose,
                     x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                     x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float,
                     a1: Float, a2: Float, a3: Float, a4: Float) {
        vertex(buffer, pose, x1, y1, z1, a1)
        vertex(buffer, pose, x2, y2, z2, a2)
        vertex(buffer, pose, x3, y3, z3, a3)
        vertex(buffer, pose, x4, y4, z4, a4)

        vertex(buffer, pose, x4, y4, z4, a4)
        vertex(buffer, pose, x3, y3, z3, a3)
        vertex(buffer, pose, x2, y2, z2, a2)
        vertex(buffer, pose, x1, y1, z1, a1)
    }

    private fun vertex(buffer: VertexConsumer, pose: PoseStack.Pose, x: Float, y: Float, z: Float, alpha: Float) {
        buffer.addVertex(pose, x, y, z).setColor(RED, GREEN, BLUE, alpha)
    }
}
