package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.client.gui.PloppAnimation
import net.alpaka.addons.features.wheel.WheelMesh
import net.alpaka.addons.utils.ModVersion
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import kotlin.math.PI
import kotlin.math.sin

/**
 * The pieces the main menu and the pause menu are built from, in the config menu's design language
 * ([AlpakaStyle]) so the two read as windows of the same mod: a dark panel that pops open, a header
 * with the mod's name and version over a hairline, cards and buttons that go one step lighter on
 * hover, the accent for what is about to happen and red for leaving - and the menus' line icons.
 *
 * Each piece draws at the position it is given and at an [appear] of 0 → 1, which fades it in; the
 * screens decide where things go and when they arrive.
 */
object MenuStyle {
    @JvmField val MOD_ICON_ID: Identifier = Identifier.parse("alpaka:textures/gui/alpaka_icon.png")

    /**
     * The menus' line icons, a font of their own drawn by tools/GenMenuIcons.java: glyphs, so they
     * sit on the text's baseline and take its color. Written as escapes: the private-use characters
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
     * The logo in the header, and how much it grows and wiggles under the mouse. The picture fills
     * only about the middle of its square (29 to 99 of 128 across), so the square is drawn larger
     * than the header's text and pulled left by its empty margin, to line up with the padding.
     */
    const val LOGO_SIZE = 32
    private const val LOGO_INSET = LOGO_SIZE * 29 / 128
    private const val LOGO_VISIBLE_RIGHT = LOGO_SIZE * 99 / 128
    private const val LOGO_HOVER_GROWTH = 0.12f
    private const val LOGO_WIGGLE_DEGREES = 6f

    /** The live dot beside Hypixel's player count. */
    const val ONLINE_DOT = 0xFF4ADE80.toInt()

    /** How far a card's chevron moves along under the mouse. */
    private const val CHEVRON_SLIDE = 2f

    const val HOVER_EASE = 0.25f

    /** The version as the config menu's header shows it, without the build part. */
    @JvmStatic
    fun versionLabel(): String = "v" + ModVersion.mod().substringBefore('+')

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

    /** The color with its alpha scaled by the factor. */
    @JvmStatic
    fun fade(color: Int, factor: Float): Int {
        val alpha = Math.round(((color ushr 24) and 0xFF) * factor.coerceIn(0f, 1f))
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    @JvmStatic
    fun accent(): Int = AlpakaStyle.accent() or 0xFF000000.toInt()

    /** The color [a] blended towards [b] by [t], alpha included. */
    @JvmStatic
    fun mix(a: Int, b: Int, t: Float): Int = ModernGuiUtils.lerpColor(a, b, t.coerceIn(0f, 1f))

    @JvmStatic
    fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }

    @JvmStatic
    fun guiScale(): Float = Minecraft.getInstance().window.guiScale.toFloat()

    /**
     * Whether a widget shows as hovered: under the mouse, or focused while the player steps through
     * the menu with Tab or the arrow keys - otherwise nothing would show which one Enter presses.
     */
    @JvmStatic
    fun highlighted(mouseOver: Boolean, focused: Boolean): Boolean =
        mouseOver || (focused && Minecraft.getInstance().lastInputType.isKeyboard)

    // The status pulse draws with one mesh kept between frames instead of building a new one every
    // time. The feather is about one screen pixel, so a new GUI scale needs a new mesh.
    private var sharedMesh: WheelMesh? = null
    private var sharedMeshScale = 0

    private fun mesh(): WheelMesh {
        val scale = Minecraft.getInstance().window.guiScale
        var mesh = sharedMesh
        if (mesh == null || scale != sharedMeshScale) {
            mesh = WheelMesh(1.15f / scale.toFloat())
            sharedMesh = mesh
            sharedMeshScale = scale
        }
        mesh.reset()
        return mesh
    }

    // ---- The window ----------------------------------------------------------------------------

    /**
     * Starts the config menu's opening pop around the point ([cx], [cy]), the window's own centre.
     * Returns whether a pose was pushed, which [endOpen] needs to know.
     */
    @JvmStatic
    fun beginOpen(graphics: GuiGraphicsExtractor, openTimeMs: Long, cx: Float, cy: Float): Boolean {
        val scale = PloppAnimation.getOpenScale(openTimeMs)
        if (scale >= 0.999f) return false
        graphics.pose().pushMatrix()
        graphics.pose().scaleAround(scale, scale, cx, cy)
        return true
    }

    @JvmStatic
    fun endOpen(graphics: GuiGraphicsExtractor, pushed: Boolean) {
        if (pushed) graphics.pose().popMatrix()
    }

