package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.features.mainmenu.MenuStyle.fade
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

/**
 * The main menu's window with a release's notes, opened from its What's New card: over a dim, a
 * panel of the pause menu's prompt glass with the version and date at its head, the sections with
 * their items as a bulleted list that scrolls when it is longer than the panel, and at its foot
 * the way to the release page. A cross in the corner, Escape or a click beside it closes it.
 *
 * Only draws and answers; the menu decides when it is open and what a click on the release page
 * button does.
 */
internal class ReleaseNotesPanel(private val notes: ReleaseNotes.Notes) {

    private companion object {
        const val MAX_WIDTH = 340
        const val MAX_HEIGHT = 300
        const val SCREEN_MARGIN = 16
        const val PAD = 16
        const val RADIUS = 12
        const val HEAD = 44
        const val FOOT = 40
        const val LINE = 10
        const val SECTION_GAP = 7
        const val BULLET_INDENT = 9
        const val SCROLL_STEP = 14f
        const val FILL = 0xF00D1117.toInt()
        const val DIM = 0x66000000
        const val RISE = 8f
        const val BUTTON_H = 20
    }

    /** One drawn line of the list: a section's title, or a piece of an item, the first one with its bullet. */
    private class Line(val text: FormattedCharSequence, val heading: Boolean, val bullet: Boolean, val gapBefore: Int)

    private var x = 0
    private var y = 0
    private var width = 0
    private var height = 0
    private var lines: List<Line> = emptyList()
    private var wrappedFor = -1
    private var scroll = 0f
    private var closeHover = 0f
    private var linkHover = 0f

    /** Places the panel in the middle of a screen this size. */
    fun layout(screenWidth: Int, screenHeight: Int) {
        width = minOf(MAX_WIDTH, screenWidth - SCREEN_MARGIN * 2)
        height = minOf(MAX_HEIGHT, screenHeight - SCREEN_MARGIN * 2)
        x = (screenWidth - width) / 2
        y = (screenHeight - height) / 2
    }

    private fun listTop() = y + HEAD
    private fun listBottom() = y + height - FOOT
    private fun closeBox() = intArrayOf(x + width - 12 - 16, y + 12, 16, 16)
    private fun linkBox(font: Font): IntArray {
        val w = font.width(MenuStyle.icon(MenuStyle.ICON_CHEVRON)) + 6 + GuiFont.width(font, "Release Page") + 18
        return intArrayOf(x + width - PAD - w, y + height - FOOT + (FOOT - BUTTON_H) / 2, w, BUTTON_H)
    }

