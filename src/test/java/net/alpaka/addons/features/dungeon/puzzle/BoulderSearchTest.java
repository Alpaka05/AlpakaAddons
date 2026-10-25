package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.BoulderBoard.Push;
import net.alpaka.addons.features.dungeon.puzzle.BoulderSearch.Outcome;
import net.alpaka.addons.features.dungeon.puzzle.BoulderSearch.Result;
import net.alpaka.addons.features.dungeon.puzzle.BoulderSearch.Step;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Solution data adapted from Odin by odtheking, BSD-3-Clause, see CREDITS.md.
 * The eight board keys and the pushes in odinSteps(...) are taken from Odin's
 * boulder-solutions.json; they serve only as test data for reading boards and the push geometry.
 * The solver never uses Odin's plans (five of the eight need boxes to merge, a Hypixel bug).
 */

/** The Boulder board and its push search (puzzles-floor.md 1, critic.md 3.1). */
class BoulderSearchTest {

    /** A known layout: its key, its picture (row 0 = the far row first, x 24 on the left), the fewest merge-free pushes. */
    private record Layout(String name, String key, String picture, int pushes) {}

    private static final List<Layout> LAYOUTS = List.of(
            new Layout("L1", "010000010111101001010011100000101110000111",
                    ".#..... #.####. #..#.#. .###... ..#.### ....###", 4),
            new Layout("L2", "100000111101111011101110001110111010000000",
                    "#.....# ###.### #.###.# ##...## #.###.# .......", 9),
            new Layout("L3", "110001110111011010001100111111100011000001",
                    "##...## #.###.# #.#...# #..#### ###...# #.....#", 4),
            new Layout("L4", "100100101000100010100010101000101000100010",
                    "#..#..# .#...#. ..#.#.. .#.#.#. ..#.#.. .#...#.", 1),
            new Layout("L5", "000000011111101001010011100000101110000110",
                    "....... ######. #..#.#. .###... ..#.### ....##.", 4),
            new Layout("L6", "000000001111100100010010001011111110000000",
                    "....... .#####. .#...#. .#...#. ####### .......", 4),
            new Layout("L7", "000000010101110101010011010000010100000000",
                    "....... #.#.### .#.#.#. .##.#.. ...#.#. .......", 1),
            new Layout("L8", "100101001000000010101001111101010101010101",
                    "#..#.#. .#..... ..#.#.# ..##### .#.#.#. #.#.#.#", 2));

    private static long fromPicture(String picture) {
        String rows = picture.replace(" ", "");
        assertEquals(BoulderBoard.CELLS, rows.length(), picture);
        return BoulderBoard.parse(rows.replace('#', '1').replace('.', '0'));
    }

    @Test
    void keysAreReadRowByRowFromTheFarCorner() {
        for (Layout layout : LAYOUTS) {
            long board = BoulderBoard.parse(layout.key());
            assertEquals(fromPicture(layout.picture()), board, layout.name());
            assertEquals(layout.key(), BoulderBoard.key(board), layout.name());
        }
        // Bit 0 is the far row's cell at room X 24, bit 7 the next row's.
        long l2 = BoulderBoard.parse(LAYOUTS.get(1).key());
        assertTrue((l2 & 1L) != 0);
        assertEquals(24, BoulderBoard.centreX(BoulderBoard.col(0)));
        assertEquals(24, BoulderBoard.centreZ(BoulderBoard.row(0)));
        assertEquals(6, BoulderBoard.centreX(BoulderBoard.col(6)));
        assertEquals(21, BoulderBoard.centreZ(BoulderBoard.row(7)));
        assertEquals(9, BoulderBoard.centreZ(5));
        assertEquals(6, BoulderBoard.centreZ(BoulderBoard.CORRIDOR_ROW));
    }

    @Test
    void theCorridorIsAlwaysWalkable() {
        long full = BoulderBoard.BOARD;
        long reach = BoulderBoard.reachable(full);
        assertEquals(0L, reach & BoulderBoard.BOARD);
        for (int col = 0; col < BoulderBoard.COLS; col++) {
            assertTrue((reach >>> BoulderBoard.index(BoulderBoard.CORRIDOR_ROW, col) & 1L) != 0);
        }
        assertTrue(BoulderBoard.open(0L));
        assertFalse(BoulderBoard.open(full));
    }

