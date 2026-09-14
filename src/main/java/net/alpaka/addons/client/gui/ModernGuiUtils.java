package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModernGuiUtils {
    // Theme Colors (Neutral Dark Charcoal/Gray with Gold accent & Green/Red states)
    public static final int COLOR_BG_BACKDROP = 0xEE111111;
    public static final int COLOR_PANEL_BG = 0xFF191919;
    public static final int COLOR_SIDEBAR_BG = 0xFF131313;
    public static final int COLOR_CARD_BG = 0xFF222222;
    public static final int COLOR_CARD_BG_HOVER = 0xFF2D2D2D;
    public static final int COLOR_CARD_BORDER = 0xFF3B3B3B;

    public static int getAccentColor() {
        return net.alpaka.addons.config.AlpakaConfig.instance.menuAccentColor;
    }

    public static int getAccentDimColor() {
        int color = net.alpaka.addons.config.AlpakaConfig.instance.menuAccentColor;
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * 0.65f);
        int g = (int) (((color >> 8) & 0xFF) * 0.65f);
        int b = (int) ((color & 0xFF) * 0.65f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int getAccentBgColor() {
        int color = net.alpaka.addons.config.AlpakaConfig.instance.menuAccentColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (0x33 << 24) | (r << 16) | (g << 8) | b;
    }

    public static final int COLOR_TOGGLE_ON_BG = 0xFF133824;
    public static final int COLOR_TOGGLE_ON_BORDER = 0xFF00E676;
    public static final int COLOR_TOGGLE_ON_TEXT = 0xFF00E676;

    public static final int COLOR_TOGGLE_OFF_BG = 0xFF381418;
    public static final int COLOR_TOGGLE_OFF_BORDER = 0xFFFF5252;
    public static final int COLOR_TOGGLE_OFF_TEXT = 0xFFFF5252;

    public static final int COLOR_TEXT_PRIMARY = 0xFFF0F0F0;
    public static final int COLOR_TEXT_MUTED = 0xFF909090;
    public static final int COLOR_TEXT_DARK = 0xFF555555;

    public static void drawRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    public static void drawOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        // Top
        graphics.fill(x, y, x + width, y + 1, color);
        // Bottom
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        // Left
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        // Right
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawModernCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean isHovered, boolean isSelected) {
        int bg = isHovered ? COLOR_CARD_BG_HOVER : COLOR_CARD_BG;
        int border = isSelected ? getAccentColor() : (isHovered ? getAccentDimColor() : COLOR_CARD_BORDER);

        drawRect(graphics, x, y, width, height, bg);
        drawOutline(graphics, x, y, width, height, border);

        if (isSelected) {
            // Accent indicator bar on left side
            drawRect(graphics, x, y, 3, height, getAccentColor());
        }
    }

    /**
     * Linearly blends two ARGB colours; {@code t} 0 gives {@code from}, 1 gives {@code to}.
     * For controls whose colour follows an animation instead of flipping with the state.
     */
    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * An ON/OFF pill whose knob sits at {@code progress} - 0 left and off, 1 right and on.
     *
     * The caller animates progress toward the real state, so a click slides the knob across and
     * fades the track through the accent colour rather than jumping. Grey knob when off, accent
     * when on, blended in between.
     */
    public static void drawModernToggle(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, float progress, boolean isHovered) {
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        int trackBg = lerpColor(COLOR_CARD_BG, getAccentBgColor(), progress);
        int border = isHovered ? getAccentColor() : lerpColor(COLOR_CARD_BORDER, getAccentDimColor(), progress);

        drawRect(graphics, x, y, width, height, trackBg);
        drawOutline(graphics, x, y, width, height, border);

        int knobSize = height - 4;
        int travel = width - knobSize - 4;
        int knobX = x + 2 + Math.round(travel * progress);
        int knobY = y + 2;

        int knobColor = lerpColor(0xFF64748B, getAccentColor(), progress);
        drawRect(graphics, knobX, knobY, knobSize, knobSize, knobColor);
        drawOutline(graphics, knobX, knobY, knobSize, knobSize, 0x40000000);
    }

    /**
     * A small square tick box, for the individually switchable lines inside a dropdown.
     *
     * Distinct from {@link #drawModernToggle} on purpose: a toggle is a wide ON/OFF pill sized for a
     * feature card, which would dominate a compact list of lines.
     */
    public static void drawModernCheckbox(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, boolean state, boolean isHovered) {
        int background = state ? COLOR_TOGGLE_ON_BG : COLOR_CARD_BG;
        int border = state ? COLOR_TOGGLE_ON_BORDER : (isHovered ? getAccentColor() : COLOR_CARD_BORDER);

        drawRect(graphics, x, y, size, size, background);
        drawOutline(graphics, x, y, size, size, border);

        if (state) {
            // Centred by measuring, so the mark stays put if the box size is ever changed.
            String mark = "✔";
            int markX = x + (size - GuiFont.width(font, mark)) / 2;
            int markY = y + (size - 8) / 2;
            graphics.text(font, GuiFont.text(mark), markX, markY, COLOR_TOGGLE_ON_TEXT);
        }
    }

    public static void drawModernSlider(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, double value, String displayValue, boolean isHovered) {
        int trackBg = COLOR_CARD_BG;
        int border = isHovered ? getAccentColor() : COLOR_CARD_BORDER;

        drawRect(graphics, x, y, width, height, trackBg);
        drawOutline(graphics, x, y, width, height, border);

        // Filled track area
        int fillWidth = Math.max(0, Math.min(width - 4, (int) ((width - 4) * value)));
        if (fillWidth > 0) {
            drawRect(graphics, x + 2, y + 2, fillWidth, height - 4, getAccentBgColor());
        }

        // Rectangular slider thumb
        int thumbWidth = 6;
        int thumbX = Math.max(x + 2, Math.min(x + width - thumbWidth - 2, x + (int) ((width - thumbWidth) * value)));
        drawRect(graphics, thumbX, y + 1, thumbWidth, height - 2, getAccentColor());

        // Value text
        int textX = x + (width - GuiFont.width(font, displayValue)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, GuiFont.text(displayValue), textX, textY, COLOR_TEXT_PRIMARY);
    }

    public static void drawModernButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, String label, boolean isHovered, boolean isPrimary) {
        int bg = isHovered ? (isPrimary ? getAccentColor() : COLOR_CARD_BG_HOVER) : (isPrimary ? getAccentDimColor() : COLOR_CARD_BG);
        int border = isHovered ? getAccentColor() : COLOR_CARD_BORDER;
        int textColor = (isPrimary && isHovered) ? 0xFF0E1015 : COLOR_TEXT_PRIMARY;

        drawRect(graphics, x, y, width, height, bg);
        drawOutline(graphics, x, y, width, height, border);

        int textX = x + (width - GuiFont.width(font, label)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, GuiFont.text(label), textX, textY, textColor);
    }

    public static void drawModernColorButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, int color, boolean isHovered) {
        int border = isHovered ? getAccentColor() : COLOR_CARD_BORDER;

        // Dark background base for alpha transparency grid representation
        drawRect(graphics, x, y, width, height, 0xFF000000);

        // Filled color swatch box
        drawRect(graphics, x + 2, y + 2, width - 4, height - 4, color);

        // Border outline
        drawOutline(graphics, x, y, width, height, border);
    }

    public static void drawModernDestructiveButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, String label, boolean isHovered) {
        int bg = isHovered ? 0xFFDC2626 : 0x44DC2626;
        int border = isHovered ? 0xFFEF4444 : 0x88DC2626;
        int textColor = isHovered ? 0xFFFFFFFF : 0xFFFCA5A5;

        drawRect(graphics, x, y, width, height, bg);
        drawOutline(graphics, x, y, width, height, border);

        int textX = x + (width - GuiFont.width(font, label)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, GuiFont.text(label), textX, textY, textColor);
    }

    /**
     * An inline text field, styled like the config screen's own search box so the two read as the
     * same control. Draws its own blinking caret while focused, since it is not a real widget.
     */
    public static void drawModernTextField(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                                           String value, String placeholder, boolean isFocused, boolean isHovered) {
        int border = isFocused ? getAccentColor() : (isHovered ? getAccentDimColor() : COLOR_CARD_BORDER);
        drawRect(graphics, x, y, width, height, COLOR_CARD_BG);
        drawOutline(graphics, x, y, width, height, border);

        boolean empty = value == null || value.isEmpty();
        String shown = empty ? placeholder : value;
        int textColor = empty ? COLOR_TEXT_DARK : COLOR_TEXT_PRIMARY;

        // Trimmed from the left, so the end being typed stays visible instead of scrolling away.
        int maxWidth = width - 8;
        while (shown.length() > 1 && GuiFont.width(font, shown) > maxWidth) {
            shown = shown.substring(1);
        }

        int textY = y + (height - 8) / 2;
        graphics.text(font, GuiFont.text(shown), x + 4, textY, textColor);

        if (isFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = x + 4 + (empty ? 0 : GuiFont.width(font, shown));
            drawRect(graphics, Math.min(caretX, x + width - 2), textY - 1, 1, 10, getAccentColor());
        }
    }
}

