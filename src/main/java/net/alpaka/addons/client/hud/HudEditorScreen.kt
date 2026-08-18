package net.alpaka.addons.client.hud

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * One editor for every HUD the mod draws: drag to reposition, scroll to resize, arrow keys to
 * nudge by a pixel.
 *
 * The screen is deliberately ignorant of individual features - it walks [HudRegistry] and talks to
 * each [HudElement] through its interface - so a new HUD becomes editable without touching this
 * file.
 *
 * Edits are applied to [AlpakaConfig.instance] immediately but only written to disk once an
 * interaction ends, so dragging does not serialize the whole config file every frame.
 */
class HudEditorScreen(private val parent: Screen?) : Screen(Component.literal("HUD Editor")) {

    private var selected: HudElement? = null

    private var dragging: HudElement? = null
    private var dragOffsetX = 0.0
    private var dragOffsetY = 0.0

    /** Set by drag/scroll/nudge/reset edits, flushed to disk on release, close, or reset. */
    private var dirty = false

    private var resetSelectedButton: Button? = null

    // Status line cache, rebuilt only when the selected element's values actually change.
    private var cachedStatus: Component? = null
    private var cachedElement: HudElement? = null
    private var cachedX = Int.MIN_VALUE
    private var cachedY = Int.MIN_VALUE
    private var cachedScale = Float.NaN

    override fun init() {
        val buttonY = this.height - 30

        resetSelectedButton = addRenderableWidget(
            Button.builder(Component.literal("Reset Selected")) {
                selected?.let {
                    it.reset()
                    dirty = true
                    flush()
                }
            }.bounds(this.width / 2 - 155, buttonY, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Reset All")) {
                HudRegistry.ELEMENTS.forEach { it.reset() }
                dirty = true
                flush()
            }.bounds(this.width / 2 - 50, buttonY, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Save")) { onClose() }
                .bounds(this.width / 2 + 55, buttonY, 100, 20).build()
        )

        syncButtons()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Replace the standard game background with a darker backdrop.
        graphics.fill(0, 0, this.width, this.height, BACKDROP_COLOR)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBackground(graphics, mouseX, mouseY, partialTick)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        graphics.centeredText(this.font, INSTRUCTIONS, this.width / 2, 12, TEXT_COLOR)
        graphics.centeredText(this.font, HINTS, this.width / 2, 24, HINT_COLOR)
        graphics.centeredText(this.font, statusLine(), this.width / 2, 36, STATUS_COLOR)

        // Only the element actually being dragged should count as hovered, so the highlight does
        // not jump to a HUD that happens to slide under the cursor mid-drag.
        val active = dragging ?: HudRegistry.topmostAt(mouseX.toDouble(), mouseY.toDouble())

        for (element in HudRegistry.ELEMENTS) {
            drawElement(graphics, element, element === active)
        }
    }

    private fun drawElement(graphics: GuiGraphicsExtractor, element: HudElement, isHovered: Boolean) {
        // Draw the HUD exactly as it appears in-game, then annotate it.
        element.render(graphics)

        val box = element.bounds()
        val disabled = !element.isFeatureEnabled

        if (disabled) {
            // Grey out the preview of a feature that is switched off. Submitted after the preview
            // and overlapping it, which is what makes GuiRenderState place it in a higher layer -
            // so this lands on top of the HUD, including the player model's 3D render.
            graphics.fill(box.x0, box.y0, box.x1, box.y1, DISABLED_SCRIM_COLOR)
        }

        val guideColor = when {
            element === selected -> ModernGuiUtils.getAccentColor()
            isHovered -> GUIDE_HOVER_COLOR
            disabled -> GUIDE_DISABLED_COLOR
            else -> GUIDE_COLOR
        }
        ModernGuiUtils.drawOutline(graphics, box.x0, box.y0, box.width, box.height, guideColor)

        val label = if (disabled) "${element.name} (disabled)" else element.name
        val labelWidth = this.font.width(label)
        val labelX = box.x0 + (box.width - labelWidth) / 2
        // Above the box by default, below it when that would collide with the header text.
        val labelY = if (box.y0 - LABEL_GAP >= HEADER_BOTTOM) box.y0 - LABEL_GAP else box.y1 + 2

        val labelColor = when {
            element === selected -> ModernGuiUtils.getAccentColor()
            isHovered -> GUIDE_HOVER_COLOR
            disabled -> GUIDE_DISABLED_COLOR
            else -> ModernGuiUtils.COLOR_TEXT_MUTED
        }
        graphics.text(this.font, Component.literal(label), labelX, labelY, labelColor)
    }

    private fun statusLine(): Component {
        val element = selected ?: return NO_SELECTION

        val x = element.anchorX
        val y = element.anchorY
        val scale = element.scaleValue()

        var status = cachedStatus
        if (status == null || element !== cachedElement || x != cachedX || y != cachedY || scale != cachedScale) {
            status = Component.literal("${element.name}  -  X: $x | Y: $y | Scale: ${element.scaleLabel()}")
            cachedStatus = status
            cachedElement = element
            cachedX = x
            cachedY = y
            cachedScale = scale
        }
        return status
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            val hit = HudRegistry.topmostAt(event.x(), event.y())
            if (hit != null) {
                select(hit)
                dragging = hit
                dragOffsetX = event.x() - hit.anchorX
                dragOffsetY = event.y() - hit.anchorY
                return true
            }
        }

        // Let the widgets have the click first - clearing the selection before they run would make
        // "Reset Selected" a no-op the moment it is pressed.
        if (super.mouseClicked(event, doubleClick)) return true

        if (event.button() == 0) select(null)
        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0 && dragging != null) {
            dragging = null
            flush()
        }
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val element = dragging ?: return super.mouseDragged(event, deltaX, deltaY)

