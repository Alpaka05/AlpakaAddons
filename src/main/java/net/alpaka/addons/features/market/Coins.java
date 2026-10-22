package net.alpaka.addons.features.market;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Reading and writing coin amounts the way SkyBlock writes them. */
public final class Coins {
    private Coins() {}

    private static final DecimalFormatSymbols US = DecimalFormatSymbols.getInstance(Locale.US);
    private static final java.util.regex.Pattern TIME_PART = java.util.regex.Pattern.compile("(\\d+)\\s*([dhms])");

    /**
     * A number as it appears in a lore line, such as "1,234,567.5". Null when it isn't one.
     */
    public static Double parseLore(String text) {
        if (text == null) return null;
        String cleaned = text.replace(",", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A number as the player types it into one of Hypixel's inputs: digits with commas or a point,
     * optionally followed by k, m or b ("1.5m", "250k"). Null for anything else.
     */
    public static Double parseInput(String text) {
        if (text == null) return null;
        String t = text.replace(",", "").replace(" ", "").toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        double factor = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            factor = last == 'k' ? 1e3 : last == 'm' ? 1e6 : 1e9;
            t = t.substring(0, t.length() - 1);
        }
        if (t.isEmpty() || !t.matches("\\d*\\.?\\d+|\\d+\\.")) return null;
        try {
            return Double.parseDouble(t) * factor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A number in plain digits, as it goes on one of Hypixel's signs: "4000000", "3.5". With
     * decimals, at most two; without, rounded to a whole number.
     */
    public static String plain(double value, boolean decimals) {
        if (!decimals) return Long.toString(Math.round(value));
        return java.math.BigDecimal.valueOf(Math.round(value * 100) / 100.0).stripTrailingZeros().toPlainString();
    }

    /** "950", "12.5k", "1.25M", "3.1B": short enough for a card. Below 1,000 one decimal at most. */
    public static String compact(double value) {
        double abs = Math.abs(value);
        // Each unit ends where rounding would write it as 1000 of itself: 999,700 is "1M", not "1000k".
        if (abs < 999.95) return trim(new DecimalFormat("0.#", US).format(value));
        if (abs < 999_500) return scaled(value / 1e3) + "k";
        if (abs < 999_500_000) return scaled(value / 1e6) + "M";
        return scaled(value / 1e9) + "B";
    }

    /** Three significant digits: 1.25, 12.5, 125. */
    private static String scaled(double value) {
        double abs = Math.abs(value);
        String pattern = abs < 10 ? "0.##" : abs < 100 ? "0.#" : "0";
        return trim(new DecimalFormat(pattern, US).format(value));
    }

    private static String trim(String text) {
        return text.equals("-0") ? "0" : text;
    }

    /** The full amount with thousands separators and at most one decimal: "1,500,000". */
    public static String full(double value) {
        return new DecimalFormat("#,##0.#", US).format(value);
    }

    /**
     * The seconds in a time Hypixel writes as "1d 5h", "5h 3m 12s" or "59s". -1 when nothing in it
     * reads as a time.
     */
    public static long seconds(String text) {
        if (text == null) return -1;
        java.util.regex.Matcher m = TIME_PART.matcher(text.toLowerCase(Locale.ROOT));
        long total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86_400;
                case "h" -> n * 3_600;
                case "m" -> n * 60;
                default -> n;
            };
        }
        return any ? total : -1;
    }
}
