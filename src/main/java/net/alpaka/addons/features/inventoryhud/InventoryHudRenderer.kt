package net.alpaka.addons.features.inventoryhud

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

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

    /** Unscaled size of the flat panel: the slot grid plus its own thin margin. */
    private const val FLAT_WIDTH = COLS * PITCH + PAD * 2
    private const val FLAT_HEIGHT = ROWS * PITCH + PAD * 2

    /**
     * Unscaled size of the chest panel, and where its three pieces come from in the texture.
     *
     * A container GUI is 176 wide however many rows it has, and vanilla draws a chest as one slab
     * from the top of the texture followed by the player's own inventory. Neither piece is what the
     * HUD wants: the first carries the band a chest keeps its title in, ten pixels of empty grey
     * with nothing to put in it, and cut short it has a raw edge along the bottom.
     *
     * So the panel is built from three strips instead - the frame above the band, the three rows of
     * slots, and the frame that closes the GUI off at its foot. The result is bordered on all four
     * sides and no taller than the slots need.
     */
    private const val CHEST_WIDTH = 176
    private const val CHEST_FRAME_HEIGHT = 7
    private const val CHEST_ROWS_V = 17
    private const val CHEST_ROWS_HEIGHT = ROWS * PITCH
    private const val CHEST_HEIGHT = CHEST_FRAME_HEIGHT * 2 + CHEST_ROWS_HEIGHT

    /** Where the closing frame sits: the last rows of vanilla's 222-tall six-row chest GUI. */
    private const val CHEST_FOOT_V = 222 - CHEST_FRAME_HEIGHT

    /** Item face of the first slot, measured from the panel's top left, in each style. */
    private const val FLAT_SLOT_X = PAD + 1
    private const val FLAT_SLOT_Y = PAD + 1
    private const val CHEST_SLOT_X = 8
    private const val CHEST_SLOT_Y = CHEST_FRAME_HEIGHT + 1

    private fun chestStyle(): Boolean = AlpakaConfig.instance.inventoryHudVanillaTexture

    /** Unscaled panel size. Asked for rather than stored, because the style decides it. */
    @JvmStatic
    fun panelWidth(): Int = if (chestStyle()) CHEST_WIDTH else FLAT_WIDTH

    @JvmStatic
    fun panelHeight(): Int = if (chestStyle()) CHEST_HEIGHT else FLAT_HEIGHT

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

    /**
     * The texture a chest GUI is drawn from.
     *
     * Named rather than copied on purpose: a resource pack replaces this very file, so pointing at
     * it is what makes the HUD wear whatever pack is loaded. Every offset here was read off the
     * shipped copy rather than assumed: the frame runs to row 221, the title band to row 16, and
     * the three rows of slots from row 17 to row 70.
     */
    private val CHEST_TEXTURE: Identifier = Identifier.parse("minecraft:textures/gui/container/generic_54.png")

    /** The texture is authored against a 256x256 sheet; a pack at higher resolution still maps. */
    private const val SHEET = 256

    /** Replaces the alpha byte of an RGB colour. */
    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.inventoryHudEnabled) return

        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return
        // Behind a real menu the inventory is on screen anyway; chat is not a menu.
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return

        val open = InventoryHudFeature.openAmount()
        if (open <= 0.001f) return

        val scale = cfg.inventoryHudScale
        // visibleBounds, not footprint: attached-to-hotbar is on screen by construction, but a
        // freely positioned panel can be stranded by a GUI-scale change.
        val box = InventoryHudElement.visibleBounds(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
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
        val width = Math.round(panelWidth() * cfg.inventoryHudScale)
        val height = Math.round(panelHeight() * cfg.inventoryHudScale)

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
        val width = Math.round(panelWidth() * scale)
        val height = Math.round(panelHeight() * scale)

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

        if (chestStyle()) {
            // Tinted white so the opacity slider still means something here: white leaves every
            // colour as the pack drew it and only the alpha does any work.
            if (backdropAlpha > 0) {
                val tint = withAlpha(0xFFFFFF, backdropAlpha)
                // The frame above the title band, the slots, then the frame from the foot of the
                // GUI - the band itself is skipped, which is the only seam in the whole panel and
                // falls between two rows of frame that are identical grey anyway.
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, 0, 0, 0.0f, 0.0f,
                    CHEST_WIDTH, CHEST_FRAME_HEIGHT, CHEST_WIDTH, CHEST_FRAME_HEIGHT, SHEET, SHEET, tint
                )
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, 0, CHEST_FRAME_HEIGHT,
                    0.0f, CHEST_ROWS_V.toFloat(),
                    CHEST_WIDTH, CHEST_ROWS_HEIGHT, CHEST_WIDTH, CHEST_ROWS_HEIGHT, SHEET, SHEET, tint
                )
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, 0, CHEST_FRAME_HEIGHT + CHEST_ROWS_HEIGHT,
                    0.0f, CHEST_FOOT_V.toFloat(),
                    CHEST_WIDTH, CHEST_FRAME_HEIGHT, CHEST_WIDTH, CHEST_FRAME_HEIGHT, SHEET, SHEET, tint
                )
            }
            // No accent frame in this style. The whole point is that the panel passes for a real
            // container, and a coloured outline is the one thing that would give it away.
        } else {
            if (backdropAlpha > 0) {
                ModernGuiUtils.drawRect(graphics, 0, 0, FLAT_WIDTH, FLAT_HEIGHT, withAlpha(PANEL_BG, backdropAlpha))
                // The inner frame fades with the backdrop, so turning the background off leaves only
                // the accent outline rather than a stranded grey rectangle.
                ModernGuiUtils.drawOutline(
                    graphics, 1, 1, FLAT_WIDTH - 2, FLAT_HEIGHT - 2,
                    withAlpha(ModernGuiUtils.COLOR_CARD_BORDER, backdropAlpha)
                )
            }

            // The accent outline stays at full strength whatever the backdrop does - it is the
            // frame, and it is what keeps the HUD locatable at zero opacity.
            ModernGuiUtils.drawOutline(graphics, 0, 0, FLAT_WIDTH, FLAT_HEIGHT, ModernGuiUtils.getAccentColor())
        }

        val inventory = player.inventory
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val slot = FIRST_SLOT + row * COLS + col
                if (slot >= inventory.containerSize) continue
                val stack = inventory.getItem(slot)
                if (stack.isEmpty) continue

                // Where the 16x16 face sits inside its 18px cell, which the two styles put in
                // different places: the flat panel insets by its own margin, the chest style lands
                // on the slots the texture already has.
                val slotX = (if (chestStyle()) CHEST_SLOT_X else FLAT_SLOT_X) + col * PITCH
                val slotY = (if (chestStyle()) CHEST_SLOT_Y else FLAT_SLOT_Y) + row * PITCH
                graphics.item(stack, slotX, slotY)
                // Stack counts and durability bars, so the readout matches the real inventory.
                graphics.itemDecorations(mc.font, stack, slotX, slotY)
            }
        }

        graphics.pose().popMatrix()
        graphics.disableScissor()
    }
}
