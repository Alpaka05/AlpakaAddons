package net.alpaka.addons.features.cosmetics

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.util.ARGB
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the aura effects draw with: soft glowing sprites, ribbons and flat rings, all made of quads
 * whose light is shaped by the aura shader (see `aura.fsh`).
 *
 * Effects work in the player's own frame - x and z across the ground, y up, the feet at the origin -
 * and the canvas moves that onto the player, relative to the camera. Sprites face the camera; ribbons
 * turn about their own length so they always show their full width, the way a light trail would.
 *
 * @param ox the player's feet relative to the camera; together with the camera's right and up
 *   vectors this is everything needed to face geometry towards the viewer.
 * @param strength multiplies every alpha: the Brightness setting.
 */
internal class AuraCanvas(
    private val pose: PoseStack.Pose,
    private val buffer: VertexConsumer,
    private val ox: Float,
    private val oy: Float,
    private val oz: Float,
    private val rightX: Float,
    private val rightY: Float,
    private val rightZ: Float,
    private val upX: Float,
    private val upY: Float,
    private val upZ: Float,
    private val strength: Float,
) {

    /**
     * A camera-facing glow at (x, y, z), [size] blocks across.
     *
     * @param sparkle adds the two crossing rays of a star; [spin] turns them, in radians.
     */
    fun sprite(x: Float, y: Float, z: Float, size: Float, rgb: Int, alpha: Float, sparkle: Boolean = false, spin: Float = 0f) {
        val a = alphaByte(alpha)
        if (a == 0 || size <= 0f) return
        val half = size * 0.5f
        val c = cos(spin) * half
        val s = sin(spin) * half
        // The quad's two half-diagonals in the camera's plane, turned by the spin.
        val ax = rightX * c + upX * s
        val ay = rightY * c + upY * s
        val az = rightZ * c + upZ * s
        val bx = upX * c - rightX * s
        val by = upY * c - rightY * s
        val bz = upZ * c - rightZ * s
        val px = ox + x
        val py = oy + y
        val pz = oz + z
        val u0 = if (sparkle) 2f else 0f
        val color = ARGB.color(a, rgb)
        vertex(px - ax - bx, py - ay - by, pz - az - bz, u0, 0f, color)
        vertex(px + ax - bx, py + ay - by, pz + az - bz, u0 + 1f, 0f, color)
        vertex(px + ax + bx, py + ay + by, pz + az + bz, u0 + 1f, 1f, color)
        vertex(px - ax + bx, py - ay + by, pz - az + bz, u0, 1f, color)
    }

    // A ribbon's points, collected between beginRibbon and endRibbon. Kept and regrown rather than
    // allocated per ribbon: an effect draws dozens of them every frame.
    private var count = 0
    private var xs = FloatArray(64)
    private var ys = FloatArray(64)
    private var zs = FloatArray(64)
    private var widths = FloatArray(64)
    private var colors = IntArray(64)

    fun beginRibbon() {
        count = 0
    }

    /** The next point along the ribbon, its full width there, and its color and strength. */
    fun point(x: Float, y: Float, z: Float, width: Float, rgb: Int, alpha: Float) {
        if (count == xs.size) {
            val n = count * 2
            xs = xs.copyOf(n); ys = ys.copyOf(n); zs = zs.copyOf(n)
            widths = widths.copyOf(n); colors = colors.copyOf(n)
        }
        xs[count] = ox + x
        ys[count] = oy + y
        zs[count] = oz + z
        widths[count] = width
        colors[count] = ARGB.color(alphaByte(alpha), rgb)
        count++
    }

    /**
     * Draws the points collected since [beginRibbon] as one strip.
     *
     * At each point the strip spreads sideways along the cross product of its direction and the line
     * to the camera, which is the one sideways direction that faces the viewer. The light falls off
     * across the width only (u is held at the glow's middle), so the strip reads as a beam.
     *
     * @param closed joins the last point back to the first, for rings.
     */
    fun endRibbon(closed: Boolean = false) {
        val n = count
        if (n < 2) return
        var prevLx = 0f; var prevLy = 0f; var prevLz = 0f
        var prevRx = 0f; var prevRy = 0f; var prevRz = 0f
        var prevColor = 0
        val last = if (closed) n else n - 1
        for (k in 0..last) {
            val i = if (k == n) 0 else k
            // Direction along the ribbon at this point: from the point before to the point after.
            val before = when {
                i > 0 -> i - 1
                closed -> n - 1
                else -> 0
            }
            val after = when {
                i < n - 1 -> i + 1
                closed -> 0
                else -> n - 1
            }
            val tx = xs[after] - xs[before]
            val ty = ys[after] - ys[before]
            val tz = zs[after] - zs[before]
            // The camera sits at the origin, so the point's own position is the line from it.
            val vx = xs[i]; val vy = ys[i]; val vz = zs[i]
            var sx = ty * vz - tz * vy
            var sy = tz * vx - tx * vz
            var sz = tx * vy - ty * vx
            val len = sqrt(sx * sx + sy * sy + sz * sz)
            if (len < 1e-7f) {
                // Seen exactly end-on: any sideways direction will do.
                sx = rightX; sy = rightY; sz = rightZ
            } else {
                val f = 1f / len
                sx *= f; sy *= f; sz *= f
            }
            val half = widths[i] * 0.5f
            sx *= half; sy *= half; sz *= half
            val lx = vx - sx; val ly = vy - sy; val lz = vz - sz
            val rx = vx + sx; val ry = vy + sy; val rz = vz + sz
            val color = colors[i]
            if (k > 0) {
                vertex(prevLx, prevLy, prevLz, 0.5f, 0f, prevColor)
                vertex(prevRx, prevRy, prevRz, 0.5f, 1f, prevColor)
                vertex(rx, ry, rz, 0.5f, 1f, color)
                vertex(lx, ly, lz, 0.5f, 0f, color)
            }
            prevLx = lx; prevLy = ly; prevLz = lz
            prevRx = rx; prevRy = ry; prevRz = rz
            prevColor = color
        }
        count = 0
    }

    /** A camera-facing circle of light at height [y]: [segments] points of a closed ribbon. */
    fun ring(y: Float, radius: Float, width: Float, rgb: Int, alpha: Float, segments: Int = 48) {
        beginRibbon()
        for (i in 0 until segments) {
            val a = i.toFloat() / segments * TAU
            point(cos(a) * radius, y, sin(a) * radius, width, rgb, alpha)
        }
        endRibbon(closed = true)
    }

    /**
     * A band lying flat at height [y], like a circle painted on the ground, from angle [start] over
     * [sweep] radians. [fade] dims it towards the end of the sweep, which turns an arc into a comet.
     */
    fun flatArc(
        y: Float, radius: Float, width: Float, rgb: Int, alpha: Float,
        start: Float = 0f, sweep: Float = TAU, segments: Int = 48, fade: Boolean = false,
    ) {
        val inner = radius - width * 0.5f
        val outer = radius + width * 0.5f
        var prevC = 0f; var prevS = 0f; var prevColor = 0
        for (i in 0..segments) {
            val f = i.toFloat() / segments
            val a = start + sweep * f
            val c = cos(a)
            val s = sin(a)
            val color = ARGB.color(alphaByte(if (fade) alpha * (1f - f) else alpha), rgb)
            if (i > 0) {
                vertex(ox + prevC * inner, oy + y, oz + prevS * inner, 0.5f, 0f, prevColor)
                vertex(ox + prevC * outer, oy + y, oz + prevS * outer, 0.5f, 1f, prevColor)
                vertex(ox + c * outer, oy + y, oz + s * outer, 0.5f, 1f, color)
                vertex(ox + c * inner, oy + y, oz + s * inner, 0.5f, 0f, color)
            }
            prevC = c; prevS = s; prevColor = color
        }
    }

    /** A straight band lying flat at height [y], from (x0, z0) to (x1, z1). */
    fun flatLine(y: Float, x0: Float, z0: Float, x1: Float, z1: Float, width: Float, rgb: Int, alpha: Float) {
        val dx = x1 - x0
        val dz = z1 - z0
        val len = sqrt(dx * dx + dz * dz)
        if (len < 1e-6f) return
        val nx = -dz / len * width * 0.5f
        val nz = dx / len * width * 0.5f
        val color = ARGB.color(alphaByte(alpha), rgb)
        vertex(ox + x0 - nx, oy + y, oz + z0 - nz, 0.5f, 0f, color)
        vertex(ox + x0 + nx, oy + y, oz + z0 + nz, 0.5f, 1f, color)
        vertex(ox + x1 + nx, oy + y, oz + z1 + nz, 0.5f, 1f, color)
        vertex(ox + x1 - nx, oy + y, oz + z1 - nz, 0.5f, 0f, color)
    }

    private fun alphaByte(alpha: Float): Int = (alpha * strength * 255f).toInt().coerceIn(0, 255)

    private fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float, color: Int) {
        buffer.addVertex(pose, x, y, z).setUv(u, v).setColor(color)
    }

    companion object {
        const val TAU = (Math.PI * 2).toFloat()
    }
}
