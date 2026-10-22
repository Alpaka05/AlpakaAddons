package net.alpaka.addons.features.cosmetics

import net.alpaka.addons.features.cosmetics.AuraCanvas.Companion.TAU
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One frame of an aura: the animation clock, the player's size, and the colors.
 *
 * @param time seconds of aura time; runs at the Speed setting, so every effect speeds up alike.
 * @param height how tall the player is drawn right now - lower while sneaking, stretched by Player
 *   Scale - so the effects wrap the body they belong to.
 * @param size the Size setting; scales how far the aura reaches out from the player.
 * @param color the color at a point 0..1 of the effect's own gradient (see [AuraFeature.colorAt]).
 */
internal class AuraFrame(
    val time: Float,
    val height: Float,
    val size: Float,
    val color: (Float) -> Int,
)

/**
 * The aura effects. Each one is a pure function of the frame: nothing is simulated or remembered
 * between frames, every position follows from the time, so the animation is exactly as smooth as the
 * frame rate and costs nothing while the aura is off. Trails are drawn by asking where something was
 * a moment ago, which for a closed-form path is just an earlier time.
 */
internal object AuraEffects {

    /**
     * Glowing spirits on tilted orbits around the chest, each trailing a tapering streak of light and
     * shedding sparks that drift upwards and fade. The orbits slowly precess, so the paths never
     * repeat exactly.
     */
    fun spiritOrbs(c: AuraCanvas, f: AuraFrame) {
        val t = f.time
        val radius = 0.78f * f.size
        val orbs = 3
        val speed = 2.1f
        val trail = 34
        val trailSpan = 2.3f
        val p = FloatArray(3)
        for (i in 0 until orbs) {
            val phase = i * TAU / orbs
            val tilt = 0.5f + 0.28f * sin(t * 0.37f + i * 1.7f)
            val node = t * 0.33f + phase * 1.3f
            val centerY = f.height * (0.5f + 0.08f * sin(t * 0.8f + phase))
            val theta = t * speed + phase
            val tone = i.toFloat() / (orbs - 1)

            // The streak, from the orb backwards along its path.
            c.beginRibbon()
            for (k in 0..trail) {
                val back = k.toFloat() / trail
                orbit(theta - back * trailSpan, tilt, node, radius, centerY, p)
                val fade = (1f - back).pow(1.6f)
                c.point(p[0], p[1], p[2], 0.21f * f.size * (1f - back).pow(0.7f),
                    f.color(tone * 0.7f + back * 0.3f), 0.85f * fade)
            }
            c.endRibbon()

            // Sparks: each is born where the orb was a moment ago, rises and dies out.
            val life = 1.1f
            for (j in 0 until 7) {
                val age = fract(t / life + j / 7f + i * 0.31f)
                orbit(theta - age * life * speed, tilt, node, radius, centerY, p)
                val seed = i * 31 + j * 7 + floor(t / life + j / 7f + i * 0.31f).toInt() * 101
                val dx = (hash(seed) - 0.5f) * 0.3f * age
                val dz = (hash(seed + 1) - 0.5f) * 0.3f * age
                val fade = (1f - age) * (1f - age)
                c.sprite(p[0] + dx, p[1] + age * 0.4f, p[2] + dz, 0.11f * f.size * (1f - age * 0.6f),
                    f.color(tone * 0.7f + 0.15f), 0.8f * fade, sparkle = j % 2 == 0, spin = t * 3f + j)
            }

            // The orb itself: a wide halo, a star that slowly turns, a white-hot core.
            orbit(theta, tilt, node, radius, centerY, p)
            val pulse = 0.85f + 0.15f * sin(t * 6f + phase)
            val tint = f.color(tone * 0.7f)
            c.sprite(p[0], p[1], p[2], 0.5f * f.size * pulse, tint, 0.7f)
            c.sprite(p[0], p[1], p[2], 0.8f * f.size, tint, 0.35f, sparkle = true, spin = t * 1.4f + phase)
            c.sprite(p[0], p[1], p[2], 0.16f * f.size, mix(tint, 0xFFFFFF, 0.6f), 1f)
        }
    }

