package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import kotlin.math.ceil

/**
 * Draws the running boss time while a slayer boss is up.
 *
 * Unlike the session HUD this is deliberately not cached on a timer: the whole point is a clock that
 * visibly runs, so the text is rebuilt every frame. It stays one short line for that reason - a
 * per-frame rebuild is only cheap because there is so little of it.
 */
object SlayerTimerHudRenderer {

    private const val GLYPH_HEIGHT = 9

    private const val COLOR_LABEL = 0xFFAAAAAA.toInt()
    private const val COLOR_RUNNING = 0xFFFFFFFF.toInt()

    /** A finished time that beat the record, and one that did not. */
    private const val COLOR_BEST = 0xFF55FF55.toInt()
    private const val COLOR_DONE = 0xFFFFAA00.toInt()

    private const val LABEL = "Boss"
    private const val LABEL_GAP = 5

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.slayerTimerEnabled || !cfg.slayerTimerHudEnabled) return

        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.level == null || mc.player == null) return
        if (mc.screen != null && mc.screen !is ChatScreen) return

        if (SlayerTimer.displayMs() == null) return

        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight
        renderHud(
            graphics,
            SlayerTimerHudElement.visibleAnchorX(screenWidth, screenHeight),
            SlayerTimerHudElement.visibleAnchorY(screenWidth, screenHeight),
            cfg.slayerTimerHudScale,
            preview = false,
        )
    }

    /**
     * Draws the timer at an explicit position. Shared with the editor, which passes [preview] so a
     * sample time is drawn even with no boss up - there is otherwise nothing to position against.
     */
    fun renderHud(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, preview: Boolean) {
        val font = Minecraft.getInstance().font ?: return

        val elapsed = if (preview) 42_300L else SlayerTimer.displayMs() ?: return
        val finished = !preview && SlayerTimer.isShowingResult()

        val color = when {
            !finished -> COLOR_RUNNING
            beatTheRecord(elapsed) -> COLOR_BEST
            else -> COLOR_DONE
        }

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        graphics.text(font, Component.literal(LABEL), 0, 0, COLOR_LABEL)
        graphics.text(font, Component.literal(SlayerTimer.format(elapsed)), font.width(LABEL) + LABEL_GAP, 0, color)
        graphics.pose().popMatrix()
    }

    /**
     * Whether the finished time on screen is the record. Compared against the stored best, which the
     * kill has already written, so an equal value means this fight set it.
     */
    private fun beatTheRecord(elapsed: Long): Boolean {
        val type = SlayerTimer.currentType() ?: return false
        return SlayerTimer.personalBest(type)?.let { elapsed <= it } ?: false
    }

    /** Unscaled width of the line, for the editor's bounding box. */
    fun width(font: Font, preview: Boolean): Int {
        val elapsed = if (preview) 42_300L else SlayerTimer.displayMs() ?: 0L
        return font.width(LABEL) + LABEL_GAP + font.width(SlayerTimer.format(elapsed))
    }

    fun height(scale: Float): Int = ceil(GLYPH_HEIGHT * scale).toDouble().toInt()
}
