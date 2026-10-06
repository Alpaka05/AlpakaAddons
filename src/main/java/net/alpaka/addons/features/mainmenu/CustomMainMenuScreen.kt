package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.AlpakaConfigScreen
import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.BlurRectRenderState
import net.alpaka.addons.client.gui.GradientRoundedRectRenderState
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.chat.ChatBlurFeature
import net.alpaka.addons.features.snow.SnowOverlayRenderer
import net.alpaka.addons.features.wheel.WheelMesh
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
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import kotlin.math.PI
import kotlin.math.sin

/**
 * The main menu, laid out like a game's lobby: the player's own avatar standing in a scene, and a
 * card of dark glass on the left holding the menu.
 *
 * The scene is what makes it more than a list of buttons (see [MenuAmbience]): a picture of a place,
 * softly out of focus and drifting like an idle camera, with fireflies rising through it. In front
 * of it, on the ground to the right, the player's skin stands as a live 3D model (see [MenuAvatar])
 * under the player's name, with a shadow at its feet. It looks at the mouse, can be turned by
 * dragging and waves when clicked. The mouse moves the picture, the avatar and the fireflies by
 * different amounts, which is what gives the flat picture its depth. Without the picture, the
 * panorama stands in.
 *
 * The card holds the logo - also the way into the Alpaka config - and the mod's name; Join Hypixel,
 * set apart by a slowly circling gradient edge, a glint that crosses it now and then and a pulsing
 * live dot beside Hypixel's player count and ping; then the rows - Singleplayer, Multiplayer, Join
 * Alpha, Mods, Options - and at its foot the versions and a red Quit.
 *
 * Opening plays in order: the card springs in from the left with a glint sweeping across it, the
 * rows follow one by one, and the avatar lands on its spot.
 * Under the mouse a row slides out a little, its icon tile pops in the accent and its chevron
 * moves along.
 *
 * The card is only as tall as what it holds. Most players see this at Auto GUI scale, 240 to 270
 * pixels tall, so its sizes come in three steps and the roomiest that fits is used.
 */
class CustomMainMenuScreen : Screen(Component.literal("Custom Main Menu")) {

