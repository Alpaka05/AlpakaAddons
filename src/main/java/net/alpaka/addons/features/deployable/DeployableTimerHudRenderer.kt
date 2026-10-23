package net.alpaka.addons.features.deployable

import java.util.EnumMap
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.market.ItemIcons
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

/**
 * Draws the strongest deployable in range: its head as an icon, and the time it has left beside it.
 * Shown only while one is in range; the editor draws a sample Plasmaflux instead.
 */
object DeployableTimerHudRenderer {

    private const val ICON_SIZE = 16
    private const val ICON_GAP = 3
    private const val GLYPH_HEIGHT = 9
    /** Centres the line on the icon. */
    private const val TEXT_Y = (ICON_SIZE - GLYPH_HEIGHT + 1) / 2

    private const val COLOR_PLENTY = 0xFF55FF55.toInt()
    private const val COLOR_HALF = 0xFFFFFF55.toInt()
    private const val COLOR_LOW = 0xFFFF5555.toInt()

    private val PREVIEW = DeployableTracker.Active(Deployable.PLASMAFLUX, 42)

    /** Each kind's head, built once; building one means a profile and a skin lookup. */
    private val icons = EnumMap<Deployable, ItemStack>(Deployable::class.java)

    private fun icon(type: Deployable): ItemStack = icons.getOrPut(type) { ItemIcons.head(type.textureValue()) }

    /** Green while more than half is left, yellow down to a fifth, then red. */
    private fun color(active: DeployableTracker.Active): Int {
        val left = active.secondsLeft().toFloat() / active.type().durationSeconds
        return when {
            left > 0.5f -> COLOR_PLENTY
            left > 0.2f -> COLOR_HALF
            else -> COLOR_LOW
        }
    }

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.deployableTimerEnabled) return

        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return

        val active = DeployableTracker.active() ?: return
        val placement = DeployableTimerHudElement.visiblePlacement(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
        renderHud(graphics, placement.anchorX, placement.anchorY, cfg.deployableTimerHudScale, active)
    }

    /** What to draw: the live deployable, or the sample while there is none to position against. */
    fun shown(): DeployableTracker.Active = DeployableTracker.active() ?: PREVIEW

    fun renderHud(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, active: DeployableTracker.Active) {
        val font = Minecraft.getInstance().font ?: return
        val cfg = AlpakaConfig.instance
        val time = timeText(active)

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        if (cfg.blurOn(cfg.deployableTimerHudBlur)) {
            HudPanel.aroundText(graphics, ICON_SIZE + ICON_GAP + font.width(time), ICON_SIZE, scale)
        }
        graphics.item(icon(active.type()), 0, 0)
        graphics.text(font, time, ICON_SIZE + ICON_GAP, TEXT_Y, color(active), HudPanel.TEXT_SHADOW)
        graphics.pose().popMatrix()
    }

    private fun timeText(active: DeployableTracker.Active): Component {
        val cfg = AlpakaConfig.instance
        return HudPanel.text(Component.literal(Deployable.formatTime(active.secondsLeft())),
            cfg.smoothOn(cfg.deployableTimerHudSmoothFont))
    }

    /** Unscaled width, for the editor's box. */
    fun width(font: Font, active: DeployableTracker.Active): Int = ICON_SIZE + ICON_GAP + font.width(timeText(active))

    fun height(scale: Float): Int = ceil(ICON_SIZE * scale).toInt()
}
