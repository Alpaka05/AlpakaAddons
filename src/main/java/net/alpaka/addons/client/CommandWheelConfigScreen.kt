package net.alpaka.addons.client

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.features.sound.CustomSoundFeature
import net.alpaka.addons.features.wheel.CommandWheelPages
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where the quick command wheel's commands are edited.
 *
 * The list is shown page by page, the way the wheel shows it. A command can be dragged to another
 * spot - within its page, onto another page, or onto the "new page" slot at the end - and the two
 * small arrows on a row move it a page back or forward without dragging. New commands go to the
 * page picked in the bottom row, or to the first page with room when that is left on "Auto"; a page
 * that is full hands the command on to the next one (see [CommandWheelPages]).
 *
 * Drawn with [AlpakaStyle], the config menu's look: slim rows that light up on hover, their move
 * and remove buttons only shown then, the accent for whatever is being dropped on.
 */
class CommandWheelConfigScreen(private val parent: Screen) : Screen(Component.literal("Configure Quick Commands")) {

    private var newCommandInput: String = "/"
    private var inputFocused: Boolean = false

    /** Where a new command goes: -1 for "Auto", a page index, or the page count for "New page". */
    private var addTargetPage: Int = -1

    private var scrollY: Double = 0.0
    private var targetScrollY: Double = 0.0
    private var maxScrollY: Double = 0.0

    // Drag state. A press on a row arms a drag; it becomes one after the mouse has moved a little,
    // so a plain click on a row does nothing surprising.
    private var pressPage = -1
    private var pressIndex = -1
    private var pressX = 0.0
    private var pressY = 0.0
    private var dragging = false
    private var dragGrabOffsetY = 0
    private var dropPage = -1
    private var dropIndex = -1
    private var dropAllowed = false

    private var lastFrameMs = System.currentTimeMillis()
    private var openTimeMs = 0L
    private var caretFrom = 0L

    // ----------------------------------------------------------------- layout

    private class Frame(
        val winX: Int, val winY: Int, val winW: Int, val winH: Int,
        val listX: Int, val listY: Int, val listW: Int, val listH: Int,
        /** The new-command field, and the page, add and reset buttons: one row, or two when narrow. */
        val inputY: Int, val inputW: Int, val buttonsY: Int, val buttonsX: Int,
    )

    /**
     * The window clamped to the screen, with the list taking whatever height is left.
     *
     * It used to be a fixed 470 by 372, which at 1080p with Auto GUI scale (480 by 270) put the header
     * and the bottom row off screen and handed the renderer a scissor above the screen's top edge. Too
     * narrow for the bottom row, the field gets a line of its own above the buttons.
     */
    private fun frame(): Frame {
        val winW = (this.width - MARGIN * 2).coerceIn(MIN_W, WIN_W)
        val winH = (this.height - MARGIN * 2).coerceIn(MIN_H, WIN_H)
        val winX = (this.width - winW) / 2
        val winY = maxOf(MARGIN / 2, (this.height - winH) / 2)
        val listX = winX + AlpakaStyle.PAD + 4
        val listY = winY + AlpakaStyle.HEADER_H + 6
        val listW = winW - 2 * (AlpakaStyle.PAD + 4)

        val buttonsW = PAGE_BTN_W + ADD_W + RESET_W + 12
        val split = listW < MIN_INPUT_W + 6 + buttonsW
        val bottom = winY + winH - AlpakaStyle.PAD - AlpakaStyle.BUTTON_H
        val inputY = if (split) bottom - AlpakaStyle.BUTTON_H - 6 else bottom
        val inputW = if (split) listW else listW - buttonsW - 6
        val buttonsX = listX + listW - buttonsW
        val listH = inputY - 16 - listY
        return Frame(winX, winY, winW, winH, listX, listY, listW, listH, inputY, inputW, bottom, buttonsX)
    }

    private enum class Kind { HEADER, ITEM, NEW_PAGE }

