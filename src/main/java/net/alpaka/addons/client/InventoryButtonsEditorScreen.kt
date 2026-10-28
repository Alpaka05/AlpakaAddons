package net.alpaka.addons.client

import net.alpaka.addons.client.InventoryButtonsSkin as Skin
import net.alpaka.addons.client.gui.EditableText
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaConfig.InventoryButton
import net.alpaka.addons.features.inventorybuttons.InventoryButtonIcons
import net.alpaka.addons.features.inventorybuttons.InventoryButtonLayout
import net.alpaka.addons.features.inventorybuttons.InventoryButtonLayout.Box
import net.alpaka.addons.features.inventorybuttons.InventoryButtonsFeature
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

/**
 * Where the inventory buttons are set up.
 *
 * The whole screen is the inventory as the game shows it: a container panel in the middle, drawn
 * at the size and place the real one has, and the buttons around it where they will be. A button
 * is picked by clicking it and moved by dragging it anywhere on the screen; it snaps to the panel's
 * edges and to the other buttons, and Shift lets it go free. The panel can be switched between the
 * inventory and the two chest sizes, since a button keeps to the panel edge it is nearest to.
 *
 * The settings sit in a panel at the side, away from the picked button: the list of buttons, or the
 * picked one's command, tooltip, where it shows, and its icon from a searchable grid of every item.
 */
