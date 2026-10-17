package net.alpaka.addons.features.mainmenu

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.alpaka.addons.features.wheel.WheelMesh
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.PI
import kotlin.math.sin

/**
 * The main menu's scene: the picture the avatar stands in, and the light and depth around it.
 *
 * - The background is a screenshot of a place, cover-fitted to the screen and a little larger than
 *   it. It drifts slowly from side to side like a lobby's idle camera, and shifts against the mouse;
 *   the avatar in front shifts further, and that difference is the depth. It was blurred a little
 *   when it was made (tools/PrepMenuBackground.java), as a camera focused on the avatar would see
 *   it. A resource pack can replace it; without it the menu falls back to the panorama.
 * - Shade: the whole picture a little darker, darker again towards the top, the bottom and the
 *   card's side, so the card reads and the avatar stands out.
 * - The avatar's footing: a soft shadow under its feet and a pool of light around them, so it stands
 *   on the ground rather than in front of a picture of it.
 * - Fireflies drift up through it, each at a depth of its own, the nearer ones shifting further
 *   with the mouse.
 *
 * All the shapes are colour-only geometry through [WheelMesh], smooth at any GUI scale.
 */
internal object MenuAmbience {

    private val START_MS = System.currentTimeMillis()

    /**
     * Seconds since the menu's code was loaded: the clock every animation on the menu runs on.
     *
     * Not the wall clock's seconds: as a float, 1.8 billion seconds since 1970 only moves in steps of
     * two minutes, and every animation reading it stood still.
     */
    fun seconds(): Float = (System.currentTimeMillis() - START_MS) / 1000f

    val BACKGROUND: Identifier = Identifier.fromNamespaceAndPath("alpaka", "textures/gui/menu_background.png")

    /** The picture's own size; the screen's shape decides which of its edges are cut off. */
    private const val BACKGROUND_ASPECT = 1920f / 1080f

    /**
     * How much larger than the screen the picture is drawn, so neither the drift nor the parallax
     * ever shows an edge: on the narrowest common screen (427 GUI pixels wide) that leaves 25 pixels
     * either side, for 9 of drift and 10 of parallax.
     */
    private const val OVERSCAN = 1.12f
    private const val DRIFT_SHARE = 0.35f
    private const val DRIFT_SECONDS = 38f

    /** Whether the picture is there to draw: shipped with the mod, or from a resource pack. */
    fun hasBackground(): Boolean =
        Minecraft.getInstance().resourceManager.getResource(BACKGROUND).isPresent

    /** Where the picture is drawn this frame, in GUI pixels: its top-left corner and its size. */
    class Placement(val x: Float, val y: Float, val width: Float, val height: Float)

    /**
     * The picture, cover-fitted with room to move, drifting slowly and shifted against the mouse by
     * up to [parallax] pixels; returns where it went, so what stands in it can move with it. Drawn
     * with linear filtering: it is shown smaller or larger than its own size on nearly every
     * screen, and the nearest texel would make its fine detail shimmer.
     */
    fun background(graphics: GuiGraphicsExtractor, width: Int, height: Int, t: Float,
                   mouseNx: Float, mouseNy: Float, parallax: Float): Placement {
        val texture = Minecraft.getInstance().textureManager.getTexture(BACKGROUND)
        var drawWidth = width * OVERSCAN
        var drawHeight = drawWidth / BACKGROUND_ASPECT
        if (drawHeight < height * OVERSCAN) {
            drawHeight = height * OVERSCAN
            drawWidth = drawHeight * BACKGROUND_ASPECT
        }
        val spareX = (drawWidth - width) / 2f
        val spareY = (drawHeight - height) / 2f
        val drift = sin(t * 2f * PI.toFloat() / DRIFT_SECONDS) * spareX * DRIFT_SHARE
        // On whole screen pixels: the avatar standing in the picture can only be drawn on whole
        // screen pixels, and if the picture slid between them the two would round apart and the
        // avatar would jitter against the ground. Both step together instead.
        val x = snapToScreen(-spareX + drift - mouseNx * parallax)
        val y = snapToScreen(-spareY - mouseNy * parallax * 0.5f)

        val roundedWidth = Math.round(drawWidth)
        val roundedHeight = Math.round(drawHeight)
        graphics.pose().pushMatrix()
        graphics.pose().translate(x, y)
        graphics.blit(texture.textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            0, 0, roundedWidth, roundedHeight, 0f, 1f, 0f, 1f)
        graphics.pose().popMatrix()
        return Placement(x, y, roundedWidth.toFloat(), roundedHeight.toFloat())
    }

    /** The GUI coordinate rounded to the nearest whole screen pixel. */
    fun snapToScreen(value: Float): Float {
        val scale = Minecraft.getInstance().window.guiScale.coerceAtLeast(1)
        return Math.round(value * scale) / scale.toFloat()
    }

    private fun mesh(): WheelMesh = WheelMesh(1.15f / Minecraft.getInstance().window.guiScale.coerceAtLeast(1))

    /**
     * Darker over all, towards the top and bottom, and along the left where the card sits, out to
     * [cardEdge].
     */
    fun shade(graphics: GuiGraphicsExtractor, width: Int, height: Int, cardEdge: Float) {
        graphics.fill(0, 0, width, height, 0x2E000000)
        graphics.fillGradient(0, 0, width, (height * 0.3f).toInt(), 0x59000000, 0x00000000)
        graphics.fillGradient(0, (height * 0.55f).toInt(), width, height, 0x00000000, 0x8C000000.toInt())
        val mesh = mesh()
        val reach = cardEdge * 1.6f
        mesh.quad(0f, 0f, 0x73000000, 0f, height.toFloat(), 0x73000000,
            reach, height.toFloat(), 0x00000000, reach, 0f, 0x00000000)
        mesh.submit(graphics)
    }

