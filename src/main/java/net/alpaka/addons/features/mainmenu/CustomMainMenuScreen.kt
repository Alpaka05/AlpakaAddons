package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.AlpakaConfigScreen
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.snow.SnowOverlayRenderer
import net.alpaka.addons.features.sound.CustomSoundFeature
import net.alpaka.addons.features.wheel.WheelMesh
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
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import net.minecraft.server.network.EventLoopGroupHolder
import java.net.UnknownHostException

/**
 * The main menu: the panorama, and down its left edge a column of tabs.
 *
 * There is no panel. The logo and the mod's name sit in the top-left corner, the Join Hypixel
 * artwork below them with Hypixel's live player count and ping under it, and then the tabs: dark
 * glass shapes anchored to the left edge of the screen with a slanted right end. Pointing at one
 * pulls it out of the edge, brightens it and lights an accent strip along its left side; leaving
 * lets it slide back. The dark glass rather than the light one of the pause menu is what keeps
 * the labels readable over a bright sky without any darkening of the panorama itself.
 *
 * Everything appears in a short stagger when the screen opens: the logo, then the artwork, then
 * the tabs one after another sliding in from the left.
 */
class CustomMainMenuScreen : Screen(Component.literal("Custom Main Menu")) {

    companion object {
        val HERO_TEXTURE_ID: Identifier = Identifier.parse("alpaka:textures/gui/join_hypixel_button.png")
        val MOD_ICON_ID: Identifier = Identifier.parse("alpaka:textures/gui/alpaka_icon.png")

        /** The pause menu's icon font; see CustomPauseScreen for why the icons are glyphs. */
        private val ICON_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", "pause_icons"))
        private const val ICON_PLAY = ""
        private const val ICON_SERVER = ""
        private const val ICON_BOX = ""
        private const val ICON_SLIDERS = ""
        private const val ICON_DOOR = ""
        private const val ICON_PUZZLE = ""
        private const val ICON_POTION = ""

        /** Left edge of the column, and the logo in its corner. */
        private const val COLUMN_X = 28
        private const val LOGO_SIZE = 38
        private const val LOGO_Y = 20
        private const val LOGO_HOVER_GROWTH = 0.07f

        /**
         * The Join Hypixel emblem. Its artwork has a straight left edge made to sit flush against the
         * screen edge, so it is drawn at x = 0; the texture is 1016 x 1024 and shown at this size, with
         * a smaller cut for short windows.
         */
        private const val HERO_TEX_W = 1012
        private const val HERO_TEX_H = 1024
        private const val HERO_W = 119
        private const val HERO_H = 120
        private const val HERO_W_COMPACT = 89
        private const val HERO_H_COMPACT = 90

        /** The tabs: anchored at x = 0, this wide, with the right end slanted by this much. */
        private const val TAB_WIDTH = 190
        private const val TAB_SLANT = 16
        private const val TAB_HEIGHT = 30
        private const val TAB_PITCH = 34
        private const val TAB_HEIGHT_COMPACT = 26
        private const val TAB_PITCH_COMPACT = 30
        private const val TAB_TEXT_X = 14
        /** How far a hovered tab pulls out of the edge. */
        private const val TAB_PULL = 16f

        /** Dark glass, a little denser than before now that no hairline outlines the shape. */
        private const val TAB_FILL = 0x7A080C14
        private const val TAB_FILL_HOVER = 0xA80A0E18.toInt()
        private const val TAB_TEXT = 0xE8FFFFFF.toInt()
        private const val TAB_TEXT_HOVER = 0xFFFFFFFF.toInt()
        private const val RED = 0xFFEF4444.toInt()
        private const val RED_TEXT = 0xFFF0B4B4.toInt()
        private const val RED_TEXT_HOVER = 0xFFF87171.toInt()
        private const val ONLINE_TEXT = 0xFFD8DEE8.toInt()

        private const val APPEAR_SECONDS = 0.18f
        private const val STAGGER_SECONDS = 0.03f

        /** Hypixel is pinged again this often while the menu stays open. */
        private const val PING_INTERVAL_MS = 60_000L
        /** An attempt still unanswered after this long is shown as offline. */
        private const val PING_TIMEOUT_MS = 8_000L

        private var textureRegistered = false
        private var modIconRegistered = false

        fun ensureTextureRegistered() {
            if (!textureRegistered) {
                textureRegistered = true
                try {
                    Minecraft.getInstance().textureManager.registerAndLoad(HERO_TEXTURE_ID, SimpleTexture(HERO_TEXTURE_ID))
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
                    Minecraft.getInstance().textureManager.registerAndLoad(MOD_ICON_ID, SimpleTexture(MOD_ICON_ID))
                } catch (e: Throwable) {
                    System.err.println("[AlpakaAddons] Failed to register SimpleTexture for alpaka_icon.png:")
                    e.printStackTrace()
                }
            }
        }

        private fun iconLabel(icon: String, text: String): Component =
            Component.empty()
                .append(Component.literal(icon).withStyle { it.withFont(ICON_FONT) })
                .append(GuiFont.text("  $text"))

        private fun fade(color: Int, factor: Float): Int {
            val alpha = Math.round(((color ushr 24) and 0xFF) * factor.coerceIn(0f, 1f))
            return (alpha shl 24) or (color and 0x00FFFFFF)
        }
    }

