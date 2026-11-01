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
import net.minecraft.world.item.Items
import kotlin.math.ceil

/**
 * Draws the strongest deployable of each family in range, one line each: its head as an icon, and
 * the time it has left beside it. Shown only while one is in range; the editor draws a sample
 * Plasmaflux and Glacite Lantern instead.
 */
object DeployableTimerHudRenderer {

    private const val ICON_SIZE = 16
    private const val ICON_GAP = 3
    private const val GLYPH_HEIGHT = 9
    private const val LINE_GAP = 2
    private const val LINE_HEIGHT = ICON_SIZE + LINE_GAP
    /** Centres the line on the icon. */
    private const val TEXT_Y = (ICON_SIZE - GLYPH_HEIGHT + 1) / 2

    private const val COLOR_PLENTY = 0xFF55FF55.toInt()
    private const val COLOR_HALF = 0xFFFFFF55.toInt()
    private const val COLOR_LOW = 0xFFFF5555.toInt()

    private val PREVIEW = listOf(
        DeployableTracker.Active(Deployable.PLASMAFLUX, 42),
        DeployableTracker.Active(Deployable.GLACITE_LANTERN, 183),
    )

    /** Each kind's head, built once; building one means a profile and a skin lookup. */
    private val icons = EnumMap<Deployable, ItemStack>(Deployable::class.java)

    /**
     * Null until a world has been joined: items get their components only then, and building a stack
     * before that throws. The HUD editor can be opened from the title screen, where the sample is drawn
     * without its head.
     */
    private fun icon(type: Deployable): ItemStack? {
        icons[type]?.let { return it }
        if (!Items.PLAYER_HEAD.builtInRegistryHolder().areComponentsBound()) return null
        val texture = type.textureValue()
        val stack = if (texture != null) ItemIcons.head(texture) else ItemStack(Items.BANNER.white())
        return stack.also { icons[type] = it }
    }

    /**
     * Green while more than half is left, yellow down to a fifth, then red. A Bubblegum pet item
     * doubles the time, which simply reads as plenty until the normal duration is reached.
     */
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

        val active = DeployableTracker.active()
        if (active.isEmpty()) return
        val placement = DeployableTimerHudElement.visiblePlacement(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
        renderHud(graphics, placement.anchorX, placement.anchorY, cfg.deployableTimerHudScale, active)
    }

    /** What to draw: the live deployables, or the sample while there is none to position against. */
    fun shown(): List<DeployableTracker.Active> = DeployableTracker.active().ifEmpty { PREVIEW }

    fun renderHud(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, shown: List<DeployableTracker.Active>) {
        val font = Minecraft.getInstance().font ?: return
        val cfg = AlpakaConfig.instance

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        if (cfg.blurOn(cfg.deployableTimerHudBlur)) {
            HudPanel.aroundText(graphics, width(font, shown), unscaledHeight(shown.size), scale)
        }
        shown.forEachIndexed { i, active ->
            val top = i * LINE_HEIGHT
            icon(active.type())?.let { graphics.item(it, 0, top) }
            graphics.text(font, timeText(active), ICON_SIZE + ICON_GAP, top + TEXT_Y, color(active), HudPanel.TEXT_SHADOW)
        }
        graphics.pose().popMatrix()
    }

    private fun timeText(active: DeployableTracker.Active): Component {
        val cfg = AlpakaConfig.instance
        return HudPanel.text(Component.literal(Deployable.formatTime(active.secondsLeft())),
            cfg.smoothOn(cfg.deployableTimerHudSmoothFont))
    }

    /** Unscaled width of the widest line, for the editor's box. */
    fun width(font: Font, shown: List<DeployableTracker.Active>): Int =
        ICON_SIZE + ICON_GAP + (shown.maxOfOrNull { font.width(timeText(it)) } ?: 0)

    private fun unscaledHeight(lines: Int): Int = lines * LINE_HEIGHT - LINE_GAP

    fun height(scale: Float, shown: List<DeployableTracker.Active>): Int = ceil(unscaledHeight(shown.size) * scale).toInt()
}
