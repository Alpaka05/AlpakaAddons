package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.features.dungeon.terminal.TerminalSolution.Mark;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The practice terminals: dealt the way Hypixel deals them, and solvable by following the solver. */
class TerminalPracticeBoardTest {

    /** A few items for every letter What Starts With asks for, and some that start with none of them. */
    private static final List<TerminalPracticeBoard.Choice> ITEMS = items(
            "apple", "arrow", "bone", "book", "coal", "cobblestone", "diamond", "dirt", "gold_ingot", "gravel",
            "melon", "minecart", "name_tag", "nether_star", "redstone", "rail", "sand", "stick", "torch", "tnt",
            "wheat", "water_bucket", "egg", "feather", "iron_ingot", "lead", "paper", "quartz");

    private static List<TerminalPracticeBoard.Choice> items(String... ids) {
        List<TerminalPracticeBoard.Choice> list = new ArrayList<>();
        for (String id : ids) list.add(new TerminalPracticeBoard.Choice(id, TerminalPracticeBoard.words(id)));
        return list;
    }

    private static TerminalPracticeBoard deal(TerminalKind kind, long seed) {
        return TerminalPracticeBoard.create(kind, new Random(seed), ITEMS, 0L);
    }

    @Test
    void everyTerminalIsRecognisedByItsTitle() {
        for (TerminalKind kind : TerminalKind.values()) {
            for (long seed = 0; seed < 50; seed++) {
                TerminalPracticeBoard board = deal(kind, seed);
                assertEquals(kind, TerminalKind.byTitle(board.title()), board.title());
                assertEquals(kind.rows() * 9, board.size());
                assertFalse(board.solved(), kind + " dealt solved, seed " + seed);
                // The solver's color for Select All must be one it knows, SILVER included.
                if (kind == TerminalKind.SELECT_ALL) {
                    assertTrue(TerminalSolver.selectColor(kind.argument(board.title())) != null, board.title());
                }
            }
        }
    }

    @Test
    void followingTheSolverSolvesEveryTerminal() {
        for (TerminalKind kind : TerminalKind.values()) {
            if (kind.isMelody()) continue;
            for (long seed = 0; seed < 50; seed++) {
                TerminalPracticeBoard board = deal(kind, seed);
                TerminalSession.State state = new TerminalSession.State();
                int clicks = 0;
                while (!board.solved()) {
                    TerminalSolution solution = TerminalSolver.solve(kind, board.title(), board.items(), state);
                    int slot = -1;
                    for (int s = 0; s < board.size() && slot < 0; s++) {
                        if (solution.mark(s) == Mark.CLICK) slot = s;
                    }
                    assertTrue(slot >= 0, kind + " has nothing left to click, seed " + seed);
                    int button = kind == TerminalKind.RUBIX && solution.clicks(slot) < 0 ? 1 : 0;
                    assertTrue(board.click(slot, button, 0L), kind + " refused the solver's click, seed " + seed);
                    state.withConfirmed(slot, board.item(slot));
                    assertTrue(++clicks <= 60, kind + " never ends, seed " + seed);
                }
                assertTrue(TerminalSolver.solve(kind, board.title(), board.items(), state).solved(), kind + " seed " + seed);
            }
        }
    }

    @Test
    void panesTurnBothWays() {
        TerminalPracticeBoard board = deal(TerminalKind.PANES, 3);
        int slot = TerminalKind.PANES.playSlots()[0];
        String before = board.item(slot).itemId();
        assertTrue(board.click(slot, 0, 0L));
        assertNotEquals(before, board.item(slot).itemId());
        assertTrue(board.click(slot, 0, 0L));
        assertEquals(before, board.item(slot).itemId());
    }

