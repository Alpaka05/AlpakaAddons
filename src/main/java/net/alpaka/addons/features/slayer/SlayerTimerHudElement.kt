package net.alpaka.addons.features.slayer

import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the live boss timer. */
object SlayerTimerHudElement : HudElement {

    private const val DEFAULT_X = 10
    private const val DEFAULT_Y = 120
    private const val DEFAULT_SCALE = 1.0f

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1

    /** Breathing room around the text, so the guide box does not sit on the glyphs. */
    private const val TEXT_MARGIN = 2

    override val id: String = "slayer_timer"
    override val name: String = "Slayer Boss Timer"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.slayerTimerEnabled && AlpakaConfig.instance.slayerTimerHudEnabled

    override var anchorX: Int
        get() = AlpakaConfig.instance.slayerTimerHudX
        set(value) { AlpakaConfig.instance.slayerTimerHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.slayerTimerHudY
        set(value) { AlpakaConfig.instance.slayerTimerHudY = value }

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val font = Minecraft.getInstance().font
        val scale = cfg.slayerTimerHudScale

        // Measured against the sample time whenever no boss is up, so the box does not collapse to
        // nothing in the editor - which is exactly when the element is being positioned.
        val preview = SlayerTimer.displayMs() == null
        val width = kotlin.math.ceil(SlayerTimerHudRenderer.width(font, preview) * scale).toInt()
        val height = SlayerTimerHudRenderer.height(scale)

        return HudBounds(
            cfg.slayerTimerHudX - TEXT_MARGIN,
            cfg.slayerTimerHudY - TEXT_MARGIN,
            cfg.slayerTimerHudX + width + TEXT_MARGIN,
            cfg.slayerTimerHudY + height + TEXT_MARGIN,
        )
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

    override fun render(graphics: GuiGraphicsExtractor) {
        val cfg = AlpakaConfig.instance
        SlayerTimerHudRenderer.renderHud(
            graphics, cfg.slayerTimerHudX, cfg.slayerTimerHudY, cfg.slayerTimerHudScale,
            preview = SlayerTimer.displayMs() == null,
        )
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.slayerTimerHudScale

    override fun scaleLabel(): String = String.format("%.2fx", AlpakaConfig.instance.slayerTimerHudScale)
}