    @Test
    void walkingGoesAroundBoxesButNotThroughThem() {
        // One open column at the right edge, closed off at row 2, and a walled-in hole at row 4's
        // first column, right after row 3's last cell in bit order.
        long board = fromPicture("######. ######. ####### ######. .#####. ######.");
        long reach = BoulderBoard.reachable(board);
        assertTrue((reach >>> BoulderBoard.index(5, 6) & 1L) != 0);
        assertTrue((reach >>> BoulderBoard.index(3, 6) & 1L) != 0);
        assertFalse((reach >>> BoulderBoard.index(1, 6) & 1L) != 0);
        assertFalse(BoulderBoard.open(board));
        // Nothing wraps from one row's last column into the next row's first.
        assertFalse((reach >>> BoulderBoard.index(4, 0) & 1L) != 0);
    }

    @Test
    void aPushNeedsAnEmptyTargetAndAHandleThePlayerCanReach() {
        long board = fromPicture("....... ....... ....... ....... .#..... .#.....");
        long reach = BoulderBoard.reachable(board);
        int front = BoulderBoard.index(5, 1);
        int back = BoulderBoard.index(4, 1);
        // The front box cannot go forward: the box behind it is in the way, and nothing merges.
        assertEquals(-1L, BoulderBoard.push(board, reach, front, Push.FORWARD));
        // The back box goes forward from the cell behind it... which is the front box: no handle to reach.
        assertEquals(-1L, BoulderBoard.push(board, reach, back, Push.FORWARD));
        // Sideways works for both: the cells beside them are open.
        long moved = BoulderBoard.push(board, reach, front, Push.RIGHT);
        assertEquals(fromPicture("....... ....... ....... ....... .#..... ..#...."), moved);
        // Off the board, and into the corridor, are not pushes.
        long edge = fromPicture("....... ....... ....... ....... ....... #......");
        assertEquals(-1L, BoulderBoard.push(edge, BoulderBoard.reachable(edge), BoulderBoard.index(5, 0), Push.LEFT));
        assertEquals(-1L, BoulderBoard.push(edge, BoulderBoard.reachable(edge), BoulderBoard.index(5, 0), Push.BACK));
        // No box, no push.
        assertEquals(-1L, BoulderBoard.push(board, reach, BoulderBoard.index(0, 0), Push.FORWARD));
    }

    @Test
    void handleSpotsAndBoxesSitWhereTheRoomHasThem() {
        // Odin's L4 step: box at (21, 21), its handle at (23, 65, 21) on the +X face, pushed to -X.
        int cell = BoulderBoard.index(1, 1);
        assertEquals(new BlockPos(23, 65, 21), BoulderBoard.handleSpot(cell, Push.RIGHT));
        assertEquals(new BlockPos(19, 65, 21), BoulderBoard.handleSpot(cell, Push.LEFT));
        assertEquals(new BlockPos(21, 65, 19), BoulderBoard.handleSpot(cell, Push.FORWARD));
        assertEquals(new BlockPos(21, 65, 23), BoulderBoard.handleSpot(cell, Push.BACK));
        var box = BoulderBoard.boxVolume(cell, 68);
        assertEquals(20, box.minX);
        assertEquals(23, box.maxX);
        assertEquals(20, box.minZ);
        assertEquals(23, box.maxZ);
        assertEquals(65, box.minY);
        assertEquals(68, box.maxY);
        // The handle's cell is the one the player stands in.
        assertEquals(BoulderBoard.index(1, 0), BoulderBoard.handleCell(cell, Push.RIGHT));
        assertEquals(BoulderBoard.index(2, 1), BoulderBoard.handleCell(cell, Push.FORWARD));
        assertEquals(-1, BoulderBoard.handleCell(BoulderBoard.index(0, 0), Push.RIGHT));
        assertEquals(BoulderBoard.index(BoulderBoard.CORRIDOR_ROW, 3), BoulderBoard.handleCell(BoulderBoard.index(5, 3), Push.FORWARD));
    }

