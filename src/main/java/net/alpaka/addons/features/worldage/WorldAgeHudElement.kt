package net.alpaka.addons.features.worldage

import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the world age ("Day: N") HUD. */
object WorldAgeHudElement : HudElement {

    private const val DEFAULT_X = 10
    private const val DEFAULT_Y = 10
    private const val DEFAULT_SCALE = 1.0f

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1

    /** Breathing room around the text, so the guide box does not sit on the glyphs. */
    private const val TEXT_MARGIN = 2

    override val id: String = "world_age"
    override val name: String = "World Age (Day)"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.worldAgeHudEnabled

    override var anchorX: Int
        get() = AlpakaConfig.instance.worldAgeHudX
        set(value) { AlpakaConfig.instance.worldAgeHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.worldAgeHudY
        set(value) { AlpakaConfig.instance.worldAgeHudY = value }

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val font = Minecraft.getInstance().font
        val scale = cfg.worldAgeHudScale

        val width = WorldAgeHudRenderer.getWidth(font, WorldAgeHudRenderer.getWorldDay(), scale)
        val height = WorldAgeHudRenderer.getHeight(font, scale)

        return HudBounds(
            cfg.worldAgeHudX - TEXT_MARGIN,
            cfg.worldAgeHudY - TEXT_MARGIN,
            cfg.worldAgeHudX + width + TEXT_MARGIN,
            cfg.worldAgeHudY + height + TEXT_MARGIN
        )
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.worldAgeHudScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        // Rounded to two decimals so repeated scrolling cannot accumulate float drift into the
        // saved config, and so the status line stays readable.
        cfg.worldAgeHudScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.worldAgeHudX = DEFAULT_X
        cfg.worldAgeHudY = DEFAULT_Y
        cfg.worldAgeHudScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        val cfg = AlpakaConfig.instance
        WorldAgeHudRenderer.renderHud(graphics, cfg.worldAgeHudX, cfg.worldAgeHudY, cfg.worldAgeHudScale)
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.worldAgeHudScale

    override fun scaleLabel(): String = String.format("%.2fx", AlpakaConfig.instance.worldAgeHudScale)
}
