package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.IcePathSearch.Route;
import net.alpaka.addons.features.dungeon.puzzle.IcePathSearch.Slide;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Ice Path board and the slide search (puzzles-floor.md 3). */
class IcePathSearchTest {

    /** A real board (puzzles-floor.md 3.6): row 0 (the gate side) first, room X 23 on the left. */
    private static final String[] BOARD = {
            ".....#...........",
            ".#...............",
            "....#.......#....",
            "#...............#",
            ".................",
            ".................",
            ".................",
            ".................",
            ".................",
            "...............#.",
            ".#........#....#.",
            "..#........#.....",
            ".................",
            ".................",
            ".................",
            "......#...#......",
            "..#..........#...",
    };

    private static boolean[] board(String... rows) {
        boolean[] blocked = new boolean[IcePathSearch.CELLS];
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < IcePathSearch.SIZE; col++) {
                blocked[IcePathSearch.cell(row, col)] = rows[row].charAt(col) == '#';
            }
        }
        return blocked;
    }

    private static boolean[] empty() {
        return new boolean[IcePathSearch.CELLS];
    }

    @Test
    void cellsSitWhereTheRoomHasThem() {
        int start = IcePathSearch.cell(15, 15);
        assertEquals(8, IcePathSearch.relX(start));
        assertEquals(9, IcePathSearch.relZ(start));
        assertEquals(start, IcePathSearch.cellAt(8, 9));
        assertEquals(IcePathSearch.cell(0, 9), IcePathSearch.cellAt(14, 24));
        assertEquals(IcePathSearch.cell(16, 16), IcePathSearch.cellAt(7, 8));
        assertEquals(-1, IcePathSearch.cellAt(6, 9));
        assertEquals(-1, IcePathSearch.cellAt(15, 25));
        // The gate is in front of room X 14 to 16.
        assertTrue(IcePathSearch.atGate(IcePathSearch.cellAt(16, 24)));
        assertTrue(IcePathSearch.atGate(IcePathSearch.cellAt(14, 24)));
        assertFalse(IcePathSearch.atGate(IcePathSearch.cellAt(13, 24)));
        assertFalse(IcePathSearch.atGate(IcePathSearch.cellAt(15, 23)));
    }

    @Test
    void slidesStopBeforeABlockOrAtTheEdge() {
        boolean[] blocked = board(BOARD);
        int start = IcePathSearch.cell(15, 15);
        // Towards room +X (lower columns) it runs into the block at column 10.
        assertEquals(IcePathSearch.cell(15, 11), IcePathSearch.slide(blocked, start, Slide.PLUS_X));
        // Towards room -Z it stops at the near edge.
        assertEquals(IcePathSearch.cell(16, 15), IcePathSearch.slide(blocked, start, Slide.MINUS_Z));
        // A block right next to it: it does not move.
        int beside = IcePathSearch.cell(15, 11);
        assertEquals(beside, IcePathSearch.slide(blocked, beside, Slide.PLUS_X));
        // The slide directions are the room's.
        assertEquals(1, Slide.PLUS_Z.dz);
        assertEquals(-1, Slide.PLUS_Z.dRow);
        assertEquals(1, Slide.PLUS_X.dx);
        assertEquals(-1, Slide.PLUS_X.dCol);
    }

    @Test
    void theKnownBoardHasItsTenHitRoute() {
        Route route = IcePathSearch.solve(board(BOARD), IcePathSearch.cell(15, 15));
        assertNotNull(route);
        int[][] stops = {{15, 15}, {15, 11}, {16, 11}, {16, 3}, {0, 3}, {0, 4}, {1, 4}, {1, 2}, {10, 2}, {10, 9}, {0, 9}};
        List<Integer> expected = new ArrayList<>();
        for (int[] s : stops) expected.add(IcePathSearch.cell(s[0], s[1]));
        assertEquals(expected, route.stops());
        assertEquals(List.of(Slide.PLUS_X, Slide.MINUS_Z, Slide.PLUS_X, Slide.PLUS_Z, Slide.MINUS_X,
                Slide.MINUS_Z, Slide.PLUS_X, Slide.MINUS_Z, Slide.MINUS_X, Slide.PLUS_Z), route.slides());
        assertEquals(10, route.hits());
        // It ends at room (14, 24), in front of the gate.
        assertEquals(14, IcePathSearch.relX(route.stops().getLast()));
        assertEquals(24, IcePathSearch.relZ(route.stops().getLast()));

        // After the first hit, the rest of the route is the same route.
        Route rest = route.rest();
        assertEquals(9, rest.hits());
        assertEquals(expected.subList(1, expected.size()), rest.stops());
        assertEquals(rest, IcePathSearch.solve(board(BOARD), IcePathSearch.cell(15, 11)));
    }

    @Test
    void theLastSlideHasToGoIntoTheGate() {
        // A block at row 0, column 9: sliding along the far row stops in front of the gate, but
        // sideways; it only counts once it comes up the column into it.
        boolean[] blocked = empty();
        blocked[IcePathSearch.cell(0, 9)] = true;
        Route route = IcePathSearch.solve(blocked, IcePathSearch.cell(0, 0));
        assertNotNull(route);
        assertEquals(3, route.hits());
        assertEquals(Slide.PLUS_Z, route.slides().getLast());
        assertEquals(IcePathSearch.cell(0, 8), route.stops().getLast());
    }

    @Test
    void atTheGateThereIsNothingToDoAndBoxedInThereIsNoWay() {
        Route there = IcePathSearch.solve(empty(), IcePathSearch.cell(0, 8));
        assertNotNull(there);
        assertEquals(0, there.hits());

        boolean[] boxed = empty();
        int fish = IcePathSearch.cell(8, 8);
        for (Slide s : Slide.values()) boxed[IcePathSearch.step(fish, s)] = true;
        assertNull(IcePathSearch.solve(boxed, fish));

        // A straight run up a gate column is one hit.
        Route straight = IcePathSearch.solve(empty(), IcePathSearch.cell(16, 7));
        assertEquals(1, straight.hits());
        assertEquals(List.of(Slide.PLUS_Z), straight.slides());
    }
}
