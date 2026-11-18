package net.alpaka.addons.features.performance;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;

/**
 * A number on the Performance HUD that, when it changes, rolls like a flip clock: the characters
 * that changed slide out and fade while the new ones slide in from the other side. A rising value
 * rolls upward, a falling one downward.
 */
final class RollingValue {
    static final long DURATION_MS = 400L;
    private static final int LINE_HEIGHT = 9;

    private String value = null;
    private int color;
    private double number;

    private String previous = null;
    private int previousColor;
    private long changedAt;
    /** -1 when the value rose (everything moves up), 1 when it fell. */
    private int direction;

    /**
     * Sets the value to show. {@code number} only gives the roll its direction; NaN (no reading)
     * counts as lower than any number.
     */
    void set(String value, int color, double number, long now) {
        if (value.equals(this.value)) {
            this.color = color;
            this.number = number;
            return;
        }
        if (this.value != null) {
            previous = this.value;
            previousColor = this.color;
            changedAt = now;
            double from = Double.isNaN(this.number) ? Double.NEGATIVE_INFINITY : this.number;
            double to = Double.isNaN(number) ? Double.NEGATIVE_INFINITY : number;
            direction = to >= from ? -1 : 1;
        }
        this.value = value;
        this.color = color;
        this.number = number;
    }

    boolean animating(long now) {
        return previous != null && now - changedAt < DURATION_MS;
    }

    /**
     * Draws the value with its left edge at {@code x}; returns how wide the new value is. Positions
     * are kept in fractions of a pixel: a smooth font's glyphs are not whole pixels wide, and
     * rounding each piece would push everything after it to the right.
     */
    float draw(GuiGraphicsExtractor graphics, Font font, Style style, float x, long now, boolean shadow) {
        currentLayout = layout(currentLayout, font, value, style);
        Layout cur = currentLayout;
        float width = cur.prefix[value.length()];
        if (!animating(now)) {
            previous = null;
            text(graphics, font, cur.whole, color, 1.0f, x, 0.0f, shadow);
            return width;
        }
        previousLayout = layout(previousLayout, font, previous, style);
        Layout old = previousLayout;

        float t = (now - changedAt) / (float) DURATION_MS;
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        float outOffset = direction * LINE_HEIGHT * eased;
        float inOffset = -direction * LINE_HEIGHT * (1.0f - eased);

        // Characters only roll one by one when both numbers have the same length; otherwise the
        // digits don't line up and the whole number rolls.
        boolean perChar = previous.length() == value.length();
        float oldWidth = old.prefix[previous.length()];
        graphics.enableScissor((int) Math.floor(x), 0, (int) Math.ceil(x + Math.max(width, oldWidth)) + 1, LINE_HEIGHT);
        for (int i = 0; i < value.length(); i++) {
            float cx = x + cur.prefix[i];
            if (perChar && previous.charAt(i) == value.charAt(i)) {
                text(graphics, font, cur.chars[i], color, 1.0f, cx, 0.0f, shadow);
            } else {
                text(graphics, font, cur.chars[i], color, eased, cx, inOffset, shadow);
            }
        }
        for (int i = 0; i < previous.length(); i++) {
            if (perChar && previous.charAt(i) == value.charAt(i)) continue;
            float cx = x + old.prefix[i];
            text(graphics, font, old.chars[i], previousColor, 1.0f - eased, cx, outOffset, shadow);
        }
        graphics.disableScissor();
        return width;
    }

    /**
     * A value's pieces as drawn: the width of each prefix (measured exactly as before, prefix by
     * prefix), each character and the whole text as components. The HUD draws the same value every
     * frame and rolls it for 400 ms, so they are worked out once per value, style and font
     * generation instead of every frame.
     */
    private static final class Layout {
        final String text;
        final Style style;
        final Font font;
        final int generation;
        final float[] prefix;
        final Component[] chars;
        final Component whole;

        Layout(String text, Style style, Font font, int generation) {
            this.text = text;
            this.style = style;
            this.font = font;
            this.generation = generation;
            int n = text.length();
            prefix = new float[n + 1];
            chars = new Component[n];
            for (int i = 0; i <= n; i++) prefix[i] = width(font, text.substring(0, i), style);
            for (int i = 0; i < n; i++) chars[i] = Component.literal(text.substring(i, i + 1)).withStyle(style);
            whole = Component.literal(text).withStyle(style);
        }
    }

    /** The labels' layouts ("FPS ", "  Ping "...): drawn every frame of a roll, the same few texts. */
    private static final java.util.Map<String, Layout> LABELS = new java.util.HashMap<>();

    /** Draws a fixed label at {@code x} and returns its width, as {@link #text} plus {@link #width} did. */
    static float label(GuiGraphicsExtractor graphics, Font font, String text, Style style, int color, float x, boolean shadow) {
        Layout kept = LABELS.get(text);
        Layout layout = layout(kept, font, text, style);
        if (layout != kept) LABELS.put(text, layout);
        text(graphics, font, layout.whole, color, 1.0f, x, 0.0f, shadow);
        return layout.prefix[text.length()];
    }

    private Layout currentLayout;
    private Layout previousLayout;

    private static Layout layout(Layout kept, Font font, String text, Style style) {
        int generation = net.alpaka.addons.client.gui.AlpakaStyle.fontGeneration;
        if (kept != null && kept.text.equals(text) && kept.style.equals(style) && kept.font == font
                && kept.generation == generation) {
            return kept;
        }
        return new Layout(text, style, font, generation);
    }

    static float width(Font font, String text, Style style) {
        return font.getSplitter().stringWidth(Component.literal(text).withStyle(style));
    }

    /**
     * Draws text at a position in fractions of a pixel. The fade goes through the colour passed to
     * the draw call, since a style's colour carries no alpha.
     */
    static void text(GuiGraphicsExtractor graphics, Font font, String text, Style style, int color, float alpha,
                     float x, float y, boolean shadow) {
        if (alpha <= 0.0f) return;
        text(graphics, font, Component.literal(text).withStyle(style), color, alpha, x, y, shadow);
    }

    /** {@link #text} with the component already built. */
    static void text(GuiGraphicsExtractor graphics, Font font, Component text, int color, float alpha,
                     float x, float y, boolean shadow) {
        if (alpha <= 0.0f) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.text(font, text, 0, 0, ARGB.multiplyAlpha(color, alpha), shadow);
        graphics.pose().popMatrix();
    }
}