    companion object {
        val MOD_ICON_ID: Identifier = Identifier.parse("alpaka:textures/gui/alpaka_icon.png")

        /**
         * The menu's line icons, a font of their own drawn by tools/GenMenuIcons.java: glyphs, so
         * they sit on the text's baseline and take its colour (see CustomPauseScreen), but smooth
         * where the pause menu's are pixel art. Written as escapes: the private-use characters
         * themselves are invisible in most editors.
         */
        private val ICON_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", "menu_icons"))
        private const val ICON_SERVER = "\ue000"
        private const val ICON_PERSON = "\ue001"
        private const val ICON_PEOPLE = "\ue002"
        private const val ICON_FLASK = "\ue003"
        private const val ICON_PUZZLE = "\ue004"
        private const val ICON_SLIDERS = "\ue005"
        private const val ICON_POWER = "\ue006"
        private const val ICON_CHEVRON = "\ue007"

        /** Singleplayer, Multiplayer, Join Alpha, Mods and Options. */
        private const val ROWS = 5

        /** The card's sizes, roomiest first; see [Sizes]. */
        private val ROOMY = Sizes(margin = 14, pad = 14, width = 212, radius = 16, header = 30, gapHeader = 12,
            hero = 40, gapHero = 10, row = 24, rowGap = 3, footer = 22, footerGap = 10)
        private val NORMAL = Sizes(margin = 8, pad = 12, width = 198, radius = 14, header = 26, gapHeader = 10,
            hero = 36, gapHero = 8, row = 21, rowGap = 2, footer = 20, footerGap = 8)
        private val TIGHT = Sizes(margin = 6, pad = 10, width = 186, radius = 12, header = 26, gapHeader = 8,
            hero = 30, gapHero = 6, row = 18, rowGap = 1, footer = 18, footerGap = 6)

        private const val LOGO_HOVER_GROWTH = 0.1f
        private const val LOGO_BOB_PIXELS = 1.2f
        private const val LOGO_WIGGLE_DEGREES = 7f

        /**
         * The titles - the mod's name and the player's - in the larger title font. Its glyphs hang
         * from a baseline further down than the normal text's, so they are drawn this far below
         * their box's top for the capitals to start there.
         */
        private const val TITLE_HEIGHT = 13
        private const val TITLE_BASELINE_DROP = 4

        /** The card's glass: its tint over the blurred panorama and how strongly that covers the blur. */
        private const val CARD_TINT = 0xFF0D1117.toInt()
        private const val CARD_STRENGTH = 0.72f
        private const val CARD_EDGE = 0x1AFFFFFF
        private const val CARD_SHADOW = 0.9f

        /** Join Hypixel: a lighter panel washed with the accent from the left, in a circling gradient edge. */
        private const val HERO_FILL = 0x10FFFFFF
        private const val HERO_FILL_HOVER = 0x22FFFFFF
        private const val HERO_WASH = 0.16f
        private const val HERO_EDGE_REST = 0.6f
        /** One turn of the gradient around the edge, and the time between glints. */
        private const val HERO_EDGE_TURN_SECONDS = 4.0f
        private const val HERO_GLINT_EVERY = 5.5f
        private const val HERO_GLINT_SECONDS = 0.9f

        /** The rows: bare at rest, a light band under the mouse; the icon tile and the chevron. */
        private const val ROW_HOVER = 0x18FFFFFF
        private const val ROW_SLIDE = 3f
        private const val TILE = 0x14FFFFFF
        private const val TILE_HOVER_ACCENT = 0.55f
        private const val TILE_POP = 0.14f
        private const val CHEVRON = 0x59FFFFFF
        private const val CHEVRON_HOVER = 0xE6FFFFFF.toInt()
        private const val CHEVRON_SLIDE = 3f

        private const val SEPARATOR = 0x1AFFFFFF
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val TEXT_SOFT = 0xEBFFFFFF.toInt()
        private const val MUTED = 0x9EFFFFFF.toInt()
        private const val ONLINE_DOT = 0xFF4ADE80.toInt()

        private const val QUIT_FILL = 0x29EF4444
        private const val QUIT_FILL_HOVER = 0x4DEF4444
        private const val QUIT_TEXT = 0xFFFCA5A5.toInt()
        private const val QUIT_RING = 0xFFEF4444.toInt()
        /** Room left and right of Quit's label inside its pill. */
        private const val QUIT_PADDING = 10

        /**
         * The stage beside the card: the avatar's height as a share of the screen and its limits,
         * and where its feet stand - low on the screen, on the ground near the camera.
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

        private const val HOVER_EASE = 0.25f
        private const val APPEAR_SECONDS = 0.32f
        private const val STAGGER_SECONDS = 0.045f
        private const val SLIDE_IN = 28f
        private const val CARD_GLINT_DELAY = 0.30f
        private const val CARD_GLINT_SECONDS = 0.85f
        private const val STAGE_DELAY = 0.20f
        private const val LANDING_SECONDS = 0.55f

        private var modIconRegistered = false

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

        private fun icon(icon: String): Component =
            Component.literal(icon).withStyle { it.withFont(ICON_FONT) }

        /** Quit's label: the power icon, then the word. */
        private fun quitLabel(): Component =
            Component.empty().append(icon(ICON_POWER)).append(GuiFont.text("  Quit"))

        private fun fade(color: Int, factor: Float): Int {
            val alpha = Math.round(((color ushr 24) and 0xFF) * factor.coerceIn(0f, 1f))
            return (alpha shl 24) or (color and 0x00FFFFFF)
        }

        private fun accent(): Int = ModernGuiUtils.getAccentColor() or 0xFF000000.toInt()

        /** Eased out with a little overshoot, for things that spring into place. */
        private fun easeOutBack(t: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val u = t - 1f
            return 1f + c3 * u * u * u + c1 * u * u
        }

        private fun easeOutCubic(t: Float): Float {
            val inv = 1f - t.coerceIn(0f, 1f)
            return 1f - inv * inv * inv
        }
    }

