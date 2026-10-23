package net.alpaka.addons.client

import net.alpaka.addons.client.gui.EditableText
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.chat.CustomMessageFilter
import net.alpaka.addons.features.sound.CustomSoundFeature
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * Where the Message Filter's own filters are made: paste a message copied from the chat, pick how
 * it is compared, add it.
 *
 * Each row has its on/off box, the text, the comparison (cycled by clicking it), "Aa" for matching
 * regardless of case, and a delete button. Clicking a row's text loads it into the field below to
 * be edited; the add button then saves it back instead.
 *
 * Drawn like the quick command editor: rounded panels with hairline borders, the menu font without
 * shadows, the accent color for whatever is selected.
 */
class MessageFilterScreen(private val parent: Screen) : Screen(Component.literal("Custom Message Filters")) {

    private val input = EditableText(MAX_TEXT)
    private var inputFocused = true
    private var newMode = CustomMessageFilter.Mode.EQUALS
    /** The filter being edited in the field, or -1 while the field makes a new one. */
    private var editing = -1

    private var scrollY = 0.0
    private var targetScrollY = 0.0
    private var maxScrollY = 0.0
    private var lastFrameMs = System.currentTimeMillis()
    /** What the control under the mouse does, shown in the hint line; null when it is self-explanatory. */
    private var hoverHint: String? = null

    private val filters: MutableList<CustomMessageFilter>
        get() = AlpakaConfig.instance.messageFilterCustom

    // ----------------------------------------------------------------- layout

    private class Frame(
        val winX: Int, val winY: Int, val winW: Int, val winH: Int,
        val listX: Int, val listY: Int, val listW: Int, val listH: Int,
        val hintY: Int, val inputY: Int, val inputW: Int,
    )

    /** The window clamped to the screen; the list takes whatever height the bottom rows leave. */
    private fun frame(): Frame {
        val winW = (this.width - MARGIN * 2).coerceIn(MIN_W, WIN_W)
        val winH = (this.height - MARGIN * 2).coerceIn(MIN_H, WIN_H)
        val winX = (this.width - winW) / 2
        val winY = maxOf(MARGIN / 2, (this.height - winH) / 2)
        val listX = winX + 16
        val listY = winY + HEADER_H + 10
        val listW = winW - 32
        val inputY = winY + winH - 16 - BOTTOM_H
        val hintY = inputY - 14
        val listH = hintY - 8 - listY
        val inputW = listW - MODE_W - ADD_W - 12
        return Frame(winX, winY, winW, winH, listX, listY, listW, listH, hintY, inputY, inputW)
    }

    private fun playPloppSound() {
        try {
            CustomSoundFeature.playButtonClickSound()
        } catch (_: Throwable) {}
    }

    override fun onClose() {
        AlpakaConfig.save()
        this.minecraft?.gui?.setScreen(this.parent)
    }

