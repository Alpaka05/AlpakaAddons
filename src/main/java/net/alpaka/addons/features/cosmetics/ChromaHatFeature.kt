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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A translucent samurai hat - a jingasa, the wide, shallow straw hat - on the local
 * player's head.
 *
 * Client-side cosmetic only. The geometry is built here and handed to the renderer every frame; no
 * item, no packet, no server involvement, and nobody else can see it. It rides on the player's head
 * bone through [ChromaHatLayer], so it turns with the head and follows every pose the model takes.
 *
 * ### Shape
 *
 * The hat is a solid of revolution: a 2D profile - knob, crown, brim lip, underside - is swept
 * around the head's axis in [SEGMENTS] steps and stitched into quads. The crown is a shallow cone that
 * flares towards the brim (radius grows as a power of the height), which is what separates a proper
 * kasa from a flat disc: a defined point on top, a gentle swoop, and a brim that turns out at the
 * edge. The brim has actual thickness, closed by a rounded lip, so it reads as an object from every
 * angle, and the profile continues under the brim so the hat is not hollow from below. A small
 * two-part knob sits on the apex.
 *
 * Every wedge is shaded a little differently in three alternating steps, which reads as the woven
 * straw strips of the real thing. The texture only supplies alpha and a faint weave; every colour
 * comes from the vertices.
 *
 * Two looks: plain (the configured colour, lit by world light like the rest of the model) and
 * chroma (the hue runs once around the hat and rotates with time, drawn emissive so it glows in a
 * dark cave exactly as it does at noon).
 */
object ChromaHatFeature {

    /** Shipped in the mod's own assets; a plain white weave that the vertex colours tint. */
    @JvmField
    val TEXTURE: Identifier = Identifier.fromNamespaceAndPath("alpaka", "textures/cosmetics/chroma_hat.png")

    /** Packed lightmap coordinate for full brightness. Emissive geometry has to hand over something. */
    private const val FULL_BRIGHT = 0xF000F0

    /** Wedges around the hat. 48 keeps the brim round up close without a visible polygon count. */
    private const val SEGMENTS = 48

    /**
     * The head the hat rests on, in head-part space, where +y points *down* the body.
     *
     * Measured on the skin's outer "hat" layer rather than the bare head cube, since that is what
     * every skin actually shows: the cube inflated by half a pixel, so it spans 4.5/16 of a block
     * either side and reaches up to -8.5/16. A helmet's own cube is inflated by a full pixel on top
     * of that, hence the larger figures when one is worn.
     */
    private const val HEAD_TOP = -8.5f / 16f
    private const val HEAD_HALF_WIDTH = 4.5f / 16f
    private const val HELMET_TOP = -9f / 16f
    private const val HELMET_HALF_WIDTH = 5f / 16f

    /** Brim radius and crown height at size 1, in blocks. Wide and shallow is what reads as a jingasa. */
    private const val BRIM_RADIUS = 0.72f
    private const val CROWN_HEIGHT = 0.30f

    /**
     * How the crown flares: radius = BRIM_RADIUS * t^FLARE for height fraction t from the apex. Above 1
     * the cone is steep at the point and eases out towards the brim - the classic kasa swoop.
     */
    private const val FLARE = 1.12f

    /** Rings along the crown; more rings, smoother swoop. */
    private const val CROWN_RINGS = 7

    /** Thickness of the brim and the radius of its rounded lip. */
    private const val BRIM_THICKNESS = 0.045f

    /** Inner radius where the underside stops; anything nearer the axis is inside the head. */
    private const val UNDERSIDE_INNER_RADIUS = 0.30f

    /** The knob on the apex: a short neck and a bulb. */
    private const val KNOB_NECK_RADIUS = 0.028f
    private const val KNOB_BULB_RADIUS = 0.045f
    private const val KNOB_HEIGHT = 0.08f

    /** The underside is drawn a little fainter than the top so the two do not stack into a solid. */
    private const val UNDERSIDE_ALPHA = 0.7f

    /** Shade multipliers for the knob and the brim lip, relative to the hat colour. */
    private const val KNOB_SHADE = 0.45f
    private const val LIP_SHADE = 0.62f

    /** The three brightness steps of the woven strips, cycling around the hat. */
    private val STRIP_SHADES = floatArrayOf(1.0f, 0.86f, 0.93f)

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

    /** One point of the revolved profile: radius from the axis and height (head space, +y down). */
    private class ProfilePoint(val r: Float, val y: Float, val shade: Float, val alpha: Float)

