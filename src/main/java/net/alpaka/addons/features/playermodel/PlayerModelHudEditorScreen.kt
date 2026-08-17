package net.alpaka.addons.features.playermodel

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Visual editor for the player model HUD: drag the avatar to reposition it, scroll to resize.
 *
 * Edits are applied to [AlpakaConfig.instance] immediately but only written to disk once the
 * interaction ends, so a drag no longer serializes the whole config file every frame.
 */
class PlayerModelHudEditorScreen(private val parent: Screen?) : Screen(Component.literal("HUD Editor")) {

    private var dragging = false
    private var dragOffsetX = 0.0
    private var dragOffsetY = 0.0

    /** Set by drag/scroll edits, flushed to disk on release, reset, or close. */
    private var dirty = false

    // Status line cache - rebuilt only when one of the three values actually changes.
    private var cachedStatus: Component? = null
    private var cachedX = Int.MIN_VALUE
    private var cachedY = Int.MIN_VALUE
    private var cachedScale = Int.MIN_VALUE

    override fun init() {
        val buttonY = this.height - 40

        addRenderableWidget(
            Button.builder(Component.literal("Reset")) {
                val cfg = AlpakaConfig.instance
                cfg.playerModelX = PlayerModelRenderer.DEFAULT_X
                cfg.playerModelY = PlayerModelRenderer.DEFAULT_Y
                cfg.playerModelScale = PlayerModelRenderer.DEFAULT_SCALE
                dirty = true
                flush()
            }.bounds(this.width / 2 - 105, buttonY, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Save")) { onClose() }
                .bounds(this.width / 2 + 5, buttonY, 100, 20).build()
        )
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Replace the standard game background with a darker backdrop.
        graphics.fill(0, 0, this.width, this.height, BACKDROP_COLOR)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBackground(graphics, mouseX, mouseY, partialTick)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val cfg = AlpakaConfig.instance
        val x = cfg.playerModelX
        val y = cfg.playerModelY
        val scale = cfg.playerModelScale

        graphics.centeredText(this.font, INSTRUCTIONS, this.width / 2, 20, TEXT_COLOR)
        graphics.centeredText(this.font, statusLine(x, y, scale), this.width / 2, 35, STATUS_COLOR)

        val player = (this.minecraft ?: Minecraft.getInstance()).player ?: return
        PlayerModelRenderer.renderPlayerModel(graphics, x, y, scale, player)

        val hovered = PlayerModelRenderer.isOverModel(mouseX.toDouble(), mouseY.toDouble(), x, y, scale)
        PlayerModelRenderer.outlineModel(graphics, x, y, scale, if (hovered) GUIDE_HOVER_COLOR else GUIDE_COLOR)
    }

    private fun statusLine(x: Int, y: Int, scale: Int): Component {
        var status = cachedStatus
        if (status == null || x != cachedX || y != cachedY || scale != cachedScale) {
            status = Component.literal("X: $x | Y: $y | Scale: ${scale}x")
            cachedStatus = status
            cachedX = x
            cachedY = y
            cachedScale = scale
        }
        return status
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val cfg = AlpakaConfig.instance
        if (event.button() == 0 &&
            PlayerModelRenderer.isOverModel(event.x(), event.y(), cfg.playerModelX, cfg.playerModelY, cfg.playerModelScale)
        ) {
            dragging = true
            dragOffsetX = event.x() - cfg.playerModelX
            dragOffsetY = event.y() - cfg.playerModelY
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0) {
            dragging = false
            flush()
        }
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY)

        val cfg = AlpakaConfig.instance
        cfg.playerModelX = (event.x() - dragOffsetX).toInt()
        cfg.playerModelY = (event.y() - dragOffsetY).toInt()
        dirty = true
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

        val cfg = AlpakaConfig.instance
        cfg.playerModelScale = (cfg.playerModelScale + (scrollY * 2).toInt()).coerceIn(MIN_SCALE, MAX_SCALE)
        dirty = true
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        flush()
        this.minecraft?.setScreen(parent)
    }

    private fun flush() {
        if (dirty) {
            dirty = false
            AlpakaConfig.save()
        }
    }

    private companion object {
        const val MIN_SCALE = 10
        const val MAX_SCALE = 200

        const val BACKDROP_COLOR = 0x88000000.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val STATUS_COLOR = 0xFFA0A0A0.toInt()
        const val GUIDE_COLOR = 0xFFFFFFFF.toInt()
        const val GUIDE_HOVER_COLOR = 0xFF00FF00.toInt()

        val INSTRUCTIONS: Component = Component.literal("HUD Editor - Drag model to position, Scroll to resize")
    }
}
