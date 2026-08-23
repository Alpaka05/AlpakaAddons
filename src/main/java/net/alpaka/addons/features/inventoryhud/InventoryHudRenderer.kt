package net.alpaka.addons.features.inventoryhud

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen

/**
 * Draws the player's main inventory on the HUD, so its 27 slots can be read without opening a
 * screen - the standalone equivalent of what mods like Inventory HUD+ provide.
 *
 * Entirely a matter of drawing: it reads the inventory the client already holds and paints it, at
 * the same 18px slot pitch the real inventory uses. Nothing is requested from the server, no
 * container is opened, and the inventory itself is never touched - which is also what keeps it
 * inside Hypixel's rules.
 *
 * Visibility and the slide are driven from [InventoryHudFeature]; this file is only the picture.
 */
object InventoryHudRenderer {

    /** Slot grid of the vanilla main inventory: 3 rows of 9, 18px pitch, 16px item face. */
    private const val COLS = 9
    private const val ROWS = 3
    private const val PITCH = 18

    /** Panel margin around the grid. Slim, so the backdrop hugs the slots. */
    private const val PAD = 3

    /** Unscaled panel size. Matches the generated sprite exactly, so it never has to stretch. */
    const val PANEL_WIDTH = COLS * PITCH + PAD * 2
    const val PANEL_HEIGHT = ROWS * PITCH + PAD * 2

    /** Main inventory occupies slots 9..35; 0..8 is the hotbar, which vanilla already draws. */
    private const val FIRST_SLOT = 9

    /** Height of the vanilla hotbar widget, plus a hair of breathing room above it. */
    private const val HOTBAR_HEIGHT = 22
    private const val HOTBAR_GAP = 1

    const val DEFAULT_X = 10
    const val DEFAULT_Y = 10
    const val DEFAULT_SCALE = 1.0f
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 3.0f

    /** Backdrop colour, matching the config menu's panel. Alpha comes from the player's slider. */
    private const val PANEL_BG = 0x191919

    /** Replaces the alpha byte of an RGB colour. */
    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.inventoryHudEnabled) return

        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.level == null || mc.player == null) return
        // Behind a real menu the inventory is on screen anyway; chat is not a menu.
        if (mc.screen != null && mc.screen !is ChatScreen) return

        val open = InventoryHudFeature.openAmount()
        if (open <= 0.001f) return

        val scale = cfg.inventoryHudScale
        val box = footprint(cfg, mc)
        drawPanel(graphics, box.x0, box.y0, scale, open)
    }

    /**
     * The box the HUD occupies on the current screen.
     *
     * When attached, it is centred on the hotbar and sits flush above it, so the two read as one
     * block; otherwise it honours the position set in the HUD editor.
     */
    @JvmStatic
    fun footprint(cfg: AlpakaConfig, mc: Minecraft): HudBounds {
        val width = Math.round(PANEL_WIDTH * cfg.inventoryHudScale)
        val height = Math.round(PANEL_HEIGHT * cfg.inventoryHudScale)

        if (cfg.inventoryHudAttachToHotbar) {
            val screenWidth = mc.window.guiScaledWidth
            val screenHeight = mc.window.guiScaledHeight
            val x = (screenWidth - width) / 2
            val y = screenHeight - HOTBAR_HEIGHT - HOTBAR_GAP - height
            return HudBounds(x, y, x + width, y + height)
        }

        return HudBounds(cfg.inventoryHudX, cfg.inventoryHudY,
            cfg.inventoryHudX + width, cfg.inventoryHudY + height)
    }

    /** Draws the panel and its items. Shared with the HUD editor, which always passes a full [open]. */
    @JvmStatic
    fun drawPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, open: Float) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val width = Math.round(PANEL_WIDTH * scale)
        val height = Math.round(PANEL_HEIGHT * scale)

        // Clipped to its final box so the slide reads as the panel emerging from behind the hotbar
        // rather than sweeping across it.
        graphics.enableScissor(x, y, x + width, y + height)

        val slideOffset = (1.0f - open) * height
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y + slideOffset)
        graphics.pose().scale(scale, scale)

        // One flat fill, no texture: a single constant colour whose strength the player sets, from
        // solid down to fully invisible. Anything patterned here reads as dirt behind the items.
        val backdropAlpha = Math.round(
            AlpakaConfig.instance.inventoryHudBackgroundOpacity / 100.0f * 255.0f
        ).coerceIn(0, 255)

        if (backdropAlpha > 0) {
            ModernGuiUtils.drawRect(graphics, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, withAlpha(PANEL_BG, backdropAlpha))
            // The inner frame fades with the backdrop, so turning the background off leaves only the
            // accent outline rather than a stranded grey rectangle.
            ModernGuiUtils.drawOutline(
                graphics, 1, 1, PANEL_WIDTH - 2, PANEL_HEIGHT - 2,
                withAlpha(ModernGuiUtils.COLOR_CARD_BORDER, backdropAlpha)
            )
        }

        // The accent outline stays at full strength whatever the backdrop does - it is the frame,
        // and it is what keeps the HUD locatable at zero opacity.
        ModernGuiUtils.drawOutline(graphics, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, ModernGuiUtils.getAccentColor())

        val inventory = player.inventory
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val slot = FIRST_SLOT + row * COLS + col
                if (slot >= inventory.containerSize) continue
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue

                // +1 puts the 16x16 item face inside the 18px cell, as the real inventory does.
                val slotX = PAD + col * PITCH + 1
                val slotY = PAD + row * PITCH + 1
                graphics.item(stack, slotX, slotY)
                // Stack counts and durability bars, so the readout matches the real inventory.
                graphics.itemDecorations(mc.font, stack, slotX, slotY)
            }
        }

        graphics.pose().popMatrix()
        graphics.disableScissor()
    }
}