    private var openTime = 0L
    private var logoHover = 0.0f

    /** Hypixel's status, kept alive for the live player count; pinged on open and once a minute. */
    private val pinger = ServerStatusPinger()
    private val hypixel = ServerData("Hypixel Network", "mc.hypixel.net", ServerData.Type.OTHER)
    private var lastPingMs = 0L

    private val compact get() = this.height < 380
    private val heroW get() = if (compact) HERO_W_COMPACT else HERO_W
    private val heroH get() = if (compact) HERO_H_COMPACT else HERO_H
    private val tabHeight get() = if (compact) TAB_HEIGHT_COMPACT else TAB_HEIGHT
    private val tabPitch get() = if (compact) TAB_PITCH_COMPACT else TAB_PITCH

    private fun heroY() = LOGO_Y + LOGO_SIZE + 14
    private fun onlineY() = heroY() + heroH + 6
    private fun tabsY() = onlineY() + 18

    private fun isOverLogo(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= COLUMN_X && mouseX < COLUMN_X + LOGO_SIZE && mouseY >= LOGO_Y && mouseY < LOGO_Y + LOGO_SIZE

    /** 0 → 1 appearance of the element with this stagger index, eased out. */
    private fun appear(index: Int): Float {
        val t = ((System.currentTimeMillis() - openTime) / 1000.0f - index * STAGGER_SECONDS) / APPEAR_SECONDS
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val inv = 1f - t
        return 1f - inv * inv * inv
    }

    override fun shouldCloseOnEsc(): Boolean = false

    private fun joinServer(ip: String) {
        val mc = this.minecraft ?: return
        CustomSoundFeature.playButtonClickSound()
        val address = ServerAddress.parseString(ip)
        val data = ServerData(if (ip.contains("alpha")) "Hypixel Alpha" else "Hypixel Network", ip, ServerData.Type.OTHER)
        ConnectScreen.startConnecting(this, mc, address, data, false, null)
    }

    override fun init() {
        this.clearWidgets()
        this.openTime = System.currentTimeMillis()

        this.addRenderableWidget(RetroHeroJoinButton(0, heroY(), heroW, heroH) {
            joinServer("mc.hypixel.net")
        })

        var y = tabsY()
        var index = 2
        fun tab(label: Component, red: Boolean, action: () -> Unit) {
            this.addRenderableWidget(EdgeTab(index++, y, TAB_WIDTH, tabHeight, label, red, action))
            y += tabPitch
        }

        tab(iconLabel(ICON_PLAY, "Singleplayer"), false) {
            this.minecraft?.gui?.setScreen(SelectWorldScreen(this))
        }
        tab(iconLabel(ICON_SERVER, "Multiplayer"), false) {
            this.minecraft?.gui?.setScreen(JoinMultiplayerScreen(this))
        }
        tab(iconLabel(ICON_POTION, "Join Alpha"), false) {
            joinServer("alpha.hypixel.net")
        }
        tab(iconLabel(ICON_PUZZLE, "Mods"), false) {
            val mc = this.minecraft ?: return@tab
            // Falls back to the options screen without Mod Menu; see ModMenuCompat for why the
            // Mod Menu class must not be named here.
            if (net.alpaka.addons.compat.ModMenuCompat.isLoaded()) {
                net.alpaka.addons.compat.ModMenuCompat.openModsScreen(this)
            } else {
                mc.gui.setScreen(OptionsScreen(this, mc.options, false))
            }
        }
        tab(iconLabel(ICON_SLIDERS, "Options"), false) {
            val mc = this.minecraft ?: return@tab
            mc.gui.setScreen(OptionsScreen(this, mc.options, false))
        }
        tab(iconLabel(ICON_DOOR, "Quit"), true) {
            this.minecraft?.stop()
        }

        pingHypixel()
    }

    private fun pingHypixel() {
        val mc = this.minecraft ?: return
        lastPingMs = System.currentTimeMillis()
        // The pinger fills in players and ping but leaves the state to its caller, like the server
        // list does: the first callback fires when the status (with the player count) has arrived,
        // the second when the round-trip time is known. A failed attempt never calls back, so the
        // line falls back to "offline" once the attempt has taken too long; see onlineLine.
        hypixel.setState(ServerData.State.PINGING)
        try {
            pinger.pingServer(
                hypixel,
                { mc.execute { hypixel.setState(ServerData.State.SUCCESSFUL) } },
                { mc.execute { hypixel.setState(ServerData.State.SUCCESSFUL) } },
                EventLoopGroupHolder.remote(mc.options.useNativeTransport()),
            )
        } catch (_: UnknownHostException) {
            hypixel.setState(ServerData.State.UNREACHABLE)
        } catch (_: Throwable) {
            hypixel.setState(ServerData.State.UNREACHABLE)
        }
    }

    override fun tick() {
        super.tick()
        pinger.tick()
        if (System.currentTimeMillis() - lastPingMs > PING_INTERVAL_MS) pingHypixel()
    }

    override fun removed() {
        super.removed()
        pinger.removeAll()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // The panorama, undimmed: the tabs bring their own darkness.
        this.extractPanorama(graphics, partialTick)

        if (AlpakaConfig.instance.inventorySnowEnabled) {
            SnowOverlayRenderer.render(graphics, this.width, this.height)
        }

        // Logo and name in the corner. The logo is also the way into the Alpaka config, and hovering
        // grows it in place like the pause menu's copy of it.
        val logoAppear = appear(0)
        val hovered = isOverLogo(mouseX.toDouble(), mouseY.toDouble())
        logoHover += ((if (hovered) 1.0f else 0.0f) - logoHover) * 0.25f
        val grow = (LOGO_SIZE * LOGO_HOVER_GROWTH * logoHover).toInt()
        val logoY = LOGO_Y + Math.round((1f - logoAppear) * 6f)

        ensureModIconRegistered()
        if (logoAppear > 0.01f) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED, MOD_ICON_ID, COLUMN_X - grow, logoY - grow, 0.0f, 0.0f,
                LOGO_SIZE + grow * 2, LOGO_SIZE + grow * 2, 128, 128, 128, 128, fade(0xFFFFFFFF.toInt(), logoAppear),
            )

            val textX = COLUMN_X + LOGO_SIZE + 10
            graphics.pose().pushMatrix()
            graphics.pose().translate(textX.toFloat(), (logoY + 5).toFloat())
            graphics.pose().scale(1.5f, 1.5f)
            graphics.text(this.font, GuiFont.text("Alpaka Addons"), 0, 0, fade(TAB_TEXT_HOVER, logoAppear), false)
            graphics.pose().popMatrix()
            graphics.text(this.font, GuiFont.text("v${ModVersion.mod()} · Minecraft ${ModVersion.minecraft()}"),
                textX, logoY + 24, fade(ModernGuiUtils.COLOR_TEXT_MUTED, logoAppear), false)
        }

