package net.alpaka.addons.features.slayer

import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
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

    /**
     * [LABEL] as drawn, kept between frames. The word never changes, so only the font can make it
     * stale, and [labelStyle] is the font it was built in. Rebuilding it cost a wrapper and a bidi
     * pass every frame.
     */
    private var labelStyle: Style? = null
    private var labelText: Component? = null

    private fun label(smooth: Boolean): Component {
        val style = if (smooth) GuiFont.style() else Style.EMPTY
        val cached = labelText
        if (cached != null && style == labelStyle) return cached
        val built = HudPanel.text(Component.literal(LABEL), smooth)
        labelStyle = style
        labelText = built
        return built
    }

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.slayerTimerEnabled || !cfg.slayerTimerHudEnabled) return

        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return

        if (SlayerTimer.displayMs() == null) return

        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight
        // The clamp is measured once through visiblePlacement, the editor's own call, instead of
        // re-measuring the line for each axis.
        val placement = SlayerTimerHudElement.visiblePlacement(screenWidth, screenHeight)
        renderHud(
            graphics,
            placement.anchorX,
            placement.anchorY,
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
        val cfg = AlpakaConfig.instance
        val smooth = cfg.smoothOn(cfg.slayerTimerHudSmoothFont)
        val label = label(smooth)
        val time = HudPanel.text(Component.literal(SlayerTimer.format(elapsed)), smooth)
        val timeX = font.width(label) + LABEL_GAP
        val blurred = cfg.blurOn(cfg.slayerTimerHudBlur)
        if (blurred) HudPanel.aroundText(graphics, timeX + font.width(time), GLYPH_HEIGHT, scale)
        val shadow = HudPanel.TEXT_SHADOW
        graphics.text(font, label, 0, 0, COLOR_LABEL, shadow)
        graphics.text(font, time, timeX, 0, color, shadow)
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
        val smooth = AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.slayerTimerHudSmoothFont)
        return font.width(label(smooth)) + LABEL_GAP +
            font.width(HudPanel.text(Component.literal(SlayerTimer.format(elapsed)), smooth))
    }

    fun height(scale: Float): Int = ceil(GLYPH_HEIGHT * scale).toDouble().toInt()
}
