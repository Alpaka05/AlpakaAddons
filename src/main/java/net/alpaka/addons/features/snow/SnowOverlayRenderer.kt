package net.alpaka.addons.features.snow

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Inventory Particles: snowflakes, fireflies, ash, cherry petals, falling leaves, enchanting glyphs
 * or rain drawn over inventory and menu screens.
 *
 * Each kind moves its own way: snow drifts down at a slant, fireflies wander on curving paths and
 * pulse, ash sways down slowly, cherry petals tumble along on a breeze, leaves zigzag down tilting
 * with each swing, glyphs float up and fade, rain streaks down fast. Petals, leaves and glyphs use
 * vanilla's own particle textures.
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
    const val TYPE_CHERRY_PETALS = 3
    const val TYPE_LEAVES = 4
    const val TYPE_GLYPHS = 5
    const val TYPE_RAIN = 6

    /** Names for the particle type select, indexed by the TYPE_ constants. */
    @JvmField
    val TYPE_NAMES = arrayOf("Snowflakes", "Fireflies", "Ash", "Cherry Petals", "Falling Leaves", "Enchanting Glyphs", "Rain")

    // Vanilla's particle textures. Each is a set of shapes picked at random per particle, not an
    // animation.
    private val CHERRY_SPRITES = Array(12) { Identifier.withDefaultNamespace("textures/particle/cherry_$it.png") }
    private const val CHERRY_TEXTURE_SIZE = 3
    private val LEAF_SPRITES = Array(12) { Identifier.withDefaultNamespace("textures/particle/leaf_$it.png") }
    private const val LEAF_TEXTURE_SIZE = 5
    private val GLYPH_SPRITES = Array(26) { Identifier.withDefaultNamespace("textures/particle/sga_${'a' + it}.png") }
    private const val GLYPH_TEXTURE_SIZE = 8

    /** Leaf textures are gray and tinted at draw time, like vanilla tints them with the foliage color. */
    private val LEAF_TINTS = intArrayOf(0xFFB04A, 0xFF7A3A, 0xF0503A, 0xFFD24A, 0xC8884A)
    private val GLYPH_TINTS = intArrayOf(0xFFFFFF, 0xE4DCFF, 0xCBB8FF)

    /** Particles at amount multiplier 1. */
    private const val BASE_PARTICLE_COUNT = 105

    const val MIN_AMOUNT = 0.1f
    const val MAX_AMOUNT = 5.0f

    private val MAX_PARTICLE_COUNT = Math.round(BASE_PARTICLE_COUNT * MAX_AMOUNT)

    /**
     * Share of [BASE_PARTICLE_COUNT] each type shows at amount 1. Fireflies, leaves and glyphs are
     * bigger or brighter than a flake, so the full snow count of them crowds the screen.
     */
    private val TYPE_DENSITY = floatArrayOf(1.0f, 0.3f, 0.8f, 0.6f, 0.35f, 0.3f, 1.2f)

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

    /**
     * Velocity, except fireflies (heading in radians in X, pace in Y) and glyphs (share of their
     * life used per second in X, rise speed in Y).
     */
    private val speedX = FloatArray(MAX_PARTICLE_COUNT)
    private val speedY = FloatArray(MAX_PARTICLE_COUNT)
    private val size = IntArray(MAX_PARTICLE_COUNT)

    /** Offset into the sway, turn and pulse waves, so particles don't move in step. */
    private val phase = FloatArray(MAX_PARTICLE_COUNT)

    /** Frequency of a particle's sway, turn or pulse, in radians per second. */
    private val wobble = FloatArray(MAX_PARTICLE_COUNT)

    /** Glyphs only: how far through its life the glyph is, 0 to 1. */
    private val life = FloatArray(MAX_PARTICLE_COUNT)

    /** Textured types: which of the type's textures this particle uses. */
    private val sprite = IntArray(MAX_PARTICLE_COUNT)

    /** Petals and leaves: rotation in radians, and how fast it turns in radians per second. */
    private val angle = FloatArray(MAX_PARTICLE_COUNT)
    private val spin = FloatArray(MAX_PARTICLE_COUNT)

    /** Packed ARGB; the alpha is the particle's full opacity, which fireflies and glyphs scale down. */
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
        // A new type starts from a fresh field: snow positions make no sense as rising glyphs.
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
            TYPE_CHERRY_PETALS -> renderFalling(graphics, count, width, height, step, dt * speed,
                TYPE_CHERRY_PETALS, CHERRY_SPRITES, CHERRY_TEXTURE_SIZE, sway = 4.0f, tilt = 0.35f)
            TYPE_LEAVES -> renderFalling(graphics, count, width, height, step, dt * speed,
                TYPE_LEAVES, LEAF_SPRITES, LEAF_TEXTURE_SIZE, sway = 12.0f, tilt = 0.8f)
            TYPE_GLYPHS -> renderGlyphs(graphics, count, width, height, step, dt * speed)
            TYPE_RAIN -> renderRain(graphics, count, width, height, step)
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
     * Petals and leaves: fall while swaying side to side and tilting with each swing; petals also
     * spin on their own and ride a breeze to the right, leaves zigzag straight down.
     */
    private fun renderFalling(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float,
                              scaledDt: Float, type: Int, sprites: Array<Identifier>, textureSize: Int,
                              sway: Float, tilt: Float) {
        val yLimit = height + RESPAWN_MARGIN
        val margin = RESPAWN_MARGIN.toFloat()
        for (i in 0 until count) {
            var x = posX[i] + speedX[i] * step
            var y = posY[i] + speedY[i] * step

            if (y > yLimit) {
                spawn(type, i, width, height, initial = false)
                x = posX[i]
                y = posY[i]
            } else {
                if (x < -margin) x += width + 2 * margin else if (x > width + margin) x -= width + 2 * margin
                posX[i] = x
                posY[i] = y
            }

            angle[i] += spin[i] * scaledDt
            val wave = elapsed * wobble[i] + phase[i]
            drawSprite(graphics, sprites[sprite[i]], textureSize, x + sin(wave) * sway, y, size[i],
                angle[i] + cos(wave) * tilt, color[i], width, height)
        }
    }

    /** Float slowly upward with a slight sway, fading in and back out over a few seconds. */
    private fun renderGlyphs(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float,
                             scaledDt: Float) {
        for (i in 0 until count) {
            val progress = life[i] + speedX[i] * scaledDt
            if (progress >= 1.0f) {
                spawn(TYPE_GLYPHS, i, width, height, initial = false)
            } else {
                life[i] = progress
                posY[i] -= speedY[i] * step
            }

            val alpha = (color[i] ushr 24) * sin(life[i] * Math.PI.toFloat())
            val x = posX[i] + sin(elapsed * wobble[i] + phase[i]) * 3.0f
            drawSprite(graphics, GLYPH_SPRITES[sprite[i]], GLYPH_TEXTURE_SIZE, x, posY[i], size[i], 0.0f,
                withAlpha(color[i] and 0xFFFFFF, alpha), width, height)
        }
    }

    /** Thin streaks falling fast at a slight slant. */
    private fun renderRain(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float) {
        for (i in 0 until count) {
            var x = posX[i] + speedX[i] * step
            var y = posY[i] + speedY[i] * step

            if (y > height || x > width + RESPAWN_MARGIN) {
                spawn(TYPE_RAIN, i, width, height, initial = false)
                x = posX[i]
                y = posY[i]
            } else {
                posX[i] = x
                posY[i] = y
            }

            drawRect(graphics, Math.round(x), Math.round(y), 1, size[i], color[i], width, height)
        }
    }

    /** One of vanilla's particle textures, scaled to [extent] and turned by [rotation] around its middle. */
    private fun drawSprite(graphics: GuiGraphicsExtractor, texture: Identifier, textureSize: Int, x: Float, y: Float,
                           extent: Int, rotation: Float, argb: Int, width: Int, height: Int) {
        val drawX = Math.round(x)
        val drawY = Math.round(y)
        // Turned, the corners reach past the square; one extent of slack covers any angle.
        if (drawX + 2 * extent <= 0 || drawY + 2 * extent <= 0 || drawX - extent >= width || drawY - extent >= height) return
        if ((argb ushr 24) == 0) return

        if (rotation == 0.0f) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, drawX, drawY, 0.0f, 0.0f,
                extent, extent, textureSize, textureSize, textureSize, textureSize, argb)
            return
        }
        val half = extent / 2
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate((drawX + half).toFloat(), (drawY + half).toFloat())
        pose.rotate(rotation)
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -half, -half, 0.0f, 0.0f,
            extent, extent, textureSize, textureSize, textureSize, textureSize, argb)
        pose.popMatrix()
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
            TYPE_CHERRY_PETALS -> spawnPetal(index, w, h, initial)
            TYPE_LEAVES -> spawnLeaf(index, w, h, initial)
            TYPE_GLYPHS -> spawnGlyph(index, w, h, initial)
            TYPE_RAIN -> spawnRain(index, w, h, initial)
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

    private fun spawnPetal(index: Int, w: Float, h: Float, initial: Boolean) {
        // Spawned partly left of the screen, since the breeze carries them right.
        posX[index] = -w * 0.2f + random.nextFloat() * w * 1.2f
        posY[index] = if (initial) random.nextFloat() * h else RESPAWN_Y
        speedX[index] = 0.3f + random.nextFloat() * 0.4f
        speedY[index] = 0.3f + random.nextFloat() * 0.3f
        sprite[index] = random.nextInt(CHERRY_SPRITES.size)
        // Whole multiples of the texture only: anything in between smears the pixel art.
        size[index] = CHERRY_TEXTURE_SIZE * (1 + random.nextInt(2))
        angle[index] = random.nextFloat() * 6.2832f
        spin[index] = (random.nextFloat() - 0.5f) * 4.0f
        phase[index] = random.nextFloat() * 6.2832f
        wobble[index] = 1.0f + random.nextFloat() * 1.0f
        color[index] = ((200 + random.nextInt(56)) shl 24) or 0xFFFFFF
    }

    private fun spawnLeaf(index: Int, w: Float, h: Float, initial: Boolean) {
        posX[index] = random.nextFloat() * w
        posY[index] = if (initial) random.nextFloat() * h else RESPAWN_Y
        speedX[index] = (random.nextFloat() - 0.5f) * 0.2f
        speedY[index] = 0.45f + random.nextFloat() * 0.35f
        sprite[index] = random.nextInt(LEAF_SPRITES.size)
        size[index] = LEAF_TEXTURE_SIZE * (1 + random.nextInt(2))
        angle[index] = random.nextFloat() * 6.2832f
        spin[index] = 0.0f
        phase[index] = random.nextFloat() * 6.2832f
        wobble[index] = 1.2f + random.nextFloat() * 0.8f
        color[index] = ((220 + random.nextInt(36)) shl 24) or LEAF_TINTS[random.nextInt(LEAF_TINTS.size)]
    }

    private fun spawnGlyph(index: Int, w: Float, h: Float, initial: Boolean) {
        posX[index] = random.nextFloat() * w
        posY[index] = random.nextFloat() * h
        life[index] = if (initial) random.nextFloat() else 0.0f
        // Lives 2.5 to 5 seconds at speed 1.
        speedX[index] = 1.0f / (2.5f + random.nextFloat() * 2.5f)
        speedY[index] = 0.1f + random.nextFloat() * 0.2f
        sprite[index] = random.nextInt(GLYPH_SPRITES.size)
        size[index] = GLYPH_TEXTURE_SIZE
        phase[index] = random.nextFloat() * 6.2832f
        wobble[index] = 0.8f + random.nextFloat() * 0.8f
        color[index] = ((200 + random.nextInt(56)) shl 24) or GLYPH_TINTS[random.nextInt(GLYPH_TINTS.size)]
    }

    private fun spawnRain(index: Int, w: Float, h: Float, initial: Boolean) {
        size[index] = 4 + random.nextInt(5)
        posX[index] = -w * 0.15f + random.nextFloat() * w * 1.15f
        posY[index] = if (initial) random.nextFloat() * h else -size[index] - random.nextFloat() * 20.0f
        speedY[index] = 6.0f + random.nextFloat() * 2.0f
        speedX[index] = 0.8f
        color[index] = ((70 + random.nextInt(80)) shl 24) or 0xA8C8E8
    }
}
