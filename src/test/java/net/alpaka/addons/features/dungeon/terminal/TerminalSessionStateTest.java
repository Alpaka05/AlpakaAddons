package net.alpaka.addons.features.dungeon.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pending clicks and the server's answers to them (design.md 4.7, compliance.md T5). */
class TerminalSessionStateTest {

    private static TerminalSession.Pending click(int slot, int button, long atMs, TerminalItem before) {
        return new TerminalSession.Pending(slot, button, atMs, null, before);
    }

    @Test
    void pendingUntilAnsweredOldestFirst() {
        TerminalItem red = TerminalItem.of(12, "red_stained_glass_pane", "", 1, false);
        TerminalItem orange = TerminalItem.of(12, "orange_stained_glass_pane", "", 1, false);
        TerminalItem yellow = TerminalItem.of(12, "yellow_stained_glass_pane", "", 1, false);
        TerminalSession.State state = new TerminalSession.State();
        state.add(click(12, 0, 100L, red));
        state.add(click(12, 0, 150L, red));
        state.add(click(20, 1, 160L, TerminalItem.empty(20)));
        assertEquals(List.of(12, 20), List.copyOf(state.pending()));
        assertEquals(2, state.pendingCount(12));
        assertEquals(2, state.pendingSteps(12));
        assertEquals(-1, state.pendingSteps(20));
        assertEquals(List.of(12, 20), List.copyOf(state.clicked()));

        long version = state.version();
        assertTrue(state.answer(12, orange));
        assertTrue(state.version() > version);
        assertEquals(1, state.pendingCount(12));
        assertEquals(orange, state.confirmedAs(12));
        // The click still waiting starts from the answer, so the chest reads as the server has it now.
        assertEquals(orange, state.firstPending(12).item());
        assertTrue(state.answer(12, yellow));
        assertFalse(state.isPending(12));
        assertEquals(yellow, state.confirmedAs(12));
        assertFalse(state.answer(12, yellow), "nothing left to answer there");
        assertTrue(state.isPending(20));
    }

    @Test
    void unansweredClicksExpire() {
        TerminalSession.State state = new TerminalSession.State().withPending(11, 1000L).withPending(13, 1300L);
        assertFalse(state.expire(1600L), "600 ms is still in time");
        assertTrue(state.isPending(11));
        assertTrue(state.expire(1601L));
        assertFalse(state.isPending(11));
        assertNull(state.confirmedAs(11), "dropped, not confirmed");
        assertTrue(state.isPending(13));
        assertTrue(state.expire(1901L));
        assertTrue(state.pending().isEmpty());
    }

    @Test
    void throttleNoticeDropsPendingAndRecentAnswers() {
        TerminalItem star = TerminalItem.of(25, "nether_star", "Nether Star", 1, true);
        TerminalItem book = TerminalItem.of(26, "enchanted_book", "Enchanted Book", 1, true);
        TerminalSession.State state = new TerminalSession.State();
        state.add(click(25, 0, 1000L, star));
        state.answer(25, star);
        state.add(click(26, 0, 3000L, book));
        state.answer(26, book);
        state.add(click(27, 0, 3100L, TerminalItem.empty(27)));
        // Throttled at 3200: the click at 3000 was ignored even though its slot came back.
        state.throttled(3200L);
        assertTrue(state.pending().isEmpty());
        assertEquals(star, state.confirmedAs(25), "an older answer stands");
        assertNull(state.confirmedAs(26));
        assertTrue(state.clicked().contains(26), "it was still clicked");
    }

    @Test
    void lockingTheRubixTargetIsAChange() {
        TerminalSession.State state = new TerminalSession.State();
        long version = state.version();
        state.lockRubixTarget(2, 0b111L);
        assertEquals(2, state.rubixTarget());
        assertEquals(0b111L, state.rubixSlots());
        assertTrue(state.version() > version);
        version = state.version();
        state.lockRubixTarget(2, 0b111L);
        assertEquals(version, state.version(), "the same lock again changes nothing");
        state.lockRubixTarget(3);
        assertEquals(3, state.rubixTarget());
        assertEquals(0b111L, state.rubixSlots());
    }

    @Test
    void clearPending() {
        TerminalSession.State state = new TerminalSession.State().withPending(11, 0L);
        long version = state.version();
        state.clearPending();
        assertTrue(state.pending().isEmpty());
        assertTrue(state.version() > version);
        version = state.version();
        state.clearPending();
        assertEquals(version, state.version());
    }
}
