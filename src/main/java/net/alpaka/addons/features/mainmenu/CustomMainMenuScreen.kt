package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.AlpakaConfigScreen
import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.BlurRectRenderState
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.chat.ChatBlurFeature
import net.alpaka.addons.features.mainmenu.MenuStyle.HOVER_EASE
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_FLASK
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_PEOPLE
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_PERSON
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_PUZZLE
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_SERVER
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_SLIDERS
import net.alpaka.addons.features.mainmenu.MenuStyle.MUTED
import net.alpaka.addons.features.mainmenu.MenuStyle.ONLINE_DOT
import net.alpaka.addons.features.mainmenu.MenuStyle.TEXT
import net.alpaka.addons.features.mainmenu.MenuStyle.TITLE_BASELINE_DROP
import net.alpaka.addons.features.mainmenu.MenuStyle.TITLE_HEIGHT
import net.alpaka.addons.features.mainmenu.MenuStyle.easeOutBack
import net.alpaka.addons.features.mainmenu.MenuStyle.easeOutCubic
import net.alpaka.addons.features.mainmenu.MenuStyle.fade
import net.alpaka.addons.features.mainmenu.MenuStyle.guiScale
import net.alpaka.addons.features.snow.SnowOverlayRenderer
import net.alpaka.addons.utils.ModVersion
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
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
import net.minecraft.network.chat.Component
import org.joml.Matrix3x2f

/**
 * The main menu, laid out like a game's lobby: the player's own avatar standing in a scene, and the
 * menu spread over it in its corners, each button a piece of glass of its own - the pause menu's
 * design language, without a card.
 *
 * The scene is what makes it more than a list of buttons (see [MenuAmbience]): a picture of a place,
 * softly out of focus and drifting like an idle camera, with fireflies rising through it. In front
 * of it, on the ground to the right, the player's skin stands as a live 3D model (see [MenuAvatar])
 * under the player's name, with a shadow at its feet. It looks at the mouse, can be turned by
 * dragging and waves when clicked. The mouse moves the picture, the avatar and the fireflies by
 * different amounts, which is what gives the flat picture its depth. Without the picture, the
 * panorama stands in.
 *
 * Over it, in the corners:
 * - top left, the logo - also the way into the Alpaka config - with the mod's name and versions;
 * - top right, a bar of small glass buttons: Mods, Options, and Quit as a red power button;
 * - bottom left, the way into a game: Join Hypixel as a large hero tile, with a slowly circling
 *   gradient edge, a glint that crosses it now and then and a pulsing live dot beside Hypixel's
 *   player count and ping; under it three glass tiles, Singleplayer, Multiplayer and Join Alpha,
 *   each its icon over its name.
 *
 * Opening plays in order: the corners at the top drop in, the hero and the tiles rise into place
 * one after another, and the avatar lands on its spot. Under the mouse a button's glass brightens
 * and its edge takes the accent; a tile also lifts a little and its icon pops in the accent, and
 * the hero's arrow moves along.
 *
 * Most players see this at Auto GUI scale, 240 to 270 pixels tall, so the sizes come in three steps
 * by the screen's height.
 */
class CustomMainMenuScreen : Screen(Component.literal("Custom Main Menu")) {

