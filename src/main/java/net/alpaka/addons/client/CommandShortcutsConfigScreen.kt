package net.alpaka.addons.client

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind
import net.alpaka.addons.client.gui.EditableText
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaConfig.CommandAlias
import net.alpaka.addons.config.AlpakaConfig.KeyShortcut
import net.alpaka.addons.features.shortcuts.CommandShortcuts
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * Where Command Shortcuts are set up: typed aliases ("/v" for "/visit") and keys that run a
 * command ("Y" for "/sb menu"), one section each, edited in place.
 *
 * Every cell is a field: a click puts the caret in it, Tab moves on to the next one. A key cell
 * waits for the next key press instead, with any modifiers held then. Rows left completely empty
 * are dropped when the screen closes; an alias or key used twice is shown red, since only the first
 * one counts. Drawn with [AlpakaStyle], like the command wheel editor next to it.
 */
class CommandShortcutsConfigScreen(private val parent: Screen) : Screen(GuiFont.text("Command Shortcuts")),
    net.alpaka.addons.client.gui.NoticeHost {

    private enum class Kind { SECTION, HINT, ALIAS, KEY, ADD_ALIAS, ADD_KEY }

    /** One line of the list, positioned relative to the top of the scrolling content. */
    private class Row(val kind: Kind, val index: Int, val y: Int, val height: Int, val text: String = "")

    /** The cell being typed into: an alias row's 0 (alias) or 1 (command), a key row's 1 (command). */
    private var focusKind: Kind? = null
    private var focusIndex = -1
    private var focusColumn = 0

    /** The key row waiting for a key press, or -1. */
    private var capturingKey = -1

    /**
     * The character of the key just captured, still to come: it arrives after the key press, when
     * the command cell already has the focus, and must not be typed into it.
     */
    private var swallowChar = false

    private var scrollY = 0.0
    private var targetScrollY = 0.0
    private var maxScrollY = 0.0
    private var lastFrameMs = System.currentTimeMillis()
    private var openTimeMs = 0L
    private var caretFrom = 0L

    private var rows: List<Row> = emptyList()
    private var contentHeight = 0

    private val aliases: MutableList<CommandAlias>
        get() = AlpakaConfig.instance.commandAliases ?: ArrayList<CommandAlias>().also { AlpakaConfig.instance.commandAliases = it }
    private val keys: MutableList<KeyShortcut>
        get() = AlpakaConfig.instance.commandKeyShortcuts ?: ArrayList<KeyShortcut>().also { AlpakaConfig.instance.commandKeyShortcuts = it }

    // ----------------------------------------------------------------- layout

    private class Frame(val winX: Int, val winY: Int, val winW: Int, val winH: Int,
                        val listX: Int, val listY: Int, val listW: Int, val listH: Int)

    private fun frame(): Frame {
        val winW = (this.width - MARGIN * 2).coerceIn(MIN_W, WIN_W)
        val winH = (this.height - MARGIN * 2).coerceIn(MIN_H, WIN_H)
        val winX = (this.width - winW) / 2
        val winY = maxOf(MARGIN / 2, (this.height - winH) / 2)
        val listX = winX + AlpakaStyle.PAD + 4
        val listY = winY + AlpakaStyle.HEADER_H + 2
        val listW = winW - 2 * (AlpakaStyle.PAD + 4)
        val listH = winY + winH - AlpakaStyle.PAD - listY
        return Frame(winX, winY, winW, winH, listX, listY, listW, listH)
    }

    private fun buildRows() {
        val list = ArrayList<Row>()
        var y = 2
        fun add(kind: Kind, index: Int, h: Int, text: String = "") {
            list.add(Row(kind, index, y, h, text))
            y += h
        }
        add(Kind.SECTION, 0, SECTION_H, "Command Aliases")
        add(Kind.HINT, 0, HINT_H, "\"/v\" for \"/visit\": /v Steve sends /visit Steve.")
        for (i in aliases.indices) { add(Kind.ALIAS, i, ITEM_H); y += ITEM_GAP }
        add(Kind.ADD_ALIAS, 0, ITEM_H)
        y += SECTION_GAP
        add(Kind.SECTION, 1, SECTION_H, "Key Shortcuts")
        add(Kind.HINT, 1, HINT_H, "Runs the command when the key is pressed in game.")
        for (i in keys.indices) { add(Kind.KEY, i, ITEM_H); y += ITEM_GAP }
        add(Kind.ADD_KEY, 0, ITEM_H)
        y += 4
        rows = list
        contentHeight = y
    }

    // Cell positions within a row, shared by drawing and clicking.
    // One width for both sections, so the arrows and the commands line up down the whole window.
    private fun leftW(@Suppress("UNUSED_PARAMETER") kind: Kind) = KEY_W
    private fun commandX(rowX: Int, kind: Kind) = rowX + leftW(kind) + ARROW_W
    private fun delX(rowX: Int, rowW: Int) = rowX + rowW - SMALL_BTN - 3
    private fun commandW(rowX: Int, rowW: Int, kind: Kind) = delX(rowX, rowW) - 6 - commandX(rowX, kind)

    private fun doneX(f: Frame) = f.winX + f.winW - DONE_W - AlpakaStyle.PAD
    private fun doneY(f: Frame) = f.winY + (AlpakaStyle.HEADER_H - AlpakaStyle.BUTTON_H) / 2

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = AlpakaStyle.inside(mx.toDouble(), my.toDouble(), x, y, w, h)

    private fun playPloppSound() = AlpakaStyle.click()

    // -------------------------------------------------------------- rendering

    override fun init() {
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val now = System.currentTimeMillis()
        val deltaSec = min(0.1f, (now - lastFrameMs) / 1000f)
        lastFrameMs = now

        AlpakaStyle.backdrop(graphics, this.width, this.height)
        val pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height)

        val f = frame()
        AlpakaStyle.panel(graphics, f.winX, f.winY, f.winW, f.winH)
        AlpakaStyle.header(graphics, this.font, "Command Shortcuts", f.winX, f.winY, f.winW)
        val hoverDone = inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)
        AlpakaStyle.button(graphics, this.font, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H, "Done", hoverDone, ButtonKind.PRIMARY)

        buildRows()
        maxScrollY = max(0.0, (contentHeight - f.listH).toDouble())
        targetScrollY = targetScrollY.coerceIn(0.0, maxScrollY)
        scrollY += (targetScrollY - scrollY) * min(1.0, deltaSec * 14.0)

        val inList = inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)
        graphics.enableScissor(f.listX, f.listY, f.listX + f.listW, f.listY + f.listH)

        val rowX = f.listX
        val rowW = f.listW
        duplicateTip = null
        val aliasDupes = duplicateAliases()
        val keyDupes = duplicateKeys()
        for (row in rows) {
            val y = f.listY + row.y - scrollY.toInt()
            if (y + row.height < f.listY || y > f.listY + f.listH) continue
            when (row.kind) {
                // The heading a step brighter than the hint under it, so the two read as title and text.
                Kind.SECTION -> graphics.text(this.font, GuiFont.text(row.text), rowX + 4, y + 8, AlpakaStyle.TEXT_SOFT, false)
                // While two entries clash, the section's help line says so, in the error color.
                Kind.HINT -> {
                    val clash = clashMessage(row.index, if (row.index == 0) aliasDupes else keyDupes)
                    graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, clash ?: row.text, rowW - 8)),
                        rowX + 4, y + 1, if (clash != null) AlpakaStyle.DANGER_SOFT else AlpakaStyle.TEXT_MUTED, false)
                }
                Kind.ALIAS, Kind.KEY -> drawEntryRow(graphics, row, rowX, y, rowW, mouseX, mouseY, inList,
                    if (row.kind == Kind.ALIAS) aliasDupes[row.index] else keyDupes[row.index])
                Kind.ADD_ALIAS, Kind.ADD_KEY -> {
                    // The same full-width button the config menu adds a preset with.
                    val hovered = inList && inside(mouseX, mouseY, rowX, y, rowW, row.height)
                    val label = if (row.kind == Kind.ADD_ALIAS) "+ Add Alias" else "+ Add Key Shortcut"
                    AlpakaStyle.button(graphics, this.font, rowX, y, rowW, row.height, label, hovered, ButtonKind.PLAIN)
                }
            }
        }
        AlpakaStyle.scrollFades(graphics, f.listX, f.listY, f.listW, f.listH, scrollY > 0.5, scrollY < maxScrollY - 0.5)
        graphics.disableScissor()

        duplicateTip?.let { AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, it) }

        AlpakaStyle.endOpen(graphics, pushed)
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics)
    }

    /** Why the duplicate field under the mouse is marked red, shown as its tooltip; null when none is. */
    private var duplicateTip: String? = null

    private fun drawEntryRow(graphics: GuiGraphicsExtractor, row: Row, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int,
                             inList: Boolean, duplicate: Boolean?) {
        val hovered = inList && inside(mouseX, mouseY, x, y, w, row.height)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, row.height, AlpakaStyle.CARD_RADIUS, if (hovered) AlpakaStyle.ROW_HOVER else AlpakaStyle.ROW)

        val fieldX = x + 3
        val fieldY = y + (row.height - FIELD_H) / 2
        val leftW = leftW(row.kind) - 3
        val hoverLeft = inList && inside(mouseX, mouseY, fieldX, fieldY, leftW, FIELD_H)
        if (row.kind == Kind.ALIAS) {
            val alias = aliases[row.index]
            val focused = focusKind == Kind.ALIAS && focusIndex == row.index && focusColumn == 0
            if (focused) editorScroll = AlpakaStyle.textField(graphics, this.font, fieldX, fieldY, leftW, FIELD_H, editor, "/alias", true, hoverLeft, caretFrom)
            else AlpakaStyle.textField(graphics, this.font, fieldX, fieldY, leftW, FIELD_H, alias.alias, "/alias", false, hoverLeft, caretFrom)
        } else {
            val shortcut = keys[row.index]
            val capturing = capturingKey == row.index
            val label = if (capturing) "Press a Key" else CommandShortcuts.keyLabel(shortcut)
            val shown = AlpakaStyle.ellipsize(this.font, label, leftW - 8)
            if (capturing) {
                // Waiting for a key looks as it does in the config: the field with its line and
                // "Press a Key" in the accent.
                ModernGuiUtils.drawRoundedRect(graphics, fieldX, fieldY, leftW, FIELD_H, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.FIELD)
                ModernGuiUtils.drawRect(graphics, fieldX + 3, fieldY + FIELD_H - 1, leftW - 6, 1, AlpakaStyle.accentIndicator())
                graphics.text(this.font, GuiFont.text(shown), fieldX + 5, fieldY + (FIELD_H - 8) / 2,
                    AlpakaStyle.accentTextOn(AlpakaStyle.FIELD), false)
            } else {
                // Set off from the row like the fields beside it, since it sits on the same row color.
                ModernGuiUtils.drawRoundedRect(graphics, fieldX, fieldY, leftW, FIELD_H, AlpakaStyle.FIELD_RADIUS,
                    if (hoverLeft) AlpakaStyle.fieldHover() else AlpakaStyle.FIELD)
                // Left-aligned at the text fields' inset, like the aliases above it.
                graphics.text(this.font, GuiFont.text(shown), fieldX + 5, fieldY + (FIELD_H - 8) / 2,
                    if (shortcut.key.isNullOrEmpty()) AlpakaStyle.TEXT_MUTED else AlpakaStyle.TEXT, false)
            }
        }
        // Only the first of two equal aliases or keys counts. Both are underlined red, so it shows
        // what clashes with what; the section's help line says which one runs.
        if (duplicate != null) {
            ModernGuiUtils.drawRect(graphics, fieldX + 3, fieldY + FIELD_H - 1, leftW - 6, 1, AlpakaStyle.DANGER)
            // The red line alone doesn't say why; the mouse on the field does.
            val what = if (row.kind == Kind.ALIAS) "alias" else "key"
            if (hoverLeft) duplicateTip = if (duplicate) "This $what is used again below. This one applies, the other does not."
                else "This $what is already used above. Only the first one applies."
        }

        AlpakaStyle.mapsTo(graphics, x + leftW(row.kind) + ARROW_W / 2, fieldY + FIELD_H / 2, AlpakaStyle.TEXT_MUTED)

        val cmdX = commandX(x, row.kind)
        val cmdW = commandW(x, w, row.kind)
        val command = if (row.kind == Kind.ALIAS) aliases[row.index].command else keys[row.index].command
        val cmdFocused = focusKind == row.kind && focusIndex == row.index && focusColumn == 1
        val cmdHover = inList && inside(mouseX, mouseY, cmdX, fieldY, cmdW, FIELD_H)
        if (cmdFocused) editorScroll = AlpakaStyle.textField(graphics, this.font, cmdX, fieldY, cmdW, FIELD_H, editor, "/command", true, cmdHover, caretFrom)
        else AlpakaStyle.textField(graphics, this.font, cmdX, fieldY, cmdW, FIELD_H, command, "/command", false, cmdHover, caretFrom)

        val dx = delX(x, w)
        val btnY = y + (row.height - SMALL_BTN) / 2
        AlpakaStyle.trashButton(graphics, dx, btnY, SMALL_BTN, inList && inside(mouseX, mouseY, dx, btnY, SMALL_BTN, SMALL_BTN))
    }

    /** Alias rows that repeat an earlier alias, which therefore never apply. */
    private fun duplicateAliases(): Map<Int, Boolean> =
        clashes(aliases.map { CommandShortcuts.aliasName(it.alias) })

    /** Key rows bound to the same keys as an earlier one. */
    /** What the section's help line says while entries clash, or null when none do. */
    private fun clashMessage(section: Int, clashes: Map<Int, Boolean>): String? {
        val first = clashes.entries.filter { it.value }.minOfOrNull { it.key } ?: return null
        return if (section == 0) "\"${aliases[first].alias}\" is used twice; the first one runs."
            else "${CommandShortcuts.keyLabel(keys[first])} is used twice; the first one runs."
    }

    private fun duplicateKeys(): Map<Int, Boolean> =
        clashes(keys.map { if (it.key.isNullOrEmpty()) "" else it.key + "|" + (it.modifiers and CommandShortcuts.MODIFIER_MASK) })

    /**
     * Every row whose name another row shares, both of them, so the player sees what clashes with
     * what; the value says whether the row is the first of its name, the one that applies.
     */
    private fun clashes(names: List<String>): Map<Int, Boolean> {
        val result = HashMap<Int, Boolean>()
        names.withIndex().filter { it.value.isNotEmpty() }.groupBy { it.value }.values
            .filter { it.size > 1 }
            .forEach { group -> group.forEachIndexed { n, item -> result[item.index] = n == 0 } }
        return result
    }

    // ------------------------------------------------------------------ input

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick)
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val f = frame()
        capturingKey = -1

        if (inside(mouseX, mouseY, doneX(f), doneY(f), DONE_W, AlpakaStyle.BUTTON_H)) {
            playPloppSound()
            this.onClose()
            return true
        }

        // Which cell was being edited, so a click back into it only moves the caret.
        clickedWhileEditing = if (focusKind != null) Triple(focusKind, focusIndex, focusColumn) else null
        endEditing()
        if (!inside(mouseX, mouseY, f.listX, f.listY, f.listW, f.listH)) return super.mouseClicked(event, isDoubleClick)

        val rowX = f.listX
        val rowW = f.listW
        for (row in rows) {
            val y = f.listY + row.y - scrollY.toInt()
            if (!inside(mouseX, mouseY, rowX, y, rowW, row.height)) continue
            when (row.kind) {
                Kind.ADD_ALIAS -> {
                    playPloppSound()
                    aliases.add(CommandAlias("/", "/"))
                    focus(Kind.ALIAS, aliases.size - 1, 0)
                    save()
                }
                Kind.ADD_KEY -> {
                    playPloppSound()
                    keys.add(KeyShortcut("", 0, "/"))
                    capturingKey = keys.size - 1
                    save()
                }
                Kind.ALIAS, Kind.KEY -> {
                    val fieldY = y + (row.height - FIELD_H) / 2
                    val btnY = y + (row.height - SMALL_BTN) / 2
                    when {
                        inside(mouseX, mouseY, delX(rowX, rowW), btnY, SMALL_BTN, SMALL_BTN) -> {
                            playPloppSound()
                            if (row.kind == Kind.ALIAS) aliases.removeAt(row.index) else keys.removeAt(row.index)
                            save()
                        }
                        inside(mouseX, mouseY, rowX + 3, fieldY, leftW(row.kind) - 3, FIELD_H) -> {
                            if (row.kind == Kind.ALIAS) focusAt(Kind.ALIAS, row.index, 0, mouseX - (rowX + 3))
                            else {
                                playPloppSound()
                                capturingKey = row.index
                            }
                        }
                        inside(mouseX, mouseY, commandX(rowX, row.kind), fieldY, commandW(rowX, rowW, row.kind), FIELD_H) ->
                            focusAt(row.kind, row.index, 1, mouseX - commandX(rowX, row.kind))
                    }
                }
                else -> {}
            }
            return true
        }
        return true
    }

    private fun focus(kind: Kind, index: Int, column: Int) {
        caretFrom = System.currentTimeMillis()
        focusKind = kind
        focusIndex = index
        focusColumn = column
        // The cell's text goes into the editor, which brings the caret, selection and clipboard
        // every text field of the mod has; each change is written straight back to the cell.
        editor.setText(focusedText ?: "")
        editor.setCaret(editor.text.length)
    }

    private val editor = EditableText(MAX_LEN)
    /** How far the focused cell's text is scrolled, for placing the caret on a click. */
    private var editorScroll = 0
    /** The cell that was being edited when the current click came in, if any. */
    private var clickedWhileEditing: Triple<Kind?, Int, Int>? = null

    /**
     * A click into a cell: into the one being edited it moves the caret to where it landed; into
     * another it starts editing that one, the caret at the end, ready to type on.
     */
    private fun focusAt(kind: Kind, index: Int, column: Int, localX: Int) {
        val again = clickedWhileEditing == Triple(kind, index, column)
        focus(kind, index, column)
        if (again) editor.setCaretFromX(localX - 5 + editorScroll) { GuiFont.width(this.font, it) }
    }

    /** Writes the editor back to the focused cell; an alias stays a single word. */
    private fun syncEditor() {
        if (focusKind == Kind.ALIAS && focusColumn == 0 && editor.text.contains(' ')) {
            val caret = editor.caret
            val before = editor.text.substring(0, caret).count { it == ' ' }
            editor.setText(editor.text.replace(" ", ""))
            editor.setCaret(caret - before)
        }
        focusedText = editor.text
    }

    private fun endEditing() {
        if (focusKind != null) save()
        focusKind = null
        focusIndex = -1
    }

    /** The text of the focused cell, or null when nothing is focused. */
    private var focusedText: String?
        get() = when (focusKind) {
            Kind.ALIAS -> aliases.getOrNull(focusIndex)?.let { if (focusColumn == 0) it.alias else it.command }
            Kind.KEY -> keys.getOrNull(focusIndex)?.command
            else -> null
        }
        set(value) {
            val v = value ?: return
            when (focusKind) {
                Kind.ALIAS -> aliases.getOrNull(focusIndex)?.let { if (focusColumn == 0) it.alias = v else it.command = v }
                Kind.KEY -> keys.getOrNull(focusIndex)?.command = v
                else -> {}
            }
        }

    private fun insert(typed: String) {
        if (focusedText == null) return
        // An alias is a single word: spaces would never match the first word of a command.
        val clean = if (focusKind == Kind.ALIAS && focusColumn == 0) typed.replace(" ", "") else typed
        clean.filter { it.code >= 32 && it.code != 127 && it != '§' }.forEach { editor.insert(it) }
        syncEditor()
        caretFrom = System.currentTimeMillis()
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (swallowChar) {
            swallowChar = false
            return true
        }
        if (focusKind != null) {
            insert(String(Character.toChars(event.codepoint())))
            return true
        }
        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        // A captured key without a character (F5, Ctrl + E) leaves none to swallow.
        swallowChar = false
        if (capturingKey >= 0) {
            val shortcut = keys.getOrNull(capturingKey)
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || shortcut == null) {
                capturingKey = -1
                return true
            }
            // A modifier alone is not the key yet: it is held for the key that follows.
            if (CommandShortcuts.isModifierKey(event.key())) return true
            shortcut.key = InputConstants.getKey(event).name
            shortcut.modifiers = event.modifiers() and CommandShortcuts.MODIFIER_MASK
            capturingKey = -1
            swallowChar = true
            playPloppSound()
            // Straight on to the command, which a fresh row still needs.
            focus(Kind.KEY, keys.indexOf(shortcut), 1)
            save()
            return true
        }
        if (focusKind != null) {
            when {
                event.isPaste -> {
                    insert((this.minecraft?.keyboardHandler?.clipboard ?: "").lineSequence().firstOrNull() ?: "")
                    return true
                }
                event.key() == GLFW.GLFW_KEY_TAB -> {
                    // From an alias on to its command; from a command, out of the field.
                    if (focusKind == Kind.ALIAS && focusColumn == 0) focus(Kind.ALIAS, focusIndex, 1)
                    else endEditing()
                    return true
                }
                event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER
                        || event.key() == GLFW.GLFW_KEY_ESCAPE -> {
                    endEditing()
                    return true
                }
            }
            // Backspace, Delete, the arrows, Home and End, selecting, copy and cut.
            if (editor.keyPressed(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown())) {
                syncEditor()
                caretFrom = System.currentTimeMillis()
            }
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0) {
            targetScrollY = (targetScrollY - scrollY * 24.0).coerceIn(0.0, maxScrollY)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun save() = AlpakaConfig.save()

    override fun onClose() {
        this.minecraft?.gui?.setScreen(this.parent)
    }

    /** Rows left with nothing in them are dropped once the editor closes. */
    override fun removed() {
        aliases.removeIf { CommandShortcuts.cleanCommand(it.alias).isEmpty() && CommandShortcuts.cleanCommand(it.command).isEmpty() }
        keys.removeIf { it.key.isNullOrEmpty() && CommandShortcuts.cleanCommand(it.command).isEmpty() }
        save()
        super.removed()
    }

    companion object {
        private const val WIN_W = 360
        private const val WIN_H = 300
        private const val MIN_W = 260
        private const val MIN_H = 180
        /** Space kept free around the window, as the config screen keeps. */
        private const val MARGIN = 8
        private const val DONE_W = AlpakaStyle.DONE_W

        private const val SECTION_H = 19
        private const val HINT_H = 13
        private const val SECTION_GAP = 6
        private const val ITEM_H = AlpakaStyle.CARD_H
        private const val ITEM_GAP = AlpakaStyle.CARD_GAP
        private const val FIELD_H = 16
        private const val KEY_W = 92
        private const val ARROW_W = 14
        private const val SMALL_BTN = AlpakaStyle.ICON_BTN

        /** As long as a chat command may be. */
        private const val MAX_LEN = 256
    }
}
