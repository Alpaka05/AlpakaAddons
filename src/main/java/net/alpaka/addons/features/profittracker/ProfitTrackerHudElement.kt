package net.alpaka.addons.features.profittracker

import java.util.Locale
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudDefaults
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the Profit Tracker. */
object ProfitTrackerHudElement : HudElement {

    private const val DEFAULT_SCALE = 1.0f
    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1
    private const val TEXT_MARGIN = 2

    override val id: String = "profit_tracker"
    override val name: String = "Profit Tracker"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.profitTrackerEnabled

    override val settingsOptionId: String = "profit_tracker"

    override var anchorX: Int
        get() = AlpakaConfig.instance.profitTrackerX
        set(value) { AlpakaConfig.instance.profitTrackerX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.profitTrackerY
        set(value) { AlpakaConfig.instance.profitTrackerY = value }

    /** Sample figures where nothing was counted yet: in the editor from the title screen, or a fresh profile. */
    private fun usePreview(): Boolean = ProfitTracker.shown()?.bucket?.isEmpty() ?: true

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val preview = usePreview()
        val width = ProfitTrackerHudRenderer.width(preview, cfg.profitTrackerScale)
        val height = ProfitTrackerHudRenderer.height(preview, cfg.profitTrackerScale)
        val margin = HudPanel.margin(cfg.blurOn(cfg.profitTrackerBlur), cfg.profitTrackerScale, TEXT_MARGIN)
        return HudBounds(cfg.profitTrackerX - margin, cfg.profitTrackerY - margin,
            cfg.profitTrackerX + width + margin, cfg.profitTrackerY + height + margin)
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.profitTrackerScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        cfg.profitTrackerScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.profitTrackerX = HudDefaults.PROFIT_TRACKER_X
        cfg.profitTrackerY = HudDefaults.PROFIT_TRACKER_Y
        cfg.profitTrackerScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        ProfitTrackerHudRenderer.renderHud(graphics, x, y, AlpakaConfig.instance.profitTrackerScale, usePreview())
    }

    /**
     * A left click on an item line while the chat is open hides it, so it no longer counts toward
     * the total (`/alpakaprofit unhide` brings hidden ones back); returns whether the click was taken.
     */
    @JvmStatic
    fun handleChatClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || !ProfitTracker.isHudVisible()) return false
        val drawn = ProfitTrackerHudRenderer.lastDrawn ?: return false
        if (!drawn.contains(mouseX, mouseY)) return false
        ProfitTrackerHudRenderer.itemAt(mouseX, mouseY)?.let { ProfitTracker.toggleHidden(it) }
        return true
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.profitTrackerScale

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", AlpakaConfig.instance.profitTrackerScale)
}
