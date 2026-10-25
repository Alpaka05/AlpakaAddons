package net.alpaka.addons.features.scoreboard;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.AlpakaGradient;
import net.minecraft.network.chat.Component;


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
        // Asked for every sidebar line every frame, and nearly all of them are something else: a
        // long line or one without a dot is not the address, whatever else is in it.
        if (line.length() > MAX_FOOTER_LENGTH || line.indexOf('.') < 0) return false;
        // One walk against both addresses at once, in place of two regex passes and the strings
        // they built.
        int www = 0;
        int alpha = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (!isAddressChar(c)) continue;
            c = Character.toLowerCase(c);
            www = www >= 0 && www < WWW.length() && WWW.charAt(www) == c ? www + 1 : -1;
            alpha = alpha >= 0 && alpha < ALPHA.length() && ALPHA.charAt(alpha) == c ? alpha + 1 : -1;
            if (www < 0 && alpha < 0) return false;
        }
        return www == WWW.length() || alpha == ALPHA.length();
    }

    private static final String WWW = "www.hypixel.net";
    private static final String ALPHA = "alpha.hypixel.net";
    /** Longer than the address with any codes and markers Hypixel puts into it. */
    private static final int MAX_FOOTER_LENGTH = 40;

    private static boolean isAddressChar(char c) {
        return c == '.' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
