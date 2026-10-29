package net.alpaka.addons.features.dungeon.puzzle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which blaze of the Higher Or Lower room dies next.
 *
 * The room comes in two builds that differ only in height. The player enters at door level, Y 69.
 * Where the shaft goes up and the blazes float above that level, the lowest health dies first;
 * where it goes down and they float below it, the highest health dies first. Odin and Skyblocker
 * agree on this rule and the wiki says the same in its own words (puzzles-entities.md 2.4), so the
 * blazes' height decides it and no room data is needed. If they are not all on one side of Y 69,
 * something else is going on, and nothing is shown rather than a guess that fails the puzzle.
 *
 * The next kill is always the extreme of the blazes still alive, whoever killed the others and
 * however late the player arrived, so nothing about earlier kills has to be remembered.
 *
 * Pure, unit-tested in HigherOrLowerOrderTest.
 */
public final class HigherOrLowerOrder {

    /** The height of the room's doors; the blazes are all above it or all below it. */
    public static final double DOOR_Y = 69.0;

    /** Which end of the health range dies first. */
    public enum Order {
        /** Blazes above the doors. */
        LOWEST_FIRST,
        /** Blazes below the doors. */
        HIGHEST_FIRST
    }

    /** One living blaze: something to find it by again, and its full health. */
    public record Entry<T>(T key, long max) {}

    /**
     * The next kill and the one after it. Normally one blaze each; when blazes read the same health
     * (abbreviated numbers) the spot holds all of them, and nothing after it is known.
     *
     * @param next  the blazes that may be next; empty when none is alive
     * @param after the blazes that may come after it; empty unless the next one is certain
     */
    public record Plan<T>(List<Entry<T>> next, List<Entry<T>> after) {

        /** Exactly one blaze is next. */
        public boolean nextCertain() {
            return next.size() == 1;
        }

        /** Exactly one blaze is next and exactly one comes after it. */
        public boolean afterCertain() {
            return next.size() == 1 && after.size() == 1;
        }
    }

    private HigherOrLowerOrder() {}

    /**
     * The order from the heights of the blazes' tags: null with fewer than two, or when they are not
     * all on the same side of {@link #DOOR_Y}.
     */
    public static Order decide(List<Double> ys) {
        if (ys.size() < 2) return null;
        int above = 0;
        int below = 0;
        for (double y : ys) {
            if (y > DOOR_Y) above++;
            else if (y < DOOR_Y) below++;
        }
        if (above == ys.size()) return Order.LOWEST_FIRST;
        if (below == ys.size()) return Order.HIGHEST_FIRST;
        return null;
    }

    /** The next kill and the one after, among the blazes still alive. */
    public static <T> Plan<T> plan(List<Entry<T>> alive, Order order) {
        List<Entry<T>> sorted = new ArrayList<>(alive);
        Comparator<Entry<T>> byMax = Comparator.comparingLong(Entry::max);
        sorted.sort(order == Order.HIGHEST_FIRST ? byMax.reversed() : byMax);
        List<Entry<T>> next = group(sorted, 0);
        List<Entry<T>> after = next.size() == 1 ? group(sorted, 1) : List.of();
        return new Plan<>(next, after);
    }

    /** The entries from {@code from} on that share its health. */
    private static <T> List<Entry<T>> group(List<Entry<T>> sorted, int from) {
        if (from >= sorted.size()) return List.of();
        long max = sorted.get(from).max();
        List<Entry<T>> out = new ArrayList<>();
        for (int i = from; i < sorted.size() && sorted.get(i).max() == max; i++) out.add(sorted.get(i));
        return List.copyOf(out);
    }
}
