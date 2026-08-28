package net.alpaka.addons.features.slayer

import java.util.Locale
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Editor handle for the slayer session HUD. */
object SlayerHudElement : HudElement {

    private const val DEFAULT_X = 10
    private const val DEFAULT_Y = 60
    private const val DEFAULT_SCALE = 1.0f

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 3.0f
    private const val SCALE_PER_NOTCH = 0.1

    /** Breathing room around the text, so the guide box does not sit on the glyphs. */
    private const val TEXT_MARGIN = 2

    override val id: String = "slayer_session"
    override val name: String = "Slayer Session"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.slayerHudEnabled

    override var anchorX: Int
        get() = AlpakaConfig.instance.slayerHudX
        set(value) { AlpakaConfig.instance.slayerHudX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.slayerHudY
        set(value) { AlpakaConfig.instance.slayerHudY = value }

    /**
     * Whether the editor should show stand-in figures.
     *
     * The editor is nearly always opened outside a slayer quest, where every live figure would be
     * zero or absent and the box would collapse to something far smaller than the real HUD. Sample
     * values keep it representative; during an actual quest the real numbers are shown instead.
     */
    private fun usePreview(): Boolean = SlayerQuestDetector.currentOrRecent() == null

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        val font = Minecraft.getInstance().font
        val type = SlayerQuestDetector.currentOrRecent()
        val preview = usePreview()

        val width = SlayerHudRenderer.width(font, type, preview, cfg.slayerHudScale)
        val height = SlayerHudRenderer.height(type, preview, cfg.slayerHudScale)

        return HudBounds(
            cfg.slayerHudX - TEXT_MARGIN,
            cfg.slayerHudY - TEXT_MARGIN,
            cfg.slayerHudX + width + TEXT_MARGIN,
            cfg.slayerHudY + height + TEXT_MARGIN
        )
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val raw = (cfg.slayerHudScale + (notches * SCALE_PER_NOTCH).toFloat()).coerceIn(MIN_SCALE, MAX_SCALE)
        // Rounded to two decimals so repeated scrolling cannot accumulate float drift into the
        // saved config, and so the status line stays readable.
        cfg.slayerHudScale = (raw * 100.0f).roundToInt() / 100.0f
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.slayerHudX = DEFAULT_X
        cfg.slayerHudY = DEFAULT_Y
        cfg.slayerHudScale = DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        val cfg = AlpakaConfig.instance
        SlayerHudRenderer.renderHud(
            graphics,
            cfg.slayerHudX,
            cfg.slayerHudY,
            cfg.slayerHudScale,
            SlayerQuestDetector.currentOrRecent(),
            usePreview()
        )
    }

    /**
     * Handles a click on the HUD while the chat screen is open, holding or releasing the session
     * clock. Returns whether the click was consumed.
     *
     * Only claims the click when the HUD is genuinely on screen - the feature is on and a quest is
     * running - so an invisible HUD never swallows a click meant for chat. The preview geometry the
     * editor uses is deliberately not accepted either: clicking where the HUD *would* be, when there
     * is no session to hold, would be a no-op that silently ate the click.
     */
    fun handleChatClick(mouseX: Double, mouseY: Double): Boolean {
        if (!AlpakaConfig.instance.slayerHudEnabled) return false
        if (SlayerQuestDetector.currentOrRecent() == null) return false
        if (!bounds().contains(mouseX, mouseY)) return false

        SlayerSessionTracker.toggleManualPause()
        return true
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.slayerHudScale

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", AlpakaConfig.instance.slayerHudScale)
}
