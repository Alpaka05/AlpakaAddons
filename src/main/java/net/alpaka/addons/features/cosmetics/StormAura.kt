package net.alpaka.addons.features.cosmetics

import net.alpaka.addons.features.cosmetics.AuraCanvas.Companion.MODE_PLASMA
import net.alpaka.addons.features.cosmetics.AuraCanvas.Companion.MODE_SHARD
import net.alpaka.addons.features.cosmetics.AuraCanvas.Companion.SCORCH_U
import net.alpaka.addons.features.cosmetics.AuraCanvas.Companion.STREAK_U
import net.alpaka.addons.features.cosmetics.AuraCanvas.Companion.TAU
import net.alpaka.addons.features.cosmetics.AuraEffects.hash
import net.alpaka.addons.features.cosmetics.AuraEffects.mix
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Storm aura: the player as the god of thunder, standing in the eye of a supercell.
 *
 * A wide thunderhead hangs over the head - a broad inky base, towers piled on it, an anvil sheared
 * off behind and dark scraps of scud wheeling round its rim - and lights up from inside wherever
 * lightning runs through it. Rain spirals down round a dry eye, wind spirals climb from the ankles
 * into the cloud with chips of debris tumbling up the funnel, St. Elmo's fire crawls over the body,
 * plasma writhes in the fists, and the eyes burn with two wings of light streaming back from them
 * like a winged helmet.
 *
 * ### The strike clock
 *
 * The storm runs on four-second cycles. Each one is calm at first, then builds - the cloud darkens,
 * the vortex and the rain spin up, ambient bolts come faster, the charge under the cloud swells -
 * then holds its breath for a moment, frozen and dark, while a stepped leader feels its way down, and
 * then lets go: one huge strike with its return strokes, and a long cooling afterglow. Three kinds of
 * strike take turns:
 *
 * - **Hammerfall**: one colossal branching bolt out of the cloud's edge, landing well to the side
 *   and cracking the ground open.
 * - **Thunder Volley**: three bolts fanning down behind the player, ba-ba-BAM.
 * - **Thor's Call**: the sky splits, a bolt arcs down onto the player's head, the cloud flashes from
 *   inside, and the player hurls six bolts into the ground around them.
 *
 * ### Keeping it playable
 *
 * Everything bright lands beside or behind the player, never between the camera and them, so the
 * body stands out as a silhouette against the flash instead of being covered by it. Only thin
 * channels flicker, at most three peaks a second; every large glow flashes once and fades, nothing
 * flashes the screen, and the storm never runs faster than 1.5x (see AuraFeature). Vanilla's Hide
 * Lightning Flashes setting tones every flash down and steadies the crackle. The cloud opens towards
 * the camera, fades along the line of sight and near the camera, and ducks under low roofs.
 *
 * Like every aura, each frame is worked out from the time alone; the only memory is [AuraHistory],
 * which lets marks on the ground stay put while the player runs on. Render thread only: the scratch
 * buffers below are shared between frames.
 */
internal object StormAura {

    private const val PI = Math.PI.toFloat()
    private const val WHITE = 0xFFFFFF

    private const val HAMMERFALL = 0
    private const val VOLLEY = 1
    private const val THORS_CALL = 2

    /**
     * Return strokes, in real seconds after the strike, and how bright each is. Kept to three peaks
     * a second at most, for photosensitive players: the Volley and the fan already bring their own
     * rhythm, one stroke per bolt.
     */
    private val HAMMER_DELAYS = floatArrayOf(0f, 0.12f, 0.29f)
    private val HAMMER_PEAKS = floatArrayOf(1f, 0.8f, 0.6f)
    private val THOR_DELAYS = floatArrayOf(0f, 0.32f)
    private val THOR_PEAKS = floatArrayOf(1f, 0.7f)
    private val SINGLE_DELAYS = floatArrayOf(0f)
    private val SINGLE_PEAKS = floatArrayOf(1f)

    /** How long before Thor's Call's leader each surge starts coiling up the body. */
    private val COIL_LEADS = floatArrayOf(1.5f, 1.15f, 0.85f)

    private val CHIP_COLORS = intArrayOf(0x2E2A26, 0x4A443C, 0x3B4A2C)

    /** A constant fall period, so the rain keeps falling the same way when the cloud moves. */
    private const val RAIN_PERIOD = 0.41

    // --- This frame ---------------------------------------------------------------------------

    private lateinit var c: AuraCanvas
    private lateinit var f: AuraFrame
    private var anchors: FloatArray? = null

    /** Aura time: [T] at full precision for the strike clock, [t] as the effects animate with. */
    private var T = 0.0
    private var t = 0f
    /** [t], but standing still while the storm holds its breath, for everything that crackles. */
    private var tc = 0f
    private var s = 1f
    /** Size, capped, for glows of a block or more, which would otherwise swamp the screen at 2x. */
    private var sf = 1f
    /** The cloud's scale: gentler than Size, so Size 2 does not swallow the screen. */
    private var sc = 1f
    private var h = 1.8f
    /** The body's scale, for what hugs the model. */
    private var bk = 1f
    private var speed = 1f
    /** Flash strength for thin channels and small sprites, and for anything a block or more across. */
    private var fa = 1f
    private var faWide = 1f
    private var hideFlash = false
    private var camYaw = 0f
    private var camHoriz = 0f
    /** 0..1, how far the camera looks down from over the cloud rather than from beside it. */
    private var over = 0f
    /** 0..1, how far the camera has risen over the cloud. */
    private var above = 0f
    /** How light it is round the player: the cloud and the dust darken with it. */
    private var amb = 1f
    /** The wind clock, unwrapped; see [windAt]. [wind] is it turned once, for one-to-one uses. */
    private var windD = 0.0
    private var wind = 0f
    private var cy = 0f
    private var belly = 0f
    /** How strongly a hero strike is flashing right now, 0..1. */
    private var heroGlow = 0f
    /** The current cycle's charge, 0..1. */
    private var charge = 0f
    private var hold = false
    private var push = 0f
    private var crackleCount = 3
    private var eyeBoost = 0f
    /** Real seconds since the latest Thor's Call strike, or negative. */
    private var thorAfter = -1f
    private var groundY = 0.03f

    private val cur = Cycle()
    private val prev = Cycle()

    /** One four-second cycle of the strike clock. */
    private class Cycle {
        var n = 0
        var kind = 0
        /** When the leader starts, how long the hold before it lasts, how long the leader takes. */
        var start = 0f
        var holdLen = 0f
        var leaderLen = 0f
        /** When the bolt attaches: the strike. */
        var attach = 0f
        /** Aura seconds into the cycle, and since the strike; [rs] is real seconds since it. */
        var tau = 0f
        var ts = 0f
        var rs = 0f
        var charge = 0f
        /** Where the camera was, from the feet, when the leader started; strikes are aimed by it. */
        var heroYaw = 0f
        var heroHoriz = 0f
        /** The aura time of the strike, for where things landed. */
        var tb = 0.0
        var b = 0f
        var bs = 0f
        var g = 0f
        var stroke = 0
        val inHold get() = tau >= start - holdLen && tau < start
        val inLeader get() = tau >= start && tau < attach
    }

    // --- Scratch ------------------------------------------------------------------------------

    private val px = Array(12) { FloatArray(65) }
    private val py = Array(12) { FloatArray(65) }
    private val pz = Array(12) { FloatArray(65) }

    private const val PUFFS = 56
    private val puffX = FloatArray(PUFFS)
    private val puffY = FloatArray(PUFFS)
    private val puffZ = FloatArray(PUFFS)
    private val puffW = FloatArray(PUFFS)
    private val puffH = FloatArray(PUFFS)
    private val puffTop = IntArray(PUFFS)
    private val puffBottom = IntArray(PUFFS)
    private val puffAlpha = FloatArray(PUFFS)
    private var puffCount = 0

    /** Light inside the cloud: where lightning runs through it, and how strongly. */
    private const val MAX_LIGHTS = 24
    private val lightX = FloatArray(MAX_LIGHTS)
    private val lightY = FloatArray(MAX_LIGHTS)
    private val lightZ = FloatArray(MAX_LIGHTS)
    private val lightI = FloatArray(MAX_LIGHTS)
    /** How far each light reaches through the cloud, squared: a hero strike lights more of it. */
    private val lightR = FloatArray(MAX_LIGHTS)
    private var lights = 0
    private var cloudFlash = 0f

    /** Where strikes light up the rain round them. */
    private val flashX = FloatArray(MAX_LIGHTS)
    private val flashY = FloatArray(MAX_LIGHTS)
    private val flashZ = FloatArray(MAX_LIGHTS)
    private val flashB = FloatArray(MAX_LIGHTS)
    private var flashes = 0

    private val e = FloatArray(3)
    private val e2 = FloatArray(3)
    private val start = FloatArray(3)
    private val shiftScratch = FloatArray(3)
    private val v = FloatArray(3)

    /** Set by [ground]: whether there was ground there at all, and how far the player has moved since. */
    private var groundFound = true
    private var lastDrift = 0f

    // --- Entry --------------------------------------------------------------------------------

    fun draw(canvas: AuraCanvas, frame: AuraFrame, anchorPoints: FloatArray?) {
        c = canvas
        f = frame
        anchors = anchorPoints
        T = frame.timeD
        t = frame.time
        s = frame.size
        sf = min(s, 1.5f)
        h = frame.height
        bk = h / 1.8f
        speed = frame.speed
        hideFlash = frame.hideFlash
        fa = if (hideFlash) 0.35f else 1f
        faWide = if (hideFlash) 0.1f else 1f
        amb = frame.ambient
        sc = min(0.65f + 0.35f * s, 1.25f)
        groundY = -frame.groundDrop + 0.03f
        camHoriz = hypot(frame.camX, frame.camZ)
        camYaw = if (camHoriz < 0.4f) atan2(-cos(frame.bodyYaw), sin(frame.bodyYaw)) else atan2(frame.camZ, frame.camX)

        val n = floor(T / 4.0).toInt()
        fill(cur, n)
        fill(prev, n - 1)
        hold = cur.inHold
        charge = cur.charge
        tc = if (hold) t - (cur.tau - (cur.start - cur.holdLen)) else t
        windD = windAt(T)
        wind = turn(1.0)
        heroGlow = max(heroGlowOf(prev), heroGlowOf(cur))
        val latest = if (cur.ts >= 0f) cur else prev
        push = if (latest.ts >= 0f) sin(PI * min(1f, latest.ts / 0.6f)) * exp(-latest.ts / 0.4f) else 0f
        thorAfter = when {
            cur.kind == THORS_CALL && cur.ts >= 0f -> cur.rs
            prev.kind == THORS_CALL && prev.ts >= 0f -> prev.rs
            else -> -1f
        }
        crackleCount = crackleCount()
        eyeBoost = if (thorAfter >= 0f) exp(-thorAfter / 0.6f) * (1f - smooth(0.8f, 1.4f, thorAfter)) else 0f
        lights = 0
        flashes = 0
        cloudFlash = 0f

        // The cloud sinks onto the player before Thor's Call and rises back as the charge fades,
        // recoils at every strike, and stays clear of the name tag and under any low roof.
        val descend = if (cur.kind == THORS_CALL) 0.12f * sc * cur.charge else 0f
        cy = h + 0.87f + 0.38f * sc - descend + 0.06f * sc * heroGlow
        cy = min(cy, max(h + 0.45f, frame.ceiling - 0.3f))
        belly = cy - 0.18f * sc
        over = smooth(1f, 2f, (frame.camY - belly) / max(camHoriz, 0.05f))
        above = smooth(belly + 0.8f, belly + 2.0f, frame.camY)

        canvas.nearFade = true
        hero(prev)
        hero(cur)
        ambient()
        chargeCore()
        surgeCoils()
        thunderhead()
        rain()
        updraft()
        bodyCrackle()
        eyesAndWings()
        fists()
        canvas.nearFade = false
    }

    // --- The strike clock ---------------------------------------------------------------------

    /**
     * The aura time [sinceStrike] seconds after cycle [n]'s strike lands (negative: before it), for
     * the screenshot tour to freeze the storm at. Cycle 0 is a Hammerfall, 1 a Volley, 2 Thor's Call.
     */
    @JvmStatic
    fun momentOf(n: Int, sinceStrike: Float): Double {
        val kind = Math.floorMod(n, 3)
        return 4.0 * n + startOf(n, kind) + (if (kind == THORS_CALL) 0.22f else 0.16f) + sinceStrike
    }

    private fun startOf(n: Int, kind: Int): Float = if (kind == THORS_CALL) 3f else 2.85f + 0.3f * hash(7 * n + 1)

    private fun fill(cyc: Cycle, n: Int) {
        cyc.n = n
        cyc.kind = Math.floorMod(n, 3)
        cyc.start = startOf(n, cyc.kind)
        cyc.holdLen = if (cyc.kind == THORS_CALL) 0.24f else 0.12f
        cyc.leaderLen = if (cyc.kind == THORS_CALL) 0.22f else 0.16f
        cyc.attach = cyc.start + cyc.leaderLen
        cyc.tau = (T - 4.0 * n).toFloat()
        cyc.ts = cyc.tau - cyc.attach
        cyc.rs = cyc.ts / speed
        cyc.charge = if (cyc.tau < cyc.attach) smooth(cyc.start - 1.5f, cyc.start - cyc.holdLen, cyc.tau)
        else exp(-cyc.ts / 0.3f)
        cyc.tb = 4.0 * n + cyc.attach
        // Latched when the leader starts, so a strike does not swing round when the mouse moves.
        val history = f.history
        cyc.heroYaw = history?.camYawAt(4.0 * n + cyc.start, camYaw) ?: camYaw
        cyc.heroHoriz = history?.camHorizAt(4.0 * n + cyc.start, camHoriz) ?: camHoriz
        val delays = if (cyc.kind == THORS_CALL) THOR_DELAYS else if (cyc.kind == HAMMERFALL) HAMMER_DELAYS else SINGLE_DELAYS
        val peaks = if (cyc.kind == THORS_CALL) THOR_PEAKS else if (cyc.kind == HAMMERFALL) HAMMER_PEAKS else SINGLE_PEAKS
        cyc.b = strokes(cyc.rs, delays, peaks)
        cyc.stroke = strokeIndex(cyc.rs, delays)
        cyc.bs = flash(cyc.rs)
        cyc.g = afterglow(cyc.rs)
    }

