package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.HigherOrLowerOrder.Entry;
import net.alpaka.addons.features.dungeon.puzzle.HigherOrLowerOrder.Order;
import net.alpaka.addons.features.dungeon.puzzle.HigherOrLowerOrder.Plan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The kill order of Higher Or Lower (puzzles-entities.md 2.4, 2.6 and the fixtures in 2.9). */
class HigherOrLowerOrderTest {

    private static final long[] MAX_HP = {167, 233, 300, 412, 478, 512, 600, 689, 744, 811};

    /** Each blaze keyed by its own health, which keeps the assertions readable. */
    private static List<Entry<Long>> blazes(long... maxHp) {
        List<Entry<Long>> out = new ArrayList<>();
        for (long hp : maxHp) out.add(new Entry<>(hp, hp));
        // The order the world hands them over in is no order at all.
        Collections.shuffle(out, new java.util.Random(7));
        return out;
    }

    private static List<Double> ys(double... ys) {
        List<Double> out = new ArrayList<>();
        for (double y : ys) out.add(y);
        return out;
    }

    private static long only(List<Entry<Long>> spot) {
        assertEquals(1, spot.size(), "one blaze in this spot");
        return spot.getFirst().key();
    }

    @Test
    void blazesAboveTheDoorsGoLowestFirst() {
        assertEquals(Order.LOWEST_FIRST, HigherOrLowerOrder.decide(ys(75, 79, 83, 87, 91, 95, 99, 103, 107, 111)));
        assertEquals(Order.LOWEST_FIRST, HigherOrLowerOrder.decide(ys(111, 75, 99, 83, 107, 91, 79, 103, 87, 95)));
        Plan<Long> plan = HigherOrLowerOrder.plan(blazes(MAX_HP), Order.LOWEST_FIRST);
        assertEquals(167, only(plan.next()));
        assertEquals(233, only(plan.after()));
        assertTrue(plan.afterCertain());
    }

    @Test
    void blazesBelowTheDoorsGoHighestFirst() {
        assertEquals(Order.HIGHEST_FIRST, HigherOrLowerOrder.decide(ys(64, 60, 56, 52, 48, 44, 40, 36, 32, 28)));
        Plan<Long> plan = HigherOrLowerOrder.plan(blazes(MAX_HP), Order.HIGHEST_FIRST);
        assertEquals(811, only(plan.next()));
        assertEquals(744, only(plan.after()));
    }

    @Test
    void aPartlySolvedRoomCarriesOnFromWhatIsLeft() {
        Plan<Long> up = HigherOrLowerOrder.plan(blazes(412, 600, 811), Order.LOWEST_FIRST);
        assertEquals(412, only(up.next()));
        assertEquals(600, only(up.after()));
        Plan<Long> down = HigherOrLowerOrder.plan(blazes(167, 300), Order.HIGHEST_FIRST);
        assertEquals(300, only(down.next()));
        assertEquals(167, only(down.after()));
    }

    @Test
    void theLastBlazeHasNothingAfterIt() {
        Plan<Long> plan = HigherOrLowerOrder.plan(blazes(512), Order.LOWEST_FIRST);
        assertEquals(512, only(plan.next()));
        assertTrue(plan.after().isEmpty());
        assertFalse(plan.afterCertain());
        Plan<Long> none = HigherOrLowerOrder.plan(List.of(), Order.LOWEST_FIRST);
        assertTrue(none.next().isEmpty());
        assertFalse(none.nextCertain());
    }

    @Test
    void blazesOnBothSidesOrTooFewDecideNothing() {
        // Median exactly at the doors.
        assertNull(HigherOrLowerOrder.decide(ys(65, 66, 72, 73)));
        // Mostly above, but not all: no guess that could fail the puzzle.
        assertNull(HigherOrLowerOrder.decide(ys(65, 80, 84, 88, 92)));
        assertNull(HigherOrLowerOrder.decide(ys(69, 80)));
        assertNull(HigherOrLowerOrder.decide(ys(80)));
        assertNull(HigherOrLowerOrder.decide(ys()));
        assertEquals(Order.LOWEST_FIRST, HigherOrLowerOrder.decide(ys(80, 84)));
    }

    @Test
    void equalHealthAtTheFrontLeavesTheNextKillOpen() {
        // Abbreviated tags: two blazes read 1.5k. Neither may be called next.
        List<Entry<Long>> alive = List.of(new Entry<>(1L, 1500), new Entry<>(2L, 1500), new Entry<>(3L, 2000));
        Plan<Long> plan = HigherOrLowerOrder.plan(alive, Order.LOWEST_FIRST);
        assertFalse(plan.nextCertain());
        assertEquals(Set.of(1L, 2L), plan.next().stream().map(Entry::key).collect(Collectors.toSet()));
        assertTrue(plan.after().isEmpty(), "nothing is known after an open spot");
    }

    @Test
    void equalHealthInSecondPlaceLeavesOnlyThatOpen() {
        List<Entry<Long>> alive = List.of(new Entry<>(1L, 1000), new Entry<>(2L, 1500), new Entry<>(3L, 1500));
        Plan<Long> plan = HigherOrLowerOrder.plan(alive, Order.LOWEST_FIRST);
        assertTrue(plan.nextCertain());
        assertEquals(1L, only(plan.next()));
        assertFalse(plan.afterCertain());
        assertEquals(Set.of(2L, 3L), plan.after().stream().map(Entry::key).collect(Collectors.toSet()));
    }
}
