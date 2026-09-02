package net.alpaka.addons.features.wheel

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class CommandWheelScreen : Screen(Component.literal("Quick Command Menu")) {
    private var selectedIndex = -1

    override fun isPauseScreen(): Boolean = false

    override fun keyReleased(event: KeyEvent): Boolean {
        val key = CommandWheelFeature.COMMAND_WHEEL_KEY
        if (key != null && key.matches(event)) {
            executeSelectedCommandAndClose()
            return true
        }
        return super.keyReleased(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() == 0 && selectedIndex >= 0) {
            executeSelectedCommandAndClose()
            return true
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    private fun executeSelectedCommandAndClose() {
        val commands = AlpakaConfig.instance.commandWheelCommands
        val mc = this.minecraft ?: Minecraft.getInstance()
        val player = mc.player
        if (!commands.isNullOrEmpty() && selectedIndex in commands.indices && player != null) {
            var cmd = commands[selectedIndex]
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1)
            }
            player.connection.sendCommand(cmd)
        }
        mc.gui.setScreen(null)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)

        // Soft dark backdrop
        graphics.fill(0, 0, this.width, this.height, 0x65000000.toInt())

        val cx = this.width / 2
        val cy = this.height / 2

        val commands = AlpakaConfig.instance.commandWheelCommands
        if (commands.isNullOrEmpty()) {
            graphics.centeredText(this.font, Component.literal("No quick commands set! Add commands in Alpaka Config."), cx, cy, 0xFFFFFFFF.toInt())
            return
        }

        val count = commands.size

        // Center dot / crosshair
        ModernGuiUtils.drawRect(graphics, cx - 2, cy - 2, 4, 4, ModernGuiUtils.getAccentColor())
        ModernGuiUtils.drawOutline(graphics, cx - 3, cy - 3, 6, 6, 0xFF0E1015.toInt())

        // Dynamic elliptical radius based on command count to prevent top & bottom card overlap
        val baseRadius = max(85.0, 60.0 + count * 7.5)
        val rx = baseRadius * 1.45
        val ry = baseRadius * 1.05
        val sectorAngle = 2.0 * Math.PI / count

        selectedIndex = -1
        var bestDistSq = Double.MAX_VALUE

        // Position & Hover Detection Pass
        for (i in 0 until count) {
            val cmd = commands[i]
            val angle = -Math.PI / 2.0 + i * sectorAngle

            val boxW = max(76, this.font.width(cmd) + 18)
            val boxH = 22

            val boxCx = cx + (cos(angle) * rx).roundToInt()
            val boxCy = cy + (sin(angle) * ry).roundToInt()

            val bx = boxCx - boxW / 2
            val by = boxCy - boxH / 2

            val isDirectHover = mouseX >= bx && mouseX <= bx + boxW && mouseY >= by && mouseY <= by + boxH

            if (isDirectHover) {
                selectedIndex = i
                break
            } else {
                val dSq = (mouseX - boxCx) * (mouseX - boxCx) + (mouseY - boxCy) * (mouseY - boxCy).toDouble()
                if (dSq < bestDistSq && sqrt(dSq) < 85.0) {
                    bestDistSq = dSq
                    selectedIndex = i
                }
            }
        }

        // Render Command Cards Pass
        for (i in 0 until count) {
            val cmd = commands[i]
            val isSelected = (i == selectedIndex)

            val angle = -Math.PI / 2.0 + i * sectorAngle
            val boxW = max(76, this.font.width(cmd) + 18)
            val boxH = 22

            val boxCx = cx + (cos(angle) * rx).roundToInt()
            val boxCy = cy + (sin(angle) * ry).roundToInt()

            val bx = boxCx - boxW / 2
            val by = boxCy - boxH / 2

            val bg = if (isSelected) ModernGuiUtils.COLOR_CARD_BG_HOVER else ModernGuiUtils.COLOR_CARD_BG
            val border = if (isSelected) ModernGuiUtils.getAccentColor() else ModernGuiUtils.COLOR_CARD_BORDER
            val textColor = if (isSelected) ModernGuiUtils.getAccentColor() else ModernGuiUtils.COLOR_TEXT_PRIMARY

            graphics.pose().pushMatrix()
            if (isSelected) {
                graphics.pose().scaleAround(1.08f, 1.08f, boxCx.toFloat(), boxCy.toFloat())
            }

            ModernGuiUtils.drawRect(graphics, bx, by, boxW, boxH, bg)
            ModernGuiUtils.drawOutline(graphics, bx, by, boxW, boxH, border)

            val textX = bx + (boxW - this.font.width(cmd)) / 2
            val textY = by + (boxH - 8) / 2
            graphics.text(this.font, Component.literal(cmd), textX, textY, textColor)

            graphics.pose().popMatrix()
        }
    }
}