    /**
     * Emits the hat's quads. The pose is the head part's own space (see [ChromaHatLayer]).
     *
     * @param wearingHelmet lifts the hat so it does not intersect a helmet's overlay cube.
     * @param rainbow chroma colours sweeping around the hat, self-lit; false is the plain hat in the
     *   configured colour, lit by [light] like the rest of the model.
     */
    fun emit(pose: PoseStack.Pose, consumer: VertexConsumer, wearingHelmet: Boolean, rainbow: Boolean, light: Int) {
        val cfg = AlpakaConfig.instance
        val alpha = (cfg.chromaHatOpacity / 100f).coerceIn(0.05f, 1f)
        val size = cfg.chromaHatSize
        val time = (System.currentTimeMillis() % 3_600_000L) / 1000.0 * cfg.chromaHatSpeed
        val hueShift = time * HUE_TURNS_PER_SECOND

        val plainRgb = cfg.chromaHatColor and 0xFFFFFF
        // Colour of a vertex: its position around the brim in turns, a shade multiplier, an alpha byte.
        val colorAt: (Double, Float, Int) -> Int = if (rainbow) {
            { turns, shade, a -> ARGB.color(a, ARGB.scaleRGB(Mth.hsvToRgb(fract(turns + hueShift), 0.9f, 1.0f), shade)) }
        } else {
            { _, shade, a -> ARGB.color(a, ARGB.scaleRGB(plainRgb, shade)) }
        }
        val vertexLight = if (rainbow) FULL_BRIGHT else light

        val height = CROWN_HEIGHT * size
        val radius = BRIM_RADIUS * size

        // Seated rather than hovering: the crown is lowered until its surface just touches the head's
        // top corners - the points of the head box farthest from its axis - so it rests on the head
        // the way a real kasa does, while every part of it stays outside the skin. With the flared
        // profile the height at a given radius follows the inverse of the flare curve.
        val headTop = if (wearingHelmet) HELMET_TOP else HEAD_TOP
        val halfWidth = if (wearingHelmet) HELMET_HALF_WIDTH else HEAD_HALF_WIDTH
        val cornerRadius = halfWidth * sqrt(2f)
        val tAtCorner = (cornerRadius / radius).coerceIn(0f, 1f).pow(1f / FLARE)
        val apexY = headTop - height * tAtCorner - cfg.chromaHatHeightOffset
        val brimY = apexY + height

        val profile = ArrayList<ProfilePoint>()

        // Knob: bulb on a short neck, standing on the apex.
        val knobTop = apexY - KNOB_HEIGHT * size
        profile += ProfilePoint(0f, knobTop, KNOB_SHADE, alpha)
        profile += ProfilePoint(KNOB_BULB_RADIUS * size * 0.7f, knobTop + KNOB_HEIGHT * size * 0.12f, KNOB_SHADE, alpha)
        profile += ProfilePoint(KNOB_BULB_RADIUS * size, knobTop + KNOB_HEIGHT * size * 0.4f, KNOB_SHADE, alpha)
        profile += ProfilePoint(KNOB_BULB_RADIUS * size * 0.7f, knobTop + KNOB_HEIGHT * size * 0.68f, KNOB_SHADE, alpha)
        profile += ProfilePoint(KNOB_NECK_RADIUS * size, knobTop + KNOB_HEIGHT * size * 0.72f, KNOB_SHADE, alpha)
        profile += ProfilePoint(KNOB_NECK_RADIUS * size, apexY, KNOB_SHADE, alpha)

        // Crown: the flared cone from the apex out to the brim.
        for (i in 1..CROWN_RINGS) {
            val t = i.toFloat() / CROWN_RINGS
            profile += ProfilePoint(radius * t.pow(FLARE), apexY + height * t, 1.0f, alpha)
        }

        // Brim lip: a rounded edge that gives the brim its thickness.
        val lip = BRIM_THICKNESS * size
        profile += ProfilePoint(radius + lip * 0.8f, brimY + lip * 0.35f, LIP_SHADE, alpha)
        profile += ProfilePoint(radius + lip * 0.8f, brimY + lip * 0.75f, LIP_SHADE, alpha)
        profile += ProfilePoint(radius, brimY + lip, LIP_SHADE, alpha)

        // Underside: back in towards the head, one thickness below the crown surface.
        for (i in CROWN_RINGS downTo 1) {
            val t = i.toFloat() / CROWN_RINGS
            val r = radius * t.pow(FLARE)
            if (r < UNDERSIDE_INNER_RADIUS * size) break
            profile += ProfilePoint(r, apexY + height * t + lip, 0.9f, alpha * UNDERSIDE_ALPHA)
        }

        emitLathe(pose, consumer, profile, colorAt, vertexLight)
    }

