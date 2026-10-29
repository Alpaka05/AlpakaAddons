package net.alpaka.addons.features.dungeon.terminal;

import java.util.Locale;

/** The words the custom terminal window shows besides the cells. Pure. */
public final class TerminalGuiText {

    private TerminalGuiText() {}

    /**
     * What the terminal asks for, beside its name in the header: What Starts With's letter in
     * quotes, Select All's color in Title Case. Hypixel still says "Silver" for light gray, as 1.8
     * did, so that one carries the modern name too. Null for the other terminals.
     */
    public static String argument(TerminalKind kind, String plainTitle) {
        String argument = kind.argument(plainTitle);
        if (argument == null || argument.isBlank()) return null;
        if (kind == TerminalKind.STARTS_WITH) return "'" + argument.toUpperCase(Locale.ROOT) + "'";
        if (kind != TerminalKind.SELECT_ALL) return null;
        String color = titleCase(argument.trim());
        return color.equals("Silver") ? "Silver (Light Gray)" : color;
    }

    /**
     * The short note shown for a moment after the window refused a click; it explains that click
     * and nothing else. It is never shown before a click, so it is no signal for when to click.
     */
    public static String refusal(TerminalClicks.Verdict verdict) {
        if (verdict == null) return null;
        return switch (verdict) {
            case TOO_EARLY -> "Ignored: just opened";
            case TOO_FAST -> "Ignored: too fast";
            case WRONG -> "Blocked: wrong click";
            case ALLOW -> null;
        };
    }

    private static String titleCase(String words) {
        StringBuilder out = new StringBuilder(words.length());
        boolean start = true;
        for (char ch : words.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                if (!start) out.append(' ');
                start = true;
                continue;
            }
            out.append(start ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            start = false;
        }
        return out.toString().trim();
    }
}
