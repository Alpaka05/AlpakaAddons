package net.alpaka.addons.features.scoreboard;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.AlpakaGradient;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Writes "AlpakaAddons" in a gradient where Hypixel puts its address at the bottom of the
 * scoreboard. Vanilla measures the sidebar from the lines it is given, so swapping the line before
 * it is measured is all it takes for the width to fit.
 */
public final class ScoreboardFooterFeature {

    /** Built once; the line is drawn every frame and never changes. */
    private static final Component FOOTER = AlpakaGradient.of("AlpakaAddons");

    private ScoreboardFooterFeature() {}

    /** The line to show in place of {@code line}: the gradient footer if it is Hypixel's address. */
    public static Component replace(Component line) {
        if (!AlpakaConfig.instance.scoreboardAlpakaFooter) return line;
        return isHypixelFooter(line.getString()) ? FOOTER : line;
    }

    /**
     * Whether vanilla is about to draw the footer, as {@link #replace} handed it back or wrapped in
     * a parent that only sets the font.
     */
    public static boolean isFooter(Component line) {
        return line == FOOTER || (line.getSiblings().size() == 1 && line.getSiblings().get(0) == FOOTER);
    }

    /** Where the footer starts so it sits in the middle of the sidebar, as vanilla does with the title. */
    public static int centeredX(int left, int width, int textWidth) {
        return left + width / 2 - textWidth / 2;
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
}
