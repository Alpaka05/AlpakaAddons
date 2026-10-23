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
import net.alpaka.addons.features.mainmenu.MenuStyle.SEPARATOR
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
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
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
 * Over it on the left, one column holds the whole menu, centred on the screen's height, under the logo - also the way into the
 * Alpaka config - with the mod's name and versions: Join Hypixel as the hero, with a slowly
 * circling gradient edge, a glint that crosses it now and then and a pulsing live dot beside
 * Hypixel's player count and ping; then Singleplayer, Multiplayer and Join Alpha; after a hairline
 * Mods and Options side by side, and Quit in red. All of them are the same width and share one
 * row height, so the column reads as one menu; only the hero stands out. At the top right, level
 * with the logo, the What's New card shows the first changes of the newest release, and opens all
 * of them ([ReleaseNotesPanel]).
 *
 * Opening plays in order: the logo and the card drop in, the column's buttons rise into place one
 * after another, and the avatar lands on its spot. Under the mouse a button's glass brightens and
 * its edge takes the accent, its icon takes the accent too, and a chevron fades in at its end.
 *
 * Most players see this at Auto GUI scale, 240 to 270 pixels tall, so the sizes come in three steps
 * by the screen's height.
 */
class CustomMainMenuScreen : Screen(Component.literal("Custom Main Menu")) {

    companion object {
        /** The sizes, roomiest first, and the least screen height each one is used from; see [Sizes]. */
        private val ROOMY = Sizes(minHeight = 330, margin = 20, logo = 36, column = 184, hero = 46, row = 24,
            gap = 4, divider = 7, radius = 8, card = 176)
        private val NORMAL = Sizes(minHeight = 250, margin = 14, logo = 32, column = 164, hero = 40, row = 22,
            gap = 4, divider = 6, radius = 7, card = 160)
        private val TIGHT = Sizes(minHeight = 0, margin = 10, logo = 28, column = 152, hero = 34, row = 20,
            gap = 3, divider = 5, radius = 6, card = 150)

        /** Room left of a row's icon, and between the logo and the hero. */
        private const val ROW_PADDING = 9
        private const val BRAND_GAP = 12
        /** The What's New card: its padding, how many changes it lists, and their line height. */
        private const val CARD_PAD = 8
        private const val CARD_ITEMS = 3
        private const val CARD_LINE = 10
        /** The least room between the column and the card; with less, the card is left out. */
        private const val CARD_MIN_GAP = 24
        private const val PANEL_SECONDS = 0.22f
        /** The card's glass is a little darker than the buttons', for the text on it. */
        private const val CARD_FILL = 0x330D1117
        /** Quit's icon and name: the red pill's text color. */
        private const val QUIT_TEXT = 0xFFFCA5A5.toInt()

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
        /** How far the pieces travel as they spring in: the logo down, the column's pieces up. */
        private const val DROP_IN = 14f
        private const val RISE_IN = 22f
        private const val HERO_GLINT_DELAY = 0.30f
        private const val HERO_GLINT_SECONDS = 0.85f
        private const val STAGE_DELAY = 0.20f
        private const val LANDING_SECONDS = 0.55f
        /** How much the hero's icon grows under the mouse, and how far a row's chevron moves along. */
        private const val ICON_POP = 0.2f
        private const val CHEVRON_SLIDE = 3f
        /** The hero's icon, drawn larger than the text, and how far its arrow moves along under the mouse. */
        private const val HERO_ICON_SCALE = 2f
        /** The room around the hero's icon, which fills the rest of its height. */
        private const val HERO_ICON_INSET = 7
        private const val HERO_ARROW_SLIDE = 4f

        /** The stagger indices: the logo and the card first, then the column's buttons from the top. */
        private const val APPEAR_BRAND = 0
        private const val APPEAR_CARD = 1
        private const val APPEAR_HERO = 1
    }

