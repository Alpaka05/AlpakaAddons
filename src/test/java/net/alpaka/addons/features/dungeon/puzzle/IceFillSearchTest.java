package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.IceFillBoard.Floor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Solution data adapted from Odin by odtheking, BSD-3-Clause, see CREDITS.md.
 * The fifteen floor layouts below were decoded from Odin's ice-fill-floors.json (puzzles-floor.md
 * 2.5) and serve only as test data; the solver searches every floor itself and ships no layouts.
 */

/** The Ice Fill floors and the walk search (puzzles-floor.md 2). */
class IceFillSearchTest {

    /**
     * A known layout. The picture is drawn as puzzles-floor.md 2.5 prints it: the far (exit) row
     * first, the high-X column on the left. {@code obstacles} are the same blocks as room X, Z.
     */
    private record Layout(String name, int floor, String picture, int[][] obstacles, int fewestTurns) {}

    private static final List<Layout> LAYOUTS = List.of(
            new Layout("F0.0", 0, "#.. ... ..#", new int[][]{{16, 9}, {14, 7}}, 4),
            new Layout("F0.1", 0, "..# ... #..", new int[][]{{14, 9}, {16, 7}}, 4),
            new Layout("F0.2", 0, "#.. #.. #.#", new int[][]{{16, 9}, {16, 8}, {16, 7}, {14, 7}}, 3),
            new Layout("F0.3", 0, "..# ..# #.#", new int[][]{{14, 9}, {14, 8}, {14, 7}, {16, 7}}, 3),
            new Layout("F1.0", 1, "##... ..... ...#. .#... .....", new int[][]{{17, 16}, {16, 16}, {14, 14}, {16, 13}}, 13),
            new Layout("F1.1", 1, "..... ..... .##.. ..... .....", new int[][]{{16, 14}, {15, 14}}, 11),
            new Layout("F1.2", 1, "..... ..##. ..... .##.. .....", new int[][]{{15, 15}, {14, 15}, {16, 13}, {15, 13}}, 8),
            new Layout("F1.3", 1, "..... ...#. ..... ..#.. .....", new int[][]{{14, 15}, {15, 13}}, 14),
            new Layout("F1.4", 1, "##... #..#. ..... ..#.. #....",
                    new int[][]{{17, 16}, {16, 16}, {17, 15}, {14, 15}, {15, 13}, {17, 12}}, 12),
            new Layout("F1.5", 1, "..... ..... ..##. ..... .....", new int[][]{{15, 14}, {14, 14}}, 11),
            new Layout("F2.0", 2, "..#.... ....... ...##.. ....... ....... ....... #......",
                    new int[][]{{16, 25}, {15, 23}, {14, 23}, {18, 19}}, 18),
            new Layout("F2.1", 2, "..#.#.. ..#.... ....... ....... ..###.. ....... .......",
                    new int[][]{{16, 25}, {14, 25}, {16, 24}, {16, 21}, {15, 21}, {14, 21}}, 19),
            new Layout("F2.2", 2, "..#...# ....... ...#... ....... ....##. ...#... .......",
                    new int[][]{{16, 25}, {12, 25}, {15, 23}, {14, 21}, {13, 21}, {15, 20}}, 24),
            new Layout("F2.3", 2, "..#.... ....##. ....... .##.... ..#.... ....#.. ....#..",
                    new int[][]{{16, 25}, {14, 24}, {13, 24}, {17, 22}, {16, 22}, {16, 21}, {14, 20}, {14, 19}}, 21),
            new Layout("F2.4", 2, "..#...# ....... ...#... .####.. ...#... ...#.#. .......",
                    new int[][]{{16, 25}, {12, 25}, {15, 23}, {17, 22}, {16, 22}, {15, 22}, {14, 22}, {15, 21}, {15, 20}, {13, 20}}, 20));

