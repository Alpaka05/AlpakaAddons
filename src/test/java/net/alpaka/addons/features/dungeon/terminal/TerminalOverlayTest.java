package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.features.dungeon.terminal.TerminalClicks.Verdict;
import net.alpaka.addons.features.dungeon.terminal.TerminalOverlay.Input;
import net.alpaka.addons.features.dungeon.terminal.TerminalSolution.Mark;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the vanilla chest draws and how a run of clicks is judged there (design.md 5.1, 1.4). */
class TerminalOverlayTest {

    private static final TerminalPalette PALETTE = new TerminalPalette(1, 2, 3, 4, 5, 6);

    private static Supplier<Verdict> fixed(Verdict verdict, AtomicInteger asked) {
        return () -> {
            asked.incrementAndGet();
            return verdict;
        };
    }

    @Test
    void aDragIsDecidedWhenItStarts() {
        TerminalOverlay.Sequence seq = new TerminalOverlay.Sequence();
        AtomicInteger asked = new AtomicInteger();
        assertEquals(Verdict.TOO_EARLY, seq.decide(null, Input.DRAG_START, 100L, null, fixed(Verdict.TOO_EARLY, asked)));
        // The guards would let the rest through by now; the drag still goes out whole or not at all.
        assertEquals(Verdict.TOO_EARLY, seq.decide(null, Input.DRAG_MORE, 101L, null, fixed(Verdict.ALLOW, asked)));
        assertEquals(Verdict.TOO_EARLY, seq.decide(null, Input.DRAG_MORE, 102L, null, fixed(Verdict.ALLOW, asked)));
        assertEquals(1, asked.get());

        assertEquals(Verdict.ALLOW, seq.decide(null, Input.DRAG_START, 900L, null, fixed(Verdict.ALLOW, asked)));
        // The rate guard would refuse a click 1 ms later, but not the middle of a drag.
        assertEquals(Verdict.ALLOW, seq.decide(null, Input.DRAG_MORE, 901L, null, fixed(Verdict.TOO_FAST, asked)));
        assertEquals(Verdict.ALLOW, seq.decide(null, Input.DRAG_MORE, 901L, null, fixed(Verdict.TOO_FAST, asked)));
    }

    @Test
    void aDoubleClicksCollectFollowsItsClick() {
        TerminalOverlay.Sequence seq = new TerminalOverlay.Sequence();
        AtomicInteger asked = new AtomicInteger();
        assertEquals(Verdict.WRONG, seq.decide(null, Input.CELL, 1000L, fixed(Verdict.WRONG, asked), null));
        assertEquals(Verdict.WRONG, seq.decide(null, Input.COLLECT, 1100L, null, fixed(Verdict.ALLOW, asked)),
                "never a collect for a click that was not sent");
        assertEquals(1, asked.get());
        // After a click that went out, the collect gets the guards like any click.
        assertEquals(Verdict.ALLOW, seq.decide(null, Input.CELL, 2000L, fixed(Verdict.ALLOW, asked), null));
        assertEquals(Verdict.TOO_FAST, seq.decide(null, Input.COLLECT, 2020L, null, fixed(Verdict.TOO_FAST, asked)));
        // Long after a refused click, a collect is judged on its own.
        seq.decide(null, Input.CELL, 3000L, fixed(Verdict.WRONG, asked), null);
        assertEquals(Verdict.ALLOW, seq.decide(null, Input.COLLECT, 3600L, null, fixed(Verdict.ALLOW, asked)));
    }

    @Test
    void otherClicksGetTheGuardsOnly() {
        TerminalOverlay.Sequence seq = new TerminalOverlay.Sequence();
        AtomicInteger cells = new AtomicInteger();
        AtomicInteger guards = new AtomicInteger();
        assertEquals(Verdict.TOO_FAST, seq.decide(null, Input.OTHER, 0L, fixed(Verdict.WRONG, cells), fixed(Verdict.TOO_FAST, guards)));
        assertEquals(0, cells.get());
        assertEquals(1, guards.get());
    }

    @Test
    void hideOtherItems() {
        Chests chest = Chests.rows(5);
        for (int slot : TerminalKind.PANES.playSlots()) chest.pane(slot, "lime");
        chest.pane(11, "red");
        TerminalSolution panes = TerminalSolver.solve(TerminalKind.PANES, "Correct all the panes!", chest.list(),
                new TerminalSession.State());
        assertFalse(TerminalOverlay.hidden(panes, 11), "to click");
        assertFalse(TerminalOverlay.hidden(panes, 12), "done, still shown");
        assertTrue(TerminalOverlay.hidden(panes, 0), "filler");
        // Nothing is hidden before the chest is loaded.
        TerminalSolution loading = TerminalSolver.solve(TerminalKind.PANES, "Correct all the panes!",
                chest.clear(33).list(), new TerminalSession.State());
        assertFalse(TerminalOverlay.hidden(loading, 0));

        // Click the Button on Time keeps every pane the player times the click by.
        Chests melody = Chests.rows(6);
        melody.pane(3, "magenta").pane(39, "magenta");
        for (int col = 1; col <= 5; col++) {
            melody.pane(9 + col, "white").pane(18 + col, col == 2 ? "lime" : "red").pane(27 + col, "white");
        }
        melody.put(16, "red_terracotta", "").put(25, "lime_terracotta", "").put(34, "red_terracotta", "");
        TerminalSolution m = TerminalSolver.solve(TerminalKind.MELODY, "Click the button on time!", melody.list(),
                new TerminalSession.State());
        assertTrue(m.loaded());
        for (int slot : new int[]{3, 10, 20, 21, 32, 16, 34, 39, 2}) assertFalse(TerminalOverlay.hidden(m, slot), "slot " + slot);
        for (int slot : new int[]{0, 8, 15, 17, 45, 53}) assertTrue(TerminalOverlay.hidden(m, slot), "slot " + slot);
    }

    @Test
    void cellColors() {
        TerminalSolution numbers = TerminalSolution.builder(TerminalKind.NUMBERS)
                .mark(11, Mark.CLICK).mark(12, Mark.SECOND).mark(13, Mark.THIRD).mark(14, Mark.DONE).loaded(true).build();
        assertEquals(1, PALETTE.fill(numbers, 11));
        assertEquals(2, PALETTE.fill(numbers, 12));
        assertEquals(3, PALETTE.fill(numbers, 13));
        assertEquals(0, PALETTE.fill(numbers, 14));
        assertEquals(0, PALETTE.fill(numbers, 0));

        TerminalSolution rubix = TerminalSolution.builder(TerminalKind.RUBIX)
                .mark(12, Mark.CLICK).clicks(12, 2).mark(13, Mark.CLICK).clicks(13, -1).mark(14, Mark.DONE).loaded(true).build();
        assertEquals(4, PALETTE.fill(rubix, 12), "left");
        assertEquals(5, PALETTE.fill(rubix, 13), "right");
        assertEquals(0, PALETTE.fill(rubix, 14));

        TerminalSolution melody = TerminalSolution.builder(TerminalKind.MELODY)
                .melody(3, 5, 2, 25).mark(25, Mark.CLICK).loaded(true).build();
        assertEquals(1, PALETTE.fill(melody, 25), "the button");
        assertEquals(6, PALETTE.fill(melody, 23), "the moving pane");
        assertEquals(6, PALETTE.fill(melody, 39), "the target column");
        assertEquals(0, PALETTE.fill(melody, 16), "another row's button");
    }
}
