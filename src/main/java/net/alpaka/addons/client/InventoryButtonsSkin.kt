package net.alpaka.addons.client

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * The pieces the inventory button editor is drawn with, in the config menu's look ([AlpakaStyle]):
 * a dark rounded panel with a slim header, rows that light up on hover, fields with an accent line.
 * Kept apart from the editor so its layout and input don't depend on how a control looks.
 */
object InventoryButtonsSkin {
    const val HEADER_H = AlpakaStyle.HEADER_H
    const val BUTTON_H = AlpakaStyle.BUTTON_H
    const val PAD = AlpakaStyle.PAD + 2

    const val PLAIN = 0
    const val PRIMARY = 1
    const val DANGER = 2

    val TEXT: Int get() = AlpakaStyle.TEXT
    val TEXT_MUTED: Int get() = AlpakaStyle.TEXT_MUTED
    val LINE: Int get() = AlpakaStyle.DIVIDER

    fun accent(): Int = AlpakaStyle.accent()

    fun click() = AlpakaStyle.click()

    fun backdrop(graphics: GuiGraphicsExtractor, width: Int, height: Int) = AlpakaStyle.backdrop(graphics, width, height)

    fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) = AlpakaStyle.panel(graphics, x, y, w, h)

    fun header(graphics: GuiGraphicsExtractor, font: Font, title: String, x: Int, y: Int, w: Int) =
        AlpakaStyle.header(graphics, font, title, x, y, w)

    fun button(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, text: String, hover: Boolean, kind: Int) {
        val label = AlpakaStyle.ellipsize(font, text, w - 6)
        AlpakaStyle.button(graphics, font, x, y, w, h, label, hover,
            when (kind) { PRIMARY -> ButtonKind.PRIMARY; DANGER -> ButtonKind.DANGER; else -> ButtonKind.PLAIN })
    }

    fun textField(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int,
                  text: String, placeholder: String, focused: Boolean, hover: Boolean) {
        AlpakaStyle.textField(graphics, font, x, y, w, h, text, placeholder, focused, hover, 0L)
    }

    /** A row of a list: lighter on hover, lighter still while selected. */
    fun row(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, hover: Boolean, selected: Boolean) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.ROW_RADIUS,
            if (selected) AlpakaStyle.ROW_SELECTED else if (hover) AlpakaStyle.ROW_HOVER else AlpakaStyle.ROW)
    }

    /** The well the icon grid sits in. */
    fun well(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, AlpakaStyle.FIELD)
    }

    fun label(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int = TEXT_MUTED) {
        graphics.text(font, GuiFont.text(text), x, y, color, false)
    }

    fun outline(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, 2, color)
    }

    fun ellipsize(font: Font, text: String, maxW: Int): String = AlpakaStyle.ellipsize(font, text, maxW)
}
