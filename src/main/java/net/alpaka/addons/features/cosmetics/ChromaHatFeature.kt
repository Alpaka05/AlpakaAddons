package net.alpaka.addons.features.cosmetics

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A translucent, chroma-lit samurai hat (a jingasa: a wide, shallow cone) worn by the local player.
 *
 * Client-side cosmetic only. The geometry is built here and handed to the renderer every frame; no
 * item, no packet, no server involvement, and nobody else can see it. It rides on the player's head
 * bone through [ChromaHatLayer], so it turns with the head and follows every pose the model takes.
 *
 * ### Look
 *
 * The hat is a cone of [SEGMENTS] wedges with a small knob on top. The hue runs once around the brim
 * and rotates with time, so the rainbow appears to spin around the head. It is drawn with the
 * emissive translucent entity render type, which ignores world light entirely - the point is that it
 * glows in a dark cave exactly as it does at noon. A faint, slightly larger second cone around it
 * softens the edge into a glow. The texture only supplies alpha and a woven-straw shading; every
 * colour comes from the vertices.
 */
object ChromaHatFeature {

    /** Shipped in the mod's own assets; a plain white weave that the vertex colours tint. */
    @JvmField
    val TEXTURE: Identifier = Identifier.fromNamespaceAndPath("alpaka", "textures/cosmetics/chroma_hat.png")

    /** Packed lightmap coordinate for full brightness. Emissive geometry has to hand over something. */
    private const val FULL_BRIGHT = 0xF000F0

    /** Wedges around the cone. 32 keeps the brim round at arm's length without a visible polygon count. */
    private const val SEGMENTS = 32

    /**
     * Where the brim sits, in head-part space, where +y points *down* the body.
     *
     * The head cube spans y from -0.5 (its top) to 0 in this space, and a helmet adds a little on
     * top of that, so the brim floats just clear of a worn helmet rather than cutting through it.
     */
    private const val BRIM_Y = -0.56f

    /** How much further the brim is raised when the player wears something on their head. */
    private const val HELMET_LIFT = 0.05f

    /** Brim radius and cone height at size 1, in blocks. Wide and shallow is what reads as a jingasa. */
    private const val BRIM_RADIUS = 0.72f
    private const val CONE_HEIGHT = 0.30f

    /** The knob on the apex: its radius and height. */
    private const val KNOB_RADIUS = 0.045f
    private const val KNOB_HEIGHT = 0.07f

    /** The glow shell is this much larger than the hat, and this much fainter. */
    private const val GLOW_SCALE = 1.06f
    private const val GLOW_ALPHA = 0.30f

    /** The underside is drawn a little fainter than the top so the two do not stack into a solid. */
    private const val UNDERSIDE_ALPHA = 0.6f

    /** Full turns of the rainbow around the hat per second, at speed 1. */
    private const val HUE_TURNS_PER_SECOND = 0.25

    /** Whether the hat belongs on this render state: the feature is on and it is the local player. */
    @JvmStatic
    fun shouldRender(state: AvatarRenderState): Boolean {
        if (!AlpakaConfig.instance.chromaHatEnabled) return false
        if (state.isSpectator || state.isInvisible) return false
        val player = Minecraft.getInstance().player ?: return false
        return state.id == player.id
    }

    /**
     * Emits the hat's triangles. The pose is the head part's own space (see [ChromaHatLayer]).
     *
     * @param wearingHelmet lifts the hat so it does not intersect a helmet's overlay cube.
     */
    fun emit(pose: PoseStack.Pose, consumer: VertexConsumer, wearingHelmet: Boolean) {
        val cfg = AlpakaConfig.instance
        val alpha = (cfg.chromaHatOpacity / 100f).coerceIn(0.05f, 1f)
        val size = cfg.chromaHatSize
        val time = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * cfg.chromaHatSpeed
        val hueShift = time * HUE_TURNS_PER_SECOND

        val brimY = BRIM_Y - (if (wearingHelmet) HELMET_LIFT else 0f)
        val height = CONE_HEIGHT * size
        val radius = BRIM_RADIUS * size
        val apexY = brimY - height

        // The hat proper: top surface, then the underside so it also reads from below.
        emitCone(pose, consumer, apexY, brimY, radius, hueShift, alpha, outward = true)
        emitCone(pose, consumer, apexY, brimY, radius, hueShift, alpha * UNDERSIDE_ALPHA, outward = false)

        // The knob: a tiny cone standing on the apex.
        emitCone(pose, consumer, apexY - KNOB_HEIGHT * size, apexY, KNOB_RADIUS * size, hueShift, alpha, outward = true)

        // The glow shell, sharing the apex so it hugs the hat's silhouette.
        val glowHeight = height * GLOW_SCALE
        emitCone(pose, consumer, apexY, apexY + glowHeight, radius * GLOW_SCALE, hueShift, alpha * GLOW_ALPHA, outward = true)
    }