    /** A point on a circle of [radius] tilted by [tilt] and turned by [node] about the vertical. */
    private fun orbit(theta: Float, tilt: Float, node: Float, radius: Float, centerY: Float, out: FloatArray) {
        val x = cos(theta) * radius
        val z0 = sin(theta) * radius
        val y = z0 * sin(tilt)
        val z = z0 * cos(tilt)
        val cn = cos(node)
        val sn = sin(node)
        out[0] = x * cn - z * sn
        out[1] = centerY + y
        out[2] = x * sn + z * cn
    }

    /**
     * Two strands of light wound around the body like a DNA helix, beaded with lights that pulse in a
     * wave running up the strands, faint rungs between them, and a bright surge that keeps climbing
     * from the feet to above the head.
     */
    fun twinHelix(c: AuraCanvas, f: AuraFrame) {
        val t = f.time
        val radius = 0.68f * f.size
        val top = f.height + 0.2f
        val turns = 1.75f
        val p = FloatArray(3)
        val q = FloatArray(3)

        c.ring(0.04f, radius, 0.05f * f.size, f.color(0f), 0.4f)
        c.ring(top, radius * 0.75f, 0.035f * f.size, f.color(1f), 0.3f)

        for (s in 0 until 2) {
            val offset = s * Math.PI.toFloat()
            c.beginRibbon()
            val points = 44
            for (i in 0..points) {
                val k = i.toFloat() / points
                helix(k, s, t, radius, top, turns, offset, p)
                val ends = sqrt(sin(k * Math.PI.toFloat()))
                c.point(p[0], p[1], p[2], 0.08f * f.size, f.color(k * 0.85f + s * 0.15f), 0.65f * ends)
            }
            c.endRibbon()

            // The surge climbing the strand, with a short streak below it.
            val surge = fract(t * 0.42f + s * 0.5f)
            c.beginRibbon()
            for (i in 0..10) {
                val k = surge - i * 0.012f
                if (k < 0f) break
                helix(k, s, t, radius, top, turns, offset, p)
                c.point(p[0], p[1], p[2], 0.14f * f.size * (1f - i / 10f), f.color(k), 0.8f * (1f - i / 10f))
            }
            c.endRibbon()
            helix(surge, s, t, radius, top, turns, offset, p)
            val surgeFade = sin(surge * Math.PI.toFloat())
            c.sprite(p[0], p[1], p[2], 0.45f * f.size, f.color(surge), 0.8f * surgeFade)
            c.sprite(p[0], p[1], p[2], 0.7f * f.size, f.color(surge), 0.4f * surgeFade, sparkle = true, spin = t * 2f)
        }

        val beads = 12
        for (b in 0 until beads) {
            val k = (b + 0.5f) / beads
            for (s in 0 until 2) {
                val offset = s * Math.PI.toFloat()
                helix(k, s, t, radius, top, turns, offset, p)
                val wave = 0.5f + 0.5f * sin(t * 4f - k * 10f + offset)
                c.sprite(p[0], p[1], p[2], 0.19f * f.size * (0.7f + 0.6f * wave), f.color(k * 0.85f + s * 0.15f),
                    0.45f + 0.55f * wave)
            }
            if (b % 3 == 1) {
                helix(k, 0, t, radius, top, turns, 0f, p)
                helix(k, 1, t, radius, top, turns, Math.PI.toFloat(), q)
                c.beginRibbon()
                for (i in 0..8) {
                    val m = i / 8f
                    c.point(p[0] + (q[0] - p[0]) * m, p[1] + (q[1] - p[1]) * m, p[2] + (q[2] - p[2]) * m,
                        0.03f * f.size, f.color(k), 0.3f)
                }
                c.endRibbon()
            }
        }
    }

    private fun helix(k: Float, strand: Int, t: Float, radius: Float, top: Float, turns: Float, offset: Float, out: FloatArray) {
        val angle = k * turns * TAU - t * 1.6f + offset
        val r = radius * (0.88f + 0.12f * sin(k * TAU + t + strand))
        out[0] = cos(angle) * r
        out[1] = 0.04f + k * (top - 0.04f)
        out[2] = sin(angle) * r
    }

