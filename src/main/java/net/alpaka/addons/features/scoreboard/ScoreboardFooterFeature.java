package net.alpaka.addons.features.scoreboard;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Writes "AlpakaAddons" in a gradient where Hypixel puts its address at the bottom of the
 * scoreboard. Vanilla measures the sidebar from the lines it is given, so swapping the line before
 * it is measured is all it takes for the width to fit.
 */
public final class ScoreboardFooterFeature {

    private static final String TEXT = "AlpakaAddons";
    /** Warm gold through pink to violet, left to right. */
    private static final int[] STOPS = {0xFFC24B, 0xFF6FA8, 0x9B7BFF};
    /** Built once; the line is drawn every frame and never changes. */
    private static final Component FOOTER = gradient(TEXT, STOPS);

    private ScoreboardFooterFeature() {}

    /** The line to show in place of {@code line}: the gradient footer if it is Hypixel's address. */
    public static Component replace(Component line) {
        if (!AlpakaConfig.instance.scoreboardAlpakaFooter) return line;
        return isHypixelFooter(line.getString()) ? FOOTER : line;
    }

    /**
     * Hypixel's last line reads "www.hypixel.net" (or "alpha.hypixel.net" on the test server).
     * The line is built from team prefixes, so legacy colour codes sit in the raw text, and
     * Hypixel keeps every line unique with a made-up code in the middle ("§ewww.hypixel.ne§g§et").
     * ChatFormatting.stripFormatting only knows the real codes, so every § goes with the character
     * after it, and of the rest only letters, digits and dots are compared.
     */
    static boolean isHypixelFooter(String line) {
        String plain = ANY_CODE.matcher(line).replaceAll("");
        plain = NOT_ADDRESS.matcher(plain).replaceAll("").toLowerCase(Locale.ROOT);
        return plain.equals("www.hypixel.net") || plain.equals("alpha.hypixel.net");
    }

    private static final Pattern ANY_CODE = Pattern.compile("§.?");
    private static final Pattern NOT_ADDRESS = Pattern.compile("[^A-Za-z0-9.]");

    /** One component per letter, coloured along the stops evenly spread over the text. */
    static Component gradient(String text, int[] stops) {
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