    /**
     * One step of the sizes: the least screen height it is for, the distance from the screen's
     * edges, the logo, the column's width, the hero and the rows, the gap between buttons, the room
     * above and below the hairline, the buttons' corner radius, and the What's New card's width.
     */
    private class Sizes(
        val minHeight: Int, val margin: Int, val logo: Int, val column: Int, val hero: Int, val row: Int,
        val gap: Int, val divider: Int, val radius: Int, val card: Int,
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
    /** How much of the background's slow drift is shown, 0 → 1, eased; off with the parallax. */
    private var driftWeight = if (AlpakaConfig.instance.customMainMenuParallax) 1f else 0f
    private var lastFrameMs = 0L

    /** The release notes window while it is open, and when it opened. */
    private var panel: ReleaseNotesPanel? = null
    private var panelOpenTime = 0L
    private var card: CardButton? = null

    /** Where everything sits on the current screen. */
    private inner class Layout {
        val sizes = listOf(ROOMY, NORMAL).firstOrNull { this@CustomMainMenuScreen.height >= it.minHeight } ?: TIGHT
        val margin = sizes.margin

        /** Top left: the logo, and the name and versions beside it. */
        val logoSize = sizes.logo
        val logoX = margin
        val logoY = margin

        /**
         * The column under the logo: the hero, three rows, the hairline, Mods and Options, Quit.
         * Centred on the screen's height, but never higher than just under the logo.
         */
        val columnX = margin
        val columnW = sizes.column
        val columnH = sizes.hero + 3 * (sizes.row + sizes.gap) + sizes.divider * 2 + 1 + sizes.row * 2 + sizes.gap
        val heroY = maxOf(logoY + logoSize + BRAND_GAP, (this@CustomMainMenuScreen.height - columnH) / 2)
        val rowsY = heroY + sizes.hero + sizes.gap
        val dividerY = rowsY + 3 * (sizes.row + sizes.gap) - sizes.gap + sizes.divider
        val pairY = dividerY + 1 + sizes.divider
        val quitY = pairY + sizes.row + sizes.gap
        val halfW = (columnW - sizes.gap) / 2

        /** The What's New card at the top right, level with the logo; left out when it would crowd the column. */
        val cardW = sizes.card
        val cardH = CARD_PAD * 2 + 12 + CARD_ITEMS * CARD_LINE + 3 + 9
        val cardX = this@CustomMainMenuScreen.width - margin - cardW
        val cardY = margin
        val hasCard = cardX >= columnX + columnW + CARD_MIN_GAP

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
        // The player's own server-list entry when there is one, as the server list itself would
        // join: its resource pack choice stands. A fresh entry is always set to prompt, so Hypixel's
        // pack was asked about again on every join from here.
        val data = savedEntryFor(mc, ip)
            ?: ServerData(if (ip.contains("alpha")) "Hypixel Alpha" else "Hypixel Network", ip, ServerData.Type.OTHER)
        ConnectScreen.startConnecting(this, mc, address, data, false, null)
    }

    /** The first server-list entry for the same Hypixel network as [ip], or null; alpha only matches alpha. */
    private fun savedEntryFor(mc: Minecraft, ip: String): ServerData? {
        val wanted = hostOf(ip)
        val alpha = wanted.startsWith("alpha.")
        val list = ServerList(mc)
        list.load()
        for (i in 0 until list.size()) {
            val entry = list.get(i)
            val host = hostOf(entry.ip)
            val same = if (alpha) host == wanted
                else (host == "hypixel.net" || host.endsWith(".hypixel.net")) && !host.startsWith("alpha.")
            if (same) return entry
        }
        return null
    }

    private fun hostOf(ip: String): String = ip.trim().lowercase().substringBefore(':').trimEnd('.')

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

        // The column: the hero, the rows, and after the hairline Mods and Options, then Quit.
        this.addRenderableWidget(HeroButton(layout.columnX, layout.heroY, layout.columnW, sizes.hero) {
            joinServer("mc.hypixel.net")
        })
        var index = APPEAR_HERO + 1
        fun row(x: Int, y: Int, width: Int, glyph: String, label: String, quit: Boolean = false, action: () -> Unit) {
            this.addRenderableWidget(RowButton(index++, x, y, width, sizes.row, glyph, label, quit, action))
        }
        var rowY = layout.rowsY
        row(layout.columnX, rowY, layout.columnW, ICON_PERSON, "Singleplayer") {
            this.minecraft?.gui?.setScreen(SelectWorldScreen(this))
        }
        rowY += sizes.row + sizes.gap
        row(layout.columnX, rowY, layout.columnW, ICON_PEOPLE, "Multiplayer") {
            this.minecraft?.gui?.setScreen(JoinMultiplayerScreen(this))
        }
        rowY += sizes.row + sizes.gap
        row(layout.columnX, rowY, layout.columnW, ICON_FLASK, "Join Alpha") {
            joinServer("alpha.hypixel.net")
        }
        row(layout.columnX, layout.pairY, layout.halfW, ICON_PUZZLE, "Mods") {
            val mc = this.minecraft ?: return@row
            // Falls back to the options screen without Mod Menu; see ModMenuCompat for why the
            // Mod Menu class must not be named here.
            if (net.alpaka.addons.compat.ModMenuCompat.isLoaded()) {
                net.alpaka.addons.compat.ModMenuCompat.openModsScreen(this)
            } else {
                mc.gui.setScreen(OptionsScreen(this, mc.options, false))
            }
        }
        row(layout.columnX + layout.columnW - layout.halfW, layout.pairY, layout.halfW, ICON_SLIDERS, "Options") {
            val mc = this.minecraft ?: return@row
            mc.gui.setScreen(OptionsScreen(this, mc.options, false))
        }
        row(layout.columnX, layout.quitY, layout.columnW, MenuStyle.ICON_POWER, "Quit", quit = true) {
            this.minecraft?.stop()
        }

        card = if (layout.hasCard) {
            this.addRenderableWidget(CardButton(layout.cardX, layout.cardY, layout.cardW, layout.cardH) { openNotes() })
        } else null
        panel?.layout(this.width, this.height)
        updateWidgetStates()

        ReleaseNotes.request()
        HypixelStatus.requestRefresh()
    }

