package net.alpaka.addons.features.dungeon.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Arrow Align wall: where its cells are, the nine layouts, matching and click counts (terminals.md 4.3, Critic additions A). */
class ArrowAlignPatternsTest {

    /**
     * The nine layouts as the player sees them, rows from the top, written down independently in
     * terminals.md (Critic additions A) from Skyblocker's table; the stored table is Odin's, in
     * Odin's cell order. Both have to describe the same walls.
     */
    private static final List<String> SEEN_BY_PLAYER = List.of(
            "RRD.. U.D.. ..D.. ..D.U ..RRU",
            "RR.LL U...U UL.RU .U.U. .....",
            "DLL.. D.U.. ..... ..D.U ..RRU",
            "..... D...D D...D D.U.D RRULL",
            "RRRRD U...D U...D U.U.D ..ULL",
            "RRD.. U.D.U U.D.U U.D.U ..RRU",
            ".RRR. ..... .RRR. ..... .RRR.",
            ".RRD. .U.D. .U.D. .U.D. .U.R.",
            ".RD.. ..RR. .RU.. ..RR. .RU..");

    private static String asSeen(int pattern) {
        char[][] grid = new char[5][5];
        for (int cell = 0; cell < ArrowAlignPatterns.CELLS; cell++) {
            grid[ArrowAlignPatterns.row(cell)][ArrowAlignPatterns.column(cell)] = switch (ArrowAlignPatterns.target(pattern, cell)) {
                case 1 -> 'R';
                case 3 -> 'D';
                case 5 -> 'L';
                case 7 -> 'U';
                case ArrowAlignPatterns.NONE -> '.';
                default -> '?';
            };
        }
        StringBuilder out = new StringBuilder();
        for (char[] row : grid) {
            if (!out.isEmpty()) out.append(' ');
            out.append(row);
        }
        return out.toString();
    }

    /** The arrows of a layout at the given rotation each, nothing elsewhere. */
    private static int[] wall(int pattern, int rotation) {
        int[] rotations = new int[ArrowAlignPatterns.CELLS];
        for (int i = 0; i < rotations.length; i++) {
            rotations[i] = ArrowAlignPatterns.target(pattern, i) == ArrowAlignPatterns.NONE ? ArrowAlignPatterns.NONE : rotation;
        }
        return rotations;
    }

    @Test
    void theStoredLayoutsAreTheOnesThePlayerSees() {
        assertEquals(SEEN_BY_PLAYER.size(), ArrowAlignPatterns.count());
        for (int p = 0; p < ArrowAlignPatterns.count(); p++) {
            assertEquals(SEEN_BY_PLAYER.get(p), asSeen(p), "layout " + p);
        }
    }

    /** The cells holding arrows tell every layout apart, so the arrows alone pick it. */
    @Test
    void everyLayoutHasItsOwnArrowCells() {
        Set<String> masks = new HashSet<>();
        for (int p = 0; p < ArrowAlignPatterns.count(); p++) {
            StringBuilder mask = new StringBuilder();
            for (int cell = 0; cell < ArrowAlignPatterns.CELLS; cell++) {
                mask.append(ArrowAlignPatterns.target(p, cell) == ArrowAlignPatterns.NONE ? '.' : '#');
            }
            assertTrue(masks.add(mask.toString()), "layout " + p + " has the same arrow cells as another");
        }
    }

    @Test
    void cellsAndTheirFrames() {
        assertEquals(new BlockPos(-2, 120, 75), ArrowAlignPatterns.framePos(0));
        assertEquals(new BlockPos(-2, 124, 79), ArrowAlignPatterns.framePos(24));
        // Top left as the player sees it: highest, and furthest south (the player looks west).
        assertEquals(new BlockPos(-2, 124, 79), ArrowAlignPatterns.framePos(cellAtRowColumn(0, 0)));
        assertEquals(new BlockPos(-2, 120, 75), ArrowAlignPatterns.framePos(cellAtRowColumn(4, 4)));
        assertEquals(new BlockPos(-2, 124, 75), ArrowAlignPatterns.framePos(cellAtRowColumn(0, 4)));
        for (int cell = 0; cell < ArrowAlignPatterns.CELLS; cell++) {
            assertEquals(cell, ArrowAlignPatterns.cellAt(ArrowAlignPatterns.framePos(cell)));
        }
        assertEquals(-1, ArrowAlignPatterns.cellAt(new BlockPos(-3, 122, 77)));
        assertEquals(-1, ArrowAlignPatterns.cellAt(new BlockPos(-1, 122, 77)));
        assertEquals(-1, ArrowAlignPatterns.cellAt(new BlockPos(-2, 119, 77)));
        assertEquals(-1, ArrowAlignPatterns.cellAt(new BlockPos(-2, 125, 77)));
        assertEquals(-1, ArrowAlignPatterns.cellAt(new BlockPos(-2, 122, 74)));
        assertEquals(-1, ArrowAlignPatterns.cellAt(new BlockPos(-2, 122, 80)));
    }

