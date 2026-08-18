package net.alpaka.addons.features.playermodel

import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Editor handle for the player model HUD. See [PlayerModelRenderer] for the rendering itself. */
object PlayerModelHudElement : HudElement {

    private const val MIN_SCALE = 10
    private const val MAX_SCALE = 200

    /** Scale steps per scroll notch, matching the feel of the original per-HUD editor. */
    private const val SCALE_PER_NOTCH = 2.0

    override val id: String = "player_model"
    override val name: String = "Player Model"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.playerModelEnabled

    override var anchorX: Int
        get() = AlpakaConfig.instance.playerModelX
        set(value) { AlpakaConfig.instance.playerModelX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.playerModelY
        set(value) { AlpakaConfig.instance.playerModelY = value }

    override fun bounds(): HudBounds {
        val cfg = AlpakaConfig.instance
        return PlayerModelRenderer.footprint(cfg.playerModelX, cfg.playerModelY, cfg.playerModelScale)
    }

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        // Truncating means a partial notch is simply ignored, as in the original editor.
        val delta = (notches * SCALE_PER_NOTCH).toInt()
        cfg.playerModelScale = (cfg.playerModelScale + delta).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.playerModelX = PlayerModelRenderer.DEFAULT_X
        cfg.playerModelY = PlayerModelRenderer.DEFAULT_Y
        cfg.playerModelScale = PlayerModelRenderer.DEFAULT_SCALE
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        // Null on the title screen, where the config screen - and so this editor - is reachable
        // without a world loaded. The box is still draggable; there is just nothing to draw in it.
        val player = Minecraft.getInstance().player ?: return
        val cfg = AlpakaConfig.instance
        PlayerModelRenderer.renderPlayerModel(graphics, cfg.playerModelX, cfg.playerModelY, cfg.playerModelScale, player)
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.playerModelScale.toFloat()

    override fun scaleLabel(): String = "${AlpakaConfig.instance.playerModelScale}x"
}