    companion object {
        /** The sizes, roomiest first, and the least screen height each one is used from; see [Sizes]. */
        private val ROOMY = Sizes(minHeight = 330, margin = 20, logo = 36, bar = 24, column = 264,
            hero = 54, gap = 6, tile = 46, radius = 8)
        private val NORMAL = Sizes(minHeight = 250, margin = 14, logo = 32, bar = 22, column = 240,
            hero = 48, gap = 5, tile = 42, radius = 7)
        private val TIGHT = Sizes(minHeight = 0, margin = 10, logo = 28, bar = 20, column = 222,
            hero = 42, gap = 4, tile = 36, radius = 6)

        /** Between the bar's buttons, and the room left and right of a bar button's content. */
        private const val BAR_GAP = 4
        private const val BAR_PADDING = 9

        /**
         * The stage beside the play column: the avatar's height as a share of the screen and its
         * limits, and where its feet stand - low on the screen, on the ground near the camera.
         */
        private const val STAGE_GAP = 12
        private const val STAGE_MIN_WIDTH = 110
        private const val AVATAR_SCREEN_SHARE = 0.44f
        private const val AVATAR_WIDTH_SHARE = 0.9f
        /**
         * The feet stand low, on the ground near the camera, and the model is sized so its eyes
         * come to about the picture's horizon: that is where a person standing in front of the
         * camera has them, and anything much taller reads as a giant pasted over the picture.
         */
        private const val FEET_SCREEN_SHARE = 0.87f
        /** The avatar in the night scene's light: dimmer and a touch cooler than the GUI's own. */
        private const val SCENE_TINT = 0xFFBFC4D6.toInt()

        /**
         * Where the avatar stands in the background picture, as shares of its width and height,
         * and how tall its model is (MenuAvatar's box, a little over a player's height) as a share
         * of the picture's height. Measured off a screenshot of the same place with a stand-in
         * player on the spot, so the avatar has exactly the size a player standing there has.
         */
        private const val SPOT_X = 0.7775f
        private const val SPOT_FEET = 0.818f
        private const val SPOT_MODEL_HEIGHT = 0.293f
        /** How quickly the layers catch up with the mouse; they ease after it rather than jump. */
        private const val PARALLAX_EASE_RATE = 7f
        /**
         * How far the mouse moves each layer, for depth: the picture least, the avatar in front of it
         * more, the fireflies - some nearer still - the most.
         */
        private const val BACKGROUND_PARALLAX = 10f
        private const val STAGE_PARALLAX = 22f
        private const val MOTE_PARALLAX = 34f
        /** Fireflies: warm, like the lanterns. */
        private const val MOTE_COLOR = 0xFFFFC870.toInt()
        private const val MOTE_OPACITY = 1f

        /**
         * The glass behind each button: the scene blurred and darkened this much, so the buttons
         * read on the bright parts of the picture as well as on the dark ones.
         */
        private const val BUTTON_GLASS_STRENGTH = 0.38f

        private const val APPEAR_SECONDS = 0.34f
        private const val STAGGER_SECONDS = 0.05f
        /** How far the pieces travel as they spring in: the top ones down, the bottom ones up. */
        private const val DROP_IN = 14f
        private const val RISE_IN = 22f
        private const val HERO_GLINT_DELAY = 0.30f
        private const val HERO_GLINT_SECONDS = 0.85f
        private const val STAGE_DELAY = 0.20f
        private const val LANDING_SECONDS = 0.55f
        /** How far a tile lifts under the mouse, and how much its icon grows. */
        private const val TILE_LIFT = 2f
        private const val ICON_POP = 0.2f
        /** The hero's icon, drawn larger than the text, and how far its arrow moves along under the mouse. */
        private const val HERO_ICON_SCALE = 2f
        private const val HERO_ARROW_SLIDE = 4f

        /** The stagger indices: the brand and the bar first, then the hero and the tiles. */
        private const val APPEAR_BRAND = 0
        private const val APPEAR_BAR = 1
        private const val APPEAR_HERO = 2
        private const val APPEAR_TILES = 3
    }

    /**
     * One step of the sizes: the least screen height it is for, the distance from the screen's
     * edges, the logo, the bar's buttons, the play column's width, the hero, the gap between the
     * column's pieces, the tiles, and the buttons' corner radius.
     */
    private class Sizes(
        val minHeight: Int, val margin: Int, val logo: Int, val bar: Int, val column: Int,
        val hero: Int, val gap: Int, val tile: Int, val radius: Int,
    )

    private var openTime = 0L
    private var logoHover = 0.0f
    private var avatar: MenuAvatar? = null
    private var hasBackground = false
    /** The buttons' corner radius on the current screen, from its step of [Sizes]. */
    private var buttonRadius = NORMAL.radius

    /** The mouse's place from the screen's centre, -1 → 1, eased; drives the parallax. */
    private var parallaxX = 0f
    private var parallaxY = 0f
    private var lastFrameMs = 0L

