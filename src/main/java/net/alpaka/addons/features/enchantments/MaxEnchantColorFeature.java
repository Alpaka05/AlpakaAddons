package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enchantments at their highest level get a color of their own in item tooltips, so a maxed
 * "Sharpness VII" stands out from the blue "Sharpness V" next to it.
 *
 * Hypixel writes the enchantments into the lore as plain text, either several to a line
 * ("Sharpness VII, Critical VII, Ender Slayer VII") or one per line with its description below.
 * Only a name that opens a line or follows ", " and is followed by its level counts, so a
 * description mentioning "Sharpness" is left alone. Stacking enchantments carry their counter
 * behind the level ("Expertise X 15k"), which keeps its own color.
 */
public final class MaxEnchantColorFeature {

    private MaxEnchantColorFeature() {
    }

    private static final Pattern NORMAL_PATTERN = pattern(Enchants.NORMAL.keySet().stream());
    private static final Pattern ALL_PATTERN = pattern(
            Stream.concat(Enchants.NORMAL.keySet().stream(), Enchants.ULTIMATE.keySet().stream()));

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            AlpakaConfig config = AlpakaConfig.instance;
            if (!config.maxEnchantColorEnabled) return;
            TextColor color = TextColor.fromRgb(config.maxEnchantColor & 0xFFFFFF);
            // Line 0 is the item's name.
            for (int i = 1; i < lines.size(); i++) {
                lines.set(i, recolor(lines.get(i), color, config.maxEnchantColorUltimate));
            }
        });
    }

    /** Longest names first, so "Luck of the Sea" wins over "Luck". */
    private static Pattern pattern(Stream<String> names) {
        String alternation = names.sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?:^|(?<=, ))(" + alternation + ") ([IVXL]+|\\d+)(?=$|[, ])");
    }

    /** The line with every maxed enchantment in {@code color}, or the line itself if it has none. */
    static Component recolor(Component line, TextColor color, boolean ultimates) {
        String plain = ChatFormatting.stripFormatting(line.getString());
        List<int[]> ranges = maxedRanges(plain, ultimates);
        if (ranges.isEmpty()) return line;

        // Flatten into one style per character, resolving legacy § codes inside the text, so the
        // ranges found on the plain text line up however Hypixel split the line.
        StringBuilder text = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        line.visit((style, part) -> {
            Style current = style;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c == '§' && i + 1 < part.length()) {
                    ChatFormatting format = ChatFormatting.getByCode(part.charAt(i + 1));
                    if (format == ChatFormatting.RESET) current = style;
                    else if (format != null) current = current.applyLegacyFormat(format);
                    i++;
                    continue;
                }
                text.append(c);
                styles.add(current);
            }
            return Optional.empty();
        }, Style.EMPTY);

        // A stray § the strip above left in place would shift the ranges; find them again then.
        if (!text.toString().equals(plain)) ranges = maxedRanges(text.toString(), ultimates);
        if (ranges.isEmpty()) return line;
        for (int[] range : ranges) {
            for (int i = range[0]; i < range[1]; i++) styles.set(i, styles.get(i).withColor(color));
        }

        MutableComponent out = Component.empty();
        int start = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (i == text.length() || !styles.get(i).equals(styles.get(start))) {
                out.append(Component.literal(text.substring(start, i)).withStyle(styles.get(start)));
                start = i;
            }
        }
        return out;
    }

    /** Start and end of each "Name Level" in a plain lore line whose level is the highest there is. */
    static List<int[]> maxedRanges(String plain, boolean ultimates) {
        List<int[]> ranges = new ArrayList<>();
        Matcher m = (ultimates ? ALL_PATTERN : NORMAL_PATTERN).matcher(plain);
        while (m.find()) {
            Integer max = Enchants.NORMAL.get(m.group(1));
            if (max == null) max = Enchants.ULTIMATE.get(m.group(1));
            if (max != null && level(m.group(2)) >= max) ranges.add(new int[]{m.start(), m.end()});
        }
        return ranges;
    }

    /** A level written as a Roman numeral (Hypixel's default) or as digits; 0 if neither. */
    static int level(String text) {
        if (Character.isDigit(text.charAt(0))) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        int total = 0;
        int previous = 0;
        for (int i = text.length() - 1; i >= 0; i--) {
            int value = switch (text.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                default -> 0;
            };
            total += value < previous ? -value : value;
            previous = Math.max(previous, value);
        }
        return total;
    }
}
