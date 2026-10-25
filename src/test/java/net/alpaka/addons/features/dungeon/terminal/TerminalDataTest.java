package net.alpaka.addons.features.dungeon.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plain data the terminal solvers share: items, solutions and the clamped settings. */
class TerminalDataTest {

    @Test
    void dyeColorsByItemId() {
        assertEquals("lime", TerminalItem.dyeColorOf("lime_stained_glass_pane"));
        assertEquals("red", TerminalItem.dyeColorOf("red_stained_glass"));
        assertEquals("light_blue", TerminalItem.dyeColorOf("light_blue_wool"));
        assertEquals("blue", TerminalItem.dyeColorOf("blue_terracotta"));
        assertEquals("light_gray", TerminalItem.dyeColorOf("light_gray_dye"));
        assertEquals("green", TerminalItem.dyeColorOf("green_concrete"));
        // The 1.8 dyes that are items of their own now.
        assertEquals("white", TerminalItem.dyeColorOf("bone_meal"));
        assertEquals("blue", TerminalItem.dyeColorOf("lapis_lazuli"));
        assertEquals("brown", TerminalItem.dyeColorOf("cocoa_beans"));
        assertEquals("black", TerminalItem.dyeColorOf("ink_sac"));
        // Not dyed, even though the id starts with a color word.
        assertNull(TerminalItem.dyeColorOf("redstone"));
        assertNull(TerminalItem.dyeColorOf("red_mushroom"));
        assertNull(TerminalItem.dyeColorOf("blackstone"));
        assertNull(TerminalItem.dyeColorOf("red_glazed_terracotta"));
        assertNull(TerminalItem.dyeColorOf("terracotta"));
        assertNull(TerminalItem.dyeColorOf(null));
    }

    @Test
    void itemsAndFiller() {
        TerminalItem filler = TerminalItem.of(0, "black_stained_glass_pane", "", 1, false);
        assertTrue(filler.filler());
        assertFalse(filler.empty());
        assertFalse(TerminalItem.of(3, "black_stained_glass_pane", "Black Stained Glass Pane", 1, false).filler());
        assertTrue(TerminalItem.empty(5).empty());
        assertEquals("lime", TerminalItem.of(11, "lime_stained_glass_pane", "", 3, false).dyeColor());
    }

    @Test
    void anEmptySolutionMarksNothingAndRefusesNothing() {
        TerminalSolution solution = TerminalSolution.empty(TerminalKind.PANES);
        assertFalse(solution.loaded());
        assertFalse(solution.solved());
        assertEquals(TerminalSolution.Mark.OTHER, solution.mark(11));
        assertEquals(TerminalSolution.Mark.OTHER, solution.mark(-1));
        assertEquals(TerminalSolution.Mark.OTHER, solution.mark(99));
        // Not loaded: when in doubt, a click is never wrong.
        assertFalse(solution.wrongClick(11, 0));
        assertEquals(-1, solution.buttonSlot());
        assertFalse(solution.aligned());
    }

    @Test
    void wrongClicks() {
        TerminalSolution panes = TerminalSolution.builder(TerminalKind.PANES)
                .mark(11, TerminalSolution.Mark.CLICK).mark(12, TerminalSolution.Mark.DONE).loaded(true).build();
        assertFalse(panes.wrongClick(11, 0));
        assertFalse(panes.wrongClick(11, 1));
        assertTrue(panes.wrongClick(12, 0));
        assertTrue(panes.wrongClick(0, 0));

        TerminalSolution numbers = TerminalSolution.builder(TerminalKind.NUMBERS)
                .mark(11, TerminalSolution.Mark.CLICK).mark(12, TerminalSolution.Mark.SECOND).label(11, "1").loaded(true).build();
        assertFalse(numbers.wrongClick(11, 0));
        assertTrue(numbers.wrongClick(12, 0));
        assertEquals("1", numbers.label(11));

        TerminalSolution rubix = TerminalSolution.builder(TerminalKind.RUBIX)
                .clicks(12, 2).clicks(13, -1).clicks(14, 0).loaded(true).build();
        assertFalse(rubix.wrongClick(12, 0));
        assertTrue(rubix.wrongClick(12, 1));
        assertFalse(rubix.wrongClick(13, 1));
        assertTrue(rubix.wrongClick(13, 0));
        assertTrue(rubix.wrongClick(14, 0));
        assertEquals(-1, rubix.clicks(13));

        // Never for Click the Button on Time, whatever is marked.
        TerminalSolution melody = TerminalSolution.builder(TerminalKind.MELODY)
                .melody(3, 3, 1, 16).loaded(true).build();
        assertFalse(melody.wrongClick(0, 0));
        assertFalse(melody.wrongClick(16, 0));
        assertTrue(melody.aligned());
        assertEquals(16, melody.buttonSlot());
    }

    @Test
    void settingsAreClamped() {
        TerminalSettings low = new TerminalSettings(true, 10, true, false, false, false, false, 0.2f);
        assertEquals(350, low.firstClickMs());
        assertEquals(7, low.firstClickTicks());
        assertEquals(1.0f, low.guiScale());
        TerminalSettings high = new TerminalSettings(true, 5000, true, false, false, false, false, 9.0f);
        assertEquals(800, high.firstClickMs());
        assertEquals(16, high.firstClickTicks());
        assertEquals(3.0f, high.guiScale());
        TerminalSettings usual = new TerminalSettings(true, 500, true, false, false, false, false, Float.NaN);
        assertEquals(10, usual.firstClickTicks());
        assertEquals(1.5f, usual.guiScale());
    }

    @Test
    void sessionStateForTests() {
        TerminalSession.State state = new TerminalSession.State().withClicked(11).withPending(12, 100L).withPredict(true);
        assertTrue(state.clicked().contains(11));
        assertTrue(state.isPending(12));
        assertTrue(state.predict());
        assertEquals(-1, state.rubixTarget());
        long version = state.version();
        state.lockRubixTarget(2);
        assertEquals(2, state.rubixTarget());
        assertTrue(state.version() > version);
        // An empty chest solves to nothing, for any state.
        TerminalSolution nothing = TerminalSolver.solve(TerminalKind.PANES, "Correct all the panes!", java.util.List.of(), state);
        assertEquals(TerminalKind.PANES, nothing.kind());
        assertFalse(nothing.loaded());
        assertEquals(TerminalSolution.Mark.OTHER, nothing.mark(11));
    }
}