    /** Where everything sits on the current screen. */
    private inner class Layout {
        val sizes = listOf(ROOMY, NORMAL).firstOrNull { this@CustomMainMenuScreen.height >= it.minHeight } ?: TIGHT
        val margin = sizes.margin

        /** Top left: the logo, and the name and versions beside it. */
        val logoSize = sizes.logo
        val logoX = margin
        val logoY = margin

        /** Top right: the bar, its buttons centred on the logo's height. */
        val barY = logoY + (sizes.logo - sizes.bar) / 2

        /** Bottom left: the play column, the hero over a row of three tiles. */
        val columnX = margin
        val columnW = sizes.column
        val tileY = this@CustomMainMenuScreen.height - margin - sizes.tile
        val heroY = tileY - sizes.gap - sizes.hero
        val tileW = (columnW - 2 * sizes.gap) / 3

        /** The stage to the column's right, and the avatar's size and place on it; null when too narrow. */
        val stageX0 = columnX + columnW + STAGE_GAP
        val stageWidth = this@CustomMainMenuScreen.width - stageX0 - STAGE_GAP
        val hasStage = stageWidth >= STAGE_MIN_WIDTH
        val avatarHeight = minOf(this@CustomMainMenuScreen.height * AVATAR_SCREEN_SHARE, stageWidth * AVATAR_WIDTH_SHARE).toInt()
        val stageCenterX = stageX0 + stageWidth / 2
        val feetY = (this@CustomMainMenuScreen.height * FEET_SCREEN_SHARE).toInt()
    }

    private fun isOverLogo(layout: Layout, mouseX: Double, mouseY: Double): Boolean =
        mouseX >= layout.logoX && mouseX < layout.logoX + layout.logoSize &&
            mouseY >= layout.logoY && mouseY < layout.logoY + layout.logoSize

    private fun elapsed(): Float = (System.currentTimeMillis() - openTime) / 1000.0f

    /** 0 → 1 progress of the element with this stagger index through its entrance, linear. */
    private fun progress(index: Int): Float =
        ((elapsed() - index * STAGGER_SECONDS) / APPEAR_SECONDS).coerceIn(0f, 1f)

    override fun shouldCloseOnEsc(): Boolean = false

    private fun joinServer(ip: String) {
        val mc = this.minecraft ?: return
        val address = ServerAddress.parseString(ip)
        val data = ServerData(if (ip.contains("alpha")) "Hypixel Alpha" else "Hypixel Network", ip, ServerData.Type.OTHER)
        ConnectScreen.startConnecting(this, mc, address, data, false, null)
    }