    /**
     * The avatar's footing, centred on ([centerX], [feetY]) for a model [modelHeight] tall: a pool
     * of light on the ground around it, the shadow it casts away from the lanterns on the left, and
     * a soft dark patch right under the feet, darkest where they touch the ground.
     */
    fun footing(graphics: GuiGraphicsExtractor, centerX: Float, feetY: Float, modelHeight: Float, opacity: Float) {
        if (opacity <= 0.01f) return
        val shadow = 0xFF05070C.toInt()
        val clear = shadow and 0x00FFFFFF
        graphics.pose().pushMatrix()
        graphics.pose().translate(centerX, feetY)
        graphics.pose().scale(1f, FOOTING_ASPECT)
        val mesh = mesh()
        mesh.radialGradient(0f, 0f, modelHeight * 0.75f, WheelMesh.scaleAlpha(0xFFFFF2DC.toInt(), 0.08f * opacity), 0x00FFF2DC)
        mesh.submit(graphics)
        // The cast shadow: longer, fainter, reaching out to the right and a little back.
        graphics.pose().pushMatrix()
        graphics.pose().translate(modelHeight * 0.24f, -modelHeight * 0.05f / FOOTING_ASPECT)
        graphics.pose().scale(1.6f, 0.75f)
        val cast = mesh()
        cast.radialGradient(0f, 0f, modelHeight * 0.30f, WheelMesh.scaleAlpha(shadow, 0.45f * opacity), clear)
        cast.submit(graphics)
        graphics.pose().popMatrix()
        val contact = mesh()
        contact.radialGradient(0f, 0f, modelHeight * 0.28f, WheelMesh.scaleAlpha(shadow, 0.75f * opacity), clear)
        contact.radialGradient(0f, 0f, modelHeight * 0.12f, WheelMesh.scaleAlpha(shadow, 0.7f * opacity), clear)
        contact.submit(graphics)
        graphics.pose().popMatrix()
    }

    private const val MOTES = 42

    /** Each mote: horizontal place, start height, rise speed, size, depth, sway phase, warm or white. */
    private val moteX = FloatArray(MOTES)
    private val moteY = FloatArray(MOTES)
    private val moteSpeed = FloatArray(MOTES)
    private val moteSize = FloatArray(MOTES)
    private val moteDepth = FloatArray(MOTES)
    private val motePhase = FloatArray(MOTES)
    private val moteTinted = BooleanArray(MOTES)

    init {
        // A fixed seed: the same sky of motes every time, scattered rather than in a pattern.
        val random = java.util.Random(0x41_4C_50_41L)
        for (i in 0 until MOTES) {
            moteX[i] = random.nextFloat()
            moteY[i] = random.nextFloat()
            moteDepth[i] = 0.25f + random.nextFloat() * 0.75f
            moteSpeed[i] = 0.012f + 0.035f * moteDepth[i] * (0.6f + random.nextFloat() * 0.4f)
            moteSize[i] = 0.8f + 1.6f * moteDepth[i] * (0.5f + random.nextFloat() * 0.5f)
            motePhase[i] = random.nextFloat() * (2f * PI.toFloat())
            moteTinted[i] = random.nextFloat() < 0.6f
        }
    }

    /**
     * The motes at time [t] seconds, shifted against the mouse by up to [parallax] pixels for the
     * nearest; [mouseNx], [mouseNy] are the mouse's place from the screen's centre, -1 → 1. Most
     * glow in [tint], the rest white.
     */
    fun motes(graphics: GuiGraphicsExtractor, width: Int, height: Int, t: Float,
              mouseNx: Float, mouseNy: Float, parallax: Float, tint: Int, opacity: Float) {
        if (opacity <= 0.01f) return
        val mesh = mesh()
        for (i in 0 until MOTES) {
            val depth = moteDepth[i]
            var y = (moteY[i] - t * moteSpeed[i]) % 1f
            if (y < 0f) y += 1f
            val sway = sin(t * 0.6f + motePhase[i]) * 0.012f
            val px = (moteX[i] + sway) * width - mouseNx * parallax * depth
            val py = y * height - mouseNy * parallax * 0.6f * depth

            // Fade in from the bottom and out towards the top, and twinkle a little.
            val edge = (y * 6f).coerceAtMost(1f) * ((1f - y) * 4f).coerceAtMost(1f)
            val twinkle = 0.6f + 0.4f * sin(t * 2.2f + motePhase[i] * 3f)
            val alpha = (0.22f + 0.5f * depth) * edge * twinkle * opacity
            val base = if (moteTinted[i]) tint else 0xFFFFFFFF.toInt()
            // A faint halo around each, then the bright core.
            mesh.radialGradient(px, py, moteSize[i] * 2.6f, WheelMesh.scaleAlpha(base, alpha * 0.35f), base and 0x00FFFFFF)
            mesh.disc(px, py, moteSize[i] * 0.7f, WheelMesh.scaleAlpha(base, alpha))
        }
        mesh.submit(graphics)
    }

    /**
     * How flat the footing's ellipses are, their height over their width: ground a few blocks away,
     * seen from eye height, as the background's camera sees it.
     */
    private const val FOOTING_ASPECT = 0.24f
}