    /** The return strokes: each restrike flares and dies in 40 ms. One stroke with Hide Lightning Flashes. */
    private fun strokes(rs: Float, delays: FloatArray, peaks: FloatArray): Float {
        if (rs < 0f) return 0f
        if (hideFlash) return 0.8f * exp(-rs / 0.12f)
        var b = 0f
        for (k in delays.indices) if (rs >= delays[k]) b = max(b, peaks[k] * exp(-(rs - delays[k]) / 0.04f))
        return b
    }

    /** Which return stroke is running: each one re-rolls the bolt's finest kinks. */
    private fun strokeIndex(rs: Float, delays: FloatArray): Int {
        if (hideFlash) return 0
        var k = 0
        for (i in delays.indices) if (rs >= delays[i]) k = i
        return k
    }

    /** The one smooth flash every large glow follows: 20 ms in, a little over 200 ms out. */
    private fun flash(rs: Float): Float = when {
        rs < 0f -> 0f
        rs < 0.02f -> rs / 0.02f
        else -> exp(-(rs - 0.02f) / 0.22f)
    }

    private fun afterglow(rs: Float): Float = if (rs < 0f) 0f else exp(-rs / 0.35f) * (1f - smooth(0.9f, 1.2f, rs))

    /** A hero channel's strength: the strokes, never quite out between them, and the afterglow. */
    private fun channel(b: Float, rs: Float, g: Float): Float = min(1f, max(b, 0.55f * exp(-rs / 0.3f)) + 0.35f * g)

    private fun heroGlowOf(cyc: Cycle): Float = when (cyc.kind) {
        VOLLEY -> max(flash(cyc.rs), max(flash((cyc.ts - 0.09f) / speed), flash((cyc.ts - 0.18f) / speed)))
        THORS_CALL -> max(flash(cyc.rs), flash((cyc.ts - 0.18f) / speed))
        else -> flash(cyc.rs)
    }

    /**
     * The angle the cloud, vortex and rain have turned through: steady, with an extra push into
     * every strike, then coasting, never backwards. Unwrapped; every use turns it by its own factor
     * with [turn] and only then wraps, or the cloud would jump each time the clock passed a turn.
     */
    private fun windAt(time: Double): Double {
        val n = floor(time / 4.0).toInt()
        val tau = (time - 4.0 * n).toFloat()
        val start = startOf(n, Math.floorMod(n, 3))
        return 0.7 * time + 2.5 * (n + smooth(start - 1.5f, start + 0.6f, tau))
    }

    private fun turn(k: Double): Float = ((k * windD) % (Math.PI * 2)).toFloat()

    /** How likely an ambient bolt is in a slot starting at [time]: none round a strike, many before it. */
    private fun ambientChance(time: Double, base: Float, build: Float): Float {
        val n = floor(time / 4.0).toInt()
        val kind = Math.floorMod(n, 3)
        val start = startOf(n, kind)
        val holdLen = if (kind == THORS_CALL) 0.24f else 0.12f
        val attach = start + if (kind == THORS_CALL) 0.22f else 0.16f
        val tau = (time - 4.0 * n).toFloat()
        val p = when {
            tau < start - holdLen -> base + build * smooth(start - 1.5f, start - holdLen, tau)
            tau < attach + 0.3f -> 0f
            else -> base * smooth(0.3f, 0.8f, tau - attach)
        }
        return p * (if (hideFlash) 0.6f else 1f)
    }

    private fun crackleCount(): Int {
        val nc = when {
            cur.tau >= cur.attach -> 3f
            cur.inHold || cur.inLeader -> 16f
            else -> 3f + 11f * cur.charge
        }
        return round(max(nc, max(crackleOf(prev), crackleOf(cur)))).toInt().coerceIn(0, 24)
    }

    /** How many arcs a strike sets crawling over the body, as it fades. */
    private fun crackleOf(cyc: Cycle): Float {
        if (cyc.ts < 0f) return 0f
        val rs = cyc.rs
        val peak = if (cyc.kind == THORS_CALL) 24f else 20f
        return when {
            rs < 0.25f -> peak
            cyc.kind == THORS_CALL && rs < 0.6f -> lerp(peak, 8f, (rs - 0.25f) / 0.35f)
            cyc.kind == THORS_CALL && rs < 1.5f -> lerp(8f, 3f, (rs - 0.6f) / 0.9f)
            cyc.kind != THORS_CALL && rs < 1f -> lerp(peak, 3f, (rs - 0.25f) / 0.75f)
            else -> 3f
        }
    }

    // --- Lightning ------------------------------------------------------------------------------