    /**
     * A magic circle on the ground: two rings with turning runes between them, a hexagram spinning
     * the other way, lit points at its tips, rings of light rising off it up the body and motes
     * floating up from its edge. The whole circle breathes slowly.
     */
    fun arcaneSigil(c: AuraCanvas, f: AuraFrame) {
        val t = f.time
        val s = f.size
        val ground = 0.03f
        val breathe = 0.82f + 0.18f * sin(t * 1.8f)
        val outer = 1.15f * s

        c.flatArc(ground, outer, 0.08f * s, f.color(0f), 0.85f * breathe, segments = 72)
        c.flatArc(ground, 0.97f * s, 0.04f * s, f.color(0.3f), 0.65f * breathe, segments = 64)
        c.flatArc(ground, 0.42f * s, 0.05f * s, f.color(0.5f), 0.7f * breathe, segments = 40)

        // The runes: dashes of varying length running round between the two outer rings.
        val runes = 18
        val spinRunes = t * 0.4f
        for (i in 0 until runes) {
            val length = (0.25f + 0.45f * hash(i * 13 + 5)) * TAU / runes
            val start = spinRunes + i * TAU / runes
            c.flatArc(ground, 1.06f * s, 0.06f * s, f.color(0.15f), 0.75f * breathe, start, length, segments = 4)
        }

        // The hexagram, turning the other way, and a light at each of its six points.
        val spinStar = -t * 0.25f
        val r = 0.93f * s
        for (tri in 0 until 2) {
            for (e in 0 until 3) {
                val a0 = spinStar + tri * TAU / 6 + e * TAU / 3
                val a1 = a0 + TAU / 3
                c.flatLine(ground, cos(a0) * r, sin(a0) * r, cos(a1) * r, sin(a1) * r, 0.04f * s, f.color(0.4f), 0.7f * breathe)
            }
        }
        for (v in 0 until 6) {
            val a = spinStar + v * TAU / 6
            val twinkle = 0.6f + 0.4f * sin(t * 5f + v * 1.3f)
            c.sprite(cos(a) * r, ground + 0.06f, sin(a) * r, 0.26f * s, f.color(0.2f), 0.75f * twinkle,
                sparkle = true, spin = t + v)
        }

        // Rings of light lifting off the circle and narrowing as they climb past the head.
        for (i in 0 until 3) {
            val rise = fract(t * 0.33f + i / 3f)
            val fade = sin(rise * Math.PI.toFloat())
            c.ring(ground + rise * (f.height + 0.4f), outer * (0.95f - 0.4f * rise), 0.05f * s,
                f.color(rise * 0.6f), 0.6f * fade, segments = 56)
        }

        // Motes drifting up from the edge.
        for (m in 0 until 16) {
            val rise = fract(t * 0.22f + hash(m * 7 + 1))
            val a = hash(m * 7 + 2) * TAU + t * 0.3f
            val rr = outer * (0.85f + 0.15f * hash(m * 7 + 3))
            c.sprite(cos(a) * rr, rise * f.height * 1.15f, sin(a) * rr, 0.1f * s, f.color(0.1f + rise * 0.4f),
                0.8f * sin(rise * Math.PI.toFloat()), sparkle = m % 3 == 0, spin = t * 2f + m)
        }
    }

