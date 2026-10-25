package net.alpaka.addons.features.slayer

import java.util.Locale
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudDefaults
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the live boss timer. */
object SlayerTimerHudElement : HudElement {

    private const val DEFAULT_X = HudDefaults.SLAYER_TIMER_X
    private const val DEFAULT_Y = HudDefaults.SLAYER_TIMER_Y
    private const val DEFAULT_SCALE = 1.0f

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1

    /** Breathing room around the text, so the guide box does not sit on the glyphs. */
    private const val TEXT_MARGIN = 2

    override val id: String = "slayer_timer"
    override val name: String = "Boss Timer"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.slayerTimerEnabled && AlpakaConfig.instance.slayerTimerHudEnabled

    override val settingsOptionId: String = "slayer_timer_hud"

    /** While the stored x is [HudDefaults.CENTERED_X], the x that centers the line on screen. */
    override var anchorX: Int
        get() = x(lineWidth())
        set(value) { AlpakaConfig.instance.slayerTimerHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.slayerTimerHudY
        set(value) { AlpakaConfig.instance.slayerTimerHudY = value }

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val scale = cfg.slayerTimerHudScale
        val width = lineWidth()
        val height = SlayerTimerHudRenderer.height(scale)
        val x = x(width)

        val margin = HudPanel.margin(cfg.blurOn(cfg.slayerTimerHudBlur), scale, TEXT_MARGIN)
        return HudBounds(
            x - margin,
            cfg.slayerTimerHudY - margin,
            x + width + margin,
            cfg.slayerTimerHudY + height + margin,
        )
    }

    /** The line's width on screen, scaled. */
    private fun lineWidth(): Int {
        // Measured against the sample time whenever no boss is up, so the box does not collapse to
        // nothing in the editor - which is exactly when the element is being positioned.
        val preview = SlayerTimer.displayMs() == null
        val font = Minecraft.getInstance().font
        return kotlin.math.ceil(SlayerTimerHudRenderer.width(font, preview) * AlpakaConfig.instance.slayerTimerHudScale).toInt()
    }

    /** The stored x, or the one that centers a line [width] wide while the default is kept. */
    private fun x(width: Int): Int {
        val stored = AlpakaConfig.instance.slayerTimerHudX
        if (stored != HudDefaults.CENTERED_X) return stored
        return (Minecraft.getInstance().window.guiScaledWidth - width) / 2
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.slayerTimerHudScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        cfg.slayerTimerHudScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.slayerTimerHudX = DEFAULT_X
        cfg.slayerTimerHudY = DEFAULT_Y
        cfg.slayerTimerHudScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        val cfg = AlpakaConfig.instance
        SlayerTimerHudRenderer.renderHud(
            graphics, x, y, cfg.slayerTimerHudScale,
            preview = SlayerTimer.displayMs() == null,
        )
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.slayerTimerHudScale

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", AlpakaConfig.instance.slayerTimerHudScale)
}