    /**
     * The window's head, as the config menu has it: the logo, then "ALPAKA" large with the version
     * small beneath - and the hairline along the header's foot. The logo and name are the way into
     * the config; under the mouse ([hover]) the logo grows and wiggles, the name takes the accent
     * and the version line turns into "Open Config". [t] is the clock the wiggle runs on.
     */
    @JvmStatic
    fun header(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, hover: Float, t: Float) {
        val logoX = x + AlpakaStyle.PAD - LOGO_INSET
        val logoY = y + (AlpakaStyle.HEADER_H - LOGO_SIZE) / 2
        logo(graphics, logoX, logoY, LOGO_SIZE, hover, t)

        val textX = logoX + LOGO_VISIBLE_RIGHT + 7
        val title = GuiFont.title("ALPAKA")
        val titleColor = mix(AlpakaStyle.TEXT, accent(), hover)
        // Where the config menu's header has them.
        graphics.text(font, title ?: Component.literal("ALPAKA"), textX, y + 8, titleColor, false)

        // The version at rest, "Open Config" under the mouse: crossfaded, on the same line.
        val subY = y + 22
        if (hover < 0.98f) {
            graphics.text(font, GuiFont.text(versionLabel()), textX, subY, fade(AlpakaStyle.TEXT_MUTED, 1f - hover), false)
        }
        if (hover > 0.02f) {
            graphics.text(font, GuiFont.text("Open Config"), textX, subY, fade(accent(), hover), false)
        }
        AlpakaStyle.headerLine(graphics, x, y, width)
    }

    /**
     * The clickable part of the header - the logo and the name - as x, y, width and height, for the
     * window whose top-left corner is ([x], [y]).
     */
    @JvmStatic
    fun brandBox(font: Font, x: Int, y: Int): IntArray {
        val nameW = maxOf(
            GuiFont.title("ALPAKA")?.let { font.width(it) } ?: GuiFont.width(font, "ALPAKA"),
            GuiFont.width(font, "Open Config"), GuiFont.width(font, versionLabel()))
        val left = x + AlpakaStyle.PAD - 2
        return intArrayOf(left, y + 3, LOGO_VISIBLE_RIGHT - LOGO_INSET + 7 + nameW + 4, AlpakaStyle.HEADER_H - 6)
    }