    override fun init() {
        this.clearWidgets()
        // Stamped once: init() runs again on every resize and on the return from a sub-screen, and
        // replaying the entrance each time made the menu animate in again after every Options visit.
        if (this.openTime == 0L) this.openTime = System.currentTimeMillis()
        if (avatar == null) this.minecraft?.let { avatar = MenuAvatar(it) }
        hasBackground = MenuAmbience.hasBackground()

        val layout = Layout()
        val sizes = layout.sizes
        buttonRadius = sizes.radius

        // The bar, laid out from the right: Quit in the corner, then Options and Mods.
        var barRight = this.width - layout.margin
        this.addRenderableWidget(QuitButton(barRight - sizes.bar, layout.barY, sizes.bar, sizes.bar) {
            this.minecraft?.stop()
        })
        barRight -= sizes.bar + BAR_GAP
        fun barButton(index: Int, glyph: String, label: String, action: () -> Unit) {
            val width = this.font.width(MenuStyle.icon(glyph)) + 6 + GuiFont.width(this.font, label) + BAR_PADDING * 2
            this.addRenderableWidget(BarButton(index, barRight - width, layout.barY, width, sizes.bar, glyph, label, action))
            barRight -= width + BAR_GAP
        }
        barButton(APPEAR_BAR + 1, ICON_SLIDERS, "Options") {
            val mc = this.minecraft ?: return@barButton
            mc.gui.setScreen(OptionsScreen(this, mc.options, false))
        }
        barButton(APPEAR_BAR + 2, ICON_PUZZLE, "Mods") {
            val mc = this.minecraft ?: return@barButton
            // Falls back to the options screen without Mod Menu; see ModMenuCompat for why the
            // Mod Menu class must not be named here.
            if (net.alpaka.addons.compat.ModMenuCompat.isLoaded()) {
                net.alpaka.addons.compat.ModMenuCompat.openModsScreen(this)
            } else {
                mc.gui.setScreen(OptionsScreen(this, mc.options, false))
            }
        }

        // The play column: the hero, and the tiles under it.
        this.addRenderableWidget(HeroButton(layout.columnX, layout.heroY, layout.columnW, sizes.hero) {
            joinServer("mc.hypixel.net")
        })
        var tileX = layout.columnX
        var index = APPEAR_TILES
        fun tile(glyph: String, label: String, action: () -> Unit) {
            this.addRenderableWidget(TileButton(index++, tileX, layout.tileY, layout.tileW, sizes.tile, glyph, label, action))
            tileX += layout.tileW + sizes.gap
        }
        tile(ICON_PERSON, "Singleplayer") {
            this.minecraft?.gui?.setScreen(SelectWorldScreen(this))
        }
        tile(ICON_PEOPLE, "Multiplayer") {
            this.minecraft?.gui?.setScreen(JoinMultiplayerScreen(this))
        }
        tile(ICON_FLASK, "Join Alpha") {
            joinServer("alpha.hypixel.net")
        }

        HypixelStatus.requestRefresh()
    }

    override fun tick() {
        super.tick()
        HypixelStatus.tick()
    }

    override fun removed() {
        super.removed()
        HypixelStatus.onMenuClosed()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val t = MenuAmbience.seconds()
        val now = System.currentTimeMillis()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceAtMost(0.1f)
        lastFrameMs = now

        // The mouse to the fraction of a GUI pixel: the whole-pixel position the screen is handed
        // moves in steps several screen pixels wide at a large GUI scale.
        val mc = this.minecraft ?: Minecraft.getInstance()
        val preciseX = (mc.mouseHandler.xpos() * this.width / mc.window.screenWidth).toFloat()
        val preciseY = (mc.mouseHandler.ypos() * this.height / mc.window.screenHeight).toFloat()
        val ease = 1f - kotlin.math.exp(-dt * PARALLAX_EASE_RATE)
        parallaxX += (((preciseX - this.width / 2f) / (this.width / 2f)).coerceIn(-1f, 1f) - parallaxX) * ease
        parallaxY += (((preciseY - this.height / 2f) / (this.height / 2f)).coerceIn(-1f, 1f) - parallaxY) * ease
        val nx = parallaxX
        val ny = parallaxY
        val layout = Layout()

        val placement = if (hasBackground) {
            MenuAmbience.background(graphics, this.width, this.height, t, nx, ny, BACKGROUND_PARALLAX)
        } else {
            this.extractPanorama(graphics, partialTick)
            null
        }
        MenuAmbience.shade(graphics, this.width, this.height, (layout.columnX + layout.columnW).toFloat())

        // The panorama's overlay is a GUI element, and resource packs put whole backgrounds in it.
        // The glass has to blur that too, so the frame copy it samples is taken after this first
        // stratum rather than before any GUI element: the blur marker splits the draw there, and
        // the capture takes vanilla's blur's place. Only one marker is allowed per frame; another
        // mod's leaves the glass with the copy from before the GUI.
        graphics.nextStratum()
        try {
            graphics.blurBeforeThisStratum()
            ChatBlurFeature.captureAfterBackground()
        } catch (_: IllegalStateException) {
        }

        if (AlpakaConfig.instance.inventorySnowEnabled) {
            SnowOverlayRenderer.render(graphics, this.width, this.height)
        }
        MenuAmbience.motes(graphics, this.width, this.height, t, nx, ny, MOTE_PARALLAX, MOTE_COLOR,
            MOTE_OPACITY * easeOutCubic(elapsed() / 0.6f))

        if (placement != null || layout.hasStage) extractStage(graphics, layout, placement, preciseX, preciseY, nx, ny)
        extractBrand(graphics, layout, mouseX, mouseY, t)
    }

