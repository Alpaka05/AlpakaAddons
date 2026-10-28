package net.alpaka.addons.features.leapmenu

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.compliance.Outbound
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.dungeonmap.DungeonMapFeature
import net.alpaka.addons.features.dungeonmap.DungeonMapRenderer
import net.alpaka.addons.utils.DungeonState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput

/**
 * Hypixel's Spirit Leap chest, drawn as up to four big buttons in the middle of the screen: each
 * teammate's head, name and class, in the class's color. Optionally the dungeon map sits beside
 * them, and a click on a teammate there leaps to them too.
 *
 * Only the look changes. The menu, its slots and closing it are vanilla's; a click on a button is
 * one left click on that teammate's head through [Outbound.menuClick], the same click the vanilla
 * chest sends. Every other mouse event is swallowed, so nothing reaches the invisible vanilla slots
 * underneath, and dead teammates, whom Hypixel won't leap to, aren't clicked at all.
 */
class LeapMenuScreen(menu: ChestMenu, inventory: Inventory, title: Component, private val preview: Boolean = false) :
    ContainerScreen(menu, inventory, title) {

    private class Button(val target: LeapTarget, val x: Int, val y: Int)

    private val buttons = ArrayList<Button>()
    /** The teammate under the mouse, on a button or on the map. */
    private var hovered: String? = null

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xA0000000.toInt())
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val targets = LeapMenu.targets(menu).take(MAX_BUTTONS)
        val columns = if (targets.size <= 1) 1 else 2
        val rows = maxOf(1, (targets.size + 1) / 2)
        val gridW = columns * BUTTON_W + (columns - 1) * GAP
        val gridH = rows * BUTTON_H + (rows - 1) * GAP

        // The map goes left of the buttons, or above them on a narrow screen, or not at all.
        var mapSize = 0
        var beside = true
        if (showMap()) {
            val wanted = maxOf(gridH, MAP_SIZE)
            if (wanted + MAP_GAP + gridW <= width - 2 * MARGIN && wanted <= height - 2 * MARGIN - TITLE_H) {
                mapSize = wanted
            } else if (gridH + MAP_GAP + MAP_SIZE <= height - 2 * MARGIN - TITLE_H && MAP_SIZE <= width - 2 * MARGIN) {
                mapSize = MAP_SIZE
                beside = false
            }
        }
        val totalW = if (mapSize == 0) gridW else if (beside) mapSize + MAP_GAP + gridW else maxOf(mapSize, gridW)
        val totalH = if (mapSize == 0) gridH else if (beside) maxOf(mapSize, gridH) else mapSize + MAP_GAP + gridH
        val left = (width - totalW) / 2
        val top = (height - totalH + TITLE_H) / 2

        val font = font
        val heading = "Spirit Leap"
        graphics.text(font, heading, (width - font.width(heading)) / 2, top - TITLE_H, 0xFFFFFFFF.toInt(), true)

        var gridX = left + (totalW - gridW) / 2
        var gridY = top + (totalH - gridH) / 2
        var mapHovered: String? = null
        if (mapSize > 0) {
            val mapX = if (beside) left else left + (totalW - mapSize) / 2
            val mapY = if (beside) top + (totalH - mapSize) / 2 else top
            if (beside) gridX = left + mapSize + MAP_GAP else gridY = top + mapSize + MAP_GAP
            ModernGuiUtils.drawRoundedRect(graphics, mapX - 4, mapY - 4, mapSize + 8, mapSize + 8, 6, 0xD0141414.toInt())
            val buttonHovered = buttonAt(mouseX.toDouble(), mouseY.toDouble())?.target?.name
            mapHovered = DungeonMapRenderer.drawLeapMap(graphics, mapX, mapY, mapSize, mouseX, mouseY, buttonHovered,
                preview, ::mapColor)
                ?.takeIf { name -> targets.any { it.name.equals(name, true) && !it.dead } }
        }

        buttons.clear()
        if (targets.isEmpty()) {
            val text = "No Teammates To Leap To"
            graphics.text(font, text, gridX + (gridW - font.width(text)) / 2, gridY + (gridH - 8) / 2, 0xFFAAAAAA.toInt(), true)
        }
        targets.forEachIndexed { index, target ->
            // An odd last button sits centered under the others.
            val lastAlone = targets.size % 2 == 1 && index == targets.size - 1 && columns == 2
            val bx = if (lastAlone) gridX + (gridW - BUTTON_W) / 2 else gridX + (index % columns) * (BUTTON_W + GAP)
            val by = gridY + (index / columns) * (BUTTON_H + GAP)
            buttons += Button(target, bx, by)
        }
        hovered = buttonAt(mouseX.toDouble(), mouseY.toDouble())?.target?.takeUnless { it.dead }?.name ?: mapHovered
        for (button in buttons) drawButton(graphics, button)
    }

    private fun drawButton(graphics: GuiGraphicsExtractor, button: Button) {
        val target = button.target
        val dungeonClass = LeapMenu.classOf(target.name)
        val classColor = dungeonClass?.color ?: UNKNOWN
        val lit = !target.dead && target.name.equals(hovered, true)
        val fill = when {
            target.dead -> 0xF0303030.toInt()
            lit -> ModernGuiUtils.lerpColor(classColor, 0xFF000000.toInt(), 0.18f)
            else -> ModernGuiUtils.lerpColor(classColor, 0xFF000000.toInt(), 0.42f)
        }
        val border = if (target.dead) 0xFF505050.toInt() else if (lit) 0xFFFFFFFF.toInt() else classColor
        ModernGuiUtils.drawRoundedRect(graphics, button.x - 1, button.y - 1, BUTTON_W + 2, BUTTON_H + 2, 7, border)
        ModernGuiUtils.drawRoundedRect(graphics, button.x, button.y, BUTTON_W, BUTTON_H, 6, fill)

        val headX = button.x + PAD
        val headY = button.y + (BUTTON_H - HEAD) / 2
        graphics.fill(headX - 1, headY - 1, headX + HEAD + 1, headY + HEAD + 1, 0x80000000.toInt())
        val profile = target.profile
        if (profile != null) PlayerFaceExtractor.extractRenderState(graphics, profile, headX, headY, HEAD)
        else PlayerFaceExtractor.extractRenderState(graphics,
            DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(target.name)), headX, headY, HEAD)
        if (target.dead) graphics.fill(headX, headY, headX + HEAD, headY + HEAD, 0x90202020.toInt())

        val textX = headX + HEAD + PAD
        val font = font
        graphics.text(font, target.name, textX, button.y + BUTTON_H / 2 - 10,
            if (target.dead) 0xFF9A9A9A.toInt() else 0xFFFFFFFF.toInt(), true)
        val line = if (target.dead) "Dead" else dungeonClass?.label ?: "Unknown Class"
        graphics.text(font, line, textX, button.y + BUTTON_H / 2 + 2,
            if (target.dead) 0xFFFF5555.toInt() else 0xFFE6E6E6.toInt(), true)
    }

    private fun mapColor(name: String): Int = LeapMenu.classOf(name)?.color ?: UNKNOWN

    private fun showMap(): Boolean = AlpakaConfig.instance.leapMenuShowMap &&
        (preview || !DungeonState.inBossRoom && DungeonMapFeature.snapshot.colors != null)

    private fun buttonAt(x: Double, y: Double): Button? =
        buttons.firstOrNull { x >= it.x && x < it.x + BUTTON_W && y >= it.y && y < it.y + BUTTON_H }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return true
        val name = hovered ?: return true
        val target = buttons.firstOrNull { it.target.name.equals(name, true) }?.target ?: return true
        if (!target.dead && !preview) Outbound.menuClick(menu, target.slot, 0, ContainerInput.PICKUP, Outbound.Cause.MENU_CLICK)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean = true

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean = true

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean = true

    override fun keyPressed(event: KeyEvent): Boolean {
        // Vanilla's number and drop keys would act on the slot under the mouse, which isn't the one
        // drawn there, so they never reach it. Other keys are left to the game.
        if (event.isEscape || minecraft.options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        if (minecraft.options.keyDrop.matches(event) || minecraft.options.keyHotbarSlots.any { it.matches(event) } ||
            minecraft.options.keySwapOffhand.matches(event)) return true
        return false
    }

    private companion object {
        const val MAX_BUTTONS = 4
        const val BUTTON_W = 150
        const val BUTTON_H = 56
        const val GAP = 8
        const val PAD = 10
        const val HEAD = 32
        const val MAP_SIZE = 160
        const val MAP_GAP = 16
        const val MARGIN = 10
        const val TITLE_H = 16
        val UNKNOWN = 0xFF9A9A9A.toInt()
    }
}