    /** The logo, centred in its box; [hover] grows it and gives it a little wiggle. */
    @JvmStatic
    fun logo(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, hover: Float, t: Float) {
        val grown = size * (1f + LOGO_HOVER_GROWTH * hover)
        val wiggle = sin(t * 11f) * LOGO_WIGGLE_DEGREES * hover

        ensureModIconRegistered()
        graphics.pose().pushMatrix()
        graphics.pose().translate(x + size / 2f, y + size / 2f)
        graphics.pose().rotate((wiggle * PI / 180.0).toFloat())
        val half = Math.round(grown / 2f)
        graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICON_ID, -half, -half, 0.0f, 0.0f,
            half * 2, half * 2, 128, 128, 128, 128, -1)
        graphics.pose().popMatrix()
    }

    // ---- Controls ------------------------------------------------------------------------------

    /** What a control is: an ordinary one, the one that goes ahead (accent), or the one that leaves (red). */
    enum class Kind { PLAIN, PRIMARY, DANGER }

    /** The fill of a control of [kind] at [hover]; [card] controls sit a step lighter at rest, like the config's features. */
    private fun fill(kind: Kind, hover: Float): Int = when (kind) {
        Kind.PRIMARY -> mix((accent() and 0x00FFFFFF) or 0x2E000000, (accent() and 0x00FFFFFF) or 0x52000000, hover)
        Kind.DANGER -> mix(AlpakaStyle.ROW, (AlpakaStyle.DANGER and 0x00FFFFFF) or 0x40000000, hover)
        Kind.PLAIN -> mix(AlpakaStyle.ROW, AlpakaStyle.ROW_SELECTED, hover)
    }

    /** The label color of a control of [kind] at [hover]. */
    private fun label(kind: Kind, hover: Float): Int = when (kind) {
        Kind.PRIMARY -> AlpakaStyle.TEXT
        Kind.DANGER -> mix(AlpakaStyle.DANGER_SOFT, AlpakaStyle.DANGER, hover)
        Kind.PLAIN -> mix(AlpakaStyle.TEXT_SOFT, AlpakaStyle.TEXT, hover)
    }

    /** The icon color of a control of [kind] at [hover]: muted at rest, the accent once hovered. */
    private fun glyph(kind: Kind, hover: Float): Int = when (kind) {
        Kind.PRIMARY -> accent()
        Kind.DANGER -> mix(AlpakaStyle.DANGER_SOFT, AlpakaStyle.DANGER, hover)
        Kind.PLAIN -> mix(AlpakaStyle.TEXT_MUTED, accent(), hover)
    }

    /**
     * A button, as the config's: a slim rounded box, its icon and label centred together. Plain ones
     * go a step lighter on hover with the icon taking the accent; the primary one is washed in the
     * accent, the dangerous one turns red.
     */
    @JvmStatic
    fun button(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int,
               glyph: String?, text: String, kind: Kind, hover: Float, appear: Float) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, AlpakaStyle.ROW_RADIUS, fade(fill(kind, hover), appear))
        if (kind == Kind.PRIMARY) {
            ModernGuiUtils.drawRoundedOutline(graphics, x, y, width, height, AlpakaStyle.ROW_RADIUS,
                fade(accent(), (0.35f + 0.45f * hover) * appear))
        }
        val label = GuiFont.text(text)
        val labelW = font.width(label)
        val textY = y + (height - 8) / 2
        if (glyph == null) {
            graphics.text(font, label, x + (width - labelW) / 2, textY, fade(label(kind, hover), appear), false)
            return
        }
        val iconC = icon(glyph)
        val iconW = font.width(iconC)
        val left = x + (width - iconW - 6 - labelW) / 2
        graphics.text(font, iconC, left, textY, fade(glyph(kind, hover), appear), false)
        graphics.text(font, label, left + iconW + 6, textY, fade(label(kind, hover), appear), false)
    }

    /**
     * A card of a menu, as the config lists its features: the icon, the name, and a chevron at the
     * end. On hover it goes a step lighter, the icon takes the accent and the chevron moves along.
     */
    @JvmStatic
    fun card(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int,
             glyph: String, text: String, hover: Float, appear: Float) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, AlpakaStyle.CARD_RADIUS,
            fade(mix(AlpakaStyle.ROW, AlpakaStyle.ROW_SELECTED, hover), appear))
        val textY = y + (height - 8) / 2
        val iconC = icon(glyph)
        val iconW = font.width(iconC)
        graphics.text(font, iconC, x + 8 + (9 - iconW) / 2, textY, fade(glyph(Kind.PLAIN, hover), appear), false)
        graphics.text(font, GuiFont.text(text), x + 24, textY, fade(label(Kind.PLAIN, hover), appear), false)
        chevron(graphics, x + width - 9, y + height / 2, hover, appear, mix(AlpakaStyle.TEXT_MUTED, accent(), hover))
    }

    /** The config's side chevron, moving along under the mouse. */
    private fun chevron(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, hover: Float, appear: Float, color: Int) {
        if (appear <= 0.01f) return
        graphics.pose().pushMatrix()
        graphics.pose().translate(CHEVRON_SLIDE * hover - CHEVRON_SLIDE, 0f)
        AlpakaStyle.sideChevron(graphics, cx, cy, false, fade(color, appear))
        graphics.pose().popMatrix()
    }

    /**
     * The card that stands out: the primary action, washed in the accent with an accent edge, its
     * icon in the accent, the name and a status line under it. Returns where the name starts, for
     * that line.
     */
    @JvmStatic
    fun heroCard(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, width: Int, height: Int,
                 glyph: String, text: String, hover: Float, appear: Float): Int {
        val r = AlpakaStyle.CARD_RADIUS
        ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, r, fade(fill(Kind.PRIMARY, hover), appear))
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, width, height, r,
            fade(accent(), (0.35f + 0.45f * hover) * appear))
        val iconC = icon(glyph)
        val iconW = font.width(iconC)
        graphics.text(font, iconC, x + 8 + (9 - iconW) / 2, y + (height - 8) / 2, fade(accent(), appear), false)
        val textX = x + 24
        graphics.text(font, GuiFont.text(text), textX, y + height / 2 - 9, fade(AlpakaStyle.TEXT, appear), false)
        chevron(graphics, x + width - 9, y + height / 2, hover, appear, mix(accent(), AlpakaStyle.TEXT, hover))
        return textX
    }

    /**
     * A status line: a dot, and the text muted. A [pulse] dot sends out a ring once a second and a
     * half, like a live signal.
     */
    @JvmStatic
    fun statusLine(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, dot: Int, text: String,
                   pulse: Boolean, appear: Float) {
        val dotSize = 4
        val dotX = x
        val dotY = y + 2
        if (pulse) {
            val p = (MenuAmbience.seconds() % 1.5f) / 1.5f
            val mesh = mesh()
            mesh.ring(dotX + dotSize / 2f, dotY + dotSize / 2f, 1.5f + p * 4f, 2.2f + p * 4f,
                WheelMesh.scaleAlpha(dot, (1f - p) * 0.8f * appear))
            mesh.submit(graphics)
        }
        ModernGuiUtils.drawRoundedRect(graphics, dotX, dotY, dotSize, dotSize, dotSize / 2, fade(dot, appear))
        graphics.text(font, GuiFont.text(text), x + dotSize + 5, y, fade(AlpakaStyle.TEXT_MUTED, appear), false)
    }
}
