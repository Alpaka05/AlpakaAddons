package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.AlpakaConfigScreen
import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.mainmenu.MenuStyle.HOVER_EASE
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_FLASK
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_PEOPLE
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_PERSON
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_POWER
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_PUZZLE
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_SERVER
import net.alpaka.addons.features.mainmenu.MenuStyle.ICON_SLIDERS
import net.alpaka.addons.features.mainmenu.MenuStyle.ONLINE_DOT
import net.alpaka.addons.features.mainmenu.MenuStyle.easeOutCubic
import net.alpaka.addons.features.mainmenu.MenuStyle.fade
import net.alpaka.addons.features.snow.SnowOverlayRenderer
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

/**
 * The main menu, laid out like a game's lobby: the player's own avatar standing in a scene, and the
 * menu beside it as a window of the config menu's kind, so the mod's screens read as one.
 *
 * The scene is what makes it more than a list of buttons (see [MenuAmbience]): a picture of a place,
 * softly out of focus and drifting like an idle camera, with fireflies rising through it. In front
 * of it, on the ground to the right, the player's skin stands as a live 3D model (see [MenuAvatar])
 * under a small name plate, with a shadow at its feet. It looks at the mouse, can be turned by
 * dragging and waves when clicked. The mouse moves the picture, the avatar and the fireflies by
 * different amounts, which is what gives the flat picture its depth. Without the picture, the
 * panorama stands in.
 *
 * The window is the config menu's: the dark panel, and its header - the logo, "ALPAKA" and the
 * version - which is also the way into the config. Under it Join Hypixel, washed in the accent with
 * Hypixel's player count and ping beside a pulsing live dot, then the rows as the config lists its
 * features - Singleplayer, Multiplayer, Join Alpha, Mods, Options - and at its foot the Minecraft
 * version and a red Quit.
 *
 * Opening plays in order: the window pops open as the config menu does, the rows fade in one by
 * one, and the avatar lands on its spot. Under the mouse a row goes a step lighter, its icon takes
 * the accent and its chevron moves along.
 *
 * The window is only as tall as what it holds. Most players see this at Auto GUI scale, 240 to 270
 * pixels tall, so the rows grow with the room there is, between [ROW_MIN] and [ROW_MAX].
 */
class CustomMainMenuScreen : Screen(Component.literal("Custom Main Menu")) {

    companion object {
        /** Singleplayer, Multiplayer, Join Alpha, Mods and Options. */
        private const val ROWS = 5

        /** The window: its width, its least distance from the screen's edges, and its padding. */
        private const val PANEL_W = 176
        private const val MARGIN = 8
        private const val PAD = AlpakaStyle.PAD
        /** A row's height, and how much taller Join Hypixel is, for its status line. */
        private const val ROW_MIN = 17
        private const val ROW_MAX = 22
        private const val HERO_EXTRA = 12
        private const val ROW_GAP = 3
        /** The room under Join Hypixel, and around the hairline over the footer. */
        private const val HERO_GAP = 7
        private const val FOOTER_GAP = 7

        /** Everything in the window but the rows and Join Hypixel. */
        private const val FIXED_H = AlpakaStyle.HEADER_H + PAD + HERO_GAP + (ROWS - 1) * ROW_GAP +
            FOOTER_GAP * 2 + 1 + AlpakaStyle.BUTTON_H + PAD

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

        /** The rows' fade-in after the window's pop: each this long, one after the other. */
        private const val APPEAR_SECONDS = 0.24f
        private const val STAGGER_SECONDS = 0.035f
        private const val ROWS_DELAY = 0.06f
        /** How far a row slides in from the left as it fades in. */
        private const val SLIDE_IN = 6f
        private const val STAGE_DELAY = 0.20f
        private const val LANDING_SECONDS = 0.55f

        /** The name plate over the avatar: the panel's dark, a little see-through. */
        private const val PLATE = 0xE6131313.toInt()
        private const val PLATE_H = 16
    }

    private var openTime = 0L
    private var headerHover = 0.0f
    private var avatar: MenuAvatar? = null
    private var hasBackground = false

