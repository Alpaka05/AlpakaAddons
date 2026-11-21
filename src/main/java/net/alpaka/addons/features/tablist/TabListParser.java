package net.alpaka.addons.features.tablist;

import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/** Plain text of the tab list's rows, for the features that read Hypixel's widgets from it. */
public final class TabListParser {

    private static final Pattern FORMATTING = Pattern.compile("§.");

    private TabListParser() {}

    /** The text without Hypixel's color codes. */
    public static String plain(Component text) {
        String string = text.getString();
        // Most rows carry their colors as styles, not codes; those need no matcher at all.
        return string.indexOf('§') < 0 ? string : FORMATTING.matcher(string).replaceAll("");
    }
}
