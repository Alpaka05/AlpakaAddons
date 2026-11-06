package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.compliance.Outbound
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaConfig.InventoryButton
import net.alpaka.addons.features.market.MarketScreen
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

    /**
     * Whether the buttons show on [screen] right now. Not over the custom Auction House or Bazaar:
     * that screen covers the chest with its own, larger window, so the buttons would sit behind it
     * and a click meant for the market could run a command instead. In its classic view, where
     * Hypixel's chest shows, they are back.
     */
    private fun active(screen: AbstractContainerScreen<*>): Boolean {
        val cfg = AlpakaConfig.instance
        if (!cfg.inventoryButtonsEnabled || cfg.inventoryButtons.isNullOrEmpty()) return false
        if (screen is MarketScreen && !screen.classic()) return false
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
        return InventoryButtonLayout.Box(a.`alpaka$leftPos`(), a.`alpaka$topPos`(), a.`alpaka$imageWidth`(), a.`alpaka$imageHeight`())
    }

    private fun buttonAt(screen: AbstractContainerScreen<*>, mouseX: Double, mouseY: Double): InventoryButton? {
        if (!active(screen)) return null
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
        if (!active(screen)) return
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
     * The looks a button can have, by [AlpakaConfig.inventoryButtonsStyle]. The numbers are what the
     * config file stores, so a new look gets the next free one; [STYLE_ORDER] is the order the
     * settings list them in.
     */
    const val STYLE_ALPAKA = 0
    const val STYLE_VANILLA = 1
    const val STYLE_ICON_ONLY = 2
    const val STYLE_VANILLA_DARK = 3
    @JvmField
    val STYLE_ORDER = intArrayOf(STYLE_ALPAKA, STYLE_VANILLA, STYLE_VANILLA_DARK, STYLE_ICON_ONLY)
    @JvmField
    val STYLE_NAMES = arrayOf("Alpaka", "Vanilla", "Vanilla Dark", "Icon Only")

    /** Where [style] stands in [STYLE_NAMES]; an unknown one counts as the first. */
    @JvmStatic
    fun styleIndex(style: Int): Int = STYLE_ORDER.indexOf(style).coerceAtLeast(0)

    /** How much a hovered icon grows in Icon Only. */
    private const val ICON_HOVER_SCALE = 1.25f

    /** One button with its item on it, in the look picked in the settings. */
    @JvmStatic
    fun drawButton(graphics: GuiGraphicsExtractor, button: InventoryButton, x: Int, y: Int, hovered: Boolean) {
        val cfg = AlpakaConfig.instance
        val s = InventoryButtonLayout.SIZE
        when (cfg.inventoryButtonsStyle) {
            STYLE_VANILLA -> drawVanilla(graphics, x, y, VANILLA_LIGHT)
            STYLE_VANILLA_DARK -> drawVanilla(graphics, x, y, VANILLA_DARK)
            STYLE_ICON_ONLY -> {
                // No tile to light up, so the icon itself grows a little around its middle.
                if (hovered) {
                    val c = s / 2f
                    graphics.pose().pushMatrix()
                    graphics.pose().translate(x + c, y + c)
                    graphics.pose().scale(ICON_HOVER_SCALE, ICON_HOVER_SCALE)
                    graphics.pose().translate(-(x + c), -(y + c))
                    drawIcon(graphics, button.icon, x + 1, y + 1)
                    graphics.pose().popMatrix()
                } else {
                    drawIcon(graphics, button.icon, x + 1, y + 1)
                }
                return
            }
            else -> drawAlpaka(graphics, x, y, cfg.inventoryButtonsColor, cfg.inventoryButtonsRounding, hovered)
        }
        drawIcon(graphics, button.icon, x + 1, y + 1)
        if (hovered) {
            when (cfg.inventoryButtonsStyle) {
                STYLE_VANILLA -> graphics.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0x80FFFFFF.toInt())
                // A softer highlight, so the dark tile doesn't wash out to gray.
                STYLE_VANILLA_DARK -> graphics.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0x30FFFFFF)
            }
        }
    }

    /**
     * The config menu's look: a dark rounded tile with a hairline edge that turns to the accent
     * color on hover, the way a row in the menu lights up.
     */
    private fun drawAlpaka(graphics: GuiGraphicsExtractor, x: Int, y: Int, color: Int, rounding: Int, hovered: Boolean) {
        val s = InventoryButtonLayout.SIZE
        val r = rounding.coerceIn(0, s / 2)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, s, s, r, color)
        if (hovered) ModernGuiUtils.drawRoundedRect(graphics, x, y, s, s, r, 0x1AFFFFFF)
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, s, s, r, if (hovered) ModernGuiUtils.getAccentColor() else 0x40FFFFFF)
    }

    /** Face, top-left light and bottom-right shade of a raised square. */
    private class Bevel(val face: Int, val light: Int, val shade: Int)

    /** The colors of a vanilla container panel. */
    private val VANILLA_LIGHT = Bevel(0xFFC6C6C6.toInt(), 0xFFFFFFFF.toInt(), 0xFF555555.toInt())
    /** The same panel in dark, the way dark inventory resource packs draw it. */
    private val VANILLA_DARK = Bevel(0xFF2E2E2E.toInt(), 0xFF4A4A4A.toInt(), 0xFF1A1A1A.toInt())

    /** A raised square in the colors of a container panel, so it reads as part of the inventory. */
    private fun drawVanilla(graphics: GuiGraphicsExtractor, x: Int, y: Int, bevel: Bevel) {
        val s = InventoryButtonLayout.SIZE
        graphics.fill(x + 1, y, x + s - 1, y + s, 0xFF000000.toInt())
        graphics.fill(x, y + 1, x + s, y + s - 1, 0xFF000000.toInt())
        graphics.fill(x + 1, y + 1, x + s - 1, y + s - 1, bevel.face)
        graphics.fill(x + 1, y + 1, x + s - 2, y + 2, bevel.light)
        graphics.fill(x + 1, y + 1, x + 2, y + s - 2, bevel.light)
        graphics.fill(x + 2, y + s - 2, x + s - 1, y + s - 1, bevel.shade)
        graphics.fill(x + s - 2, y + 2, x + s - 1, y + s - 1, bevel.shade)
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