    /**
     * The avatar on its spot in the scene, its shadow, and the player's name above it.
     *
     * In the background picture the spot is a place in it ([SPOT_X], [SPOT_FEET]): the avatar is
     * pinned there and moves exactly as the picture does, drift and parallax alike, so its feet stay
     * on the same stones. Over the panorama there is no ground to stand on, and it stands on the
     * stage to the play column's right instead, shifting against the mouse a layer in front.
     */
    private fun extractStage(graphics: GuiGraphicsExtractor, layout: Layout, placement: MenuAmbience.Placement?,
                             mouseX: Float, mouseY: Float, nx: Float, ny: Float) {
        val since = elapsed() - STAGE_DELAY
        if (since <= 0f) return
        val arrive = (since / LANDING_SECONDS).coerceIn(0f, 1f)

        val cx: Float
        val feet: Float
        val modelHeight: Int
        if (placement != null) {
            // The spot's offset from the picture's corner is snapped once, and the corner is on the
            // screen-pixel grid already: avatar and picture move by exactly the same steps.
            cx = placement.x + MenuAmbience.snapToScreen(placement.width * SPOT_X)
            feet = placement.y + MenuAmbience.snapToScreen(placement.height * SPOT_FEET)
            modelHeight = Math.round(placement.height * SPOT_MODEL_HEIGHT)
            // On a narrow screen the spot can fall behind the play column; then there is no avatar.
            if (cx - modelHeight * 0.3f < layout.columnX + layout.columnW) return
        } else {
            cx = layout.stageCenterX - nx * STAGE_PARALLAX
            feet = layout.feetY - ny * STAGE_PARALLAX * 0.4f
            modelHeight = layout.avatarHeight
        }
        val height = modelHeight.toFloat()

        // The shadow darkens as the avatar comes down onto it.
        MenuAmbience.footing(graphics, cx, feet, height, easeOutCubic(arrive))
        avatar?.draw(graphics, cx, feet, modelHeight, mouseX, mouseY,
            this.width, this.height, arrive, if (placement != null) SCENE_TINT else 0xFFFFFFFF.toInt())

        // The player's name over the avatar's head, and a greeting above it.
        val nameAppear = easeOutCubic((since - 0.35f) / 0.5f)
        if (nameAppear <= 0.01f) return
        val mc = this.minecraft ?: return
        val rise = (1f - nameAppear) * 6f
        val name = GuiFont.title(mc.user.name) ?: Component.literal(mc.user.name)
        val greeting = GuiFont.text("Welcome back,")
        // Placed by offsets from the avatar's feet and centre, each snapped once: the feet and
        // centre are on the screen-pixel grid the picture and the avatar step on, so the labels
        // step with them. Rounded to whole GUI pixels instead, they jumped at other moments than
        // the avatar, several screen pixels at a time, and wobbled over its head.
        val nameTop = maxOf(4f, feet - MenuAmbience.snapToScreen(height + 8f + TITLE_HEIGHT))
        label(graphics, name, cx, nameTop + TITLE_BASELINE_DROP + rise, fade(TEXT, nameAppear))
        label(graphics, greeting, cx, nameTop - 10f + rise, fade(MUTED, nameAppear))
    }

