package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.Chroma;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.concurrent.ConcurrentHashMap;
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

    /** A lore line as read: its plain text and the maxed enchantments found in it. */
    private record Found(String plain, List<int[]> ranges) {
    }

    /**
     * Results by the line's text, one map per Ultimates setting. A tooltip is built again every frame
     * from the same lore, and the stripping and the long pattern are the costly part; the same lines
     * keep coming, so they are looked for once. Cleared when full rather than kept in order, which
     * is enough for a hovered item's few dozen lines. Concurrent, as other mods build tooltips off
     * the render thread too.
     */
    private static final int CACHE_LIMIT = 1024;
    private static final Map<String, Found> FOUND = new ConcurrentHashMap<>();
    private static final Map<String, Found> FOUND_ULTIMATES = new ConcurrentHashMap<>();

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            AlpakaConfig config = AlpakaConfig.instance;
            if (!config.maxEnchantColorEnabled) return;
            boolean ultimates = config.maxEnchantColorUltimate;
            // Line 0 is the item's name.
            Chroma chroma = config.maxEnchantChroma;
            if (chroma != null && chroma.enabled) {
                // The tooltip is built again every frame, so the hues move along by themselves.
                double shift = chroma.shift(System.currentTimeMillis());
                for (int i = 1; i < lines.size(); i++) {
                    int line = i;
                    lines.set(i, recolor(lines.get(i), ultimates, at -> chroma(chroma, shift, line, at), true));
                }
                return;
            }
            TextColor color = TextColor.fromRgb(config.maxEnchantColor & 0xFFFFFF);
            for (int i = 1; i < lines.size(); i++) {
                lines.set(i, recolor(lines.get(i), color, ultimates));
            }
        });
    }

    /** Each line starts this many letters further on, so the rainbow's bands run diagonally. */
    private static final double CHROMA_LINE_OFFSET = 2.0;

    /** The chroma color of letter {@code at} on tooltip line {@code line}. */
    static TextColor chroma(Chroma chroma, double shift, int line, int at) {
        return TextColor.fromRgb(chroma.rgb(shift, at + line * CHROMA_LINE_OFFSET));
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
        return recolor(line, ultimates, at -> color, false);
    }

    /**
     * The line with every maxed enchantment colored by {@code colorAt}, which gets the character's
     * index in the line; {@code perChar} asks it for every character instead of once per stretch.
     */
    static Component recolor(Component line, boolean ultimates, IntFunction<TextColor> colorAt, boolean perChar) {
        Found found = find(line.getString(), ultimates);
        String plain = found.plain();
        List<int[]> ranges = found.ranges();
        if (ranges.isEmpty()) return line;

        // Flatten into runs of one style each, resolving legacy § codes inside the text, so the
        // ranges found on the plain text line up however Hypixel split the line. Runs rather than a
        // style per character: the output only depends on where the style changes.
        StringBuilder text = new StringBuilder();
        List<Run> runs = new ArrayList<>();
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
                int at = text.length();
                text.append(c);
                Run last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
                if (last != null && last.end == at && last.style.equals(current)) last.end = at + 1;
                else runs.add(new Run(at, at + 1, current));
            }
            return Optional.empty();
        }, Style.EMPTY);

        String flat = text.toString();
        // A stray § the strip above left in place would shift the ranges; find them again then.
        if (!flat.equals(plain)) ranges = maxedRanges(flat, ultimates);
        if (ranges.isEmpty()) return line;

        // Cut the runs at the range edges, color the parts inside a range, and join neighbours that
        // end up with the same style: the same pieces the per-character version produced.
        MutableComponent out = Component.empty();
        int pendingStart = 0;
        Style pending = null;
        int r = 0;
        for (Run run : runs) {
            int at = run.start;
            while (at < run.end) {
                while (r < ranges.size() && ranges.get(r)[1] <= at) r++;
                boolean inside = r < ranges.size() && ranges.get(r)[0] <= at;
                int to = inside ? (perChar ? at + 1 : Math.min(run.end, ranges.get(r)[1]))
                        : r < ranges.size() ? Math.min(run.end, ranges.get(r)[0]) : run.end;
                Style style = inside ? run.style.withColor(colorAt.apply(at)) : run.style;
                if (pending == null) {
                    pending = style;
                    pendingStart = at;
                } else if (!pending.equals(style)) {
                    out.append(Component.literal(flat.substring(pendingStart, at)).withStyle(pending));
                    pending = style;
                    pendingStart = at;
                }
                at = to;
            }
        }
        if (pending != null) out.append(Component.literal(flat.substring(pendingStart)).withStyle(pending));
        return out;
    }

    /** Characters {@code start..end} of the flattened line, all in one style. */
    private static final class Run {
        final int start;
        int end;
        final Style style;

        Run(int start, int end, Style style) {
            this.start = start;
            this.end = end;
            this.style = style;
        }
    }

    /** The plain text of a line and its maxed ranges, worked out once per distinct line. */
    private static Found find(String raw, boolean ultimates) {
        Map<String, Found> cache = ultimates ? FOUND_ULTIMATES : FOUND;
        Found found = cache.get(raw);
        if (found != null) return found;
        String plain = ChatFormatting.stripFormatting(raw);
        List<int[]> ranges = maxedRanges(plain, ultimates);
        found = new Found(plain, ranges.isEmpty() ? List.of() : ranges);
        if (cache.size() >= CACHE_LIMIT) cache.clear();
        cache.put(raw, found);
        return found;
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