    /**
     * A jagged channel from A to B into buffer [slot], by midpoint displacement: each level splits
     * every segment and kicks its middle sideways, by less each time, which gives the fractal look of
     * real lightning. Returns the last index (2^levels).
     *
     * [restroke] re-rolls only the finest kinks, so on each return stroke the channel shivers but
     * stays where it is, the way real restrikes follow the same path. [bow] bends the whole channel
     * towards (bx, by, bz), for bolts that arc like a fountain.
     */
    private fun crack(
        slot: Int, seed: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        levels: Int, rough: Float, decay: Float = 0.56f, restroke: Int = 0,
        bowX: Float = 0f, bowY: Float = 0f, bowZ: Float = 0f, bow: Float = 0f,
    ): Int {
        val n = 1 shl levels
        val x = px[slot]; val y = py[slot]; val z = pz[slot]
        x[0] = ax; y[0] = ay; z[0] = az
        x[n] = bx; y[n] = by; z[n] = bz
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-5f) {
            for (i in 1 until n) { x[i] = ax; y[i] = ay; z[i] = az }
            return n
        }
        dx /= len; dy /= len; dz /= len
        // Two directions across the channel: the first lies level, square to the channel's heading,
        // which turns smoothly as the heading does. Only a channel that is exactly vertical has none,
        // and takes another; switching any earlier would make a steep bolt jump as it tilted past.
        var e1x: Float; var e1y: Float; var e1z: Float
        if (dx * dx + dz * dz < 1e-6f) { e1x = 0f; e1y = dz; e1z = -dy } else { e1x = -dz; e1y = 0f; e1z = dx }
        val l1 = sqrt(e1x * e1x + e1y * e1y + e1z * e1z)
        e1x /= l1; e1y /= l1; e1z /= l1
        val e2x = dy * e1z - dz * e1y
        val e2y = dz * e1x - dx * e1z
        val e2z = dx * e1y - dy * e1x
        var amp = rough * len
        for (level in 0 until levels) {
            val step = n shr level
            val half = step shr 1
            val sl = seed + if (level == levels - 1) 7919 * restroke else 0
            var i = half
            while (i < n) {
                val k1 = (hash(977 * sl + 13 * i + 1) - 0.5f) * 2f * amp
                val k2 = (hash(977 * sl + 13 * i + 2) - 0.5f) * 2f * amp
                x[i] = (x[i - half] + x[i + half]) * 0.5f + e1x * k1 + e2x * k2
                y[i] = (y[i - half] + y[i + half]) * 0.5f + e1y * k1 + e2y * k2
                z[i] = (z[i - half] + z[i + half]) * 0.5f + e1z * k1 + e2z * k2
                i += step
            }
            amp *= decay
        }
        if (bow != 0f) {
            for (i in 1 until n) {
                val m = i.toFloat() / n
                val k = 4f * m * (1f - m) * bow
                x[i] += bowX * k; y[i] += bowY * k; z[i] += bowZ * k
            }
        }
        return n
    }

    /**
     * Draws a channel from buffer [slot] as far as [reveal] (0..1) along it: the narrow bolt with its
     * white core, and a wide coarse glow through every fourth point - coarse, so the wide strip cannot
     * fold over itself at the fine kinks. The channel is thickest where it leaves the cloud.
     *
     * [fadeIn] hides where a bolt leaves the cloud; [fadeOut] lets it die out in mid-air.
     */
    private fun drawBolt(
        slot: Int, n: Int, reveal: Float, wCh: Float, wGl: Float, tint: Int, aCh: Float, aGl: Float, seed: Int,
        fadeIn: Boolean = true, fadeOut: Boolean = false,
    ) {
        if (aCh < 0.004f && aGl < 0.004f) return
        val x = px[slot]; val y = py[slot]; val z = pz[slot]
        val r = reveal.coerceIn(0f, 1f) * n
        val last = floor(r).toInt().coerceIn(0, n)
        val frac = r - last
        if (aCh >= 0.004f) {
            c.beginBolt(seed)
            for (i in 0..last) {
                val m = i.toFloat() / n
                c.point(x[i], y[i], z[i], wCh * (0.55f + 0.45f * sqrt(1f - m)), tint, aCh * ends(m, fadeIn, fadeOut))
            }
            if (last < n && frac > 0.01f) {
                val m = r / n
                c.point(lerp(x[last], x[last + 1], frac), lerp(y[last], y[last + 1], frac), lerp(z[last], z[last + 1], frac),
                    wCh * (0.55f + 0.45f * sqrt(1f - m)), tint, aCh * ends(m, fadeIn, fadeOut))
            }
            c.endRibbon()
        }
        if (aGl >= 0.004f && last >= 1) {
            c.beginRibbon()
            var i = 0
            while (i <= last) {
                val m = i.toFloat() / n
                c.point(x[i], y[i], z[i], wGl * (0.6f + 0.4f * sin(PI * m)), tint, aGl * ends(m, fadeIn, fadeOut))
                i += 4
            }
            if ((i - 4) != last) {
                val m = last.toFloat() / n
                c.point(x[last], y[last], z[last], wGl * (0.6f + 0.4f * sin(PI * m)), tint, aGl * ends(m, fadeIn, fadeOut))
            }
            c.endRibbon()
        }
    }

    private fun ends(m: Float, fadeIn: Boolean, fadeOut: Boolean): Float =
        (if (fadeIn) smooth(0f, 0.06f, m) else 1f) * (if (fadeOut) 1f - smooth(0.75f, 1f, m) else 1f)

    /**
     * A branch off point [j] of the channel in [parent], into [out]: it leaves along the channel,
     * outwards from it and a little down, turned by [rot] about the vertical. With [avoidCam] it is
     * also kept that many radians away from the direction of [ref], the camera. Returns its last index.
     */
    private fun branch(
        parent: Int, parentN: Int, j: Int, len: Float, rot: Float, seed: Int, levels: Int, out: Int,
        avoidCam: Float = 0f, ref: Float = camYaw,
    ): Int {
        val x = px[parent]; val y = py[parent]; val z = pz[parent]
        val i = j.coerceIn(1, parentN - 1)
        var tx = x[i + 1] - x[i - 1]
        var ty = y[i + 1] - y[i - 1]
        var tz = z[i + 1] - z[i - 1]
        val tl = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-5f)
        tx /= tl; ty /= tl; tz /= tl
        // Outwards: across the ground, away from the channel's own line.
        var ox = x[parentN] - x[0]
        var oz = z[parentN] - z[0]
        var ol = hypot(ox, oz)
        if (ol < 0.05f) { ox = x[i]; oz = z[i]; ol = hypot(ox, oz) }
        if (ol < 1e-4f) { ox = 1f; oz = 0f; ol = 1f }
        ox /= ol; oz /= ol
        var dx = tx + 0.9f * ox
        val dy = ty - 0.25f
        var dz = tz + 0.9f * oz
        val cr = cos(rot); val sr = sin(rot)
        val rx = dx * cr - dz * sr
        dz = dx * sr + dz * cr
        dx = rx
        if (avoidCam > 0f) {
            val hd = hypot(dx, dz)
            val ha = away(atan2(dz, dx), avoidCam, ref)
            dx = cos(ha) * hd
            dz = sin(ha) * hd
        }
        val dl = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
        return crack(out, seed, x[i], y[i], z[i], x[i] + dx / dl * len, y[i] + dy / dl * len, z[i] + dz / dl * len, levels, 0.22f)
    }

    // --- Where things land ----------------------------------------------------------------------

    /**
     * A point on the ground at local (x, z), as it was at aura time [tb]: it stays where it landed in
     * the world while the player moves on. Returns whether marks may be left there - false when the
     * point is from before the history last started over (a teleport), or there was no ground at all;
     * then [out] is a point in mid-air under the cloud, for a bolt to die out at. Also leaves
     * [groundFound] and [lastDrift], how far the player has moved since.
     */
    private fun ground(tb: Double, x: Float, z: Float, out: FloatArray): Boolean {
        val history = f.history
        lastDrift = 0f
        if (history == null) {
            out[0] = x; out[1] = groundY; out[2] = z
            groundFound = !f.groundMissing
        } else if (tb < history.validFrom) {
            // From before a teleport: where that was is gone, so it stays with the player instead.
            out[0] = x; out[1] = groundY; out[2] = z
            groundFound = !f.groundMissing
            return false
        } else {
            history.shift(tb, shiftScratch)
            val drop = history.dropAt(tb)
            groundFound = !drop.isNaN()
            out[0] = x + shiftScratch[0]
            out[1] = if (groundFound) shiftScratch[1] - drop + 0.03f else 0f
            out[2] = z + shiftScratch[2]
            lastDrift = hypot(shiftScratch[0], shiftScratch[2])
        }
        if (!groundFound) {
            out[1] = belly - 3f
            return false
        }
        return history == null || tb >= history.validFrom
    }

    /** [a] pushed out of the [min] radians either side of the direction [ref] (the camera's). */
    private fun away(a: Float, min: Float, ref: Float = camYaw): Float {
        val d = wrap(a - ref)
        return if (abs(d) < min) ref + (if (d >= 0f) min else -min) else a
    }

    /**
     * How far a landing on the camera's side may reach: never as far as the camera itself - unless the
     * camera looks down from over the cloud, when nothing can land between it and the player anyway.
     */
    private fun reach(a: Float, r: Float, ref: Float = camYaw, horiz: Float = camHoriz): Float =
        if (abs(wrap(a - ref)) < PI / 2) min(r, lerp(0.7f * horiz, r, over)) else r

    /**
     * Where the hero bolt of [cyc] leaves the cloud: from the edge of its belly, on the side the bolt
     * goes, so the whole channel shows instead of starting behind the head and the name tag.
     */
    private fun heroStart(cyc: Cycle, out: FloatArray) {
        when (cyc.kind) {
            THORS_CALL -> {
                val side = cyc.heroYaw + PI / 2
                val sign = thorSign(cyc)
                out[0] = sign * 1.3f * sc * cos(side)
                out[1] = min(belly + 0.1f, f.ceiling - 0.1f)
                out[2] = sign * 1.3f * sc * sin(side)
            }
            else -> {
                val a = if (cyc.kind == HAMMERFALL) hammerAngle(cyc) else volleyAngle(cyc, 0)
                val r = if (cyc.kind == HAMMERFALL) 1.2f * sc else 1.1f * sc
                out[0] = cos(a) * r
                out[1] = min(belly + 0.05f, f.ceiling - 0.1f)
                out[2] = sin(a) * r
            }
        }
    }

    private fun thorSign(cyc: Cycle): Float = if (hash(1019 * cyc.n + 7) < 0.5f) -1f else 1f

    /**
     * Hammerfall lands well to one side, just behind the player, where the whole channel crosses the
     * screen; now and then dead behind, where the body stands out black against the flash.
     */
    private fun hammerAngle(cyc: Cycle): Float {
        val n = cyc.n
        val behind = cyc.heroYaw + PI
        return if (hash(13 * n + 3) < 0.15f) behind + (hash(13 * n + 4) - 0.5f) * 0.3f
        else behind + (if (hash(13 * n + 2) < 0.5f) -1f else 1f) * (1.1f + 0.3f * hash(13 * n + 4))
    }

    private fun volleyAngle(cyc: Cycle, i: Int): Float =
        cyc.heroYaw + PI + (if (i == 0) -0.95f else if (i == 1) 0.95f else 0f) + (hash(13 * cyc.n + 20 + i) - 0.5f) * 0.2f

    /**
     * How much of a bolt landing at [p] to keep: less as the player runs away from where it landed,
     * and less as the camera comes up to that spot - running back through a strike.
     */
    private fun keep(p: FloatArray): Float {
        val dx = p[0] - f.camX; val dy = p[1] - f.camY; val dz = p[2] - f.camZ
        return (1f - smooth(1.5f, 4f, lastDrift)) * smooth(1.8f, 3f, sqrt(dx * dx + dy * dy + dz * dz))
    }

    private fun addLight(x: Float, y: Float, z: Float, intensity: Float, reach: Float = 0.75f) {
        if (lights >= MAX_LIGHTS || intensity < 0.01f) return
        lightX[lights] = x; lightY[lights] = y; lightZ[lights] = z; lightI[lights] = intensity
        lightR[lights] = (reach * sc) * (reach * sc)
        lights++
    }

    /**
     * A hero strike lighting up the whole cloud: an overall flash, and light sweeping across the
     * belly from where the bolt left it to the far side.
     */
    private fun heroCloudLight(from: FloatArray, rs: Float, flashAll: Float) {
        cloudFlash = max(cloudFlash, flashAll * flash(rs) * faWide)
        addLight(from[0], from[1], from[2], 1.2f * flash(rs) * faWide, 1.3f)
        for (k in 1..3) {
            val m = k / 3f
            addLight(lerp(from[0], -from[0], m), belly - 0.05f, lerp(from[2], -from[2], m), 0.8f * flash(rs - 0.03f * k) * faWide, 1.3f)
        }
    }

    private fun addFlash(x: Float, y: Float, z: Float, b: Float) {
        if (flashes >= MAX_LIGHTS || b < 0.01f) return
        flashX[flashes] = x; flashY[flashes] = y; flashZ[flashes] = z; flashB[flashes] = b
        flashes++
    }

    /*
     * By day a bright background turns a bolt's sheath and glow into a soft white beam; there the
     * big channels are narrower, glow less and carry a deeper blue, so the white core stays crisp.
     */
    private fun dayWidth(): Float = lerp(0.75f, 1f, 1f - amb)
    private fun dayGlowWidth(): Float = lerp(0.7f, 1f, 1f - amb)
    private fun dayGlow(): Float = lerp(0.5f, 1f, 1f - amb)
    private fun dayTint(tint: Int): Int = mix(f.color(0.8f), tint, 1f - amb)

    /** Dims what sits over the cloud as the camera rises above it, looking down. */
    private fun overhead(): Float = lerp(1f, 0.4f, above)

    // --- Hero strikes ---------------------------------------------------------------------------

    private fun hero(cyc: Cycle) {
        if (cyc.ts > 2.6f) return
        if (cyc.tau < cyc.start - cyc.holdLen) return
        when (cyc.kind) {
            HAMMERFALL -> hammerfall(cyc)
            VOLLEY -> volley(cyc)
            else -> thorsCall(cyc)
        }
    }

    /** One colossal bolt out of the cloud's edge, landing well to the side and cracking the ground open. */
    private fun hammerfall(cyc: Cycle) {
        val n = cyc.n
        val a = hammerAngle(cyc)
        val r = reach(a, (3.0f + 0.8f * hash(13 * n + 5)) * s, cyc.heroYaw, cyc.heroHoriz)
        val marks = ground(cyc.tb, cos(a) * r, sin(a) * r, e)
        val kept = keep(e)
        heroStart(cyc, start)
        val seed = 1009 * n + 17

        if (cyc.ts < 0f) {
            if (marks) streamers(e[0], e[1], e[2], cyc, 0)
            if (cyc.inLeader) {
                val pn = crack(0, seed, start[0], start[1], start[2], e[0], e[1], e[2], 6, 0.16f)
                leader(cyc, pn, 0.5f * s, 5, seed)
            }
            return
        }
        val rs = cyc.rs
        val tint = dayTint(mix(f.color(0.55f), f.color(1f), smooth(0.35f, 0.9f, rs)))
        if (rs < 1.2f && kept > 0.01f) {
            val pn = crack(0, seed, start[0], start[1], start[2], e[0], e[1], e[2], 6, 0.16f, restroke = cyc.stroke)
            val wf = if (rs < 0.35f) 1f else 0.6f + 0.4f * cyc.g
            drawBolt(0, pn, 1f, 0.5f * s * (1f + 0.4f * cyc.b) * wf * dayWidth(), 0.9f * s * wf * dayGlowWidth(), tint,
                channel(cyc.b, rs, cyc.g) * kept, (0.2f * cyc.bs + 0.08f * cyc.g) * fa * kept * dayGlow(), seed, fadeOut = !groundFound)
            val ab = 0.75f * cyc.b * exp(-rs / 0.12f) * kept
            val at = 0.6f * cyc.b * exp(-rs / 0.08f) * kept
            if (ab > 0.004f) {
                for (i in 0 until 6) {
                    val j = 8 + i * 36 / 5 + ((hash(seed + 40 + i) - 0.5f) * 4f).toInt()
                    val bn = branch(0, pn, j, (0.5f + 0.6f * hash(seed + 50 + i)) * s,
                        (hash(seed + 60 + i) - 0.5f) * 1.2f, seed + 100 * (i + 1), 4, 1 + i)
                    drawBolt(1 + i, bn, 1f, 0.13f * s, 0.45f * s, tint, ab, 0.25f * cyc.bs * fa * kept, seed + i + 1)
                    if (at > 0.004f) {
                        val tn = branch(1 + i, bn, 4 + (hash(seed + 70 + i) * 8f).toInt(), (0.3f + 0.2f * hash(seed + 80 + i)) * s,
                            (hash(seed + 90 + i) - 0.5f) * 1.2f, seed + 1000 * (i + 1), 3, 7)
                        drawBolt(7, tn, 1f, 0.06f * s, 0f, tint, at, 0f, seed + i + 7)
                    }
                }
            }
            handArc(pn, pn * 11 / 16, e, rs, cyc.b, cyc.bs, cyc.g, kept, tint, cyc.stroke, seed)
        }
        heroCloudLight(start, rs, 0.3f)
        // Spider lightning racing off across the cloud's belly from where the bolt left it.
        if (rs < 0.3f) {
            val a0 = atan2(start[2], start[0])
            for (k in 0 until 2) {
                val a1 = a0 + PI + (if (k == 0) -0.7f else 0.7f)
                val y0 = start[1] - 0.04f
                val sn = crack(9, seed + 500 + k, start[0], y0, start[2], cos(a1) * 1.7f * sc, y0, sin(a1) * 1.7f * sc, 5, 0.18f)
                for (i in 0..sn) py[9][i] = y0 + (py[9][i] - y0) * 0.25f
                drawBolt(9, sn, min(1f, rs / 0.1f), 0.08f * s, 0.3f * s, tint, 0.9f * exp(-rs / 0.12f), 0.25f * cyc.bs * fa, seed + 500 + k)
            }
        }
        impact(e, 1f, cyc.b * kept, cyc.bs * kept, cyc.g * kept, 1f, start, cyc.ts, seed, marks)
        if (!marks) return
        groundCracks(e, 7, 0.2f, 1.4f, 2.4f, 10f, cyc.ts, cyc.rs, cyc.b, seed, n)
        debris(e, 28, seed, cyc.ts, 0.45f, 0.85f, cyc.heroYaw)
        embers(e, 14, seed, cyc.ts, cyc.rs)
        dust(e, 10, seed, cyc.ts, 0.3f, false)
        scorch(e, 1.2f * s, seed, cyc.ts, cyc.rs)
        steam(e, 2, seed, cyc.ts)
    }

    /** Three bolts fanning down behind the player: behind-left, behind-right, then dead behind and biggest. */
    private fun volley(cyc: Cycle) {
        val n = cyc.n
        var sum = 0f
        for (i in 0 until 3) sum += flash((cyc.ts - 0.09f * i) / speed)
        val ov = 1f / sqrt(max(1f, sum))
        for (i in 0 until 3) {
            val center = i == 2
            val a = volleyAngle(cyc, i)
            val r = reach(a, 2.1f * s * (0.9f + 0.2f * hash(13 * n + 30 + i)), cyc.heroYaw, cyc.heroHoriz)
            val off = 0.09f * i
            val marks = ground(cyc.tb + off, cos(a) * r, sin(a) * r, e)
            val kept = keep(e)
            start[0] = cos(a) * 1.1f * sc
            start[1] = min(belly + 0.05f, f.ceiling - 0.1f)
            start[2] = sin(a) * 1.1f * sc
            val seed = 1013 * n + 31 * i + 5
            val levels = if (center) 6 else 5
            val ts = cyc.ts - off
            if (cyc.ts < 0f) {
                if (marks) streamers(e[0], e[1], e[2], cyc, i * 3)
                if (i == 0 && cyc.inLeader) {
                    val pn = crack(0, seed, start[0], start[1], start[2], e[0], e[1], e[2], levels, 0.16f)
                    leader(cyc, pn, 0.32f * s, 5, seed)
                }
                continue
            }
            if (ts < 0f) continue
            val rs = ts / speed
            val b = strokes(rs, SINGLE_DELAYS, SINGLE_PEAKS)
            val bs = flash(rs)
            val g = afterglow(rs)
            val tint = dayTint(mix(f.color(0.55f), f.color(1f), smooth(0.35f, 0.9f, rs)))
            if (rs < 1.2f && kept > 0.01f) {
                val pn = crack(0, seed, start[0], start[1], start[2], e[0], e[1], e[2], levels, 0.16f)
                val wf = if (rs < 0.35f) 1f else 0.6f + 0.4f * g
                val wCh = (if (center) 0.42f else 0.32f) * s * dayWidth()
                val wGl = (if (center) 0.8f else 0.6f) * s * dayGlowWidth()
                drawBolt(0, pn, 1f, wCh * (1f + 0.4f * b) * wf, wGl * wf, tint, channel(b, rs, g) * kept,
                    (0.2f * bs + 0.08f * g) * fa * ov * kept * dayGlow(), seed, fadeOut = !groundFound)
                val ab = 0.75f * b * exp(-rs / 0.12f) * kept
                if (ab > 0.004f) {
                    val count = if (center) 4 else 3
                    for (k in 0 until count) {
                        val j = (if (center) 8 + k * 12 else 4 + k * 9) + ((hash(seed + 40 + k) - 0.5f) * 3f).toInt()
                        val bn = branch(0, pn, j, (0.4f + 0.4f * hash(seed + 50 + k)) * s,
                            (hash(seed + 60 + k) - 0.5f) * 1.2f, seed + 100 * (k + 1), 4, 1 + k)
                        drawBolt(1 + k, bn, 1f, 0.1f * s, 0.4f * s, tint, ab, 0.2f * bs * fa * ov * kept, seed + k + 1)
                    }
                }
                if (!center) handArc(pn, pn * 11 / 16, e, rs, b, bs, g, kept * ov, tint, 0, seed)
            }
            heroCloudLight(start, rs, 0.22f * ov)
            impact(e, if (center) 0.9f else 0.75f, b * kept, bs * kept, g * kept, ov, start, ts, seed, marks)
            if (!marks) continue
            debris(e, if (center) 16 else 10, seed, ts, 0.45f, 0.85f, cyc.heroYaw)
            embers(e, if (center) 10 else 6, seed, ts, rs)
            dust(e, 6, seed, ts, 0.3f, false)
            scorch(e, 0.9f * s, seed, ts, rs)
            steam(e, 1, seed, ts)
        }
    }

    /**
     * The sky splits and a bolt arcs down out of the cloud onto the player's head. The cloud flashes
     * from inside, a column of light backlights the body, and the player hurls six bolts into the
     * ground in a sweeping fan while cracks race out from the feet.
     */
    private fun thorsCall(cyc: Cycle) {
        val n = cyc.n
        val anchor = anchors
        val tx: Float; val ty: Float; val tz: Float
        if (anchor != null) {
            tx = anchor[AuraAnchors.HEAD_TOP * 3]
            ty = anchor[AuraAnchors.HEAD_TOP * 3 + 1] + 0.06f
            tz = anchor[AuraAnchors.HEAD_TOP * 3 + 2]
        } else {
            tx = 0f; ty = h + 0.08f; tz = 0f
        }
        val seed = 1019 * n + 3
        val acrossX = cos(f.bodyYaw)
        val acrossZ = sin(f.bodyYaw)
        val side = cyc.heroYaw + PI / 2
        val sign = thorSign(cyc)
        val sideX = sign * cos(side)
        val sideZ = sign * sin(side)
        heroStart(cyc, start)

        if (cyc.ts < 0f) {
            // Hair standing on end: streamers on the head and both shoulders, waiting for the bolt.
            streamers(tx, ty, tz, cyc, 0)
            streamers(0.3f * bk * acrossX, 1.42f * bk, 0.3f * bk * acrossZ, cyc, 3)
            streamers(-0.3f * bk * acrossX, 1.42f * bk, -0.3f * bk * acrossZ, cyc, 6)
            if (cyc.inLeader) {
                val pn = headBolt(seed, tx, ty, tz, sideX, sideZ, 0)
                leader(cyc, pn, 0.6f * s, 6, seed)
            }
            return
        }
        val rs = cyc.rs
        val b = cyc.b
        val bs = cyc.bs
        val g = cyc.g
        val up = overhead()

        if (rs < 1.2f) {
            // The sky spear, from far above down into the cloud on the same side.
            val sn = crack(0, seed + 2, sideX * 3.2f * s, cy + 5.5f, sideZ * 3.2f * s,
                sideX * 1.2f * sc, cy + 0.4f * sc, sideZ * 1.2f * sc, 5, 0.12f, restroke = cyc.stroke)
            drawBolt(0, sn, 1f, 0.42f * s * dayWidth(), 0.9f * s * dayGlowWidth(), dayTint(f.color(0.3f)), channel(b, rs, g),
                0.3f * bs * fa * dayGlow(), seed + 2)

            // The head bolt, arcing out of the cloud's edge onto the head, and its branches.
            val hn = headBolt(seed, tx, ty, tz, sideX, sideZ, cyc.stroke)
            drawBolt(0, hn, 1f, 0.6f * s * (1f + 0.4f * b) * dayWidth(), 0.9f * s * dayGlowWidth(), dayTint(f.color(0.3f)),
                channel(b, rs, g), 0.35f * bs * fa * dayGlow(), seed + 1, fadeIn = false)
            val ab = 0.7f * b * exp(-rs / 0.1f)
            for (k in 0 until 4) {
                if (ab < 0.004f) break
                val j = 4 + k * 4 + (hash(seed + 30 + k) * 4f).toInt()
                val bn = branch(0, hn, j, (0.45f + 0.35f * hash(seed + 40 + k)) * s,
                    (hash(seed + 50 + k) - 0.5f) * 1.2f, seed + 100 * (k + 1), 3, 1 + k, avoidCam = 50f * PI / 180f, ref = cyc.heroYaw)
                drawBolt(1 + k, bn, 1f, 0.12f * s, 0.4f * s, f.color(0.3f), ab, 0.2f * bs * fa, seed + k + 3)
            }
            // The afterimage of the head bolt's path, burnt into the eye.
            val after = 0.28f * (1f - smooth(0.25f, 0.8f, rs)) * (1f - b)
            if (after > 0.004f) {
                c.beginRibbon()
                for (i in 0..hn) c.point(px[0][i], py[0][i], pz[0][i], 0.14f * s, f.color(0.9f), after)
                c.endRibbon()
            }
        }

        // The head burst.
        c.sprite(tx, ty, tz, 1.0f * sf, f.color(0.3f), 0.55f * bs * faWide * up)
        c.sprite(tx, ty, tz, 1.3f * sf, mix(f.color(0.2f), WHITE, 0.5f), 0.8f * bs * faWide * up, sparkle = true,
            spin = hash(seed + 9) * TAU + 2f * cyc.ts)
        c.sprite(tx, ty, tz, 0.3f * s, WHITE, b)

        // Backlight: a column of light on the body's axis and a corona behind it. The body hides
        // their middles, so they only rim its outline.
        if (bs > 0.004f) {
            c.beginRibbon()
            for (i in 0..8) {
                val m = i / 8f
                c.point(0f, 0.05f + (belly - 0.05f) * m, 0f, 1.6f * sf, f.color(0.4f), 0.45f * bs * faWide * sin(PI * (0.1f + 0.8f * m)))
            }
            c.endRibbon()
            val bx = if (camHoriz > 0.01f) -f.camX / camHoriz * 0.45f else 0f
            val bz = if (camHoriz > 0.01f) -f.camZ / camHoriz * 0.45f else 0f
            for (i in 1..3) c.sprite(bx, h * 0.3f * i, bz, 1.2f * bk, f.color(0.6f), 0.5f * bs * faWide)
        }
        // The cloud lit from inside, strongest where the bolts run through it.
        cloudFlash = max(cloudFlash, 0.45f * bs * faWide)
        for (k in 0 until 4) {
            val m = k / 3f
            addLight(lerp(sideX * 1.2f * sc, start[0], m), lerp(cy + 0.4f * sc, start[1], m), lerp(sideZ * 1.2f * sc, start[2], m),
                1.0f * bs * faWide)
        }
        addFlash(tx, ty, tz, bs * fa)

        // The discharge fan: six bolts leap out of the chest and arc down into the ground round the
        // player, landing one after another in a sweep.
        val chestX: Float; val chestY: Float; val chestZ: Float
        if (anchor != null) {
            chestX = anchor[AuraAnchors.CHEST * 3]; chestY = anchor[AuraAnchors.CHEST * 3 + 1]; chestZ = anchor[AuraAnchors.CHEST * 3 + 2]
        } else {
            chestX = 0f; chestY = 0.72f * h; chestZ = 0f
        }
        var sum = 0f
        for (k in 0 until 6) sum += flash((cyc.ts - 0.18f - 0.015f * ((5 * k) % 6)) / speed)
        val ov = 1f / sqrt(max(1f, sum))
        for (k in 0 until 6) {
            val td = 0.18f + 0.015f * ((5 * k) % 6)
            val tsk = cyc.ts - td
            if (tsk < 0f) continue
            val rsk = tsk / speed
            val a = away(cyc.heroYaw + (75f + 42f * k + (hash(seed + 60 + k) - 0.5f) * 16f) * PI / 180f, 50f * PI / 180f, cyc.heroYaw)
            val r = reach(a, (2.8f + 0.8f * hash(seed + 70 + k)) * s, cyc.heroYaw, cyc.heroHoriz)
            val marks = ground(cyc.tb + td, cos(a) * r, sin(a) * r, e)
            val kept = keep(e)
            val sx = chestX + 0.28f * bk * cos(a)
            val sz = chestZ + 0.28f * bk * sin(a)
            val bk2 = strokes(rsk, SINGLE_DELAYS, SINGLE_PEAKS)
            val bsk = flash(rsk)
            val gk = afterglow(rsk)
            val ks = seed + 200 + 17 * k
            if (rsk < 1.2f && kept > 0.01f) {
                val pn = crack(0, ks, sx, chestY, sz, e[0], e[1], e[2], 5, 0.15f, bowY = 1f, bow = 0.45f * s)
                drawBolt(0, pn, 1f, 0.24f * s * (1f + 0.4f * bk2), 0.8f * s, f.color(0.35f), channel(bk2, rsk, gk) * kept,
                    0.35f * bsk * fa * ov * kept, ks, fadeIn = false, fadeOut = !groundFound)
                val ab = 0.7f * bk2 * exp(-rsk / 0.12f) * kept
                if (ab > 0.004f) {
                    for (j in 0 until 2) {
                        val bn = branch(0, pn, 10 + j * 10 + (hash(ks + j) * 4f).toInt(), (0.4f + 0.4f * hash(ks + 5 + j)) * s,
                            (hash(ks + 9 + j) - 0.5f) * 1.2f, ks + 100 * (j + 1), 4, 1 + j)
                        drawBolt(1 + j, bn, 1f, 0.1f * s, 0f, f.color(0.35f), ab, 0f, ks + j)
                    }
                }
            }
            impact(e, 0.6f, bk2 * kept, bsk * kept, gk * kept, ov, null, tsk, ks, marks)
            if (!marks) continue
            debris(e, 6, ks, tsk, 0.45f, 0.85f, cyc.heroYaw)
            embers(e, 6, ks, tsk, rsk)
            scorch(e, 1.05f * s, ks, tsk, rsk)
            steam(e, 1, ks, tsk)
        }

        // At the feet, from the moment of the discharge.
        val tsd = cyc.ts - 0.18f
        if (tsd >= 0f && ground(cyc.tb + 0.18f, 0f, 0f, e2)) {
            val rsd = tsd / speed
            val bsd = flash(rsd)
            groundCracks(e2, 8, 0.3f, 2.2f, 3.2f, 14f, tsd, rsd, b, seed + 7, n)
            c.flatSprite(e2[0], e2[1], e2[2], 4.0f * sf, f.color(0.5f), 0.45f * bsd * faWide)
            dust(e2, 14, seed + 11, tsd, 0.7f, true)
            debris(e2, 24, seed + 13, tsd, 0.45f, 0.85f, cyc.heroYaw)
            // Burns along three of the cracks, out where they run - never a dark disc round the feet.
            for (j in intArrayOf(0, 3, 5)) {
                val a = crackAngle(seed + 7, j, 8, n)
                val r = 0.6f * crackLength(seed + 7, j, 2.2f, 3.2f)
                v[0] = e2[0] + cos(a) * r; v[1] = e2[1]; v[2] = e2[2] + sin(a) * r
                scorch(v, 0.6f * s, seed + 15 + j, tsd, rsd)
            }
        }

        // Aftermath: arcs lingering round the feet, and the god cooling down in steam.
        if (rs in 0.4f..1.6f && !f.groundMissing) {
            val al = 0.8f * (1f - (rs - 0.4f) / 1.2f)
            val epoch = floor(rs * 8f).toInt()
            for (k in 0 until 3) {
                val ks = seed + 500 + 13 * k + 7919 * epoch
                val a = hash(ks) * TAU
                val r = (0.8f + 0.5f * hash(ks + 1)) * s
                val sd = (k - 1) * 0.12f * bk
                val fx = sd * acrossX
                val fz = sd * acrossZ
                val pn = crack(7, ks, fx, 0.05f, fz, fx + cos(a) * r, groundY, fz + sin(a) * r, 3, 0.25f)
                drawBolt(7, pn, 1f, 0.07f * s, 0f, mix(f.color(0.4f), WHITE, 0.3f), al, 0f, ks, fadeIn = false)
            }
        }
        if (rs in 0.3f..1.5f) {
            val age = (rs - 0.3f) / 1.2f
            val bx = if (camHoriz > 0.01f) -f.camX / camHoriz * 0.1f else 0f
            val bz = if (camHoriz > 0.01f) -f.camZ / camHoriz * 0.1f else 0f
            for (k in 0 until 6) {
                val ox: Float; val oy: Float; val oz: Float
                when (k) {
                    0, 1 -> { val sd = if (k == 0) 1f else -1f; ox = 0.3f * bk * acrossX * sd; oy = 1.45f * bk; oz = 0.3f * bk * acrossZ * sd }
                    2, 3 -> { ox = tx + (k - 2.5f) * 0.3f * acrossX; oy = ty; oz = tz + (k - 2.5f) * 0.3f * acrossZ }
                    else -> {
                        val hand = if (k == 4) AuraAnchors.HAND_R else AuraAnchors.HAND_L
                        if (anchor != null) { ox = anchor[hand * 3]; oy = anchor[hand * 3 + 1]; oz = anchor[hand * 3 + 2] }
                        else { val sd = if (k == 4) 1f else -1f; ox = 0.4f * bk * acrossX * sd; oy = 0.8f * bk; oz = 0.4f * bk * acrossZ * sd }
                    }
                }
                wisp(ox + bx, oy, oz + bz, age * 1.2f, 1.2f, k, 0.25f)
            }
        }
    }

    /**
     * Thor calling the strike down: an arc leaps from the hand nearer the landing [p] into the main
     * channel in buffer 0 (point [ti] of [pn]) as it strikes, and dies out within half a second.
     */
    private fun handArc(pn: Int, ti: Int, p: FloatArray, rs: Float, b: Float, bs: Float, g: Float, kept: Float, tint: Int,
                        stroke: Int, seed: Int) {
        val a = anchors ?: return
        if (rs >= 0.5f) return
        val strength = channel(b, rs, g) * kept * (1f - smooth(0.3f, 0.5f, rs))
        if (strength < 0.01f) return
        val dr = hypot(a[AuraAnchors.HAND_R * 3] - p[0], a[AuraAnchors.HAND_R * 3 + 2] - p[2])
        val dl = hypot(a[AuraAnchors.HAND_L * 3] - p[0], a[AuraAnchors.HAND_L * 3 + 2] - p[2])
        val hand = if (dr <= dl) AuraAnchors.HAND_R else AuraAnchors.HAND_L
        val i = ti.coerceIn(0, pn)
        val n = crack(8, seed + 7, a[hand * 3], a[hand * 3 + 1], a[hand * 3 + 2], px[0][i], py[0][i], pz[0][i], 4, 0.2f,
            restroke = stroke)
        drawBolt(8, n, min(1f, rs / 0.04f), 0.16f * s * dayWidth(), 0.4f * s, tint, strength, 0.15f * bs * fa * kept * dayGlow(),
            seed + 7, fadeIn = false)
        c.sprite(a[hand * 3], a[hand * 3 + 1], a[hand * 3 + 2], 0.35f * bk, mix(tint, WHITE, 0.5f), 0.8f * strength, sparkle = true,
            spin = hash(seed + 8) * TAU)
    }

    /**
     * Thor's Call's head bolt into buffer 0: out of the cloud's edge on one side, bowing out and up
     * before it comes down onto the head. Returns its last index.
     */
    private fun headBolt(seed: Int, tx: Float, ty: Float, tz: Float, sideX: Float, sideZ: Float, restroke: Int): Int =
        crack(0, seed + 1, start[0], start[1], start[2], tx, ty, tz, 5, 0.14f, restroke = restroke,
            bowX = sideX * 0.7f, bowY = 0.7f, bowZ = sideZ * 0.7f, bow = 0.35f * s)

    /**
     * The stepped leader: the coming bolt feeling its way down its own path in visible jerks, each
     * step flaring and resting, with searching branches. Drawn until the bolt attaches.
     */
    private fun leader(cyc: Cycle, pn: Int, mainWidth: Float, steps: Int, seed: Int) {
        val progress = (cyc.tau - cyc.start) / cyc.leaderLen
        val stepIndex = ceil(steps * progress).coerceAtLeast(1f)
        val rho = (stepIndex / steps).coerceIn(0f, 1f)
        val stepStart = cyc.start + (stepIndex - 1f) * cyc.leaderLen / steps
        val alpha = if (hideFlash) 0.6f else 0.5f + 0.5f * exp(-(cyc.tau - stepStart) / 0.012f)
        val tint = mix(f.color(0.3f), WHITE, 0.5f)
        drawBolt(0, pn, rho, 0.5f * mainWidth, 0f, tint, alpha, 0f, seed)
        val tip = (rho * pn).toInt().coerceIn(0, pn)
        val tipX = px[0][tip]; val tipY = py[0][tip]; val tipZ = pz[0][tip]
        for (k in 1..8) {
            val frac = k / 9f
            if (rho < frac) break
            val bn = branch(0, pn, (frac * pn).toInt(), (0.2f + 0.3f * hash(seed + 300 + k)) * s,
                (hash(seed + 310 + k) - 0.5f) * 1.6f, seed + 320 + k, 3, 7)
            drawBolt(7, bn, 1f, 0.2f * mainWidth, 0f, tint, 0.45f, 0f, seed + k)
        }
        c.sprite(tipX, tipY, tipZ, 0.5f * s, f.color(0.6f), 0.85f, sparkle = true, spin = t * 7f)
    }

    /**
     * Upward streamers on the spot about to be struck: they stand frozen while the storm holds its
     * breath, like hair on end, then grow and flicker to meet the leader. Gone when it attaches.
     */
    private fun streamers(x: Float, y: Float, z: Float, cyc: Cycle, base: Int) {
        if (!(cyc.inHold || cyc.inLeader)) return
        val length = if (cyc.inLeader) 0.25f + 0.35f * (cyc.tau - cyc.start) / cyc.leaderLen else 0.25f
        val epoch = if (cyc.inLeader) floor(t * 30f).toInt() else 0
        for (j in 0 until 3) {
            val ks = 4001 * cyc.n + 37 * (base + j) + 7919 * epoch
            val ex = x + (hash(ks) - 0.5f) * 0.2f
            val ez = z + (hash(ks + 1) - 0.5f) * 0.2f
            val pn = crack(8, ks, x, y, z, ex, y + length * (0.7f + 0.3f * hash(ks + 2)), ez, 3, 0.25f)
            val flicker = if (!cyc.inLeader || hideFlash) 1f else if (hash(7 * floor(30f * t).toInt() + j + base) > 0.3f) 1f else 0.35f
            drawBolt(8, pn, 1f, 0.09f, 0f, f.color(0.6f), 0.8f * flicker, 0f, ks, fadeIn = false)
        }
    }

    // --- What a strike leaves behind ------------------------------------------------------------

    /**
     * The landing: a small white core that flickers with the strokes, a halo and sparkle that flash
     * once, and - where there is ground to leave marks on ([marks]) - an edgeless pool of light as if
     * the terrain were lit and a pillar of light up from it. [from] is where the bolt left the cloud,
     * which flashes too; null for none.
     */
    private fun impact(p: FloatArray, q: Float, b: Float, bs: Float, g: Float, ov: Float, from: FloatArray?, ts: Float, seed: Int,
                       marks: Boolean) {
        if (ts < 0f || ts > 1.6f) return
        val x = p[0]; val y = p[1]; val z = p[2]
        c.sprite(x, y + 0.1f * q, z, 0.5f * q * s, mix(f.color(0f), WHITE, 0.7f), b)
        c.sprite(x, y + 0.15f, z, 2.2f * q * sf, f.color(0.45f), 0.5f * bs * faWide * ov)
        c.sprite(x, y + 0.15f, z, 1.6f * q * sf, mix(f.color(0.2f), WHITE, 0.5f), 0.85f * bs * faWide * ov, sparkle = true,
            spin = hash(seed + 3) * TAU + 2f * ts)
        if (marks) {
            c.flatSprite(x, y, z, 3.2f * q * sf, f.color(0.5f), 0.55f * max(bs, 0.25f * g) * faWide * ov)
            if (bs > 0.004f) {
                c.beginRibbon()
                for (i in 0..4) {
                    val m = i / 4f
                    c.point(x, y + 1.3f * q * sf * m, z, lerp(0.55f, 0.1f, m) * q * sf, f.color(0.3f),
                        0.5f * bs * faWide * ov * (1f - m * 0.7f))
                }
                c.endRibbon()
            }
        }
        if (from != null) c.sprite(from[0], from[1], from[2], 1.1f * q * sf, f.color(0.4f), 0.6f * bs * faWide * ov * overhead())
        addFlash(x, y + 0.5f, z, bs * fa)
    }

    /**
     * The ground splitting under the blow: jagged cracks of lightning racing out from the strike,
     * each with a fork. Radial lines, never a circle.
     */
    private fun groundCracks(p: FloatArray, arms: Int, r0: Float, rMin: Float, rMax: Float, speedOut: Float,
                             ts: Float, rs: Float, b: Float, seed: Int, n: Int) {
        val nova = exp(-rs / 0.5f) * (0.6f + 0.4f * b) * sqrt(fa)
        if (ts < 0f || nova < 0.01f) return
        val front = r0 * s + speedOut * ts
        val y = p[1] + 0.02f
        val tint = f.color(0.35f)
        for (j in 0 until arms) {
            val a = crackAngle(seed, j, arms, n)
            val r1 = crackLength(seed, j, rMin, rMax)
            val ca = cos(a); val sa = sin(a)
            val x = px[9]; val yy = py[9]; val z = pz[9]
            var last = 0
            for (i in 0..9) {
                val m = i / 9f
                val r = r0 * s + m * (r1 - r0 * s)
                val jit = if (i == 0) 0f else (hash(seed * 31 + j * 17 + i) - 0.5f) * 0.12f * s + (hash(seed * 37 + j * 19 + i) - 0.5f) * 0.05f * s
                x[i] = p[0] + ca * r - sa * jit
                yy[i] = y
                z[i] = p[2] + sa * r + ca * jit
                if (r <= front) last = i
            }
            val reveal = if (front >= r1) 1f else ((front - r0 * s) / (r1 - r0 * s)).coerceIn(0f, 1f)
            if (reveal <= 0f) continue
            c.beginBolt(seed + j)
            val shown = reveal * 9f
            val upTo = floor(shown).toInt().coerceIn(0, 9)
            for (i in 0..upTo) c.point(x[i], yy[i], z[i], 0.2f * s * (1f - 0.5f * i / 9f), tint, nova)
            if (upTo < 9) {
                val fr = shown - upTo
                c.point(lerp(x[upTo], x[upTo + 1], fr), y, lerp(z[upTo], z[upTo + 1], fr), 0.2f * s * 0.6f, tint, nova)
            }
            c.endRibbon()
            // A fork from the middle, once the front has passed it.
            val from = 4 + (hash(seed + j + 90) * 3f).toInt()
            if (last >= from) {
                val fa2 = a + (if (hash(seed + j + 91) < 0.5f) -0.6f else 0.6f)
                val fl = 0.4f * (r1 - r0 * s)
                val fn = crack(10, seed + j + 92, x[from], y, z[from], x[from] + cos(fa2) * fl, y, z[from] + sin(fa2) * fl, 2, 0.2f)
                for (i in 0..fn) py[10][i] = y
                drawBolt(10, fn, 1f, 0.1f * s, 0f, tint, nova * 0.8f, 0f, seed + j, fadeIn = false)
            }
        }
    }

    /** The direction of crack arm [j] of [arms]: evenly spread with jitter, a new star every cycle. */
    private fun crackAngle(seed: Int, j: Int, arms: Int, n: Int): Float = j * TAU / arms + 0.5f * hash(seed + j) + 0.7f * n

    private fun crackLength(seed: Int, j: Int, rMin: Float, rMax: Float): Float = (rMin + (rMax - rMin) * hash(seed + j + 50)) * s

    /**
     * What a strike throws up: white-hot sparks and chunks of rock, sprayed away from the camera
     * ([ref]), falling under gravity and dying where they land.
     */
    private fun debris(p: FloatArray, count: Int, seed: Int, ts: Float, lifeMin: Float, lifeMax: Float, ref: Float) {
        if (ts < 0f || ts > lifeMax) return
        val rock = mix(0, 0x3B3630, lerp(0.3f, 1f, amb))
        for (k in 0 until count) {
            val ks = 37 * seed + k * 11
            val life = lifeMin + (lifeMax - lifeMin) * hash(ks)
            if (ts > life) continue
            val chunk = k % 2 == 1
            val beta = away(hash(ks + 1) * TAU, 0.9f, ref)
            val vh = (2.5f + 2.5f * hash(ks + 2)) * s
            val vy = if (chunk) 3f + 3f * hash(ks + 3) else 2f + 3f * hash(ks + 3)
            val land = vy / 7f
            if (ts > land + 0.05f) continue
            val a = min(ts, land)
            val cb = cos(beta); val sb = sin(beta)
            val fade = (1f - ts / life).pow(1.5f)
            val hx = p[0] + cb * vh * a
            val hy = p[1] + max(0f, vy * a - 7f * a * a)
            val hz = p[2] + sb * vh * a
            if (chunk) {
                c.sprite(hx, hy, hz, (0.08f + 0.08f * hash(ks + 4)) * s, rock, fade, spin = t * 9f + k, mode = MODE_SHARD)
                continue
            }
            val a0 = max(0f, a - 0.035f)
            val tint = mix(WHITE, f.color(0.3f), ts / life)
            c.beginRibbon()
            c.point(p[0] + cb * vh * a0, p[1] + max(0f, vy * a0 - 7f * a0 * a0), p[2] + sb * vh * a0, 0.04f * s, tint, 0f)
            c.point(hx, hy, hz, 0.04f * s, tint, fade)
            c.endRibbon()
            c.sprite(hx, hy, hz, 0.08f * s, tint, fade)
        }
    }

    /** Embers glowing on the ground round a strike, cooling from white through blue to violet. */
    private fun embers(p: FloatArray, count: Int, seed: Int, ts: Float, rs: Float) {
        if (ts < 0f || ts > 1.6f) return
        val glow = 0.9f * exp(-ts / 0.55f)
        val tint = f.color(min(1f, rs / 1.6f))
        for (k in 0 until count) {
            val ks = 41 * seed + 7 * k
            val a = hash(ks) * TAU
            val r = sqrt(hash(ks + 1)) * 0.55f * s
            c.sprite(p[0] + cos(a) * r, p[1] + 0.04f, p[2] + sin(a) * r, 0.1f * s, tint,
                glow * (0.6f + 0.4f * sin(31f * t + 2.7f * k)), sparkle = true, spin = hash(ks + 2) * TAU)
        }
    }

    /** Dust blasted out of a strike in ragged radial jets along the ground - never a ring of puffs. */
    private fun dust(p: FloatArray, count: Int, seed: Int, ts: Float, r0: Float, even: Boolean) {
        if (ts < 0f || ts > 0.7f) return
        val sd = min(s, 1.25f)
        val alpha = 0.5f * (1f - ts / 0.7f).pow(2) * smooth(0f, 0.04f, ts)
        val color = mix(0, 0x6E6458, lerp(0.16f, 1f, amb))
        for (k in 0 until count) {
            val ks = 43 * seed + 5 * k
            if (hash(ks) < 0.2f) continue
            val beta = if (even) k * TAU / count + (hash(ks + 1) - 0.5f) * 0.5f else hash(ks + 1) * TAU
            val len = 1.2f * sd * (1f - exp(-ts / 0.15f)) * (0.7f + 0.6f * hash(ks + 2))
            val cb = cos(beta); val sb = sin(beta)
            val ri = r0 * sd
            c.beginRibbon(STREAK_U)
            c.point(p[0] + cb * ri, p[1] + 0.05f, p[2] + sb * ri, 0.18f * sd, color, alpha)
            c.point(p[0] + cb * (ri + len), p[1] + 0.05f + 0.3f * len / (1.2f * sd), p[2] + sb * (ri + len), 0.05f * sd, color, 0.3f * alpha)
            c.endRibbon()
        }
    }

    /** A charred splash where a bolt struck, its cracks glowing and cooling. Stays put in the world. */
    private fun scorch(p: FloatArray, size: Float, seed: Int, ts: Float, rs: Float) {
        if (ts < 0f || ts > 2.5f) return
        val heat = when {
            rs < 0.6f -> mix(f.color(0.1f), f.color(1f), rs / 0.6f)
            rs < 1.2f -> mix(f.color(1f), 0, (rs - 0.6f) / 0.6f)
            else -> 0
        }
        val k = 1f - ts / 2.5f
        c.flatSprite(p[0], p[1] - 0.01f, p[2], size, heat, 0.75f * k * k, u0 = SCORCH_U, v0 = 2f * Math.floorMod(seed, 16),
            spin = hash(seed + 77) * TAU)
    }

    /** Steam rising off a fresh scorch: thin wisps curling up, three per [count]. */
    private fun steam(p: FloatArray, count: Int, seed: Int, ts: Float) {
        for (k in 0 until count * 3) {
            val age = ts - 0.15f - 0.1f * k
            if (age < 0f || age > 1.2f) continue
            val ks = 47 * seed + k
            wisp(p[0] + (hash(ks) - 0.5f) * 0.4f * s, p[1], p[2] + (hash(ks + 1) - 0.5f) * 0.4f * s, age, 1.2f, k, 0.3f)
        }
    }

    /**
     * One wisp of steam rising from (x, y, z), [age] seconds into its [life]: a thin curling streak,
     * widening as it climbs and fading out - never a grey ball.
     */
    private fun wisp(x: Float, y: Float, z: Float, age: Float, life: Float, k: Int, strength: Float) {
        val alpha = strength * (1f - age / life)
        if (alpha < 0.01f) return
        val top = 0.25f + 0.9f * age
        val color = dim(0xC9CED8)
        c.beginRibbon(STREAK_U)
        for (i in 0..4) {
            val m = i / 4f
            val wob = 0.08f * sin(7f * age + 2f * k + 3f * m) * m
            c.point(x + wob * cos(wind + k), y + 0.05f + top * m, z + wob * sin(wind + k), lerp(0.05f, 0.12f, m),
                color, alpha * sin(PI * (0.15f + 0.85f * m)))
        }
        c.endRibbon()
    }

    // --- Ambient lightning ----------------------------------------------------------------------

    /**
     * Lightning between the big strikes, more and more of it as the charge builds: bolts striking the
     * ground on the player's flanks, spider lightning crawling along the cloud's underside, and bolts
     * that die out in mid-air. Slots of 1/16 s; at most four bolts at once. Each lights its patch of
     * the cloud, but only every eighth slot brightly, so the cloud flares twice a second at most.
     */
    private fun ambient() {
        val slot = floor(T * 16.0).toLong()
        for (q in 0 until 8) {
            val j = slot - q
            val tj = j / 16.0
            val age = (T - tj).toFloat()
            if (age > 0.5f) continue
            val seed = (31L * j).toInt()
            if (hash(seed + 7) >= ambientChance(tj, 0.24f, 0.36f)) continue
            var kind = hash(seed + 11)
            if (f.groundMissing && kind < 0.45f) kind = 0.9f
            val cloudLight = (if (Math.floorMod(j, 8L) == 0L) 0.5f else 0.1f) *
                smooth(0f, 0.04f, age) * exp(-age / 0.15f) * (1f - smooth(0.35f, 0.5f, age)) * faWide
            val drawn = age <= 0.22f
            val restrike = !hideFlash && age > 0.1f && hash(seed + 12) > 0.4f
            val ba = when {
                age < 0.03f -> 0.6f
                else -> exp(-(age - 0.03f) / 0.035f) + if (restrike) 0.6f * exp(-(age - 0.1f) / 0.035f) else 0f
            }
            val bsa = when {
                age < 0.03f -> 0f
                age < 0.045f -> (age - 0.03f) / 0.015f
                else -> exp(-(age - 0.045f) / 0.08f)
            }
            val reveal = if (age < 0.03f) (age / 0.03f).pow(0.7f) else 1f
            val tint = f.color(0.15f + 0.4f * hash(seed + 13))
            when {
                kind < 0.45f -> {
                    val a1 = away(hash(seed + 1) * TAU, 40f * PI / 180f)
                    val r1 = reach(a1, (2.0f + 2.5f * hash(seed + 2)) * s)
                    val a0 = a1 + (hash(seed + 3) - 0.5f) * 0.8f
                    val r0 = (0.3f + 0.8f * hash(seed + 4)) * sc
                    val sx = cos(a0) * r0; val sz = sin(a0) * r0
                    val sy = belly - 0.06f
                    addLight(sx, sy, sz, cloudLight)
                    if (!drawn) continue
                    val ex = cos(a1) * r1; val ez = sin(a1) * r1
                    val pn = crack(0, seed, sx, sy, sz, ex, groundY, ez, 5, 0.17f, restroke = if (restrike) 1 else 0)
                    drawBolt(0, pn, reveal, 0.12f * s, 0.5f * s, tint, min(1f, ba), 0.3f * bsa * fa, seed)
                    for (k in 0 until 2) {
                        val bn = branch(0, pn, 6 + (hash(seed + 20 + k) * 14f).toInt(), (0.35f + 0.35f * hash(seed + 22 + k)) * s,
                            (hash(seed + 24 + k) - 0.5f) * 1.2f, seed + 30 + k, 4, 1 + k)
                        if (reveal >= 1f) drawBolt(1 + k, bn, 1f, 0.07f * s, 0f, tint, 0.7f * ba, 0f, seed + k)
                    }
                    if (age >= 0.03f) {
                        c.sprite(ex, groundY + 0.08f, ez, 0.2f * s, WHITE, min(1f, ba))
                        c.sprite(ex, groundY + 0.1f, ez, 0.9f * sf, tint, 0.35f * bsa * faWide)
                        c.flatSprite(ex, groundY, ez, 1.3f * sf, tint, 0.3f * bsa * faWide)
                        v[0] = ex; v[1] = groundY; v[2] = ez
                        debris(v, 4, seed, age - 0.03f, 0.3f, 0.5f, camYaw)
                    }
                    addFlash(ex, groundY + 0.5f, ez, 0.6f * bsa * fa)
                }
                kind < 0.75f -> {
                    // Spider lightning, racing along the underside of the cloud.
                    val a0 = hash(seed + 1) * TAU
                    val a1 = a0 + (hash(seed + 2) - 0.5f) * 2f
                    val r0 = (0.1f + 0.3f * hash(seed + 3)) * sc
                    val r1 = (1.6f + 0.6f * hash(seed + 4)) * sc
                    val y0 = belly - 0.06f
                    val pn = crack(0, seed, cos(a0) * r0, y0, sin(a0) * r0, cos(a1) * r1, y0, sin(a1) * r1, 5, 0.18f)
                    for (i in 0..pn) py[0][i] = y0 + (py[0][i] - y0) * 0.25f
                    val mid = pn / 2
                    addLight(px[0][mid], py[0][mid], pz[0][mid], cloudLight)
                    if (!drawn) continue
                    val crawl = min(1f, age / 0.12f)
                    val ac = if (age < 0.12f) 0.9f else exp(-(age - 0.12f) / 0.04f)
                    drawBolt(0, pn, crawl, 0.07f * s, 0.3f * s, tint, ac, 0.25f * max(bsa, if (age < 0.12f) 0.6f else 0f) * fa, seed)
                    for (k in 0 until 2) {
                        val from = (pn * (0.3f + 0.4f * k)).toInt()
                        if (from > crawl * pn) continue
                        val da = (40f + 25f * hash(seed + 40 + k)) * PI / 180f
                        val dl = (0.3f + 0.2f * hash(seed + 42 + k)) * s
                        val ox = px[0][from]; val oyy = py[0][from]; val oz = pz[0][from]
                        val hl = hypot(ox, oz).coerceAtLeast(0.01f)
                        val bn = crack(1 + k, seed + 50 + k, ox, oyy, oz, ox + ox / hl * dl * cos(da), oyy - dl * sin(da), oz + oz / hl * dl * cos(da), 3, 0.2f)
                        drawBolt(1 + k, bn, 1f, 0.05f * s, 0f, tint, 0.7f * ac, 0f, seed + k, fadeIn = false)
                    }
                }
                else -> {
                    // A bolt that never reaches the ground and dies out in mid-air with a fizzle.
                    val a1 = away(hash(seed + 1) * TAU, 40f * PI / 180f)
                    val r1 = reach(a1, (1.8f + 1.5f * hash(seed + 2)) * s)
                    val a0 = a1 + (hash(seed + 3) - 0.5f) * 0.8f
                    val r0 = (0.3f + 0.8f * hash(seed + 4)) * sc
                    val sy = belly - 0.06f
                    addLight(cos(a0) * r0, sy, sin(a0) * r0, cloudLight)
                    if (!drawn) continue
                    val ey = (0.35f + 0.45f * hash(seed + 5)) * h
                    val pn = crack(0, seed, cos(a0) * r0, sy, sin(a0) * r0, cos(a1) * r1, ey, sin(a1) * r1, 5, 0.17f)
                    drawBolt(0, pn, reveal, 0.1f * s, 0.4f * s, tint, min(1f, ba), 0.25f * bsa * fa, seed, fadeOut = true)
                    if (reveal >= 1f) {
                        for (k in 0 until 3) {
                            val bn = branch(0, pn, (pn * (0.75f + 0.2f * hash(seed + 60 + k))).toInt(),
                                (0.15f + 0.15f * hash(seed + 63 + k)) * s, (hash(seed + 66 + k) - 0.5f) * 2f, seed + 70 + k, 2, 1 + k)
                            drawBolt(1 + k, bn, 1f, 0.04f * s, 0f, tint, 0.6f * ba, 0f, seed + k, fadeIn = false)
                        }
                    }
                }
            }
        }
        // Sheet lightning: the cloud glowing from inside now and then, with no bolt to be seen.
        val sheetSlot = floor(T / 0.12).toLong()
        for (q in 0 until 4) {
            val j = sheetSlot - q
            val tj = j * 0.12
            val age = (T - tj).toFloat()
            val seed = (53L * j).toInt()
            if (hash(seed + 3) >= ambientChance(tj, 0.06f, 0.12f)) continue
            val env = smooth(0f, 0.06f, age) * exp(-age / 0.2f)
            val a = hash(seed + 4) * TAU
            val r = sqrt(hash(seed + 5)) * 1.6f * sc
            addLight(cos(a) * r, cy + 0.1f * sc, sin(a) * r, 0.15f * env * faWide)
        }
    }

    // --- The charge -----------------------------------------------------------------------------

    /**
     * The charge gathering under the cloud before a strike: a glow swelling over the head with motes
     * of light spiralling into it along the cloud's belly. Through the hold it collapses to a white-hot
     * pinpoint and slides out to where the bolt will leave the cloud.
     */
    private fun chargeCore() {
        if (cur.tau >= cur.attach || charge <= 0.02f) return
        val k = charge
        val up = overhead()
        var x = 0f
        var y = belly - 0.12f
        var z = 0f
        val slide = smooth(cur.start - cur.holdLen - 0.3f, cur.start - cur.holdLen, cur.tau)
        if (slide > 0f) {
            heroStart(cur, start)
            x = lerp(x, start[0], slide); y = lerp(y, start[1], slide); z = lerp(z, start[2], slide)
        }
        if (hold || cur.inLeader) {
            c.sprite(x, y, z, 0.56f * sc, mix(f.color(0.4f), WHITE, 0.8f), up)
            c.sprite(x, y, z, 0.12f * sc, WHITE, up)
        } else {
            c.sprite(x, y, z, (0.35f + 0.7f * k) * sc, f.color(0.4f), (0.12f + 0.45f * k) * (0.88f + 0.12f * sin(TAU * 2f * t)) * up)
            c.sprite(x, y, z, (0.12f + 0.12f * k) * sc, mix(f.color(0.2f), WHITE, 0.7f),
                (0.3f + 0.6f * k) * (0.7f + 0.3f * sin(t * (10f + 14f * k))) * up)
            c.sprite(x, y, z, 0.9f * sc, f.color(0.3f), 0.5f * k * up, sparkle = true, spin = t * (1f + 4f * k))
        }
        addLight(x, y, z, k * (0.15f + 0.1f * sin(TAU * 2f * t)) * faWide)
        if (k <= 0.05f) return
        val swirl = turn(0.3)
        for (i in 0 until 12) {
            val age = fract(t / 1.2f + hash(i * 3 + 900))
            c.beginRibbon()
            for (jj in 0..3) {
                val ag = age - 0.03f * jj
                if (ag < 0f) break
                val a = hash(i * 3 + 901) * TAU + 2.5f * ag + swirl
                val r = lerp(1.5f * sc, 0.1f, ag.pow(1.5f))
                c.point(cos(a) * r, belly - 0.06f + 0.04f * sin(3f * t + i), sin(a) * r, 0.04f * s * (1f - jj / 3f),
                    f.color(0.3f), 0.85f * k * sin(PI * ag) * (1f - jj / 3f) * up)
            }
            c.endRibbon()
            val a = hash(i * 3 + 901) * TAU + 2.5f * age + swirl
            val r = lerp(1.5f * sc, 0.1f, age.pow(1.5f))
            c.sprite(cos(a) * r, belly - 0.06f + 0.04f * sin(3f * t + i), sin(a) * r, 0.1f * s, f.color(0.3f),
                0.85f * k * sin(PI * age) * up, sparkle = true, spin = t * 3f + i)
        }
    }

    /**
     * Before Thor's Call: three surges of electricity coil up the body, each ending in a flare of the
     * eyes. The arms hide half of every turn, so they read as winding round the player.
     */
    private fun surgeCoils() {
        if (cur.kind != THORS_CALL || cur.tau >= cur.attach) return
        val epoch = floor(t * 30f).toInt()
        for (index in COIL_LEADS.indices) {
            val since = cur.tau - (cur.start - COIL_LEADS[index])
            if (since in 0.36f..1.0f) {
                val p = flash((since - 0.36f) / speed)
                eyeBoost += 0.4f * p
                addLight(0f, belly - 0.12f, 0f, 0.3f * p * faWide)
            }
            if (since < 0f || since > 0.36f) continue
            val q = smooth(0f, 1f, since / 0.36f)
            for (stream in 0 until 2) {
                c.beginBolt(index * 2 + stream)
                var hx = 0f; var hy = 0f; var hz = 0f
                for (i in 0 until 16) {
                    val m = (q - 0.3f + 0.3f * i / 15f)
                    if (m < 0f) continue
                    val a = m * 1.6f * TAU + stream * PI + f.bodyYaw
                    val ks = 7 * epoch + 31 * i + 101 * stream + 977 * index
                    hx = cos(a) * 0.42f * bk + (hash(ks) - 0.5f) * 0.08f
                    hy = 0.05f + m * h
                    hz = sin(a) * 0.42f * bk + (hash(ks + 1) - 0.5f) * 0.08f
                    c.point(hx, hy, hz, 0.1f * bk, f.color(0.3f), 0.3f + 0.7f * i / 15f)
                }
                c.endRibbon()
                c.sprite(hx, hy, hz, 0.3f * bk, f.color(0.3f), 0.8f)
                c.sprite(hx, hy, hz, 0.45f * bk, mix(f.color(0.3f), WHITE, 0.4f), 0.6f, sparkle = true, spin = t * 5f)
            }
        }
    }

    // --- The thunderhead ------------------------------------------------------------------------

    /**
     * The cloud: a broad inky base deck, towers piled on it, an anvil sheared off behind, and dark
     * scraps of scud wheeling round the rim at visible speed - what tells you the storm turns round
     * you. It darkens as the charge builds and at night, opens towards the camera, and lightning
     * lights the puffs from inside rather than shining in front of them.
     */
    private fun thunderhead() {
        val tint = f.color(0.5f)
        val spread = 1f + 0.08f * heroGlow
        puffCount = 0
        val deckTurn = turn(0.15)
        val outerTurn = turn(0.25)
        // Seen from below (the front view looks up at it), flat puffs would leave sky between their
        // rows; there the deck's puffs grow taller and wider until they close up.
        val under = smooth(0.3f, 0.8f, (belly - f.camY) / max(camHoriz, 0.5f))
        // Base deck: a center, two rings, and a wide outer ring that makes the storm reach out.
        for (i in 0 until 22) {
            val r: Float
            val a: Float
            var w = (1.0f + 0.25f * hash(13 * i + 5)) * sc * (1f + 0.25f * under)
            var hgt = lerp(0.4f, 0.9f, under) * sc
            when {
                i == 0 -> { r = 0f; a = 0f }
                i < 7 -> { r = 0.6f * sc; a = (i - 1) * TAU / 6 + deckTurn + 0.4f * hash(13 * i + 3) }
                i < 13 -> { r = 1.2f * sc * (0.92f + 0.16f * hash(13 * i + 4)); a = (i - 7) * TAU / 6 + PI / 6 + deckTurn + 0.4f * hash(13 * i + 3) }
                else -> {
                    r = 1.9f * sc * (0.95f + 0.1f * hash(13 * i + 4))
                    a = (i - 13) * TAU / 9 + PI / 9 + outerTurn
                    w = 1.3f * sc * (1f + 0.25f * under)
                    hgt = lerp(0.32f, 0.8f, under) * sc
                }
            }
            val swell = 1f + 0.05f * sin(1.1f * t + 2.3f * i)
            addPuff(cos(a) * r * spread, belly + 0.2f * sc + 0.015f * sc * sin(0.7f * t + i), sin(a) * r * spread,
                w * swell, hgt * swell, mix(0x5A6072, tint, 0.1f), mix(0x1E2129, tint, 0.06f), 0.95f)
        }
        // Towers.
        for (i in 0 until 14) {
            val l = hash(17 * i + 101)
            val r = sqrt(hash(17 * i + 104)) * (1f - 0.55f * l) * sc
            val a = hash(17 * i + 102) * TAU + turn(0.12 + 0.08 * hash(17 * i + 103)) * (if (i % 2 == 0) 1f else -1f)
            val size = (0.85f - 0.4f * l + 0.1f * hash(17 * i + 105)) * sc * (1f + 0.05f * sin(1.1f * t + 2.3f * i))
            val top = mix(mix(0x8F96A8, 0xC9CED8, l), tint, 0.1f)
            addPuff(cos(a) * r * spread, cy + (0.08f + 0.85f * l) * sc + 0.02f * sc * sin(0.9f * t + i), sin(a) * r * spread,
                size, 0.9f * size, top, mix(mix(0x343846, tint, 0.06f), top, 0.3f * l), 0.95f)
        }
        // Anvil, sheared off on the far side.
        val beta = camYaw + PI + 0.5f
        for (j in 0 until 5) {
            val along = (0.30f + 0.10f * j) * sc
            val sideways = (j - 2) * 0.35f * sc
            addPuff((cos(beta) * along - sin(beta) * sideways) * spread,
                cy + 0.85f * sc + 0.06f * sc * (j % 2) + 0.04f * sc * sin(0.6f * t + j),
                (sin(beta) * along + cos(beta) * sideways) * spread, 1.0f * sc, 0.34f * sc,
                mix(0xA8B0C0, tint, 0.06f), 0x6A7080, 0.95f)
        }
        // Shelf scud: ragged scraps round the rim, uneven in size, height and spacing so they never
        // line up into a ring.
        val scudTurn = turn(0.6)
        for (i in 0 until 12) {
            val r = (2.2f + 0.4f * hash(19 * i + 300)) * sc
            val a = scudTurn + i * TAU / 12 + 0.45f * hash(19 * i + 301)
            val w = (0.55f + 0.3f * hash(19 * i + 302)) * sc
            addPuff(cos(a) * r * spread, belly + 0.06f * sc + (hash(19 * i + 303) - 0.5f) * 0.12f * sc + 0.03f * sin(1.3f * t + i),
                sin(a) * r * spread, w, (0.3f + 0.12f * hash(19 * i + 304)) * sc * lerp(1f, 2f, under),
                mix(0x3E4352, tint, 0.06f), 0x1A1C23, 0.85f + 0.1f * hash(19 * i + 305))
        }

        val litColorBottom = mix(f.color(0.45f), WHITE, 0.15f)
        val litColorTop = mix(f.color(0.15f), WHITE, 0.3f)
        // Darker as the charge builds and through the hold; darker at night and underground.
        val dark = 0.35f * charge
        val night = lerp(0.16f, 1f, amb)
        val holdLight = if (hold) 1f - smooth(0f, 0.08f, (cur.tau - (cur.start - cur.holdLen)) / speed) else 1f
        val roof = if (f.ceiling < h + 0.75f) smooth(h + 0.45f, h + 0.75f, f.ceiling) else 1f
        val fromAbove = lerp(1f, 0.45f, above)
        for (i in 0 until puffCount) {
            var light = cloudFlash
            for (k in 0 until lights) {
                val dx = puffX[i] - lightX[k]
                val dy = puffY[i] - lightY[k]
                val dz = puffZ[i] - lightZ[k]
                light += lightI[k] * exp(-(dx * dx + dy * dy + dz * dz) / lightR[k])
            }
            // Capped below full, so a lit cloud keeps its dark gaps and bright lumps instead of
            // turning into pale glass.
            light = (light * holdLight).coerceIn(0f, 1f)
            val top = mix(mix(puffTop[i], 0, dark), 0, 1f - night)
            val bottom = mix(mix(puffBottom[i], 0, dark), 0, 1f - night)
            val litBottom = mix(bottom, litColorBottom, min(0.75f, 0.85f * light))
            val litTop = mix(top, litColorTop, 0.25f * light)
            // A puff that has to make way - on the camera's side of the outer cloud, or in the line of
            // sight - shrinks away rather than turning into a see-through lens.
            val r = hypot(puffX[i], puffZ[i])
            val side = lerp(1f, openSide(puffX[i], puffZ[i]), smooth(1.0f * sc, 1.5f * sc, r))
            val sight = sightClear(puffX[i], puffY[i], puffZ[i], puffW[i])
            val keepSize = min(side, sight)
            val a = puffAlpha[i] * nearCamera(puffX[i], puffY[i], puffZ[i], puffW[i]) * roof * fromAbove *
                smooth(0.15f, 0.45f, side) * smooth(0.1f, 0.35f, sight)
            if (a < 0.01f) continue
            c.puff(puffX[i], puffY[i], puffZ[i], puffW[i] * lerp(0.2f, 1f, keepSize), puffH[i] * lerp(0.4f, 1f, keepSize),
                litTop, litBottom, a, i, glow = light)
        }
    }

    private fun addPuff(x: Float, y: Float, z: Float, w: Float, hgt: Float, top: Int, bottom: Int, alpha: Float) {
        if (puffCount >= PUFFS) return
        puffX[puffCount] = x; puffY[puffCount] = y; puffZ[puffCount] = z
        puffW[puffCount] = w; puffH[puffCount] = hgt
        puffTop[puffCount] = top; puffBottom[puffCount] = bottom; puffAlpha[puffCount] = alpha
        puffCount++
    }

    /** The cloud's outer reaches thin out on the camera's side, so the storm opens towards the viewer. */
    private fun openSide(x: Float, z: Float): Float = lerp(smooth(0.9f, 1.4f, abs(wrap(atan2(z, x) - camYaw))), 1f, over)

    /** How much of a puff [w] wide at (x, y, z) to show near the camera: none as it reaches the lens. */
    private fun nearCamera(x: Float, y: Float, z: Float, w: Float): Float {
        val dx = x - f.camX; val dy = y - f.camY; val dz = z - f.camZ
        return smooth(0.6f, 1.6f, sqrt(dx * dx + dy * dy + dz * dz) - 0.4f * w)
    }

    /**
     * 0..1, how clear a puff [w] wide at (x, y, z) is of the lines of sight to the player's head and
     * feet: looking steeply down, the puffs in the way make room instead of hiding the player.
     */
    private fun sightClear(x: Float, y: Float, z: Float, w: Float): Float {
        val dx = x - f.camX; val dy = y - f.camY; val dz = z - f.camZ
        val off = min(sightDistance(dx, dy, dz, 0.9f * h), sightDistance(dx, dy, dz, 0.05f))
        return smooth(0.5f * w, 0.5f * w + 0.5f, off)
    }

    /** Distance from a point (given relative to the camera) to the segment from the camera to (0, [y], 0). */
    private fun sightDistance(dx: Float, dy: Float, dz: Float, y: Float): Float {
        val tx = -f.camX; val ty = y - f.camY; val tz = -f.camZ
        val tl2 = tx * tx + ty * ty + tz * tz
        val k = if (tl2 < 1e-6f) 0f else ((dx * tx + dy * ty + dz * tz) / tl2).coerceIn(0f, 1f)
        val qx = dx - tx * k; val qy = dy - ty * k; val qz = dz - tz * k
        return sqrt(qx * qx + qy * qy + qz * qz)
    }

    /** A color of matter, darkened with the light round the player. */
    private fun dim(rgb: Int): Int = mix(0, rgb, lerp(0.16f, 1f, amb))

    // --- Rain and wind --------------------------------------------------------------------------

    /**
     * Rain spiralling down round a dry eye: dark streaks by day, pale ones at night, lit white round
     * every strike. It thickens as the charge builds.
     */
    private fun rain() {
        val yTop = min(belly - 0.05f, f.ceiling - 0.05f)
        val yGround = -f.groundDrop
        val fall = yTop - yGround
        if (fall < 0.3f) return
        val span = fall + 0.48f
        val windNow = turn(0.5)
        val windBack = (0.5 * (windD - windAt(T - 0.09))).toFloat()
        val baseColor = mix(mix(0x3A4352, 0xA9B8CC, 1f - amb), f.color(0.4f), 0.2f)
        for (d in 0 until 320) {
            if (hash(d * 13 + 5) >= 0.5f + 0.5f * charge) continue
            val phase = T / RAIN_PERIOD + hash(d)
            val m = floor(phase).toInt()
            val k = (phase - m).toFloat()
            val seed = 7919 * d + 104729 * m
            val r = sc * (0.6f + 2.0f * sqrt(hash(seed + 2)))
            val aNow = hash(seed + 1) * TAU + windNow
            val y = yTop - span * k
            val x = cos(aNow) * r
            val z = sin(aNow) * r
            if (y < yGround) {
                // The last moment: a splash, if there is ground to splash on.
                if (f.groundDrop < 0.2f) {
                    val kk = ((yGround - y) / 0.48f).coerceIn(0f, 1f)
                    c.sprite(x, yGround + 0.04f, z, 0.08f, baseColor, 0.3f * (1f - kk))
                }
                continue
            }
            var flashLight = 0f
            for (q in 0 until flashes) {
                val dx = x - flashX[q]; val dy = y - flashY[q]; val dz = z - flashZ[q]
                flashLight = max(flashLight, flashB[q] * exp(-(dx * dx + dy * dy + dz * dz) / 4f))
            }
            val front = r < 1.4f && abs(wrap(aNow - camYaw)) < 0.9f
            var alpha = (0.5f + 0.2f * charge) * (if (front) lerp(0.55f, 1f, over) else 1f) * (if (hold) 0.6f else 1f) +
                0.55f * flashLight
            if (r > 1.3f * sc) alpha *= openSide(x, z)
            if (alpha < 0.01f) continue
            // Always leaning with the swirl, a fifth of a block over the streak, even when frozen.
            val aBefore = aNow - windBack - 0.2f / r
            val tint = mix(baseColor, WHITE, 0.7f * flashLight)
            c.beginRibbon(STREAK_U)
            c.point(cos(aBefore) * r, min(yTop, y + 0.9f), sin(aBefore) * r, 0.04f, tint, 0f)
            c.point(x, y, z, 0.04f, tint, alpha)
            c.endRibbon()
        }
    }

    /**
     * The updraft: wind spirals climbing from the ankles into the cloud's belly, the anime way of
     * drawing wind, and chips of debris tumbling up the funnel and flung out at the top. A strike
     * shoves the whole vortex outwards for a moment.
     */
    private fun updraft() {
        // White by day, the way anime draws wind, so it shows against sand and sky alike.
        val streakColor = mix(0xE8EEF6, 0xDCE8F5, 1f - amb)
        for (i in 0 until 12) {
            val life = 1.6f + 0.6f * hash(i * 7)
            val theta0 = hash(i * 7 + 1) * TAU
            val k = fract(t / life + hash(i * 7 + 2))
            c.beginRibbon(STREAK_U)
            for (j in 0..17) {
                val kk = k - 0.016f * j
                if (kk < 0f) break
                val a = theta0 + wind + 4.2f * kk
                var r = (2.0f - 0.9f * kk) * s * (1f + 0.3f * push)
                if (abs(wrap(a - camYaw)) < PI / 2) r = min(r, lerp(0.85f * camHoriz, r, over))
                val y = 0.12f + (belly - 0.57f) * kk.pow(1.2f)
                val camFade = if (r < 1.6f) 0.35f + 0.65f * smooth(0.6f, 1.3f, abs(wrap(a - camYaw))) else 1f
                val fade = (1f - j / 17f)
                val alpha = 0.9f * (0.5f + 0.5f * charge) * sin(PI * kk) * fade.pow(1.5f) * camFade * (if (hold) 0.5f else 1f)
                c.point(cos(a) * r, y, sin(a) * r, 0.15f * s * fade + 0.006f, streakColor, alpha)
            }
            c.endRibbon()
        }
        val chipTurn = turn(1.3)
        for (i in 0 until 12) {
            val k = fract(t / 3.2f + hash(i * 5 + 100))
            val theta0 = hash(i * 5 + 101) * TAU
            val size = (0.1f + 0.1f * hash(i * 5 + 103)) * s
            val tint = dim(mix(CHIP_COLORS[(hash(i * 5 + 102) * 3f).toInt().coerceIn(0, 2)], 0x9DB8E0, 0.5f * heroGlow))
            val alpha = 0.95f * smooth(0f, 0.06f, k) * (1f - smooth(0.85f, 1f, k))
            chipAt(k, theta0, chipTurn, v)
            chipAt(max(0f, k - 0.02f), theta0, chipTurn, e2)
            c.beginRibbon(STREAK_U)
            c.point(e2[0], e2[1], e2[2], 0.6f * size, tint, 0f)
            c.point(v[0], v[1], v[2], 0.6f * size, tint, 0.5f * alpha)
            c.endRibbon()
            c.sprite(v[0], v[1], v[2], size, tint, alpha, spin = t * (5f + 7f * hash(i * 5 + 104)), mode = MODE_SHARD)
        }
    }

    /** Where a debris chip is at [k] (0..1) of its climb up the funnel. */
    private fun chipAt(k: Float, theta0: Float, chipTurn: Float, out: FloatArray) {
        val r = (1.3f - 0.6f * k) * s * (1f + 0.3f * push) + (if (k > 0.85f) (k - 0.85f) * 8f * s else 0f)
        val a = theta0 + chipTurn + 5.5f * k
        out[0] = cos(a) * r
        out[1] = 0.08f + (belly - 0.5f) * k.pow(1.4f)
        out[2] = sin(a) * r
    }

    // --- On the body ----------------------------------------------------------------------------

    /** A point on a flattened ring that hugs the model at [y]: legs, then shoulders with arms, then head. */
    private fun ellipse(a: Float, y: Float, out: FloatArray) {
        val yn = y / bk
        val rx = bk * lerp(lerp(0.34f, 0.56f, smooth(0.62f, 0.80f, yn)), 0.33f, smooth(1.42f, 1.52f, yn))
        val rz = bk * lerp(0.22f, 0.33f, smooth(1.42f, 1.52f, yn))
        val acrossX = cos(f.bodyYaw); val acrossZ = sin(f.bodyYaw)
        val fwdX = -sin(f.bodyYaw); val fwdZ = cos(f.bodyYaw)
        val ca = cos(a) * rx
        val sa = sin(a) * rz
        out[0] = acrossX * ca + fwdX * sa
        out[1] = y
        out[2] = acrossZ * ca + fwdZ * sa
    }

    /**
     * St. Elmo's fire: short arcs crawling over the body, a few in the calm, more as the charge
     * builds, a storm of them at a strike. Each lives a twelfth of a second; they freeze in the hold.
     */
    private fun bodyCrackle() {
        val p0 = e2
        for (j in 0 until crackleCount) {
            val epoch = floor((24f * tc + 0.618f * j) / 2f).toInt()
            val seed = 131 * j + 7919 * epoch
            val a0 = hash(seed) * TAU
            val y0 = 0.08f + hash(seed + 1) * (h - 0.03f)
            ellipse(a0, y0, p0)
            val a1 = a0 + (if (hash(seed + 2) < 0.5f) -1f else 1f) * (0.5f + 0.8f * hash(seed + 3))
            val y1 = (y0 + (hash(seed + 4) - 0.5f) * 0.9f).coerceIn(0.05f, h + 0.05f)
            ellipse(a1, y1, v)
            val am = (a0 + a1) * 0.5f
            val bx = cos(f.bodyYaw) * cos(am) - sin(f.bodyYaw) * sin(am)
            val bz = sin(f.bodyYaw) * cos(am) + cos(f.bodyYaw) * sin(am)
            val pn = crack(11, seed, p0[0], p0[1], p0[2], v[0], v[1], v[2], 3, 0.2f, bowX = bx, bowZ = bz, bow = 0.07f * bk)
            val flicker = if (hideFlash || hash(13 * floor(30f * tc).toInt() + j) > 0.25f) 1f else 0.3f
            drawBolt(11, pn, 1f, 0.045f * bk, 0.16f * bk, f.color(0.4f), 0.9f * flicker, 0.3f * fa * flicker, seed, fadeIn = false)
            c.sprite(v[0], v[1], v[2], 0.14f * bk, f.color(0.3f), 0.75f, sparkle = true, spin = hash(seed + 5) * TAU)
        }
    }

    /**
     * Thor's eyes: burning white with a flare across them, and two wings of light sweeping back and up
     * from them past the temples, like the winged helmet. From behind the head hides the eyes and the
     * wings flare out from it; in front view the eyes burn.
     */
    private fun eyesAndWings() {
        val a = anchors ?: return
        val strike = if (thorAfter >= 0f) flash(thorAfter) else 0f
        val eyeI = 0.45f + 0.45f * charge + 0.5f * heroGlow + eyeBoost
        val hcx = a[AuraAnchors.HEAD_CENTER * 3]; val hcy = a[AuraAnchors.HEAD_CENTER * 3 + 1]; val hcz = a[AuraAnchors.HEAD_CENTER * 3 + 2]
        var fx = a[AuraAnchors.HEAD_FRONT * 3] - hcx; var fy = a[AuraAnchors.HEAD_FRONT * 3 + 1] - hcy; var fz = a[AuraAnchors.HEAD_FRONT * 3 + 2] - hcz
        var ux = a[AuraAnchors.HEAD_TOP * 3] - hcx; var uy = a[AuraAnchors.HEAD_TOP * 3 + 1] - hcy; var uz = a[AuraAnchors.HEAD_TOP * 3 + 2] - hcz
        var sx = a[AuraAnchors.EYE_B * 3] - a[AuraAnchors.EYE_A * 3]
        var sy = a[AuraAnchors.EYE_B * 3 + 1] - a[AuraAnchors.EYE_A * 3 + 1]
        var sz = a[AuraAnchors.EYE_B * 3 + 2] - a[AuraAnchors.EYE_A * 3 + 2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz); val ul = sqrt(ux * ux + uy * uy + uz * uz); val sl = sqrt(sx * sx + sy * sy + sz * sz)
        if (fl < 1e-4f || ul < 1e-4f || sl < 1e-4f) return
        fx /= fl; fy /= fl; fz /= fl
        ux /= ul; uy /= ul; uz /= ul
        sx /= sl; sy /= sl; sz /= sl
        val glow = min(1f, eyeI)
        val wing = px[10]; val wingY = py[10]; val wingZ = pz[10]
        // The eyes' halo only shows from in front: from behind it would leak round the head as a ring.
        var tcx = f.camX - hcx; var tcy = f.camY - hcy; var tcz = f.camZ - hcz
        val tcl = sqrt(tcx * tcx + tcy * tcy + tcz * tcz).coerceAtLeast(1e-4f)
        tcx /= tcl; tcy /= tcl; tcz /= tcl
        val facing = smooth(-0.2f, 0.5f, fx * tcx + fy * tcy + fz * tcz)
        for (eye in 0 until 2) {
            val idx = if (eye == 0) AuraAnchors.EYE_A else AuraAnchors.EYE_B
            val ex = a[idx * 3]; val ey = a[idx * 3 + 1]; val ez = a[idx * 3 + 2]
            val out = if (eye == 0) -1f else 1f
            c.sprite(ex, ey, ez, 0.06f * bk, WHITE, glow)
            c.sprite(ex, ey, ez, (0.3f + 0.2f * charge + 0.25f * heroGlow) * bk * lerp(0.6f, 1f, facing), f.color(0.3f), 0.8f * glow * facing)
            val l = (0.08f + 0.15f * charge) * bk
            val flareTint = mix(f.color(0.35f), WHITE, 0.4f)
            c.beginRibbon()
            c.point(ex - sx * l, ey - sy * l, ez - sz * l, 0.03f * bk, flareTint, 0f)
            c.point(ex, ey, ez, 0.03f * bk, flareTint, (0.35f + 0.5f * charge) * eyeI)
            c.point(ex + sx * l, ey + sy * l, ez + sz * l, 0.03f * bk, flareTint, 0f)
            c.endRibbon()
            // The helm wing: three feathers from one root, out to the side, then back and up, each
            // shorter and steeper than the one before, with a crackling edge along the first.
            for (feather in 0 until 3) {
                val length = 1f - 0.2f * feather
                for (i in 0..7) {
                    val m = i / 7f * length
                    val ko = (0.06f + 0.55f * m.pow(0.8f)) * bk * out * (1f - 0.2f * feather)
                    val kf = -(0.08f + 0.40f * m) * bk
                    val ku = (0.04f + (0.36f + 0.18f * feather) * m.pow(1.6f - 0.3f * feather)) * bk + 0.015f * i * sin(9f * t + 0.8f * i + feather)
                    wing[i] = ex + sx * ko + fx * kf + ux * ku
                    wingY[i] = ey + sy * ko + fy * kf + uy * ku
                    wingZ[i] = ez + sz * ko + fz * kf + uz * ku
                }
                val width = (if (feather == 0) 0.17f else if (feather == 1) 0.12f else 0.09f) * bk
                c.beginRibbon()
                for (i in 0..7) {
                    val m = i / 7f
                    c.point(wing[i], wingY[i], wingZ[i], width * (1f - m).pow(0.6f) + 0.005f,
                        f.color(0.45f + 0.1f * feather + 0.35f * m), 0.95f * glow * (1f - m * m))
                }
                c.endRibbon()
                if (feather == 0) {
                    c.beginBolt(eye + 5)
                    for (i in 0..7) {
                        val m = i / 7f
                        c.point(wing[i], wingY[i], wingZ[i], 0.04f * bk, mix(f.color(0.3f), WHITE, 0.3f), 0.9f * glow * (1f - m))
                    }
                    c.endRibbon()
                }
            }
            if (strike > 0.01f) c.sprite(ex, ey, ez, 0.4f * bk, mix(f.color(0.3f), WHITE, 0.5f), 0.8f * strike * fa, sparkle = true, spin = t)
        }
    }

    /**
     * Charged fists: a plasma globe in each hand with tendrils of lightning reaching out and down,
     * grounding as the charge peaks, and an arc leaping from hand to hand in front of the chest.
     */
    private fun fists() {
        val a = anchors ?: return
        val chestX = a[AuraAnchors.CHEST * 3]; val chestY = a[AuraAnchors.CHEST * 3 + 1]; val chestZ = a[AuraAnchors.CHEST * 3 + 2]
        val rx = a[AuraAnchors.HAND_R * 3]; val ry = a[AuraAnchors.HAND_R * 3 + 1]; val rz = a[AuraAnchors.HAND_R * 3 + 2]
        val lx = a[AuraAnchors.HAND_L * 3]; val ly = a[AuraAnchors.HAND_L * 3 + 1]; val lz = a[AuraAnchors.HAND_L * 3 + 2]
        val handDist = sqrt((rx - lx) * (rx - lx) + (ry - ly) * (ry - ly) + (rz - lz) * (rz - lz))
        val arcSlot = floor(tc / 0.07f).toInt()
        val arcOn = charge > 0.5f && handDist > 0.3f && (hideFlash || hash(arcSlot * 3 + 11) < 0.25f + 0.5f * charge)
        val grounding = (charge > 0.6f || (thorAfter in 0f..1f)) && !f.groundMissing
        for (hand in 0 until 2) {
            var hx = if (hand == 0) rx else lx
            var hy = if (hand == 0) ry else ly
            var hz = if (hand == 0) rz else lz
            // A little towards the camera, so the globe sits in front of the hand.
            val cx = f.camX - hx; val cyy = f.camY - hy; val cz = f.camZ - hz
            val cd = sqrt(cx * cx + cyy * cyy + cz * cz)
            if (cd > 0.01f) {
                val pushBy = min(0.2f, 0.4f * cd) / cd
                hx += cx * pushBy; hy += cyy * pushBy; hz += cz * pushBy
            }
            var ox = hx - chestX; var oz = hz - chestZ
            val ol = hypot(ox, oz)
            if (ol > 1e-3f) { ox /= ol; oz /= ol } else { ox = cos(f.bodyYaw) * (if (hand == 0) -1f else 1f); oz = sin(f.bodyYaw) * (if (hand == 0) -1f else 1f) }
            c.sprite(hx, hy, hz, (0.45f + 0.3f * charge) * bk, f.color(0.5f), 0.85f + (if (arcOn) 0.2f else 0f),
                spin = (if (hand == 0) 0.7f else -0.7f) * t, mode = MODE_PLASMA)
            c.sprite(hx, hy, hz, 0.6f * bk * (0.7f + 0.5f * charge), f.color(0.45f), 0.3f + 0.2f * charge)
            c.sprite(hx, hy, hz, 0.07f * bk, WHITE, 1f)
            val tendrils = 2 + round(3f * charge).toInt()
            for (j in 0 until tendrils) {
                val epoch = if (hideFlash) 0 else floor(tc / 0.05f + 0.27f * j).toInt()
                val ks = 6007 * hand + 131 * j + 7919 * epoch
                var dx = 0.6f * ox + (hash(ks) - 0.5f) * 0.5f
                var dy = -0.6f + (hash(ks + 1) - 0.5f) * 0.5f
                var dz = 0.6f * oz + (hash(ks + 2) - 0.5f) * 0.5f
                val dl = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-3f)
                val len = (0.2f + 0.45f * charge) * bk * (0.6f + 0.4f * hash(ks + 3))
                dx = dx / dl * len; dy = dy / dl * len; dz = dz / dl * len
                val pn = crack(11, ks, hx, hy, hz, hx + dx, hy + dy, hz + dz, 3, 0.3f)
                drawBolt(11, pn, 1f, 0.055f * bk, 0f, mix(f.color(0.4f), WHITE, 0.3f), 0.9f, 0f, ks, fadeIn = false)
                c.sprite(hx + dx, hy + dy, hz + dz, 0.08f * bk, mix(f.color(0.3f), WHITE, 0.4f), 0.8f, sparkle = true, spin = hash(ks + 4) * TAU)
            }
            if (grounding) {
                val epoch = if (hideFlash) 0 else floor(tc / 0.1f).toInt()
                val ks = 9001 * hand + 7919 * epoch
                val gx = hx + 0.3f * ox
                val gz = hz + 0.3f * oz
                val pn = crack(11, ks, hx, hy, hz, gx, groundY, gz, 3, 0.25f)
                drawBolt(11, pn, 1f, 0.06f * bk, 0.2f * bk, mix(f.color(0.4f), WHITE, 0.3f), 0.9f, 0.2f * fa, ks, fadeIn = false)
                c.sprite(gx, groundY + 0.03f, gz, 0.25f * bk, mix(f.color(0.3f), WHITE, 0.4f), 0.8f, sparkle = true, spin = hash(ks + 1) * TAU)
            }
        }
        if (arcOn) {
            val life = if (hideFlash) 0f else (tc / 0.07f) - arcSlot
            val fwdX = -sin(f.bodyYaw); val fwdZ = cos(f.bodyYaw)
            val mx = chestX + fwdX * 0.4f; val my = chestY - 0.15f; val mz = chestZ + fwdZ * 0.4f
            val slotSeed = if (hideFlash) 0 else arcSlot
            c.beginBolt(slotSeed)
            for (i in 0..16) {
                val m = i / 16f
                val u = 1f - m
                val bx = u * u * lx + 2f * u * m * mx + m * m * rx
                val by = u * u * ly + 2f * u * m * my + m * m * ry
                val bz = u * u * lz + 2f * u * m * mz + m * m * rz
                val ks = slotSeed * 37 + i
                val jit = if (i == 0 || i == 16) 0f else 0.04f
                c.point(bx + (hash(ks) - 0.5f) * 2f * jit, by + (hash(ks + 1) - 0.5f) * 2f * jit, bz + (hash(ks + 2) - 0.5f) * 2f * jit,
                    0.11f * bk, mix(f.color(0.4f), WHITE, 0.3f), lerp(1f, 0.4f, life))
            }
            c.endRibbon()
        }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val k = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    private fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k

    private fun fract(x: Float): Float = x - floor(x)

    /** [a] wrapped into -pi..pi. */
    private fun wrap(a: Float): Float {
        var x = a % TAU
        if (x > PI) x -= TAU
        if (x < -PI) x += TAU
        return x
    }
}
