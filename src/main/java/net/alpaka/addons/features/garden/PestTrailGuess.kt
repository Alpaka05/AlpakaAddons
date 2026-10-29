package net.alpaka.addons.features.garden

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Works out where a vacuum's particle trail is heading.
 *
 * A left click with a vacuum makes Hypixel draw a short trail of angry villager particles from the
 * player towards the nearest pest. The trail is a Bézier curve whose far end is the pest, but only
 * its first few blocks are drawn. Fitting a cubic to the drawn points, one per axis with the point's
 * place in the trail as the parameter, gives the curve; its slope at the start and how steeply it
 * climbs tell how far along the full curve the pest lies. The method and its constants are
 * SkyHanni's (Pest Particle Waypoint), see CREDITS.md.
 */
class PestTrailGuess {

    private val xs = ArrayList<Double>()
    private val ys = ArrayList<Double>()
    private val zs = ArrayList<Double>()

    val size: Int get() = xs.size

    fun isEmpty(): Boolean = xs.isEmpty()

    fun clear() {
        xs.clear()
        ys.clear()
        zs.clear()
    }

    /**
     * Adds a particle if it continues the trail: within [MAX_STEP] of the last point and not a
     * repeat of any earlier one. Hypixel sends some positions twice, sometimes interleaved, and a
     * repeat would stall the curve's parameter and throw the guess far off. The first point is taken
     * as given; the caller decides whether it can start a trail.
     */
    fun tryAdd(x: Double, y: Double, z: Double): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return false
        if (xs.isNotEmpty()) {
            val last = xs.size - 1
            if (distSq(x, y, z, last) > MAX_STEP * MAX_STEP) return false
            for (i in xs.indices) {
                if (distSq(x, y, z, i) < REPEAT_TOLERANCE_SQ) return false
            }
        }
        xs.add(x)
        ys.add(y)
        zs.add(z)
        return true
    }

    private fun distSq(x: Double, y: Double, z: Double, i: Int): Double {
        val dx = x - xs[i]
        val dy = y - ys[i]
        val dz = z - zs[i]
        return dx * dx + dy * dy + dz * dz
    }

    /** The guessed pest position as (x, y, z), or null until there are enough points. */
    fun solve(): DoubleArray? {
        if (xs.size <= DEGREE) return null
        val cx = fit(xs) ?: return null
        val cy = fit(ys) ?: return null
        val cz = fit(zs) ?: return null

        // The slope at the start of the trail; c1 is the derivative of the polynomial at 0.
        val dx = cx[1]
        val dy = cy[1]
        val dz = cz[1]
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1e-9) return null

        val t = 3 * controlPointDistance(dx, dy, dz) / length
        val guess = doubleArrayOf(eval(cx, t), eval(cy, t), eval(cz, t))
        return if (guess.all { it.isFinite() }) guess else null
    }

    companion object {
        /** The trail is fitted with a cubic, so four points are the least that can be solved. */
        const val DEGREE = 3

        /** How far apart two neighboring trail particles may be, in blocks. */
        const val MAX_STEP = 3.0

        /** Two particles closer than 0.01 blocks are the same one sent again. */
        private const val REPEAT_TOLERANCE_SQ = 0.0001

        /** Least-squares coefficients c0..c3 of [values] over t = 0, 1, 2, ... */
        internal fun fit(values: List<Double>): DoubleArray? {
            val n = DEGREE + 1
            // Normal equations: (TᵀT) c = Tᵀv, with T[i][k] = i^k.
            val a = Array(n) { DoubleArray(n + 1) }
            for ((i, v) in values.withIndex()) {
                val powers = DoubleArray(2 * n - 1)
                powers[0] = 1.0
                for (k in 1 until powers.size) powers[k] = powers[k - 1] * i
                for (r in 0 until n) {
                    for (c in 0 until n) a[r][c] += powers[r + c]
                    a[r][n] += powers[r] * v
                }
            }
            return solveLinear(a)
        }

        /** Gaussian elimination with partial pivoting on an augmented n×(n+1) matrix. */
        private fun solveLinear(a: Array<DoubleArray>): DoubleArray? {
            val n = a.size
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r
                if (Math.abs(a[pivot][col]) < 1e-12) return null
                val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r][col] / a[col][col]
                    if (f == 0.0) continue
                    for (c in col..n) a[r][c] -= f * a[col][c]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }

        private fun eval(c: DoubleArray, t: Double): Double {
            var result = 0.0
            for (k in c.indices.reversed()) result = result * t + c[k]
            return result
        }

        /**
         * How far the curve's first control point sits from the start, from how steeply the trail
         * leaves the player. Hypixel bends the start of the trail upwards, so the pitch the trail
         * shows is atan2(sin p - 0.75, cos p) of the real one; p is found from it by bisection.
         */
        internal fun controlPointDistance(dx: Double, dy: Double, dz: Double): Double {
            val shownPitch = -atan2(dy, sqrt(dx * dx + dz * dz))
            var low = -PI / 2
            var high = PI / 2
            var pitch = shownPitch
            repeat(100) {
                val shown = atan2(sin(pitch) - 0.75, cos(pitch))
                if (shown == shownPitch) return@repeat
                if (shown < shownPitch) low = pitch else high = pitch
                pitch = (low + high) / 2
            }
            return sqrt(24 * sin(pitch - PI) + 25)
        }
    }
}