    private fun inside(box: IntArray, mx: Double, my: Double) =
        mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]

    fun isOver(mx: Double, my: Double) = mx >= x && mx < x + width && my >= y && my < y + height
    fun isOverClose(mx: Double, my: Double) = inside(closeBox(), mx, my)
    fun isOverLink(font: Font, mx: Double, my: Double) = inside(linkBox(font), mx, my)

    private fun wrap(font: Font) {
        val textWidth = width - PAD * 2 - BULLET_INDENT
        if (wrappedFor == textWidth) return
        wrappedFor = textWidth
        val language = Language.getInstance()
        val out = mutableListOf<Line>()
        notes.sections.forEachIndexed { index, section ->
            out += Line(GuiFont.text(section.title).visualOrderText, heading = true, bullet = false,
                gapBefore = if (index == 0) 0 else SECTION_GAP)
            for (item in section.items) {
                GuiFont.splitLines(font, item, textWidth).forEachIndexed { i, piece ->
                    out += Line(language.getVisualOrder(piece), heading = false, bullet = i == 0, gapBefore = 0)
                }
            }
        }
        lines = out
    }

    private fun contentHeight() = lines.sumOf { LINE + it.gapBefore }

    private fun maxScroll() = maxOf(0, contentHeight() - (listBottom() - listTop()))

    fun scrollBy(amount: Double) {
        scroll = (scroll - amount.toFloat() * SCROLL_STEP).coerceIn(0f, maxScroll().toFloat())
    }

    /** Draws the dim and the panel, [shown] 0 → 1 as it comes in. */
    fun draw(graphics: GuiGraphicsExtractor, font: Font, screenWidth: Int, screenHeight: Int,
             mouseX: Int, mouseY: Int, shown: Float) {
        if (shown <= 0.01f) return
        wrap(font)
        scroll = scroll.coerceIn(0f, maxScroll().toFloat())

        // The dim against an identity matrix: a full-screen darkening must never move with a
        // transform another GUI mod puts around the screen.
        graphics.pose().pushMatrix()
        graphics.pose().identity()
        graphics.fill(0, 0, screenWidth, screenHeight, fade(DIM, shown))
        graphics.pose().popMatrix()

        graphics.pose().pushMatrix()
        graphics.pose().translate(0f, (1f - shown) * RISE)
        MenuStyle.cardShadow(graphics, x, y, width, height, RADIUS, shown)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, width, height, RADIUS, fade(FILL, shown))
        MenuStyle.cardEdge(graphics, x, y, width, height, RADIUS, shown)

        // The head: the version in the title font, the date under it, the cross in the corner.
        val title = "What's New in ${notes.version}"
        val titleComponent = GuiFont.title(title)
        if (titleComponent != null) {
            graphics.text(font, titleComponent, x + PAD, y + 12 + MenuStyle.TITLE_BASELINE_DROP, fade(MenuStyle.TEXT, shown), false)
        } else {
            graphics.text(font, Component.literal(title), x + PAD, y + 14, fade(MenuStyle.TEXT, shown), false)
        }
        if (notes.date.isNotEmpty()) {
            graphics.text(font, GuiFont.text("Released ${notes.date}"), x + PAD, y + 12 + MenuStyle.TITLE_HEIGHT + 3,
                fade(MenuStyle.MUTED, shown), false)
        }
        val close = closeBox()
        closeHover += ((if (isOverClose(mouseX.toDouble(), mouseY.toDouble())) 1f else 0f) - closeHover) * MenuStyle.HOVER_EASE
        MenuStyle.glassBox(graphics, close[0], close[1], close[2], close[3], 5, closeHover, shown)
        ModernGuiUtils.centeredText(graphics, font, Component.literal("×"), close[0] + close[2] / 2, close[1] + 4,
            fade(ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, MenuStyle.TEXT, closeHover), shown))

        // The list, clipped to its area and scrolled.
        val top = listTop()
        val bottom = listBottom()
        graphics.enableScissor(x + 1, top, x + width - 1, bottom)
        var lineY = top - Math.round(scroll)
        for (line in lines) {
            lineY += line.gapBefore
            if (lineY + LINE > top && lineY < bottom) {
                if (line.heading) {
                    graphics.text(font, line.text, x + PAD, lineY, fade(MenuStyle.accent(), shown), false)
                } else {
                    if (line.bullet) {
                        graphics.text(font, GuiFont.text("•"), x + PAD + 1, lineY, fade(MenuStyle.MUTED, shown), false)
                    }
                    graphics.text(font, line.text, x + PAD + BULLET_INDENT, lineY, fade(MenuStyle.TEXT_SOFT, shown), false)
                }
            }
            lineY += LINE
        }
        graphics.disableScissor()

        // A thin scrollbar when the list is longer than its area.
        val max = maxScroll()
        if (max > 0) {
            val track = bottom - top
            val thumb = maxOf(16, track * track / (track + max))
            val thumbY = top + Math.round((track - thumb) * (scroll / max))
            ModernGuiUtils.drawRoundedRect(graphics, x + width - 7, thumbY, 2, thumb, 1, fade(0x40FFFFFF, shown))
        }

        // The foot: a hairline, the number of changes, and the way to the release page.
        graphics.fill(x + PAD, bottom, x + width - PAD, bottom + 1, fade(MenuStyle.SEPARATOR, shown))
        val link = linkBox(font)
        graphics.text(font, GuiFont.text("${notes.itemCount} changes"), x + PAD, link[1] + (BUTTON_H - 8) / 2,
            fade(MenuStyle.MUTED, shown), false)
        linkHover += ((if (isOverLink(font, mouseX.toDouble(), mouseY.toDouble())) 1f else 0f) - linkHover) * MenuStyle.HOVER_EASE
        MenuStyle.glassBox(graphics, link[0], link[1], link[2], link[3], 6, linkHover, shown)
        val label = GuiFont.text("Release Page")
        val chevron = MenuStyle.icon(MenuStyle.ICON_CHEVRON)
        val textY = link[1] + (BUTTON_H - 8) / 2
        graphics.text(font, label, link[0] + 9, textY,
            fade(ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, MenuStyle.TEXT, linkHover), shown), false)
        graphics.text(font, chevron, link[0] + 9 + font.width(label) + 6 + Math.round(2 * linkHover), textY,
            fade(ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, MenuStyle.accent(), linkHover), shown), false)
        graphics.pose().popMatrix()
    }
}