    @Test
    void rubixRightClickGoesBack() {
        TerminalPracticeBoard board = deal(TerminalKind.RUBIX, 5);
        int slot = TerminalKind.RUBIX.playSlots()[4];
        int color = TerminalSolver.rubixColor(board.item(slot));
        board.click(slot, 1, 0L);
        assertEquals(Math.floorMod(color - 1, 5), TerminalSolver.rubixColor(board.item(slot)));
        board.click(slot, 0, 0L);
        assertEquals(color, TerminalSolver.rubixColor(board.item(slot)));
    }

    @Test
    void numbersOnlyTakeTheLowest() {
        TerminalPracticeBoard board = deal(TerminalKind.NUMBERS, 7);
        int one = -1, two = -1;
        for (int slot : TerminalKind.NUMBERS.playSlots()) {
            if (board.item(slot).count() == 1) one = slot;
            if (board.item(slot).count() == 2) two = slot;
        }
        assertFalse(board.click(two, 0, 0L));
        assertEquals("red_stained_glass_pane", board.item(two).itemId());
        assertTrue(board.click(one, 0, 0L));
        assertEquals("lime_stained_glass_pane", board.item(one).itemId());
        assertEquals(1, board.item(one).count());
        assertTrue(board.click(two, 0, 0L));
    }

    @Test
    void wrongItemsAndFillerChangeNothing() {
        for (TerminalKind kind : new TerminalKind[]{TerminalKind.STARTS_WITH, TerminalKind.SELECT_ALL}) {
            TerminalPracticeBoard board = deal(kind, 11);
            TerminalSolution solution = TerminalSolver.solve(kind, board.title(), board.items(), new TerminalSession.State());
            assertFalse(board.click(0, 0, 0L), "filler");
            for (int slot : kind.playSlots()) {
                if (solution.mark(slot) == Mark.CLICK) {
                    assertTrue(board.click(slot, 0, 0L));
                    assertTrue(board.item(slot).glint());
                    assertFalse(board.click(slot, 0, 0L), "a second click on the same item");
                } else {
                    assertFalse(board.click(slot, 0, 0L), kind + " took " + board.item(slot).name());
                }
            }
            assertTrue(board.solved());
        }
    }

    @Test
    void melodyTakesTheButtonOnlyOnTime() {
        TerminalPracticeBoard board = deal(TerminalKind.MELODY, 2);
        long now = 0L;
        for (int row = 1; row <= 3; row++) {
            TerminalSolution s = TerminalSolver.solve(TerminalKind.MELODY, board.title(), board.items(), null);
            assertEquals(row, s.activeRow());
            assertEquals(row * 9 + 7, s.buttonSlot());
            // Wait until the moving pane is in the target column; a press before that is ignored.
            int guard = 0;
            while (s.movingColumn() != s.columnTarget()) {
                now += TerminalPracticeBoard.MELODY_STEP_MS;
                board.advance(now);
                s = TerminalSolver.solve(TerminalKind.MELODY, board.title(), board.items(), null);
                assertTrue(++guard < 20);
            }
            assertFalse(board.click(row * 9 + 6, 0, now), "not a button");
            assertTrue(board.click(row * 9 + 7, 0, now));
        }
        assertTrue(board.solved());
    }

    @Test
    void aMistimedPressHoldsThePane() {
        TerminalPracticeBoard board = deal(TerminalKind.MELODY, 4);
        TerminalSolution s = TerminalSolver.solve(TerminalKind.MELODY, board.title(), board.items(), null);
        long now = 0L;
        while (s.movingColumn() == s.columnTarget()) {
            now += TerminalPracticeBoard.MELODY_STEP_MS;
            board.advance(now);
            s = TerminalSolver.solve(TerminalKind.MELODY, board.title(), board.items(), null);
        }
        int column = s.movingColumn();
        assertFalse(board.click(16, 0, now));
        assertFalse(board.advance(now + TerminalPracticeBoard.MELODY_PAUSE_MS - 1));
        assertEquals(column, TerminalSolver.solve(TerminalKind.MELODY, board.title(), board.items(), null).movingColumn());
        assertTrue(board.advance(now + TerminalPracticeBoard.MELODY_PAUSE_MS));
    }
}