        // Hypixel's player count under the artwork: a dot in the accent while it is reachable.
        val onlineAppear = appear(1)
        if (onlineAppear > 0.01f) {
            val (dot, line) = onlineLine()
            val x = COLUMN_X + 4
            val y = onlineY()
            graphics.text(this.font, GuiFont.text("●"), x, y, fade(dot, onlineAppear), false)
            graphics.text(this.font, GuiFont.text(line), x + 10, y, fade(ONLINE_TEXT, onlineAppear), false)
        }
    }

    /** The dot colour and the text of the online line, from whatever the last ping learned. */
    private fun onlineLine(): Pair<Int, String> {
        val players = hypixel.players
        return when (hypixel.state()) {
            ServerData.State.SUCCESSFUL -> {
                val count = players?.let { String.format("%,d", it.online()).replace(',', '.') } ?: "?"
                val ping = if (hypixel.ping > 0) " · ${hypixel.ping} ms" else ""
                ModernGuiUtils.getAccentColor() to "$count online$ping"
            }
            ServerData.State.UNREACHABLE, ServerData.State.INCOMPATIBLE ->
                ModernGuiUtils.COLOR_TEXT_MUTED to "offline"
            else -> if (System.currentTimeMillis() - lastPingMs > PING_TIMEOUT_MS)
                ModernGuiUtils.COLOR_TEXT_MUTED to "offline"
            else
                ModernGuiUtils.COLOR_TEXT_MUTED to "connecting…"
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0 && isOverLogo(event.x(), event.y())) {
            CustomSoundFeature.playButtonClickSound()
            this.minecraft?.gui?.setScreen(AlpakaConfigScreen(this))
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    /** The Join Hypixel artwork, which swells a little when pointed at. */
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
            val appear = appear(1)
            if (appear <= 0.01f) return

            val hovered = mouseX >= this.x && mouseX < this.x + this.width &&
                          mouseY >= this.y && mouseY < this.y + this.height && this.active
            this.hoverTime += ((if (hovered) 1.0f else 0.0f) - this.hoverTime) * 0.25f

            // The emblem hangs off the screen edge, so it grows away from that edge rather than
            // around its centre: the left side stays put, the right side and the top and bottom
            // move out, and the artwork brightens from a slightly dimmed rest state.
            val grow = (6.0f * this.hoverTime).toInt()
            val drawY = this.y + Math.round((1f - appear) * 6f)
            val tint = WheelMesh.lerpColor(0xFFDCDCDC.toInt(), 0xFFFFFFFF.toInt(), this.hoverTime)

            ensureTextureRegistered()
            graphics.blit(
                RenderPipelines.GUI_TEXTURED, HERO_TEXTURE_ID, this.x, drawY - grow, 0.0f, 0.0f,
                this.width + grow * 2, this.height + grow * 2, HERO_TEX_W, HERO_TEX_H, HERO_TEX_W, HERO_TEX_H, fade(tint, appear),
            )
        }

        override fun updateWidgetNarration(narration: NarrationElementOutput) {}
    }

    /**
     * A tab on the left edge: dark glass with a slanted right end, drawn as a real quad so the
     * slant is smooth. Hover pulls it out of the edge by [TAB_PULL] pixels, darkens the glass a
     * touch so the label gains contrast, and lights an accent strip along its left side; the quit
     * tab does all of that in red. On open it slides in from off screen.
     */
    private inner class EdgeTab(
        private val appearIndex: Int,
        y: Int, width: Int, height: Int,
        message: Component,
        private val isRed: Boolean,
        private val onClickAction: () -> Unit
    ) : AbstractButton(0, y, width, height, message) {

        private var hoverTime = 0.0f

        override fun onPress(input: InputWithModifiers) {
            CustomSoundFeature.playButtonClickSound()
            onClickAction()
        }

        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val appear = appear(appearIndex)
            if (appear <= 0.01f) return

            val hovered = mouseX >= this.x && mouseX < this.x + this.width &&
                          mouseY >= this.y && mouseY < this.y + this.height && this.active
            this.hoverTime += ((if (hovered) 1.0f else 0.0f) - this.hoverTime) * 0.25f
            val hover = this.hoverTime

            val guiScale = (this@CustomMainMenuScreen.minecraft ?: Minecraft.getInstance()).window.guiScale.coerceAtLeast(1)
            val mesh = WheelMesh(1.15f / guiScale)

            // Slides in from the left on open, pulls out to the right on hover.
            val x0 = (1f - appear) * -(this.width + 20f) + hover * TAB_PULL
            val y0 = this.y.toFloat()
            val y1 = y0 + this.height
            val x1 = x0 + this.width

            val fill = fade(WheelMesh.lerpColor(TAB_FILL, TAB_FILL_HOVER, hover), appear)
            mesh.quad(
                x0, y0, fill,
                x0, y1, fill,
                x1 - TAB_SLANT, y1, fill,
                x1, y0, fill,
            )

            // The accent strip, only as bright as the hover; it sits on the edge the tab pulls away from.
            if (hover > 0.02f) {
                val strip = fade(if (isRed) RED else ModernGuiUtils.getAccentColor(), appear * hover)
                mesh.quad(
                    x0, y0, strip,
                    x0, y1, strip,
                    x0 + 3f, y1, strip,
                    x0 + 3f, y0, strip,
                )
            }
            mesh.submit(graphics)

            val text = if (isRed) WheelMesh.lerpColor(RED_TEXT, RED_TEXT_HOVER, hover)
                       else WheelMesh.lerpColor(TAB_TEXT, TAB_TEXT_HOVER, hover)
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            graphics.text(mc.font, this.message, Math.round(x0) + TAB_TEXT_X, this.y + (this.height - 8) / 2, fade(text, appear), false)
        }

        override fun updateWidgetNarration(narration: NarrationElementOutput) {}
    }
}
