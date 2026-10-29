package net.alpaka.addons.client

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.features.wheel.CommandWheelPages
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
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
 * The row above the list picks which wheel is edited: the shared one, or the own wheel of a
 * Skyblock profile. The profile in play can get one there, starting as a copy of the shared wheel,
 * and a profile's wheel can be dropped again, after which that profile opens the shared one.
 *
 * Drawn with [AlpakaStyle], the config menu's look: slim rows that light up on hover, their move
 * and remove buttons only shown then, the accent for whatever is being dropped on.
 */
class CommandWheelConfigScreen(private val parent: Screen) : Screen(GuiFont.text("Command Wheel")), net.alpaka.addons.client.gui.NoticeHost {

    /** The wheel being edited, as a [CommandWheelPages] key; starts on the one the key opens now. */
    private var wheel: String? = CommandWheelPages.activeWheel()
    /** Whether the list of wheels is open below its field. */
    private var wheelPickerOpen = false

    private var newCommandInput: String = "/"
    private var inputFocused: Boolean = false

    /** Where a new command goes: -1 for "Auto", a page index, or the page count for "New page". */
    private var addTargetPage: Int = -1
    /** Whether the list of pages to add to is open above its field, and how far it is scrolled. */
    private var pagePickerOpen = false
    private var pickerScroll = 0

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
        /** The row that picks the wheel being edited, above the list. */
        val wheelRowY: Int,
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
        val wheelRowY = winY + AlpakaStyle.HEADER_H + 6
        val listY = wheelRowY + AlpakaStyle.BUTTON_H + 8
        val listW = winW - 2 * (AlpakaStyle.PAD + 4)

        val buttonsW = PAGE_BTN_W + ADD_W + RESET_W + 12
        val split = listW < MIN_INPUT_W + 6 + buttonsW
        val bottom = winY + winH - AlpakaStyle.PAD - AlpakaStyle.BUTTON_H
        val inputY = if (split) bottom - AlpakaStyle.BUTTON_H - 6 else bottom
        val inputW = if (split) listW else listW - buttonsW - 6
        val buttonsX = listX + listW - buttonsW
        val listH = inputY - 16 - listY
        return Frame(winX, winY, winW, winH, wheelRowY, listX, listY, listW, listH, inputY, inputW, bottom, buttonsX)
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

    private fun playPloppSound() = AlpakaStyle.click()

    override fun onClose() {
        this.minecraft?.gui?.setScreen(this.parent)
    }

    // -------------------------------------------------------------- rendering

    override fun init() {
        // Once only: back from the Reset dialog the window is simply there again.
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val now = System.currentTimeMillis()
        val deltaSec = min(0.1f, (now - lastFrameMs) / 1000f)
        lastFrameMs = now

        AlpakaStyle.backdrop(graphics, this.width, this.height)
        val pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height)

        // A wheel dropped elsewhere (another screen, a config reload) falls back to the shared one.
        if (wheel != null && !CommandWheelPages.hasOwnWheel(wheel!!)) wheel = null
        val f = frame()
        val pages = CommandWheelPages.pages(wheel)
        val accent = ModernGuiUtils.getAccentColor()

        AlpakaStyle.panel(graphics, f.winX, f.winY, f.winW, f.winH)
        AlpakaStyle.header(graphics, this.font, "Command Wheel", f.winX, f.winY, f.winW)

