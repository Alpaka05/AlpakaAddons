package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.IceFillBoard.Floor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Following a walk on an Ice Fill floor as its ice turns to packed ice (puzzles-floor.md 2.7). */
class IceFillPlanTest {

    /** The middle floor, all ice: cells a * 5 + b, start 2, end 22. */
    private static final Floor FLOOR = IceFillBoard.FLOORS.get(1);
    private static final long ALL = (1L << 25) - 1;
    private static final int BUDGET = IceFillSearch.DEFAULT_BUDGET;

    private static long cells(int... cells) {
        long out = 0L;
        for (int c : cells) out |= 1L << c;
        return out;
    }

    private static long prefix(int[] route, int count) {
        return cells(Arrays.copyOf(route, count));
    }

    @Test
    void anUntouchedFloorShowsTheWholeWalk() {
        IceFillPlan plan = IceFillPlan.update(null, FLOOR, ALL, 0L, -1, BUDGET);
        assertNotNull(plan);
        int[] route = plan.route();
        IceFillSearchTest.assertWalk(FLOOR, ALL, FLOOR.start(), route, "fresh");
        assertEquals(-1, plan.position(0L));
        assertArrayEquals(route, plan.ahead(0L));
        // The same floor read again keeps the same plan.
        assertSame(plan, IceFillPlan.update(plan, FLOOR, ALL, 0L, -1, BUDGET));
    }

    @Test
    void walkingTheWalkMovesAlongIt() {
        IceFillPlan plan = IceFillPlan.update(null, FLOOR, ALL, 0L, -1, BUDGET);
        int[] route = plan.route();
        for (int walked = 1; walked < route.length; walked++) {
            long packed = prefix(route, walked);
            assertEquals(walked - 1, plan.position(packed));
            int[] ahead = plan.ahead(packed);
            assertEquals(route[walked - 1], ahead[0]);
            assertEquals(route.length - walked + 1, ahead.length);
            assertSame(plan, IceFillPlan.update(plan, FLOOR, ALL, packed, route[walked - 1], BUDGET));
        }
        // Two cells arriving in one tick are still the walk.
        assertEquals(2, plan.position(prefix(route, 3)));
        // All walked: the floor is done and shows nothing.
        assertNull(IceFillPlan.update(plan, FLOOR, ALL, ALL, FLOOR.end(), BUDGET));
    }

    @Test
    void steppingOffTheWalkGivesANewOneFromThere() {
        // Start, then left along the near edge and up: 2 -> 1 -> 0 -> 5.
        long packed = cells(2, 1, 0, 5);
        IceFillPlan old = IceFillPlan.fromStart(ALL, new IceFillSearch.Path(new int[]{2, 3, 4, 9, 8, 7, 6, 5, 0, 1}, 0, false));
        assertEquals(IceFillPlan.OFF_PLAN, old.position(packed));

        IceFillPlan plan = IceFillPlan.update(old, FLOOR, ALL, packed, 5, BUDGET);
        assertNotNull(plan);
        assertNotSame(old, plan);
        int[] route = plan.route();
        assertEquals(5, route[0]);
        IceFillSearchTest.assertWalk(FLOOR, (ALL & ~packed) | 1L << 5, 5, route, "from the walker");
        assertEquals(0, plan.position(packed));
        // It then follows that walk on.
        assertEquals(1, plan.position(packed | 1L << route[1]));
        assertEquals(IceFillPlan.OFF_PLAN, plan.position(packed | 1L << route[2]));

        // Without the walker on the floor, the trail of packed ice tells where they are.
        IceFillPlan fromTrail = IceFillPlan.update(old, FLOOR, ALL, packed, -1, BUDGET);
        assertNotNull(fromTrail);
        assertEquals(5, fromTrail.route()[0]);
    }

    @Test
    void aWalkThatCanNoLongerFinishShowsNothingUntilTheFloorResets() {
        // 2 -> 1 -> 6 -> 5 leaves the corner cell 0 with only one way in: no walk can cover it.
        long packed = cells(2, 1, 6, 5);
        assertNull(IceFillPlan.update(null, FLOOR, ALL, packed, 5, BUDGET));
        // The floor resets to ice: the walk from the start is back.
        IceFillPlan again = IceFillPlan.update(null, FLOOR, ALL, 0L, -1, BUDGET);
        assertNotNull(again);
        assertEquals(FLOOR.start(), again.route()[0]);
        // A plan made mid-floor does not survive a reset either.
        IceFillPlan mid = IceFillPlan.update(null, FLOOR, ALL, cells(2, 1, 0, 5), 5, BUDGET);
        assertEquals(IceFillPlan.OFF_PLAN, mid.position(0L));
        IceFillPlan reset = IceFillPlan.update(mid, FLOOR, ALL, 0L, -1, BUDGET);
        assertEquals(FLOOR.start(), reset.route()[0]);
        assertEquals(-1, reset.position(0L));
    }

    @Test
    void theTrailOfPackedIceGivesTheWalkersCell() {
        assertEquals(5, IceFillBoard.walkerFromTrail(FLOOR, cells(2, 1, 6, 5)));
        assertEquals(2, IceFillBoard.walkerFromTrail(FLOOR, cells(2)));
        // Both ways from the start: no single trail.
        assertEquals(-1, IceFillBoard.walkerFromTrail(FLOOR, cells(2, 1, 3)));
        // A packed cell away from the trail.
        assertEquals(-1, IceFillBoard.walkerFromTrail(FLOOR, cells(2, 1, 24)));
        // Nothing on the start.
        assertEquals(-1, IceFillBoard.walkerFromTrail(FLOOR, cells(7, 12)));
    }

    @Test
    void theNeighboursOfACellStayOnTheFloor() {
        assertEquals(cells(1, 5), FLOOR.neighbours(0));
        // Cell 9 is the high-X end of the second row.
        assertEquals(cells(4, 8, 14), FLOOR.neighbours(9));
        assertEquals(cells(19, 23), FLOOR.neighbours(24));
        assertFalse((FLOOR.neighbours(4) >>> 5 & 1L) != 0, "no wrap from one row's end to the next row");
        assertTrue((FLOOR.neighbours(12) & cells(7, 11, 13, 17)) == cells(7, 11, 13, 17));
    }
}
