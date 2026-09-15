package net.alpaka.addons.client

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
 * Drawn with the same pieces as the config screen: rounded panels with hairline borders, the menu
 * font without shadows, the accent colour for whatever is selected or being dropped on.
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

    // ----------------------------------------------------------------- layout

    private class Frame(val winX: Int, val winY: Int, val listX: Int, val listY: Int, val listW: Int, val listH: Int, val bottomY: Int)

    private fun frame(): Frame {
        val winX = (this.width - WIN_W) / 2
        val winY = (this.height - WIN_H) / 2
        val listX = winX + 16
        val listY = winY + HEADER_H + 10
        val listW = WIN_W - 32
        val bottomY = winY + WIN_H - 16 - BOTTOM_H
        val listH = bottomY - 10 - listY
        return Frame(winX, winY, listX, listY, listW, listH, bottomY)
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
        val pages = CommandWheelPages.pages()
        val accent = ModernGuiUtils.getAccentColor()

        ModernGuiUtils.drawPanelShadow(graphics, f.winX, f.winY, WIN_W, WIN_H, ModernGuiUtils.PANEL_RADIUS, 1.0f)
        ModernGuiUtils.drawRoundedPanel(graphics, f.winX, f.winY, WIN_W, WIN_H, ModernGuiUtils.PANEL_RADIUS,
            ModernGuiUtils.COLOR_PANEL_BG, ModernGuiUtils.COLOR_CARD_BORDER)

        // Header band with the accent rule, as on the other menus.
        val r = ModernGuiUtils.PANEL_RADIUS
        ModernGuiUtils.drawRoundedRect(graphics, f.winX + 1, f.winY + 1, WIN_W - 2, HEADER_H - 1, r - 1, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawRect(graphics, f.winX + 1, f.winY + HEADER_H - r, WIN_W - 2, r, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawRect(graphics, f.winX + 1, f.winY + HEADER_H - 1, WIN_W - 2, 1, accent)
        graphics.text(this.font, GuiFont.text("Quick Command Settings"), f.winX + 16, f.winY + 15, ModernGuiUtils.COLOR_TEXT_PRIMARY, false)

        val hoverClose = mouseX in doneX(f)..(doneX(f) + DONE_W) && mouseY in doneY(f)..(doneY(f) + DONE_H)
        ModernGuiUtils.drawModernButton(graphics, this.font, doneX(f), doneY(f), DONE_W, DONE_H, "Done", hoverClose, false)

        // The list: pages as sections, each command a row.
        ModernGuiUtils.drawRoundedPanel(graphics, f.listX, f.listY, f.listW, f.listH, ModernGuiUtils.WIDGET_RADIUS + 2,
            ModernGuiUtils.COLOR_SIDEBAR_BG, ModernGuiUtils.COLOR_CARD_BORDER)

        buildRows(pages)
        maxScrollY = max(0.0, (contentHeight - f.listH).toDouble())
        targetScrollY = targetScrollY.coerceIn(0.0, maxScrollY)
        scrollY += (targetScrollY - scrollY) * min(1.0, deltaSec * 14.0)

        if (dragging) updateDropTarget(f, pages, mouseY)

        val inList = mouseX in f.listX..(f.listX + f.listW) && mouseY in f.listY..(f.listY + f.listH)
        graphics.enableScissor(f.listX + 1, f.listY + 1, f.listX + f.listW - 1, f.listY + f.listH - 1)

        val rowX = f.listX + 8
        val rowW = f.listW - 16
        for (row in rows) {
            val y = f.listY + row.y - scrollY.toInt()
            if (y + row.height < f.listY || y > f.listY + f.listH) continue
            when (row.kind) {
                Kind.HEADER -> {
                    val count = pages[row.page].size
                    graphics.text(this.font, GuiFont.text("Page ${row.page + 1}"), rowX + 2, y + 6, accent, false)
                    val countText = "$count / ${CommandWheelPages.MAX_PER_PAGE}"
                    val full = count >= CommandWheelPages.MAX_PER_PAGE
                    graphics.text(this.font, GuiFont.text(countText), rowX + rowW - GuiFont.width(this.font, countText) - 2, y + 6,
                        if (full) ModernGuiUtils.COLOR_TOGGLE_OFF_TEXT else ModernGuiUtils.COLOR_TEXT_DARK, false)
                }
                Kind.ITEM -> drawItemRow(graphics, f, row, rowX, y, rowW, mouseX, mouseY, inList && !dragging, pages.size)
                Kind.NEW_PAGE -> {
                    val active = dropPage == row.page
                    ModernGuiUtils.drawRoundedOutline(graphics, rowX, y, rowW, row.height, ModernGuiUtils.WIDGET_RADIUS + 1,
                        if (active) accent else ModernGuiUtils.COLOR_CARD_BORDER)
                    ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Drop here for a new page"), rowX + rowW / 2, y + (row.height - 8) / 2,
                        if (active) accent else ModernGuiUtils.COLOR_TEXT_DARK)
                }
            }
        }

        // The insertion marker while dragging: an accent line where the command would go, red
        // where the page is already full and the drop would be refused.
        if (dragging && dropPage >= 0) {
            val markerY = dropMarkerY()
            if (markerY >= 0) {
                val color = if (dropAllowed) accent else ModernGuiUtils.COLOR_TOGGLE_OFF_TEXT
                ModernGuiUtils.drawRoundedRect(graphics, rowX + 4, f.listY + markerY - scrollY.toInt(), rowW - 8, 2, 1, color)
            }
        }

        graphics.disableScissor()

        // The dragged command follows the mouse as a translucent copy of its row.
        if (dragging && pressPage in pages.indices && pressIndex in pages[pressPage].indices) {
            val ghostY = mouseY - dragGrabOffsetY
            ModernGuiUtils.drawRoundedPanel(graphics, rowX, ghostY, rowW, ITEM_H, ModernGuiUtils.WIDGET_RADIUS + 1,
                0xE02D2D2D.toInt(), accent)
            graphics.text(this.font, GuiFont.text(pages[pressPage][pressIndex]), rowX + 22, ghostY + (ITEM_H - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY, false)
        }

        // Bottom row: new command, target page, add, reset.
        val hoverInput = mouseX in inputX(f)..(inputX(f) + INPUT_W) && mouseY in f.bottomY..(f.bottomY + BOTTOM_H)
        ModernGuiUtils.drawModernTextField(graphics, this.font, inputX(f), f.bottomY, INPUT_W, BOTTOM_H,
            newCommandInput, "/command", inputFocused, hoverInput)

        val hoverPage = mouseX in pageBtnX(f)..(pageBtnX(f) + PAGE_BTN_W) && mouseY in f.bottomY..(f.bottomY + BOTTOM_H)
        ModernGuiUtils.drawModernButton(graphics, this.font, pageBtnX(f), f.bottomY, PAGE_BTN_W, BOTTOM_H, addTargetLabel(pages.size), hoverPage, false)

        val hoverAdd = mouseX in addBtnX(f)..(addBtnX(f) + ADD_W) && mouseY in f.bottomY..(f.bottomY + BOTTOM_H)
        ModernGuiUtils.drawModernButton(graphics, this.font, addBtnX(f), f.bottomY, ADD_W, BOTTOM_H, "+ Add", hoverAdd, true)

        val hoverReset = mouseX in resetBtnX(f)..(resetBtnX(f) + RESET_W) && mouseY in f.bottomY..(f.bottomY + BOTTOM_H)
        ModernGuiUtils.drawModernButton(graphics, this.font, resetBtnX(f), f.bottomY, RESET_W, BOTTOM_H, "Reset", hoverReset, false)
    }

    private fun drawItemRow(graphics: GuiGraphicsExtractor, f: Frame, row: Row, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int, hoverable: Boolean, pageCount: Int) {
        val hovered = hoverable && mouseX in x..(x + w) && mouseY in y..(y + row.height)
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, row.height, ModernGuiUtils.WIDGET_RADIUS + 1,
            if (hovered) ModernGuiUtils.COLOR_CARD_BG_HOVER else ModernGuiUtils.COLOR_CARD_BG,
            if (hovered) ModernGuiUtils.getAccentDimColor() else ModernGuiUtils.COLOR_CARD_BORDER)

        // Grip marks: three short bars, the usual sign for "drag me".
        val gripColor = if (hovered) ModernGuiUtils.COLOR_TEXT_MUTED else ModernGuiUtils.COLOR_TEXT_DARK
        for (k in 0..2) {
            ModernGuiUtils.drawRect(graphics, x + 8, y + row.height / 2 - 3 + k * 3, 7, 1, gripColor)
        }

        graphics.text(this.font, GuiFont.text(row.command ?: ""), x + 22, y + (row.height - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY, false)

        val textY = y + (row.height - 8) / 2
        // Move to the previous page (dimmed on the first), move to the next page (a new one after the last).
        val prevX = prevBtnX(x, w)
        val nextX = nextBtnX(x, w)
        val delX = delBtnX(x, w)
        val btnY = y + (row.height - SMALL_BTN) / 2
        val canPrev = row.page > 0
        drawSmallButton(graphics, prevX, btnY, "‹", hoverable && canPrev && mouseX in prevX..(prevX + SMALL_BTN) && mouseY in btnY..(btnY + SMALL_BTN),
            if (canPrev) ModernGuiUtils.COLOR_TEXT_PRIMARY else ModernGuiUtils.COLOR_TEXT_DARK, false)
        drawSmallButton(graphics, nextX, btnY, "›", hoverable && mouseX in nextX..(nextX + SMALL_BTN) && mouseY in btnY..(btnY + SMALL_BTN),
            ModernGuiUtils.COLOR_TEXT_PRIMARY, false)
        drawSmallButton(graphics, delX, btnY, "✕", hoverable && mouseX in delX..(delX + SMALL_BTN) && mouseY in btnY..(btnY + SMALL_BTN),
            0xFFFCA5A5.toInt(), true)
    }

    private fun drawSmallButton(graphics: GuiGraphicsExtractor, x: Int, y: Int, glyph: String, hovered: Boolean, textColor: Int, destructive: Boolean) {
        val bg = if (destructive) (if (hovered) 0xFFDC2626.toInt() else 0x33DC2626) else (if (hovered) ModernGuiUtils.COLOR_CARD_BG_HOVER else ModernGuiUtils.COLOR_SIDEBAR_BG)
        val border = if (destructive) (if (hovered) 0xFFEF4444.toInt() else 0x88DC2626.toInt()) else (if (hovered) ModernGuiUtils.getAccentColor() else ModernGuiUtils.COLOR_CARD_BORDER)
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, SMALL_BTN, SMALL_BTN, ModernGuiUtils.WIDGET_RADIUS, bg, border)
        val color = if (destructive && hovered) 0xFFFFFFFF.toInt() else textColor
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(glyph), x + SMALL_BTN / 2, y + (SMALL_BTN - 8) / 2, color)
    }

    private fun addTargetLabel(pageCount: Int): String = when {
        addTargetPage < 0 -> "Auto"
        addTargetPage >= pageCount -> "New page"
        else -> "Page ${addTargetPage + 1}"
    }

    // Positions of the bottom row and header controls, shared by drawing and clicking.
    private fun doneX(f: Frame) = f.winX + WIN_W - DONE_W - 12
    private fun doneY(f: Frame) = f.winY + (HEADER_H - DONE_H) / 2
    private fun inputX(f: Frame) = f.winX + 16
    private fun pageBtnX(f: Frame) = inputX(f) + INPUT_W + 6
    private fun addBtnX(f: Frame) = pageBtnX(f) + PAGE_BTN_W + 6
    private fun resetBtnX(f: Frame) = addBtnX(f) + ADD_W + 6
    private fun delBtnX(rowX: Int, rowW: Int) = rowX + rowW - SMALL_BTN - 5
    private fun nextBtnX(rowX: Int, rowW: Int) = delBtnX(rowX, rowW) - SMALL_BTN - 8
    private fun prevBtnX(rowX: Int, rowW: Int) = nextBtnX(rowX, rowW) - SMALL_BTN - 3

    // ------------------------------------------------------------------ input

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick)
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val f = frame()
        val pages = CommandWheelPages.pages()

        if (mouseX in doneX(f)..(doneX(f) + DONE_W) && mouseY in doneY(f)..(doneY(f) + DONE_H)) {
            playPloppSound()
            this.onClose()
            return true
        }

        // Bottom row.
        if (mouseY in f.bottomY..(f.bottomY + BOTTOM_H)) {
            if (mouseX in inputX(f)..(inputX(f) + INPUT_W)) {
                inputFocused = true
                return true
            }
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
                CommandWheelPages.reset()
                addTargetPage = -1
                return true
            }
        } else {
            inputFocused = false
        }

        // List rows.
        if (mouseX in f.listX..(f.listX + f.listW) && mouseY in f.listY..(f.listY + f.listH)) {
            val rowX = f.listX + 8
            val rowW = f.listW - 16
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
        private const val WIN_W = 470
        private const val WIN_H = 372
        private const val HEADER_H = 38
        private const val DONE_W = 60
        private const val DONE_H = 22
        private const val BOTTOM_H = 24
        private const val INPUT_W = 200
        private const val PAGE_BTN_W = 84
        private const val ADD_W = 60
        private const val RESET_W = 60

        private const val PAGE_HEADER_H = 22
        private const val ITEM_H = 26
        private const val ITEM_GAP = 4
        private const val PAGE_GAP = 10
        private const val SMALL_BTN = 18

        /** How far the mouse has to travel after a press before a row starts to follow it. */
        private const val DRAG_THRESHOLD = 4.0
    }
}