    // -------------------------------------------------------------- rendering

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)

        val now = System.currentTimeMillis()
        val deltaSec = min(0.1f, (now - lastFrameMs) / 1000f)
        lastFrameMs = now

        graphics.pose().pushMatrix()
        graphics.pose().identity()
        graphics.fill(0, 0, this.width, this.height, 0x70000000)
        graphics.pose().popMatrix()

        val f = frame()
        val accent = ModernGuiUtils.getAccentColor()

        ModernGuiUtils.drawPanelShadow(graphics, f.winX, f.winY, f.winW, f.winH, ModernGuiUtils.PANEL_RADIUS, 1.0f)
        ModernGuiUtils.drawRoundedPanel(graphics, f.winX, f.winY, f.winW, f.winH, ModernGuiUtils.PANEL_RADIUS,
            ModernGuiUtils.COLOR_PANEL_BG, ModernGuiUtils.COLOR_CARD_BORDER)

        // Header band with the accent rule, as on the other menus.
        val r = ModernGuiUtils.PANEL_RADIUS
        ModernGuiUtils.drawRoundedRect(graphics, f.winX + 1, f.winY + 1, f.winW - 2, HEADER_H - 1, r - 1, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawRect(graphics, f.winX + 1, f.winY + HEADER_H - r, f.winW - 2, r, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawRect(graphics, f.winX + 1, f.winY + HEADER_H - 1, f.winW - 2, 1, accent)
        graphics.text(this.font, GuiFont.text("Custom Message Filters"), f.winX + 16, f.winY + 15, ModernGuiUtils.COLOR_TEXT_PRIMARY, false)

        val hoverDone = inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, DONE_H)
        ModernGuiUtils.drawModernButton(graphics, this.font, doneX(f), doneY(f), DONE_W, DONE_H, "Done", hoverDone, false)

        ModernGuiUtils.drawRoundedPanel(graphics, f.listX, f.listY, f.listW, f.listH, ModernGuiUtils.WIDGET_RADIUS + 2,
            ModernGuiUtils.COLOR_SIDEBAR_BG, ModernGuiUtils.COLOR_CARD_BORDER)

        val contentHeight = 6 + filters.size * (ITEM_H + ITEM_GAP)
        maxScrollY = max(0.0, (contentHeight - f.listH + 6).toDouble())
        targetScrollY = targetScrollY.coerceIn(0.0, maxScrollY)
        scrollY += (targetScrollY - scrollY) * min(1.0, deltaSec * 14.0)

        if (filters.isEmpty()) {
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("No filters yet."),
                f.listX + f.listW / 2, f.listY + f.listH / 2 - 10, ModernGuiUtils.COLOR_TEXT_MUTED)
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Paste a copied message below and press Add."),
                f.listX + f.listW / 2, f.listY + f.listH / 2 + 2, ModernGuiUtils.COLOR_TEXT_DARK)
        }

        val inList = inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)
        hoverHint = null
        graphics.enableScissor(f.listX + 1, f.listY + 1, f.listX + f.listW - 1, f.listY + f.listH - 1)
        val rowX = f.listX + 8
        val rowW = f.listW - 16
        for (i in filters.indices) {
            val y = rowY(f, i)
            if (y + ITEM_H < f.listY || y > f.listY + f.listH) continue
            drawRow(graphics, filters[i], i, rowX, y, rowW, mouseX, mouseY, inList)
        }
        graphics.disableScissor()

        // What the comparison picked for the field does, so the choice needs no guessing.
        val hint = hoverHint ?: if (editing >= 0) "Editing a filter: Enter saves, Esc cancels. ${newMode.hint}" else newMode.hint
        graphics.text(this.font, GuiFont.text(fit(hint, f.listW)), f.listX + 2, f.hintY, ModernGuiUtils.COLOR_TEXT_MUTED, false)

        drawField(graphics, f, mouseX, mouseY)
        val hoverMode = inside(mouseX, mouseY, modeBtnX(f), f.inputY, MODE_W, BOTTOM_H)
        ModernGuiUtils.drawModernButton(graphics, this.font, modeBtnX(f), f.inputY, MODE_W, BOTTOM_H, newMode.label, hoverMode, false)
        val hoverAdd = inside(mouseX, mouseY, addBtnX(f), f.inputY, ADD_W, BOTTOM_H)
        ModernGuiUtils.drawModernButton(graphics, this.font, addBtnX(f), f.inputY, ADD_W, BOTTOM_H,
            if (editing >= 0) "Save" else "+ Add", hoverAdd, true)
    }

    private fun drawRow(graphics: GuiGraphicsExtractor, filter: CustomMessageFilter, index: Int, x: Int, y: Int, w: Int,
                        mouseX: Int, mouseY: Int, inList: Boolean) {
        val hovered = inList && inside(mouseX, mouseY, x, y, w, ITEM_H)
        val selected = index == editing
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, ITEM_H, ModernGuiUtils.WIDGET_RADIUS + 1,
            if (hovered) ModernGuiUtils.COLOR_CARD_BG_HOVER else ModernGuiUtils.COLOR_CARD_BG,
            when {
                selected -> ModernGuiUtils.getAccentColor()
                hovered -> ModernGuiUtils.getAccentDimColor()
                else -> ModernGuiUtils.COLOR_CARD_BORDER
            })

        val btnY = y + (ITEM_H - SMALL_BTN) / 2
        val boxY = y + (ITEM_H - CHECK) / 2
        ModernGuiUtils.drawModernCheckbox(graphics, this.font, x + 6, boxY, CHECK, filter.enabled,
            inList && inside(mouseX, mouseY, x + 6, boxY, CHECK, CHECK))
        if (hovered && hoverHint == null) hoverHint = "Click the text to edit it, the box to switch the filter off."

        val textX = x + 6 + CHECK + 8
        val textW = rowModeX(x, w) - 6 - textX
        val valid = filter.isValid()
        val label = if (valid) filter.text else "${filter.text}  (invalid regex)"
        val color = when {
            !valid -> ModernGuiUtils.COLOR_TOGGLE_OFF_TEXT
            filter.enabled -> ModernGuiUtils.COLOR_TEXT_PRIMARY
            else -> ModernGuiUtils.COLOR_TEXT_DARK
        }
        graphics.text(this.font, GuiFont.text(fit(label, textW)), textX, y + (ITEM_H - 8) / 2, color, false)

        val modeX = rowModeX(x, w)
        val modeHover = inList && inside(mouseX, mouseY, modeX, btnY, ROW_MODE_W, SMALL_BTN)
        ModernGuiUtils.drawModernButton(graphics, this.font, modeX, btnY, ROW_MODE_W, SMALL_BTN, filter.mode.label, modeHover, false)
        if (modeHover) hoverHint = "Click to change. ${filter.mode.hint}"

        val caseX = rowCaseX(x, w)
        val caseHover = inList && inside(mouseX, mouseY, caseX, btnY, CASE_W, SMALL_BTN)
        if (caseHover) hoverHint = if (filter.ignoreCase) "Ignores upper and lower case. Click to match case exactly."
            else "Matches case exactly. Click to ignore upper and lower case."
        drawSmallButton(graphics, caseX, btnY, CASE_W, "Aa", caseHover,
            if (filter.ignoreCase) ModernGuiUtils.getAccentColor() else ModernGuiUtils.COLOR_TEXT_DARK, false)

        val delX = rowDelX(x, w)
        drawSmallButton(graphics, delX, btnY, SMALL_BTN, "✕", inList && inside(mouseX, mouseY, delX, btnY, SMALL_BTN, SMALL_BTN),
            0xFFFCA5A5.toInt(), true)
    }

    private fun drawSmallButton(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, glyph: String, hovered: Boolean, textColor: Int, destructive: Boolean) {
        val bg = if (destructive) (if (hovered) 0xFFDC2626.toInt() else 0x33DC2626) else (if (hovered) ModernGuiUtils.COLOR_CARD_BG_HOVER else ModernGuiUtils.COLOR_SIDEBAR_BG)
        val border = if (destructive) (if (hovered) 0xFFEF4444.toInt() else 0x88DC2626.toInt()) else (if (hovered) ModernGuiUtils.getAccentColor() else ModernGuiUtils.COLOR_CARD_BORDER)
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, SMALL_BTN, ModernGuiUtils.WIDGET_RADIUS, bg, border)
        val color = if (destructive && hovered) 0xFFFFFFFF.toInt() else textColor
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(glyph), x + w / 2, y + (SMALL_BTN - 8) / 2, color)
    }

    /**
     * The text field, drawn here rather than with the shared one because it has a caret that can
     * move and a selection: a pasted message often needs a number cut out of it before it is useful.
     */
    private fun drawField(graphics: GuiGraphicsExtractor, f: Frame, mouseX: Int, mouseY: Int) {
        val x = f.listX
        val y = f.inputY
        val w = f.inputW
        val hovered = inside(mouseX, mouseY, x, y, w, BOTTOM_H)
        val border = if (inputFocused) ModernGuiUtils.getAccentColor() else if (hovered) ModernGuiUtils.getAccentDimColor() else ModernGuiUtils.COLOR_CARD_BORDER
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, BOTTOM_H, ModernGuiUtils.WIDGET_RADIUS, ModernGuiUtils.COLOR_CARD_BG, border)

        val textY = y + (BOTTOM_H - 8) / 2
        val text = input.text
        if (text.isEmpty()) {
            graphics.text(this.font, GuiFont.text(if (newMode == CustomMessageFilter.Mode.REGEX) "Regex" else "Message to hide"),
                x + 4, textY, ModernGuiUtils.COLOR_TEXT_DARK, false)
        }

        val start = visibleStart(text, w - 8)
        val shown = text.substring(start)
        graphics.enableScissor(x + 2, y + 1, x + w - 2, y + BOTTOM_H - 1)
        if (inputFocused && input.hasSelection()) {
            val selFrom = max(input.selectionStart, start)
            val selTo = max(input.selectionEnd, start)
            val sx = x + 4 + GuiFont.width(this.font, text.substring(start, selFrom))
            val ex = x + 4 + GuiFont.width(this.font, text.substring(start, selTo))
            ModernGuiUtils.drawRect(graphics, sx, textY - 1, ex - sx, 10, ModernGuiUtils.getAccentDimColor())
        }
        graphics.text(this.font, GuiFont.text(shown), x + 4, textY, ModernGuiUtils.COLOR_TEXT_PRIMARY, false)
        if (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val caretX = x + 4 + GuiFont.width(this.font, text.substring(start, max(start, input.caret)))
            ModernGuiUtils.drawRect(graphics, caretX, textY - 1, 1, 10, ModernGuiUtils.getAccentColor())
        }
        graphics.disableScissor()
    }

    /** The first character shown, so that the caret is always inside the field. */
    private fun visibleStart(text: String, maxWidth: Int): Int {
        var start = 0
        val caret = input.caret
        while (start < caret && GuiFont.width(this.font, text.substring(start, caret)) > maxWidth) start++
        return start
    }

    private fun fit(text: String, width: Int): String {
        if (GuiFont.width(this.font, text) <= width) return text
        var cut = text
        while (cut.isNotEmpty() && GuiFont.width(this.font, "$cut…") > width) cut = cut.substring(0, cut.length - 1)
        return "$cut…"
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx in x..(x + w) && my in y..(y + h)

    // Positions shared by drawing and clicking.
    private fun rowY(f: Frame, index: Int) = f.listY + 6 + index * (ITEM_H + ITEM_GAP) - scrollY.toInt()
    private fun doneX(f: Frame) = f.winX + f.winW - DONE_W - 12
    private fun doneY(f: Frame) = f.winY + (HEADER_H - DONE_H) / 2
    private fun modeBtnX(f: Frame) = f.listX + f.inputW + 6
    private fun addBtnX(f: Frame) = modeBtnX(f) + MODE_W + 6
    private fun rowDelX(x: Int, w: Int) = x + w - SMALL_BTN - 5
    private fun rowCaseX(x: Int, w: Int) = rowDelX(x, w) - CASE_W - 4
    private fun rowModeX(x: Int, w: Int) = rowCaseX(x, w) - ROW_MODE_W - 4

    // ------------------------------------------------------------------ input

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick)
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val f = frame()

        if (inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, DONE_H)) {
            playPloppSound()
            onClose()
            return true
        }

        if (inside(mouseX, mouseY, f.listX, f.inputY, f.inputW, BOTTOM_H)) {
            inputFocused = true
            input.setCaretFromX(mouseX - f.listX - 4 + GuiFont.width(this.font, input.text.substring(0, visibleStart(input.text, f.inputW - 8)))) {
                GuiFont.width(this.font, it)
            }
            return true
        }
        if (inside(mouseX, mouseY, modeBtnX(f), f.inputY, MODE_W, BOTTOM_H)) {
            playPloppSound()
            newMode = newMode.next()
            return true
        }
        if (inside(mouseX, mouseY, addBtnX(f), f.inputY, ADD_W, BOTTOM_H)) {
            submit()
            return true
        }

        if (inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)) {
            val rowX = f.listX + 8
            val rowW = f.listW - 16
            for (i in filters.indices) {
                val y = rowY(f, i)
                if (!inside(mouseX, mouseY, rowX, y, rowW, ITEM_H)) continue
                val filter = filters[i]
                val btnY = y + (ITEM_H - SMALL_BTN) / 2
                when {
                    inside(mouseX, mouseY, rowDelX(rowX, rowW), btnY, SMALL_BTN, SMALL_BTN) -> {
                        filters.removeAt(i)
                        if (editing == i) stopEditing() else if (editing > i) editing--
                    }
                    inside(mouseX, mouseY, rowCaseX(rowX, rowW), btnY, CASE_W, SMALL_BTN) -> filter.ignoreCase = !filter.ignoreCase
                    inside(mouseX, mouseY, rowModeX(rowX, rowW), btnY, ROW_MODE_W, SMALL_BTN) -> filter.mode = filter.mode.next()
                    mouseX <= rowX + 6 + CHECK + 4 -> filter.enabled = !filter.enabled
                    // The row itself: edit it in the field, or stop editing when it is already there.
                    editing == i -> stopEditing()
                    else -> {
                        editing = i
                        input.setText(filter.text)
                        newMode = filter.mode
                        inputFocused = true
                    }
                }
                playPloppSound()
                AlpakaConfig.save()
                return true
            }
        }

        inputFocused = false
        input.clearSelection()
        return super.mouseClicked(event, isDoubleClick)
    }

    private fun submit() {
        val text = input.text.trim()
        if (text.isEmpty()) return
        playPloppSound()
        if (editing in filters.indices) {
            filters[editing].text = text
            filters[editing].mode = newMode
        } else {
            filters.add(CustomMessageFilter(text, newMode))
            targetScrollY = Double.MAX_VALUE
        }
        AlpakaConfig.save()
        stopEditing()
    }

    private fun stopEditing() {
        editing = -1
        input.setText("")
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0) {
            targetScrollY = (targetScrollY - scrollY * 24.0).coerceIn(0.0, maxScrollY)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (inputFocused) {
            val codePoint = event.codepoint()
            if (Character.isBmpCodePoint(codePoint)) input.insert(codePoint.toChar())
            return true
        }
        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (inputFocused) {
            when (event.key()) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    submit()
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    // Esc first drops the edit, then the focus, and only then closes the screen.
                    if (editing >= 0) stopEditing() else inputFocused = false
                    return true
                }
            }
            if (input.keyPressed(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown())) return true
        }
        return super.keyPressed(event)
    }

    companion object {
        private const val WIN_W = 520
        private const val WIN_H = 340
        private const val MIN_W = 320
        private const val MIN_H = 200
        /** Space kept free around the window, as the config screen keeps. */
        private const val MARGIN = 8
        private const val HEADER_H = 38
        private const val DONE_W = 60
        private const val DONE_H = 22
        private const val BOTTOM_H = 24
        private const val MODE_W = 80
        private const val ADD_W = 60

        private const val ITEM_H = 26
        private const val ITEM_GAP = 4
        private const val SMALL_BTN = 18
        private const val ROW_MODE_W = 74
        private const val CASE_W = 24
        private const val CHECK = 12

        /** Long enough for any chat line Hypixel sends, and for a regex written around one. */
        private const val MAX_TEXT = 512
    }
}