    /** The mouse's place from the screen's centre, -1 → 1, eased; drives the parallax. */
    private var parallaxX = 0f
    private var parallaxY = 0f
    private var lastFrameMs = 0L

    /** Where the window and everything in it sit on the current screen. */
    private inner class Layout {
        private val screenH = this@CustomMainMenuScreen.height
        val row = ((screenH - MARGIN * 2 - FIXED_H - HERO_EXTRA) / (ROWS + 1)).coerceIn(ROW_MIN, ROW_MAX)
        val hero = row + HERO_EXTRA

        val panelX = MARGIN
        val panelW = PANEL_W
        val panelH = FIXED_H + hero + ROWS * row
        val panelY = maxOf(MARGIN, (screenH - panelH) / 2)
        val innerX = panelX + PAD
        val innerW = panelW - PAD * 2

        val heroY = panelY + AlpakaStyle.HEADER_H + PAD
        val rowsY = heroY + hero + HERO_GAP
        val footerY = panelY + panelH - PAD - AlpakaStyle.BUTTON_H
        val separatorY = footerY - FOOTER_GAP - 1

        /** The stage to the window's right, and the avatar's size and place on it. */
        val stageX0 = panelX + panelW + STAGE_GAP
        val stageWidth = this@CustomMainMenuScreen.width - stageX0 - STAGE_GAP
        val hasStage = stageWidth >= STAGE_MIN_WIDTH
        val avatarHeight = minOf(screenH * AVATAR_SCREEN_SHARE, stageWidth * AVATAR_WIDTH_SHARE).toInt()
        val stageCenterX = stageX0 + stageWidth / 2
        val feetY = (screenH * FEET_SCREEN_SHARE).toInt()

        /** The window's centre, which it pops open around. */
        val centerX = panelX + panelW / 2f
        val centerY = panelY + panelH / 2f
    }

    private fun isOverHeader(layout: Layout, mouseX: Double, mouseY: Double): Boolean {
        val box = MenuStyle.brandBox(this.font, layout.panelX, layout.panelY)
        return AlpakaStyle.inside(mouseX, mouseY, box[0], box[1], box[2], box[3])
    }

    private fun elapsed(): Float = (System.currentTimeMillis() - openTime) / 1000.0f

    /** 0 → 1 of the element with this stagger index through its fade-in, eased. */
    private fun appear(index: Int): Float =
        easeOutCubic((elapsed() - ROWS_DELAY - index * STAGGER_SECONDS) / APPEAR_SECONDS)

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

        this.addRenderableWidget(HeroButton(layout.innerX, layout.heroY, layout.innerW, layout.hero) {
            joinServer("mc.hypixel.net")
        })

