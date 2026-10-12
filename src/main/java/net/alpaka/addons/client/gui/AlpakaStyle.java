package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The look of the config menu, for every other window the mod opens from it - the colour picker,
 * the quick command editor, the HUD editor's bar and the confirmation dialog - so they read as
 * parts of one menu: a dark rounded panel with a soft shadow that pops open, a hairline under the
 * header, slim rows that only show a background on hover, the accent for whatever is active, and
 * the menu font without shadows.
 */
public final class AlpakaStyle {
    private AlpakaStyle() {}

    // ---- Colours, the config screen's -----------------------------------------------------------

    public static final int BACKDROP = 0x80000000;
    public static final int PANEL = 0xFF131313;
    public static final int LINE = 0xFF262626;
    public static final int DIVIDER = 0xFF3A3A3A;
    public static final int ROW = 0xFF1E1E1E;
    public static final int ROW_HOVER = 0xFF242424;
    public static final int ROW_SELECTED = 0xFF2A2A2A;
    public static final int FIELD = 0xFF1F1F1F;
    public static final int TEXT = 0xFFEDEDED;
    public static final int TEXT_SOFT = 0xFFBDBDBD;
    public static final int TEXT_MUTED = 0xFF7C7C7C;
    public static final int TRACK = 0xFF3A3A3A;
    public static final int DANGER = 0xFFE5534B;

    // ---- Metrics, in GUI pixels -----------------------------------------------------------------

    public static final int HEADER_H = 30;
    public static final int PAD = 8;
    public static final int ROW_H = 16;
    public static final int BUTTON_H = 18;
    public static final int ROW_RADIUS = 4;

    /** The three kinds of button: plain, the one that confirms, and the one that destroys. */
    public enum ButtonKind { PLAIN, PRIMARY, DANGER }

    // ---- The window -----------------------------------------------------------------------------

