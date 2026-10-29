package net.alpaka.addons.utils

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.alpaka.addons.features.slayer.BossHighlightRenderState
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB

/**
 * The three ways a mob can be highlighted, shared by the slayer Boss Highlight and the dungeon
 * Starred Mobs Highlight: a glowing outline, a box around its hitbox, or a color laid over its model.
 */
object EntityHighlight {

    /** The style choices, in order; the configs store an index into this. */
    @JvmField
    val STYLE_NAMES = arrayOf("Outline", "Hitbox", "Overlay")

    const val STYLE_OUTLINE = 0
    const val STYLE_HITBOX = 1
    const val STYLE_OVERLAY = 2

    /** How strongly the overlay lifts a light color towards white, which a tint alone cannot do. */
    private const val OVERLAY_WHITE_LIFT = 0.6f

    /** Alpha of the overlay at most, so the model's own texture still reads through it. */
    private const val OVERLAY_MAX = 0.75f

    /** Thickness of the hitbox edges, in blocks. */
    private const val EDGE = 0.02f

    @JvmStatic
    fun style(index: Int): Int = index.coerceIn(STYLE_OUTLINE, STYLE_OVERLAY)

    /**
     * Gives an entity the glowing outline vanilla draws for the Glowing effect, in [color].
     * The outline is drawn opaque, and 0 means "no outline", so pure black is nudged off it.
     */
    @JvmStatic
    fun outline(state: EntityRenderState, color: Int) {
        state.outlineColor = (color and 0xFFFFFF).takeIf { it != 0 } ?: 0x010101
    }

    /**
     * Notes the overlay in [color] on a mob's render state, where the model is drawn from later in
     * the frame.
     *
     * The model tint multiplies the texture, so on its own it can only darken: white changed nothing
     * at all. The white flash vanilla uses for a creeper about to blow lifts the model towards white
     * instead, so a light color gets that on top, in proportion to how light it is.
     */
    @JvmStatic
    fun overlay(state: BossHighlightRenderState, color: Int) {
        val strength = (color ushr 24 and 0xFF) / 255.0f * OVERLAY_MAX
        fun mix(shift: Int): Int {
            val channel = color ushr shift and 0xFF
            return (255 + (channel - 255) * strength).toInt().coerceIn(0, 255) shl shift
        }
        val tint = 0xFF000000.toInt() or mix(16) or mix(8) or mix(0)
        val luminance = (0.299f * (color ushr 16 and 0xFF) + 0.587f * (color ushr 8 and 0xFF) +
            0.114f * (color and 0xFF)) / 255.0f
        state.`alpaka$setHighlight`(tint, strength * luminance * OVERLAY_WHITE_LIFT)
    }

    /** Draws a box around the hitbox of each entity in [targets] (entity id to ARGB color). */
    @JvmStatic
    fun submitHitboxes(context: LevelRenderContext, targets: Map<Int, Int>) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos

        // Worked out now, relative to the camera in double precision; the geometry is built later
        // in the frame, and far from the origin a float cannot hold an absolute position finely.
        val boxes = ArrayList<Pair<AABB, Int>>(targets.size)
        for ((id, color) in targets) {
            val entity = level.getEntity(id) ?: continue
            val pos = entity.getPosition(partialTick)
            val box = entity.boundingBox
                .move(pos.x - entity.x - camera.x, pos.y - entity.y - camera.y, pos.z - entity.z - camera.z)
            boxes.add(box to color)
        }
        if (boxes.isEmpty()) return

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads()) { pose, buffer ->
            for ((box, color) in boxes) drawBox(buffer, pose, box, color)
        }
    }

    /** The twelve edges of [box], each as a thin bar. */
    private fun drawBox(buffer: VertexConsumer, pose: PoseStack.Pose, box: AABB, color: Int) {
        val x0 = box.minX.toFloat()
        val y0 = box.minY.toFloat()
        val z0 = box.minZ.toFloat()
        val x1 = box.maxX.toFloat()
        val y1 = box.maxY.toFloat()
        val z1 = box.maxZ.toFloat()
        val e = EDGE
        for (y in floatArrayOf(y0, y1)) {
            for (z in floatArrayOf(z0, z1)) bar(buffer, pose, color, x0 - e, y - e, z - e, x1 + e, y + e, z + e)
            for (x in floatArrayOf(x0, x1)) bar(buffer, pose, color, x - e, y - e, z0 - e, x + e, y + e, z1 + e)
        }
        for (x in floatArrayOf(x0, x1)) {
            for (z in floatArrayOf(z0, z1)) bar(buffer, pose, color, x - e, y0 - e, z - e, x + e, y1 + e, z + e)
        }
    }

    /** A solid cuboid from (ax, ay, az) to (bx, by, bz): six faces. */
    private fun bar(buffer: VertexConsumer, pose: PoseStack.Pose, color: Int,
                    ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
        face(buffer, pose, color, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz)
        face(buffer, pose, color, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az)
        face(buffer, pose, color, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az)
        face(buffer, pose, color, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz)
        face(buffer, pose, color, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az)
        face(buffer, pose, color, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz)
    }

    /** One quad in both windings, since debug quads may be culled. */
    private fun face(buffer: VertexConsumer, pose: PoseStack.Pose, color: Int,
                     x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                     x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float) {
        buffer.addVertex(pose, x1, y1, z1).setColor(color)
        buffer.addVertex(pose, x2, y2, z2).setColor(color)
        buffer.addVertex(pose, x3, y3, z3).setColor(color)
        buffer.addVertex(pose, x4, y4, z4).setColor(color)
        buffer.addVertex(pose, x4, y4, z4).setColor(color)
        buffer.addVertex(pose, x3, y3, z3).setColor(color)
        buffer.addVertex(pose, x2, y2, z2).setColor(color)
        buffer.addVertex(pose, x1, y1, z1).setColor(color)
    }
}
