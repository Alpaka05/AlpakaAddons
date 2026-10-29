package net.alpaka.addons.client

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind
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
    private fun leftW(kind: Kind) = if (kind == Kind.KEY) KEY_W else ALIAS_W
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
        val aliasDupes = duplicateAliases()
        val keyDupes = duplicateKeys()
        for (row in rows) {
            val y = f.listY + row.y - scrollY.toInt()
            if (y + row.height < f.listY || y > f.listY + f.listH) continue
            when (row.kind) {
                Kind.SECTION -> AlpakaStyle.groupLabel(graphics, this.font, row.text, rowX + 4, y + 8)
                Kind.HINT -> graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, row.text, rowW - 8)),
                    rowX + 4, y + 1, AlpakaStyle.TEXT_SOFT, false)
                Kind.ALIAS, Kind.KEY -> drawEntryRow(graphics, row, rowX, y, rowW, mouseX, mouseY, inList,
                    if (row.kind == Kind.ALIAS) row.index in aliasDupes else row.index in keyDupes)
                Kind.ADD_ALIAS, Kind.ADD_KEY -> {
                    val hovered = inList && inside(mouseX, mouseY, rowX, y, rowW, row.height)
                    val accent = ModernGuiUtils.getAccentColor()
                    ModernGuiUtils.drawRoundedOutline(graphics, rowX, y, rowW, row.height, AlpakaStyle.CARD_RADIUS,
                        if (hovered) accent else AlpakaStyle.DIVIDER)
                    val label = if (row.kind == Kind.ADD_ALIAS) "Add Alias" else "Add Key Shortcut"
                    val color = if (hovered) accent else AlpakaStyle.TEXT_MUTED
                    val lw = GuiFont.width(this.font, label)
                    val cx = rowX + rowW / 2
                    AlpakaStyle.plus(graphics, (cx - lw / 2 - 6).toFloat(), y + row.height / 2f, 3f, color)
                    graphics.text(this.font, GuiFont.text(label), cx - lw / 2 + 2, y + (row.height - 8) / 2, color, false)
                }
            }
        }
        AlpakaStyle.scrollFades(graphics, f.listX, f.listY, f.listW, f.listH, scrollY > 0.5, scrollY < maxScrollY - 0.5)
        graphics.disableScissor()

        AlpakaStyle.endOpen(graphics, pushed)
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics)
    }

    private fun drawEntryRow(graphics: GuiGraphicsExtractor, row: Row, x: Int, y: Int, w: Int, mouseX: Int, mouseY: Int,
                             inList: Boolean, duplicate: Boolean) {
        val hovered = inList && inside(mouseX, mouseY, x, y, w, row.height)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, row.height, AlpakaStyle.CARD_RADIUS, if (hovered) AlpakaStyle.ROW_HOVER else AlpakaStyle.ROW)

        val fieldX = x + 3
        val fieldY = y + (row.height - FIELD_H) / 2
        val leftW = leftW(row.kind) - 3
        val hoverLeft = inList && inside(mouseX, mouseY, fieldX, fieldY, leftW, FIELD_H)
        if (row.kind == Kind.ALIAS) {
            val alias = aliases[row.index]
            val focused = focusKind == Kind.ALIAS && focusIndex == row.index && focusColumn == 0
            AlpakaStyle.textField(graphics, this.font, fieldX, fieldY, leftW, FIELD_H, alias.alias, "/alias", focused, hoverLeft, caretFrom)
        } else {
            val shortcut = keys[row.index]
            val capturing = capturingKey == row.index
            val label = if (capturing) "Press a Key..." else CommandShortcuts.keyLabel(shortcut)
            val shown = AlpakaStyle.ellipsize(this.font, label, leftW - 8)
            if (capturing) {
                AlpakaStyle.button(graphics, this.font, fieldX, fieldY, leftW, FIELD_H, shown, true, ButtonKind.PRIMARY)
            } else {
                // Set off from the row like the fields beside it, since it sits on the same row color.
                ModernGuiUtils.drawRoundedRect(graphics, fieldX, fieldY, leftW, FIELD_H, AlpakaStyle.FIELD_RADIUS,
                    if (hoverLeft) AlpakaStyle.ROW_SELECTED else AlpakaStyle.FIELD)
                ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(shown), fieldX + leftW / 2, fieldY + (FIELD_H - 8) / 2,
                    if (shortcut.key.isNullOrEmpty()) AlpakaStyle.TEXT_MUTED else AlpakaStyle.TEXT)
            }
        }
        // Only the first of two equal aliases or keys counts; the later one is underlined red.
        if (duplicate) ModernGuiUtils.drawRect(graphics, fieldX + 3, fieldY + FIELD_H - 1, leftW - 6, 1, AlpakaStyle.DANGER)

        AlpakaStyle.sideChevron(graphics, x + leftW(row.kind) + ARROW_W / 2, fieldY + FIELD_H / 2, false, AlpakaStyle.TEXT_MUTED)

        val cmdX = commandX(x, row.kind)
        val cmdW = commandW(x, w, row.kind)
        val command = if (row.kind == Kind.ALIAS) aliases[row.index].command else keys[row.index].command
        val cmdFocused = focusKind == row.kind && focusIndex == row.index && focusColumn == 1
        AlpakaStyle.textField(graphics, this.font, cmdX, fieldY, cmdW, FIELD_H, command, "/command",
            cmdFocused, inList && inside(mouseX, mouseY, cmdX, fieldY, cmdW, FIELD_H), caretFrom)

        val dx = delX(x, w)
        val btnY = y + (row.height - SMALL_BTN) / 2
        AlpakaStyle.trashButton(graphics, dx, btnY, SMALL_BTN, inList && inside(mouseX, mouseY, dx, btnY, SMALL_BTN, SMALL_BTN))
    }

    /** Alias rows that repeat an earlier alias, which therefore never apply. */
    private fun duplicateAliases(): Set<Int> {
        val seen = HashSet<String>()
        val dupes = HashSet<Int>()
        aliases.forEachIndexed { i, a ->
            val name = CommandShortcuts.aliasName(a.alias)
            if (name.isNotEmpty() && !seen.add(name)) dupes.add(i)
        }
        return dupes
    }

    /** Key rows bound to the same keys as an earlier one. */
    private fun duplicateKeys(): Set<Int> {
        val seen = HashSet<String>()
        val dupes = HashSet<Int>()
        keys.forEachIndexed { i, k ->
            if (!k.key.isNullOrEmpty() && !seen.add(k.key + "|" + (k.modifiers and CommandShortcuts.MODIFIER_MASK))) dupes.add(i)
        }
        return dupes
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
                            if (row.kind == Kind.ALIAS) focus(Kind.ALIAS, row.index, 0)
                            else {
                                playPloppSound()
                                capturingKey = row.index
                            }
                        }
                        inside(mouseX, mouseY, commandX(rowX, row.kind), fieldY, commandW(rowX, rowW, row.kind), FIELD_H) ->
                            focus(row.kind, row.index, 1)
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
        val current = focusedText ?: return
        // An alias is a single word: spaces would never match the first word of a command.
        val clean = if (focusKind == Kind.ALIAS && focusColumn == 0) typed.replace(" ", "") else typed
        val filtered = clean.filter { it.code >= 32 && it.code != 127 && it != '§' }
        focusedText = (current + filtered).take(MAX_LEN)
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
                event.key() == GLFW.GLFW_KEY_BACKSPACE -> {
                    val current = focusedText ?: ""
                    if (current.isNotEmpty()) focusedText = current.substring(0, current.length - 1)
                    return true
                }
                event.isPaste -> {
                    insert(this.minecraft?.keyboardHandler?.clipboard ?: "")
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
        private const val DONE_W = 44

        private const val SECTION_H = 19
        private const val HINT_H = 13
        private const val SECTION_GAP = 6
        private const val ITEM_H = 20
        private const val ITEM_GAP = 3
        private const val FIELD_H = 16
        private const val ALIAS_W = 72
        private const val KEY_W = 92
        private const val ARROW_W = 14
        private const val SMALL_BTN = 14

        /** As long as a chat command may be. */
        private const val MAX_LEN = 256
    }
}