    /** The dimmed game behind the window, against an identity pose so it never slides along. */
    public static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.fill(0, 0, width, height, BACKDROP);
        graphics.pose().popMatrix();
    }

    /**
     * Starts the window's opening pop around the screen's centre. Returns whether a pose was pushed,
     * which {@link #endOpen} needs to know.
     */
    public static boolean beginOpen(GuiGraphicsExtractor graphics, long openTimeMs, int width, int height) {
        float scale = PloppAnimation.getOpenScale(openTimeMs);
        if (scale >= 0.999f) return false;
        graphics.pose().pushMatrix();
        graphics.pose().scaleAround(scale, scale, width / 2.0f, height / 2.0f);
        return true;
    }

    public static void endOpen(GuiGraphicsExtractor graphics, boolean pushed) {
        if (pushed) graphics.pose().popMatrix();
    }

    /** The window itself: a rounded dark panel with a soft shadow. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        ModernGuiUtils.drawPanelShadow(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS, 1.0f);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS, PANEL);
    }

    /** The header: a title on the left, and the hairline that closes it off below. */
    public static void header(GuiGraphicsExtractor graphics, Font font, String title, int x, int y, int w) {
        graphics.text(font, GuiFont.text(title), x + PAD + 2, y + (HEADER_H - 8) / 2, TEXT, false);
        ModernGuiUtils.drawRect(graphics, x + PAD, y + HEADER_H - 1, w - PAD * 2, 1, LINE);
    }

    /** A small label over a group of controls, as the config's sidebar labels its categories. */
    public static void groupLabel(GuiGraphicsExtractor graphics, Font font, String label, int x, int y) {
        graphics.text(font, GuiFont.text(label), x, y, TEXT_MUTED, false);
    }

    // ---- Controls -------------------------------------------------------------------------------

    /**
     * A button: a slim rounded row with its label centred. Plain ones turn lighter on hover; the
     * primary one carries the accent, the dangerous one red.
     */
    public static void button(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                              String label, boolean hover, ButtonKind kind) {
        int accent = ModernGuiUtils.getAccentColor();
        int background;
        int text;
        switch (kind) {
            case PRIMARY -> {
                background = (accent & 0x00FFFFFF) | (hover ? 0x59000000 : 0x33000000);
                text = hover ? TEXT : accent;
            }
            case DANGER -> {
                background = hover ? (DANGER & 0x00FFFFFF) | 0x40000000 : ROW;
                text = hover ? DANGER : 0xFFC9726C;
            }
            default -> {
                background = hover ? ROW_SELECTED : ROW;
                text = hover ? TEXT : TEXT_SOFT;
            }
        }
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, ROW_RADIUS, background);
        ModernGuiUtils.centeredText(graphics, font, GuiFont.text(label), x + w / 2, y + (h - 8) / 2, text);
    }

    /** A glyph that acts as a button, with a soft square behind it on hover. */
    public static void iconButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, String glyph,
                                  boolean hover, boolean enabled, boolean danger) {
        if (hover && enabled) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y, size, size, 3,
                    danger ? (DANGER & 0x00FFFFFF) | 0x33000000 : ROW_SELECTED);
        }
        int color = !enabled ? DIVIDER
                : hover ? (danger ? DANGER : ModernGuiUtils.getAccentColor())
                : TEXT_MUTED;
        int gw = GuiFont.width(font, glyph);
        graphics.text(font, GuiFont.text(glyph), x + (size - gw) / 2, y + (size - 8) / 2, color, false);
    }

    /**
     * A text field: a rounded box with a line along its bottom that lights up in the accent colour
     * while it has the focus, and a blinking caret.
     */
    public static void textField(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                 String text, String placeholder, boolean focused, boolean hover, long caretFrom) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, 3, hover && !focused ? ROW_HOVER : FIELD);
        ModernGuiUtils.drawRect(graphics, x + 3, y + h - 1, w - 6, 1, focused ? ModernGuiUtils.getAccentColor() : LINE);
        int ty = y + (h - 8) / 2;
        boolean empty = text.isEmpty();
        String shown = empty && !focused ? placeholder : text;
        String clipped = clipLeft(font, shown, w - 10);
        graphics.text(font, GuiFont.text(clipped), x + 5, ty, empty && !focused ? TEXT_MUTED : TEXT, false);
        if (focused && (System.currentTimeMillis() - caretFrom) / 500 % 2 == 0) {
            int caretX = x + 5 + GuiFont.width(font, clipped);
            ModernGuiUtils.drawRect(graphics, caretX, ty - 1, 1, 10, TEXT);
        }
    }

    /** The end of a text that fits the width - the part being typed stays in view. */
    private static String clipLeft(Font font, String text, int maxW) {
        if (GuiFont.width(font, text) <= maxW) return text;
        int start = 0;
        while (start < text.length() && GuiFont.width(font, text.substring(start)) > maxW) start++;
        return text.substring(start);
    }

    /** The config's slider track: a hairline, the accent up to the knob, and a round knob. */
    public static void sliderTrack(GuiGraphicsExtractor graphics, int x, int y, int w, double norm, boolean hover, boolean dragging) {
        int accent = ModernGuiUtils.getAccentColor();
        int knobX = x + (int) Math.round(Math.max(0.0, Math.min(1.0, norm)) * w);
        ModernGuiUtils.drawRect(graphics, x, y, w, 1, TRACK);
        ModernGuiUtils.drawRect(graphics, x, y, knobX - x, 1, accent);
        int knob = hover || dragging ? 7 : 6;
        ModernGuiUtils.drawRoundedRect(graphics, knobX - knob / 2, y - knob / 2, knob, knob, knob / 2, accent);
    }

    /**
     * A slider for one colour channel: the track shows the colour from the channel's 0 to its 255,
     * and the knob is filled with the colour as it is now.
     */
    public static void gradientTrack(GuiGraphicsExtractor graphics, int x, int y, int w, int from, int to,
                                     double norm, int knobColor, boolean hover, boolean dragging) {
        int h = 4;
        int top = y - h / 2;
        // A horizontal gradient from narrow vertical slices; the rounded ends are drawn as caps.
        int steps = Math.max(1, w / 2);
        for (int i = 0; i < steps; i++) {
            int sx = x + i * w / steps;
            int ex = x + (i + 1) * w / steps;
            int color = ModernGuiUtils.lerpColor(from, to, (i + 0.5f) / steps) | 0xFF000000;
            ModernGuiUtils.drawRect(graphics, sx, top, ex - sx, h, color);
        }
        ModernGuiUtils.drawRoundedOutline(graphics, x - 1, top - 1, w + 2, h + 2, 2, LINE);
        int knobX = x + (int) Math.round(Math.max(0.0, Math.min(1.0, norm)) * w);
        int outer = hover || dragging ? 10 : 9;
        int inner = outer - 4;
        ModernGuiUtils.drawRoundedRect(graphics, knobX - outer / 2, y - outer / 2, outer, outer, outer / 2, TEXT);
        ModernGuiUtils.drawRoundedRect(graphics, knobX - inner / 2, y - inner / 2, inner, inner, inner / 2, knobColor | 0xFF000000);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
