package net.alpaka.addons.features.performance;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;

/**
 * A number on the Performance HUD that, when it changes, rolls like a flip clock: the characters
 * that changed slide out and fade while the new ones slide in from the other side. A rising value
 * rolls upward, a falling one downward.
 */
final class RollingValue {
    static final long DURATION_MS = 250L;
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

    /** Draws the value with its left edge at {@code x}; returns how wide the new value is. */
    int draw(GuiGraphicsExtractor graphics, Font font, Style style, int x, long now, boolean shadow) {
        int width = font.width(styled(value, style));
        if (!animating(now)) {
            previous = null;
            graphics.text(font, styled(value, style).withColor(color), x, 0, 0xFFFFFFFF, shadow);
            return width;
        }

        float t = (now - changedAt) / (float) DURATION_MS;
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        int outOffset = Math.round(direction * LINE_HEIGHT * eased);
        int inOffset = Math.round(-direction * LINE_HEIGHT * (1.0f - eased));
        int outColor = ARGB.multiplyAlpha(previousColor, 1.0f - eased);
        int inColor = ARGB.multiplyAlpha(color, eased);

        // Characters only roll one by one when both numbers have the same length; otherwise the
        // digits don't line up and the whole number rolls.
        boolean perChar = previous.length() == value.length();
        int oldWidth = font.width(styled(previous, style));
        graphics.enableScissor(x, 0, x + Math.max(width, oldWidth) + 1, LINE_HEIGHT);
        for (int i = 0; i < value.length(); i++) {
            int cx = x + font.width(styled(value.substring(0, i), style));
            Component c = styled(value.substring(i, i + 1), style);
            if (perChar && previous.charAt(i) == value.charAt(i)) {
                graphics.text(font, c.copy().withColor(color), cx, 0, 0xFFFFFFFF, shadow);
            } else {
                graphics.text(font, c.copy().withColor(inColor), cx, inOffset, 0xFFFFFFFF, shadow);
            }
        }
        for (int i = 0; i < previous.length(); i++) {
            if (perChar && previous.charAt(i) == value.charAt(i)) continue;
            int cx = x + font.width(styled(previous.substring(0, i), style));
            graphics.text(font, styled(previous.substring(i, i + 1), style).withColor(outColor), cx, outOffset,
                    0xFFFFFFFF, shadow);
        }
        graphics.disableScissor();
        return width;
    }

    private static MutableComponent styled(String text, Style style) {
        return Component.literal(text).withStyle(style);
    }
}
