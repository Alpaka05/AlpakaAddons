package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.GradientRoundedRectRenderState
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.features.wheel.WheelMesh
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import kotlin.math.PI
import kotlin.math.sin

/**
 * The look the main menu and the pause menu share: a card of dark glass with the logo and the mod's
 * name at its head, one accented hero tile, rows with an icon tile and a chevron, and a red pill to
 * leave by - together with the colours, easings and line icons they are drawn with.
 *
 * Each piece draws at the position it is given and at an [appear] of 0 → 1; the screens decide
 * where things go and when they arrive.
 */
object MenuStyle {
    @JvmField val MOD_ICON_ID: Identifier = Identifier.parse("alpaka:textures/gui/alpaka_icon.png")

    /**
     * The menus' line icons, a font of their own drawn by tools/GenMenuIcons.java: glyphs, so they
     * sit on the text's baseline and take its colour. Written as escapes: the private-use characters
     * themselves are invisible in most editors.
     */
    private val ICON_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", "menu_icons"))
    const val ICON_SERVER = ""
    const val ICON_PERSON = ""
    const val ICON_PEOPLE = ""
    const val ICON_FLASK = ""
    const val ICON_PUZZLE = ""
    const val ICON_SLIDERS = ""
    const val ICON_POWER = ""
    const val ICON_CHEVRON = ""
    const val ICON_PLAY = ""
    const val ICON_BOOK = ""
    const val ICON_DOOR = ""

    /**
     * The titles - the mod's name and the player's - in the larger title font. Its glyphs hang from
     * a baseline further down than the normal text's, so they are drawn this far below their box's
     * top for the capitals to start there.
     */
    const val TITLE_HEIGHT = 13
    const val TITLE_BASELINE_DROP = 4

    const val LOGO_HOVER_GROWTH = 0.1f
    private const val LOGO_BOB_PIXELS = 1.2f
    private const val LOGO_WIGGLE_DEGREES = 7f

    /** The card's glass: its tint over the blurred backdrop and how strongly that covers the blur. */
    const val CARD_TINT = 0xFF0D1117.toInt()
    const val CARD_STRENGTH = 0.72f
    const val CARD_EDGE = 0x1AFFFFFF
    const val CARD_SHADOW = 0.9f

    /** The hero tile: a lighter panel washed with the accent from the left, in a circling gradient edge. */
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

    const val SEPARATOR = 0x1AFFFFFF
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_SOFT = 0xEBFFFFFF.toInt()
    const val MUTED = 0x9EFFFFFF.toInt()
    const val ONLINE_DOT = 0xFF4ADE80.toInt()

    /** The red pill to leave by, at a card's foot. */
    private const val QUIT_FILL = 0x29EF4444
    private const val QUIT_FILL_HOVER = 0x4DEF4444
    private const val QUIT_TEXT = 0xFFFCA5A5.toInt()
    private const val QUIT_RING = 0xFFEF4444.toInt()
    /** Room left and right of a pill's label. */
    const val PILL_PADDING = 10

    const val HOVER_EASE = 0.25f

    private var modIconRegistered = false

    @JvmStatic
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

    @JvmStatic
    fun icon(icon: String): Component =
        Component.literal(icon).withStyle { it.withFont(ICON_FONT) }

    /**
     * A label: the icon, then the text. Built on an empty root so the text inherits the default font
     * rather than the icon's, in which every ordinary letter is a missing glyph.
     */
    @JvmStatic
    fun iconLabel(glyph: String, text: String): Component =
        Component.empty().append(icon(glyph)).append(GuiFont.text("  $text"))

    /** The colour with its alpha scaled by the factor. */
    @JvmStatic
    fun fade(color: Int, factor: Float): Int {
        val alpha = Math.round(((color ushr 24) and 0xFF) * factor.coerceIn(0f, 1f))
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    @JvmStatic
    fun accent(): Int = ModernGuiUtils.getAccentColor() or 0xFF000000.toInt()

    /** Eased out with a little overshoot, for things that spring into place. */
    @JvmStatic
    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    @JvmStatic
    fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }

    @JvmStatic
    fun guiScale(): Float = Minecraft.getInstance().window.guiScale.toFloat()

    /**
     * The card's frame over whatever glass is behind it: the soft shadow, and the hairline edge. The
     * glass itself differs - the main menu blurs its picture, the pause menu sits on vanilla's blur.
     */
    @JvmStatic
    fun cardShadow(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, appear: Float) {
        ModernGuiUtils.drawPanelShadow(graphics, x, y, width, height, radius, CARD_SHADOW * appear)
    }

