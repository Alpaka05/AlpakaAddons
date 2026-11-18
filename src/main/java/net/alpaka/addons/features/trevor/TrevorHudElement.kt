package net.alpaka.addons.features.trevor

import java.util.Locale
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudDefaults
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the Trevor Helper HUD. */
object TrevorHudElement : HudElement {

    private const val DEFAULT_SCALE = 1.0f
    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1
    private const val TEXT_MARGIN = 2

    override val id: String = "trevor_hud"
    override val name: String = "Trevor Helper"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.trevorHelperEnabled

    override val settingsOptionId: String = "trevor_helper"

    override var anchorX: Int
        get() = AlpakaConfig.instance.trevorHudX
        set(value) { AlpakaConfig.instance.trevorHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.trevorHudY
        set(value) { AlpakaConfig.instance.trevorHudY = value }

    /** Sample figures away from the Farming Islands, where the live ones mean little. */
    private fun usePreview(): Boolean = !TrevorTracker.huntActive && !TrevorTracker.onFarmingIslands()

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val preview = usePreview()
        val width = TrevorHudRenderer.width(preview, cfg.trevorHudScale)
        val height = TrevorHudRenderer.height(preview, cfg.trevorHudScale)
        val margin = HudPanel.margin(cfg.blurOn(cfg.trevorHudBlur), cfg.trevorHudScale, TEXT_MARGIN)
        return HudBounds(cfg.trevorHudX - margin, cfg.trevorHudY - margin,
            cfg.trevorHudX + width + margin, cfg.trevorHudY + height + margin)
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.trevorHudScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        cfg.trevorHudScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.trevorHudX = HudDefaults.TREVOR_X
        cfg.trevorHudY = HudDefaults.TREVOR_Y
        cfg.trevorHudScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        TrevorHudRenderer.renderHud(graphics, x, y, AlpakaConfig.instance.trevorHudScale, usePreview())
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.trevorHudScale

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", AlpakaConfig.instance.trevorHudScale)
}