    /**
     * Fire licking up around the player: tongues of flame rise from a ring at the feet, sway, narrow
     * in towards the body and cool from white-hot through orange to a dark red as they climb, while
     * embers spiral up out of them. A glow of heat lies on the ground beneath.
     */
    fun inferno(c: AuraCanvas, f: AuraFrame) {
        val t = f.time
        val s = f.size
        val base = 0.52f * s

        c.flatArc(0.03f, base, 0.55f * s, f.color(0.35f), 0.3f, segments = 40)

        val columns = 16
        val puffs = 9
        for (col in 0 until columns) {
            val angle0 = col * TAU / columns + t * 0.12f + (hash(col * 3) - 0.5f) * 0.3f
            val rate = 0.85f + 0.45f * hash(col * 3 + 1)
            val reach = (0.45f + 0.55f * hash(col * 3 + 2)) * f.height * 0.95f
            // Each tongue flares up and dies down on its own slow beat.
            val flare = 0.75f + 0.25f * sin(t * (1.3f + hash(col) * 0.8f) + col * 2.1f)
            for (pf in 0 until puffs) {
                val age = fract(t * rate + pf.toFloat() / puffs + hash(col + 40))
                val angle = angle0 + sin(t * 2.3f + col) * 0.1f * age
                val rr = base * (1f - 0.45f * age)
                val sway = sin(t * 5f + col * 1.3f + age * 6f) * 0.06f * age * s
                val x = cos(angle) * rr + sway
                val z = sin(angle) * rr + cos(t * 4.4f + col * 0.7f + age * 5f) * 0.06f * age * s
                val y = 0.02f + age * reach * flare
                val size = 0.46f * s * (1f - age).pow(0.5f) * (0.75f + 0.25f * hash(col * 7 + pf))
                val alpha = smooth(0f, 0.08f, age) * (1f - age).pow(0.9f) * 0.9f
                c.sprite(x, y, z, size, f.color(age.pow(0.8f)), alpha)
            }
        }

        // Embers: quick sparks spiralling up out of the flames, flickering.
        for (e in 0 until 20) {
            val age = fract(t * 0.5f + hash(e * 5))
            val a = hash(e * 5 + 1) * TAU + age * 2.6f
            val rr = base * (0.8f + 0.7f * age)
            val flicker = 0.55f + 0.45f * sin(t * 22f + e * 3.7f)
            c.sprite(cos(a) * rr, age * f.height * 1.3f, sin(a) * rr, 0.1f * s, f.color(0.1f + age * 0.5f),
                0.9f * (1f - age) * flicker, sparkle = true, spin = t * 4f + e)
        }
    }

    /**
     * A small spiral galaxy around the waist: stars on two arms in a slowly wobbling, tilted disk,
     * twinkling, with a haze of dust along each arm.
     */
    fun galaxy(c: AuraCanvas, f: AuraFrame) {
        val t = f.time
        val s = f.size
        val centerY = f.height * 0.48f
        // Kept shallow: the third person camera looks down on the disk, and a steeper tilt turns it
        // edge-on whenever its raised side swings round to face the camera.
        val tilt = 0.18f + 0.07f * sin(t * 0.31f)
        val node = t * 0.18f
        val spin = t * 0.5f
        val inner = 0.42f * s
        val reach = 1.0f * s
        val p = FloatArray(3)

        for (arm in 0 until 2) {
            c.beginRibbon()
            val points = 26
            for (i in 0..points) {
                val k = i.toFloat() / points
                val rr = inner + reach * k
                disk(rr, arm * Math.PI.toFloat() + k * 3.2f + spin, 0f, tilt, node, centerY, p)
                c.point(p[0], p[1], p[2], 0.32f * s * (1f - 0.5f * k), f.color(k), 0.32f * sin(k * Math.PI.toFloat()).pow(0.6f))
            }
            c.endRibbon()
        }

        val stars = 120
        for (st in 0 until stars) {
            val u = hash(st * 4)
            val k = u.pow(0.75f)
            val rr = inner + reach * k
            val arm = (st % 2) * Math.PI.toFloat()
            val scatter = (hash(st * 4 + 1) - 0.5f) * (0.35f + 0.6f * k)
            // A small wobble of its own, so the stars do not move as one rigid plate.
            val wobble = sin(t * (0.6f + hash(st * 4 + 3)) + st) * 0.05f
            val angle = arm + k * 3.2f + scatter + spin + wobble
            val lift = (hash(st * 4 + 2) - 0.5f) * 0.16f * s * (1f - 0.6f * k)
            disk(rr, angle, lift, tilt, node, centerY, p)
            val big = hash(st * 4 + 3) > 0.82f
            val twinkle = 0.55f + 0.45f * sin(t * (4f + 5f * hash(st)) + st * 1.7f)
            val size = (if (big) 0.26f else 0.09f + 0.07f * hash(st * 9)) * s
            c.sprite(p[0], p[1], p[2], size * (0.8f + 0.2f * twinkle), f.color(k), 0.9f * twinkle,
                sparkle = big, spin = t * 0.7f + st)
        }
    }

    /** A point at [radius] and [angle] in the tilted galaxy disk, [lift] above its plane. */
    private fun disk(radius: Float, angle: Float, lift: Float, tilt: Float, node: Float, centerY: Float, out: FloatArray) {
        val x = cos(angle) * radius
        val z0 = sin(angle) * radius
        val y = z0 * sin(tilt) + lift * cos(tilt)
        val z = z0 * cos(tilt) - lift * sin(tilt)
        val cn = cos(node)
        val sn = sin(node)
        out[0] = x * cn - z * sn
        out[1] = centerY + y
        out[2] = x * sn + z * cn
    }