    /**
     * One step of the card's sizes: its least distance from the screen's edges, inner padding, width
     * and corner radius, the logo row, Join Hypixel, the rows and the footer, and the gaps between
     * them.
     */
    private class Sizes(
        val margin: Int, val pad: Int, val width: Int, val radius: Int,
        val header: Int, val gapHeader: Int, val hero: Int, val gapHero: Int,
        val row: Int, val rowGap: Int, val footer: Int, val footerGap: Int,
    ) {
        /** The card's height: exactly what it holds. */
        val height = pad * 2 + header + gapHeader + hero + gapHero + ROWS * row + (ROWS - 1) * rowGap +
            footerGap * 2 + 1 + footer
    }

    private var openTime = 0L
    private var logoHover = 0.0f
    private var avatar: MenuAvatar? = null
    private var hasBackground = false

    /** The mouse's place from the screen's centre, -1 → 1, eased; drives the parallax. */
    private var parallaxX = 0f
    private var parallaxY = 0f
    private var lastFrameMs = 0L

    /** Where the card and everything on it sit on the current screen. */
    private inner class Layout {
        val sizes = listOf(ROOMY, NORMAL).firstOrNull {
            this@CustomMainMenuScreen.height >= it.height + it.margin * 2
        } ?: TIGHT

        val cardX = sizes.margin
        val cardW = sizes.width
        val cardH = sizes.height
        val cardY = maxOf(sizes.margin, (this@CustomMainMenuScreen.height - cardH) / 2)
        val innerX = cardX + sizes.pad
        val innerW = cardW - sizes.pad * 2

        val logoSize = sizes.header
        val logoX = innerX
        val logoY = cardY + sizes.pad
        val heroY = logoY + sizes.header + sizes.gapHeader
        val rowsY = heroY + sizes.hero + sizes.gapHero
        val footerY = cardY + cardH - sizes.pad - sizes.footer
        val separatorY = footerY - sizes.footerGap - 1

        /** The stage to the card's right, and the avatar's size and place on it; null when too narrow. */
        val stageX0 = cardX + cardW + STAGE_GAP
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

        this.addRenderableWidget(HeroButton(layout.innerX, layout.heroY, layout.innerW, sizes.hero) {
            joinServer("mc.hypixel.net")
        })

        // The rows reach a little past the text column, so the hover band has some room around
        // the tile and the chevron.
        val rowX = layout.innerX - 4
        val rowW = layout.innerW + 8
        var y = layout.rowsY
        var index = 3
        fun row(glyph: String, label: String, action: () -> Unit) {
            this.addRenderableWidget(RowButton(index++, rowX, y, rowW, sizes.row, glyph, label, action))
            y += sizes.row + sizes.rowGap
        }
        row(ICON_PERSON, "Singleplayer") {
            this.minecraft?.gui?.setScreen(SelectWorldScreen(this))
        }
        row(ICON_PEOPLE, "Multiplayer") {
            this.minecraft?.gui?.setScreen(JoinMultiplayerScreen(this))
        }
        row(ICON_FLASK, "Join Alpha") {
            joinServer("alpha.hypixel.net")
        }
        row(ICON_PUZZLE, "Mods") {
            val mc = this.minecraft ?: return@row
            // Falls back to the options screen without Mod Menu; see ModMenuCompat for why the
            // Mod Menu class must not be named here.
            if (net.alpaka.addons.compat.ModMenuCompat.isLoaded()) {
                net.alpaka.addons.compat.ModMenuCompat.openModsScreen(this)
            } else {
                mc.gui.setScreen(OptionsScreen(this, mc.options, false))
            }
        }
        row(ICON_SLIDERS, "Options") {
            val mc = this.minecraft ?: return@row
            mc.gui.setScreen(OptionsScreen(this, mc.options, false))
        }

        val quitWidth = this.font.width(quitLabel()) + QUIT_PADDING * 2
        this.addRenderableWidget(QuitButton(layout.innerX + layout.innerW - quitWidth, layout.footerY,
            quitWidth, sizes.footer) {
            this.minecraft?.stop()
        })

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
        MenuAmbience.shade(graphics, this.width, this.height, (layout.cardX + layout.cardW).toFloat())

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
        extractCard(graphics, layout, mouseX, mouseY, t)
    }

