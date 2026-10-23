package net.alpaka.addons.client

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind
import net.alpaka.addons.client.gui.EditableText
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.chat.CustomMessageFilter
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * Where the Message Filter's own filters are made: paste a message copied from the chat, pick how
 * it is compared, add it.
 *
 * Each row has its switch, the text, the comparison (cycled by clicking it), "Aa" for matching
 * regardless of case, and a delete button that shows while the row is hovered. Clicking a row's
 * text loads it into the field below to be edited; the add button then saves it back instead.
 *
 * Drawn with [AlpakaStyle], the config menu's look, like the command wheel editor.
 */
class MessageFilterScreen(private val parent: Screen) : Screen(GuiFont.text("Custom Message Filters")),
    net.alpaka.addons.client.gui.NoticeHost {

    private val input = EditableText(MAX_TEXT)
    private var inputFocused = true
    private var newMode = CustomMessageFilter.Mode.EQUALS
    /** The filter being edited in the field, or -1 while the field makes a new one. */
    private var editing = -1

    private var scrollY = 0.0
    private var targetScrollY = 0.0
    private var maxScrollY = 0.0
    private var lastFrameMs = System.currentTimeMillis()
    private var openTimeMs = 0L
    private var caretFrom = 0L
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

    /** The window clamped to the screen; the list takes whatever height the footer leaves. */
    private fun frame(): Frame {
        val winW = (this.width - MARGIN * 2).coerceIn(MIN_W, WIN_W)
        val winH = (this.height - MARGIN * 2).coerceIn(MIN_H, WIN_H)
        val winX = (this.width - winW) / 2
        val winY = maxOf(MARGIN / 2, (this.height - winH) / 2)
        val listX = winX + AlpakaStyle.PAD + 4
        val listY = winY + AlpakaStyle.HEADER_H + 6
        val listW = winW - 2 * (AlpakaStyle.PAD + 4)
        val inputY = winY + winH - AlpakaStyle.PAD - AlpakaStyle.BUTTON_H
        val hintY = inputY - 8 - 14
        val listH = hintY - 6 - listY
        val inputW = listW - MODE_W - ADD_W - 12
        return Frame(winX, winY, winW, winH, listX, listY, listW, listH, hintY, inputY, inputW)
    }

    private fun playPloppSound() = AlpakaStyle.click()

    override fun init() {
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis()
    }

    override fun onClose() {
        AlpakaConfig.save()
        this.minecraft?.gui?.setScreen(this.parent)
    }

    // -------------------------------------------------------------- rendering

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val now = System.currentTimeMillis()
        val deltaSec = min(0.1f, (now - lastFrameMs) / 1000f)
        lastFrameMs = now

        AlpakaStyle.backdrop(graphics, this.width, this.height)
        val pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height)

        val f = frame()
        AlpakaStyle.panel(graphics, f.winX, f.winY, f.winW, f.winH)
        AlpakaStyle.header(graphics, this.font, "Custom Message Filters", f.winX, f.winY, f.winW)

        val hoverDone = inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H, "Done", hoverDone, ButtonKind.PRIMARY)

        val contentHeight = filters.size * (ITEM_H + ITEM_GAP)
        maxScrollY = max(0.0, (contentHeight - f.listH).toDouble())
        targetScrollY = targetScrollY.coerceIn(0.0, maxScrollY)
        scrollY += (targetScrollY - scrollY) * min(1.0, deltaSec * 14.0)

        if (filters.isEmpty()) {
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("No filters yet."),
                f.listX + f.listW / 2, f.listY + f.listH / 2 - 10, AlpakaStyle.TEXT_SOFT)
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Paste a copied message below and press Add."),
                f.listX + f.listW / 2, f.listY + f.listH / 2 + 2, AlpakaStyle.TEXT_MUTED)
        }

        val inList = inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)
        hoverHint = null
        graphics.enableScissor(f.listX, f.listY, f.listX + f.listW, f.listY + f.listH)
        for (i in filters.indices) {
            val y = rowY(f, i)
            if (y + ITEM_H < f.listY || y > f.listY + f.listH) continue
            drawRow(graphics, filters[i], i, f.listX, y, f.listW, mouseX, mouseY, inList)
        }
        AlpakaStyle.scrollFades(graphics, f.listX, f.listY, f.listW, f.listH, scrollY > 0.5, scrollY < maxScrollY - 0.5)
        graphics.disableScissor()

        // Footer: what the chosen comparison does, then the field, the comparison and add.
        ModernGuiUtils.drawRect(graphics, f.winX + AlpakaStyle.PAD, f.hintY - 6, f.winW - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE)
        val hint = hoverHint ?: if (editing >= 0) "Editing a filter: Enter saves, Esc cancels. ${newMode.hint}" else newMode.hint
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, hint, f.listW)), f.listX, f.hintY + 3, AlpakaStyle.TEXT_MUTED, false)

        drawField(graphics, f, mouseX, mouseY)
        val hoverMode = inside(mouseX, mouseY, modeBtnX(f), f.inputY, MODE_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, modeBtnX(f), f.inputY, MODE_W, AlpakaStyle.BUTTON_H, newMode.label, hoverMode, ButtonKind.PLAIN)
        if (hoverMode) hoverHint = "Click to change how the text is compared."
        val hoverAdd = inside(mouseX, mouseY, addBtnX(f), f.inputY, ADD_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, addBtnX(f), f.inputY, ADD_W, AlpakaStyle.BUTTON_H,
            if (editing >= 0) "Save" else "Add", hoverAdd, ButtonKind.PRIMARY)

        AlpakaStyle.endOpen(graphics, pushed)
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics)
    }

    private fun drawRow(graphics: GuiGraphicsExtractor, filter: CustomMessageFilter, index: Int, x: Int, y: Int, w: Int,
                        mouseX: Int, mouseY: Int, inList: Boolean) {
        val hovered = inList && inside(mouseX, mouseY, x, y, w, ITEM_H)
        val selected = index == editing
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, ITEM_H, AlpakaStyle.CARD_RADIUS,
            when {
                selected -> AlpakaStyle.ROW_SELECTED
                hovered -> AlpakaStyle.ROW_HOVER
                else -> AlpakaStyle.ROW
            })
        if (selected) ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, ITEM_H, AlpakaStyle.CARD_RADIUS, AlpakaStyle.accentDim())

        val switchY = y + (ITEM_H - SWITCH_H) / 2
        AlpakaStyle.toggle(graphics, x + 6, switchY, SWITCH_W, SWITCH_H, if (filter.enabled) 1f else 0f)
        if (inList && inside(mouseX, mouseY, x + 6, switchY, SWITCH_W, SWITCH_H)) {
            hoverHint = if (filter.enabled) "Switch this filter off without deleting it." else "Switch this filter back on."
        }

        val btnY = y + (ITEM_H - SMALL_BTN) / 2
        val textX = x + 6 + SWITCH_W + 8
        val textW = rowModeX(x, w) - 6 - textX
        val valid = filter.isValid()
        val label = if (valid) filter.text else "${filter.text}  (invalid regex)"
        val color = when {
            !valid -> AlpakaStyle.DANGER
            filter.enabled -> AlpakaStyle.TEXT
            else -> AlpakaStyle.TEXT_MUTED
        }
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, label, textW)), textX, y + (ITEM_H - 8) / 2, color, false)
        if (hovered && hoverHint == null && mouseX in textX..(textX + textW)) hoverHint = "Click to edit this filter in the field below."

        val modeX = rowModeX(x, w)
        val modeHover = inList && inside(mouseX, mouseY, modeX, btnY, ROW_MODE_W, SMALL_BTN)
        AlpakaStyle.button(graphics, this.font, modeX, btnY, ROW_MODE_W, SMALL_BTN, filter.mode.label, modeHover, ButtonKind.PLAIN)
        if (modeHover) hoverHint = "Click to change. ${filter.mode.hint}"

        val caseX = rowCaseX(x, w)
        val caseHover = inList && inside(mouseX, mouseY, caseX, btnY, SMALL_BTN, SMALL_BTN)
        if (caseHover) {
            hoverHint = if (filter.ignoreCase) "Ignores upper and lower case. Click to match case exactly."
                else "Matches case exactly. Click to ignore upper and lower case."
        }
        if (filter.ignoreCase && !caseHover) {
            val gw = GuiFont.width(this.font, "Aa")
            graphics.text(this.font, GuiFont.text("Aa"), caseX + (SMALL_BTN - gw) / 2, btnY + (SMALL_BTN - 8) / 2, AlpakaStyle.accent(), false)
        } else {
            AlpakaStyle.iconButton(graphics, this.font, caseX, btnY, SMALL_BTN, "Aa", caseHover, true, false)
        }

        // The bin stays out of the way until the row is hovered, as in the command wheel editor.
        if (hovered) {
            val delX = rowDelX(x, w)
            val delHover = inside(mouseX, mouseY, delX, btnY, SMALL_BTN, SMALL_BTN)
            AlpakaStyle.trashButton(graphics, delX, btnY, SMALL_BTN, delHover)
            if (delHover) hoverHint = "Delete this filter."
        }
    }

    /**
     * The text field, drawn here rather than with [AlpakaStyle.textField] because it has a caret
     * that can move and a selection: a pasted message often needs a number cut out of it first.
     */
    private fun drawField(graphics: GuiGraphicsExtractor, f: Frame, mouseX: Int, mouseY: Int) {
        val x = f.listX
        val y = f.inputY
        val w = f.inputW
        val h = AlpakaStyle.BUTTON_H
        val hovered = inside(mouseX, mouseY, x, y, w, h)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.FIELD_RADIUS, if (hovered && !inputFocused) AlpakaStyle.ROW_HOVER else AlpakaStyle.FIELD)
        ModernGuiUtils.drawRect(graphics, x + 3, y + h - 1, w - 6, 1, if (inputFocused) AlpakaStyle.accent() else AlpakaStyle.LINE)

        val textY = y + (h - 8) / 2
        val text = input.text
        if (text.isEmpty() && !inputFocused) {
            graphics.text(this.font, GuiFont.text(if (newMode == CustomMessageFilter.Mode.REGEX) "Regex" else "Message to hide"),
                x + 5, textY, AlpakaStyle.TEXT_MUTED, false)
        }

        val start = visibleStart(text, w - 10)
        graphics.enableScissor(x + 2, y, x + w - 2, y + h)
        if (inputFocused && input.hasSelection()) {
            val sx = x + 5 + GuiFont.width(this.font, text.substring(start, max(input.selectionStart, start)))
            val ex = x + 5 + GuiFont.width(this.font, text.substring(start, max(input.selectionEnd, start)))
            ModernGuiUtils.drawRect(graphics, sx, textY - 1, ex - sx, 10, AlpakaStyle.accentDim())
        }
        graphics.text(this.font, GuiFont.text(text.substring(start)), x + 5, textY, AlpakaStyle.TEXT, false)
        if (inputFocused && (System.currentTimeMillis() - caretFrom) / 500 % 2 == 0L) {
            val caretX = x + 5 + GuiFont.width(this.font, text.substring(start, max(start, input.caret)))
            ModernGuiUtils.drawRect(graphics, caretX, textY - 1, 1, 10, AlpakaStyle.TEXT)
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

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = AlpakaStyle.inside(mx.toDouble(), my.toDouble(), x, y, w, h)

    // Positions shared by drawing and clicking.
    private fun rowY(f: Frame, index: Int) = f.listY + index * (ITEM_H + ITEM_GAP) - scrollY.toInt()
    private fun doneX(f: Frame) = f.winX + f.winW - DONE_W - AlpakaStyle.PAD
    private fun doneY(f: Frame) = f.winY + (AlpakaStyle.HEADER_H - AlpakaStyle.BUTTON_H) / 2
    private fun modeBtnX(f: Frame) = f.listX + f.inputW + 6
    private fun addBtnX(f: Frame) = modeBtnX(f) + MODE_W + 6
    private fun rowDelX(x: Int, w: Int) = x + w - SMALL_BTN - 3
    private fun rowCaseX(x: Int, w: Int) = rowDelX(x, w) - SMALL_BTN - 4
    private fun rowModeX(x: Int, w: Int) = rowCaseX(x, w) - ROW_MODE_W - 4

    // ------------------------------------------------------------------ input

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick)
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val f = frame()

        if (inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)) {
            playPloppSound()
            onClose()
            return true
        }

        if (inside(mouseX, mouseY, f.listX, f.inputY, f.inputW, AlpakaStyle.BUTTON_H)) {
            inputFocused = true
            caretFrom = System.currentTimeMillis()
            val offset = GuiFont.width(this.font, input.text.substring(0, visibleStart(input.text, f.inputW - 10)))
            input.setCaretFromX(mouseX - f.listX - 5 + offset) { GuiFont.width(this.font, it) }
            return true
        }
        if (inside(mouseX, mouseY, modeBtnX(f), f.inputY, MODE_W, AlpakaStyle.BUTTON_H)) {
            playPloppSound()
            newMode = newMode.next()
            return true
        }
        if (inside(mouseX, mouseY, addBtnX(f), f.inputY, ADD_W, AlpakaStyle.BUTTON_H)) {
            submit()
            return true
        }

        if (inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)) {
            val x = f.listX
            val w = f.listW
            for (i in filters.indices) {
                val y = rowY(f, i)
                if (!inside(mouseX, mouseY, x, y, w, ITEM_H)) continue
                val filter = filters[i]
                val btnY = y + (ITEM_H - SMALL_BTN) / 2
                when {
                    inside(mouseX, mouseY, rowDelX(x, w), btnY, SMALL_BTN, SMALL_BTN) -> {
                        filters.removeAt(i)
                        if (editing == i) stopEditing() else if (editing > i) editing--
                    }
                    inside(mouseX, mouseY, rowCaseX(x, w), btnY, SMALL_BTN, SMALL_BTN) -> filter.ignoreCase = !filter.ignoreCase
                    inside(mouseX, mouseY, rowModeX(x, w), btnY, ROW_MODE_W, SMALL_BTN) -> filter.mode = filter.mode.next()
                    mouseX <= x + 6 + SWITCH_W + 4 -> filter.enabled = !filter.enabled
                    // The row itself: edit it in the field, or stop editing when it is already there.
                    editing == i -> stopEditing()
                    else -> {
                        editing = i
                        input.setText(filter.text)
                        newMode = filter.mode
                        inputFocused = true
                        caretFrom = System.currentTimeMillis()
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
            caretFrom = System.currentTimeMillis()
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
            if (input.keyPressed(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown())) {
                caretFrom = System.currentTimeMillis()
                return true
            }
        }
        return super.keyPressed(event)
    }

    companion object {
        private const val WIN_W = 520
        private const val WIN_H = 320
        private const val MIN_W = 320
        private const val MIN_H = 200
        /** Space kept free around the window, as the config screen keeps. */
        private const val MARGIN = 8
        private const val DONE_W = 56
        private const val MODE_W = 76
        private const val ADD_W = 50

        private const val ITEM_H = 24
        private const val ITEM_GAP = 3
        private const val SMALL_BTN = 16
        private const val ROW_MODE_W = 70
        private const val SWITCH_W = 20
        private const val SWITCH_H = 11

        /** Long enough for any chat line Hypixel sends, and for a regex written around one. */
        private const val MAX_TEXT = 512
    }
}
