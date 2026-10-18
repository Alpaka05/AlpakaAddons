package net.alpaka.addons.features.performance

import java.util.Locale
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudDefaults
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the Performance HUD (FPS, ping, TPS). */
object PerformanceHudElement : HudElement {

    private const val DEFAULT_SCALE = 1.0f

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1

    /** Breathing room around the text, so the guide box does not sit on the glyphs. */
    private const val TEXT_MARGIN = 2

    override val id: String = "performance"
    override val name: String = "Performance"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.performanceHudEnabled

    override val settingsOptionId: String = "performance_hud_enabled"

    override var anchorX: Int
        get() = AlpakaConfig.instance.performanceHudX
        set(value) { AlpakaConfig.instance.performanceHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.performanceHudY
        set(value) { AlpakaConfig.instance.performanceHudY = value }

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val font = Minecraft.getInstance().font
        val scale = cfg.performanceHudScale

        val width = PerformanceHudRenderer.getWidth(font, scale)
        val height = PerformanceHudRenderer.getHeight(scale)

        return HudBounds(
            cfg.performanceHudX - TEXT_MARGIN,
            cfg.performanceHudY - TEXT_MARGIN,
            cfg.performanceHudX + width + TEXT_MARGIN,
            cfg.performanceHudY + height + TEXT_MARGIN
        )
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.performanceHudScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        // Rounded to two decimals so repeated scrolling cannot accumulate float drift into the
        // saved config, and so the status line stays readable.
        cfg.performanceHudScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.performanceHudX = HudDefaults.PERFORMANCE_X
        cfg.performanceHudY = HudDefaults.PERFORMANCE_Y
        cfg.performanceHudScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        PerformanceHudRenderer.renderHud(graphics, x, y, AlpakaConfig.instance.performanceHudScale)
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.performanceHudScale

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", AlpakaConfig.instance.performanceHudScale)
}