    /**
     * Fireflies drifting around the player on lazy, never-repeating paths, each blinking on its own
     * rhythm and leaving a short faint streak behind.
     */
    fun fireflies(c: AuraCanvas, f: AuraFrame) {
        val t = f.time
        val flies = 22
        val p = FloatArray(3)
        for (i in 0 until flies) {
            val blink = smooth(-0.3f, 0.9f, sin(t * (1.1f + hash(i * 11 + 9)) + hash(i * 11 + 10) * TAU))
            val glow = 0.12f + 0.88f * blink
            val tone = hash(i * 11 + 8)
            c.beginRibbon()
            for (k in 0..10) {
                firefly(i, t - k * 0.07f, f, p)
                val fade = 1f - k / 10f
                c.point(p[0], p[1], p[2], 0.05f * f.size * fade, f.color(tone), 0.5f * glow * fade * fade)
            }
            c.endRibbon()
            firefly(i, t, f, p)
            c.sprite(p[0], p[1], p[2], 0.34f * f.size * (0.6f + 0.4f * blink), f.color(tone), 0.8f * glow)
            c.sprite(p[0], p[1], p[2], 0.07f * f.size, mix(f.color(tone), 0xFFFFFF, 0.5f), glow)
        }
    }

    private fun firefly(i: Int, t: Float, f: AuraFrame, out: FloatArray) {
        val h = i * 11
        val angle = t * (0.25f + 0.35f * hash(h)) * (if (i % 2 == 0) 1f else -1f) + hash(h + 1) * TAU +
            0.6f * sin(t * (0.4f + 0.3f * hash(h + 2)) + hash(h + 3) * TAU)
        val radius = f.size * (0.55f + 0.55f * (0.5f + 0.5f * sin(t * (0.3f + 0.4f * hash(h + 4)) + hash(h + 5) * TAU)))
        val rise = 0.5f + 0.5f * sin(t * (0.2f + 0.35f * hash(h + 6)) + hash(h + 7) * TAU)
        out[0] = cos(angle) * radius
        out[1] = f.height * (0.08f + 0.95f * rise)
        out[2] = sin(angle) * radius
    }