    @JvmStatic
    fun cardEdge(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, appear: Float) {
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, width, height, radius, fade(CARD_EDGE, appear))
    }

    /** The card's tint as one colour, for glass that is a plain fill over an already blurred backdrop. */
    @JvmStatic
    fun cardTint(): Int = (Math.round(CARD_STRENGTH * 255f) shl 24) or (CARD_TINT and 0x00FFFFFF)

    /**
     * The logo, centred in its box, floating gently; [hover] grows it and gives it a little wiggle.
     * [t] is the ambient clock the float runs on.
     */
    @JvmStatic
    fun logo(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, hover: Float, appear: Float, t: Float) {
        val grown = size * (1f + LOGO_HOVER_GROWTH * hover)
        val bob = sin(t * 1.6f) * LOGO_BOB_PIXELS
        val wiggle = sin(t * 11f) * LOGO_WIGGLE_DEGREES * hover

        ensureModIconRegistered()
        graphics.pose().pushMatrix()
        graphics.pose().translate(x + size / 2f, y + size / 2f + bob)
        graphics.pose().rotate((wiggle * PI / 180.0).toFloat())
        val half = Math.round(grown / 2f)
        graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICON_ID, -half, -half, 0.0f, 0.0f,
            half * 2, half * 2, 128, 128, 128, 128, fade(0xFFFFFFFF.toInt(), appear))
        graphics.pose().popMatrix()
    }

    /** A title in the title font, centred on the height of the box from [top] that is [height] tall. */
    @JvmStatic
    fun title(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, top: Int, height: Int, appear: Float) {
        val title = GuiFont.title(text)
        if (title != null) {
            graphics.text(font, title, x, top + (height - TITLE_HEIGHT) / 2 + TITLE_BASELINE_DROP, fade(TEXT, appear), false)
        } else {
            graphics.text(font, Component.literal(text), x, top + (height - 8) / 2, fade(TEXT, appear), false)
        }
    }

    /**
     * The hero tile's panel: a lighter fill washed with the accent from the left, inside an edge
     * whose gradient slowly circles it, with a glint crossing it every few seconds; then its icon in
     * the accent and its name. Returns where the name starts, for a line under it.
     */
    @JvmStatic
    fun hero(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int,
             glyph: String, name: String, nameY: Int, hover: Float, appear: Float): Int {
        heroPanel(graphics, x, y, width, height, height / 4, hover, appear)

        val iconComponent = icon(glyph)
        val iconX = x + 10
        graphics.text(font, iconComponent, iconX, y + (height - 8) / 2, fade(accent(), appear), false)

        val textX = iconX + font.width(iconComponent) + 8
        graphics.text(font, GuiFont.text(name), textX, nameY, fade(TEXT, appear), false)
        return textX
    }

    /**
     * The hero tile without its content: the washed panel with corners of [radius], the circling
     * edge and the glint.
     */
    @JvmStatic
    fun heroPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int,
                  hover: Float, appear: Float) {
        val t = MenuAmbience.seconds()
        val accent = accent()
        val light = WheelMesh.lerpColor(accent, 0xFFFFFFFF.toInt(), 0.6f)

        ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, radius,
            fade(ModernGuiUtils.lerpColor(HERO_FILL, HERO_FILL_HOVER, hover), appear))
        val wash = fade(accent, HERO_WASH * appear * (1f + hover))
        val clearWash = accent and 0x00FFFFFF
        gradientRect(graphics, x, y, width, height, radius, 0, wash, clearWash, clearWash, wash)

        // The edge: each corner's colour runs between the accent and its lighter self, a quarter
        // turn apart, so the bright part travels around the tile.
        val edgeStrength = appear * (HERO_EDGE_REST + (1f - HERO_EDGE_REST) * hover)
        fun corner(k: Int): Int {
            val phase = (t / HERO_EDGE_TURN_SECONDS + k * 0.25f) * 2f * PI.toFloat()
            return fade(WheelMesh.lerpColor(accent, light, 0.5f + 0.5f * sin(phase)), edgeStrength)
        }
        gradientRect(graphics, x, y, width, height, radius,
            maxOf(1, Math.round(guiScale())), corner(0), corner(1), corner(2), corner(3))

        val glintPhase = (t % HERO_GLINT_EVERY) / HERO_GLINT_SECONDS
        if (glintPhase in 0f..1f) glint(graphics, x, y, width, height, glintPhase, 0.12f * appear)
    }

    /**
     * A status line under the hero's name: a dot, and the text in the muted colour. A [pulse] dot
     * sends out a ring once a second and a half, like a live signal.
     */
    @JvmStatic
    fun statusLine(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, dot: Int, text: String,
                   pulse: Boolean, appear: Float) {
        if (pulse) {
            val p = (MenuAmbience.seconds() % 1.5f) / 1.5f
            val mesh = WheelMesh(1.15f / guiScale())
            val dotCenterX = x + font.width(GuiFont.text("●")) / 2f
            val dotCenterY = y + 3.5f
            mesh.ring(dotCenterX, dotCenterY, 1.5f + p * 4f, 2.2f + p * 4f,
                WheelMesh.scaleAlpha(dot, (1f - p) * 0.8f * appear))
            mesh.submit(graphics)
        }
        graphics.text(font, GuiFont.text("●"), x, y, fade(dot, appear), false)
        graphics.text(font, GuiFont.text(text), x + GuiFont.width(font, "● "), y, fade(MUTED, appear), false)
    }

    /**
     * A row of a menu: the icon on a small tile, the name, and a chevron at the end. Bare at rest;
     * under the mouse a light band lies behind it, the row slides out a little, the tile pops in the
     * accent and the chevron moves along.
     */
    @JvmStatic
    fun row(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int,
            glyph: String, label: Component, hover: Float, appear: Float) {
        if (hover > 0.02f) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, 6, fade(ROW_HOVER, appear * hover))
        }

        val slide = ROW_SLIDE * hover
        val tile = height - 4
        iconTile(graphics, font, x + 4 + slide, y + 2f, tile, glyph, hover, appear)

        val textY = y + (height - 8) / 2
        graphics.pose().pushMatrix()
        graphics.pose().translate(slide, 0f)
        graphics.text(font, label, x + 4 + tile + 8, textY,
            fade(ModernGuiUtils.lerpColor(TEXT_SOFT, TEXT, hover), appear), false)
        graphics.pose().popMatrix()

        val chevron = icon(ICON_CHEVRON)
        graphics.pose().pushMatrix()
        graphics.pose().translate(CHEVRON_SLIDE * hover, 0f)
        graphics.text(font, chevron, x + width - 6 - font.width(chevron) - CHEVRON_SLIDE.toInt(), textY,
            fade(ModernGuiUtils.lerpColor(CHEVRON, CHEVRON_HOVER, hover), appear), false)
        graphics.pose().popMatrix()
    }

    /**
     * An icon on a small square tile [size] wide with its corner at [x], [y]; under the mouse the
     * tile pops and takes the accent.
     */
    @JvmStatic
    fun iconTile(graphics: GuiGraphicsExtractor, font: Font, x: Float, y: Float, size: Int, glyph: String,
                 hover: Float, appear: Float) {
        val tileColor = ModernGuiUtils.lerpColor(TILE, fade(accent(), TILE_HOVER_ACCENT), hover)
        graphics.pose().pushMatrix()
        graphics.pose().translate(x + size / 2f, y + size / 2f)
        val pop = 1f + TILE_POP * hover
        graphics.pose().scale(pop, pop)
        ModernGuiUtils.drawRoundedRect(graphics, -size / 2, -size / 2, size, size, size / 3, fade(tileColor, appear))
        val glyphComponent = icon(glyph)
        graphics.text(font, glyphComponent, -font.width(glyphComponent) / 2 + 1, -4, fade(TEXT, appear), false)
        graphics.pose().popMatrix()
    }

    /** The red pill to leave by, ringed in red under the mouse. */
    @JvmStatic
    fun quitPill(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int,
                 label: Component, hover: Float, appear: Float) {
        pill(graphics, font, x, y, width, height, label, hover, appear, QUIT_FILL, QUIT_FILL_HOVER, QUIT_RING, QUIT_TEXT)
    }

    /** The red pill's look on a box with corners of [radius] rather than round ends. */
    @JvmStatic
    fun quitBox(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, radius: Int,
                label: Component, hover: Float, appear: Float) {
        box(graphics, font, x, y, width, height, radius, label, hover, appear, QUIT_FILL, QUIT_FILL_HOVER, QUIT_RING, QUIT_TEXT)
    }

    private fun pill(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int,
                     label: Component, hover: Float, appear: Float, fill: Int, fillHover: Int, ring: Int, text: Int) {
        box(graphics, font, x, y, width, height, height / 2, label, hover, appear, fill, fillHover, ring, text)
    }

    private fun box(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int, radius: Int,
                    label: Component, hover: Float, appear: Float, fill: Int, fillHover: Int, ring: Int, text: Int) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, radius,
            fade(ModernGuiUtils.lerpColor(fill, fillHover, hover), appear))
        if (hover > 0.02f) {
            ModernGuiUtils.drawRoundedOutline(graphics, x, y, width, height, radius, fade(ring, appear * hover))
        }
        ModernGuiUtils.centeredText(graphics, font, label, x + width / 2, y + (height - 8) / 2, fade(text, appear))
    }

    /**
     * A rounded rectangle with a colour at each corner, filled when [thickness] is 0 or as an edge
     * that many screen pixels wide; nothing without the extractor mixin.
     */
    @JvmStatic
    fun gradientRect(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int,
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
    @JvmStatic
    fun glint(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int,
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
}