        val hoverDone = inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H, "Done", hoverDone, ButtonKind.PRIMARY)

        drawWheelRow(graphics, f, mouseX, mouseY)

        buildRows(pages)
        maxScrollY = max(0.0, (contentHeight - f.listH).toDouble())
        targetScrollY = targetScrollY.coerceIn(0.0, maxScrollY)
        scrollY += (targetScrollY - scrollY) * min(1.0, deltaSec * 14.0)

        if (dragging) updateDropTarget(f, pages, mouseY)

        val inList = inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)
        graphics.enableScissor(f.listX, f.listY, f.listX + f.listW, f.listY + f.listH)

        val rowX = f.listX
        val rowW = f.listW
        for (row in rows) {
            val y = f.listY + row.y - scrollY.toInt()
            if (y + row.height < f.listY || y > f.listY + f.listH) continue
            when (row.kind) {
                Kind.HEADER -> {
                    val count = pages[row.page].size
                    AlpakaStyle.groupLabel(graphics, this.font, "Page ${row.page + 1}", rowX + 4, y + 5)
                    val countText = "$count / ${CommandWheelPages.MAX_PER_PAGE}"
                    val full = count >= CommandWheelPages.MAX_PER_PAGE
                    graphics.text(this.font, GuiFont.text(countText), rowX + rowW - GuiFont.width(this.font, countText) - 4, y + 5,
                        if (full) AlpakaStyle.DANGER else AlpakaStyle.TEXT_MUTED, false)
                }
                Kind.ITEM -> drawItemRow(graphics, row, rowX, y, rowW, mouseX, mouseY, inList && !dragging && !pagePickerOpen && !wheelPickerOpen)
                Kind.NEW_PAGE -> {
                    val active = dropPage == row.page
                    ModernGuiUtils.drawRoundedOutline(graphics, rowX, y, rowW, row.height, AlpakaStyle.CARD_RADIUS,
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

        // Soft edges where the pages scroll on, in place of a scrollbar.
        AlpakaStyle.scrollFades(graphics, f.listX, f.listY, f.listW, f.listH, scrollY > 0.5, scrollY < maxScrollY - 0.5)
        graphics.disableScissor()

        // The dragged command follows the mouse as a copy of its row.
        if (dragging && pressPage in pages.indices && pressIndex in pages[pressPage].indices) {
            val ghostY = mouseY - dragGrabOffsetY
            ModernGuiUtils.drawPanelShadow(graphics, rowX, ghostY, rowW, ITEM_H, AlpakaStyle.CARD_RADIUS, 0.8f)
            ModernGuiUtils.drawRoundedRect(graphics, rowX, ghostY, rowW, ITEM_H, AlpakaStyle.CARD_RADIUS, AlpakaStyle.ROW_SELECTED)
            ModernGuiUtils.drawRoundedOutline(graphics, rowX, ghostY, rowW, ITEM_H, AlpakaStyle.CARD_RADIUS, AlpakaStyle.accentDim())
            drawGrip(graphics, rowX, ghostY, accent)
            graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, pages[pressPage][pressIndex], rowW - 28)),
                rowX + 20, ghostY + (ITEM_H - 8) / 2, AlpakaStyle.TEXT, false)
        }

        // Footer: new command, target page, add, reset.
        ModernGuiUtils.drawRect(graphics, f.winX + AlpakaStyle.PAD, f.inputY - 8, f.winW - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE)
        val hoverInput = inside(mouseX, mouseY, inputX(f), f.inputY, f.inputW, AlpakaStyle.BUTTON_H)
        AlpakaStyle.textField(graphics, this.font, inputX(f), f.inputY, f.inputW, AlpakaStyle.BUTTON_H,
            newCommandInput, "/command", inputFocused, hoverInput, caretFrom)

        val buttonsY = f.buttonsY
        AlpakaStyle.selectField(graphics, this.font, pageBtnX(f), buttonsY, PAGE_BTN_W, AlpakaStyle.BUTTON_H,
            addTargetLabel(addTargetPage, pages.size), pagePickerOpen)

        val hoverAdd = inside(mouseX, mouseY, addBtnX(f), buttonsY, ADD_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, addBtnX(f), buttonsY, ADD_W, AlpakaStyle.BUTTON_H, "Add", hoverAdd, ButtonKind.PRIMARY)

        val hoverReset = inside(mouseX, mouseY, resetBtnX(f), buttonsY, RESET_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, resetBtnX(f), buttonsY, RESET_W, AlpakaStyle.BUTTON_H,
            "Reset", hoverReset, ButtonKind.DANGER)

        if (pagePickerOpen) drawPagePicker(graphics, f, pages.size, mouseX, mouseY)
        if (wheelPickerOpen) drawWheelPicker(graphics, f, mouseX, mouseY)

        AlpakaStyle.endOpen(graphics, pushed)
        // Notices over the window, as over the config menu.
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics)
    }

    /**
     * The open list of pages to add to, above its field since the field sits at the window's foot:
     * a small window of its own, as wide as the field, its names as the config's choice lists show
     * them. With more pages than fit above the field it scrolls.
     */
    private fun drawPagePicker(graphics: GuiGraphicsExtractor, f: Frame, pageCount: Int, mouseX: Int, mouseY: Int) {
        graphics.nextStratum()
        val box = pagePickerBox(f, pageCount)
        val targets = pickerTargets(pageCount)
        val rows = pickerRows(f, pageCount)
        // The window may have grown since the picker opened, giving room for more rows.
        pickerScroll = pickerScroll.coerceIn(0, maxOf(0, targets.size - rows))
        AlpakaStyle.popover(graphics, box[0], box[1], box[2], box[3])
        graphics.enableScissor(box[0], box[1] + PICKER_PAD, box[0] + box[2], box[1] + box[3] - PICKER_PAD)
        for (i in 0 until rows) {
            val target = targets.getOrNull(pickerScroll + i) ?: break
            val y = box[1] + PICKER_PAD + i * AlpakaStyle.ROW_H
            val hover = inside(mouseX, mouseY, box[0] + PICKER_PAD, y, box[2] - 2 * PICKER_PAD, AlpakaStyle.ROW_H)
            AlpakaStyle.choiceEntry(graphics, this.font, box[0] + PICKER_PAD, y, box[2] - 2 * PICKER_PAD, AlpakaStyle.ROW_H,
                addTargetLabel(target, pageCount), target == addTargetPage, hover)
        }
        AlpakaStyle.scrollFades(graphics, box[0] + 1, box[1] + PICKER_PAD, box[2] - 2, rows * AlpakaStyle.ROW_H,
            pickerScroll > 0, pickerScroll + rows < targets.size)
        graphics.disableScissor()
    }

    /** The wheels to pick from: the shared one, then every profile of this account with its own. */
    private fun wheelChoices(): List<String?> = listOf<String?>(null) + CommandWheelPages.profileWheels()

    private fun wheelName(key: String?): String = key?.let { CommandWheelPages.profileName(it) } ?: "Shared Wheel"

    /**
     * The button on the right of the wheel row, or null when it has none: on a profile's wheel it
     * removes that wheel, on the shared one it gives the profile in play a wheel of its own.
     */
    private fun wheelActionLabel(): String? {
        if (wheel != null) return "Remove"
        val profile = CommandWheelPages.currentProfileKey() ?: return null
        if (CommandWheelPages.hasOwnWheel(profile)) return null
        return "Own Wheel for " + CommandWheelPages.profileName(profile)
    }

    private fun wheelHint(): String = when {
        wheel != null -> "Opens on ${CommandWheelPages.profileName(wheel!!)} instead of the shared wheel."
        CommandWheelPages.currentProfileKey() == null -> "Join Skyblock to give a profile its own wheel."
        else -> "Opens on every profile without its own wheel."
    }

    private fun wheelActionW(label: String): Int = GuiFont.width(this.font, label) + 16

    private fun wheelActionX(f: Frame, label: String): Int = f.listX + f.listW - wheelActionW(label)

    /** The picker's width: its own, less whatever a long profile name on the right needs on a narrow window. */
    private fun wheelFieldW(f: Frame): Int {
        val action = wheelActionLabel() ?: return WHEEL_FIELD_W
        return min(WHEEL_FIELD_W, f.listW - wheelActionW(action) - 6)
    }

    private fun drawWheelRow(graphics: GuiGraphicsExtractor, f: Frame, mouseX: Int, mouseY: Int) {
        val y = f.wheelRowY
        val fieldW = wheelFieldW(f)
        AlpakaStyle.selectField(graphics, this.font, f.listX, y, fieldW, AlpakaStyle.BUTTON_H, wheelName(wheel), wheelPickerOpen)

        var hintRight = f.listX + f.listW
        val action = wheelActionLabel()
        if (action != null) {
            val x = wheelActionX(f, action)
            val w = wheelActionW(action)
            val hover = !wheelPickerOpen && inside(mouseX, mouseY, x, y, w, AlpakaStyle.BUTTON_H)
            AlpakaStyle.button(graphics, this.font, x, y, w, AlpakaStyle.BUTTON_H, action, hover,
                if (wheel != null) ButtonKind.DANGER else ButtonKind.PLAIN)
            hintRight = x - 8
        }

        // Only where it fits whole; a cut-off sentence says less than none.
        val hint = wheelHint()
        val hintX = f.listX + fieldW + 8
        if (hintX + GuiFont.width(this.font, hint) <= hintRight) {
            graphics.text(this.font, GuiFont.text(hint), hintX, y + (AlpakaStyle.BUTTON_H - 8) / 2, AlpakaStyle.TEXT_MUTED, false)
        }
    }

    /** The picker's box below its field: x, y, width, height. */
    private fun wheelPickerBox(f: Frame): IntArray {
        val choices = wheelChoices()
        val rows = choices.size.coerceAtMost(maxOf(3, (f.inputY - 8 - (f.wheelRowY + AlpakaStyle.BUTTON_H + 4) - 2 * PICKER_PAD) / AlpakaStyle.ROW_H))
        val widest = choices.maxOf { GuiFont.width(this.font, wheelName(it)) }
        val w = maxOf(wheelFieldW(f), widest + 2 * PICKER_PAD + 24)
        return intArrayOf(f.listX, f.wheelRowY + AlpakaStyle.BUTTON_H + 4, w, rows * AlpakaStyle.ROW_H + 2 * PICKER_PAD)
    }

    private fun drawWheelPicker(graphics: GuiGraphicsExtractor, f: Frame, mouseX: Int, mouseY: Int) {
        graphics.nextStratum()
        val box = wheelPickerBox(f)
        val rows = (box[3] - 2 * PICKER_PAD) / AlpakaStyle.ROW_H
        AlpakaStyle.popover(graphics, box[0], box[1], box[2], box[3])
        for ((i, choice) in wheelChoices().take(rows).withIndex()) {
            val y = box[1] + PICKER_PAD + i * AlpakaStyle.ROW_H
            val hover = inside(mouseX, mouseY, box[0] + PICKER_PAD, y, box[2] - 2 * PICKER_PAD, AlpakaStyle.ROW_H)
            AlpakaStyle.choiceEntry(graphics, this.font, box[0] + PICKER_PAD, y, box[2] - 2 * PICKER_PAD, AlpakaStyle.ROW_H,
                wheelName(choice), choice == wheel, hover)
        }
    }

    /** Switches the editor to another wheel and starts that one's list from the top. */
    private fun showWheel(next: String?) {
        wheel = next
        addTargetPage = -1
        scrollY = 0.0
        targetScrollY = 0.0
    }

    private fun clickWheelRow(f: Frame, mouseX: Int, mouseY: Int): Boolean {
        val y = f.wheelRowY
        if (inside(mouseX, mouseY, f.listX, y, wheelFieldW(f), AlpakaStyle.BUTTON_H)) {
            playPloppSound()
            wheelPickerOpen = true
            return true
        }
        val action = wheelActionLabel() ?: return false
        if (!inside(mouseX, mouseY, wheelActionX(f, action), y, wheelActionW(action), AlpakaStyle.BUTTON_H)) return false
        playPloppSound()
        val current = wheel
        if (current != null) {
            val name = CommandWheelPages.profileName(current)
            this.minecraft?.gui?.setScreen(AlpakaConfirmScreen(this, "Remove $name's Wheel",
                "$name opens the shared wheel again, and its own commands are deleted. This cannot be undone.", "Remove", true) {
                CommandWheelPages.removeProfileWheel(current)
                showWheel(null)
            })
        } else {
            val profile = CommandWheelPages.currentProfileKey() ?: return true
            CommandWheelPages.createProfileWheel(profile)
            showWheel(profile)
        }
        return true
    }

    /** Auto, every page, and a new page: what the picker lists. */
    private fun pickerTargets(pageCount: Int): List<Int> = (-1..pageCount).toList()

    /** How many names the picker shows at once: all of them, or as many as fit above the field. */
    private fun pickerRows(f: Frame, pageCount: Int): Int {
        // Between the field and the window's header line.
        val room = (f.buttonsY - 4 - (f.winY + AlpakaStyle.HEADER_H + 4) - 2 * PICKER_PAD) / AlpakaStyle.ROW_H
        return pickerTargets(pageCount).size.coerceAtMost(maxOf(3, room))
    }

    /**
     * The picker's box: x, y, width, height. It starts where the field starts and ends just above
     * it, as wide as the field or as its longest name needs, whichever is more.
     */
    private fun pagePickerBox(f: Frame, pageCount: Int): IntArray {
        val h = pickerRows(f, pageCount) * AlpakaStyle.ROW_H + 2 * PICKER_PAD
        val widest = pickerTargets(pageCount).maxOf { GuiFont.width(this.font, addTargetLabel(it, pageCount)) }
        val w = maxOf(PAGE_BTN_W, widest + 2 * PICKER_PAD + 24)
        return intArrayOf(pageBtnX(f), f.buttonsY - 4 - h, w, h)
    }

    /** Opens the picker scrolled so the page chosen now is in view. */
    private fun openPagePicker(f: Frame, pageCount: Int) {
        pagePickerOpen = true
        val rows = pickerRows(f, pageCount)
        val index = pickerTargets(pageCount).indexOf(addTargetPage).coerceAtLeast(0)
        pickerScroll = (index - rows + 1).coerceAtLeast(0).coerceAtMost(maxOf(0, pickerTargets(pageCount).size - rows))
    }

    private fun drawGrip(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        for (k in 0..2) {
            ModernGuiUtils.drawRect(graphics, x + 7, y + ITEM_H / 2 - 3 + k * 3, 6, 1, color)
        }
    }

    private fun drawItemRow(graphics: GuiGraphicsExtractor, row: Row, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int, hoverable: Boolean) {
        val hovered = hoverable && inside(mouseX, mouseY, x, y, w, row.height)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, row.height, AlpakaStyle.CARD_RADIUS, if (hovered) AlpakaStyle.ROW_HOVER else AlpakaStyle.ROW)

        drawGrip(graphics, x, y, if (hovered) AlpakaStyle.TEXT_MUTED else AlpakaStyle.DIVIDER)
        // Cut short of the row's buttons, which show while it is hovered.
        val textRoom = (if (hovered) prevBtnX(x, w) - 6 else x + w - 8) - (x + 20)
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, row.command ?: "", textRoom)),
            x + 20, y + (row.height - 8) / 2, AlpakaStyle.TEXT, false)

        // Move to the previous page (dimmed on the first), move to the next page (a new one after
        // the last), remove. Quiet until the row is hovered.
        if (!hovered) return
        val prevX = prevBtnX(x, w)
        val nextX = nextBtnX(x, w)
        val delX = delBtnX(x, w)
        val btnY = y + (row.height - SMALL_BTN) / 2
        val canPrev = row.page > 0
        AlpakaStyle.chevronButton(graphics, prevX, btnY, SMALL_BTN, true, inside(mouseX, mouseY, prevX, btnY, SMALL_BTN, SMALL_BTN), canPrev)
        AlpakaStyle.chevronButton(graphics, nextX, btnY, SMALL_BTN, false, inside(mouseX, mouseY, nextX, btnY, SMALL_BTN, SMALL_BTN), true)
        AlpakaStyle.trashButton(graphics, delX, btnY, SMALL_BTN, inside(mouseX, mouseY, delX, btnY, SMALL_BTN, SMALL_BTN))
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = AlpakaStyle.inside(mx.toDouble(), my.toDouble(), x, y, w, h)

    private fun addTargetLabel(target: Int, pageCount: Int): String = when {
        target < 0 -> "Auto"
        target >= pageCount -> "New Page"
        else -> "Page ${target + 1}"
    }

    // Positions of the bottom row and header controls, shared by drawing and clicking.
    private fun doneX(f: Frame) = f.winX + f.winW - DONE_W - AlpakaStyle.PAD
    private fun doneY(f: Frame) = f.winY + (AlpakaStyle.HEADER_H - AlpakaStyle.BUTTON_H) / 2
    private fun inputX(f: Frame) = f.listX
    private fun pageBtnX(f: Frame) = f.buttonsX
    private fun addBtnX(f: Frame) = pageBtnX(f) + PAGE_BTN_W + 6
    private fun resetBtnX(f: Frame) = addBtnX(f) + ADD_W + 6

    /** For the screenshot tour: the middle of the page field, to open its list. */
    fun devPagePickerPoint(): IntArray {
        val f = frame()
        return intArrayOf(pageBtnX(f) + PAGE_BTN_W / 2, f.buttonsY + AlpakaStyle.BUTTON_H / 2)
    }

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
        val pages = CommandWheelPages.pages(wheel)

        // An open wheel list takes the click the same way.
        if (wheelPickerOpen) {
            wheelPickerOpen = false
            val box = wheelPickerBox(f)
            val listY = box[1] + PICKER_PAD
            if (inside(mouseX, mouseY, box[0], listY, box[2], box[3] - 2 * PICKER_PAD)) {
                val choices = wheelChoices()
                val index = (mouseY - listY) / AlpakaStyle.ROW_H
                if (index in choices.indices) {
                    playPloppSound()
                    if (choices[index] != wheel) showWheel(choices[index])
                }
            }
            return true
        }

        // An open page list takes the click: a name picks that page, anywhere else just closes it.
        if (pagePickerOpen) {
            pagePickerOpen = false
            val box = pagePickerBox(f, pages.size)
            val listY = box[1] + PICKER_PAD
            if (inside(mouseX, mouseY, box[0], listY, box[2], pickerRows(f, pages.size) * AlpakaStyle.ROW_H)) {
                val target = pickerTargets(pages.size).getOrNull(pickerScroll + (mouseY - listY) / AlpakaStyle.ROW_H)
                if (target != null) {
                    playPloppSound()
                    addTargetPage = target
                }
            }
            return true
        }

        if (inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)) {
            playPloppSound()
            this.onClose()
            return true
        }

        if (clickWheelRow(f, mouseX, mouseY)) {
            inputFocused = false
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
                openPagePicker(f, pages.size)
                return true
            }
            if (mouseX in addBtnX(f)..(addBtnX(f) + ADD_W)) {
                addCurrentInput()
                return true
            }
            if (mouseX in resetBtnX(f)..(resetBtnX(f) + RESET_W)) {
                playPloppSound()
                // Reset wipes every command, so it asks first, in the dialog every such action uses.
                this.minecraft?.gui?.setScreen(AlpakaConfirmScreen(this, "Reset Command Wheel",
                    "Replaces every command on this wheel with the default set. This cannot be undone.", "Reset", true) {
                    CommandWheelPages.reset(wheel)
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
            val rowW = f.listW
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
                        CommandWheelPages.remove(wheel, row.page, row.index)
                        clampAddTarget()
                        return true
                    }
                    if (mouseX in nextX..(nextX + SMALL_BTN)) {
                        if (CommandWheelPages.shiftToPage(wheel, row.page, row.index, 1)) playPloppSound()
                        return true
                    }
                    if (mouseX in prevX..(prevX + SMALL_BTN)) {
                        if (row.page > 0 && CommandWheelPages.shiftToPage(wheel, row.page, row.index, -1)) playPloppSound()
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
                if (CommandWheelPages.move(wheel, pressPage, pressIndex, dropPage, toIndex)) playPloppSound()
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
        val count = CommandWheelPages.pageCount(wheel)
        if (addTargetPage > count) addTargetPage = count
    }

    private fun addCurrentInput() {
        var trimmed = newCommandInput.trim()
        if (trimmed.isEmpty() || trimmed == "/") return
        if (!trimmed.startsWith("/")) trimmed = "/$trimmed"

        if (!CommandWheelPages.contains(wheel, trimmed)) {
            playPloppSound()
            val wanted = if (addTargetPage < 0) null else addTargetPage
            CommandWheelPages.add(wheel, trimmed, wanted)
            // "New page" has been used up; the next command should join it rather than open another.
            if (addTargetPage >= CommandWheelPages.pageCount(wheel)) addTargetPage = CommandWheelPages.pageCount(wheel) - 1
        }
        newCommandInput = "/"
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (pagePickerOpen && scrollY != 0.0) {
            val f = frame()
            val count = CommandWheelPages.pages(wheel).size
            val most = maxOf(0, pickerTargets(count).size - pickerRows(f, count))
            pickerScroll = (pickerScroll - scrollY.toInt().coerceIn(-1, 1)).coerceIn(0, most)
            return true
        }
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
        if ((pagePickerOpen || wheelPickerOpen) && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            pagePickerOpen = false
            wheelPickerOpen = false
            return true
        }
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
        private const val WIN_H = 326
        private const val MIN_W = 260
        private const val MIN_H = 180
        /** Space kept free around the window, as the config screen keeps. */
        private const val MARGIN = 8
        private const val DONE_W = 44
        private const val MIN_INPUT_W = 110
        private const val PAGE_BTN_W = 62
        private const val ADD_W = 40
        private const val RESET_W = 44
        private const val WHEEL_FIELD_W = 96
        /** Inner margin of the page list. */
        private const val PICKER_PAD = 4

        private const val PAGE_HEADER_H = 17
        private const val ITEM_H = 20
        private const val ITEM_GAP = 3
        private const val PAGE_GAP = 6
        private const val SMALL_BTN = 14

        /** How far the mouse has to travel after a press before a row starts to follow it. */
        private const val DRAG_THRESHOLD = 4.0
    }
}