    /**
     * Crackling electricity: jagged arcs leap across the body and re-strike many times a second,
     * each with a forked branch and a wide glow, while balls of plasma race around waist and ankles.
     */
    fun storm(c: AuraCanvas, f: AuraFrame) {
        val t = f.time
        val s = f.size
        val radius = 0.62f * s
        val strikes = 12f
        val strike = floor(t * strikes).toInt()
        val flash = 1f - 0.6f * fract(t * strikes)

        val xs = FloatArray(17)
        val ys = FloatArray(17)
        val zs = FloatArray(17)
        for (arc in 0 until 4) {
            val seed = strike * 17 + arc * 5
            if (hash(seed) < 0.3f) continue
            val a0 = hash(seed + 1) * TAU
            val a1 = a0 + (0.7f + 1.3f * hash(seed + 2)) * (if (hash(seed + 3) < 0.5f) -1f else 1f)
            val y0 = hash(seed + 4) * f.height
            val y1 = hash(seed + 5) * f.height
            for (k in 0 until 17) {
                val m = k / 16f
                val a = a0 + (a1 - a0) * m
                val env = sin(m * Math.PI.toFloat())
                val jitter = 0.16f * s * env
                val rr = radius * (1f + 0.25f * env)
                xs[k] = cos(a) * rr + (hash(seed * 31 + k * 3) - 0.5f) * jitter
                ys[k] = y0 + (y1 - y0) * m + (hash(seed * 31 + k * 3 + 1) - 0.5f) * jitter
                zs[k] = sin(a) * rr + (hash(seed * 31 + k * 3 + 2) - 0.5f) * jitter
            }
            val tone = 0.15f + 0.5f * hash(seed + 6)
            bolt(c, xs, ys, zs, 0, 17, 0.22f * s, f.color(tone), 0.28f * flash)
            bolt(c, xs, ys, zs, 0, 17, 0.05f * s, mix(f.color(tone), 0xFFFFFF, 0.6f), flash)

            // A fork off the middle of the arc, reaching outwards.
            val from = 5 + (hash(seed + 7) * 6).toInt()
            val bx = FloatArray(6); val by = FloatArray(6); val bz = FloatArray(6)
            bx[0] = xs[from]; by[0] = ys[from]; bz[0] = zs[from]
            val out = 1f / max(0.001f, sqrt(xs[from] * xs[from] + zs[from] * zs[from]))
            for (k in 1 until 6) {
                bx[k] = bx[k - 1] + xs[from] * out * 0.07f * s + (hash(seed * 7 + k) - 0.5f) * 0.12f * s
                by[k] = by[k - 1] + (hash(seed * 7 + k + 20) - 0.6f) * 0.14f * s
                bz[k] = bz[k - 1] + zs[from] * out * 0.07f * s + (hash(seed * 7 + k + 40) - 0.5f) * 0.12f * s
            }
            bolt(c, bx, by, bz, 0, 6, 0.03f * s, mix(f.color(tone), 0xFFFFFF, 0.4f), 0.8f * flash)
        }

        // Plasma racing round the waist and the ankles in opposite directions.
        val p = FloatArray(3)
        for (band in 0 until 2) {
            val y = if (band == 0) f.height * 0.55f else 0.2f
            val dir = if (band == 0) 1f else -1f
            for (j in 0 until 2) {
                val a = dir * t * 4.5f + j * Math.PI.toFloat() + band
                c.beginRibbon()
                for (k in 0..12) {
                    val back = k / 12f
                    val aa = a - dir * back * 1.4f
                    p[0] = cos(aa) * radius * 1.15f
                    p[2] = sin(aa) * radius * 1.15f
                    c.point(p[0], y + sin(aa * 3f) * 0.05f, p[2], 0.1f * s * (1f - back), f.color(0.6f), 0.7f * (1f - back))
                }
                c.endRibbon()
                c.sprite(cos(a) * radius * 1.15f, y + sin(a * 3f) * 0.05f, sin(a) * radius * 1.15f, 0.3f * s,
                    f.color(0.6f), 0.8f, sparkle = true, spin = t * 6f)
            }
        }

        // Static: tiny sparks that snap in and out around the body.
        for (m in 0 until 10) {
            val seed = strike * 23 + m * 3
            if (hash(seed) < 0.45f) continue
            val a = hash(seed + 1) * TAU
            val rr = radius * (0.9f + 0.5f * hash(seed + 2))
            c.sprite(cos(a) * rr, hash(seed + 3) * f.height, sin(a) * rr, 0.12f * s, mix(f.color(0.3f), 0xFFFFFF, 0.5f),
                flash, sparkle = true, spin = hash(seed + 4) * TAU)
        }
    }

    private fun bolt(c: AuraCanvas, xs: FloatArray, ys: FloatArray, zs: FloatArray, from: Int, to: Int,
                     width: Float, rgb: Int, alpha: Float) {
        c.beginRibbon()
        for (k in from until to) {
            val m = (k - from).toFloat() / (to - from - 1)
            c.point(xs[k], ys[k], zs[k], width * (0.6f + 0.4f * sin(m * Math.PI.toFloat())), rgb, alpha)
        }
        c.endRibbon()
    }

    /** A well-mixed pseudo-random number in 0..1 for an integer, the same every frame. */
    fun hash(n: Int): Float {
        var x = n * 0x27d4eb2d + 0x165667b1
        x = (x xor (x ushr 15)) * 0x2c1b3c6d
        x = (x xor (x ushr 12)) * 0x297a2d39
        x = x xor (x ushr 15)
        return (x ushr 8) / 16_777_216f
    }

    private fun fract(x: Float): Float = x - floor(x)

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val k = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    /** [a] moved towards [b] by [k], per channel. */
    fun mix(a: Int, b: Int, k: Float): Int {
        val r = ((a shr 16) and 0xFF) + ((((b shr 16) and 0xFF) - ((a shr 16) and 0xFF)) * k).toInt()
        val g = ((a shr 8) and 0xFF) + ((((b shr 8) and 0xFF) - ((a shr 8) and 0xFF)) * k).toInt()
        val bl = (a and 0xFF) + (((b and 0xFF) - (a and 0xFF)) * k).toInt()
        return (r shl 16) or (g shl 8) or bl
    }
}
