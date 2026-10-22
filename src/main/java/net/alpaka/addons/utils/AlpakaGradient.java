package net.alpaka.addons.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * The mod's name gradient, shared by the scoreboard footer and the chat prefix so both always
 * show the same colours.
 */
public final class AlpakaGradient {

    /** Warm gold through pink to violet, left to right. */
    public static final int[] STOPS = {0xFFC24B, 0xFF6FA8, 0x9B7BFF};

    private AlpakaGradient() {}

    /** {@code text} in the mod's gradient. */
    public static Component of(String text) {
        return of(text, STOPS);
    }

    /** One component per letter, coloured along the stops evenly spread over the text. */
    static Component of(String text, int[] stops) {
        MutableComponent out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0f : (float) i / (n - 1);
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(sample(stops, t)))));
        }
        return out;
    }

    private static int sample(int[] stops, float t) {
        float pos = t * (stops.length - 1);
        int i = Math.min((int) pos, stops.length - 2);
        float f = pos - i;
        int a = stops[i], b = stops[i + 1];
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (r << 16) | (g << 8) | bl;
    }
}