    /** Odin's [renderX, renderZ, clickX, clickZ] entries as our pushes: the box is two blocks past the click. */
    private static List<Step> odinSteps(int[][] entries) {
        List<Step> steps = new ArrayList<>();
        for (int[] e : entries) {
            int dx = Integer.signum(e[0] - e[2]);
            int dz = Integer.signum(e[1] - e[3]);
            int boxX = e[2] + 2 * dx;
            int boxZ = e[3] + 2 * dz;
            int cell = BoulderBoard.index((24 - boxZ) / 3, (24 - boxX) / 3);
            Push push = null;
            for (Push p : Push.values()) if (p.dx == dx && p.dz == dz) push = p;
            steps.add(new Step(cell, push));
            assertEquals(new BlockPos(e[2], 65, e[3]), BoulderBoard.handleSpot(cell, push));
        }
        return steps;
    }

    @Test
    void odinsMergeFreePlansWorkWithThisPushRule() {
        assertTrue(BoulderSearch.works(BoulderBoard.parse(LAYOUTS.get(3).key()), odinSteps(new int[][]{{22, 21, 23, 21}})));
        assertTrue(BoulderSearch.works(BoulderBoard.parse(LAYOUTS.get(6).key()), odinSteps(new int[][]{{22, 18, 23, 18}})));
        assertTrue(BoulderSearch.works(BoulderBoard.parse(LAYOUTS.get(7).key()),
                odinSteps(new int[][]{{21, 11, 21, 10}, {22, 21, 23, 21}})));
    }

    @Test
    void plansThatNeedBoxesToMergeAreRefused() {
        // Odin's L1 and L5 plans push a box into another one; that is the bug, and it is not a push here.
        assertFalse(BoulderSearch.works(BoulderBoard.parse(LAYOUTS.get(0).key()),
                odinSteps(new int[][]{{22, 15, 23, 15}, {19, 21, 20, 21}})));
        assertFalse(BoulderSearch.works(BoulderBoard.parse(LAYOUTS.get(4).key()), odinSteps(new int[][]{{13, 12, 14, 12}})));
    }

    @Test
    void everyKnownLayoutGetsTheFewestMergeFreePushes() {
        for (Layout layout : LAYOUTS) {
            long board = BoulderBoard.parse(layout.key());
            Result result = BoulderSearch.solve(board, BoulderSearch.FirstPush.ANY);
            assertEquals(Outcome.PLAN, result.outcome(), layout.name());
            assertEquals(layout.pushes(), result.steps().size(), layout.name() + " " + result.steps());
            assertTrue(BoulderSearch.works(board, result.steps()), layout.name());
            assertTrue(result.boards() < 20_000, layout.name() + " looked at " + result.boards());

            long at = board;
            for (Step step : result.steps()) {
                assertNotEquals(Push.BACK, step.push(), layout.name());
                assertTrue((at >>> step.cell() & 1L) != 0, layout.name());
                assertEquals(0L, at >>> step.target() & 1L, layout.name() + ": pushed into a box");
                at = step.apply(at);
            }
            assertEquals(Long.bitCount(board), Long.bitCount(at), layout.name() + ": a box went missing");
            assertTrue(BoulderBoard.open(at), layout.name());
        }
    }

    @Test
    void searchingIsQuick() {
        long started = System.nanoTime();
        for (int round = 0; round < 3; round++) {
            for (Layout layout : LAYOUTS) BoulderSearch.solve(BoulderBoard.parse(layout.key()), BoulderSearch.FirstPush.ANY);
        }
        long ms = (System.nanoTime() - started) / 1_000_000;
        assertTrue(ms < 5_000, "24 searches took " + ms + " ms");
    }

    @Test
    void anOpenBoardNeedsNothingAndALockedOneHasNoPlan() {
        Result open = BoulderSearch.solve(fromPicture("....... ....... ....... ....... ....... #.#.#.#"), BoulderSearch.FirstPush.ANY);
        assertEquals(Outcome.OPEN, open.outcome());
        assertTrue(open.steps().isEmpty());
        assertTrue(open.found());

        Result locked = BoulderSearch.solve(BoulderBoard.BOARD, BoulderSearch.FirstPush.ANY);
        assertEquals(Outcome.NO_PLAN, locked.outcome());
        assertFalse(locked.found());
        assertTrue(locked.steps().isEmpty());
    }