    /**
     * One cone as a fan of triangles from the apex to the brim ring.
     *
     * Each wedge is one triangle: apex plus two brim points. The vertex colour is the chroma hue at
     * that point's angle, and the GPU blends it across the triangle, which with 32 wedges is smooth
     * to the eye. The apex takes the hue of the wedge it belongs to rather than one shared colour,
     * so the top is not a single grey point where every hue averages out.
     *
     * Winding is chosen from the face normal: [outward] true orders the vertices anticlockwise when
     * seen from outside the cone, false from inside. This is decided by geometry rather than by a
     * fixed vertex order, so it survives whatever transform the head has been given.
     *
     * The render type draws quads, so each triangle repeats its last vertex: a zero-area sliver
     * that costs nothing and keeps the vertex count a multiple of four.
     */
    private fun emitCone(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        apexY: Float,
        brimY: Float,
        radius: Float,
        hueShift: Double,
        alpha: Float,
        outward: Boolean,
    ) {
        val alphaByte = (alpha * 255f).toInt().coerceIn(0, 255)
        val height = brimY - apexY
        // Outward cone normal, before rotation about the axis: radial component proportional to the
        // height, axial component (pointing up, i.e. -y here) proportional to the radius.
        val normalLength = sqrt(height * height + radius * radius)
        val nRadial = height / normalLength
        val nUp = -radius / normalLength

        val apex = Vector3f(0f, apexY, 0f)
        for (i in 0 until SEGMENTS) {
            val a0 = i.toDouble() / SEGMENTS * Math.PI * 2
            val a1 = (i + 1).toDouble() / SEGMENTS * Math.PI * 2
            val aMid = (a0 + a1) / 2

            val p0 = Vector3f((cos(a0) * radius).toFloat(), brimY, (sin(a0) * radius).toFloat())
            val p1 = Vector3f((cos(a1) * radius).toFloat(), brimY, (sin(a1) * radius).toFloat())

            val c0 = hueColor(i.toDouble() / SEGMENTS + hueShift, alphaByte)
            val c1 = hueColor((i + 1).toDouble() / SEGMENTS + hueShift, alphaByte)
            val cApex = hueColor((i + 0.5) / SEGMENTS + hueShift, alphaByte)

            var nx = (cos(aMid) * nRadial).toFloat()
            var ny = nUp
            var nz = (sin(aMid) * nRadial).toFloat()
            if (!outward) {
                nx = -nx
                ny = -ny
                nz = -nz
            }

            // Anticlockwise about the face normal: the brim points go in whichever order makes
            // (first - apex) x (second - apex) point along the normal.
            val cross = Vector3f(p0).sub(apex).cross(Vector3f(p1).sub(apex))
            val flip = cross.dot(nx, ny, nz) < 0
            val first = if (flip) p1 else p0
            val second = if (flip) p0 else p1
            val firstColor = if (flip) c1 else c0
            val secondColor = if (flip) c0 else c1

            val u0 = i.toFloat() / SEGMENTS
            val u1 = (i + 1).toFloat() / SEGMENTS
            val uMid = (u0 + u1) / 2
            val uFirst = if (flip) u1 else u0
            val uSecond = if (flip) u0 else u1

            vertex(pose, consumer, apex, cApex, uMid, 0f, nx, ny, nz)
            vertex(pose, consumer, first, firstColor, uFirst, 1f, nx, ny, nz)
            vertex(pose, consumer, second, secondColor, uSecond, 1f, nx, ny, nz)
            vertex(pose, consumer, second, secondColor, uSecond, 1f, nx, ny, nz)
        }
    }

    private fun vertex(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        position: Vector3f,
        color: Int,
        u: Float,
        v: Float,
        nx: Float,
        ny: Float,
        nz: Float,
    ) {
        consumer.addVertex(pose, position.x, position.y, position.z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(pose, nx, ny, nz)
    }

    /** Fully saturated colour at a hue given in turns, with the alpha byte attached. */
    private fun hueColor(hueTurns: Double, alphaByte: Int): Int {
        val hue = (hueTurns - Math.floor(hueTurns)).toFloat()
        return ARGB.color(alphaByte, Mth.hsvToRgb(hue, 0.9f, 1.0f))
    }
}