    private static int cellAtRowColumn(int row, int column) {
        for (int cell = 0; cell < ArrowAlignPatterns.CELLS; cell++) {
            if (ArrowAlignPatterns.row(cell) == row && ArrowAlignPatterns.column(cell) == column) return cell;
        }
        throw new AssertionError("no cell at " + row + ", " + column);
    }

    /** Labels stand 0.3 in front of the wall face (X -2), centred on their frame. */
    @Test
    void labelsStandInFrontOfTheirFrame() {
        assertEquals(new Vec3(-1.7, 120.5, 75.5), ArrowAlignPatterns.labelPos(0));
        assertEquals(new Vec3(-1.7, 124.5, 79.5), ArrowAlignPatterns.labelPos(24));
        assertEquals(new Vec3(-1.7, 122.5, 77.5), ArrowAlignPatterns.labelPos(12));
    }

    @Test
    void theArrowsPickTheirLayoutWhateverWayTheyPoint() {
        for (int p = 0; p < ArrowAlignPatterns.count(); p++) {
            for (int rotation = 0; rotation < ArrowAlignPatterns.ROTATIONS; rotation++) {
                assertEquals(p, ArrowAlignPatterns.match(wall(p, rotation)), "layout " + p + " at " + rotation);
            }
        }
    }

    /** A wall that is not loaded yet, or a layout nobody knows, matches nothing. */
    @Test
    void anIncompleteOrUnknownWallMatchesNothing() {
        int[] empty = new int[ArrowAlignPatterns.CELLS];
        Arrays.fill(empty, ArrowAlignPatterns.NONE);
        assertEquals(-1, ArrowAlignPatterns.match(empty));

        int[] missing = wall(4, 0);
        missing[0] = ArrowAlignPatterns.NONE;
        assertEquals(-1, ArrowAlignPatterns.match(missing));

        int[] extra = wall(6, 0);
        extra[0] = 3;
        assertEquals(-1, ArrowAlignPatterns.match(extra));

        assertEquals(-1, ArrowAlignPatterns.match(new int[24]));
    }

    @Test
    void clicksCountForwardsAroundTheEightSteps() {
        assertEquals(0, ArrowAlignPatterns.clicks(3, 3));
        assertEquals(2, ArrowAlignPatterns.clicks(1, 3));
        assertEquals(2, ArrowAlignPatterns.clicks(7, 1));
        assertEquals(6, ArrowAlignPatterns.clicks(1, 7));
        assertEquals(7, ArrowAlignPatterns.clicks(4, 3));
        assertEquals(1, ArrowAlignPatterns.clicks(0, 1));
    }

    @Test
    void clicksLeftPerFrame() {
        int[] rotations = wall(6, 1);
        // Layout 6 wants every arrow at 1 (right): all done, nothing to show.
        assertArrayEquals(new int[ArrowAlignPatterns.CELLS], ArrowAlignPatterns.clicksLeft(6, rotations));

        rotations[5] = 0;
        rotations[7] = 3;
        int[] left = ArrowAlignPatterns.clicksLeft(6, rotations);
        assertEquals(1, left[5]);
        assertEquals(6, left[7]);
        assertEquals(0, left[9]);
        // No arrow, no count.
        assertEquals(0, left[0]);

        // Without a layout there is nothing to count against.
        assertArrayEquals(new int[ArrowAlignPatterns.CELLS], ArrowAlignPatterns.clicksLeft(-1, rotations));
    }
}