    /**
     * The avatar on its spot in the scene, its shadow, and the player's name above it.
     *
     * In the background picture the spot is a place in it ([SPOT_X], [SPOT_FEET]): the avatar is
     * pinned there and moves exactly as the picture does, drift and parallax alike, so its feet stay
     * on the same stones. Over the panorama there is no ground to stand on, and it stands on the
     * stage to the card's right instead, shifting against the mouse a layer in front.
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
            // On a narrow screen the spot can fall behind the card; then there is no avatar.
            if (cx - modelHeight * 0.3f < layout.cardX + layout.cardW) return
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

    /** The card: glass, the glint across it on opening, the logo and name, and the footer. */
    private fun extractCard(graphics: GuiGraphicsExtractor, layout: Layout, mouseX: Int, mouseY: Int, t: Float) {
        val sizes = layout.sizes
        val cardProgress = progress(0)
        if (cardProgress <= 0f) return
        val cardAlpha = easeOutCubic(cardProgress)
        val slide = (1f - easeOutBack(cardProgress)) * -SLIDE_IN

        graphics.pose().pushMatrix()
        graphics.pose().translate(slide, 0f)
        ModernGuiUtils.drawPanelShadow(graphics, layout.cardX, layout.cardY, layout.cardW, layout.cardH,
            sizes.radius, CARD_SHADOW * cardAlpha)
        glass(graphics, layout.cardX, layout.cardY, layout.cardW, layout.cardH, sizes.radius, cardAlpha)
        ModernGuiUtils.drawRoundedOutline(graphics, layout.cardX, layout.cardY, layout.cardW, layout.cardH,
            sizes.radius, fade(CARD_EDGE, cardAlpha))

        // One glint across the whole card as it lands.
        val glint = (elapsed() - CARD_GLINT_DELAY) / CARD_GLINT_SECONDS
        if (glint in 0f..1f) {
            glint(graphics, layout.cardX, layout.cardY, layout.cardW, layout.cardH, easeOutCubic(glint), 0.10f)
        }

        // The logo, which is also the way into the Alpaka config. It floats gently; under the mouse
        // it grows and gives a little wiggle. Beside it the name, centred on the logo's height.
        val headerProgress = progress(1)
        if (headerProgress > 0f) {
            val headerAlpha = easeOutCubic(headerProgress)
            val hovered = isOverLogo(layout, mouseX.toDouble(), mouseY.toDouble())
            logoHover += ((if (hovered) 1.0f else 0.0f) - logoHover) * HOVER_EASE
            val size = layout.logoSize * (1f + LOGO_HOVER_GROWTH * logoHover)
            val bob = sin(t * 1.6f) * LOGO_BOB_PIXELS
            val wiggle = sin(t * 11f) * LOGO_WIGGLE_DEGREES * logoHover

            ensureModIconRegistered()
            graphics.pose().pushMatrix()
            graphics.pose().translate(layout.logoX + layout.logoSize / 2f, layout.logoY + layout.logoSize / 2f + bob)
            graphics.pose().rotate((wiggle * PI / 180.0).toFloat())
            val half = Math.round(size / 2f)
            graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICON_ID, -half, -half, 0.0f, 0.0f,
                half * 2, half * 2, 128, 128, 128, 128, fade(0xFFFFFFFF.toInt(), headerAlpha))
            graphics.pose().popMatrix()

            val textX = layout.logoX + layout.logoSize + 8
            val title = GuiFont.title("Alpaka Addons")
            if (title != null) {
                val titleTop = layout.logoY + (layout.logoSize - TITLE_HEIGHT) / 2
                graphics.text(this.font, title, textX, titleTop + TITLE_BASELINE_DROP, fade(TEXT, headerAlpha), false)
            } else {
                graphics.text(this.font, Component.literal("Alpaka Addons"), textX, layout.logoY + (layout.logoSize - 8) / 2,
                    fade(TEXT, headerAlpha), false)
            }
        }

