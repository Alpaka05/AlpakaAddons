package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.compliance.Outbound
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaConfig.InventoryButton
import net.alpaka.addons.mixin.AbstractContainerScreenAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

/**
 * Inventory Buttons: the player's own buttons around the inventory and every chest or menu, each
 * running a command when clicked - a SkyBlock menu, a warp, or one of the mod's own screens.
 *
 * They are drawn over the screen after its background, so items and tooltips still go on top, and a
 * click on one is taken before the screen sees it: a button outside the panel would otherwise count
 * as a click outside the window, which drops the held item. The press and its release are both
 * kept from the screen for the same reason.
 *
 * A command only goes out through [Outbound], and only from that click.
 */
object InventoryButtonsFeature {
    /** The button the last press landed on, so its release is kept from the screen too. */
    private var pressConsumed = false

    @JvmStatic
    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            ScreenEvents.afterBackground(screen).register { s, graphics, mouseX, mouseY, _ ->
                extract(s as AbstractContainerScreen<*>, graphics, mouseX, mouseY)
            }
            ScreenMouseEvents.allowMouseClick(screen).register { s, event ->
                if (event.button() != 0) return@register true
                val button = buttonAt(s as AbstractContainerScreen<*>, event.x(), event.y()) ?: return@register true
                pressConsumed = true
                run(button)
                false
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, _ ->
                if (!pressConsumed) return@register true
                pressConsumed = false
                false
            }
        }
    }

    /** Whether the buttons show at all right now. */
    private fun active(): Boolean {
        val cfg = AlpakaConfig.instance
        if (!cfg.inventoryButtonsEnabled || cfg.inventoryButtons.isNullOrEmpty()) return false
        return !cfg.inventoryButtonsSkyblockOnly || SkyblockUtils.isOnSkyblock()
    }

    /** Whether [button] belongs on [screen], by its "Show In" choice. */
    @JvmStatic
    fun showsOn(button: InventoryButton, screen: Screen): Boolean {
        val inventory = screen is InventoryScreen || screen is CreativeModeInventoryScreen
        return when (button.showIn) {
            InventoryButton.SHOW_INVENTORY -> inventory
            InventoryButton.SHOW_CONTAINERS -> !inventory
            else -> true
        }
    }

    @JvmStatic
    fun box(screen: AbstractContainerScreen<*>): InventoryButtonLayout.Box {
        val a = screen as AbstractContainerScreenAccessor
        return InventoryButtonLayout.Box(a.`alpaka$getLeftPos`(), a.`alpaka$getTopPos`(), a.`alpaka$getImageWidth`(), a.`alpaka$getImageHeight`())
    }

    private fun buttonAt(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): InventoryButton? {
        if (!active()) return null
        val box = box(screen)
        // The last drawn is on top, so it is the one a click finds first.
        for (button in AlpakaConfig.instance.inventoryButtons.asReversed()) {
            if (!showsOn(button, screen)) continue
            val p = InventoryButtonLayout.place(button, box, screen.width, screen.height)
            if (mouseX >= p[0] && mouseX < p[0] + InventoryButtonLayout.SIZE && mouseY >= p[1] && mouseY < p[1] + InventoryButtonLayout.SIZE) {
                return button
            }
        }
        return null
    }

    private fun extract(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!active()) return
        val box = box(screen)
        val hovered = buttonAt(screen, mouseX.toDouble(), mouseY.toDouble())
        for (button in AlpakaConfig.instance.inventoryButtons) {
            if (!showsOn(button, screen)) continue
            val p = InventoryButtonLayout.place(button, box, screen.width, screen.height)
            drawButton(graphics, button, p[0], p[1], button === hovered)
        }
        if (hovered != null) graphics.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip(hovered), java.util.Optional.empty(), mouseX, mouseY)
    }

    /**
     * One button: a raised square in the colors of a vanilla container panel, so it reads as part
     * of the inventory it sits beside, with its item on it. Hovered, it lightens like a slot does.
     */
    @JvmStatic
    fun drawButton(graphics: GuiGraphicsExtractor, button: InventoryButton, x: Int, y: Int, hovered: Boolean) {
        val s = InventoryButtonLayout.SIZE
        graphics.fill(x + 1, y, x + s - 1, y + s, 0xFF000000.toInt())
        graphics.fill(x, y + 1, x + s, y + s - 1, 0xFF000000.toInt())
        graphics.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0xFFC6C6C6.toInt())
        graphics.fill(x + 1, y + 1, x + s - 2, y + 2, 0xFFFFFFFF.toInt())
        graphics.fill(x + 1, y + 1, x + 2, y + s - 2, 0xFFFFFFFF.toInt())
        graphics.fill(x + 2, y + s - 2, x + s - 1, y + s - 1, 0xFF555555.toInt())
        graphics.fill(x + s - 2, y + 2, x + s - 1, y + s - 1, 0xFF555555.toInt())
        drawIcon(graphics, button.icon, x + 1, y + 1)
        if (hovered) graphics.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0x80FFFFFF.toInt())
    }

    /** The button's item at [x], [y]; the item's first letter on a gray square where there is none yet. */
    @JvmStatic
    fun drawIcon(graphics: GuiGraphicsExtractor, icon: String?, x: Int, y: Int) {
        val stack = InventoryButtonIcons.stack(icon)
        if (stack != null) {
            graphics.fakeItem(stack, x, y)
        } else {
            val font = Minecraft.getInstance().font
            val letter = InventoryButtonIcons.path(icon).take(1).uppercase().ifEmpty { "?" }
            graphics.text(font, letter, x + 8 - font.width(letter) / 2, y + 4, 0xFF404040.toInt(), false)
        }
    }

    private fun tooltip(button: InventoryButton): List<Component> {
        val command = displayCommand(button.command)
        val lines = ArrayList<Component>()
        if (button.label.isNullOrBlank()) {
            lines.add(Component.literal(command))
        } else {
            lines.add(Component.literal(button.label))
            lines.add(Component.literal(command).withStyle(ChatFormatting.GRAY))
        }
        return lines
    }

    /** The command as the player would type it: with its slash. */
    @JvmStatic
    fun displayCommand(command: String?): String {
        val trimmed = command?.trim().orEmpty()
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    /**
     * Runs a button's command. One of the mod's own commands, or another client-side one, runs here
     * in the client - "/alpaka" opens the config without the server ever seeing it. Anything else
     * goes to the server through [Outbound].
     */
    private fun run(button: InventoryButton) {
        val command = displayCommand(button.command).removePrefix("/").trim()
        if (command.isEmpty()) return
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().soundManager)
        if (runClientCommand(command)) return
        Outbound.command(command, Outbound.Cause.INVENTORY_BUTTON)
    }

    private fun runClientCommand(command: String): Boolean {
        val dispatcher = ClientCommands.getActiveDispatcher() ?: return false
        val root = command.substringBefore(' ')
        if (dispatcher.root.getChild(root) == null) return false
        val source = Minecraft.getInstance().connection?.suggestionsProvider as? FabricClientCommandSource ?: return false
        return try {
            dispatcher.execute(command, source)
            true
        } catch (e: Exception) {
            source.sendError(Component.literal(e.message ?: "Could not run /$command"))
            true
        }
    }
}
