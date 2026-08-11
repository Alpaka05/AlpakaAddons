package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ModernGuiUtils {
    // Theme Colors (Dark slate black/grey with Electric Cyan accent & Green/Red states)
    public static final int COLOR_BG_BACKDROP = 0xEE0B0C10;
    public static final int COLOR_PANEL_BG = 0xFF12141A;
    public static final int COLOR_SIDEBAR_BG = 0xFF0E1015;
    public static final int COLOR_CARD_BG = 0xFF181B24;
    public static final int COLOR_CARD_BG_HOVER = 0xFF222633;
    public static final int COLOR_CARD_BORDER = 0xFF2B3042;

    public static final int COLOR_ACCENT = 0xFF00E5FF;       // Electric Cyan
    public static final int COLOR_ACCENT_DIM = 0xFF008899;   // Muted Cyan
    public static final int COLOR_ACCENT_BG = 0x3300E5FF;    // Cyan overlay

    public static final int COLOR_TOGGLE_ON_BG = 0xFF133824;
    public static final int COLOR_TOGGLE_ON_BORDER = 0xFF00E676;
    public static final int COLOR_TOGGLE_ON_TEXT = 0xFF00E676;

    public static final int COLOR_TOGGLE_OFF_BG = 0xFF381418;
    public static final int COLOR_TOGGLE_OFF_BORDER = 0xFFFF5252;
    public static final int COLOR_TOGGLE_OFF_TEXT = 0xFFFF5252;

    public static final int COLOR_TEXT_PRIMARY = 0xFFF0F0F5;
    public static final int COLOR_TEXT_MUTED = 0xFF8A90A0;
    public static final int COLOR_TEXT_DARK = 0xFF505565;

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
        int border = isSelected ? COLOR_ACCENT : (isHovered ? COLOR_ACCENT_DIM : COLOR_CARD_BORDER);

        drawRect(graphics, x, y, width, height, bg);
        drawOutline(graphics, x, y, width, height, border);

        if (isSelected) {
            // Accent indicator bar on left side
            drawRect(graphics, x, y, 3, height, COLOR_ACCENT);
        }
    }

    public static void drawModernToggle(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, boolean state, boolean isHovered) {
        int bg = state ? COLOR_TOGGLE_ON_BG : COLOR_TOGGLE_OFF_BG;
        int border = state ? COLOR_TOGGLE_ON_BORDER : COLOR_TOGGLE_OFF_BORDER;
        int textColor = state ? COLOR_TOGGLE_ON_TEXT : COLOR_TOGGLE_OFF_TEXT;

        if (isHovered) {
            // Brighten slightly on hover
            bg = (bg & 0xFF000000) | (((bg & 0xFFFFFF) + 0x111111) & 0xFFFFFF);
        }

        drawRect(graphics, x, y, width, height, bg);
        drawOutline(graphics, x, y, width, height, border);

        // Rectangular status knob inside toggle
        int knobWidth = width / 2 - 2;
        int knobHeight = height - 4;
        int knobX = state ? (x + width - knobWidth - 2) : (x + 2);
        int knobY = y + 2;

        drawRect(graphics, knobX, knobY, knobWidth, knobHeight, border);

        // Status label text ("AN" / "AUS" or "ON" / "OFF")
        String text = state ? "AN" : "AUS";
        int textX = state ? (x + 6) : (x + width - font.width(text) - 6);
        int textY = y + (height - 8) / 2;
        graphics.text(font, Component.literal(text), textX, textY, textColor);
    }

    public static void drawModernSlider(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, double value, String displayValue, boolean isHovered) {
        int trackBg = COLOR_CARD_BG;
        int border = isHovered ? COLOR_ACCENT : COLOR_CARD_BORDER;

        drawRect(graphics, x, y, width, height, trackBg);
        drawOutline(graphics, x, y, width, height, border);

        // Filled track area
        int fillWidth = Math.max(0, Math.min(width - 4, (int) ((width - 4) * value)));
        if (fillWidth > 0) {
            drawRect(graphics, x + 2, y + 2, fillWidth, height - 4, COLOR_ACCENT_BG);
        }

        // Rectangular slider thumb
        int thumbWidth = 6;
        int thumbX = Math.max(x + 2, Math.min(x + width - thumbWidth - 2, x + (int) ((width - thumbWidth) * value)));
        drawRect(graphics, thumbX, y + 1, thumbWidth, height - 2, COLOR_ACCENT);

        // Value text
        int textX = x + (width - font.width(displayValue)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, Component.literal(displayValue), textX, textY, COLOR_TEXT_PRIMARY);
    }

    public static void drawModernButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, String label, boolean isHovered, boolean isPrimary) {
        int bg = isHovered ? (isPrimary ? COLOR_ACCENT : COLOR_CARD_BG_HOVER) : (isPrimary ? COLOR_ACCENT_DIM : COLOR_CARD_BG);
        int border = isHovered ? COLOR_ACCENT : COLOR_CARD_BORDER;
        int textColor = (isPrimary && isHovered) ? 0xFF0E1015 : COLOR_TEXT_PRIMARY;

        drawRect(graphics, x, y, width, height, bg);
        drawOutline(graphics, x, y, width, height, border);

        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, Component.literal(label), textX, textY, textColor);
    }
}
