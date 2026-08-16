package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

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

    public static void drawModernToggle(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, boolean state, boolean isHovered) {
        int trackBg = state ? getAccentBgColor() : COLOR_CARD_BG;
        int border = isHovered ? getAccentColor() : (state ? getAccentDimColor() : COLOR_CARD_BORDER);

        drawRect(graphics, x, y, width, height, trackBg);
        drawOutline(graphics, x, y, width, height, border);

        // Compact sliding grey square knob (no text)
        int knobSize = height - 4;
        int knobX = state ? (x + width - knobSize - 2) : (x + 2);
        int knobY = y + 2;

        int knobColor = state ? getAccentColor() : 0xFF64748B; // Sleek grey when OFF, Accent when ON
        drawRect(graphics, knobX, knobY, knobSize, knobSize, knobColor);
        drawOutline(graphics, knobX, knobY, knobSize, knobSize, 0x40000000);
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
        int textX = x + (width - font.width(displayValue)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, Component.literal(displayValue), textX, textY, COLOR_TEXT_PRIMARY);
    }

    public static void drawModernButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, String label, boolean isHovered, boolean isPrimary) {
        int bg = isHovered ? (isPrimary ? getAccentColor() : COLOR_CARD_BG_HOVER) : (isPrimary ? getAccentDimColor() : COLOR_CARD_BG);
        int border = isHovered ? getAccentColor() : COLOR_CARD_BORDER;
        int textColor = (isPrimary && isHovered) ? 0xFF0E1015 : COLOR_TEXT_PRIMARY;

        drawRect(graphics, x, y, width, height, bg);
        drawOutline(graphics, x, y, width, height, border);

        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, Component.literal(label), textX, textY, textColor);
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

        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, Component.literal(label), textX, textY, textColor);
    }
}

