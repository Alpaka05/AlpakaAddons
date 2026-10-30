package net.alpaka.addons.features.garden

import java.util.Locale
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudDefaults
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the Farming HUD. */
object FarmingHudElement : HudElement {

    private const val DEFAULT_SCALE = 1.0f
    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1
    private const val TEXT_MARGIN = 2

    override val id: String = "farming_hud"
    override val name: String = "Farming HUD"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.farmingHudEnabled

    override val settingsOptionId: String = "farming_hud_enabled"

    override var anchorX: Int
        get() = AlpakaConfig.instance.farmingHudX
        set(value) { AlpakaConfig.instance.farmingHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.farmingHudY
        set(value) { AlpakaConfig.instance.farmingHudY = value }

    /** Sample figures away from the Garden, where the live ones would all be empty. */
    private fun usePreview(): Boolean = !SkyblockUtils.isOnGarden()

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val preview = usePreview()
        val width = FarmingHudRenderer.width(preview, cfg.farmingHudScale)
        val height = FarmingHudRenderer.height(preview, cfg.farmingHudScale)
        val margin = HudPanel.margin(cfg.blurOn(cfg.farmingHudBlur), cfg.farmingHudScale, TEXT_MARGIN)
        return HudBounds(cfg.farmingHudX - margin, cfg.farmingHudY - margin,
            cfg.farmingHudX + width + margin, cfg.farmingHudY + height + margin)
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.farmingHudScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        cfg.farmingHudScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.farmingHudX = HudDefaults.FARMING_X
        cfg.farmingHudY = HudDefaults.FARMING_Y
        cfg.farmingHudScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        FarmingHudRenderer.renderHud(graphics, x, y, AlpakaConfig.instance.farmingHudScale, usePreview())
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.farmingHudScale

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", AlpakaConfig.instance.farmingHudScale)
}
