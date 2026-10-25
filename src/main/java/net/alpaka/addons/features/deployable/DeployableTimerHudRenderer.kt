package net.alpaka.addons.features.deployable

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import java.nio.charset.StandardCharsets
import java.util.EnumMap
import java.util.UUID
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
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

    /**
     * Null until a world has been joined: items get their components only then, and building a stack
     * before that throws. The HUD editor can be opened from the title screen, where the sample is drawn
     * without its head.
     */
    private fun icon(type: Deployable): ItemStack? {
        icons[type]?.let { return it }
        if (!Items.PLAYER_HEAD.builtInRegistryHolder().areComponentsBound()) return null
        return head(type.textureValue()).also { icons[type] = it }
    }

    /** A player head wearing the skin with this texture value, as Hypixel's heads carry it. */
    private fun head(texture: String): ItemStack {
        val id = UUID.nameUUIDFromBytes(texture.toByteArray(StandardCharsets.UTF_8))
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(id, "alpaka", properties)))
        return stack
    }

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
        // Clamped so a GUI-scale change cannot leave the HUD off screen; the stored position is untouched.
        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight
        renderHud(
            graphics,
            DeployableTimerHudElement.visibleAnchorX(screenWidth, screenHeight),
            DeployableTimerHudElement.visibleAnchorY(screenWidth, screenHeight),
            cfg.deployableTimerHudScale,
            active,
        )
    }

    /** What to draw: the live deployable, or the sample while there is none to position against. */
    fun shown(): DeployableTracker.Active = DeployableTracker.active() ?: PREVIEW

    fun renderHud(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, active: DeployableTracker.Active) {
        val font = Minecraft.getInstance().font ?: return
        val time = timeText(active)

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        icon(active.type())?.let { graphics.item(it, 0, 0) }
        graphics.text(font, time, ICON_SIZE + ICON_GAP, TEXT_Y, color(active))
        graphics.pose().popMatrix()
    }

    private fun timeText(active: DeployableTracker.Active): Component =
        Component.literal(Deployable.formatTime(active.secondsLeft()))

    /** Unscaled width, for the editor's box. */
    fun width(font: Font, active: DeployableTracker.Active): Int = ICON_SIZE + ICON_GAP + font.width(timeText(active))

    fun height(scale: Float): Int = ceil(ICON_SIZE * scale).toInt()
}
