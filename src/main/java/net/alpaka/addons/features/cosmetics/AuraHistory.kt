package net.alpaka.addons.features.cosmetics

import java.lang.ref.WeakReference

/**
 * Where the player stood over the last few seconds of aura time, so the storm can leave things on the
 * ground - scorch marks, embers, the light of a strike - that stay where they landed while the player
 * runs on, instead of sliding along with them. It also remembers where the camera looked from, so a
 * strike stays where it was aimed when the mouse moves.
 *
 * The aura is otherwise drawn as a pure function of time; this is its only memory. Samples are taken
 * at most every [STEP] seconds of aura time into a ring of [SIZE], which holds about six seconds,
 * longer than anything on the ground lasts. The newest sample is overwritten every frame, so "now" is
 * always exact.
 */
internal class AuraHistory {

    private val times = DoubleArray(SIZE)
    private val xs = DoubleArray(SIZE)
    private val ys = DoubleArray(SIZE)
    private val zs = DoubleArray(SIZE)
    private val drops = FloatArray(SIZE)
    private val yaws = FloatArray(SIZE)
    private val horizs = FloatArray(SIZE)

    /** Index of the newest sample, and how many there are. */
    private var head = -1
    private var count = 0

    /** Weak, so a level left behind is never kept alive by the aura. */
    private var levelRef: WeakReference<Any>? = null

    /**
     * The aura time the history last started over at. Anything that landed before it landed somewhere
     * the history no longer knows - before a teleport, say - and is not drawn on the ground.
     */
    var validFrom = Double.NEGATIVE_INFINITY
        private set

    /** Forgets everything, and with it any time limit on what may still be drawn on the ground. */
    fun clear() {
        head = -1
        count = 0
        validFrom = Double.NEGATIVE_INFINITY
    }

    /**
     * Records the frame. Starts over when time runs backwards, after a gap (the storm was not being
     * drawn), when the player moves further in one frame than they could walk or fall - a teleport or
     * an Aspect of the End - and in a new level: a ground mark from before any of those is meaningless.
     *
     * @param drop how far the ground is below the feet; NaN when there is none in reach.
     * @param camYaw the camera's direction seen from the feet, as the storm measures it.
     * @param camHoriz the camera's distance from the feet across the ground.
     */
    fun record(time: Double, x: Double, y: Double, z: Double, drop: Float, camYaw: Float, camHoriz: Float, level: Any?) {
        if (count > 0) {
            val dx = x - xs[head]
            val dy = y - ys[head]
            val dz = z - zs[head]
            if (time < times[head] - 1e-9 || time - times[head] > 1.0 || dx * dx + dy * dy + dz * dz > 9.0 ||
                level !== levelRef?.get()) {
                clear()
                validFrom = time
            }
        }
        if (levelRef?.get() !== level) levelRef = if (level == null) null else WeakReference(level)
        if (count == 0 || time - previousTime() >= STEP) {
            head = (head + 1) % SIZE
            if (count < SIZE) count++
        }
        times[head] = time
        xs[head] = x
        ys[head] = y
        zs[head] = z
        drops[head] = drop
        yaws[head] = camYaw
        horizs[head] = camHoriz
    }

    /** The time of the sample before the newest one, which decides whether the newest is overwritten. */
    private fun previousTime(): Double = if (count < 2) Double.NEGATIVE_INFINITY else times[(head - 1 + SIZE) % SIZE]

    /** The i-th oldest sample's slot. */
    private fun slot(i: Int): Int = (head - count + 1 + i + SIZE * 2) % SIZE

    private val found = IntArray(2)

    /**
     * Finds where [time] falls: the slots of the samples either side of it into [found], and the
     * fraction of the way from the first to the second. Clamped to the oldest and newest sample.
     */
    private fun locate(time: Double): Float {
        var lo = 0
        var hi = count - 1
        if (time <= times[slot(0)]) {
            found[0] = slot(0); found[1] = slot(0)
            return 0f
        }
        if (time >= times[slot(hi)]) {
            found[0] = slot(hi); found[1] = slot(hi)
            return 0f
        }
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[slot(mid)] <= time) lo = mid else hi = mid
        }
        val a = slot(lo)
        val b = slot(hi)
        found[0] = a; found[1] = b
        val span = times[b] - times[a]
        return if (span <= 0.0) 0f else ((time - times[a]) / span).toFloat()
    }

    /**
     * How far the feet were at [time] from where they are now, as x, y, z into [out]; zero with no
     * history, or for a time that has not come yet.
     */
    fun shift(time: Double, out: FloatArray) {
        if (count == 0) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f
            return
        }
        val k = locate(time)
        val a = found[0]
        val b = found[1]
        out[0] = (xs[a] + (xs[b] - xs[a]) * k - xs[head]).toFloat()
        out[1] = (ys[a] + (ys[b] - ys[a]) * k - ys[head]).toFloat()
        out[2] = (zs[a] + (zs[b] - zs[a]) * k - zs[head]).toFloat()
    }

    /** How far the ground was below the feet at [time]; NaN when there was none in reach. */
    fun dropAt(time: Double): Float {
        if (count == 0) return 0f
        val k = locate(time)
        return drops[found[0]] + (drops[found[1]] - drops[found[0]]) * k
    }

    /** The camera's direction from the feet at [time], or [fallback] with no history. */
    fun camYawAt(time: Double, fallback: Float): Float {
        if (count == 0) return fallback
        val k = locate(time)
        return yaws[if (k < 0.5f) found[0] else found[1]]
    }

    /** The camera's distance from the feet across the ground at [time], or [fallback] with no history. */
    fun camHorizAt(time: Double, fallback: Float): Float {
        if (count == 0) return fallback
        val k = locate(time)
        return horizs[if (k < 0.5f) found[0] else found[1]]
    }

    companion object {
        const val SIZE = 256
        const val STEP = 0.025
    }
}