        var y = layout.rowsY
        var index = 1
        fun row(glyph: String, label: String, action: () -> Unit) {
            this.addRenderableWidget(RowButton(index++, layout.innerX, y, layout.innerW, layout.row, glyph, label, action))
            y += layout.row + ROW_GAP
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

        val quitWidth = GuiFont.width(this.font, "Quit") + this.font.width(MenuStyle.icon(ICON_POWER)) + 6 + 20
        this.addRenderableWidget(QuitButton(layout.innerX + layout.innerW - quitWidth, layout.footerY,
            quitWidth, AlpakaStyle.BUTTON_H) {
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
        MenuAmbience.shade(graphics, this.width, this.height, (layout.panelX + layout.panelW).toFloat())

        if (AlpakaConfig.instance.inventorySnowEnabled) {
            SnowOverlayRenderer.render(graphics, this.width, this.height)
        }
        MenuAmbience.motes(graphics, this.width, this.height, t, nx, ny, MOTE_PARALLAX, MOTE_COLOR,
            MOTE_OPACITY * easeOutCubic(elapsed() / 0.6f))

        if (placement != null || layout.hasStage) extractStage(graphics, layout, placement, preciseX, preciseY, nx, ny)
        extractPanel(graphics, layout, mouseX, mouseY, t)
    }

    /** The buttons pop open with the window they sit in. */
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val layout = Layout()
        val pushed = MenuStyle.beginOpen(graphics, openTime, layout.centerX, layout.centerY)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        MenuStyle.endOpen(graphics, pushed)
    }

    /**
     * The avatar on its spot in the scene, its shadow, and the name plate above it.
     *
     * In the background picture the spot is a place in it ([SPOT_X], [SPOT_FEET]): the avatar is
     * pinned there and moves exactly as the picture does, drift and parallax alike, so its feet stay
     * on the same stones. Over the panorama there is no ground to stand on, and it stands on the
     * stage to the window's right instead, shifting against the mouse a layer in front.
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
            // On a narrow screen the spot can fall behind the window; then there is no avatar.
            if (cx - modelHeight * 0.3f < layout.panelX + layout.panelW) return
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

        val nameAppear = easeOutCubic((since - 0.35f) / 0.5f)
        if (nameAppear <= 0.01f) return
        val plateTop = maxOf(4f, feet - MenuAmbience.snapToScreen(height + 10f + PLATE_H))
        namePlate(graphics, cx, plateTop + (1f - nameAppear) * 6f, nameAppear)
    }

    /**
     * The greeting over the avatar's head, on a small plate of the window's dark so it reads on any
     * part of the picture: "Welcome back," muted, then the player's name.
     *
     * Placed by offsets from the avatar's feet and centre, each snapped once: the feet and centre
     * are on the screen-pixel grid the picture and the avatar step on, so the plate steps with them.
     * Rounded to whole GUI pixels instead, it jumped at other moments than the avatar, several
     * screen pixels at a time, and wobbled over its head.
     */
    private fun namePlate(graphics: GuiGraphicsExtractor, centerX: Float, top: Float, appear: Float) {
        val mc = this.minecraft ?: return
        val greeting = GuiFont.text("Welcome back, ")
        val name = GuiFont.text(mc.user.name)
        val textW = this.font.width(greeting) + this.font.width(name)
        val plateW = textW + 14
        // Kept on the screen and clear of the window when the avatar stands near an edge.
        val left = (centerX - MenuAmbience.snapToScreen(plateW / 2f))
            .coerceAtMost(this.width - 4f - plateW).coerceAtLeast(Layout().let { it.panelX + it.panelW + 4f })
        graphics.pose().pushMatrix()
        graphics.pose().translate(left, top)
        ModernGuiUtils.drawRoundedRect(graphics, 0, 0, plateW, PLATE_H, AlpakaStyle.CARD_RADIUS, fade(PLATE, appear))
        // The accent's thin line along the plate's foot, like the config's focused field.
        ModernGuiUtils.drawRect(graphics, 5, PLATE_H - 1, plateW - 10, 1, fade(MenuStyle.accent(), 0.7f * appear))
        val textY = (PLATE_H - 8) / 2
        graphics.text(this.font, greeting, 7, textY, fade(AlpakaStyle.TEXT_MUTED, appear), false)
        graphics.text(this.font, name, 7 + this.font.width(greeting), textY, fade(AlpakaStyle.TEXT, appear), false)
        graphics.pose().popMatrix()
    }

    /** The window: the panel, its header and the footer's hairline and Minecraft version. */
    private fun extractPanel(graphics: GuiGraphicsExtractor, layout: Layout, mouseX: Int, mouseY: Int, t: Float) {
        val pushed = MenuStyle.beginOpen(graphics, openTime, layout.centerX, layout.centerY)
        AlpakaStyle.panel(graphics, layout.panelX, layout.panelY, layout.panelW, layout.panelH)

        val hovered = isOverHeader(layout, mouseX.toDouble(), mouseY.toDouble())
        headerHover += ((if (hovered) 1.0f else 0.0f) - headerHover) * HOVER_EASE
        MenuStyle.header(graphics, this.font, layout.panelX, layout.panelY, layout.panelW, headerHover, t)

        val footerAppear = appear(ROWS + 1)
        if (footerAppear > 0f) {
            ModernGuiUtils.drawRect(graphics, layout.innerX, layout.separatorY, layout.innerW, 1,
                fade(AlpakaStyle.LINE, footerAppear))
            graphics.text(this.font, GuiFont.text("Minecraft ${net.alpaka.addons.utils.ModVersion.minecraft()}"),
                layout.innerX + 2, layout.footerY + (AlpakaStyle.BUTTON_H - 8) / 2,
                fade(AlpakaStyle.TEXT_MUTED, footerAppear), false)
        }
        MenuStyle.endOpen(graphics, pushed)
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
        HypixelStatus.State.OFFLINE -> AlpakaStyle.TEXT_MUTED to "Can't reach Hypixel"
        HypixelStatus.State.CONNECTING -> AlpakaStyle.TEXT_MUTED to "Connecting…"
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0 && isOverHeader(Layout(), event.x(), event.y())) {
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
     * The shared base: the click action, hover easing - the mouse, or the keyboard's focus - and
     * the entrance, each button fading in from the left at its own moment of the stagger.
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
            val appear = appear(appearIndex)
            if (appear <= 0.01f) return

            val mouseOver = mouseX >= this.x && mouseX < this.x + this.width &&
                            mouseY >= this.y && mouseY < this.y + this.height && this.active
            val hovered = MenuStyle.highlighted(mouseOver, this.isFocused)
            hover += ((if (hovered) 1.0f else 0.0f) - hover) * HOVER_EASE

            graphics.pose().pushMatrix()
            graphics.pose().translate((1f - appear) * -SLIDE_IN, 0f)
            draw(graphics, appear)
            graphics.pose().popMatrix()
        }

        /** Draws the button at its own position; the pose already carries the entrance. */
        protected abstract fun draw(graphics: GuiGraphicsExtractor, appear: Float)

        override fun updateWidgetNarration(narration: NarrationElementOutput) {
            this.defaultButtonNarrationText(narration)
        }
    }