    /**
     * Draws the whole menu as the backdrop of a screen opened over it - the config - so the menu
     * stays visible behind that screen instead of a black void: there is no world behind a title
     * screen for the screen's dim to darken. Drawn without the mouse, so nothing on it is hovered,
     * and finished with a new stratum, so the screen on top stays in front of all of it. A resize
     * while the other screen is open is caught up on here.
     */
    fun extractAsBackdrop(graphics: GuiGraphicsExtractor, width: Int, height: Int, partialTick: Float) {
        if (this.width != width || this.height != height) this.init(width, height)
        extractBackground(graphics, -1, -1, partialTick)
        graphics.nextStratum()
        extractRenderState(graphics, -1, -1, partialTick)
        graphics.nextStratum()
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
        // With the parallax off the layers stay centred and the background stops drifting; easing towards the centre rather than
        // snapping to it lets a toggle while the menu is open glide back.
        val parallaxOn = AlpakaConfig.instance.customMainMenuParallax
        val targetX = if (parallaxOn) ((preciseX - this.width / 2f) / (this.width / 2f)).coerceIn(-1f, 1f) else 0f
        val targetY = if (parallaxOn) ((preciseY - this.height / 2f) / (this.height / 2f)).coerceIn(-1f, 1f) else 0f
        val ease = 1f - kotlin.math.exp(-dt * PARALLAX_EASE_RATE)
        parallaxX += (targetX - parallaxX) * ease
        parallaxY += (targetY - parallaxY) * ease
        driftWeight += ((if (parallaxOn) 1f else 0f) - driftWeight) * ease
        val nx = parallaxX
        val ny = parallaxY
        val layout = Layout()

        val placement = if (hasBackground) {
            MenuAmbience.background(graphics, this.width, this.height, t, nx, ny, BACKGROUND_PARALLAX, driftWeight)
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
        val hovered = panel == null && isOverLogo(layout, mouseX.toDouble(), mouseY.toDouble())
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

        // The hairline between the ways into a game and the rest, arriving with the rows around it.
        val lineAppear = easeOutCubic(progress(APPEAR_HERO + 4))
        if (lineAppear > 0f) {
            graphics.fill(layout.columnX + 4, layout.dividerY, layout.columnX + layout.columnW - 4, layout.dividerY + 1,
                fade(SEPARATOR, lineAppear))
        }
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
        HypixelStatus.State.OFFLINE -> MUTED to "can't reach Hypixel"
        HypixelStatus.State.CONNECTING -> MUTED to "connecting…"
    }

    /** 0 → 1 how far in the release notes window is, eased out; 0 while it is closed. */
    private fun panelShown(): Float {
        if (panel == null) return 0f
        return easeOutCubic((System.currentTimeMillis() - panelOpenTime) / 1000f / PANEL_SECONDS)
    }

    private fun openNotes() {
        val notes = ReleaseNotes.shown() ?: return
        panel = ReleaseNotesPanel(notes).also { it.layout(this.width, this.height) }
        panelOpenTime = System.currentTimeMillis()
        ReleaseNotes.markRead(notes)
        updateWidgetStates()
    }

    private fun closeNotes() {
        panel = null
        updateWidgetStates()
    }

    /** The menu's buttons answer only while the notes window is closed. */
    private fun updateWidgetStates() {
        for (child in this.children()) {
            if (child is AbstractWidget) child.active = panel == null
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // The card shows only once the notes have arrived, and not while network features are off.
        card?.visible = ReleaseNotes.shown() != null
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        panel?.draw(graphics, this.font, this.width, this.height, mouseX, mouseY, panelShown())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val open = panel
        if (open != null) {
            val mc = this.minecraft
            when {
                event.button() != 0 -> {}
                open.isOverClose(event.x(), event.y()) || !open.isOver(event.x(), event.y()) -> {
                    mc?.let { AbstractWidget.playButtonClickSound(it.soundManager) }
                    closeNotes()
                }
                open.isOverLink(this.font, event.x(), event.y()) -> {
                    mc?.let { AbstractWidget.playButtonClickSound(it.soundManager) }
                    ReleaseNotes.shown()?.let { ConfirmLinkScreen.confirmLinkNow(this, it.url) }
                }
            }
            return true
        }
        if (event.button() == 0 && isOverLogo(Layout(), event.x(), event.y())) {
            // Vanilla's click, which the custom-sound swap replaces when the player opted in.
            this.minecraft?.let { AbstractWidget.playButtonClickSound(it.soundManager) }
            this.minecraft?.gui?.setScreen(AlpakaConfigScreen(this))
            return true
        }
        if (super.mouseClicked(event, doubleClick)) return true
        return event.button() == 0 && avatar?.mouseClicked(event.x(), event.y()) == true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val open = panel ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        open.scrollBy(scrollY)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == 256 && panel != null) { // GLFW_KEY_ESCAPE
            closeNotes()
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (panel == null && avatar?.mouseDragged(dx) == true) return true
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (avatar?.mouseReleased() == true) return true
        return super.mouseReleased(event)
    }

    /**
     * The shared base: the click action, hover easing, and the entrance - each button springs into
     * place at its own moment of the stagger, rising from below or, at the top, dropping in.
     */
    private abstract inner class MenuButton(
        private val appearIndex: Int,
        x: Int, y: Int, width: Int, height: Int,
        private val fromAbove: Boolean = false,
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
     * seconds - on the button glass. Hypixel's "H" fills its height (the icon its server sends,
     * see [HypixelStatus.icon]), its name stands in the title font over the live dot and Hypixel's
     * player count, and an arrow at its end moves along under the mouse. One glint crosses it as it
     * lands.
     */
    private inner class HeroButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(APPEAR_HERO, x, y, width, height, message = Component.literal("Join Hypixel"), onClickAction = action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val font = mc.font
            glass(graphics, this.x, this.y, this.width, this.height, buttonRadius, appear)
            MenuStyle.heroPanel(graphics, this.x, this.y, this.width, this.height, buttonRadius, hover, appear)
            val glint = (elapsed() - HERO_GLINT_DELAY) / HERO_GLINT_SECONDS
            if (glint in 0f..1f) {
                MenuStyle.glint(graphics, this.x, this.y, this.width, this.height, easeOutCubic(glint), 0.14f)
            }

            // Hypixel's own icon, as its server sends it, filling the button's height; until a ping
            // has brought it, the server glyph in the accent. Either pops a little under the mouse.
            val iconBox = this.height - HERO_ICON_INSET * 2
            val iconCenterX = this.x + HERO_ICON_INSET + iconBox / 2f
            val iconCenterY = this.y + this.height / 2f
            val pop = 1f + ICON_POP * 0.5f * hover
            graphics.pose().pushMatrix()
            graphics.pose().translate(iconCenterX, iconCenterY)
            val serverIcon = HypixelStatus.icon
            if (serverIcon != null) {
                graphics.pose().scale(pop, pop)
                val half = iconBox / 2
                graphics.blit(RenderPipelines.GUI_TEXTURED, serverIcon, -half, -half, 0f, 0f,
                    half * 2, half * 2, 64, 64, 64, 64, fade(0xFFFFFFFF.toInt(), appear))
            } else {
                val glyph = MenuStyle.icon(ICON_SERVER)
                graphics.pose().scale(HERO_ICON_SCALE * pop, HERO_ICON_SCALE * pop)
                graphics.text(font, glyph, -font.width(glyph) / 2, -4, fade(MenuStyle.accent(), appear), false)
            }
            graphics.pose().popMatrix()

            // The name in the title font over the status line, the two centred on the height.
            val textX = this.x + HERO_ICON_INSET + iconBox + 8
            val blockTop = this.y + (this.height - (TITLE_HEIGHT + 4 + 8)) / 2
            val title = GuiFont.title("Join Hypixel")
            if (title != null) {
                graphics.text(font, title, textX, blockTop + TITLE_BASELINE_DROP, fade(TEXT, appear), false)
            } else {
                graphics.text(font, this.message, textX, blockTop + 2, fade(TEXT, appear), false)
            }

            // The arrow at the end, half there at rest, moving along and brightening under the mouse.
            val arrow = MenuStyle.icon(MenuStyle.ICON_PLAY)
            val arrowX = this.x + this.width - 10 - font.width(arrow) - HERO_ARROW_SLIDE
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
     * A row of the column: its icon and its name on glass. Under the mouse the glass brightens and
     * its edge takes the accent, the icon takes the accent too, the name slides along a little and
     * a chevron fades in at the end. Quit is the same row on the red pill's glass.
     */
    private inner class RowButton(
        appearIndex: Int,
        x: Int, y: Int, width: Int, height: Int,
        private val glyph: String,
        label: String,
        private val quit: Boolean,
        action: () -> Unit,
    ) : MenuButton(appearIndex, x, y, width, height, message = GuiFont.text(label), onClickAction = action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val font = mc.font
            glass(graphics, this.x, this.y, this.width, this.height, buttonRadius, appear)
            val iconColor: Int
            val textColor: Int
            if (quit) {
                MenuStyle.quitBox(graphics, font, this.x, this.y, this.width, this.height, buttonRadius,
                    Component.empty(), hover, appear)
                iconColor = QUIT_TEXT
                textColor = QUIT_TEXT
            } else {
                MenuStyle.glassBox(graphics, this.x, this.y, this.width, this.height, buttonRadius, hover, appear)
                iconColor = ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, MenuStyle.accent(), hover)
                textColor = ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, TEXT, hover)
            }

            val icon = MenuStyle.icon(glyph)
            val textY = this.y + (this.height - 8) / 2
            val left = this.x + ROW_PADDING
            graphics.text(font, icon, left, textY, fade(iconColor, appear), false)
            graphics.pose().pushMatrix()
            graphics.pose().translate(2f * hover, 0f)
            graphics.text(font, this.message, left + font.width(icon) + 7, textY, fade(textColor, appear), false)
            graphics.pose().popMatrix()

            if (!quit && hover > 0.02f) {
                val chevron = MenuStyle.icon(MenuStyle.ICON_CHEVRON)
                graphics.pose().pushMatrix()
                graphics.pose().translate(CHEVRON_SLIDE * hover, 0f)
                graphics.text(font, chevron, this.x + this.width - ROW_PADDING - font.width(chevron) - CHEVRON_SLIDE.toInt(),
                    textY, fade(TEXT, appear * hover), false)
                graphics.pose().popMatrix()
            }
        }
    }

