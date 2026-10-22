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
 * pulse, ash sways down slowly, cherry petals flutter along on a breeze, leaves zigzag down tilting
 * with each swing, glyphs float up and fade, rain pours down at a slant and splashes on the bottom
 * edge. Leaves and glyphs use vanilla's own particle textures; the petals are drawn by
 * tools/GenSakuraPetals.java, since vanilla's are three pixels wide.
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

    // Each texture set is a choice of shapes picked at random per particle, not an animation.
    private val PETAL_SPRITES = Array(6) { Identifier.fromNamespaceAndPath("alpaka", "textures/particle/sakura_$it.png") }
    private const val PETAL_TEXTURE_SIZE = 16
    private val LEAF_SPRITES = Array(12) { Identifier.withDefaultNamespace("textures/particle/leaf_$it.png") }
    private const val LEAF_TEXTURE_SIZE = 5
    private val GLYPH_SPRITES = Array(26) { Identifier.withDefaultNamespace("textures/particle/sga_${'a' + it}.png") }
    private const val GLYPH_TEXTURE_SIZE = 8

    /** Leaf textures are gray and tinted at draw time, like vanilla tints them with the foliage color. */
    private val LEAF_TINTS = intArrayOf(0xFFB04A, 0xFF7A3A, 0xF0503A, 0xFFD24A, 0xC8884A)
    private val GLYPH_TINTS = intArrayOf(0xFFFFFF, 0xE4DCFF, 0xCBB8FF)

    /** A petal's underside, seen when it flips over, is a touch paler than its face. */
    private const val PETAL_BACK_TINT = 0xFFE6EF

    private const val RAIN_RGB = 0xA8C8E8

    /** Rain's sideways drift as a share of its fall, the wind the downpour leans in. */
    private const val RAIN_SLANT = 0.14f

    /** Height of the spray haze hanging over the bottom edge, where the rain lands. */
    private const val RAIN_HAZE_HEIGHT = 20

    /** Rain darkens the backdrop a little, like an overcast sky. */
    private const val RAIN_GLOOM = 0x24101824

    /** Splash droplets flung up where rain lands, kept in a ring that overwrites the oldest. */
    private const val MAX_SPLASH_DROPS = 768
    private const val SPLASH_GRAVITY = 520.0f

    /** Seconds a puddle ring takes to spread out and fade. */
    private const val RIPPLE_SECONDS = 0.4f

    /** A ring seen from the side is a flat ellipse: its height as a share of its width. */
    private const val RIPPLE_FLATNESS = 0.25f
    private const val MAX_RIPPLES = 256

    /** Particles at amount multiplier 1. */
    private const val BASE_PARTICLE_COUNT = 105

    const val MIN_AMOUNT = 0.1f
    const val MAX_AMOUNT = 5.0f

    private val MAX_PARTICLE_COUNT = Math.round(BASE_PARTICLE_COUNT * MAX_AMOUNT)

    /**
     * Share of [BASE_PARTICLE_COUNT] each type shows at amount 1. Fireflies, leaves and glyphs are
     * bigger or brighter than a flake, so the full snow count of them crowds the screen. Rain needs
     * many more thin drops than snow has flakes to read as a downpour.
     */
    private val TYPE_DENSITY = floatArrayOf(1.0f, 0.3f, 0.8f, 0.6f, 0.35f, 0.3f, 3.0f)

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

    /**
     * Glyphs: how far through its life the glyph is, 0 to 1. Rain: how near the drop is, 0 to 1.
     * Petals: how fast the petal flips over, in radians per second.
     */
    private val life = FloatArray(MAX_PARTICLE_COUNT)

    /** Textured types: which of the type's textures this particle uses. */
    private val sprite = IntArray(MAX_PARTICLE_COUNT)

    /** Petals and leaves: rotation in radians, and how fast it turns in radians per second. */
    private val angle = FloatArray(MAX_PARTICLE_COUNT)
    private val spin = FloatArray(MAX_PARTICLE_COUNT)

    /** Packed ARGB; the alpha is the particle's full opacity, which fireflies and glyphs scale down. */
    private val color = IntArray(MAX_PARTICLE_COUNT)

    private val splashX = FloatArray(MAX_SPLASH_DROPS)
    private val splashY = FloatArray(MAX_SPLASH_DROPS)
    private val splashSpeedX = FloatArray(MAX_SPLASH_DROPS)
    private val splashSpeedY = FloatArray(MAX_SPLASH_DROPS)
    private val splashGround = FloatArray(MAX_SPLASH_DROPS)

    /** Seconds a splash droplet has left; 0 marks a free slot. */
    private val splashLife = FloatArray(MAX_SPLASH_DROPS)
    private val splashAlpha = FloatArray(MAX_SPLASH_DROPS)
    private var nextSplash = 0

    private val rippleX = FloatArray(MAX_RIPPLES)
    private val rippleY = FloatArray(MAX_RIPPLES)
    private val rippleWidth = FloatArray(MAX_RIPPLES)
    private val rippleAlpha = FloatArray(MAX_RIPPLES)

    /** Seconds a ripple has left; 0 marks a free slot. */
    private val rippleLife = FloatArray(MAX_RIPPLES)
    private var nextRipple = 0

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
            splashLife.fill(0.0f)
            rippleLife.fill(0.0f)
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
            TYPE_CHERRY_PETALS -> renderPetals(graphics, count, width, height, step, dt * speed)
            TYPE_LEAVES -> renderFalling(graphics, count, width, height, step, dt * speed,
                TYPE_LEAVES, LEAF_SPRITES, LEAF_TEXTURE_SIZE, sway = 12.0f, tilt = 0.8f)
            TYPE_GLYPHS -> renderGlyphs(graphics, count, width, height, step, dt * speed)
            TYPE_RAIN -> renderRain(graphics, count, width, height, step, dt * speed)
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
     * Leaves: fall while swaying side to side and tilting with each swing.
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

    /**
     * Cherry petals: carried along on a breeze in long, lazy swings, turning slowly and fluttering,
     * flipping over and over so that each one shows its face, its edge, then its paler underside.
     */
    private fun renderPetals(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float,
                             scaledDt: Float) {
        val yLimit = height + RESPAWN_MARGIN
        val margin = RESPAWN_MARGIN.toFloat()
        val pose = graphics.pose()
        for (i in 0 until count) {
            var x = posX[i] + speedX[i] * step
            var y = posY[i] + speedY[i] * step

            if (y > yLimit) {
                spawn(TYPE_CHERRY_PETALS, i, width, height, initial = false)
                x = posX[i]
                y = posY[i]
            } else {
                if (x > width + 2 * margin) x -= width + 4 * margin
                posX[i] = x
                posY[i] = y
            }

            angle[i] += spin[i] * scaledDt
            val wave = elapsed * wobble[i] + phase[i]
            val extent = size[i]
            val drawX = x + sin(wave) * 10.0f
            if (drawX + extent < 0 || y + extent < 0 || drawX - extent > width || y - extent > height) continue

            // The flip: squeezing the petal across makes it look turned edge-on. Never quite to
            // nothing, or it would blink out.
            val flip = cos(elapsed * life[i] + phase[i] * 1.7f)
            val squeeze = if (flip < 0) -maxOf(-flip, 0.15f) else maxOf(flip, 0.15f)
            val argb = if (flip < 0) (color[i] and 0xFF000000.toInt()) or PETAL_BACK_TINT else color[i]

            val half = extent / 2.0f
            pose.pushMatrix()
            pose.translate(drawX + half, y + half)
            pose.rotate(angle[i] + cos(wave) * 0.5f)
            pose.scale(squeeze, 1.0f)
            graphics.blit(RenderPipelines.GUI_TEXTURED, PETAL_SPRITES[sprite[i]], -extent / 2, -extent / 2, 0.0f, 0.0f,
                extent, extent, PETAL_TEXTURE_SIZE, PETAL_TEXTURE_SIZE, PETAL_TEXTURE_SIZE, PETAL_TEXTURE_SIZE, argb)
            pose.popMatrix()
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

    /**
     * A downpour: streaks at a slant, nearer drops longer, brighter and faster than far ones. The
     * bottom edge is the ground: every drop lands there and splashes like into a puddle.
     */
    private fun renderRain(graphics: GuiGraphicsExtractor, count: Int, width: Int, height: Int, step: Float,
                           scaledDt: Float) {
        graphics.fill(0, 0, width, height, RAIN_GLOOM)
        // A haze of spray hanging over the ground.
        graphics.fillGradient(0, height - RAIN_HAZE_HEIGHT, width, height, RAIN_RGB, (0x22 shl 24) or RAIN_RGB)

        // The splash sits on the last rows of the screen, so its whole ring still fits above the edge.
        val ground = height - 3.0f
        for (i in 0 until count) {
            var x = posX[i] + speedX[i] * step
            var y = posY[i] + speedY[i] * step

            if (y >= ground) {
                splash(posX[i] + (ground - posY[i]) * RAIN_SLANT, ground, life[i])
                spawn(TYPE_RAIN, i, width, height, initial = false)
                x = posX[i]
                y = posY[i]
            } else {
                posX[i] = x
                posY[i] = y
            }

            drawStreak(graphics, x, y, size[i], color[i], width, height)
        }

        renderSplashes(graphics, width, height, scaledDt)
    }

    /**
     * A rain streak ending at its tip ([x], [y]), leaning against the wind. It is drawn in a few
     * stepped pieces, faint at the tail and full at the tip, like a drop's motion blur.
     */
    private fun drawStreak(graphics: GuiGraphicsExtractor, x: Float, y: Float, length: Int, argb: Int,
                           width: Int, height: Int) {
        val tipX = Math.round(x)
        val tipY = Math.round(y)
        val pieces = Math.round(length * RAIN_SLANT) + 1
        val alpha = (argb ushr 24).toFloat()
        val rgb = argb and 0xFFFFFF
        for (p in 0 until pieces) {
            val top = tipY - length + length * p / pieces
            val bottom = tipY - length + length * (p + 1) / pieces
            drawRect(graphics, tipX - (pieces - 1 - p), top, 1, bottom - top,
                withAlpha(rgb, alpha * (p + 1) / pieces), width, height)
        }
    }

    /**
     * A drop hitting a puddle: a crown of droplets thrown up and outward in a V, a rebound droplet
     * shooting straight up from the middle, and a ring spreading over the water. Nearer drops splash
     * bigger; far ones only flick up a droplet or two, since hundreds of rings a second along one edge
     * would run together into a solid band.
     */
    private fun splash(x: Float, ground: Float, nearness: Float) {
        val scale = 0.5f + nearness
        val alpha = 70.0f + 160.0f * nearness

        // The crown, in pairs, one to each side, so it opens up evenly.
        val pairs = if (nearness < 0.5f) 1 else 2 + random.nextInt(2)
        for (pair in 0 until pairs) {
            // Between 20 and 55 degrees off vertical.
            val tilt = 0.35f + random.nextFloat() * 0.6f
            val pace = (60.0f + random.nextFloat() * 45.0f) * scale
            for (side in intArrayOf(-1, 1)) {
                addSplashDrop(x, ground, side * sin(tilt) * pace, -cos(tilt) * pace,
                    0.3f + random.nextFloat() * 0.2f, alpha)
            }
        }
        if (nearness < 0.5f) return
        if (random.nextBoolean()) {
            addSplashDrop(x, ground, (random.nextFloat() - 0.5f) * 8.0f, -(85.0f + random.nextFloat() * 40.0f) * scale,
                0.45f, alpha)
        }

        // Only every other near drop leaves a ring, again so the rings stay apart.
        if (random.nextBoolean()) return
        val slot = nextRipple
        nextRipple = (nextRipple + 1) % MAX_RIPPLES
        rippleX[slot] = x
        rippleY[slot] = ground
        rippleWidth[slot] = 4.0f + 8.0f * nearness
        rippleAlpha[slot] = 60.0f + 120.0f * nearness
        rippleLife[slot] = RIPPLE_SECONDS
    }

    private fun addSplashDrop(x: Float, y: Float, speedX: Float, speedY: Float, life: Float, alpha: Float) {
        val slot = nextSplash
        nextSplash = (nextSplash + 1) % MAX_SPLASH_DROPS
        splashX[slot] = x
        splashY[slot] = y - 1
        splashGround[slot] = y
        splashSpeedX[slot] = speedX
        splashSpeedY[slot] = speedY
        splashLife[slot] = life
        splashAlpha[slot] = alpha
    }

    private fun renderSplashes(graphics: GuiGraphicsExtractor, width: Int, height: Int, scaledDt: Float) {
        for (i in 0 until MAX_RIPPLES) {
            if (rippleLife[i] <= 0.0f) continue
            rippleLife[i] -= scaledDt
            if (rippleLife[i] <= 0.0f) continue
            val left = rippleLife[i] / RIPPLE_SECONDS
            // Spreads fast at first and slows down, like a real ring on water.
            val spread = 1.0f - left * left * left
            drawRing(graphics, Math.round(rippleX[i]), Math.round(rippleY[i]), 1.0f + rippleWidth[i] * spread,
                rippleAlpha[i] * left, width, height)
        }

        for (i in 0 until MAX_SPLASH_DROPS) {
            if (splashLife[i] <= 0.0f) continue
            splashLife[i] -= scaledDt
            splashSpeedY[i] += SPLASH_GRAVITY * scaledDt
            splashX[i] += splashSpeedX[i] * scaledDt
            splashY[i] += splashSpeedY[i] * scaledDt
            // Back in the puddle the droplet is done.
            if (splashLife[i] <= 0.0f || splashY[i] > splashGround[i]) {
                splashLife[i] = 0.0f
                continue
            }
            drawRect(graphics, Math.round(splashX[i]), Math.round(splashY[i]), 1, 1,
                withAlpha(0xD8E8F8, splashAlpha[i] * (splashLife[i] * 4.0f).coerceAtMost(1.0f)),
                width, height)
        }
    }

    /**
     * The outline of a flat ellipse around ([cx], [cy]), a ring on the water seen from the side. It
     * is drawn row by row, each row only the stretch of outline it holds, so a ring costs a handful
     * of quads however wide it gets. The far half is fainter than the near half, which catches the
     * light.
     */
    private fun drawRing(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, radius: Float, alpha: Float,
                         width: Int, height: Int) {
        val rows = Math.round(radius * RIPPLE_FLATNESS).coerceAtLeast(1)
        // Half a row more than the outermost row, so the top and bottom rows are not cut to a dot.
        val reach = rows + 0.5f
        for (row in -rows..rows) {
            val outer = halfWidth(radius, row, reach)
            val argb = withAlpha(RAIN_RGB, if (row < 0) alpha * 0.6f else alpha)
            if (row == -rows || row == rows) {
                drawRect(graphics, cx - outer, cy + row, 2 * outer + 1, 1, argb, width, height)
            } else {
                val inner = halfWidth(radius, Math.abs(row) + 1, reach)
                val span = (outer - inner).coerceAtLeast(1)
                drawRect(graphics, cx - outer, cy + row, span, 1, argb, width, height)
                drawRect(graphics, cx + outer - span + 1, cy + row, span, 1, argb, width, height)
            }
        }
    }

    private fun halfWidth(radius: Float, row: Int, reach: Float): Int {
        val share = row / reach
        return Math.round(radius * Math.sqrt((1.0f - share * share).toDouble()).toFloat())
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
        // Nearer petals are bigger, quicker and more opaque, which gives the drift some depth.
        val near = random.nextFloat()
        // Spawned partly left of the screen, since the breeze carries them right.
        posX[index] = -w * 0.3f + random.nextFloat() * w * 1.3f
        posY[index] = if (initial) random.nextFloat() * h else RESPAWN_Y - random.nextFloat() * 20.0f
        speedX[index] = 0.5f + near * 0.5f + random.nextFloat() * 0.3f
        speedY[index] = 0.35f + near * 0.35f + random.nextFloat() * 0.15f
        sprite[index] = random.nextInt(PETAL_SPRITES.size)
        size[index] = 8 + Math.round(near * 4.0f)
        angle[index] = random.nextFloat() * 6.2832f
        spin[index] = (random.nextFloat() - 0.5f) * 1.6f
        phase[index] = random.nextFloat() * 6.2832f
        wobble[index] = 0.8f + random.nextFloat() * 0.8f
        life[index] = 1.5f + random.nextFloat() * 2.5f
        color[index] = ((170 + Math.round(near * 85.0f)) shl 24) or 0xFFFFFF
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
        val near = random.nextFloat()
        life[index] = near
        size[index] = 5 + Math.round(near * near * 16.0f) + random.nextInt(4)
        speedY[index] = 8.0f + near * 6.0f + random.nextFloat() * 1.5f
        speedX[index] = speedY[index] * RAIN_SLANT
        // Spawned partly left of the screen, since the wind carries the drops right on the way down.
        posX[index] = -h * RAIN_SLANT + random.nextFloat() * (w + h * RAIN_SLANT)
        posY[index] = if (initial) random.nextFloat() * h else -random.nextFloat() * 40.0f
        color[index] = ((45 + Math.round(near * 140.0f) + random.nextInt(30)) shl 24) or RAIN_RGB
    }
}
