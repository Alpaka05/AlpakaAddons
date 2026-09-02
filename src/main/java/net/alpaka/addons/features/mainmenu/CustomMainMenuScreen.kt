package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.AlpakaConfigScreen
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.snow.SnowOverlayRenderer
import net.alpaka.addons.features.sound.CustomSoundFeature
import net.alpaka.addons.utils.ModVersion
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class CustomMainMenuScreen : Screen(Component.literal("Custom Main Menu")) {

    companion object {
        // Use the static resource identifier - Minecraft's TextureManager loads from resource pack system
        val HERO_TEXTURE_ID: Identifier = Identifier.parse("alpaka:textures/gui/join_hypixel_button.png")
        val MOD_ICON_ID: Identifier = Identifier.parse("alpaka:textures/gui/alpaka_icon.png")

        /**
         * The sidebar's box. Shared rather than restated per method - it was written out twice
         * already, in the layout and again in the drawing. The logo doubles as the button into the
         * Alpaka config now and its clickable area is derived from these, so a third copy would be
         * one edit away from leaving the hit test pointing at empty space.
         */
        private const val SIDEBAR_X = 40
        private const val SIDEBAR_W = 230
        private const val SIDEBAR_H = 330

        /** Logo size and its gap below the sidebar's top edge. */
        private const val LOGO_SIZE = 42
        private const val LOGO_TOP_INSET = 6

        /**
         * How far the logo grows on each side when hovered, as a fraction of its own size.
         *
         * A fraction rather than a pixel count so the escape menu's smaller copy of this logo swells
         * by the same proportion rather than by the same amount.
         */
        private const val LOGO_HOVER_GROWTH = 0.07f
        private var textureRegistered = false
        private var modIconRegistered = false

        fun ensureTextureRegistered() {
            if (!textureRegistered) {
                textureRegistered = true
                try {
                    val texture = SimpleTexture(HERO_TEXTURE_ID)
                    Minecraft.getInstance().textureManager.registerAndLoad(HERO_TEXTURE_ID, texture)
                    println("[AlpakaAddons] Registered and loaded Hypixel hero join button texture via SimpleTexture")
                } catch (e: Throwable) {
                    System.err.println("[AlpakaAddons] Failed to register SimpleTexture for join_hypixel_button.png:")
                    e.printStackTrace()
                }
            }
        }

        fun ensureModIconRegistered() {
            if (!modIconRegistered) {
                modIconRegistered = true
                try {
                    val texture = SimpleTexture(MOD_ICON_ID)
                    Minecraft.getInstance().textureManager.registerAndLoad(MOD_ICON_ID, texture)
                    println("[AlpakaAddons] Registered and loaded mod icon texture via SimpleTexture")
                } catch (e: Throwable) {
                    System.err.println("[AlpakaAddons] Failed to register SimpleTexture for alpaka_icon.png:")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Eased hover amount for the logo, smoothed on the same curve the menu's buttons use for their
     * own hover so the growth eases in rather than snapping to size.
     */
    private var logoHover = 0.0f

    private fun sidebarTop() = (this.height - SIDEBAR_H) / 2
    private fun logoLeft() = SIDEBAR_X + SIDEBAR_W / 2 - LOGO_SIZE / 2
    private fun logoTop() = sidebarTop() + LOGO_TOP_INSET

    private fun isOverLogo(mouseX: Double, mouseY: Double): Boolean {
        val x = logoLeft()
        val y = logoTop()
        return mouseX >= x && mouseX < x + LOGO_SIZE && mouseY >= y && mouseY < y + LOGO_SIZE
    }

    override fun shouldCloseOnEsc(): Boolean = false

    private fun playPloppSound() {
        try {
            CustomSoundFeature.playButtonClickSound()
        } catch (_: Throwable) {}
    }

    private fun joinServer(ip: String) {
        val mc = this.minecraft ?: return
        playPloppSound()
        val address = ServerAddress.parseString(ip)
        val data = ServerData(if (ip.contains("alpha")) "Hypixel Alpha" else "Hypixel Network", ip, ServerData.Type.OTHER)
        ConnectScreen.startConnecting(this, mc, address, data, false, null)
    }

    override fun init() {
        this.clearWidgets()

        val sidebarX = SIDEBAR_X
        val sidebarW = SIDEBAR_W
        val sidebarH = SIDEBAR_H
        val sidebarY = sidebarTop()

        val innerX = sidebarX + 14
        val innerW = sidebarW - 28

        val btnH = 24
        val startY = sidebarY + 52
        val spacing = 27

        // 1. Singleplayer
        this.addRenderableWidget(CustomMenuButton(innerX, startY, innerW, btnH, Component.literal("Singleplayer"), isRed = false) {
            this.minecraft?.gui?.setScreen(SelectWorldScreen(this))
        })

        // 2. Multiplayer
        this.addRenderableWidget(CustomMenuButton(innerX, startY + spacing, innerW, btnH, Component.literal("Multiplayer"), isRed = false) {
            this.minecraft?.gui?.setScreen(JoinMultiplayerScreen(this))
        })

        // 3. Mods (Opens Mod Menu GUI or Options fallback)
        this.addRenderableWidget(CustomMenuButton(innerX, startY + spacing * 2, innerW, btnH, Component.literal("Mods"), isRed = false) {
            val mc = this.minecraft ?: return@CustomMenuButton
            playPloppSound()
            // Falls back to the options screen without Mod Menu; see ModMenuCompat for why the
            // Mod Menu class must not be named here.
            if (net.alpaka.addons.compat.ModMenuCompat.isLoaded()) {
                net.alpaka.addons.compat.ModMenuCompat.openModsScreen(this)
            } else {
                mc.gui.setScreen(OptionsScreen(this, mc.options, false))
            }
        })

        // 4. Join Alpha
        this.addRenderableWidget(CustomMenuButton(innerX, startY + spacing * 3, innerW, btnH, Component.literal("Join Alpha"), isRed = false) {
            joinServer("alpha.hypixel.net")
        })

        // 5. Featured Ornate Retro "JOIN HYPIXEL" Hero Action Button (Exact 1.808:1 original proportions)
        val heroW = 166
        val heroH = 92
        val heroX = innerX + (innerW - heroW) / 2
        val heroY = startY + spacing * 4 + 8
        this.addRenderableWidget(RetroHeroJoinButton(heroX, heroY, heroW, heroH) {
            joinServer("mc.hypixel.net")
        })

        // 6. Bottom System Actions Row (Options & Quit side by side)
        val bottomY = sidebarY + 288
        val halfW = (innerW - 8) / 2

        // Options
        this.addRenderableWidget(CustomMenuButton(innerX, bottomY, halfW, btnH, Component.literal("Options"), isRed = false) {
            val mc = this.minecraft ?: return@CustomMenuButton
            mc.gui.setScreen(OptionsScreen(this, mc.options, false))
        })

        // Quit Game
        this.addRenderableWidget(CustomMenuButton(innerX + halfW + 8, bottomY, halfW, btnH, Component.literal("Quit"), isRed = true) {
            this.minecraft?.stop()
        })
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Render 3D Panorama from Minecraft / Active Resource Pack
        this.extractPanorama(graphics, partialTick)

        // Light translucent veil over panorama so text & sidebar remain clean & readable
        graphics.fill(0, 0, this.width, this.height, 0x30000000)

        // Render Inventory / GUI Snow Overlay Particles if enabled in AlpakaConfig
        if (AlpakaConfig.instance.inventorySnowEnabled) {
            SnowOverlayRenderer.render(graphics, this.width, this.height)
        }

        val sidebarX = SIDEBAR_X
        val sidebarW = SIDEBAR_W
        val sidebarH = SIDEBAR_H
        val sidebarY = sidebarTop()

        // Multi-Layer Drop Shadow for Sidebar Panel
        for (i in 1..6) {
            val alpha = (0x20 * (1.0f - i.toFloat() / 6.0f)).toInt()
            ModernGuiUtils.drawRect(graphics, sidebarX - i, sidebarY - i, sidebarW + i * 2, sidebarH + i * 2, alpha shl 24)
        }

        // Sidebar Panel Base & Frame
        ModernGuiUtils.drawRect(graphics, sidebarX, sidebarY, sidebarW, sidebarH, ModernGuiUtils.COLOR_PANEL_BG)
        ModernGuiUtils.drawOutline(graphics, sidebarX, sidebarY, sidebarW, sidebarH, ModernGuiUtils.COLOR_CARD_BORDER)

        // Top Accent Line
        ModernGuiUtils.drawRect(graphics, sidebarX, sidebarY, sidebarW, 3, ModernGuiUtils.getAccentColor())

        // Render Fancy Ornate AlpakaAddons Header Banner Logo
        renderFancyHeader(graphics, mouseX, mouseY)

        // Section Divider Line
        ModernGuiUtils.drawRect(graphics, sidebarX + 14, sidebarY + 276, sidebarW - 28, 1, ModernGuiUtils.COLOR_CARD_BORDER)

        // Footer Version Label - read from the running build, never hard-coded
        graphics.text(this.font, Component.literal("AlpakaAddons v${ModVersion.mod()}"), 12, this.height - 20, ModernGuiUtils.COLOR_TEXT_PRIMARY)
        graphics.text(this.font, Component.literal("Minecraft ${ModVersion.minecraft()} • Fabric"), 12, this.height - 10, ModernGuiUtils.COLOR_TEXT_MUTED)
    }

    /**
     * Draws the mod logo, which is also the way into the Alpaka config from here - the same as in
     * the escape menu, so the logo means the same thing in both places.
     */
    private fun renderFancyHeader(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val iconX = logoLeft()
        val iconY = logoTop()

        val hovered = isOverLogo(mouseX.toDouble(), mouseY.toDouble())
        logoHover += ((if (hovered) 1.0f else 0.0f) - logoHover) * 0.25f

        // Hovering grows the logo and does nothing else - no card behind it, no border, no lift. It
        // used to take the same treatment the panel's buttons give themselves, which framed a piece
        // of artwork in a box and made it read as a widget that had been selected rather than as one
        // being pointed at. This is what the Join Hypixel button already does: the growth is applied
        // to all four sides, so the logo swells in place instead of drifting.
        val grow = (LOGO_SIZE * LOGO_HOVER_GROWTH * logoHover).toInt()

        ensureModIconRegistered()
        graphics.blit(
            RenderPipelines.GUI_TEXTURED, MOD_ICON_ID, iconX - grow, iconY - grow, 0.0f, 0.0f,
            LOGO_SIZE + grow * 2, LOGO_SIZE + grow * 2, 128, 128, 128, 128,
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0 && isOverLogo(event.x(), event.y())) {
            CustomSoundFeature.playButtonClickSound()
            this.minecraft?.gui?.setScreen(AlpakaConfigScreen(this))
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    // High-Performance Retro Hypixel Hero Button with Ornate Gold Brackets & Hypixel Logo Crest
    private inner class RetroHeroJoinButton(
        x: Int, y: Int, width: Int, height: Int,
        private val onClickAction: () -> Unit
    ) : AbstractButton(x, y, width, height, Component.literal("")) {

        private var hoverTime = 0.0f

        override fun onPress(input: InputWithModifiers) {
            CustomSoundFeature.playButtonClickSound()
            onClickAction()
        }

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val hovered = mouseX >= this.x && mouseX < this.x + this.width &&
                          mouseY >= this.y && mouseY < this.y + this.height && this.active

            val targetHover = if (hovered) 1.0f else 0.0f
            this.hoverTime += (targetHover - this.hoverTime) * 0.25f

            val bx = this.x
            val by = this.y
            val bw = this.width
            val bh = this.height

            // Smooth scale-up expansion on hover (5px expansion on all sides)
            val scaleOffset = (5.0f * this.hoverTime).toInt()
            val renderX = bx - scaleOffset
            val renderY = by - scaleOffset
            val renderW = bw + scaleOffset * 2
            val renderH = bh + scaleOffset * 2

            // Render Generated Ornate Hypixel Texture Image (1024x566 RGBA with 100% original un-distorted aspect ratio)
            ensureTextureRegistered()
            graphics.blit(RenderPipelines.GUI_TEXTURED, HERO_TEXTURE_ID, renderX, renderY, 0.0f, 0.0f, renderW, renderH, 1024, 566, 1024, 566)
        }

        private fun renderOrnateCorners(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, borderCol: Int, mainCol: Int) {
            // Corner Studs (4 Corners)
            ModernGuiUtils.drawRect(graphics, x + 3, y + 3, 4, 4, mainCol)
            ModernGuiUtils.drawOutline(graphics, x + 3, y + 3, 4, 4, borderCol)

            ModernGuiUtils.drawRect(graphics, x + w - 7, y + 3, 4, 4, mainCol)
            ModernGuiUtils.drawOutline(graphics, x + w - 7, y + 3, 4, 4, borderCol)

            ModernGuiUtils.drawRect(graphics, x + 3, y + h - 7, 4, 4, mainCol)
            ModernGuiUtils.drawOutline(graphics, x + 3, y + h - 7, 4, 4, borderCol)

            ModernGuiUtils.drawRect(graphics, x + w - 7, y + h - 7, 4, 4, mainCol)
            ModernGuiUtils.drawOutline(graphics, x + w - 7, y + h - 7, 4, 4, borderCol)

            // Ornate Filigree Accent Wings (Top & Bottom Center)
            val cx = x + w / 2
            ModernGuiUtils.drawRect(graphics, cx - 14, y + 2, 28, 1, mainCol)
            ModernGuiUtils.drawRect(graphics, cx - 8, y + 3, 16, 1, borderCol)

            ModernGuiUtils.drawRect(graphics, cx - 14, y + h - 3, 28, 1, mainCol)
            ModernGuiUtils.drawRect(graphics, cx - 8, y + h - 4, 16, 1, borderCol)
        }

        // Procedural Vector Hypixel Logo Shield & Crown Crest Engine
        private fun renderHypixelShieldLogo(graphics: GuiGraphicsExtractor, sx: Int, sy: Int) {
            val sw = 26
            val sh = 28

            // Shield Golden Bevel Outer Border
            ModernGuiUtils.drawRect(graphics, sx, sy, sw, sh - 4, 0xFFF59E0B.toInt())
            ModernGuiUtils.drawRect(graphics, sx + 2, sy + sh - 4, sw - 4, 2, 0xFFF59E0B.toInt())
            ModernGuiUtils.drawRect(graphics, sx + 5, sy + sh - 2, sw - 10, 2, 0xFFF59E0B.toInt())
            ModernGuiUtils.drawRect(graphics, sx + 10, sy + sh, 6, 2, 0xFFF59E0B.toInt())

            // Shield Crimson & Obsidian Fill
            ModernGuiUtils.drawRect(graphics, sx + 2, sy + 2, sw - 4, sh - 7, 0xFF881337.toInt())
            ModernGuiUtils.drawRect(graphics, sx + 4, sy + sh - 5, sw - 8, 2, 0xFF881337.toInt())
            ModernGuiUtils.drawRect(graphics, sx + 7, sy + sh - 3, sw - 14, 2, 0xFF881337.toInt())

            // Top Golden Crown Crest on Shield
            ModernGuiUtils.drawRect(graphics, sx + 4, sy - 3, 4, 3, 0xFFF59E0B.toInt()) // Left Point
            ModernGuiUtils.drawRect(graphics, sx + 11, sy - 5, 4, 5, 0xFFFEF08A.toInt()) // Center Point (Taller)
            ModernGuiUtils.drawRect(graphics, sx + 18, sy - 3, 4, 3, 0xFFF59E0B.toInt()) // Right Point

            // Crown Gemstone Studs
            ModernGuiUtils.drawRect(graphics, sx + 5, sy - 2, 2, 2, 0xFFEF4444.toInt())
            ModernGuiUtils.drawRect(graphics, sx + 12, sy - 4, 2, 2, 0xFF3B82F6.toInt())
            ModernGuiUtils.drawRect(graphics, sx + 19, sy - 2, 2, 2, 0xFFEF4444.toInt())

            // Iconic Hypixel 'H' Emblem in Center of Shield
            val hx = sx + 7
            val hy = sy + 6

            // Left Stem of H
            ModernGuiUtils.drawRect(graphics, hx + 1, hy + 1, 3, 11, 0xFF000000.toInt()) // Shadow
            ModernGuiUtils.drawRect(graphics, hx, hy, 3, 11, 0xFFFEF08A.toInt()) // Bright Gold

            // Right Stem of H
            ModernGuiUtils.drawRect(graphics, hx + 10, hy + 1, 3, 11, 0xFF000000.toInt()) // Shadow
            ModernGuiUtils.drawRect(graphics, hx + 9, hy, 3, 11, 0xFFFEF08A.toInt()) // Bright Gold

            // Center Crossbar of H
            ModernGuiUtils.drawRect(graphics, hx + 3, hy + 5, 6, 3, 0xFF000000.toInt()) // Shadow
            ModernGuiUtils.drawRect(graphics, hx + 2, hy + 4, 6, 3, 0xFFF59E0B.toInt()) // Warm Gold
        }

        override fun updateWidgetNarration(narration: NarrationElementOutput) {}
    }

    // Modern Animated Custom Button Class matching Mod Config & Pause Theme
    private inner class CustomMenuButton(
        x: Int, y: Int, width: Int, height: Int,
        message: Component,
        private val isRed: Boolean,
        private val onClickAction: () -> Unit
    ) : AbstractButton(x, y, width, height, message) {

        private var hoverTime = 0.0f

        override fun onPress(input: InputWithModifiers) {
            CustomSoundFeature.playButtonClickSound()
            onClickAction()
        }

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val hovered = mouseX >= this.x && mouseX < this.x + this.width &&
                          mouseY >= this.y && mouseY < this.y + this.height && this.active

            val targetHover = if (hovered) 1.0f else 0.0f
            this.hoverTime += (targetHover - this.hoverTime) * 0.25f

            val bx = this.x
            val by = this.y
            val bw = this.width
            val bh = this.height

            val yOffset = (-2.0f * this.hoverTime).toInt()
            val drawY = by + yOffset

            val bg = if (hovered) (if (isRed) 0x40EF4444.toInt() else ModernGuiUtils.COLOR_CARD_BG_HOVER) else ModernGuiUtils.COLOR_CARD_BG
            val border = if (hovered) (if (isRed) 0xFFEF4444.toInt() else ModernGuiUtils.getAccentColor()) else ModernGuiUtils.COLOR_CARD_BORDER
            val textColor = if (hovered) (if (isRed) 0xFFEF4444.toInt() else ModernGuiUtils.getAccentColor()) else ModernGuiUtils.COLOR_TEXT_PRIMARY

            ModernGuiUtils.drawRect(graphics, bx, drawY, bw, bh, bg)
            ModernGuiUtils.drawOutline(graphics, bx, drawY, bw, bh, border)

            val mc = this@CustomMainMenuScreen.minecraft ?: return
            graphics.centeredText(mc.font, this.message, bx + bw / 2, drawY + (bh - 8) / 2, textColor)
        }

        override fun updateWidgetNarration(narration: NarrationElementOutput) {}
    }
}
