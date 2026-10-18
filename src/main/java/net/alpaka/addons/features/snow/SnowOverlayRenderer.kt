package net.alpaka.addons.features.snow

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Inventory Particles: snowflakes, fireflies, ash or souls drawn over inventory and menu screens.
 *
 * Each kind moves its own way: snow drifts down at a slant, fireflies wander on curving paths and
 * pulse, ash sways down slowly, souls rise with vanilla's soul
 * particle animation and fade out at different heights.
 *
 * Particles are held in parallel primitive arrays rather than as a list of objects. There are 105 of
 * them at amount 1 and every one is touched on every frame of every screen, so the object layout
 * was 105 pointer chases through the heap per frame to read four fields each; the arrays walk
 * contiguous memory instead. The arrays are sized for the highest amount, so the whole system still
 * allocates its arrays once, for the lifetime of the game; a lower amount only uses the front of
 * them.
 */
object SnowOverlayRenderer {

    const val TYPE_SNOW = 0
    const val TYPE_FIREFLIES = 1
    const val TYPE_ASH = 2
    const val TYPE_SOULS = 3

    /** Names for the particle type select, indexed by the TYPE_ constants. */
    @JvmField
    val TYPE_NAMES = arrayOf("Snowflakes", "Fireflies", "Ash", "Souls")

    /** Vanilla's soul particle animation, from soul sand and soul soil, in playing order. */
    private val SOUL_FRAMES = Array(11) { Identifier.withDefaultNamespace("textures/particle/soul_$it.png") }
    private const val SOUL_TEXTURE_SIZE = 16

    /** Particles at amount multiplier 1. */
    private const val BASE_PARTICLE_COUNT = 105

    const val MIN_AMOUNT = 0.1f
    const val MAX_AMOUNT = 5.0f

    private val MAX_PARTICLE_COUNT = Math.round(BASE_PARTICLE_COUNT * MAX_AMOUNT)

    /**
     * Share of [BASE_PARTICLE_COUNT] each type shows at amount 1. Fireflies glow and souls are
     * full sprites, so the full snow count of them crowds the screen.
     */
    private val TYPE_DENSITY = floatArrayOf(1.0f, 0.3f, 0.8f, 0.45f)

    /** Pixels per second at speed multiplier 1. */
    private const val BASE_SPEED = 40.0f

    /**
     * Ceiling on one frame's time step.
     *
     * The overlay only runs while a screen is open, so the gap since the last frame can be minutes.
     * Without this the particles would teleport off screen the moment a menu is reopened.
     */
    private const val MAX_DELTA_SECONDS = 0.1f

    /** How far left of the screen flakes spawn, and how far right they travel before respawning. */
    private const val SPAWN_SPREAD = 0.6f

    private const val RESPAWN_MARGIN = 10
    private const val RESPAWN_Y = -10.0f

    /** Stand-ins when a caller has no size yet, so a particle still gets a sane spawn range. */
    private const val FALLBACK_WIDTH = 400.0f
    private const val FALLBACK_HEIGHT = 300.0f

    private val random = Random()

    private val posX = FloatArray(MAX_PARTICLE_COUNT)
    private val posY = FloatArray(MAX_PARTICLE_COUNT)

    /** Snow and ash: velocity. Fireflies: heading (radians) in X, pace in Y. Souls: rise speed in Y. */
    private val speedX = FloatArray(MAX_PARTICLE_COUNT)
    private val speedY = FloatArray(MAX_PARTICLE_COUNT)
    private val size = IntArray(MAX_PARTICLE_COUNT)

    /** Offset into the sway, turn and pulse waves, so particles don't move in step. */
    private val phase = FloatArray(MAX_PARTICLE_COUNT)

    /** Frequency of a particle's sway, turn or pulse, in radians per second. */
    private val wobble = FloatArray(MAX_PARTICLE_COUNT)

    /** Souls only: the height, as a share of the screen, at which the soul has faded out. */
    private val fadeTop = FloatArray(MAX_PARTICLE_COUNT)

    /** Packed ARGB; the alpha is the particle's full opacity, which fireflies and souls scale down. */
    private val color = IntArray(MAX_PARTICLE_COUNT)

    /** Particles that have been given a position so far; raising the amount spawns the rest. */
    private var spawnedCount = 0
    private var spawnedType = -1
    private var lastScreenWidth = 0
    private var lastScreenHeight = 0
    private var lastTimeMs = System.currentTimeMillis()