        // The footer: a hairline across the card, and the versions beside Quit. A build between
        // releases shows as "dev" rather than with its commit (1.2.22+10.e43f661.dirty), which does
        // not fit beside Quit; the jar's name carries it.
        val footerProgress = progress(9)
        if (footerProgress > 0f) {
            val footerAlpha = easeOutCubic(footerProgress)
            graphics.fill(layout.innerX, layout.separatorY, layout.innerX + layout.innerW, layout.separatorY + 1,
                fade(SEPARATOR, footerAlpha))
            val version = ModVersion.mod()
            val shown = if (version.contains('+')) version.substringBefore('+') + " dev" else version
            val linesTop = layout.footerY + (sizes.footer - 18) / 2
            graphics.text(this.font, GuiFont.text("Alpaka $shown"), layout.innerX, linesTop,
                fade(MUTED, footerAlpha), false)
            graphics.text(this.font, GuiFont.text("Minecraft ${ModVersion.minecraft()}"), layout.innerX, linesTop + 10,
                fade(MUTED, footerAlpha), false)
        }
        graphics.pose().popMatrix()
    }

    /**
     * The card's glass: the blurred panorama behind it, darkened, the whole of it at [opacity].
     * Without the extractor mixin nothing rounded can be submitted, and a plain dark card stands in.
     */
    private fun glass(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, opacity: Float) {
        val tinted = (Math.round(CARD_STRENGTH * 255f) shl 24) or (CARD_TINT and 0x00FFFFFF)
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
     * A rounded rectangle with a colour at each corner, filled when [thickness] is 0 or as an edge
     * that many screen pixels wide; nothing without the extractor mixin.
     */
    private fun gradientRect(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int,
                             thickness: Int, topLeft: Int, topRight: Int, bottomRight: Int, bottomLeft: Int) {
        val sink = graphics as? AlpakaGuiElementSink ?: return
        val toScreen = guiScale()
        sink.`alpaka$submitElement`(GradientRoundedRectRenderState(
            Matrix3x2f(graphics.pose()), x, y, x + width, y + height, Math.round(radius * toScreen), thickness,
            topLeft, topRight, bottomRight, bottomLeft, toScreen, sink.`alpaka$currentScissor`()))
    }

    /**
     * A slanted band of light crossing the box from left to right as [progress] runs 0 → 1, at
     * [strength] at its brightest, clipped to the box.
     */
    private fun glint(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int,
                      progress: Float, strength: Float) {
        val band = 18f
        val slant = height * 0.45f
        val center = x - band - slant + (width + band * 2 + slant * 2) * progress
        val bright = WheelMesh.scaleAlpha(0xFFFFFFFF.toInt(), strength)
        val clear = 0x00FFFFFF
        graphics.enableScissor(x, y, x + width, y + height)
        val mesh = WheelMesh(1.15f / guiScale())
        val top = y.toFloat()
        val bottom = (y + height).toFloat()
        mesh.quad(center - band + slant, top, clear, center - band, bottom, clear,
            center, bottom, bright, center + slant, top, bright)
        mesh.quad(center + slant, top, bright, center, bottom, bright,
            center + band, bottom, clear, center + band + slant, top, clear)
        mesh.submit(graphics)
        graphics.disableScissor()
    }

    private fun guiScale(): Float = (this.minecraft ?: Minecraft.getInstance()).window.guiScale.toFloat()

    /**
     * The dot colour and the text of the online line, from whatever the last ping learned, or null
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
     * The shared base: the click action, hover easing, and the entrance - each button springs in
     * from the left behind the card, at its own moment of the stagger.
     */
    private abstract inner class MenuButton(
        private val appearIndex: Int,
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

            graphics.pose().pushMatrix()
            graphics.pose().translate((1f - easeOutBack(progress)) * -SLIDE_IN * 0.6f, 0f)
            draw(graphics, appear)
            graphics.pose().popMatrix()
        }

        /** Draws the button at its own position; the pose already carries the entrance. */
        protected abstract fun draw(graphics: GuiGraphicsExtractor, appear: Float)

        override fun updateWidgetNarration(narration: NarrationElementOutput) {}
    }

    /**
     * Join Hypixel: a lighter panel washed with the accent from the left, inside an edge whose
     * gradient slowly circles the tile. A glint crosses it every few seconds, and the live dot beside
     * the player count sends out a ring like a pulse. Hover brightens the panel and the edge.
     */
    private inner class HeroButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(2, x, y, width, height, Component.literal("Join Hypixel"), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val t = MenuAmbience.seconds()
            val accent = accent()
            val light = WheelMesh.lerpColor(accent, 0xFFFFFFFF.toInt(), 0.6f)
            val radius = this.height / 4

            ModernGuiUtils.drawRoundedRect(graphics, this.x, this.y, this.width, this.height, radius,
                fade(ModernGuiUtils.lerpColor(HERO_FILL, HERO_FILL_HOVER, hover), appear))
            val wash = fade(accent, HERO_WASH * appear * (1f + hover))
            val clearWash = accent and 0x00FFFFFF
            gradientRect(graphics, this.x, this.y, this.width, this.height, radius, 0, wash, clearWash, clearWash, wash)

            // The edge: each corner's colour runs between the accent and its lighter self, a quarter
            // turn apart, so the bright part travels around the tile.
            val edgeStrength = appear * (HERO_EDGE_REST + (1f - HERO_EDGE_REST) * hover)
            fun corner(k: Int): Int {
                val phase = (t / HERO_EDGE_TURN_SECONDS + k * 0.25f) * 2f * PI.toFloat()
                return fade(WheelMesh.lerpColor(accent, light, 0.5f + 0.5f * sin(phase)), edgeStrength)
            }
            gradientRect(graphics, this.x, this.y, this.width, this.height, radius,
                maxOf(1, Math.round(guiScale())), corner(0), corner(1), corner(2), corner(3))

            val glintPhase = (t % HERO_GLINT_EVERY) / HERO_GLINT_SECONDS
            if (glintPhase in 0f..1f) glint(graphics, this.x, this.y, this.width, this.height, glintPhase, 0.12f * appear)

            val serverIcon = icon(ICON_SERVER)
            val iconX = this.x + 10
            graphics.text(mc.font, serverIcon, iconX, this.y + (this.height - 8) / 2, fade(accent, appear), false)

            // The name, and the online line under it, without the ping when it would not fit.
            val textX = iconX + mc.font.width(serverIcon) + 8
            graphics.text(mc.font, GuiFont.text("Join Hypixel"), textX, this.y + this.height / 2 - 9,
                fade(TEXT, appear), false)

            val room = this.x + this.width - 10 - textX
            val status = onlineLine(withPing = true)?.let { line ->
                if (GuiFont.width(mc.font, "● " + line.second) <= room) line else onlineLine(withPing = false)
            } ?: return
            val (dot, line) = status
            val dotWidth = GuiFont.width(mc.font, "● ")
            if (dotWidth + GuiFont.width(mc.font, line) > room) return
            val lineY = this.y + this.height / 2 + 1
            if (dot == ONLINE_DOT) {
                // The pulse: a ring swelling out of the dot and fading, once a second and a half.
                val p = (t % 1.5f) / 1.5f
                val mesh = WheelMesh(1.15f / guiScale())
                val dotCenterX = textX + mc.font.width(GuiFont.text("●")) / 2f
                val dotCenterY = lineY + 3.5f
                mesh.ring(dotCenterX, dotCenterY, 1.5f + p * 4f, 2.2f + p * 4f,
                    WheelMesh.scaleAlpha(dot, (1f - p) * 0.8f * appear))
                mesh.submit(graphics)
            }
            graphics.text(mc.font, GuiFont.text("●"), textX, lineY, fade(dot, appear), false)
            graphics.text(mc.font, GuiFont.text(line), textX + dotWidth, lineY, fade(MUTED, appear), false)
        }
    }

    /**
     * A row of the menu: the icon on a small tile, the name, and a chevron at the end. Bare at rest;
     * under the mouse a light band lies behind it, the row slides out a little, the tile pops in the
     * accent and the chevron moves along.
     */
    private inner class RowButton(
        appearIndex: Int,
        x: Int, y: Int, width: Int, height: Int,
        private val glyph: String,
        label: String,
        action: () -> Unit,
    ) : MenuButton(appearIndex, x, y, width, height, GuiFont.text(label), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            if (hover > 0.02f) {
                ModernGuiUtils.drawRoundedRect(graphics, this.x, this.y, this.width, this.height, 6,
                    fade(ROW_HOVER, appear * hover))
            }

            val slide = ROW_SLIDE * hover
            val tile = this.height - 4
            val tileX = this.x + 4 + slide
            val tileY = this.y + 2f
            val tileColor = ModernGuiUtils.lerpColor(TILE, fade(accent(), TILE_HOVER_ACCENT), hover)
            graphics.pose().pushMatrix()
            graphics.pose().translate(tileX + tile / 2f, tileY + tile / 2f)
            val pop = 1f + TILE_POP * hover
            graphics.pose().scale(pop, pop)
            ModernGuiUtils.drawRoundedRect(graphics, -tile / 2, -tile / 2, tile, tile, tile / 3, fade(tileColor, appear))
            val glyphComponent = icon(glyph)
            graphics.text(mc.font, glyphComponent, -mc.font.width(glyphComponent) / 2 + 1, -4, fade(TEXT, appear), false)
            graphics.pose().popMatrix()

            val textY = this.y + (this.height - 8) / 2
            graphics.pose().pushMatrix()
            graphics.pose().translate(slide, 0f)
            graphics.text(mc.font, this.message, this.x + 4 + tile + 8, textY,
                fade(ModernGuiUtils.lerpColor(TEXT_SOFT, TEXT, hover), appear), false)
            graphics.pose().popMatrix()

            val chevron = icon(ICON_CHEVRON)
            graphics.pose().pushMatrix()
            graphics.pose().translate(CHEVRON_SLIDE * hover, 0f)
            graphics.text(mc.font, chevron, this.x + this.width - 6 - mc.font.width(chevron) - CHEVRON_SLIDE.toInt(), textY,
                fade(ModernGuiUtils.lerpColor(CHEVRON, CHEVRON_HOVER, hover), appear), false)
            graphics.pose().popMatrix()
        }
    }

    /** Quit, at the foot of the card: a red-tinted pill with the power icon, ringed in red under the mouse. */
    private inner class QuitButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(9, x, y, width, height, quitLabel(), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val radius = this.height / 2
            ModernGuiUtils.drawRoundedRect(graphics, this.x, this.y, this.width, this.height, radius,
                fade(ModernGuiUtils.lerpColor(QUIT_FILL, QUIT_FILL_HOVER, hover), appear))
            if (hover > 0.02f) {
                ModernGuiUtils.drawRoundedOutline(graphics, this.x, this.y, this.width, this.height, radius,
                    fade(QUIT_RING, appear * hover))
            }
            ModernGuiUtils.centeredText(graphics, mc.font, this.message, this.x + this.width / 2,
                this.y + (this.height - 8) / 2, fade(QUIT_TEXT, appear))
        }
    }
}
