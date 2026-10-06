package net.alpaka.addons.features.inventoryhud

import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.BlurRectRenderState
import net.alpaka.addons.client.gui.GradientRoundedRectRenderState
import net.alpaka.addons.client.gui.GuiItemFade
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.chat.ChatBlurFeature
import net.alpaka.addons.mixin.HudOverlayAccessor
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import org.joml.Matrix3x2f
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

/**
 * Draws the player's main inventory on the HUD, so its 27 slots can be read without opening a
 * screen - the standalone equivalent of what mods like Inventory HUD+ provide.
 *
 * Entirely a matter of drawing: it reads the inventory the client already holds and paints it, at
 * the same 18px slot pitch the real inventory uses. Nothing is requested from the server, no
 * container is opened, and the inventory itself is never touched - which is also what keeps it
 * inside Hypixel's rules.
 *
 * Visibility and the fade are driven from [InventoryHudFeature]; this file is only the picture.
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
     * from the top of the texture followed by the player's own inventory. Cut short after the third
     * row that slab has a raw edge along the bottom, so the panel is built from two pieces: the head
     * of the GUI (frame, the band a chest keeps its title in, and the three rows of slots as one
     * continuous run of the texture) and the frame that closes the GUI off at its foot. The result
     * is what a small chest looks like when opened, bordered on all four sides, with the title band
     * carrying "Inventory" where the real thing says "Chest".
     */
    private const val CHEST_WIDTH = 176
    private const val CHEST_FRAME_HEIGHT = 7
    /** Frame plus title band: the slots start at row 17 of the texture. */
    private const val CHEST_HEAD_HEIGHT = 17
    private const val CHEST_ROWS_HEIGHT = ROWS * PITCH
    private const val CHEST_HEIGHT = CHEST_HEAD_HEIGHT + CHEST_ROWS_HEIGHT + CHEST_FRAME_HEIGHT

    /**
     * The title, where and how a container screen puts its own: eight pixels in, six down, dark
     * grey without a shadow (AbstractContainerScreen's titleLabelX/Y and the vanilla label colour).
     */
    private const val CHEST_TITLE = "Inventory"
    private const val CHEST_TITLE_X = 8
    private const val CHEST_TITLE_Y = 6
    private const val CHEST_TITLE_COLOR = 0x404040

    /** Where the closing frame sits: the last rows of vanilla's 222-tall six-row chest GUI. */
    private const val CHEST_FOOT_V = 222 - CHEST_FRAME_HEIGHT

    /** Item face of the first slot, measured from the panel's top left, in each style. */
    private const val FLAT_SLOT_X = PAD + 1
    private const val FLAT_SLOT_Y = PAD + 1
    private const val CHEST_SLOT_X = 8
    private const val CHEST_SLOT_Y = CHEST_HEAD_HEIGHT + 1

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

    /** Vanilla's status bar rows: one every 10 pixels, the first (hearts, food) 39 up from the bottom. */
    private const val BAR_ROW = 10
    private const val FIRST_BAR = 39

    /** The line vanilla draws the action bar on, and how far its text reaches above that line. */
    private const val ACTION_BAR_LINE = 68
    private const val ACTION_BAR_ABOVE_LINE = 4

    const val DEFAULT_X = net.alpaka.addons.client.hud.HudDefaults.INVENTORY_X
    const val DEFAULT_Y = net.alpaka.addons.client.hud.HudDefaults.INVENTORY_Y
    const val DEFAULT_SCALE = 1.0f
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 3.0f

    /** Backdrop colour, matching the config menu's panel. Alpha comes from the player's slider. */
    private const val PANEL_BG = 0x191919

    /** Corner radius of the flat panel, in panel pixels. */
    private const val RADIUS = 6

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

    /**
     * How far below its place the panel starts its fade in, and ends its fade out, in GUI pixels:
     * enough to read as rising into place, not so much that it seems to come from the screen edge.
     */
    private const val FADE_DRIFT = 8.0f

    /** Replaces the alpha byte of an RGB colour. */
    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

    /** An ARGB colour with its alpha scaled by [factor]. */
    private fun fade(argb: Int, factor: Float): Int =
        withAlpha(argb, Math.round(((argb ushr 24) and 0xFF) * factor))

    /**
     * The open amount, eased at both ends: the fade in starts and settles gently, and the fade out
     * mirrors it. Smoothstep, so neither end has the abrupt start of a linear fade.
     */
    @JvmStatic
    fun eased(open: Float): Float {
        val t = open.coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

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
            val y = screenHeight - statusStackHeight(mc) - HOTBAR_GAP - height
            return HudBounds(x, y, x + width, y + height)
        }

        return HudBounds(cfg.inventoryHudX, cfg.inventoryHudY,
            cfg.inventoryHudX + width, cfg.inventoryHudY + height)
    }

    /**
     * How far up from the bottom of the screen the hotbar's status block reaches: the hotbar, then
     * hearts and armour on the left, food (or a mount's hearts) and air on the right, and the action
     * bar while one is showing - on Hypixel that is all the time. The attached panel sits above all
     * of it.
     *
     * Where each column starts comes from Fabric's status bar registry, which also counts extra heart
     * rows and bars other mods add. It only knows the bars themselves, not the action bar, so asking
     * it for the action bar - as this used to - always failed and left the panel on the hearts.
     */
    private fun statusStackHeight(mc: Minecraft): Int {
        val player = mc.player ?: return HOTBAR_HEIGHT
        var top = HOTBAR_HEIGHT
        // Creative and spectator draw no status bars.
        if (mc.gameMode?.canHurtPlayer() == true) {
            val airShown = player.isEyeInFluid(FluidTags.WATER) || player.airSupply < player.maxAirSupply
            top = maxOf(top,
                columnTop(VanillaHudElements.ARMOR_BAR, player.armorValue > 0),
                columnTop(VanillaHudElements.AIR_BAR, airShown))
        }
        if ((mc.gui.hud as HudOverlayAccessor).`alpaka$getOverlayMessageTime`() > 0) {
            top = maxOf(top, registeredHeight(VanillaHudElements.OVERLAY_MESSAGE, ACTION_BAR_LINE) + ACTION_BAR_ABOVE_LINE)
        }
        return top
    }

    /**
     * The top of a column of status bars, given its topmost bar and whether that bar is showing.
     * Fabric reports where a bar would start; when it is hidden the row below it is the top.
     */
    private fun columnTop(topBar: Identifier, shown: Boolean): Int {
        val start = registeredHeight(topBar, FIRST_BAR + BAR_ROW)
        return if (shown) start else start - BAR_ROW
    }

    /** Fabric's height for a HUD element, or vanilla's when the registry has none for it. */
    private fun registeredHeight(element: Identifier, vanilla: Int): Int = try {
        HudStatusBarHeightRegistry.getHeight(element)
    } catch (_: RuntimeException) {
        vanilla
    }

    /**
     * Draws the panel and its items. Shared with the HUD editor, which always passes a full [open].
     *
     * While opening or closing, everything - backdrop, frame, items and their counts - fades as one,
     * and the panel sits a few pixels lower the less open it is, so it rises into place as it
     * appears and sinks a little as it goes.
     */
    @JvmStatic
    fun drawPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, open: Float) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        val shown = eased(open)
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y + (1.0f - shown) * FADE_DRIFT)
        graphics.pose().scale(scale, scale)

        // One flat fill, no texture: a single constant colour whose strength the player sets, from
        // solid down to fully invisible. Anything patterned here reads as dirt behind the items.
        val backdropAlpha = Math.round(
            AlpakaConfig.instance.inventoryHudBackgroundOpacity / 100.0f * 255.0f
        ).coerceIn(0, 255)

        if (chestStyle()) {
            // Tinted white so the opacity slider still means something here: white leaves every
            // colour as the pack drew it and only the alpha does any work. The fade scales it on top.
            val chestAlpha = Math.round(backdropAlpha * shown)
            if (chestAlpha > 0) {
                val tint = withAlpha(0xFFFFFF, chestAlpha)
                // The head of the GUI with its title band and the slots in one piece, then the
                // frame from the foot of the GUI. The only seam is above the foot, between two rows
                // of frame that are identical grey anyway.
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, 0, 0, 0.0f, 0.0f,
                    CHEST_WIDTH, CHEST_HEAD_HEIGHT + CHEST_ROWS_HEIGHT,
                    CHEST_WIDTH, CHEST_HEAD_HEIGHT + CHEST_ROWS_HEIGHT, SHEET, SHEET, tint
                )
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, 0, CHEST_HEAD_HEIGHT + CHEST_ROWS_HEIGHT,
                    0.0f, CHEST_FOOT_V.toFloat(),
                    CHEST_WIDTH, CHEST_FRAME_HEIGHT, CHEST_WIDTH, CHEST_FRAME_HEIGHT, SHEET, SHEET, tint
                )
                // The title fades with the panel it sits on; on its own it would float in mid-air.
                graphics.text(
                    mc.font, CHEST_TITLE, CHEST_TITLE_X, CHEST_TITLE_Y,
                    withAlpha(CHEST_TITLE_COLOR, chestAlpha), false
                )
            }
            // No accent frame in this style. The whole point is that the panel passes for a real
            // container, and a coloured outline is the one thing that would give it away.
        } else {
            drawFlatPanel(graphics, scale, backdropAlpha, shown)
        }

        GuiItemFade.begin(shown)
        try {
            drawItems(graphics, mc, player.inventory)
        } finally {
            GuiItemFade.end()
        }

        graphics.pose().popMatrix()
    }

    private fun drawItems(graphics: GuiGraphicsExtractor, mc: Minecraft, inventory: Inventory) {
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
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
                drawItem(graphics, stack, slotX, slotY,
                    InventoryHudFeature.popTime(slot - FIRST_SLOT, partialTick))
                // Stack counts and durability bars, so the readout matches the real inventory.
                graphics.itemDecorations(mc.font, stack, slotX, slotY)
            }
        }
    }

    /**
     * One item face, popping if it has just arrived: the hotbar's own pop, squashed wide and
     * stretched tall around a point low in the slot and springing back over [popTime] ticks. The
     * count is drawn outside it and stays still, as in the hotbar.
     */
    private fun drawItem(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int, popTime: Float) {
        if (popTime <= 0.0f) {
            graphics.item(stack, x, y)
            return
        }
        val stretch = 1.0f + popTime / InventoryHudFeature.POP_TICKS
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate((x + 8).toFloat(), (y + 12).toFloat())
        pose.scale(1.0f / stretch, (stretch + 1.0f) / 2.0f)
        pose.translate(-(x + 8).toFloat(), -(y + 12).toFloat())
        graphics.item(stack, x, y)
        pose.popMatrix()
    }

    /**
     * The flat panel: a rounded box with the world blurred behind it, or a plain tinted fill when
     * the blur is off, inside a one-pixel frame whose colour runs diagonally between the two frame
     * colours from the config. Drawn under the current pose, which already places and scales the panel.
     *
     * The frame stays at full strength whatever the opacity slider does - it is what keeps the HUD
     * locatable when the backdrop is turned all the way down - unless the player turns it off. Only
     * the open fade, [shown], dims it, along with everything else.
     */
    private fun drawFlatPanel(graphics: GuiGraphicsExtractor, scale: Float, backdropAlpha: Int, shown: Float) {
        val sink = graphics as? AlpakaGuiElementSink
        if (sink == null) {
            // Without the extractor mixin nothing rounded can be submitted; square is better than nothing.
            if (backdropAlpha > 0) {
                ModernGuiUtils.drawRect(graphics, 0, 0, FLAT_WIDTH, FLAT_HEIGHT, fade(withAlpha(PANEL_BG, backdropAlpha), shown))
            }
            if (AlpakaConfig.instance.inventoryHudFrame) {
                ModernGuiUtils.drawOutline(graphics, 0, 0, FLAT_WIDTH, FLAT_HEIGHT, fade(AlpakaConfig.instance.inventoryHudFrameStart, shown))
            }
            return
        }

        val toScreen = Minecraft.getInstance().window.guiScale.toFloat() * scale
        val pose = Matrix3x2f(graphics.pose())
        val scissor = sink.`alpaka$currentScissor`()
        val radiusPx = Math.round(RADIUS * toScreen)
        val tint = withAlpha(PANEL_BG, backdropAlpha)

        if (AlpakaConfig.instance.inventoryHudBlur) {
            // The slider is the tint's strength over the blur: 0 % is clear frosted glass, 100 % the
            // solid panel colour. The frame is captured and blurred once per frame for every panel
            // that asks, so this shares the copy with the chat. The fade goes in as the panel's
            // opacity rather than into the tint, so the blur fades with it.
            sink.`alpaka$submitElement`(
                BlurRectRenderState(pose, 0, 0, FLAT_WIDTH, FLAT_HEIGHT, radiusPx, tint, toScreen, shown, scissor)
            )
            ChatBlurFeature.request()
        } else if (backdropAlpha > 0) {
            sink.`alpaka$submitElement`(
                GradientRoundedRectRenderState(
                    pose, 0, 0, FLAT_WIDTH, FLAT_HEIGHT, radiusPx, 0,
                    fade(tint, shown), fade(tint, shown), fade(tint, shown), fade(tint, shown), toScreen, scissor
                )
            )
        }

        if (!AlpakaConfig.instance.inventoryHudFrame) return

        // Top-left carries the first colour, bottom-right the second; the other two corners hold
        // the midpoint so the ramp runs straight along the diagonal without a seam.
        val start = fade(AlpakaConfig.instance.inventoryHudFrameStart, shown)
        val end = fade(AlpakaConfig.instance.inventoryHudFrameEnd, shown)
        val mid = ModernGuiUtils.lerpColor(start, end, 0.5f)
        sink.`alpaka$submitElement`(
            GradientRoundedRectRenderState(
                pose, 0, 0, FLAT_WIDTH, FLAT_HEIGHT, radiusPx, maxOf(1, Math.round(toScreen)),
                start, mid, end, mid, toScreen, scissor
            )
        )
    }
}
