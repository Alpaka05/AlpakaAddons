/*
 * Solution data adapted from Odin by odtheking, BSD-3-Clause, see CREDITS.md.
 * (The nine layouts in PATTERNS; Odin's features/impl/boss/ArrowAlign.kt, possibleSolutions.)
 */
package net.alpaka.addons.features.dungeon.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The Arrow Align wall (Goldor section 3) and its nine known layouts: where each item frame hangs,
 * which layout a wall shows, and how many right-clicks each arrow still needs. Pure, so the tables
 * and the matching can be tested without a game.
 *
 * The 5 x 5 frames hang in the blocks at X -2, Y 120..124, Z 75..79, on the east face of the wall
 * at X -3; the player stands at about (0, 120, 77) and looks west. A cell is numbered the way the
 * layout table is: {@code (y - 120) + (z - 75) * 5}, so 0 is the bottom right corner as the player
 * sees it and 24 the top left.
 *
 * A layout lists, for every cell, the rotation its arrow must end at, or {@link #NONE} where there
 * is no arrow (an empty cell, or the lime and red wool at the ends of the path). The cells that
 * hold an arrow differ between all nine layouts, so the arrows present pick the layout on their
 * own. Every right-click turns a frame one step of eight (45 degrees), always the same way, so the
 * clicks an arrow still needs are its distance to the target rotation counted forwards.
 */
public final class ArrowAlignPatterns {

    /** No arrow in this cell. */
    public static final int NONE = -1;
    public static final int CELLS = 25;
    /** {@code ItemFrame.getRotation()} has eight steps. */
    public static final int ROTATIONS = 8;

    /** The blocks the frames hang in. */
    public static final int FRAME_X = -2;
    public static final int MIN_Y = 120;
    public static final int MIN_Z = 75;
    /** The face of the wall the frames hang on. */
    public static final double WALL_FACE_X = -2.0;
    /**
     * Labels stand this far in front of the wall: clear of the frame and the arrow in it (together
     * about 0.1 deep), so the frame does not cover the number, and still close enough to read as
     * belonging to that frame.
     */
    public static final double LABEL_OFFSET = 0.3;
    /** Where the player stands to turn the arrows. */
    public static final Vec3 STAND = new Vec3(0.5, 120.0, 77.5);

    /**
     * The target rotation of every cell, per layout, in the cell order above; {@link #NONE} where
     * the layout has no arrow. Seen by the player (rows from the top, the left column first;
     * R = 1, D = 3, L = 5, U = 7 point right, down, left and up), the layouts are:
     * <pre>
     *   0      1      2      3      4      5      6      7      8
     * RRD..  RR.LL  DLL..  .....  RRRRD  RRD..  .RRR.  .RRD.  .RD..
     * U.D..  U...U  D.U..  D...D  U...D  U.D.U  .....  .U.D.  ..RR.
     * ..D..  UL.RU  .....  D...D  U...D  U.D.U  .RRR.  .U.D.  .RU..
     * ..D.U  .U.U.  ..D.U  D.U.D  U.U.D  U.D.U  .....  .U.D.  ..RR.
     * ..RRU  .....  ..RRU  RRULL  ..ULL  ..RRU  .RRR.  .U.R.  .RU..
     * </pre>
     */
    private static final int[][] PATTERNS = {
            {7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1},
            {-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1},
            {7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3},
            {5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1},
            {5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
            {7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
            {-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1},
    };

    private ArrowAlignPatterns() {}

    public static int count() {
        return PATTERNS.length;
    }

    /** Layout {@code pattern}'s target rotation for {@code cell}, or {@link #NONE}. */
    public static int target(int pattern, int cell) {
        return PATTERNS[pattern][cell];
    }

    // --- where the cells are ---

    /** The cell a frame hanging in {@code pos} belongs to, or -1 when it is not one of the wall's. */
    public static int cellAt(BlockPos pos) {
        int dy = pos.getY() - MIN_Y;
        int dz = pos.getZ() - MIN_Z;
        if (pos.getX() != FRAME_X || dy < 0 || dy > 4 || dz < 0 || dz > 4) return -1;
        return dy + dz * 5;
    }

    /** The block the frame of {@code cell} hangs in. */
    public static BlockPos framePos(int cell) {
        return new BlockPos(FRAME_X, MIN_Y + cell % 5, MIN_Z + cell / 5);
    }

    /** Where the label of {@code cell} goes: centred on its frame, a little in front of the wall. */
    public static Vec3 labelPos(int cell) {
        BlockPos frame = framePos(cell);
        return new Vec3(WALL_FACE_X + LABEL_OFFSET, frame.getY() + 0.5, frame.getZ() + 0.5);
    }

    /** The cell's row from the top as the player sees it (0..4). */
    public static int row(int cell) {
        return 4 - cell % 5;
    }

    /** The cell's column from the player's left (0..4): the player looks west, so their left is south. */
    public static int column(int cell) {
        return 4 - cell / 5;
    }

    // --- which layout, and how far each arrow is from it ---

    /**
     * The layout whose arrow cells are exactly the cells holding an arrow in {@code rotations}
     * ({@link #NONE} for no arrow), or -1. A wall that is only partly loaded, or a layout nobody
     * has seen yet, matches nothing, and then nothing should be shown.
     */
    public static int match(int[] rotations) {
        if (rotations.length != CELLS) return -1;
        for (int p = 0; p < PATTERNS.length; p++) {
            if (sameCells(PATTERNS[p], rotations)) return p;
        }
        return -1;
    }

    private static boolean sameCells(int[] pattern, int[] rotations) {
        for (int i = 0; i < CELLS; i++) {
            if ((pattern[i] == NONE) != (rotations[i] == NONE)) return false;
        }
        return true;
    }

    /** Right-clicks that turn an arrow from {@code current} to {@code target}: 0..7. */
    public static int clicks(int current, int target) {
        return Math.floorMod(target - current, ROTATIONS);
    }

    /**
     * Right-clicks still needed per cell under {@code pattern}: 0 where the arrow is right, where
     * there is no arrow, and everywhere when {@code pattern} is -1.
     */
    public static int[] clicksLeft(int pattern, int[] rotations) {
        int[] left = new int[CELLS];
        if (pattern < 0 || pattern >= PATTERNS.length || rotations.length != CELLS) return left;
        for (int i = 0; i < CELLS; i++) {
            int target = PATTERNS[pattern][i];
            if (target == NONE || rotations[i] == NONE) continue;
            left[i] = clicks(rotations[i], target);
        }
        return left;
    }
}
