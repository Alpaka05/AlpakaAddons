package net.alpaka.addons.features.chat

import java.util.Locale
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ChatComponent
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Better Chat's Movable Chat: the chat as a HUD element.
 *
 * The anchor is the chat box's bottom-left corner, where vanilla has it 40 pixels above the
 * screen's bottom. It is stored as an offset from that place, so a fresh config has the chat where
 * it always was and a moved chat keeps its distance to the bottom when the window is resized. The
 * chat mixin moves the whole chat layout by the same amount, which takes its clicks, hover and
 * blurred panel along. The box is the open chat's, the taller one, so the chat stays on screen
 * when it is opened; the editor's scroll wheel sets vanilla's own Chat Text Size.
 */
object ChatHudElement : HudElement {

    /** Vanilla's gap between the chat's lowest line and the screen's bottom. */
    private const val BOTTOM_MARGIN = 40
    /** Room right of the box for the scrollbar and the blurred panel's edge, in GUI pixels. */
    private const val RIGHT_EXTRA = 20
    /**
     * Room above the box for the blurred panel's edge and the chat's clip, which reach a few pixels
     * past the lines. The box has to hold them: kept on screen through [visiblePlacement], a clip
     * past the screen's top or right edge is one the renderer refuses, and the game crashed.
     */
    private const val TOP_EXTRA = 8
    private const val SCALE_PER_NOTCH = 0.05

    override val id: String = "chat"
    override val name: String = "Chat"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.betterChatOn(AlpakaConfig.instance.chatMovable)

    override val settingsOptionId: String = "chat_movable"

    private fun screenHeight(): Int = Minecraft.getInstance().window.guiScaledHeight

    override var anchorX: Int
        get() = AlpakaConfig.instance.chatOffsetX
        set(value) { AlpakaConfig.instance.chatOffsetX = value }

    override var anchorY: Int
        get() = screenHeight() - BOTTOM_MARGIN + AlpakaConfig.instance.chatOffsetY
        set(value) { AlpakaConfig.instance.chatOffsetY = value - (screenHeight() - BOTTOM_MARGIN) }

    private fun chatScale(): Double = Minecraft.getInstance().options.chatScale().get()

    override fun bounds(): HudBounds {
        val options = Minecraft.getInstance().options
        val scale = chatScale()
        val width = ceil((ChatComponent.getWidth(options.chatWidth().get()) + 8 * scale)).toInt() + RIGHT_EXTRA
        val height = ceil(ChatComponent.getHeight(options.chatHeightFocused().get()) * scale).toInt()
        val x = anchorX
        val y = anchorY
        return HudBounds(x, y - height - TOP_EXTRA, x + width, y)
    }

    /** Only the chat's own box: the room for the scrollbar and the panel's edge stays unmarked. */
    override fun outlineBounds(box: HudBounds): HudBounds =
        HudBounds(box.x0, box.y0 + TOP_EXTRA, box.x1 - RIGHT_EXTRA, box.y1)

    override fun adjustScale(notches: Double) {
        val raw = (chatScale() + notches * SCALE_PER_NOTCH).coerceIn(0.0, 1.0)
        // The option lays the chat out again itself when its value changes; laying it out here as
        // well did it twice per notch. options.txt is written once, when the editor closes.
        Minecraft.getInstance().options.chatScale().set((raw * 100.0).roundToInt() / 100.0)
        optionsChanged = true
    }

    /** Whether the editor changed the chat scale, a vanilla option, since options.txt was written. */
    private var optionsChanged = false

    override fun flushEdits() {
        if (!optionsChanged) return
        optionsChanged = false
        Minecraft.getInstance().options.save()
    }

    override fun reset() {
        AlpakaConfig.instance.chatOffsetX = 0
        AlpakaConfig.instance.chatOffsetY = 0
    }

    /** A stand-in for the chat: its box, with a few lines of placeholder text. */
    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        val box = bounds()
        val left = x
        val top = y - box.height + TOP_EXTRA
        ModernGuiUtils.drawRoundedRect(graphics, left, top, box.width - RIGHT_EXTRA, box.height - TOP_EXTRA, 3, 0x66000000)
        val font = Minecraft.getInstance().font
        val scale = chatScale().toFloat()
        val lines = listOf("Chat messages appear here.", "Drag to move, scroll to resize.")
        graphics.pose().pushMatrix()
        graphics.pose().translate(left + 4f, y.toFloat())
        graphics.pose().scale(scale, scale)
        lines.forEachIndexed { i, line ->
            val lineY = -(lines.size - i) * 9 - 1
            graphics.text(font, GuiFont.text(line), 0, lineY, 0xFFBDBDBD.toInt(), true)
        }
        graphics.pose().popMatrix()
    }

    override fun scaleValue(): Float = chatScale().toFloat()

    override fun scaleLabel(): String = String.format(Locale.ROOT, "%d%%", (chatScale() * 100).roundToInt())
}
