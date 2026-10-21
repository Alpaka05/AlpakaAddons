package net.alpaka.addons.features.tablist;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Hypixel SkyBlock's tab list into categories and sections, the way the Custom Tab draws it.
 *
 * Hypixel fills all 80 slots of the vanilla list, which vanilla lays out as four columns of 20.
 * The first slot of each column is the column's title ("Players (24)", "Info", "Account Info"),
 * and the widgets below it are separated by empty slots. A title that comes back in a later column
 * continues the category before it, so the two player columns become one list. Blank slots only
 * ever separate sections here, which is what drops vanilla's empty boxes.
 *
 * SkyHanni's Compact Tab List reads the list the same way; this keeps only the part that does not
 * depend on Hypixel's exact wording of each widget, so a renamed widget still shows up as it is.
 */
public final class TabListParser {

    /** How many slots vanilla puts in one column, and so how many Hypixel's columns hold. */
    public static final int COLUMN_SLOTS = 20;

    /** "[312] Name", optionally with a rank tag: a player row in the Players category. */
    private static final Pattern PLAYER = Pattern.compile("^\\[\\d+] (?:\\[[^\\]]+] )?([A-Za-z0-9_]{1,16})");
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private TabListParser() {}

    /** One row: its text without the leading spaces Hypixel indents with, and the player it names. */
    public record Line(Component text, boolean indented, String player) {}

    /** One category: the column title, and its widgets in order. */
    public record Category(Component title, List<List<Line>> sections) {
        public int lineCount() {
            int n = 0;
            for (List<Line> section : sections) n += section.size();
            return n;
        }
    }

    /** The tab list's slots, in vanilla's order, read into categories. */
    public static List<Category> parse(List<Component> slots) {
        List<Category> categories = new ArrayList<>();
        for (int start = 0; start < slots.size(); start += COLUMN_SLOTS) {
            Component title = trim(slots.get(start));
            String titleKey = plain(title).trim();
            Category category = null;
            if (titleKey.isEmpty()) {
                // A column without a title of its own carries on the one before it.
                if (!categories.isEmpty()) category = categories.get(categories.size() - 1);
            } else {
                for (Category existing : categories) {
                    if (plain(existing.title()).trim().equals(titleKey)) category = existing;
                }
            }
            if (category == null) {
                category = new Category(title, new ArrayList<>());
                categories.add(category);
            }

            List<Line> section = new ArrayList<>();
            int end = Math.min(slots.size(), start + COLUMN_SLOTS);
            for (int i = start + (titleKey.isEmpty() ? 0 : 1); i < end; i++) {
                Component slot = slots.get(i);
                String text = plain(slot);
                if (text.isBlank()) {
                    if (!section.isEmpty()) category.sections().add(section);
                    section = new ArrayList<>();
                    continue;
                }
                section.add(new Line(trim(slot), Character.isWhitespace(text.charAt(0)), playerName(text)));
            }
            if (!section.isEmpty()) category.sections().add(section);
        }
        categories.removeIf(category -> category.sections().isEmpty());
        return categories;
    }

    /** The player a row names, or null for a widget row. */
    public static String playerName(String plainText) {
        Matcher m = PLAYER.matcher(plainText.trim());
        return m.find() ? m.group(1) : null;
    }

    /** Hypixel's own advertising: the server address in the header, the store link in the footer. */
    public static boolean isAdvert(Component line) {
        return plain(line).toUpperCase(Locale.ROOT).contains("HYPIXEL.NET");
    }

    /**
     * The tab list's header or footer as lines, trimmed, without blank lines at either end and with
     * runs of blank lines kept to one, which still separates its parts. Adverts go when asked.
     */
    public static List<Component> headerFooterLines(Component text, boolean hideAdverts) {
        List<Component> lines = new ArrayList<>();
        if (text == null) return lines;
        for (Component line : splitLines(text)) {
            if (hideAdverts && isAdvert(line)) continue;
            boolean blank = plain(line).isBlank();
            if (blank && (lines.isEmpty() || plain(lines.get(lines.size() - 1)).isBlank())) continue;
            lines.add(blank ? Component.empty() : trim(line));
        }
        while (!lines.isEmpty() && plain(lines.get(lines.size() - 1)).isBlank()) lines.remove(lines.size() - 1);
        return lines;
    }

    /** A component cut at its line breaks, each part keeping the style it had. */
    public static List<Component> splitLines(Component text) {
        List<Component> lines = new ArrayList<>();
        MutableComponent[] current = {Component.empty()};
        text.visit((Style style, String part) -> {
            String[] pieces = part.split("\n", -1);
            for (int i = 0; i < pieces.length; i++) {
                if (i > 0) {
                    lines.add(current[0]);
                    current[0] = Component.empty();
                }
                if (!pieces[i].isEmpty()) current[0].append(Component.literal(pieces[i]).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        lines.add(current[0]);
        return lines;
    }

    /** The text without color codes. */
    public static String plain(Component text) {
        return FORMATTING.matcher(text.getString()).replaceAll("");
    }

    /**
     * The component without whitespace at either end, its styles kept. Hypixel's own color codes
     * inside the text are stepped over, so "§7 Server" loses its space and keeps its gray.
     */
    public static Component trim(Component text) {
        List<Style> styles = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        text.visit((Style style, String part) -> {
            styles.add(style);
            parts.add(part);
            return Optional.empty();
        }, Style.EMPTY);

        boolean leading = true;
        for (int i = 0; i < parts.size() && leading; i++) {
            String part = parts.get(i);
            StringBuilder kept = new StringBuilder();
            int j = 0;
            while (j < part.length()) {
                char c = part.charAt(j);
                if (c == '§' && j + 1 < part.length()) {
                    kept.append(c).append(part.charAt(j + 1));
                    j += 2;
                } else if (Character.isWhitespace(c)) {
                    j++;
                } else {
                    leading = false;
                    break;
                }
            }
            parts.set(i, kept + part.substring(j));
        }
        for (int i = parts.size() - 1; i >= 0; i--) {
            String part = parts.get(i);
            int end = part.length();
            while (true) {
                if (end > 0 && Character.isWhitespace(part.charAt(end - 1))) {
                    end--;
                } else if (end > 1 && part.charAt(end - 2) == '§') {
                    end -= 2;
                } else {
                    break;
                }
            }
            parts.set(i, part.substring(0, end));
            if (end > 0) break;
        }

        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (!parts.get(i).isEmpty()) out.append(Component.literal(parts.get(i)).withStyle(styles.get(i)));
        }
        return out;
    }
}