    /** Animation clock for the waves, advanced by the speed setting so it slows with the motion. */
    private var elapsed = 0.0f

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        val config = AlpakaConfig.instance
        val type = clampType(config.inventoryParticleType)
        // A new type starts from a fresh field: snow positions make no sense as rising souls.
        if (type != spawnedType) {
            spawnedCount = 0
            spawnedType = type
        }

        val count = particleCount(config.inventorySnowAmount, type)
        if (count > spawnedCount) {
            for (i in spawnedCount until count) spawn(type, i, width, height, initial = true)
            spawnedCount = count
        }

        // Scale the field with the window, so going fullscreen does not leave a bare strip.
        if (width != lastScreenWidth || height != lastScreenHeight) {
            if (lastScreenWidth > 0 && lastScreenHeight > 0) {
                val scaleX = width.toFloat() / lastScreenWidth
                val scaleY = height.toFloat() / lastScreenHeight
                for (i in 0 until spawnedCount) {
                    posX[i] *= scaleX
                    posY[i] *= scaleY
                }
            }
            lastScreenWidth = width
            lastScreenHeight = height
        }

        val now = System.currentTimeMillis()
        val dt = ((now - lastTimeMs) / 1000.0f).coerceAtMost(MAX_DELTA_SECONDS)
        lastTimeMs = now

        val speed = config.inventorySnowSpeed
        // Not wrapped: the waves have unrelated periods, so any wrap point would make them jump.
        // A float clock stays finer than a hundredth of a second for over a day of open menus.
        elapsed += dt * speed

        // Hoisted out of the loops: the same value for every particle.
        val step = BASE_SPEED * speed * dt