    /** Text centred on [centerX] with its top at [top], placed to the fraction of a GUI pixel. */
    private fun label(graphics: GuiGraphicsExtractor, text: Component, centerX: Float, top: Float, color: Int) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(centerX - MenuAmbience.snapToScreen(this.font.width(text) / 2f), top)
        graphics.text(this.font, text, 0, 0, color, false)
        graphics.pose().popMatrix()
    }

    /**
     * Top left: the logo, which is also the way into the Alpaka config, and beside it the mod's name
     * over the versions. The logo floats gently; under the mouse it grows and gives a little wiggle.
     * A build between releases shows as "dev" rather than with its commit (1.2.22+10.e43f661.dirty);
     * the jar's name carries it.
     */
    private fun extractBrand(graphics: GuiGraphicsExtractor, layout: Layout, mouseX: Int, mouseY: Int, t: Float) {
        val progress = progress(APPEAR_BRAND)
        if (progress <= 0f) return
        val appear = easeOutCubic(progress)

        graphics.pose().pushMatrix()
        graphics.pose().translate(0f, (1f - easeOutBack(progress)) * -DROP_IN)
        val hovered = isOverLogo(layout, mouseX.toDouble(), mouseY.toDouble())
        logoHover += ((if (hovered) 1.0f else 0.0f) - logoHover) * HOVER_EASE
        MenuStyle.logo(graphics, layout.logoX, layout.logoY, layout.logoSize, logoHover, appear, t)

        val textX = layout.logoX + layout.logoSize + 8
        val blockTop = layout.logoY + (layout.logoSize - (TITLE_HEIGHT + 3 + 8)) / 2
        val title = GuiFont.title("Alpaka Addons")
        if (title != null) {
            graphics.text(this.font, title, textX, blockTop + TITLE_BASELINE_DROP, fade(TEXT, appear), false)
        } else {
            graphics.text(this.font, Component.literal("Alpaka Addons"), textX, blockTop + 2, fade(TEXT, appear), false)
        }
        val version = ModVersion.mod()
        val shown = if (version.contains('+')) version.substringBefore('+') + " dev" else version
        graphics.text(this.font, GuiFont.text("$shown · Minecraft ${ModVersion.minecraft()}"), textX,
            blockTop + TITLE_HEIGHT + 3, fade(MUTED, appear), false)
        graphics.pose().popMatrix()
    }

    /**
     * The glass behind a button: the scene behind it blurred and darkened a little, the whole of it
     * at [opacity]. Without the extractor mixin nothing rounded can be submitted, and a plain dark
     * fill stands in.
     */
    private fun glass(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, opacity: Float) {
        val tinted = (Math.round(BUTTON_GLASS_STRENGTH * 255f) shl 24) or (MenuStyle.CARD_TINT and 0x00FFFFFF)
        val sink = graphics as? AlpakaGuiElementSink
        if (sink == null) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, radius, fade(tinted, opacity))
            return
        }
        val toScreen = guiScale()
        sink.`alpaka$submitElement`(BlurRectRenderState(
            Matrix3x2f(graphics.pose()), x, y, x + width, y + height,
            Math.round(radius * toScreen), tinted, toScreen, opacity, sink.`alpaka$currentScissor`()))
        ChatBlurFeature.request()
    }

    /**
     * The dot color and the text of the online line, from whatever the last ping learned, or null
     * when network features are off and nothing is pinged. [withPing] leaves the ping off when the
     * line has to be shorter.
     */
    private fun onlineLine(withPing: Boolean): Pair<Int, String>? = when (HypixelStatus.state) {
        HypixelStatus.State.OFF -> null
        HypixelStatus.State.ONLINE -> {
            val count = HypixelStatus.online?.let { String.format("%,d", it).replace(',', '.') } ?: "?"
            val ping = if (withPing && HypixelStatus.pingMs > 0) " · ${HypixelStatus.pingMs} ms" else ""
            ONLINE_DOT to "$count online$ping"
        }
        HypixelStatus.State.OFFLINE -> MUTED to "Can't reach Hypixel"
        HypixelStatus.State.CONNECTING -> MUTED to "Connecting…"
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0 && isOverLogo(Layout(), event.x(), event.y())) {
            // Vanilla's click, which the custom-sound swap replaces when the player opted in.
            this.minecraft?.let { AbstractWidget.playButtonClickSound(it.soundManager) }
            this.minecraft?.gui?.setScreen(AlpakaConfigScreen(this))
            return true
        }
        if (super.mouseClicked(event, doubleClick)) return true
        return event.button() == 0 && avatar?.mouseClicked(event.x(), event.y()) == true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (avatar?.mouseDragged(dx) == true) return true
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (avatar?.mouseReleased() == true) return true
        return super.mouseReleased(event)
    }

    /**
     * The shared base: the click action, hover easing, and the entrance - each button springs into
     * place at its own moment of the stagger, down from above or up from below.
     */
    private abstract inner class MenuButton(
        private val appearIndex: Int,
        private val fromAbove: Boolean,
        x: Int, y: Int, width: Int, height: Int,
        message: Component,
        private val onClickAction: () -> Unit,
    ) : AbstractButton(x, y, width, height, message) {

        protected var hover = 0.0f
            private set

        override fun onPress(input: InputWithModifiers) {
            // No sound here: the widget already played vanilla's click on the press.
            onClickAction()
        }

        final override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val progress = progress(appearIndex)
            if (progress <= 0f) return
            val appear = easeOutCubic(progress)

            val hovered = mouseX >= this.x && mouseX < this.x + this.width &&
                          mouseY >= this.y && mouseY < this.y + this.height && this.active
            hover += ((if (hovered) 1.0f else 0.0f) - hover) * HOVER_EASE

            val travel = 1f - easeOutBack(progress)
            graphics.pose().pushMatrix()
            graphics.pose().translate(0f, if (fromAbove) travel * -DROP_IN else travel * RISE_IN)
            draw(graphics, appear)
            graphics.pose().popMatrix()
        }

        /** Draws the button at its own position; the pose already carries the entrance. */
        protected abstract fun draw(graphics: GuiGraphicsExtractor, appear: Float)

        override fun updateWidgetNarration(narration: NarrationElementOutput) {}
    }

    /**
     * Join Hypixel, the menu's largest button: the hero panel - washed with the accent from the
     * left inside an edge whose gradient slowly circles it, with a glint crossing it every few
     * seconds - on the button glass. Its icon is drawn large in the accent, its name in the title
     * font over the live dot and Hypixel's player count, and an arrow at its end moves along under
     * the mouse. One glint crosses it as it lands.
     */
    private inner class HeroButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(APPEAR_HERO, false, x, y, width, height, Component.literal("Join Hypixel"), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val font = mc.font
            glass(graphics, this.x, this.y, this.width, this.height, buttonRadius, appear)
            MenuStyle.heroPanel(graphics, this.x, this.y, this.width, this.height, buttonRadius, hover, appear)
            val glint = (elapsed() - HERO_GLINT_DELAY) / HERO_GLINT_SECONDS
            if (glint in 0f..1f) {
                MenuStyle.glint(graphics, this.x, this.y, this.width, this.height, easeOutCubic(glint), 0.14f)
            }

            // The icon, large, in the accent; it pops a little under the mouse.
            val icon = MenuStyle.icon(ICON_SERVER)
            val iconScale = HERO_ICON_SCALE * (1f + ICON_POP * 0.5f * hover)
            val iconBox = Math.round(8 * HERO_ICON_SCALE)
            val iconCenterX = this.x + 12 + iconBox / 2f
            val iconCenterY = this.y + this.height / 2f
            graphics.pose().pushMatrix()
            graphics.pose().translate(iconCenterX, iconCenterY)
            graphics.pose().scale(iconScale, iconScale)
            graphics.text(font, icon, -font.width(icon) / 2, -4, fade(MenuStyle.accent(), appear), false)
            graphics.pose().popMatrix()

            // The name in the title font over the status line, the two centred on the height.
            val textX = this.x + 12 + iconBox + 10
            val blockTop = this.y + (this.height - (TITLE_HEIGHT + 4 + 8)) / 2
            val title = GuiFont.title("Join Hypixel")
            if (title != null) {
                graphics.text(font, title, textX, blockTop + TITLE_BASELINE_DROP, fade(TEXT, appear), false)
            } else {
                graphics.text(font, this.message, textX, blockTop + 2, fade(TEXT, appear), false)
            }

            // The arrow at the end, half there at rest, moving along and brightening under the mouse.
            val arrow = MenuStyle.icon(MenuStyle.ICON_PLAY)
            val arrowX = this.x + this.width - 14 - font.width(arrow) - HERO_ARROW_SLIDE
            graphics.pose().pushMatrix()
            graphics.pose().translate(HERO_ARROW_SLIDE * hover, 0f)
            graphics.text(font, arrow, arrowX.toInt(), this.y + (this.height - 8) / 2,
                fade(ModernGuiUtils.lerpColor(MenuStyle.accent(), TEXT, hover), appear * (0.55f + 0.45f * hover)), false)
            graphics.pose().popMatrix()

            // The online line under the name, without the ping when it would not fit.
            val room = arrowX.toInt() - 6 - textX
            val status = onlineLine(withPing = true)?.let { line ->
                if (GuiFont.width(font, "● " + line.second) <= room) line else onlineLine(withPing = false)
            } ?: return
            val (dot, line) = status
            if (GuiFont.width(font, "● ") + GuiFont.width(font, line) > room) return
            MenuStyle.statusLine(graphics, font, textX, blockTop + TITLE_HEIGHT + 4, dot, line,
                pulse = dot == ONLINE_DOT, appear = appear)
        }
    }

    /**
     * A tile of the play column: its icon over its name, on glass. Under the mouse the glass
     * brightens and its edge takes the accent, the tile lifts a little, and the icon pops in the
     * accent.
     */
    private inner class TileButton(
        appearIndex: Int,
        x: Int, y: Int, width: Int, height: Int,
        private val glyph: String,
        label: String,
        action: () -> Unit,
    ) : MenuButton(appearIndex, false, x, y, width, height, GuiFont.text(label), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val font = mc.font
            graphics.pose().pushMatrix()
            graphics.pose().translate(0f, -TILE_LIFT * hover)
            glass(graphics, this.x, this.y, this.width, this.height, buttonRadius, appear)
            MenuStyle.glassBox(graphics, this.x, this.y, this.width, this.height, buttonRadius, hover, appear)

            // The icon over the name, the two centred on the tile.
            val contentTop = this.y + (this.height - (8 + 6 + 8)) / 2
            val icon = MenuStyle.icon(glyph)
            val pop = 1f + ICON_POP * hover
            graphics.pose().pushMatrix()
            graphics.pose().translate(this.x + this.width / 2f, contentTop + 4f)
            graphics.pose().scale(pop, pop)
            graphics.text(font, icon, -font.width(icon) / 2 + 1, -4,
                fade(ModernGuiUtils.lerpColor(TEXT, MenuStyle.accent(), hover), appear), false)
            graphics.pose().popMatrix()
            ModernGuiUtils.centeredText(graphics, font, this.message, this.x + this.width / 2, contentTop + 14,
                fade(ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, TEXT, hover), appear))
            graphics.pose().popMatrix()
        }
    }

    /** A button of the bar at the top right: its icon and name side by side, on glass. */
    private inner class BarButton(
        appearIndex: Int,
        x: Int, y: Int, width: Int, height: Int,
        private val glyph: String,
        label: String,
        action: () -> Unit,
    ) : MenuButton(appearIndex, true, x, y, width, height, GuiFont.text(label), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val font = mc.font
            glass(graphics, this.x, this.y, this.width, this.height, buttonRadius, appear)
            MenuStyle.glassBox(graphics, this.x, this.y, this.width, this.height, buttonRadius, hover, appear)
            val icon = MenuStyle.icon(glyph)
            val textY = this.y + (this.height - 8) / 2
            val left = this.x + BAR_PADDING
            graphics.text(font, icon, left, textY,
                fade(ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, MenuStyle.accent(), hover), appear), false)
            graphics.text(font, this.message, left + font.width(icon) + 6, textY,
                fade(ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, TEXT, hover), appear), false)
        }
    }

    /** Quit, in the corner: a square red power button on glass, ringed in red under the mouse. */
    private inner class QuitButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(APPEAR_BAR, true, x, y, width, height, MenuStyle.icon(MenuStyle.ICON_POWER), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            glass(graphics, this.x, this.y, this.width, this.height, buttonRadius, appear)
            MenuStyle.quitBox(graphics, mc.font, this.x, this.y, this.width, this.height, buttonRadius, this.message, hover, appear)
        }
    }
}
