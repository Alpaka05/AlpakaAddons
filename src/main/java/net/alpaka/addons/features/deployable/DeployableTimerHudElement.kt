package net.alpaka.addons.features.deployable

import java.util.Locale
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudDefaults
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the deployable timer. */
object DeployableTimerHudElement : HudElement {

    private const val DEFAULT_SCALE = 1.0f

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1

    /** Breathing room around the icon and text, so the guide box does not sit on them. */
    private const val TEXT_MARGIN = 2

    override val id: String = "deployable_timer"
    override val name: String = "Deployable Timer"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.deployableTimerEnabled

    override val settingsOptionId: String = "deployable_timer"

    override var anchorX: Int
        get() = AlpakaConfig.instance.deployableTimerHudX
        set(value) { AlpakaConfig.instance.deployableTimerHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.deployableTimerHudY
        set(value) { AlpakaConfig.instance.deployableTimerHudY = value }

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val font = Minecraft.getInstance().font
        val scale = cfg.deployableTimerHudScale

        // Measured against the sample whenever nothing is in range, so the box does not collapse
        // in the editor, which is exactly when it is being positioned.
        val shown = DeployableTimerHudRenderer.shown()
        val width = kotlin.math.ceil(DeployableTimerHudRenderer.width(font, shown) * scale).toInt()
        val height = DeployableTimerHudRenderer.height(scale, shown)

        val margin = HudPanel.margin(cfg.blurOn(cfg.deployableTimerHudBlur), scale, TEXT_MARGIN)
        return HudBounds(
            cfg.deployableTimerHudX - margin,
            cfg.deployableTimerHudY - margin,
            cfg.deployableTimerHudX + width + margin,
            cfg.deployableTimerHudY + height + margin,
        )
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.deployableTimerHudScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        cfg.deployableTimerHudScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.deployableTimerHudX = HudDefaults.DEPLOYABLE_TIMER_X
        cfg.deployableTimerHudY = HudDefaults.DEPLOYABLE_TIMER_Y
        cfg.deployableTimerHudScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        DeployableTimerHudRenderer.renderHud(
            graphics, x, y, AlpakaConfig.instance.deployableTimerHudScale, DeployableTimerHudRenderer.shown(),
        )
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.deployableTimerHudScale

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", AlpakaConfig.instance.deployableTimerHudScale)
}