        element.anchorX = (event.x() - dragOffsetX).toInt()
        element.anchorY = (event.y() - dragOffsetY).toInt()
        dirty = true
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

        // Resize whatever is under the cursor; fall back to the selection so the wheel still works
        // while pointing at empty space.
        val element = HudRegistry.topmostAt(mouseX, mouseY)
            ?: selected
            ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

        select(element)
        element.adjustScale(scrollY)
        dirty = true
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        // Pixel-precise nudging, which a mouse drag cannot do.
        val element = selected
        if (element != null) {
            val dx = when (event.key()) {
                GLFW.GLFW_KEY_LEFT -> -1
                GLFW.GLFW_KEY_RIGHT -> 1
                else -> 0
            }
            val dy = when (event.key()) {
                GLFW.GLFW_KEY_UP -> -1
                GLFW.GLFW_KEY_DOWN -> 1
                else -> 0
            }
            if (dx != 0 || dy != 0) {
                element.anchorX += dx
                element.anchorY += dy
                dirty = true
                return true
            }
        }

        return super.keyPressed(event)
    }

    override fun onClose() {
        flush()
        this.minecraft?.setScreen(parent)
    }

    private fun select(element: HudElement?) {
        if (selected === element) return
        selected = element
        syncButtons()
    }

    private fun syncButtons() {
        resetSelectedButton?.active = selected != null
    }

    private fun flush() {
        if (dirty) {
            dirty = false
            AlpakaConfig.save()
        }
    }

    private companion object {
        const val BACKDROP_COLOR = 0x88000000.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val HINT_COLOR = 0xFF8A8A8A.toInt()
        const val STATUS_COLOR = 0xFFA0A0A0.toInt()
        const val GUIDE_COLOR = 0xFFFFFFFF.toInt()
        const val GUIDE_HOVER_COLOR = 0xFF00FF00.toInt()
        const val GUIDE_DISABLED_COLOR = 0xFF6A6A6A.toInt()

        /** Dark wash over a HUD whose feature is switched off. */
        const val DISABLED_SCRIM_COLOR = 0xAA0A0A0A.toInt()

        /** Vertical gap between a box and its label, and the y below which labels must not go. */
        const val LABEL_GAP = 10
        const val HEADER_BOTTOM = 48

        val INSTRUCTIONS: Component = Component.literal("HUD Editor - Drag to position, Scroll to resize")
        val HINTS: Component = Component.literal("Arrow keys nudge the selection - greyed out HUDs are disabled in the config")
        val NO_SELECTION: Component = Component.literal("Click a HUD to select it")
    }
}