    /** The free cells of a layout, read from its picture. */
    static long freeCells(Floor floor, String picture) {
        String[] rows = picture.split(" ");
        int n = floor.size();
        assertEquals(n, rows.length, picture);
        long free = 0L;
        for (int row = 0; row < n; row++) {
            assertEquals(n, rows[row].length(), picture);
            for (int col = 0; col < n; col++) {
                if (rows[row].charAt(col) == '#') continue;
                // The picture's first row is the far edge, its first column the high-X side.
                int cell = floor.cellAt(floor.x0() + n - 1 - col, floor.z0() + n - 1 - row);
                free |= 1L << cell;
            }
        }
        return free;
    }

    /** A walk over exactly the free cells, start to end, one straight step at a time. */
    static void assertWalk(Floor floor, long free, int start, int[] cells, String name) {
        assertEquals(start, cells[0], name + ": start");
        assertEquals(floor.end(), cells[cells.length - 1], name + ": end");
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < cells.length; i++) {
            assertTrue((free >>> cells[i] & 1L) != 0, name + ": cell " + cells[i] + " is not free");
            assertTrue(seen.add(cells[i]), name + ": cell " + cells[i] + " twice");
            if (i > 0) assertTrue((floor.neighbours(cells[i - 1]) >>> cells[i] & 1L) != 0, name + ": not a straight step at " + i);
        }
        assertEquals(Long.bitCount(free), cells.length, name + ": cells left out");
    }

    @Test
    void theFloorsSitWhereTheRoomHasThem() {
        List<Floor> floors = IceFillBoard.FLOORS;
        int[][] starts = {{15, 70, 7}, {15, 71, 12}, {15, 72, 19}};
        int[][] ends = {{15, 9}, {15, 16}, {15, 25}};
        int[] exits = {10, 17, 26};
        for (int i = 0; i < 3; i++) {
            Floor floor = floors.get(i);
            assertEquals(starts[i][0], floor.relX(floor.start()));
            assertEquals(starts[i][1], floor.walkY());
            assertEquals(starts[i][2], floor.relZ(floor.start()));
            assertEquals(ends[i][0], floor.relX(floor.end()));
            assertEquals(ends[i][1], floor.relZ(floor.end()));
            assertEquals(exits[i], floor.exitZ());
        }
        assertEquals(-1, floors.get(0).cellAt(13, 7));
        assertEquals(-1, floors.get(0).cellAt(15, 10));
    }

    @Test
    void thePicturesAndTheObstacleListsAgree() {
        for (Layout layout : LAYOUTS) {
            Floor floor = IceFillBoard.FLOORS.get(layout.floor());
            long blocked = 0L;
            for (int[] xz : layout.obstacles()) blocked |= 1L << floor.cellAt(xz[0], xz[1]);
            long all = (1L << floor.cells()) - 1;
            assertEquals(all & ~blocked, freeCells(floor, layout.picture()), layout.name());
        }
    }

    @Test
    void everyKnownLayoutGetsAWalkWithTheFewestTurns() {
        for (Layout layout : LAYOUTS) {
            Floor floor = IceFillBoard.FLOORS.get(layout.floor());
            long free = freeCells(floor, layout.picture());
            IceFillSearch.Path path = IceFillSearch.solve(floor.size(), floor.size(), free, floor.start(), floor.end());
            assertNotNull(path, layout.name());
            assertWalk(floor, free, floor.start(), path.cells(), layout.name());
            assertEquals(IceFillSearch.turns(path.cells()), path.turns(), layout.name());
            assertTrue(path.fewestTurns(), layout.name() + " ran out of budget");
            assertEquals(layout.fewestTurns(), path.turns(), layout.name());
        }
    }

    @Test
    void searchingIsQuick() {
        long started = System.nanoTime();
        for (int round = 0; round < 3; round++) {
            for (Layout layout : LAYOUTS) {
                Floor floor = IceFillBoard.FLOORS.get(layout.floor());
                IceFillSearch.solve(floor.size(), floor.size(), freeCells(floor, layout.picture()), floor.start(), floor.end());
            }
        }
        long ms = (System.nanoTime() - started) / 1_000_000;
        assertTrue(ms < 5_000, "45 searches took " + ms + " ms");
    }

    /** puzzles-floor.md 2.5: the walk another solver gives for layout F2.0, as (row, col) in the picture. */
    @Test
    void aKnownWalkPassesTheWalkCheck() {
        int[][] rowCol = {{6, 3}, {5, 3}, {4, 3}, {3, 3}, {3, 2}, {4, 2}, {5, 2}, {6, 2}, {6, 1}, {5, 1}, {5, 0}, {4, 0}, {4, 1},
                {3, 1}, {3, 0}, {2, 0}, {1, 0}, {0, 0}, {0, 1}, {1, 1}, {2, 1}, {2, 2}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 5},
                {3, 5}, {3, 4}, {4, 4}, {5, 4}, {6, 4}, {6, 5}, {6, 6}, {5, 6}, {5, 5}, {4, 5}, {4, 6}, {3, 6}, {2, 6}, {1, 6},
                {0, 6}, {0, 5}, {0, 4}, {0, 3}};
        Floor floor = IceFillBoard.FLOORS.get(2);
        int[] cells = new int[rowCol.length];
        for (int i = 0; i < rowCol.length; i++) {
            cells[i] = floor.cellAt(floor.x0() + 6 - rowCol[i][1], floor.z0() + 6 - rowCol[i][0]);
        }
        assertWalk(floor, freeCells(floor, LAYOUTS.get(10).picture()), floor.start(), cells, "F2.0");
        assertTrue(IceFillSearch.turns(cells) > 18);
    }

    @Test
    void floorsThatCannotBeWalkedAreTurnedDownAtOnce() {
        // A whole 3 x 3 floor: edge middle to edge middle has no walk (the chessboard count says so).
        assertFalse(IceFillSearch.parityAllows(3, 0x1FFL, 1, 7));
        assertNull(IceFillSearch.solve(3, 3, 0x1FFL, 1, 7));
        // Two pieces with nothing between them: the colours balance, the pieces do not join.
        long pieces = 1L | 1L << 4 | 1L << 3 | 1L << 7;
        assertTrue(IceFillSearch.parityAllows(4, pieces, 0, 3));
        assertNull(IceFillSearch.solve(4, 2, pieces, 0, 3));
        // Start or end not free.
        assertNull(IceFillSearch.solve(3, 3, 0x1FEL & ~(1L << 1), 1, 7));
        // One cell is a walk of one.
        IceFillSearch.Path single = IceFillSearch.solve(3, 3, 1L << 4, 4, 4);
        assertNotNull(single);
        assertArrayEquals(new int[]{4}, single.cells());
    }

    @Test
    void aSmallBudgetStillGivesAWalkOrNothing() {
        Layout hardest = LAYOUTS.get(12);
        Floor floor = IceFillBoard.FLOORS.get(hardest.floor());
        long free = freeCells(floor, hardest.picture());
        for (int budget : new int[]{1, 10, 60, 400}) {
            IceFillSearch.Path path = IceFillSearch.solve(floor.size(), floor.size(), free, floor.start(), floor.end(), budget);
            if (path == null) continue;
            assertWalk(floor, free, floor.start(), path.cells(), "budget " + budget);
            assertTrue(path.turns() >= hardest.fewestTurns());
        }
    }

    @Test
    void turnsAreCountedBetweenSteps() {
        // On a 5-wide grid: right, right, down, down, left.
        assertEquals(2, IceFillSearch.turns(new int[]{0, 1, 2, 7, 12, 11}));
        assertEquals(0, IceFillSearch.turns(new int[]{0, 1, 2}));
        assertEquals(0, IceFillSearch.turns(new int[]{4}));
        assertArrayEquals(new int[]{0, 2, 12, 11}, IceFillBoard.corners(new int[]{0, 1, 2, 7, 12, 11}));
        assertArrayEquals(new int[]{0, 2}, IceFillBoard.corners(new int[]{0, 1, 2}));
    }
}
