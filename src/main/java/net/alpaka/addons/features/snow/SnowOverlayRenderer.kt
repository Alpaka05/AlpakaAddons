package net.alpaka.addons.features.snow

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.Random

/**
 * Drifting snowflakes drawn over inventory and menu screens.
 *
 * Flakes are held in parallel primitive arrays rather than as a list of objects. There are 105 of
 * them at amount 1 and every one is touched on every frame of every screen, so the object layout
 * was 105 pointer chases through the heap per frame to read four fields each; the arrays walk
 * contiguous memory instead. The arrays are sized for the highest amount, so the whole system still
 * allocates exactly six arrays, once, for the lifetime of the game; a lower amount only uses the
 * front of them.
 */
object SnowOverlayRenderer {

    /** Flakes at amount multiplier 1. */
    private const val BASE_PARTICLE_COUNT = 105

    const val MIN_AMOUNT = 0.1f
    const val MAX_AMOUNT = 5.0f

    private val MAX_PARTICLE_COUNT = Math.round(BASE_PARTICLE_COUNT * MAX_AMOUNT)

    /** Pixels per second at speed multiplier 1. */
    private const val BASE_SPEED = 40.0f

    /**
     * Ceiling on one frame's time step.
     *
     * The overlay only runs while a screen is open, so the gap since the last frame can be minutes.
     * Without this the flakes would teleport off screen the moment a menu is reopened.
     */
    private const val MAX_DELTA_SECONDS = 0.1f

    /** How far left of the screen flakes spawn, and how far right they travel before respawning. */
    private const val SPAWN_SPREAD = 0.6f

    private const val RESPAWN_MARGIN = 10
    private const val RESPAWN_Y = -10.0f

    /** Stand-ins when a caller has no size yet, so a flake still gets a sane spawn range. */
    private const val FALLBACK_WIDTH = 400.0f
    private const val FALLBACK_HEIGHT = 300.0f

    private val random = Random()

    private val posX = FloatArray(MAX_PARTICLE_COUNT)
    private val posY = FloatArray(MAX_PARTICLE_COUNT)
    private val speedX = FloatArray(MAX_PARTICLE_COUNT)
    private val speedY = FloatArray(MAX_PARTICLE_COUNT)
    private val size = IntArray(MAX_PARTICLE_COUNT)

    /** Packed ARGB. Alpha is fixed at spawn, so the color is built there instead of every frame. */
    private val color = IntArray(MAX_PARTICLE_COUNT)

    /** Flakes that have been given a position so far; raising the amount spawns the rest. */
    private var spawnedCount = 0
    private var lastScreenWidth = 0
    private var lastScreenHeight = 0
    private var lastTimeMs = System.currentTimeMillis()

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        val count = particleCount(AlpakaConfig.instance.inventorySnowAmount)
        if (count > spawnedCount) {
            for (i in spawnedCount until count) spawn(i, width, height, initial = true)
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

        // Hoisted out of the loop: this was three multiplies per axis per flake, all of them with
        // the same value, which is 630 redundant multiplies a frame.
        val step = BASE_SPEED * AlpakaConfig.instance.inventorySnowSpeed * dt
        val yLimit = height + RESPAWN_MARGIN
        val xLimit = width + width * SPAWN_SPREAD

        for (i in 0 until count) {
            var x = posX[i] + speedX[i] * step
            var y = posY[i] + speedY[i] * step

            if (y > yLimit || x > xLimit) {
                spawn(i, width, height, initial = false)
                x = posX[i]
                y = posY[i]
            } else {
                posX[i] = x
                posY[i] = y
            }

            val drawX = Math.round(x)
            val drawY = Math.round(y)
            val extent = size[i]

            // Flakes spawn well left of the screen and drift in, so a good share of them are
            // off screen at any moment. Those are invisible either way; skipping them keeps their
            // quads out of the draw queue entirely.
            if (drawX + extent > 0 && drawY + extent > 0 && drawX < width && drawY < height) {
                graphics.fill(drawX, drawY, drawX + extent, drawY + extent, color[i])
            }
        }
    }

    /** Number of flakes drawn for an amount multiplier, at least one and at most the arrays' size. */
    @JvmStatic
    fun particleCount(amount: Float): Int {
        val clamped = if (amount.isNaN()) 1.0f else amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)
        return Math.round(BASE_PARTICLE_COUNT * clamped).coerceIn(1, MAX_PARTICLE_COUNT)
    }

    /**
     * Gives one flake a fresh size, speed, opacity and position.
     *
     * [initial] scatters it anywhere down the screen, for the first fill; otherwise it re-enters
     * from just above the top edge.
     */
    private fun spawn(index: Int, width: Int, height: Int, initial: Boolean) {
        val w = if (width > 0) width.toFloat() else FALLBACK_WIDTH
        val h = if (height > 0) height.toFloat() else FALLBACK_HEIGHT
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
}
