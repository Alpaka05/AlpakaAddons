package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The mod's one design language, the config menu's, for every window it draws - the config menu
 * itself, the color picker, the command wheel editor, the HUD editor's bar and the confirmation
 * dialog - so they read as parts of one menu: a dark rounded panel with a soft shadow that pops
 * open, a hairline under the header, slim rows that only show a background on hover, the accent
 * for whatever is active, and the menu font without shadows.
 *
 * Hover goes one step lighter: a setting row from the panel to {@link #ROW}, a card from
 * {@link #ROW} to {@link #ROW_HOVER}; buttons, which sit on rows themselves, go to
 * {@link #ROW_SELECTED} so they stay apart from the row under them.
 */
public final class AlpakaStyle {
    private AlpakaStyle() {}

    // ---- Colors ---------------------------------------------------------------------------------

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
    /** A switch that is off: its outline, and its knob. */
    private static final int TOGGLE_OFF = 0xFF6E6E6E;
    private static final int TOGGLE_KNOB_OFF = 0xFF8A8A8A;
    /** Behind a tooltip: opaque, so nothing under it shows through its text. */
    private static final int TOOLTIP = 0xFF1A1A1A;
    /** Behind a preview of something the game draws itself: the dark of the chat's own background. */
    public static final int PREVIEW = 0xFF0D0D0D;
    public static final int DANGER = 0xFFE5534B;
    /** A dangerous action's text at rest; {@link #DANGER} once hovered. */
    public static final int DANGER_SOFT = 0xFFC9726C;

    // ---- Metrics, in GUI pixels -----------------------------------------------------------------

    /** The header of every window, the config menu's included, and the height of the HUD editor's bar. */
    public static final int HEADER_H = 33;
    /** Corner radius of a window: the config menu, a dialog, a picker. */
    public static final int PANEL_RADIUS = 8;
    public static final int PAD = 8;
    public static final int ROW_H = 16;
    public static final int BUTTON_H = 18;
    public static final int ROW_RADIUS = 4;
    /** Rows that stand as cards of their own, like the config's features; a little rounder than a row. */
    public static final int CARD_RADIUS = 5;
    /** Corner radius of fields: text, choices, the hex code. */
    public static final int FIELD_RADIUS = 3;
    public static final int TOOLTIP_DELAY_MS = 450;

    /** The three kinds of button: plain, the one that confirms, and the one that destroys. */
    public enum ButtonKind { PLAIN, PRIMARY, DANGER }

    /** The player's accent color: what is selected, focused or about to happen. */
    public static int accent() {
        return ModernGuiUtils.getAccentColor();
    }

    /**
     * The click of a control: vanilla's button click, which the custom sounds swap for their chime
     * when the player turned them on. Every window of the mod clicks this way, so a press sounds the
     * same in the config, a dialog or the pause menu.
     */
    public static void click() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return;
        net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(mc.getSoundManager());
    }

    /** The accent, dimmed: a focused outline, a text selection. */
    public static int accentDim() {
        return ModernGuiUtils.getAccentDimColor();
    }

    // ---- The window -----------------------------------------------------------------------------

    /** The dimmed game behind the window, against an identity pose so it never slides along. */
    public static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
        backdrop(graphics, width, height, 1.0f);
    }

    /** The backdrop at a share of its strength, for a window that lets the world through at times. */
    public static void backdrop(GuiGraphicsExtractor graphics, int width, int height, float strength) {
        int alpha = Math.round(((BACKDROP >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, strength)));
        if (alpha <= 0) return;
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.fill(0, 0, width, height, alpha << 24);
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
        ModernGuiUtils.drawPanelShadow(graphics, x, y, w, h, PANEL_RADIUS, 1.0f);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, PANEL_RADIUS, PANEL);
    }

    /**
     * A small window that floats over another one, such as an open list: the panel with a lighter
     * shadow and a hairline edge that keeps it apart from the window beneath.
     */
    public static void popover(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        ModernGuiUtils.drawPanelShadow(graphics, x, y, w, h, PANEL_RADIUS, 0.8f);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, PANEL_RADIUS, PANEL);
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, PANEL_RADIUS, LINE);
    }

    /** The header: a title on the left, and the hairline that closes it off below. */
    public static void header(GuiGraphicsExtractor graphics, Font font, String title, int x, int y, int w) {
        title(graphics, font, title, x + PAD + 2, y);
        headerLine(graphics, x, y, w);
    }

    /** A window's title, centred in the height of the header whose top is {@code headerY}. */
    public static void title(GuiGraphicsExtractor graphics, Font font, String title, int x, int headerY) {
        graphics.text(font, GuiFont.text(title), x, headerY + (HEADER_H - 8) / 2, TEXT, false);
    }

    /** The hairline that closes a header off, along the bottom of the header whose top is {@code y}. */
    public static void headerLine(GuiGraphicsExtractor graphics, int x, int y, int w) {
        ModernGuiUtils.drawRect(graphics, x + PAD, y + HEADER_H - 1, w - PAD * 2, 1, LINE);
    }

    /** Where a button in a header sits: centred in its height. */
    public static int headerButtonY(int headerY) {
        return headerY + (HEADER_H - BUTTON_H) / 2;
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
        button(graphics, font, x, y, w, h, label, hover, kind, true);
    }

    /** A button that may be switched off: then it keeps its place, dimmed, and ignores the mouse. */
    public static void button(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                              String label, boolean hover, ButtonKind kind, boolean enabled) {
        if (!enabled) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, ROW_RADIUS, ROW);
            ModernGuiUtils.centeredText(graphics, font, GuiFont.text(label), x + w / 2, y + (h - 8) / 2, TEXT_MUTED);
            return;
        }
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
                text = hover ? DANGER : DANGER_SOFT;
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
            ModernGuiUtils.drawRoundedRect(graphics, x, y, size, size, FIELD_RADIUS,
                    danger ? (DANGER & 0x00FFFFFF) | 0x33000000 : ROW_SELECTED);
        }
        int color = !enabled ? DIVIDER
                : hover ? (danger ? DANGER : ModernGuiUtils.getAccentColor())
                : TEXT_MUTED;
        int gw = GuiFont.width(font, glyph);
        graphics.text(font, GuiFont.text(glyph), x + (size - gw) / 2, y + (size - 8) / 2, color, false);
    }

    /** An arrow that acts as a button, drawn with the menu's chevron rather than a glyph. */
    public static void chevronButton(GuiGraphicsExtractor graphics, int x, int y, int size, boolean left,
                                     boolean hover, boolean enabled) {
        if (hover && enabled) ModernGuiUtils.drawRoundedRect(graphics, x, y, size, size, FIELD_RADIUS, ROW_SELECTED);
        int color = !enabled ? DIVIDER : hover ? ModernGuiUtils.getAccentColor() : TEXT_MUTED;
        sideChevron(graphics, x + size / 2, y + size / 2, left, color);
    }

    /**
     * A text field: a rounded box with a line along its bottom that lights up in the accent color
     * while it has the focus, and a blinking caret.
     */
    public static void textField(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                 String text, String placeholder, boolean focused, boolean hover, long caretFrom) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, FIELD_RADIUS, hover && !focused ? ROW_HOVER : FIELD);
        ModernGuiUtils.drawRect(graphics, x + 3, y + h - 1, w - 6, 1, focused ? ModernGuiUtils.getAccentColor() : LINE);
        int ty = y + (h - 8) / 2;
        boolean empty = text.isEmpty();
        String shown = empty && !focused ? placeholder : text;
        // While typing, the end stays in view; at rest the start does, cut with an ellipsis.
        String clipped = focused ? clipLeft(font, shown, w - 10) : ellipsize(font, shown, w - 10);
        graphics.text(font, GuiFont.text(clipped), x + 5, ty, empty && !focused ? TEXT_MUTED : TEXT, false);
        if (focused && (System.currentTimeMillis() - caretFrom) / 500 % 2 == 0) {
            int caretX = x + 5 + GuiFont.width(font, clipped);
            ModernGuiUtils.drawRect(graphics, caretX, ty - 1, 1, 10, TEXT);
        }
    }

    /**
     * A slim switch: a rounded outline with a dot that slides across, gray when off and in the
     * accent color when on.
     */
    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float progress) {
        int accent = ModernGuiUtils.getAccentColor();
        int outline = ModernGuiUtils.lerpColor(TOGGLE_OFF, accent, progress);
        int knob = ModernGuiUtils.lerpColor(TOGGLE_KNOB_OFF, accent, progress);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, h / 2,
                ModernGuiUtils.lerpColor(0x00000000, (accent & 0x00FFFFFF) | 0x26000000, progress));
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, h / 2, outline);
        int size = h - 4;
        int travel = w - 4 - size;
        int kx = x + 2 + Math.round(travel * progress);
        ModernGuiUtils.drawRoundedRect(graphics, kx, y + 2, size, size, size / 2, knob);
    }

    /**
     * A select field: the current value in a field, with a chevron that points down while its list
     * is open. Its list is drawn with {@link #choiceEntry}.
     */
    public static void selectField(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                   String value, boolean open) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, FIELD_RADIUS, FIELD);
        graphics.text(font, GuiFont.text(ellipsize(font, value, w - 16)), x + 4, y + (h - 8) / 2, TEXT, false);
        chevron(graphics, x + w - 6, y + h / 2, open, TEXT_SOFT);
    }

    /**
     * One name of an open choice list: on one unbroken line down the list, the current choice
     * marked on it and named in the accent.
     */
    public static void choiceEntry(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                   String name, boolean current, boolean hover) {
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, ROW_RADIUS, ROW);
        ModernGuiUtils.drawRect(graphics, x + 6, y, 1, h, LINE);
        if (current) ModernGuiUtils.drawRect(graphics, x + 6, y + 2, 1, h - 4, ModernGuiUtils.getAccentColor());
        int color = current ? ModernGuiUtils.getAccentColor() : hover ? TEXT : TEXT_SOFT;
        graphics.text(font, GuiFont.text(ellipsize(font, name, w - 24)), x + 14, y + (h - 8) / 2, color, false);
    }

    /**
     * A small chevron, pointing down when open and right when closed: two smooth strokes as quads,
     * so the diagonals stay clean at every GUI scale instead of stepping pixel by pixel.
     */
    public static void chevron(GuiGraphicsExtractor graphics, int cx, int cy, boolean open, int color) {
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(1.0f / guiScale());
        float s = 2.5f;
        float t = 1.1f;
        if (open) {
            stroke(mesh, cx - s, cy - s / 2, cx, cy + s / 2, t, color);
            stroke(mesh, cx, cy + s / 2, cx + s, cy - s / 2, t, color);
        } else {
            stroke(mesh, cx - s / 2, cy - s, cx + s / 2, cy, t, color);
            stroke(mesh, cx + s / 2, cy, cx - s / 2, cy + s, t, color);
        }
        mesh.submit(graphics);
    }

    /** A chevron pointing left or right, a little larger than the dropdowns' one, for stepping. */
    public static void sideChevron(GuiGraphicsExtractor graphics, int cx, int cy, boolean left, int color) {
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(1.0f / guiScale());
        float s = 3.0f;
        float t = 1.2f;
        float d = left ? -1 : 1;
        stroke(mesh, cx - d * s / 2, cy - s, cx + d * s / 2, cy, t, color);
        stroke(mesh, cx + d * s / 2, cy, cx - d * s / 2, cy + s, t, color);
        mesh.submit(graphics);
    }

    /** Line width for the mesh-drawn hairlines below: a little over a screen pixel, in GUI units. */
    private static float meshLine(int scale) {
        return Math.max(1.5f, scale * 0.6f) / scale;
    }

    /**
     * A dashed circle, for an empty slot waiting to be filled - the color picker's plus. Each dash
     * is a ring segment with feathered edges, so it stays smooth at any GUI scale and position.
     */
    public static void dashedCircle(GuiGraphicsExtractor graphics, float cx, float cy, float radius, int dashes, int color) {
        int scale = guiScale();
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(1.0f / scale);
        float line = meshLine(scale);
        float step = (float) (2.0 * Math.PI / dashes);
        float span = step * 0.55f;
        for (int d = 0; d < dashes; d++) {
            float from = d * step - (float) Math.PI / 2 - span / 2;
            mesh.ringSector(cx, cy, radius - line, radius, from, from + span, from, from + span, color, color, false);
        }
        mesh.submit(graphics);
    }

    /** A plus centred on the point, its arms feathered like the dashed circle. */
    public static void plus(GuiGraphicsExtractor graphics, float cx, float cy, float half, int color) {
        int scale = guiScale();
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(1.0f / scale);
        float t = meshLine(scale) / 2;
        mesh.convexPolygon(new float[] {cx - half, cy - t, cx + half, cy - t, cx + half, cy + t, cx - half, cy + t}, color);
        mesh.convexPolygon(new float[] {cx - t, cy - half, cx + t, cy - half, cx + t, cy - t, cx - t, cy - t}, color);
        mesh.convexPolygon(new float[] {cx - t, cy + t, cx + t, cy + t, cx + t, cy + half, cx - t, cy + half}, color);
        mesh.submit(graphics);
    }

    /**
     * A round knob: a soft shadow, a light ring and the color inside. Drawn at a fractional
     * position, so it glides with the mouse instead of hopping from one GUI pixel to the next,
     * which on a high GUI scale is several screen pixels at a time.
     */
    public static void knob(GuiGraphicsExtractor graphics, float cx, float cy, int diameter, int color) {
        // The circles are the shader's own, which are round at any size; the fraction of a pixel
        // the integer rectangles cannot express goes into the pose instead.
        int x = (int) Math.floor(cx - diameter / 2.0f);
        int y = (int) Math.floor(cy - diameter / 2.0f);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx - diameter / 2.0f - x, cy - diameter / 2.0f - y);
        int round = (diameter + 3) / 2;
        ModernGuiUtils.drawRoundedRect(graphics, x - 1, y, diameter + 2, diameter + 2, round, 0x50000000);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, diameter, diameter, round, TEXT);
        ModernGuiUtils.drawRoundedRect(graphics, x + 2, y + 2, diameter - 4, diameter - 4, round, color | 0xFF000000);
        graphics.pose().popMatrix();
    }

    private static int guiScale() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc == null ? 2 : (int) Math.max(1, mc.getWindow().getGuiScale());
    }

    /** A straight stroke of the given thickness, with square ends half a thickness past each point. */
    private static void stroke(net.alpaka.addons.features.wheel.WheelMesh mesh, float x0, float y0, float x1, float y1,
                               float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        float ux = dx / len * thickness / 2;
        float uy = dy / len * thickness / 2;
        float nx = -uy;
        float ny = ux;
        mesh.quad(x0 - ux + nx, y0 - uy + ny, color, x1 + ux + nx, y1 + uy + ny, color,
                x1 + ux - nx, y1 + uy - ny, color, x0 - ux - nx, y0 - uy - ny, color);
    }

    /**
     * A tooltip by the mouse: the text wrapped to a narrow box, kept on the screen; the config menu
     * shows a setting's description this way after {@link #TOOLTIP_DELAY_MS} of hovering.
     */
    public static void tooltip(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH,
                               int mouseX, int mouseY, String text) {
        if (text == null || text.isBlank()) return;
        // Over everything drawn so far, its text included.
        graphics.nextStratum();
        int maxW = 170;
        int lineH = 10;
        java.util.List<net.minecraft.network.chat.FormattedText> lines = GuiFont.splitLines(font, text, maxW);
        int w = 0;
        for (net.minecraft.network.chat.FormattedText line : lines) w = Math.max(w, font.width(line));
        int h = lines.size() * lineH + 6;
        w += 10;
        int x = Math.min(mouseX + 10, screenW - w - 4);
        int y = Math.min(mouseY + 10, screenH - h - 4);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, ROW_RADIUS, TOOLTIP);
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, ROW_RADIUS, DIVIDER);
        int ly = y + 4;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(line), x + 5, ly, TEXT_SOFT, false);
            ly += lineH;
        }
    }

    /**
     * A color swatch: the color over a checkerboard where it is see-through, in a hairline edge so a
     * dark color still shows its shape on the dark window.
     */
    public static void swatch(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        int radius = 2;
        if ((color >>> 24) < 0xFF) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, radius, FIELD);
            int cell = Math.max(2, h / 3);
            for (int cy = 0; cy * cell < h; cy++) {
                for (int cx = 0; cx * cell < w; cx++) {
                    if ((cx + cy) % 2 != 0) continue;
                    int cellX = x + cx * cell;
                    int cellY = y + cy * cell;
                    int cellW = Math.min(cell, w - cx * cell);
                    int cellH = Math.min(cell, h - cy * cell);
                    boolean corner = (cx == 0 || cellX + cellW == x + w) && (cy == 0 || cellY + cellH == y + h);
                    if (corner) {
                        // A square cell would poke out of the swatch's rounded corner, so draw the
                        // whole rounded swatch shape clipped to the cell instead.
                        graphics.enableScissor(cellX, cellY, cellX + cellW, cellY + cellH);
                        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, radius, TRACK);
                        graphics.disableScissor();
                    } else {
                        ModernGuiUtils.drawRect(graphics, cellX, cellY, cellW, cellH, TRACK);
                    }
                }
            }
        }
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, radius, color);
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, radius, DIVIDER);
    }

    /**
     * Soft edges for a scrolling area: the window's color fading out over a few pixels at the top
     * and the bottom of the clip, so a row cut by the edge reads as scrolled away, not broken off.
     * Drawn only on the side where something is hidden.
     */
    public static void scrollFades(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                   boolean above, boolean below) {
        int fade = 6;
        int clear = PANEL & 0x00FFFFFF;
        if (above) graphics.fillGradient(x, y, x + w, y + fade, PANEL, clear);
        if (below) graphics.fillGradient(x, y + h - fade, x + w, y + h, clear, PANEL);
    }

    /** The text cut to fit the width, with an ellipsis where it was cut. */
    public static String ellipsize(Font font, String text, int maxW) {
        if (text == null || maxW <= 0) return "";
        if (GuiFont.width(font, text) <= maxW) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && GuiFont.width(font, text.substring(0, end) + dots) > maxW) end--;
        return text.substring(0, end).trim() + dots;
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
     * A color slider: the track shows the color from the slider's 0 to its end, and the knob is
     * filled with the color as it is now. The track is a pill whose round ends reach half its height
     * past {@code x} and {@code x + w}, so the knob at either end still sits inside it.
     */
    public static void gradientTrack(GuiGraphicsExtractor graphics, int x, int y, int w, int from, int to,
                                     double norm, int knobColor, boolean hover, boolean dragging) {
        int h = 6;
        int r = h / 2;
        int top = y - r;
        // The round ends are caps in the end colors, which is what the gradient starts and stops at.
        // Between them one quad whose left corners carry the start color and right corners the end
        // color, so the GPU blends it smoothly however wide the slider is drawn.
        int c0 = from | 0xFF000000;
        int c1 = to | 0xFF000000;
        ModernGuiUtils.drawRoundedRect(graphics, x - r, top, h, h, r, c0);
        ModernGuiUtils.drawRoundedRect(graphics, x + w - r, top, h, h, r, c1);
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(1.0f / guiScale());
        mesh.quad(x, top, c0, x, top + h, c0, x + w, top + h, c1, x + w, top, c1);
        mesh.submit(graphics);
        ModernGuiUtils.drawRoundedOutline(graphics, x - r - 1, top - 1, w + h + 2, h + 2, r + 1, LINE);
        float knobX = x + (float) (Math.max(0.0, Math.min(1.0, norm)) * w);
        knob(graphics, knobX, y, hover || dragging ? 10 : 9, knobColor);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