    /** One line of the list, positioned relative to the top of the scrolling content. */
    private class Row(val kind: Kind, val page: Int, val index: Int, val y: Int, val height: Int, val command: String?)

    private var rows: List<Row> = emptyList()
    private var contentHeight = 0

    /**
     * Lays the pages out as rows. While a command is being dragged it is left out, so the list
     * closes up around it and shows where it would land; a "new page" slot then follows the last page.
     */
    private fun buildRows(pages: List<List<String>>) {
        val list = ArrayList<Row>()
        var y = 6
        for (p in pages.indices) {
            list.add(Row(Kind.HEADER, p, -1, y, PAGE_HEADER_H, null))
            y += PAGE_HEADER_H
            for (i in pages[p].indices) {
                if (dragging && p == pressPage && i == pressIndex) continue
                list.add(Row(Kind.ITEM, p, i, y, ITEM_H, pages[p][i]))
                y += ITEM_H + ITEM_GAP
            }
            y += PAGE_GAP
        }
        if (dragging) {
            list.add(Row(Kind.NEW_PAGE, pages.size, 0, y, ITEM_H, null))
            y += ITEM_H + PAGE_GAP
        }
        rows = list
        contentHeight = y
    }

    private fun contentY(frame: Frame, screenY: Int): Int = screenY - frame.listY + scrollY.toInt()

    /**
     * Where a dragged command would land for the given mouse position: the page whose section the
     * mouse is in, and the slot before the first row whose middle lies below the mouse.
     */
    private fun updateDropTarget(frame: Frame, pages: List<List<String>>, mouseY: Int) {
        val cy = contentY(frame, mouseY)
        var targetPage = 0
        for (row in rows) {
            if ((row.kind == Kind.HEADER || row.kind == Kind.NEW_PAGE) && row.y - PAGE_GAP / 2 <= cy) targetPage = row.page
        }
        var index = 0
        for (row in rows) {
            if (row.kind == Kind.ITEM && row.page == targetPage && row.y + row.height / 2 < cy) index++
        }
        dropPage = targetPage
        dropIndex = index
        dropAllowed = targetPage == pressPage || targetPage >= pages.size || pages[targetPage].size < CommandWheelPages.MAX_PER_PAGE
    }

    /** The y (relative to content) of the insertion marker for the current drop target. */
    private fun dropMarkerY(): Int {
        var y = -1
        var seen = 0
        for (row in rows) {
            if (row.kind == Kind.HEADER && row.page == dropPage) y = row.y + row.height
            if (row.kind == Kind.NEW_PAGE && row.page == dropPage) y = row.y
            if (row.kind == Kind.ITEM && row.page == dropPage) {
                if (seen == dropIndex) return row.y - ITEM_GAP / 2 - 1
                seen++
                y = row.y + row.height + ITEM_GAP / 2 - 1
            }
        }
        return y
    }

    private fun playPloppSound() {
        try {
            CustomSoundFeature.playButtonClickSound()
        } catch (_: Throwable) {}
    }

    override fun onClose() {
        this.minecraft?.gui?.setScreen(this.parent)
    }

    // -------------------------------------------------------------- rendering

    override fun init() {
        openTimeMs = System.currentTimeMillis()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val now = System.currentTimeMillis()
        val deltaSec = min(0.1f, (now - lastFrameMs) / 1000f)
        lastFrameMs = now

        AlpakaStyle.backdrop(graphics, this.width, this.height)
        val pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height)

        val f = frame()
        val pages = CommandWheelPages.pages()
        val accent = ModernGuiUtils.getAccentColor()

        AlpakaStyle.panel(graphics, f.winX, f.winY, f.winW, f.winH)
        AlpakaStyle.header(graphics, this.font, "Quick Commands", f.winX, f.winY, f.winW)

