package net.alpaka.addons.features.dungeon.puzzle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The health read from one puzzle blaze's name tag in the Higher Or Lower room.
 *
 * Hypixel shows each blaze's level, name and health on a separate armor stand above it, for
 * example {@code [Lv15]  Blaze 289/289❤}. The character between the level and "Blaze" is a
 * private-use glyph from Hypixel's resource pack (a mob icon), not a space, and it may change with
 * Hypixel's icon set; so anything may stand there, and nothing about it is assumed
 * (puzzles-entities.md 2.3 and its Critic additions A). Health may come abbreviated ("1.5k"); such a
 * value is approximate, and two blazes can then read the same.
 *
 * Pure, unit-tested in HigherOrLowerTagTest.
 *
 * @param current     health left
 * @param max         full health; the kill order goes by this, since it does not change as the blaze is hit
 * @param approximate whether either number was abbreviated
 */
public record HigherOrLowerTag(long current, long max, boolean approximate) {

    /** Formatting already stripped. The lazy {@code .*?} takes the icon glyph, whatever it is. */
    private static final Pattern TAG = Pattern.compile(
            "^\\[Lv\\d+]\\s.*?\\bBlaze\\s+([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)❤$");

    /** The tag in a name with formatting stripped, or null if the name is not a blaze health tag. */
    public static HigherOrLowerTag parse(String plain) {
        // Most names in a dungeon are something else; skip the regex for them.
        if (plain == null || !plain.endsWith("❤") || !plain.contains("Blaze")) return null;
        Matcher m = TAG.matcher(plain);
        if (!m.matches()) return null;
        Amount current = amount(m.group(1));
        Amount max = amount(m.group(2));
        if (current == null || max == null || max.value <= 0) return null;
        return new HigherOrLowerTag(current.value, max.value, current.approximate || max.approximate);
    }

    /** The blaze is dead, or as good as: its tag already reads zero. */
    public boolean dead() {
        return current <= 0;
    }

    private record Amount(long value, boolean approximate) {}

    /** "1,234" is 1234; "1.5k" is 1500 and approximate; null for anything that is not a number. */
    private static Amount amount(String text) {
        String digits = text.replace(",", "");
        long factor = 1;
        char last = digits.isEmpty() ? ' ' : Character.toLowerCase(digits.charAt(digits.length() - 1));
        switch (last) {
            case 'k' -> factor = 1_000L;
            case 'm' -> factor = 1_000_000L;
            case 'b' -> factor = 1_000_000_000L;
            default -> { }
        }
        if (factor != 1) digits = digits.substring(0, digits.length() - 1);
        if (digits.isEmpty()) return null;
        try {
            BigDecimal value = new BigDecimal(digits).multiply(BigDecimal.valueOf(factor));
            boolean rounded = value.stripTrailingZeros().scale() > 0;
            return new Amount(value.setScale(0, RoundingMode.HALF_UP).longValueExact(), factor != 1 || rounded);
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}
