package net.alpaka.addons.client

import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.features.sound.CustomSoundFeature
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * The pieces the inventory button editor is drawn with, in the config screen's look: rounded
 * panels with hairline borders, a header band with the accent rule, the menu font without shadows.
 * Kept apart from the editor so its layout and input don't depend on how a control looks.
 */
object InventoryButtonsSkin {
    const val HEADER_H = 38
    const val BUTTON_H = 18
    const val PAD = 10

    const val PLAIN = 0
    const val PRIMARY = 1
    const val DANGER = 2

    val TEXT: Int get() = ModernGuiUtils.COLOR_TEXT_PRIMARY
    val TEXT_MUTED: Int get() = ModernGuiUtils.COLOR_TEXT_MUTED
    val LINE: Int get() = ModernGuiUtils.COLOR_CARD_BORDER

    fun accent(): Int = ModernGuiUtils.getAccentColor()

    fun click() {
        try {
            CustomSoundFeature.playButtonClickSound()
        } catch (_: Throwable) {}
    }

    fun backdrop(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
        graphics.pose().pushMatrix()
        graphics.pose().identity()
        graphics.fill(0, 0, width, height, 0x70000000)
        graphics.pose().popMatrix()
    }

    fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        ModernGuiUtils.drawPanelShadow(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS, 1.0f)
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS,
            ModernGuiUtils.COLOR_PANEL_BG, ModernGuiUtils.COLOR_CARD_BORDER)
    }

    fun header(graphics: GuiGraphicsExtractor, font: Font, title: String, x: Int, y: Int, w: Int) {
        val r = ModernGuiUtils.PANEL_RADIUS
        ModernGuiUtils.drawRoundedRect(graphics, x + 1, y + 1, w - 2, HEADER_H - 1, r - 1, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawRect(graphics, x + 1, y + HEADER_H - r, w - 2, r, ModernGuiUtils.COLOR_SIDEBAR_BG)
        ModernGuiUtils.drawRect(graphics, x + 1, y + HEADER_H - 1, w - 2, 1, accent())
        graphics.text(font, GuiFont.text(title), x + PAD, y + (HEADER_H - 8) / 2, TEXT, false)
    }

    fun button(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, text: String, hover: Boolean, kind: Int) {
        val label = ellipsize(font, text, w - 6)
        if (kind == DANGER) ModernGuiUtils.drawModernDestructiveButton(graphics, font, x, y, w, h, label, hover)
        else ModernGuiUtils.drawModernButton(graphics, font, x, y, w, h, label, hover, kind == PRIMARY)
    }

    fun textField(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int,
                  text: String, placeholder: String, focused: Boolean, hover: Boolean) {
        ModernGuiUtils.drawModernTextField(graphics, font, x, y, w, h, text, placeholder, focused, hover)
    }

    /** A row of a list: lighter on hover, outlined in the accent while selected. */
    fun row(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, hover: Boolean, selected: Boolean) {
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, h, ModernGuiUtils.WIDGET_RADIUS + 1,
            if (hover || selected) ModernGuiUtils.COLOR_CARD_BG_HOVER else ModernGuiUtils.COLOR_CARD_BG,
            if (selected) accent() else if (hover) ModernGuiUtils.getAccentDimColor() else ModernGuiUtils.COLOR_CARD_BORDER)
    }

    /** The well the icon grid sits in. */
    fun well(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, h, ModernGuiUtils.WIDGET_RADIUS + 1,
            ModernGuiUtils.COLOR_SIDEBAR_BG, ModernGuiUtils.COLOR_CARD_BORDER)
    }

    fun label(graphics: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int = TEXT_MUTED) {
        graphics.text(font, GuiFont.text(text), x, y, color, false)
    }

    fun outline(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, 2, color)
    }

    fun ellipsize(font: Font, text: String, maxW: Int): String {
        if (GuiFont.width(font, text) <= maxW) return text
        var cut = text
        while (cut.isNotEmpty() && GuiFont.width(font, "$cut...") > maxW) cut = cut.dropLast(1)
        return "$cut..."
    }
}