    /**
     * The What's New card: the newest release's version, its first changes, and how many there are
     * in all; a dot in the accent until the player has opened them. Clicking it opens the release
     * notes window.
     */
    private inner class CardButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(APPEAR_CARD, x, y, width, height, fromAbove = true, message = GuiFont.text("What's New"),
            onClickAction = action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val notes = ReleaseNotes.shown() ?: return
            val font = mc.font
            glass(graphics, this.x, this.y, this.width, this.height, buttonRadius + 2, appear)
            ModernGuiUtils.drawRoundedRect(graphics, this.x, this.y, this.width, this.height, buttonRadius + 2,
                fade(CARD_FILL, appear))
            MenuStyle.glassBox(graphics, this.x, this.y, this.width, this.height, buttonRadius + 2, hover, appear)

            // The head: the unread dot, the name, and the version at the right in the accent.
            val left = this.x + CARD_PAD
            val right = this.x + this.width - CARD_PAD
            var headX = left
            if (ReleaseNotes.unread(notes)) {
                MenuStyle.statusLine(graphics, font, left, this.y + CARD_PAD, MenuStyle.accent(), "", pulse = true, appear = appear)
                headX += GuiFont.width(font, "● ")
            }
            graphics.text(font, this.message, headX, this.y + CARD_PAD, fade(TEXT, appear), false)
            val version = GuiFont.text(notes.version)
            graphics.text(font, version, right - font.width(version), this.y + CARD_PAD, fade(MenuStyle.accent(), appear), false)

            // The first changes, each on one line, cut with an ellipsis where it would not fit.
            val items = notes.sections.flatMap { it.items }.take(CARD_ITEMS)
            var lineY = this.y + CARD_PAD + 12
            val bullet = GuiFont.text("• ")
            val textX = left + font.width(bullet)
            for (item in items) {
                graphics.text(font, bullet, left, lineY, fade(MUTED, appear), false)
                graphics.text(font, GuiFont.text(fit(font, item, right - textX)), textX, lineY, fade(MenuStyle.TEXT_SOFT, appear), false)
                lineY += CARD_LINE
            }

            // The way to all of them, moving along under the mouse.
            val more = GuiFont.text("All ${notes.itemCount} changes")
            val moreY = this.y + this.height - CARD_PAD - 8
            graphics.text(font, more, left, moreY, fade(MenuStyle.accent(), appear), false)
            graphics.text(font, MenuStyle.icon(MenuStyle.ICON_CHEVRON), left + font.width(more) + 4 + Math.round(2 * hover),
                moreY, fade(MenuStyle.accent(), appear), false)
        }

        /** The text, shortened with an ellipsis to fit [width]. */
        private fun fit(font: net.minecraft.client.gui.Font, text: String, width: Int): String {
            if (GuiFont.width(font, text) <= width) return text
            var end = text.length
            while (end > 0 && GuiFont.width(font, text.substring(0, end).trimEnd() + "…") > width) end--
            return text.substring(0, end).trimEnd() + "…"
        }
    }
}
