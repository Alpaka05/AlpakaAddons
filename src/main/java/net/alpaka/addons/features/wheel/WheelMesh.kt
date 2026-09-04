package net.alpaka.addons.features.wheel

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Smooth, colour-only 2D geometry for the GUI renderer: discs, ring segments and small convex
 * shapes such as the wheel's needle.
 *
 * The GUI only hands out axis-aligned rectangles, so an earlier wheel drew its circle as a stack of
 * one-pixel scanlines and looked like a staircase. This builds real quads instead and submits them
 * as one [GuiElementRenderState] on the plain colour pipeline - the same pipeline `fill` uses, so
 * they batch with everything else and cost nothing worth measuring.
 *
 * Every edge gets a feather strip about one real pixel wide whose alpha runs out to zero. That is
 * the whole anti-aliasing story: the GPU interpolates the alpha across the strip and the edge reads
 * as smooth at any GUI scale. [feather] is that width in GUI units, i.e. roughly `1 / guiScale`.
 *
 * Angles are radians in screen space: 0 points right and positive turns clockwise, because y grows
 * downwards. Vertices are transformed by whatever pose is current when [submit] is called, so an
 * open animation that scales the pose scales this geometry too.
 */
class WheelMesh(private val feather: Float) {
    private var xy = FloatArray(2048)
    private var colors = IntArray(1024)
    private var count = 0

    private fun vertex(x: Float, y: Float, color: Int) {
        if (count == colors.size) {
            xy = xy.copyOf(xy.size * 2)
            colors = colors.copyOf(colors.size * 2)
        }
        xy[2 * count] = x
        xy[2 * count + 1] = y
        colors[count] = color
        count++
    }