    /**
     * Sweeps the profile around the y axis and stitches consecutive rings into quads.
     *
     * The face normal comes from the profile itself: for a run from one point to the next, the 2D
     * normal in the (radius, height) plane is (dy, -dr), which points away from the surface's outer
     * side as long as the profile is traced top-first and outwards - which it is. That normal, rotated
     * to each wedge's angle, also decides the vertex order, chosen so the quad's winding matches it
     * whatever transform the head has been given.
     *
     * A ring of radius zero simply collapses its two vertices onto the axis, which is how the knob's
     * tip is closed without a special case.
     */
    private fun emitLathe(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        profile: List<ProfilePoint>,
        colorAt: (Double, Float, Int) -> Int,
        light: Int,
    ) {
        for (p in 0 until profile.size - 1) {
            val a = profile[p]
            val b = profile[p + 1]
            val dr = b.r - a.r
            val dy = b.y - a.y
            val len = sqrt(dr * dr + dy * dy)
            if (len < 1e-6f) continue
            // 2D normal of this run, radial and axial components.
            val nRadial = dy / len
            val nAxial = -dr / len

            val alphaA = (a.alpha * 255f).toInt().coerceIn(0, 255)
            val alphaB = (b.alpha * 255f).toInt().coerceIn(0, 255)

            for (i in 0 until SEGMENTS) {
                val a0 = i.toDouble() / SEGMENTS * Math.PI * 2
                val a1 = (i + 1).toDouble() / SEGMENTS * Math.PI * 2
                val aMid = (a0 + a1) / 2
                val strip = STRIP_SHADES[i % STRIP_SHADES.size]

                val p00 = Vector3f((cos(a0) * a.r).toFloat(), a.y, (sin(a0) * a.r).toFloat())
                val p01 = Vector3f((cos(a1) * a.r).toFloat(), a.y, (sin(a1) * a.r).toFloat())
                val p10 = Vector3f((cos(a0) * b.r).toFloat(), b.y, (sin(a0) * b.r).toFloat())
                val p11 = Vector3f((cos(a1) * b.r).toFloat(), b.y, (sin(a1) * b.r).toFloat())

                val nx = (cos(aMid) * nRadial).toFloat()
                val ny = nAxial
                val nz = (sin(aMid) * nRadial).toFloat()

                val turns0 = i.toDouble() / SEGMENTS
                val turns1 = (i + 1).toDouble() / SEGMENTS
                val c00 = colorAt(turns0, a.shade * strip, alphaA)
                val c01 = colorAt(turns1, a.shade * strip, alphaA)
                val c10 = colorAt(turns0, b.shade * strip, alphaB)
                val c11 = colorAt(turns1, b.shade * strip, alphaB)

                val u0 = i.toFloat() / SEGMENTS
                val u1 = (i + 1).toFloat() / SEGMENTS
                val v0 = (p.toFloat() / (profile.size - 1))
                val v1 = ((p + 1).toFloat() / (profile.size - 1))

                // Winding: p00 -> p01 -> p11 -> p10 is anticlockwise about the normal when the cross
                // product of its first two edges points along it; otherwise walk the other way.
                val cross = Vector3f(p01).sub(p00).cross(Vector3f(p11).sub(p00))
                val flip = cross.dot(nx, ny, nz) < 0
                if (!flip) {
                    vertex(pose, consumer, p00, c00, u0, v0, nx, ny, nz, light)
                    vertex(pose, consumer, p01, c01, u1, v0, nx, ny, nz, light)
                    vertex(pose, consumer, p11, c11, u1, v1, nx, ny, nz, light)
                    vertex(pose, consumer, p10, c10, u0, v1, nx, ny, nz, light)
                } else {
                    vertex(pose, consumer, p00, c00, u0, v0, nx, ny, nz, light)
                    vertex(pose, consumer, p10, c10, u0, v1, nx, ny, nz, light)
                    vertex(pose, consumer, p11, c11, u1, v1, nx, ny, nz, light)
                    vertex(pose, consumer, p01, c01, u1, v0, nx, ny, nz, light)
                }
            }
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
        light: Int,
    ) {
        consumer.addVertex(pose, position.x, position.y, position.z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz)
    }

    private fun fract(x: Double): Float = (x - Math.floor(x)).toFloat()
}