    /**
     * Join Hypixel: the primary card, washed in the accent, with Hypixel's player count and ping
     * under its name beside a live dot that sends out a ring like a pulse.
     */
    private inner class HeroButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(0, x, y, width, height, GuiFont.text("Join Hypixel"), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            val textX = MenuStyle.heroCard(graphics, mc.font, this.x, this.y, this.width, this.height,
                ICON_SERVER, "Join Hypixel", hover, appear)

            // The online line under the name, without the ping when it would not fit.
            val room = this.x + this.width - 18 - textX
            val status = onlineLine(withPing = true)?.let { line ->
                if (GuiFont.width(mc.font, line.second) + 9 <= room) line else onlineLine(withPing = false)
            } ?: return
            val (dot, line) = status
            if (GuiFont.width(mc.font, line) + 9 > room) return
            MenuStyle.statusLine(graphics, mc.font, textX, this.y + this.height / 2 + 1, dot, line,
                pulse = dot == ONLINE_DOT, appear = appear)
        }
    }

    /** A row of the menu, drawn as the config lists its features: the icon, the name, a chevron. */
    private inner class RowButton(
        appearIndex: Int,
        x: Int, y: Int, width: Int, height: Int,
        private val glyph: String,
        private val label: String,
        action: () -> Unit,
    ) : MenuButton(appearIndex, x, y, width, height, GuiFont.text(label), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            MenuStyle.card(graphics, mc.font, this.x, this.y, this.width, this.height, glyph, label, hover, appear)
        }
    }

    /** Quit, at the foot of the window: the config's dangerous button, with the power icon. */
    private inner class QuitButton(x: Int, y: Int, width: Int, height: Int, action: () -> Unit) :
        MenuButton(ROWS + 1, x, y, width, height, GuiFont.text("Quit"), action) {

        override fun draw(graphics: GuiGraphicsExtractor, appear: Float) {
            val mc = this@CustomMainMenuScreen.minecraft ?: return
            MenuStyle.button(graphics, mc.font, this.x, this.y, this.width, this.height, ICON_POWER, "Quit",
                MenuStyle.Kind.DANGER, hover, appear)
        }
    }
}
