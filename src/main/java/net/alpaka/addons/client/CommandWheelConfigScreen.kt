package net.alpaka.addons.client

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.sound.CustomSoundFeature
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.ArrayList

class CommandWheelConfigScreen(private val parent: Screen) : Screen(Component.literal("Configure Quick Commands")) {
    private var newCommandInput: String = "/"
    private var inputFocused: Boolean = false
    private var cursorBlinkTimer: Long = 0L

    private var scrollY: Double = 0.0
    private var targetScrollY: Double = 0.0
    private var maxScrollY: Double = 0.0

    private fun playPloppSound() {
        try {
            CustomSoundFeature.playButtonClickSound()
        } catch (_: Throwable) {}
    }

    override fun onClose() {
        this.minecraft?.gui?.setScreen(this.parent)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)

        graphics.fill(0, 0, this.width, this.height, 0x80000000.toInt())

        val winW = 440
        val winH = 340
        val winX = (this.width - winW) / 2
        val winY = (this.height - winH) / 2

        // Soft Drop Shadows
        for (i in 1..6) {
            val alpha = (0x24 * (1.0f - i / 6.0f)).toInt()
            ModernGuiUtils.drawRect(graphics, winX - i, winY - i, winW + i * 2, winH + i * 2, (alpha shl 24))
        }
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_PANEL_BG)
        ModernGuiUtils.drawOutline(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_CARD_BORDER)

        // Header Bar
        val headerH = 38
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, headerH, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawRect(graphics, winX, winY + headerH - 1, winW, 1, ModernGuiUtils.getAccentColor())

        graphics.text(this.font, Component.literal("Quick Command Settings"), winX + 16, winY + 12, ModernGuiUtils.COLOR_TEXT_PRIMARY)

        // Close Button
        val closeW = 60
        val closeH = 22
        val closeX = winX + winW - closeW - 10
        val closeY = winY + 8
        val hoverClose = mouseX in closeX..(closeX + closeW) && mouseY in closeY..(closeY + closeH)
        ModernGuiUtils.drawModernButton(graphics, this.font, closeX, closeY, closeW, closeH, "Done ✕", hoverClose, false)

        // Commands List Viewport
        val listX = winX + 16
        val listY = winY + headerH + 12
        val listW = winW - 32
        val listH = 190

        ModernGuiUtils.drawRect(graphics, listX, listY, listW, listH, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawOutline(graphics, listX, listY, listW, listH, ModernGuiUtils.COLOR_CARD_BORDER)

        var commands = AlpakaConfig.instance.commandWheelCommands
        if (commands == null) {
            commands = ArrayList()
            AlpakaConfig.instance.commandWheelCommands = commands
        }

        val itemH = 30
        val itemSpacing = 6
        val totalH = commands.size * (itemH + itemSpacing) + 10
        maxScrollY = Math.max(0.0, (totalH - listH).toDouble())

        scrollY += (targetScrollY - scrollY) * 0.2

        graphics.enableScissor(listX + 2, listY + 2, listX + listW - 2, listY + listH - 2)

        var curY = listY + 6 - scrollY.toInt()
        val itemW = listW - 16

        for (i in commands.indices) {
            val cmd = commands[i]
            val hoverItem = mouseX in (listX + 8)..(listX + 8 + itemW) && mouseY in curY..(curY + itemH) && mouseY in listY..(listY + listH)

            ModernGuiUtils.drawRect(graphics, listX + 8, curY, itemW, itemH, if (hoverItem) ModernGuiUtils.COLOR_CARD_BG_HOVER else ModernGuiUtils.COLOR_CARD_BG)
            ModernGuiUtils.drawOutline(graphics, listX + 8, curY, itemW, itemH, if (hoverItem) ModernGuiUtils.getAccentDimColor() else ModernGuiUtils.COLOR_CARD_BORDER)

            // Command label
            graphics.text(this.font, Component.literal(cmd), listX + 18, curY + (itemH - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY)

            // Delete button "✕"
            val delW = 20
            val delH = 20
            val delX = listX + 8 + itemW - delW - 5
            val delY = curY + (itemH - delH) / 2
            val hoverDel = mouseX in delX..(delX + delW) && mouseY in delY..(delY + delH) && mouseY in listY..(listY + listH)

            ModernGuiUtils.drawRect(graphics, delX, delY, delW, delH, if (hoverDel) 0xFFEF4444.toInt() else 0x30EF4444.toInt())
            ModernGuiUtils.drawOutline(graphics, delX, delY, delW, delH, 0xFFEF4444.toInt())

            val xTextX = delX + (delW - this.font.width("✕")) / 2
            val xTextY = delY + (delH - 8) / 2
            graphics.text(this.font, Component.literal("✕"), xTextX, xTextY, 0xFFFFFFFF.toInt())

            curY += itemH + itemSpacing
        }

        graphics.disableScissor()

        // Bottom Add Input Area
        val bottomY = winY + winH - 46
        val inputX = winX + 16
        val inputW = 240
        val inputH = 24

        val hoverInput = mouseX in inputX..(inputX + inputW) && mouseY in bottomY..(bottomY + inputH)
        val inputBorder = if (inputFocused) ModernGuiUtils.getAccentColor() else (if (hoverInput) ModernGuiUtils.getAccentDimColor() else ModernGuiUtils.COLOR_CARD_BORDER)

        ModernGuiUtils.drawRect(graphics, inputX, bottomY, inputW, inputH, ModernGuiUtils.COLOR_CARD_BG)
        ModernGuiUtils.drawOutline(graphics, inputX, bottomY, inputW, inputH, inputBorder)

        var textToDraw = newCommandInput
        if (inputFocused && (System.currentTimeMillis() - cursorBlinkTimer) % 1000 < 500) {
            textToDraw += "|"
        }
        graphics.text(this.font, Component.literal(textToDraw), inputX + 8, bottomY + (inputH - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY)

        // "Add" Button
        val addBtnX = inputX + inputW + 8
        val addBtnW = 75
        val addBtnH = 24
        val hoverAdd = mouseX in addBtnX..(addBtnX + addBtnW) && mouseY in bottomY..(bottomY + addBtnH)
        ModernGuiUtils.drawModernButton(graphics, this.font, addBtnX, bottomY, addBtnW, addBtnH, "+ Add", hoverAdd, true)

        // "Reset" Button
        val resetBtnX = addBtnX + addBtnW + 8
        val resetBtnW = 75
        val resetBtnH = 24
        val hoverReset = mouseX in resetBtnX..(resetBtnX + resetBtnW) && mouseY in bottomY..(bottomY + resetBtnH)
        ModernGuiUtils.drawModernButton(graphics, this.font, resetBtnX, bottomY, resetBtnW, resetBtnH, "Reset", hoverReset, false)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        val winW = 440
        val winH = 340
        val winX = (this.width - winW) / 2
        val winY = (this.height - winH) / 2
        val headerH = 38

        // Close button
        val closeW = 60
        val closeH = 22
        val closeX = winX + winW - closeW - 10
        val closeY = winY + 8
        if (mouseX in closeX..(closeX + closeW) && mouseY in closeY..(closeY + closeH)) {
            playPloppSound()
            this.onClose()
            return true
        }

        // List Viewport item clicks (Delete buttons)
        val listX = winX + 16
        val listY = winY + headerH + 12
        val listW = winW - 32
        val listH = 190
        val itemH = 30
        val itemSpacing = 6

        if (mouseX in listX..(listX + listW) && mouseY in listY..(listY + listH)) {
            val commands = AlpakaConfig.instance.commandWheelCommands
            var curY = listY + 6 - scrollY.toInt()
            val itemW = listW - 16

            if (commands != null) {
                for (i in commands.indices) {
                    val delW = 20
                    val delH = 20
                    val delX = listX + 8 + itemW - delW - 5
                    val delY = curY + (itemH - delH) / 2

                    if (mouseX in delX..(delX + delW) && mouseY in delY..(delY + delH)) {
                        playPloppSound()
                        commands.removeAt(i)
                        AlpakaConfig.save()
                        return true
                    }
                    curY += itemH + itemSpacing
                }
            }
        }

        // Bottom Add Input Area
        val bottomY = winY + winH - 46
        val inputX = winX + 16
        val inputW = 240
        val inputH = 24

        if (mouseX in inputX..(inputX + inputW) && mouseY in bottomY..(bottomY + inputH)) {
            this.inputFocused = true
            this.cursorBlinkTimer = System.currentTimeMillis()
            return true
        } else {
            this.inputFocused = false
        }

        // "Add" Button click
        val addBtnX = inputX + inputW + 8
        val addBtnW = 75
        val addBtnH = 24
        if (mouseX in addBtnX..(addBtnX + addBtnW) && mouseY in bottomY..(bottomY + addBtnH)) {
            addCurrentInput()
            return true
        }

        // "Reset" Button click
        val resetBtnX = addBtnX + addBtnW + 8
        val resetBtnW = 75
        val resetBtnH = 24
        if (mouseX in resetBtnX..(resetBtnX + resetBtnW) && mouseY in bottomY..(bottomY + resetBtnH)) {
            playPloppSound()
            AlpakaConfig.instance.commandWheelCommands = ArrayList(listOf("/hub", "/island", "/warp dh", "/wardrobe", "/pets", "/pv"))
            AlpakaConfig.save()
            return true
        }

        return super.mouseClicked(event, isDoubleClick)
    }

    private fun addCurrentInput() {
        var trimmed = newCommandInput.trim()
        if (trimmed.isEmpty() || trimmed == "/") return

        if (!trimmed.startsWith("/")) {
            trimmed = "/$trimmed"
        }

        if (AlpakaConfig.instance.commandWheelCommands == null) {
            AlpakaConfig.instance.commandWheelCommands = ArrayList()
        }

        val commands = AlpakaConfig.instance.commandWheelCommands
        if (!commands.contains(trimmed)) {
            playPloppSound()
            commands.add(trimmed)
            AlpakaConfig.save()
        }

        newCommandInput = "/"
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0) {
            targetScrollY = Math.max(0.0, Math.min(maxScrollY, targetScrollY - scrollY * 24.0))
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (inputFocused) {
            val codePoint = event.codepoint()
            if (codePoint >= 32 && codePoint != 127) {
                if (newCommandInput.length < 35) {
                    newCommandInput += codePoint.toChar()
                    return true
                }
            }
        }
        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (inputFocused) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (newCommandInput.isNotEmpty()) {
                    newCommandInput = newCommandInput.substring(0, newCommandInput.length - 1)
                    return true
                }
            } else if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                addCurrentInput()
                return true
            }
        }
        return super.keyPressed(event)
    }
}