    @Test
    void aFirstPushWithoutAHandleIsNotOffered() {
        long board = BoulderBoard.parse(LAYOUTS.get(3).key());
        Step usual = BoulderSearch.solve(board, BoulderSearch.FirstPush.ANY).steps().getFirst();
        Result without = BoulderSearch.solve(board, (cell, push) -> !(cell == usual.cell() && push == usual.push()));
        assertEquals(Outcome.PLAN, without.outcome());
        assertNotEquals(usual, without.steps().getFirst());
        assertTrue(BoulderSearch.works(board, without.steps()));

        // Later pushes are not filtered: the handle of a box that has not got there yet cannot be seen.
        Result noneAtAll = BoulderSearch.solve(board, (cell, push) -> false);
        assertEquals(Outcome.NO_PLAN, noneAtAll.outcome());
    }

    @Test
    void aPlanBeingFollowedKeepsItsRest() {
        long board = BoulderBoard.parse(LAYOUTS.get(0).key());
        List<Step> plan = BoulderSearch.solve(board, BoulderSearch.FirstPush.ANY).steps();
        long afterOne = plan.getFirst().apply(board);
        List<Step> rest = BoulderSearch.afterFirst(plan, board, afterOne);
        assertEquals(plan.subList(1, plan.size()), rest);
        // Still the shortest from there.
        assertEquals(rest.size(), BoulderSearch.solve(afterOne, BoulderSearch.FirstPush.ANY).steps().size());
        assertTrue(BoulderSearch.works(afterOne, rest));

        // Someone pushed a different box: the plan is dropped.
        Step other = new Step(plan.getFirst().cell(), plan.getFirst().push() == Push.LEFT ? Push.RIGHT : Push.LEFT);
        long reach = BoulderBoard.reachable(board);
        long elsewhere = BoulderBoard.push(board, reach, other.cell(), other.push());
        if (elsewhere >= 0) assertNull(BoulderSearch.afterFirst(plan, board, elsewhere));
        assertNull(BoulderSearch.afterFirst(plan, board, board));
        assertNull(BoulderSearch.afterFirst(List.of(), board, afterOne));
        // The last push leaves nothing to do.
        List<Step> last = plan.subList(plan.size() - 1, plan.size());
        long before = board;
        for (Step step : plan.subList(0, plan.size() - 1)) before = step.apply(before);
        assertTrue(BoulderSearch.afterFirst(last, before, last.getFirst().apply(before)).isEmpty());
    }

    @Test
    void theSearchGivesUpOnItsBudget() {
        long board = BoulderBoard.parse(LAYOUTS.get(1).key());
        Result result = BoulderSearch.solve(board, BoulderSearch.FirstPush.ANY,
                EnumSet.of(Push.FORWARD, Push.RIGHT, Push.LEFT), 50);
        assertEquals(Outcome.TOO_HARD, result.outcome());
        assertFalse(result.found());
    }

    @Test
    void backwardPushesAreOnlyALastResort() {
        // The player gets round to the far side of the box at row 3, column 3 by the left column;
        // the way to the far row starts behind it, in column 4. Forward and sideways it cannot go,
        // and nothing else helps, but pushing it back towards the corridor opens the way.
        long board = fromPicture("#####.# #####.# ....#.# .###..# .##.### .######");
        Result forwardAndSideways = BoulderSearch.solve(board, BoulderSearch.FirstPush.ANY,
                EnumSet.of(Push.FORWARD, Push.RIGHT, Push.LEFT), BoulderSearch.MAX_BOARDS);
        assertEquals(Outcome.NO_PLAN, forwardAndSideways.outcome());
        Result any = BoulderSearch.solve(board, BoulderSearch.FirstPush.ANY);
        assertEquals(Outcome.PLAN, any.outcome());
        assertEquals(List.of(new Step(BoulderBoard.index(3, 3), Push.BACK)), any.steps());

        // Where a forward or sideways plan exists, it is the one used.
        for (Layout layout : LAYOUTS) {
            for (Step step : BoulderSearch.solve(BoulderBoard.parse(layout.key()), BoulderSearch.FirstPush.ANY).steps()) {
                assertNotEquals(Push.BACK, step.push(), layout.name());
            }
        }
    }
}