        val hoverDone = inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H, "Done", hoverDone, ButtonKind.PRIMARY)

        buildRows(pages)
        maxScrollY = max(0.0, (contentHeight - f.listH).toDouble())
        targetScrollY = targetScrollY.coerceIn(0.0, maxScrollY)
        scrollY += (targetScrollY - scrollY) * min(1.0, deltaSec * 14.0)

        if (dragging) updateDropTarget(f, pages, mouseY)

        val inList = inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)
        graphics.enableScissor(f.listX, f.listY, f.listX + f.listW, f.listY + f.listH)

        val rowX = f.listX
        val rowW = f.listW - SCROLLBAR_ROOM
        for (row in rows) {
            val y = f.listY + row.y - scrollY.toInt()
            if (y + row.height < f.listY || y > f.listY + f.listH) continue
            when (row.kind) {
                Kind.HEADER -> {
                    val count = pages[row.page].size
                    AlpakaStyle.groupLabel(graphics, this.font, "Page ${row.page + 1}", rowX + 2, y + 5)
                    val countText = "$count / ${CommandWheelPages.MAX_PER_PAGE}"
                    val full = count >= CommandWheelPages.MAX_PER_PAGE
                    graphics.text(this.font, GuiFont.text(countText), rowX + rowW - GuiFont.width(this.font, countText) - 4, y + 5,
                        if (full) AlpakaStyle.DANGER else AlpakaStyle.TEXT_MUTED, false)
                }
                Kind.ITEM -> drawItemRow(graphics, row, rowX, y, rowW, mouseX, mouseY, inList && !dragging)
                Kind.NEW_PAGE -> {
                    val active = dropPage == row.page
                    ModernGuiUtils.drawRoundedOutline(graphics, rowX, y, rowW, row.height, 5,
                        if (active) accent else AlpakaStyle.DIVIDER)
                    ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Drop here for a new page"), rowX + rowW / 2, y + (row.height - 8) / 2,
                        if (active) accent else AlpakaStyle.TEXT_MUTED)
                }
            }
        }

        // The insertion marker while dragging: an accent line where the command would go, red
        // where the page is already full and the drop would be refused.
        if (dragging && dropPage >= 0) {
            val markerY = dropMarkerY()
            if (markerY >= 0) {
                val color = if (dropAllowed) accent else AlpakaStyle.DANGER
                ModernGuiUtils.drawRoundedRect(graphics, rowX + 4, f.listY + markerY - scrollY.toInt(), rowW - 8, 2, 1, color)
            }
        }

        graphics.disableScissor()

        // A thin scrollbar when the pages do not fit.
        if (maxScrollY > 0) {
            val trackX = f.listX + f.listW - 2
            val thumbH = max(16, (f.listH * f.listH / contentHeight.toDouble()).toInt())
            val thumbY = f.listY + ((f.listH - thumbH) * (scrollY / maxScrollY)).toInt()
            ModernGuiUtils.drawRoundedRect(graphics, trackX, thumbY, 2, thumbH, 1, AlpakaStyle.DIVIDER)
        }

        // The dragged command follows the mouse as a copy of its row.
        if (dragging && pressPage in pages.indices && pressIndex in pages[pressPage].indices) {
            val ghostY = mouseY - dragGrabOffsetY
            ModernGuiUtils.drawPanelShadow(graphics, rowX, ghostY, rowW, ITEM_H, 5, 0.8f)
            ModernGuiUtils.drawRoundedRect(graphics, rowX, ghostY, rowW, ITEM_H, 5, AlpakaStyle.ROW_SELECTED)
            ModernGuiUtils.drawRoundedOutline(graphics, rowX, ghostY, rowW, ITEM_H, 5, ModernGuiUtils.getAccentDimColor())
            drawGrip(graphics, rowX, ghostY, accent)
            graphics.text(this.font, GuiFont.text(pages[pressPage][pressIndex]), rowX + 20, ghostY + (ITEM_H - 8) / 2, AlpakaStyle.TEXT, false)
        }

        // Footer: new command, target page, add, reset.
        ModernGuiUtils.drawRect(graphics, f.winX + AlpakaStyle.PAD, f.inputY - 8, f.winW - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE)
        val hoverInput = inside(mouseX, mouseY, inputX(f), f.inputY, f.inputW, AlpakaStyle.BUTTON_H)
        AlpakaStyle.textField(graphics, this.font, inputX(f), f.inputY, f.inputW, AlpakaStyle.BUTTON_H,
            newCommandInput, "/command", inputFocused, hoverInput, caretFrom)

        val buttonsY = f.buttonsY
        val hoverPage = inside(mouseX, mouseY, pageBtnX(f), buttonsY, PAGE_BTN_W, AlpakaStyle.BUTTON_H)
        drawPagePicker(graphics, pageBtnX(f), buttonsY, addTargetLabel(pages.size), hoverPage)

        val hoverAdd = inside(mouseX, mouseY, addBtnX(f), buttonsY, ADD_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, addBtnX(f), buttonsY, ADD_W, AlpakaStyle.BUTTON_H, "Add", hoverAdd, ButtonKind.PRIMARY)

        val hoverReset = inside(mouseX, mouseY, resetBtnX(f), buttonsY, RESET_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, resetBtnX(f), buttonsY, RESET_W, AlpakaStyle.BUTTON_H,
            "Reset", hoverReset, ButtonKind.DANGER)

        AlpakaStyle.endOpen(graphics, pushed)
    }

    /** The page a new command goes to, as the config's dropdowns show a choice: value and chevron. */
    private fun drawPagePicker(graphics: GuiGraphicsExtractor, x: Int, y: Int, label: String, hover: Boolean) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, PAGE_BTN_W, AlpakaStyle.BUTTON_H, 3, if (hover) AlpakaStyle.ROW_HOVER else AlpakaStyle.FIELD)
        graphics.text(this.font, GuiFont.text(label), x + 6, y + (AlpakaStyle.BUTTON_H - 8) / 2, AlpakaStyle.TEXT, false)
        val glyph = "›"
        graphics.text(this.font, GuiFont.text(glyph), x + PAGE_BTN_W - 6 - GuiFont.width(this.font, glyph), y + (AlpakaStyle.BUTTON_H - 8) / 2,
            if (hover) ModernGuiUtils.getAccentColor() else AlpakaStyle.TEXT_MUTED, false)
    }

    private fun drawGrip(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        for (k in 0..2) {
            ModernGuiUtils.drawRect(graphics, x + 7, y + ITEM_H / 2 - 3 + k * 3, 6, 1, color)
        }
    }

    private fun drawItemRow(graphics: GuiGraphicsExtractor, row: Row, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int, hoverable: Boolean) {
        val hovered = hoverable && inside(mouseX, mouseY, x, y, w, row.height)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, row.height, 5, if (hovered) AlpakaStyle.ROW_HOVER else AlpakaStyle.ROW)

        drawGrip(graphics, x, y, if (hovered) AlpakaStyle.TEXT_MUTED else AlpakaStyle.DIVIDER)
        graphics.text(this.font, GuiFont.text(row.command ?: ""), x + 20, y + (row.height - 8) / 2, AlpakaStyle.TEXT, false)

        // Move to the previous page (dimmed on the first), move to the next page (a new one after
        // the last), remove. Quiet until the row is hovered.
        if (!hovered) return
        val prevX = prevBtnX(x, w)
        val nextX = nextBtnX(x, w)
        val delX = delBtnX(x, w)
        val btnY = y + (row.height - SMALL_BTN) / 2
        val canPrev = row.page > 0
        AlpakaStyle.iconButton(graphics, this.font, prevX, btnY, SMALL_BTN, "‹", inside(mouseX, mouseY, prevX, btnY, SMALL_BTN, SMALL_BTN), canPrev, false)
        AlpakaStyle.iconButton(graphics, this.font, nextX, btnY, SMALL_BTN, "›", inside(mouseX, mouseY, nextX, btnY, SMALL_BTN, SMALL_BTN), true, false)
        AlpakaStyle.iconButton(graphics, this.font, delX, btnY, SMALL_BTN, "✕", inside(mouseX, mouseY, delX, btnY, SMALL_BTN, SMALL_BTN), true, true)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = AlpakaStyle.inside(mx.toDouble(), my.toDouble(), x, y, w, h)

    private fun addTargetLabel(pageCount: Int): String = when {
        addTargetPage < 0 -> "Auto"
        addTargetPage >= pageCount -> "New page"
        else -> "Page ${addTargetPage + 1}"
    }

    // Positions of the bottom row and header controls, shared by drawing and clicking.
    private fun doneX(f: Frame) = f.winX + f.winW - DONE_W - AlpakaStyle.PAD
    private fun doneY(f: Frame) = f.winY + (AlpakaStyle.HEADER_H - AlpakaStyle.BUTTON_H) / 2
    private fun inputX(f: Frame) = f.listX
    private fun pageBtnX(f: Frame) = f.buttonsX
    private fun addBtnX(f: Frame) = pageBtnX(f) + PAGE_BTN_W + 6
    private fun resetBtnX(f: Frame) = addBtnX(f) + ADD_W + 6

    /** For the screenshot tour: the middle of the Reset button, to click it as a player would. */
    fun devResetPoint(): IntArray {
        val f = frame()
        return intArrayOf(resetBtnX(f) + RESET_W / 2, f.buttonsY + AlpakaStyle.BUTTON_H / 2)
    }
    private fun delBtnX(rowX: Int, rowW: Int) = rowX + rowW - SMALL_BTN - 3
    private fun nextBtnX(rowX: Int, rowW: Int) = delBtnX(rowX, rowW) - SMALL_BTN - 4
    private fun prevBtnX(rowX: Int, rowW: Int) = nextBtnX(rowX, rowW) - SMALL_BTN

    // ------------------------------------------------------------------ input

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick)
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val f = frame()
        val pages = CommandWheelPages.pages()

        if (inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)) {
            playPloppSound()
            this.onClose()
            return true
        }

        // Bottom row: the field, and the buttons on the same line or the one below it.
        if (inside(mouseX, mouseY, inputX(f), f.inputY, f.inputW, AlpakaStyle.BUTTON_H)) {
            inputFocused = true
            caretFrom = System.currentTimeMillis()
            return true
        }
        if (mouseY in f.buttonsY until (f.buttonsY + AlpakaStyle.BUTTON_H)) {
            inputFocused = false
            if (mouseX in pageBtnX(f)..(pageBtnX(f) + PAGE_BTN_W)) {
                playPloppSound()
                // Auto -> Page 1 .. Page n -> New page -> Auto
                addTargetPage = if (addTargetPage >= pages.size) -1 else addTargetPage + 1
                return true
            }
            if (mouseX in addBtnX(f)..(addBtnX(f) + ADD_W)) {
                addCurrentInput()
                return true
            }
            if (mouseX in resetBtnX(f)..(resetBtnX(f) + RESET_W)) {
                playPloppSound()
                // Reset wipes every command, so it asks first, in the dialog every such action uses.
                this.minecraft?.gui?.setScreen(AlpakaConfirmScreen(this, "Reset Quick Commands",
                    "Replaces every quick command on every page with the default set.", "Reset", true) {
                    CommandWheelPages.reset()
                    addTargetPage = -1
                })
                return true
            }
        } else {
            inputFocused = false
        }

        // List rows.
        if (mouseX in f.listX..(f.listX + f.listW) && mouseY in f.listY..(f.listY + f.listH)) {
            val rowX = f.listX
            val rowW = f.listW - SCROLLBAR_ROOM
            for (row in rows) {
                if (row.kind != Kind.ITEM) continue
                val y = f.listY + row.y - scrollY.toInt()
                if (mouseY !in y..(y + row.height) || mouseX !in rowX..(rowX + rowW)) continue

                val btnY = y + (row.height - SMALL_BTN) / 2
                if (mouseY in btnY..(btnY + SMALL_BTN)) {
                    val delX = delBtnX(rowX, rowW)
                    val nextX = nextBtnX(rowX, rowW)
                    val prevX = prevBtnX(rowX, rowW)
                    if (mouseX in delX..(delX + SMALL_BTN)) {
                        playPloppSound()
                        CommandWheelPages.remove(row.page, row.index)
                        clampAddTarget()
                        return true
                    }
                    if (mouseX in nextX..(nextX + SMALL_BTN)) {
                        if (CommandWheelPages.shiftToPage(row.page, row.index, 1)) playPloppSound()
                        return true
                    }
                    if (mouseX in prevX..(prevX + SMALL_BTN)) {
                        if (row.page > 0 && CommandWheelPages.shiftToPage(row.page, row.index, -1)) playPloppSound()
                        return true
                    }
                }

                // Anywhere else on the row arms a drag.
                pressPage = row.page
                pressIndex = row.index
                pressX = event.x()
                pressY = event.y()
                dragGrabOffsetY = mouseY - y
                return true
            }
        }

        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (pressPage >= 0) {
            if (!dragging && (abs(event.x() - pressX) > DRAG_THRESHOLD || abs(event.y() - pressY) > DRAG_THRESHOLD)) {
                dragging = true
            }
            return true
        }
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0 && pressPage >= 0) {
            if (dragging && dropPage >= 0 && dropAllowed) {
                // dropIndex counts rows with the dragged one taken out, which is exactly the index
                // after the move's own removal; on the same page the move corrects for that itself,
                // so hand it the index as it would have been before the removal.
                var toIndex = dropIndex
                if (dropPage == pressPage && dropIndex >= pressIndex) toIndex++
                if (CommandWheelPages.move(pressPage, pressIndex, dropPage, toIndex)) playPloppSound()
                clampAddTarget()
            }
            pressPage = -1
            pressIndex = -1
            dragging = false
            dropPage = -1
            return true
        }
        return super.mouseReleased(event)
    }

    private fun clampAddTarget() {
        val count = CommandWheelPages.pageCount()
        if (addTargetPage > count) addTargetPage = count
    }

    private fun addCurrentInput() {
        var trimmed = newCommandInput.trim()
        if (trimmed.isEmpty() || trimmed == "/") return
        if (!trimmed.startsWith("/")) trimmed = "/$trimmed"

        if (!CommandWheelPages.contains(trimmed)) {
            playPloppSound()
            val wanted = if (addTargetPage < 0) null else addTargetPage
            CommandWheelPages.add(trimmed, wanted)
            // "New page" has been used up; the next command should join it rather than open another.
            if (addTargetPage >= CommandWheelPages.pageCount()) addTargetPage = CommandWheelPages.pageCount() - 1
        }
        newCommandInput = "/"
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
            if (codePoint >= 32 && codePoint != 127 && newCommandInput.length < 35) {
                newCommandInput += codePoint.toChar()
            }
            caretFrom = System.currentTimeMillis()
            return true
        }
        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (inputFocused) {
            when (event.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (newCommandInput.isNotEmpty()) newCommandInput = newCommandInput.substring(0, newCommandInput.length - 1)
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    addCurrentInput()
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    inputFocused = false
                    return true
                }
            }
        }
        return super.keyPressed(event)
    }

    companion object {
        private const val WIN_W = 360
        private const val WIN_H = 300
        private const val MIN_W = 260
        private const val MIN_H = 180
        /** Space kept free around the window, as the config screen keeps. */
        private const val MARGIN = 8
        private const val DONE_W = 44
        private const val MIN_INPUT_W = 110
        private const val PAGE_BTN_W = 62
        private const val ADD_W = 40
        private const val RESET_W = 44
        private const val SCROLLBAR_ROOM = 6

        private const val PAGE_HEADER_H = 17
        private const val ITEM_H = 20
        private const val ITEM_GAP = 3
        private const val PAGE_GAP = 6
        private const val SMALL_BTN = 14

        /** How far the mouse has to travel after a press before a row starts to follow it. */
        private const val DRAG_THRESHOLD = 4.0
    }
}