        when (type) {
            TYPE_FIREFLIES -> renderFireflies(graphics, count, width, height, step, dt * speed)
            TYPE_ASH -> renderAsh(graphics, count, width, height, step)
            TYPE_SOULS -> renderSouls(graphics, count, width, height, step)
            else -> renderSnow(graphics, count, width, height, step)
        }
    }

    private fun renderSnow(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float) {
        val yLimit = height + RESPAWN_MARGIN
        val xLimit = width + width * SPAWN_SPREAD

        for (i in 0 until count) {
            var x = posX[i] + speedX[i] * step
            var y = posY[i] + speedY[i] * step

            if (y > yLimit || x > xLimit) {
                spawn(TYPE_SNOW, i, width, height, initial = false)
                x = posX[i]
                y = posY[i]
            } else {
                posX[i] = x
                posY[i] = y
            }

            drawSquare(graphics, Math.round(x), Math.round(y), size[i], color[i], width, height)
        }
    }

    /** Wander on curving paths: the heading turns along a slow wave, and the glow pulses. */
    private fun renderFireflies(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int,
                                step: Float, scaledDt: Float) {
        val margin = RESPAWN_MARGIN.toFloat()
        for (i in 0 until count) {
            val heading = speedX[i] + sin(elapsed * wobble[i] + phase[i]) * 2.2f * scaledDt
            speedX[i] = heading
            var x = posX[i] + cos(heading) * speedY[i] * step
            var y = posY[i] + sin(heading) * speedY[i] * step

            // Wrap instead of respawning: a firefly that leaves one edge drifts back in at the other.
            if (x < -margin) x += width + 2 * margin else if (x > width + margin) x -= width + 2 * margin
            if (y < -margin) y += height + 2 * margin else if (y > height + margin) y -= height + 2 * margin
            posX[i] = x
            posY[i] = y

            val pulse = 0.5f + 0.5f * sin(elapsed * wobble[i] * 2.5f + phase[i] * 3.0f)
            val alpha = (color[i] ushr 24) * (0.15f + 0.85f * pulse)
            val rgb = color[i] and 0xFFFFFF
            val drawX = Math.round(x)
            val drawY = Math.round(y)
            val extent = size[i]

            // A faint halo around the core, the part that reads as glowing.
            drawSquare(graphics, drawX - 1, drawY - 1, extent + 2, withAlpha(rgb, alpha * 0.3f), width, height)
            drawSquare(graphics, drawX, drawY, extent, withAlpha(rgb, alpha), width, height)
        }
    }

    /** Fall slowly while swaying side to side, like flakes of ash in still air. */
    private fun renderAsh(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float) {
        val yLimit = height + RESPAWN_MARGIN
        val margin = RESPAWN_MARGIN.toFloat()
        for (i in 0 until count) {
            var x = posX[i] + speedX[i] * step
            var y = posY[i] + speedY[i] * step

            if (y > yLimit) {
                spawn(TYPE_ASH, i, width, height, initial = false)
                x = posX[i]
                y = posY[i]
            } else {
                if (x < -margin) x += width + 2 * margin else if (x > width + margin) x -= width + 2 * margin
                posX[i] = x
                posY[i] = y
            }

            val sway = sin(elapsed * wobble[i] + phase[i]) * 7.0f
            drawSquare(graphics, Math.round(x + sway), Math.round(y), size[i], color[i], width, height)
        }
    }

    /**
     * Rise from the bottom with a gentle sway, drawn with vanilla's soul particle frames: the brown
     * face near the bottom turns into the cyan wisp on the way up, then fades out.
     */
    private fun renderSouls(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float) {
        val bottom = (height + RESPAWN_MARGIN).toFloat()
        for (i in 0 until count) {
            var y = posY[i] - speedY[i] * step
            val top = fadeTop[i] * height

            if (y < top) {
                spawn(TYPE_SOULS, i, width, height, initial = false)
                y = posY[i]
            } else {
                posY[i] = y
            }

            val extent = size[i]
            val drawX = Math.round(posX[i] + sin(elapsed * wobble[i] + phase[i]) * 5.0f)
            val drawY = Math.round(y)
            if (drawX + extent <= 0 || drawY + extent <= 0 || drawX >= width || drawY >= height) continue

            // How far along its climb the soul is picks the animation frame, so each one plays
            // vanilla's animation once between the bottom edge and the height it vanishes at.
            val progress = ((bottom - y) / (bottom - top).coerceAtLeast(1.0f)).coerceIn(0.0f, 1.0f)
            val frame = (progress * SOUL_FRAMES.size).toInt().coerceAtMost(SOUL_FRAMES.size - 1)

            // Fade in just above the bottom edge, and out over the last fifth of the climb.
            val fadeIn = ((bottom - y) / 20.0f).coerceIn(0.0f, 1.0f)
            val fadeOut = ((1.0f - progress) / 0.2f).coerceIn(0.0f, 1.0f)
            val alpha = (color[i] ushr 24) * fadeIn * fadeOut
            if (alpha < 1.0f) continue

            graphics.blit(RenderPipelines.GUI_TEXTURED, SOUL_FRAMES[frame], drawX, drawY, 0.0f, 0.0f,
                extent, extent, SOUL_TEXTURE_SIZE, SOUL_TEXTURE_SIZE, SOUL_TEXTURE_SIZE, SOUL_TEXTURE_SIZE,
                withAlpha(0xFFFFFF, alpha))
        }
    }

    private fun drawSquare(graphics: GuiGraphicsExtractor, x: Int, y: Int, extent: Int, argb: Int, width: Int, height: Int) {
        drawRect(graphics, x, y, extent, extent, argb, width, height)
    }

    private fun drawRect(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, argb: Int, width: Int, height: Int) {
        // A good share of particles are off screen at any moment. Those are invisible either way;
        // skipping them keeps their quads out of the draw queue entirely.
        if (x + w > 0 && y + h > 0 && x < width && y < height && (argb ushr 24) != 0) {
            graphics.fill(x, y, x + w, y + h, argb)
        }
    }

    private fun withAlpha(rgb: Int, alpha: Float): Int =
        (alpha.toInt().coerceIn(0, 255) shl 24) or rgb

    /** Number of snowflakes drawn for an amount multiplier, at least one and at most the arrays' size. */
    @JvmStatic
    fun particleCount(amount: Float): Int = particleCount(amount, TYPE_SNOW)

    /** Number of particles of a type drawn for an amount multiplier. */
    @JvmStatic
    fun particleCount(amount: Float, type: Int): Int {
        val clamped = if (amount.isNaN()) 1.0f else amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)
        val density = TYPE_DENSITY[clampType(type)]
        return Math.round(BASE_PARTICLE_COUNT * clamped * density).coerceIn(1, MAX_PARTICLE_COUNT)
    }

    /** A type index from the config, with a hand-edited out-of-range value falling back to snow. */
    @JvmStatic
    fun clampType(type: Int): Int = if (type in TYPE_NAMES.indices) type else TYPE_SNOW

    /**
     * Gives one particle a fresh size, speed, color and position for its type.
     *
     * [initial] scatters it anywhere on the screen, for the first fill; otherwise it re-enters from
     * the edge its type comes in at.
     */
    private fun spawn(type: Int, index: Int, width: Int, height: Int, initial: Boolean) {
        val w = if (width > 0) width.toFloat() else FALLBACK_WIDTH
        val h = if (height > 0) height.toFloat() else FALLBACK_HEIGHT
        when (type) {
            TYPE_FIREFLIES -> spawnFirefly(index, w, h)
            TYPE_ASH -> spawnAsh(index, w, h, initial)
            TYPE_SOULS -> spawnSoul(index, w, h, initial)
            else -> spawnSnow(index, w, h, initial)
        }
    }

    private fun spawnSnow(index: Int, w: Float, h: Float, initial: Boolean) {
        val minX = -w * SPAWN_SPREAD

        speedX[index] = 0.4f + random.nextFloat() * 0.8f
        speedY[index] = 0.8f + random.nextFloat() * 1.4f
        size[index] = random.nextInt(3) + 1
        color[index] = ((100 + random.nextInt(130)) shl 24) or 0xFFFFFF

        // Y is drawn before X, matching the order the previous implementation consumed the RNG in.
        if (initial) {
            posY[index] = random.nextFloat() * h
            posX[index] = minX + random.nextFloat() * (w - minX)
        } else {
            posY[index] = RESPAWN_Y
            posX[index] = minX + random.nextFloat() * (w - minX)
        }
    }

    /** Fireflies never respawn, they wrap around the edges, so only the first fill places them. */
    private fun spawnFirefly(index: Int, w: Float, h: Float) {
        posX[index] = random.nextFloat() * w
        posY[index] = random.nextFloat() * h
        speedX[index] = random.nextFloat() * (2.0f * Math.PI.toFloat())
        speedY[index] = 0.2f + random.nextFloat() * 0.4f
        size[index] = random.nextInt(2) + 1
        phase[index] = random.nextFloat() * 6.2832f
        wobble[index] = 0.6f + random.nextFloat() * 0.9f
        // Warm yellow to yellow-green.
        val rgb = if (random.nextBoolean()) 0xF4E86A else 0xC8F25C
        color[index] = ((190 + random.nextInt(66)) shl 24) or rgb
    }

    private fun spawnAsh(index: Int, w: Float, h: Float, initial: Boolean) {
        posX[index] = random.nextFloat() * w
        posY[index] = if (initial) random.nextFloat() * h else RESPAWN_Y
        speedX[index] = (random.nextFloat() - 0.5f) * 0.3f
        speedY[index] = 0.3f + random.nextFloat() * 0.45f
        size[index] = random.nextInt(2) + 1
        phase[index] = random.nextFloat() * 6.2832f
        wobble[index] = 0.8f + random.nextFloat() * 1.2f
        // Mostly gray flakes, now and then a glowing ember.
        val rgb = if (random.nextInt(8) == 0) {
            0xFF7A2E
        } else {
            val gray = 0x4A + random.nextInt(0x50)
            (gray shl 16) or (gray shl 8) or gray
        }
        color[index] = ((140 + random.nextInt(100)) shl 24) or rgb
    }

    private fun spawnSoul(index: Int, w: Float, h: Float, initial: Boolean) {
        fadeTop[index] = random.nextFloat() * 0.45f
        posX[index] = random.nextFloat() * w
        posY[index] = if (initial) {
            fadeTop[index] * h + random.nextFloat() * (h - fadeTop[index] * h)
        } else {
            h + RESPAWN_MARGIN
        }
        speedY[index] = 0.35f + random.nextFloat() * 0.5f
        // Half or full texture size only: anything in between smears the pixel art.
        size[index] = if (random.nextInt(3) == 0) SOUL_TEXTURE_SIZE else SOUL_TEXTURE_SIZE / 2
        phase[index] = random.nextFloat() * 6.2832f
        wobble[index] = 0.7f + random.nextFloat() * 0.8f
        // White, so the texture keeps its own colors; only the alpha varies.
        color[index] = ((200 + random.nextInt(56)) shl 24) or 0xFFFFFF
    }
}
