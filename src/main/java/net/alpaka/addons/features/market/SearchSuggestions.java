package net.alpaka.addons.features.market;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which item names to offer under a search while it is typed, best first.
 *
 * A name that starts with what was typed comes first, then one where every typed word starts a
 * word of the name ("dark clay" for Dark Claymore), then one whose initials it is ("aotd" for
 * Aspect of the Dragons, as players write it), then one that merely contains it. Within each, the
 * shorter name first: it is the closer match.
 */
public final class SearchSuggestions {
    private SearchSuggestions() {}

    /**
     * A name and the stretches of it that matched, for the dropdown to light up: pairs of start and
     * end, in order, not overlapping.
     */
    public record Match(String name, int[] ranges) {}

    public static List<Match> rank(String query, Collection<String> names, int limit) {
        String q = normalize(query);
        if (q.isEmpty() || limit <= 0) return List.of();
        String[] tokens = q.split(" ");
        String compact = q.replace(" ", "");

        record Scored(Match match, int score) {}
        List<Scored> scored = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String n = name.toLowerCase(Locale.ROOT);
            if (!seen.add(n)) continue;
            int score;
            int[] ranges;
            int at = n.indexOf(q);
            if (n.equals(q) || at == 0) {
                score = n.equals(q) ? 0 : 1;
                ranges = new int[] {0, q.length()};
            } else if (wordPrefixes(n, tokens)) {
                score = 2;
                ranges = tokenRanges(n, tokens);
            } else if (compact.length() >= 2 && initials(n).startsWith(compact)) {
                score = 3;
                ranges = initialRanges(n, compact.length());
            } else if (at > 0) {
                score = 4;
                ranges = new int[] {at, at + q.length()};
            } else {
                continue;
            }
            scored.add(new Scored(new Match(name, ranges), score));
        }
        scored.sort(Comparator.comparingInt(Scored::score)
                .thenComparingInt(s -> s.match().name().length())
                .thenComparing(s -> s.match().name(), String.CASE_INSENSITIVE_ORDER));
        List<Match> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) out.add(scored.get(i).match());
        return out;
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Whether every typed word starts some word of the name. */
    private static boolean wordPrefixes(String name, String[] tokens) {
        for (String token : tokens) {
            if (wordStart(name, token) < 0) return false;
        }
        return true;
    }

    /** Where a word of the name starting with the token begins, or -1. */
    private static int wordStart(String name, String token) {
        int from = 0;
        while (from <= name.length()) {
            int at = name.indexOf(token, from);
            if (at < 0) return -1;
            if (at == 0 || !Character.isLetterOrDigit(name.charAt(at - 1))) return at;
            from = at + 1;
        }
        return -1;
    }

    /** Where each typed word starts a word of the name, in order along the name. */
    private static int[] tokenRanges(String name, String[] tokens) {
        List<int[]> spans = new ArrayList<>();
        for (String token : tokens) {
            int at = wordStart(name, token);
            if (at >= 0) spans.add(new int[] {at, at + token.length()});
        }
        spans.sort(Comparator.comparingInt(a -> a[0]));
        List<Integer> flat = new ArrayList<>();
        int end = -1;
        for (int[] span : spans) {
            if (span[0] < end) continue;
            flat.add(span[0]);
            flat.add(span[1]);
            end = span[1];
        }
        return flat.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The first letters of the first {@code count} words. */
    private static int[] initialRanges(String name, int count) {
        List<Integer> flat = new ArrayList<>();
        boolean wordStart = true;
        for (int i = 0; i < name.length() && flat.size() < count * 2; i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (wordStart) {
                    flat.add(i);
                    flat.add(i + 1);
                }
                wordStart = false;
            } else if (c == ' ' || c == '-') {
                wordStart = true;
            }
        }
        return flat.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The first letter of each word: "aspect of the dragons" is "aotd". */
    static String initials(String name) {
        StringBuilder out = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (wordStart) out.append(c);
                wordStart = false;
            } else if (c == ' ' || c == '-') {
                wordStart = true;
            }
        }
        return out.toString();
    }
}