    /**
     * One quad, always emitted in the winding vanilla uses for its own rectangles (top-left,
     * bottom-left, bottom-right, top-right), so nothing depends on whether the pipeline culls.
     */
    fun quad(
        x0: Float, y0: Float, c0: Int,
        x1: Float, y1: Float, c1: Int,
        x2: Float, y2: Float, c2: Int,
        x3: Float, y3: Float, c3: Int
    ) {
        val signedArea = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3)
        if (signedArea <= 0f) {
            vertex(x0, y0, c0); vertex(x1, y1, c1); vertex(x2, y2, c2); vertex(x3, y3, c3)
        } else {
            vertex(x0, y0, c0); vertex(x3, y3, c3); vertex(x2, y2, c2); vertex(x1, y1, c1)
        }
    }

    /** A filled disc. */
    fun disc(cx: Float, cy: Float, radius: Float, color: Int) {
        ringSector(cx, cy, 0f, radius, 0f, TAU, 0f, TAU, color, color, fullCircle = true)
    }

    /** A disc whose colour runs from [centerColor] in the middle to [edgeColor] at the rim. */
    fun radialGradient(cx: Float, cy: Float, radius: Float, centerColor: Int, edgeColor: Int) {
        ringSector(cx, cy, 0f, radius, 0f, TAU, 0f, TAU, centerColor, edgeColor, fullCircle = true)
    }

    /** A complete ring between two radii. */
    fun ring(cx: Float, cy: Float, innerRadius: Float, outerRadius: Float, color: Int) {
        ringSector(cx, cy, innerRadius, outerRadius, 0f, TAU, 0f, TAU, color, color, fullCircle = true)
    }

    /**
     * A ring segment. The inner and outer arcs may cover different angle ranges: a segment that
     * wants a constant pixel gap to its neighbour needs a wider angular gap at the inner radius than
     * at the outer one, and passing both ranges keeps that arithmetic with the caller.
     *
     * The colour is interpolated radially from [colorIn] to [colorOut]. With [fullCircle] the two
     * radial edges are not feathered, since they coincide.
     */
    fun ringSector(
        cx: Float, cy: Float,
        rIn: Float, rOut: Float,
        aIn0: Float, aIn1: Float,
        aOut0: Float, aOut1: Float,
        colorIn: Int, colorOut: Int,
        fullCircle: Boolean = false
    ) {
        if (rOut <= rIn || rOut <= 0f) return
        val half = feather / 2f
        val hasInnerEdge = rIn > half

        // Radial bands: the feathered inner edge, the solid body, the feathered outer edge. A ring
        // thinner than the two feathers collapses to a hairline that peaks in the middle.
        var bodyStart = if (hasInnerEdge) rIn + half else 0f
        var bodyEnd = rOut - half
        if (bodyEnd <= bodyStart) {
            val mid = (max(rIn, 0f) + rOut) / 2f
            bodyStart = mid
            bodyEnd = mid
        }
        val innermost = if (hasInnerEdge) rIn - half else 0f
        val outermost = rOut + half

        val steps = max(1, ceil(abs(aOut1 - aOut0) * rOut / 3f).toInt())
        val radialSpan = rOut - rIn

        fun angleAt(r: Float, t: Float): Float {
            val inner = aIn0 + (aIn1 - aIn0) * t
            val outer = aOut0 + (aOut1 - aOut0) * t
            val k = ((r - rIn) / radialSpan).coerceIn(0f, 1f)
            return inner + (outer - inner) * k
        }

        fun colorAt(r: Float, alphaScale: Float): Int {
            val k = ((r - rIn) / radialSpan).coerceIn(0f, 1f)
            return scaleAlpha(lerpColor(colorIn, colorOut, k), alphaScale)
        }

        fun band(r0: Float, alpha0: Float, r1: Float, alpha1: Float) {
            if (r1 <= r0) return
            for (i in 0 until steps) {
                val t0 = i / steps.toFloat()
                val t1 = (i + 1) / steps.toFloat()
                val a00 = angleAt(r0, t0); val a01 = angleAt(r0, t1)
                val a10 = angleAt(r1, t0); val a11 = angleAt(r1, t1)
                val c0 = colorAt(r0, alpha0)
                val c1 = colorAt(r1, alpha1)
                quad(
                    cx + r0 * cos(a00), cy + r0 * sin(a00), c0,
                    cx + r0 * cos(a01), cy + r0 * sin(a01), c0,
                    cx + r1 * cos(a11), cy + r1 * sin(a11), c1,
                    cx + r1 * cos(a10), cy + r1 * sin(a10), c1
                )
            }
        }

        if (hasInnerEdge) band(innermost, 0f, bodyStart, 1f)
        band(bodyStart, 1f, bodyEnd, 1f)
        band(bodyEnd, 1f, outermost, 0f)

        if (fullCircle) return

        // The two radial edges: a strip just outside each edge fading out, one quad per radial band
        // so it follows the body's colour gradient and its feathered ends.
        fun radialEdge(t: Float, direction: Float) {
            fun edgeBand(r0: Float, alpha0: Float, r1: Float, alpha1: Float) {
                if (r1 <= r0) return
                val a0 = angleAt(r0, t)
                val a1 = angleAt(r1, t)
                val off0 = if (r0 > 0.01f) direction * feather / r0 else 0f
                val off1 = direction * feather / r1
                val c0 = colorAt(r0, alpha0)
                val c1 = colorAt(r1, alpha1)
                quad(
                    cx + r0 * cos(a0), cy + r0 * sin(a0), c0,
                    cx + r1 * cos(a1), cy + r1 * sin(a1), c1,
                    cx + r1 * cos(a1 + off1), cy + r1 * sin(a1 + off1), scaleAlpha(c1, 0f),
                    cx + r0 * cos(a0 + off0), cy + r0 * sin(a0 + off0), scaleAlpha(c0, 0f)
                )
            }
            if (hasInnerEdge) edgeBand(innermost, 0f, bodyStart, 1f)
            edgeBand(bodyStart, 1f, bodyEnd, 1f)
            edgeBand(bodyEnd, 1f, outermost, 0f)
        }

        radialEdge(0f, -1f)
        radialEdge(1f, 1f)
    }

    /**
     * A filled convex polygon given as x/y pairs, feathered along every edge. Used for the needle;
     * the shape is small, so a fan from the first vertex is all the triangulation it needs.
     */
    fun convexPolygon(points: FloatArray, color: Int) {
        val n = points.size / 2
        if (n < 3) return

        var centroidX = 0f
        var centroidY = 0f
        for (i in 0 until n) {
            centroidX += points[2 * i]
            centroidY += points[2 * i + 1]
        }
        centroidX /= n
        centroidY /= n

        for (i in 1 until n - 1) {
            quad(
                points[0], points[1], color,
                points[2 * i], points[2 * i + 1], color,
                points[2 * i + 2], points[2 * i + 3], color,
                points[2 * i + 2], points[2 * i + 3], color
            )
        }

        val transparent = scaleAlpha(color, 0f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x0 = points[2 * i]; val y0 = points[2 * i + 1]
            val x1 = points[2 * j]; val y1 = points[2 * j + 1]
            val ex = x1 - x0
            val ey = y1 - y0
            val len = sqrt(ex * ex + ey * ey)
            if (len < 0.0001f) continue
            var nx = ey / len
            var ny = -ex / len
            // Make sure the normal points away from the shape.
            if (nx * (centroidX - x0) + ny * (centroidY - y0) > 0f) {
                nx = -nx
                ny = -ny
            }
            quad(
                x0, y0, color,
                x1, y1, color,
                x1 + nx * feather, y1 + ny * feather, transparent,
                x0 + nx * feather, y0 + ny * feather, transparent
            )
        }
    }

    /**
     * Hands everything collected so far to the GUI renderer as one element and starts afresh.
     *
     * Overlapping elements are placed on successive layers by the renderer, so a mesh submitted
     * later is drawn on top of one submitted earlier - the hub can be submitted after the ring and
     * the needle after the hub without any depth bookkeeping here.
     */
    fun submit(graphics: GuiGraphicsExtractor) {
        if (count == 0) return

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until count) {
            val x = xy[2 * i]
            val y = xy[2 * i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        val pose = Matrix3x2f(graphics.pose())
        val local = ScreenRectangle(
            floor(minX).toInt(), floor(minY).toInt(),
            ceil(maxX - minX).toInt() + 1, ceil(maxY - minY).toInt() + 1
        )
        var bounds: ScreenRectangle? = local.transformMaxBounds(pose)
        val scissor: ScreenRectangle? = graphics.scissorStack.peek()
        if (scissor != null && bounds != null) {
            bounds = bounds.intersection(scissor)
        }
        if (bounds == null) {
            count = 0
            return
        }

        graphics.guiRenderState.addGuiElement(
            Element(pose, xy.copyOf(2 * count), colors.copyOf(count), count, scissor, bounds)
        )
        count = 0
    }

    private class Element(
        private val pose: Matrix3x2fc,
        private val xy: FloatArray,
        private val colors: IntArray,
        private val count: Int,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            for (i in 0 until count) {
                consumer.addVertexWith2DPose(pose, xy[2 * i], xy[2 * i + 1]).setColor(colors[i])
            }
        }

        override fun pipeline(): RenderPipeline = RenderPipelines.GUI
        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun bounds(): ScreenRectangle = bounds
    }

    companion object {
        const val TAU: Float = (Math.PI * 2.0).toFloat()

        /** Linear blend of two ARGB colours, alpha included. */
        @JvmStatic
        fun lerpColor(from: Int, to: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            val a = ((from ushr 24) + (((to ushr 24) - (from ushr 24)) * k)).toInt()
            val r = (((from shr 16) and 0xFF) + ((((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * k)).toInt()
            val g = (((from shr 8) and 0xFF) + ((((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * k)).toInt()
            val b = ((from and 0xFF) + (((to and 0xFF) - (from and 0xFF)) * k)).toInt()
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        /** The colour with its alpha multiplied by [factor]. */
        @JvmStatic
        fun scaleAlpha(color: Int, factor: Float): Int {
            val a = ((color ushr 24) * factor.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            return (a shl 24) or (color and 0x00FFFFFF)
        }
    }
}