class InventoryButtonsEditorScreen @JvmOverloads constructor(
    private val parent: Screen?,
    /** A button to open with picked, by its place in the list; -1 for none. */
    private val openOn: Int = -1,
) : Screen(GuiFont.text("Inventory Buttons")) {

    private enum class Preview(val label: String, val imageHeight: Int, val rows: Int) {
        INVENTORY("Inventory", 166, 0),
        SMALL_CHEST("Small Chest", 114 + 3 * 18, 3),
        LARGE_CHEST("Large Chest", 114 + 6 * 18, 6),
    }

    private enum class Field { NONE, COMMAND, LABEL, SEARCH }

    private val buttons: MutableList<InventoryButton>
        get() = AlpakaConfig.instance.inventoryButtons ?: ArrayList<InventoryButton>().also { AlpakaConfig.instance.inventoryButtons = it }

    private var preview = Preview.INVENTORY
    private var selected: InventoryButton? = null

    private val commandText = EditableText(256)
    private val labelText = EditableText(64)
    private val searchText = EditableText(40)
    private var focus = Field.NONE

    /** The picker's full list as last searched; it grows once SkyBlock's items have loaded. */
    private var searchedIn: List<String>? = null
    private var icons: List<String> = emptyList()
    private var iconQuery: String? = null
    private var iconScroll = 0
    private var listScroll = 0

    /** The dragged button, where on it the mouse took hold, and whether it has moved yet. */
    private var dragging: InventoryButton? = null
    private var grabX = 0
    private var grabY = 0
    private var pressX = 0.0
    private var pressY = 0.0
    private var moved = false
    private var snappedX = false
    private var snappedY = false

    private var panelLeft = false

    // ----------------------------------------------------------------- layout

    private fun box(): Box {
        val h = preview.imageHeight
        return Box((width - IMAGE_W) / 2, (height - h) / 2, IMAGE_W, h)
    }

    private fun panelW() = minOf(PANEL_W, maxOf(120, (width - IMAGE_W) / 2 - 12))
    private fun panelH() = minOf(height - 2 * MARGIN, PANEL_MAX_H)
    private fun panelX() = if (panelLeft) MARGIN else width - MARGIN - panelW()
    private fun panelY() = (height - panelH()) / 2
    private fun inPanel(mx: Double, my: Double) =
        mx >= panelX() && mx < panelX() + panelW() && my >= panelY() && my < panelY() + panelH()

    private fun contentX() = panelX() + Skin.PAD
    private fun contentW() = panelW() - 2 * Skin.PAD
    private fun bodyY() = panelY() + Skin.HEADER_H + 6

    private fun doneX() = panelX() + panelW() - DONE_W - 8
    private fun doneY() = panelY() + (Skin.HEADER_H - Skin.BUTTON_H) / 2

    /** The preview switch, at the top of the body. */
    private fun previewY() = bodyY()

    // The list view.
    private fun listY() = previewY() + ROW_STEP + 4
    private fun addY() = panelY() + panelH() - Skin.PAD - Skin.BUTTON_H
    private fun hintY() = addY() - 12
    private fun listH() = hintY() - 4 - listY()

    // The edit view: a back link with the delete button, then one labelled control per line.
    private fun backY() = previewY() + ROW_STEP + 4
    private fun commandY() = backY() + ROW_STEP
    private fun labelY() = commandY() + ROW_STEP
    private fun showInY() = labelY() + ROW_STEP
    private fun searchY() = showInY() + ROW_STEP
    private fun fieldX() = contentX() + labelW()
    private fun fieldW() = contentW() - labelW()
    /** The labels' column: as wide as the widest label in the menu font, which differs by font. */
    private fun labelW() = LABELS.maxOf { GuiFont.width(font, it) } + 6
    private fun gridY() = searchY() + ROW_STEP
    private fun gridH() = panelY() + panelH() - Skin.PAD - gridY()
    private fun gridCols() = maxOf(1, (contentW() - 4) / CELL)
    private fun gridRows() = maxOf(1, (gridH() - 4) / CELL)

    private fun inside(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h

    // -------------------------------------------------------------- selection

    private fun select(button: InventoryButton?) {
        commitText()
        selected = button
        focus = Field.NONE
        if (button != null) {
            commandText.setText(button.command ?: "/")
            labelText.setText(button.label ?: "")
            searchText.setText("")
            iconQuery = null
            scrollIconsTo(button.icon)
        }
    }

    /** Writes the fields back into the picked button. */
    private fun commitText() {
        val button = selected ?: return
        button.command = commandText.text.trim()
        button.label = labelText.text.trim()
    }

    private fun filteredIcons(): List<String> {
        val q = searchText.text
        val all = InventoryButtonIcons.all()
        if (q != iconQuery || all !== searchedIn) {
            if (q != iconQuery) iconScroll = 0
            icons = InventoryButtonIcons.search(all, q)
            iconQuery = q
            searchedIn = all
        }
        return icons
    }

    private fun scrollIconsTo(icon: String?) {
        val list = filteredIcons()
        val index = list.indexOf(icon)
        if (index < 0) return
        val row = index / gridCols()
        iconScroll = maxOf(0, row - gridRows() / 2)
    }

    private fun addButton() {
        val box = box()
        val button = InventoryButton("/", "", "minecraft:paper", 0, 0)
        // Down the left of the panel, under the last button there, or the first free spot of that column.
        val taken = buttons.map { InventoryButtonLayout.place(it, box, width, height) }
        var y = box.top
        while (taken.any { it[0] == box.left - InventoryButtonLayout.SIZE - InventoryButtonLayout.GAP && it[1] == y } &&
            y + InventoryButtonLayout.SIZE < height) {
            y += InventoryButtonLayout.SIZE + InventoryButtonLayout.GAP
        }
        InventoryButtonLayout.store(button, box.left - InventoryButtonLayout.SIZE - InventoryButtonLayout.GAP, y, box)
        buttons.add(button)
        AlpakaConfig.save()
        select(button)
        focus = Field.COMMAND
        commandText.selectAll()
    }

    private fun deleteSelected() {
        val button = selected ?: return
        buttons.remove(button)
        selected = null
        focus = Field.NONE
        AlpakaConfig.save()
    }

    override fun init() {
        if (openOn in buttons.indices && selected == null) select(buttons[openOn])
    }

    override fun onClose() {
        minecraft?.gui?.setScreen(parent)
    }

    override fun removed() {
        commitText()
        AlpakaConfig.save()
    }

    // -------------------------------------------------------------- rendering

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Skin.backdrop(graphics, width, height)
        val box = box()
        drawContainer(graphics, box)

        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val overPanel = inPanel(mx, my)
        val hovered = if (overPanel || dragging != null) null else buttonAt(mx, my)

        if (dragging != null && moved) {
            val d = dragging!!
            val p = InventoryButtonLayout.place(d, box, width, height)
            if (snappedX) graphics.fill(p[0], 0, p[0] + 1, height, Skin.accent() and 0x00FFFFFF or 0x80000000.toInt())
            if (snappedY) graphics.fill(0, p[1], width, p[1] + 1, Skin.accent() and 0x00FFFFFF or 0x80000000.toInt())
        }

        for (button in buttons) {
            val p = InventoryButtonLayout.place(button, box, width, height)
            InventoryButtonsFeature.drawButton(graphics, button, p[0], p[1], button === hovered)
            if (button === selected) Skin.outline(graphics, p[0] - 2, p[1] - 2, InventoryButtonLayout.SIZE + 4, InventoryButtonLayout.SIZE + 4, Skin.accent())
        }

        if (dragging == null) panelLeft = selected?.let { InventoryButtonLayout.place(it, box, width, height)[0] + InventoryButtonLayout.SIZE / 2 > width / 2 } ?: false
        drawPanel(graphics, mx, my)

        if (hovered != null) {
            val lines = ArrayList<net.minecraft.network.chat.Component>()
            if (!hovered.label.isNullOrBlank()) lines.add(GuiFont.text(hovered.label))
            lines.add(GuiFont.text(InventoryButtonsFeature.displayCommand(hovered.command)))
            graphics.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mouseX, mouseY)
        }
    }

    /** The container as the game draws it, so the buttons can be placed against the real thing. */
    private fun drawContainer(graphics: GuiGraphicsExtractor, box: Box) {
        if (preview == Preview.INVENTORY) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, AbstractContainerScreen.INVENTORY_LOCATION, box.left, box.top, 0f, 0f, IMAGE_W, box.height, 256, 256)
            graphics.text(font, GuiFont.text("Crafting"), box.left + 97, box.top + 8, 0xFF404040.toInt(), false)
        } else {
            val top = preview.rows * 18 + 17
            graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST_LOCATION, box.left, box.top, 0f, 0f, IMAGE_W, top, 256, 256)
            graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST_LOCATION, box.left, box.top + top, 0f, 126f, IMAGE_W, 96, 256, 256)
            graphics.text(font, GuiFont.text(if (preview.rows == 6) "Large Chest" else "Chest"), box.left + 8, box.top + 6, 0xFF404040.toInt(), false)
            graphics.text(font, GuiFont.text("Inventory"), box.left + 8, box.top + top + 2, 0xFF404040.toInt(), false)
        }
    }

    private fun drawPanel(graphics: GuiGraphicsExtractor, mx: Double, my: Double) {
        val x = panelX()
        val y = panelY()
        val w = panelW()
        Skin.panel(graphics, x, y, w, panelH())
        // The full title only where it clears the Done button; a narrow panel says just "Buttons".
        val title = if (GuiFont.width(font, "Inventory Buttons") + DONE_W + Skin.PAD + 14 <= w) "Inventory Buttons" else "Buttons"
        Skin.header(graphics, font, title, x, y, w)
        Skin.button(graphics, font, doneX(), doneY(), DONE_W, Skin.BUTTON_H, "Done", inside(mx, my, doneX(), doneY(), DONE_W, Skin.BUTTON_H), Skin.PRIMARY)

        val cx = contentX()
        val cw = contentW()
        Skin.label(graphics, font, "Preview", cx, previewY() + 5)
        Skin.button(graphics, font, fieldX(), previewY(), fieldW(), Skin.BUTTON_H, preview.label,
            inside(mx, my, fieldX(), previewY(), fieldW(), Skin.BUTTON_H), Skin.PLAIN)

        val button = selected
        if (button == null) drawList(graphics, mx, my, cx, cw) else drawEdit(graphics, button, mx, my, cx, cw)
    }

    private fun drawList(graphics: GuiGraphicsExtractor, mx: Double, my: Double, cx: Int, cw: Int) {
        val top = listY()
        val h = listH()
        val visible = maxOf(1, h / LIST_ROW)
        listScroll = listScroll.coerceIn(0, maxOf(0, buttons.size - visible))
        if (buttons.isEmpty()) {
            Skin.label(graphics, font, "No buttons yet.", cx + 2, top + 4)
        }
        graphics.enableScissor(cx, top, cx + cw, top + h)
        for (i in listScroll until minOf(buttons.size, listScroll + visible + 1)) {
            val b = buttons[i]
            val ry = top + (i - listScroll) * LIST_ROW
            val hover = inside(mx, my, cx, ry, cw, LIST_ROW - 2) && my < top + h
            Skin.row(graphics, cx, ry, cw, LIST_ROW - 2, hover, false)
            InventoryButtonsFeature.drawIcon(graphics, b.icon, cx + 2, ry + 1)
            val name = if (b.label.isNullOrBlank()) InventoryButtonsFeature.displayCommand(b.command) else b.label
            Skin.label(graphics, font, Skin.ellipsize(font, name, cw - 26), cx + 22, ry + 5, Skin.TEXT)
        }
        graphics.disableScissor()

        val hint = "Drag to move. Shift: no snapping."
        Skin.label(graphics, font, if (GuiFont.width(font, hint) <= cw) hint else "Drag to move.", cx, hintY())
        Skin.button(graphics, font, cx, addY(), cw, Skin.BUTTON_H, "+ Add Button", inside(mx, my, cx, addY(), cw, Skin.BUTTON_H), Skin.PRIMARY)
    }

    private fun drawEdit(graphics: GuiGraphicsExtractor, button: InventoryButton, mx: Double, my: Double, cx: Int, cw: Int) {
        val backHover = inside(mx, my, cx, backY(), cw - DELETE_W - 4, Skin.BUTTON_H)
        Skin.label(graphics, font, "‹ Back", cx, backY() + 5, if (backHover) Skin.accent() else Skin.TEXT_MUTED)
        val delX = cx + cw - DELETE_W
        Skin.button(graphics, font, delX, backY(), DELETE_W, Skin.BUTTON_H, "✕", inside(mx, my, delX, backY(), DELETE_W, Skin.BUTTON_H), Skin.DANGER)

        val fx = fieldX()
        val fw = fieldW()
        Skin.label(graphics, font, "Command", cx, commandY() + 5)
        Skin.textField(graphics, font, fx, commandY(), fw, Skin.BUTTON_H, commandText.text, "/command",
            focus == Field.COMMAND, inside(mx, my, fx, commandY(), fw, Skin.BUTTON_H))
        Skin.label(graphics, font, "Tooltip", cx, labelY() + 5)
        Skin.textField(graphics, font, fx, labelY(), fw, Skin.BUTTON_H, labelText.text, "Optional",
            focus == Field.LABEL, inside(mx, my, fx, labelY(), fw, Skin.BUTTON_H))
        Skin.label(graphics, font, "Show In", cx, showInY() + 5)
        Skin.button(graphics, font, fx, showInY(), fw, Skin.BUTTON_H, SHOW_IN[button.showIn.coerceIn(0, SHOW_IN.size - 1)],
            inside(mx, my, fx, showInY(), fw, Skin.BUTTON_H), Skin.PLAIN)
        Skin.label(graphics, font, "Icon", cx, searchY() + 5)
        Skin.textField(graphics, font, fx, searchY(), fw, Skin.BUTTON_H, searchText.text, "Search",
            focus == Field.SEARCH, inside(mx, my, fx, searchY(), fw, Skin.BUTTON_H))

        // The icon grid.
        val gy = gridY()
        val gh = gridH()
        if (gh < CELL) return
        Skin.well(graphics, cx, gy, cw, gh)
        val list = filteredIcons()
        val cols = gridCols()
        val rows = gridRows()
        val totalRows = (list.size + cols - 1) / cols
        iconScroll = iconScroll.coerceIn(0, maxOf(0, totalRows - rows))
        val gx = cx + (cw - cols * CELL) / 2
        var hoveredIcon: String? = null
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val index = (iconScroll + r) * cols + c
                if (index >= list.size) break
                val id = list[index]
                val x = gx + c * CELL
                val y = gy + 2 + r * CELL
                val hover = inside(mx, my, x, y, CELL, CELL)
                if (id == button.icon) graphics.fill(x, y, x + CELL, y + CELL, Skin.accent() and 0x00FFFFFF or 0x55000000)
                else if (hover) graphics.fill(x, y, x + CELL, y + CELL, 0x30FFFFFF)
                if (hover) hoveredIcon = id
                InventoryButtonsFeature.drawIcon(graphics, id, x + 1, y + 1)
            }
        }
        if (list.isEmpty()) Skin.label(graphics, font, "No item matches.", cx + 4, gy + 6)
        if (totalRows > rows) {
            val trackH = gh - 4
            val thumbH = maxOf(8, trackH * rows / totalRows)
            val thumbY = gy + 2 + (trackH - thumbH) * iconScroll / maxOf(1, totalRows - rows)
            graphics.fill(cx + cw - 3, thumbY, cx + cw - 1, thumbY + thumbH, Skin.LINE)
        }
        if (hoveredIcon != null) {
            graphics.setTooltipForNextFrame(GuiFont.text(InventoryButtonIcons.name(hoveredIcon)), mx.toInt(), my.toInt())
        }
    }

    // ------------------------------------------------------------------ input

    private fun buttonAt(mx: Double, my: Double): InventoryButton? {
        val box = box()
        for (button in buttons.asReversed()) {
            val p = InventoryButtonLayout.place(button, box, width, height)
            if (inside(mx, my, p[0], p[1], InventoryButtonLayout.SIZE, InventoryButtonLayout.SIZE)) return button
        }
        return null
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mx = event.x()
        val my = event.y()
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick)

        if (inPanel(mx, my)) {
            panelClick(mx, my)
            return true
        }

        val hit = buttonAt(mx, my)
        if (hit != null) {
            if (hit !== selected) select(hit)
            focus = Field.NONE
            val p = InventoryButtonLayout.place(hit, box(), width, height)
            dragging = hit
            grabX = mx.toInt() - p[0]
            grabY = my.toInt() - p[1]
            pressX = mx
            pressY = my
            moved = false
            return true
        }
        select(null)
        return true
    }

    private fun panelClick(mx: Double, my: Double) {
        val previousFocus = focus
        focus = Field.NONE
        if (inside(mx, my, doneX(), doneY(), DONE_W, Skin.BUTTON_H)) {
            Skin.click()
            onClose()
            return
        }
        if (inside(mx, my, fieldX(), previewY(), fieldW(), Skin.BUTTON_H)) {
            Skin.click()
            preview = Preview.entries[(preview.ordinal + 1) % Preview.entries.size]
            return
        }
        val cx = contentX()
        val cw = contentW()
        val button = selected
        if (button == null) {
            if (inside(mx, my, cx, addY(), cw, Skin.BUTTON_H)) {
                Skin.click()
                addButton()
                return
            }
            if (inside(mx, my, cx, listY(), cw, listH())) {
                val index = listScroll + ((my - listY()) / LIST_ROW).toInt()
                if (index in buttons.indices) {
                    Skin.click()
                    select(buttons[index])
                }
            }
            return
        }

        if (inside(mx, my, cx + cw - DELETE_W, backY(), DELETE_W, Skin.BUTTON_H)) {
            Skin.click()
            deleteSelected()
            return
        }
        if (inside(mx, my, cx, backY(), cw - DELETE_W - 4, Skin.BUTTON_H)) {
            Skin.click()
            select(null)
            return
        }
        val fx = fieldX()
        val fw = fieldW()
        when {
            inside(mx, my, fx, commandY(), fw, Skin.BUTTON_H) -> focusField(Field.COMMAND, commandText, mx - fx - 4, previousFocus)
            inside(mx, my, fx, labelY(), fw, Skin.BUTTON_H) -> focusField(Field.LABEL, labelText, mx - fx - 4, previousFocus)
            inside(mx, my, fx, searchY(), fw, Skin.BUTTON_H) -> focusField(Field.SEARCH, searchText, mx - fx - 4, previousFocus)
            inside(mx, my, fx, showInY(), fw, Skin.BUTTON_H) -> {
                Skin.click()
                button.showIn = (button.showIn + 1) % SHOW_IN.size
                AlpakaConfig.save()
            }
            inside(mx, my, cx, gridY(), cw, gridH()) -> {
                val cols = gridCols()
                val gx = cx + (cw - cols * CELL) / 2
                val c = ((mx - gx) / CELL).toInt()
                val r = ((my - gridY() - 2) / CELL).toInt()
                if (mx < gx || c !in 0 until cols || r !in 0 until gridRows()) return
                val index = (iconScroll + r) * cols + c
                val list = filteredIcons()
                if (index in list.indices) {
                    Skin.click()
                    button.icon = list[index]
                    AlpakaConfig.save()
                }
            }
        }
    }

    private fun focusField(field: Field, text: EditableText, localX: Double, previousFocus: Field) {
        focus = field
        // A click into a field that already had the focus places the caret; the first one selects
        // nothing and puts it at the end, ready to type on.
        if (previousFocus == field) text.setCaretFromX(localX.toInt()) { GuiFont.width(font, it) }
        else text.keyPressed(GLFW.GLFW_KEY_END, false, false)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val button = dragging ?: return super.mouseDragged(event, deltaX, deltaY)
        if (!moved && kotlin.math.abs(event.x() - pressX) < 2 && kotlin.math.abs(event.y() - pressY) < 2) return true
        moved = true
        val box = box()
        var x = event.x().toInt() - grabX
        var y = event.y().toInt() - grabY
        snappedX = false
        snappedY = false
        if (!event.hasShiftDown()) {
            val others = buttons.filter { it !== button }.map { InventoryButtonLayout.place(it, box, width, height) }
            val s = InventoryButtonLayout.snap(x, y, box, others)
            x = s[0]
            y = s[1]
            snappedX = s[2] == 1
            snappedY = s[3] == 1
        }
        x = x.coerceIn(0, width - InventoryButtonLayout.SIZE)
        y = y.coerceIn(0, height - InventoryButtonLayout.SIZE)
        InventoryButtonLayout.store(button, x, y, box)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging != null && event.button() == 0) {
            if (moved) AlpakaConfig.save()
            dragging = null
            moved = false
            snappedX = false
            snappedY = false
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val step = if (scrollY > 0) -1 else 1
        if (selected != null && inside(mouseX, mouseY, contentX(), gridY(), contentW(), gridH())) {
            iconScroll = maxOf(0, iconScroll + step)
            return true
        }
        if (selected == null && inside(mouseX, mouseY, contentX(), listY(), contentW(), listH())) {
            listScroll = maxOf(0, listScroll + step)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun focusedText(): EditableText? = when (focus) {
        Field.COMMAND -> commandText
        Field.LABEL -> labelText
        Field.SEARCH -> searchText
        Field.NONE -> null
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val text = focusedText() ?: return super.charTyped(event)
        val cp = event.codepoint()
        if (Character.isBmpCodePoint(cp) && text.insert(cp.toChar())) afterEdit()
        return true
    }

    private fun afterEdit() {
        commitText()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()
        val text = focusedText()
        if (text != null) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    focus = Field.NONE
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                    // On to the next field, the way a form goes.
                    focus = when (focus) {
                        Field.COMMAND -> Field.LABEL
                        Field.LABEL -> Field.SEARCH
                        else -> Field.NONE
                    }
                    focusedText()?.keyPressed(GLFW.GLFW_KEY_END, false, false)
                    return true
                }
            }
            if (text.keyPressed(key, event.hasControlDownWithQuirk(), event.hasShiftDown())) {
                afterEdit()
                return true
            }
            return true
        }

        val button = selected
        if (button != null) {
            val step = if (event.hasShiftDown()) 10 else 1
            val dx = when (key) { GLFW.GLFW_KEY_LEFT -> -step; GLFW.GLFW_KEY_RIGHT -> step; else -> 0 }
            val dy = when (key) { GLFW.GLFW_KEY_UP -> -step; GLFW.GLFW_KEY_DOWN -> step; else -> 0 }
            if (dx != 0 || dy != 0) {
                val box = box()
                val p = InventoryButtonLayout.place(button, box, width, height)
                InventoryButtonLayout.store(button,
                    (p[0] + dx).coerceIn(0, width - InventoryButtonLayout.SIZE),
                    (p[1] + dy).coerceIn(0, height - InventoryButtonLayout.SIZE), box)
                AlpakaConfig.save()
                return true
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                deleteSelected()
                return true
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                select(null)
                return true
            }
        }
        return super.keyPressed(event)
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private val CHEST_LOCATION: Identifier = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png")
        private val SHOW_IN = arrayOf("All", "Inventory", "Chests")
        private val LABELS = listOf("Preview", "Command", "Tooltip", "Show In", "Icon")

        private const val IMAGE_W = 176
        private const val MARGIN = 6
        private const val PANEL_W = 168
        private const val PANEL_MAX_H = 320
        private const val DONE_W = 44
        private const val DELETE_W = 18
        private const val ROW_STEP = 22
        private const val LIST_ROW = 20
        private const val CELL = 18
    }
}
