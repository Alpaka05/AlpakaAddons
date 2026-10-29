package net.alpaka.addons.features.dungeon.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * The Boulder room's board as plain numbers: which of its 42 cells hold a box, where the player can
 * walk, and what a push does. Pure, so {@link BoulderSearch} and the tests can use it without a game.
 *
 * The room has 7 columns and 6 rows of cells, each 3 x 3 blocks; a box fills a whole cell. Behind
 * the board, at the low-Z side of the room frame, runs the corridor the player comes in from; the
 * chest is past the far row. In room coordinates (puzzles-floor.md 1.3) a cell's centre is at
 * {@code x = 24 - 3 * col}, {@code z = 24 - 3 * row}: row 0 is the far row by the chest, row 5 the
 * row next to the corridor, and the corridor itself is row 6 (z 6), where no box can go.
 *
 * A board is a {@code long} with bit {@code row * 7 + col} set for every box, the same order as the
 * 42-character keys other mods use for these layouts. The walking grid adds the corridor as bits 42
 * to 48.
 *
 * Clicking the button (or sign) on one face of a box moves the box one cell away from that face.
 * The player has to stand next to that face to click it, so the cell on that side must be one they
 * can walk to. A push only goes into an empty cell: two boxes squashed into one is a Hypixel bug,
 * and nothing here ever plans on it (critic.md 3.1).
 */
public final class BoulderBoard {

    public static final int COLS = 7;
    /** Rows that can hold a box; the corridor is the row after them. */
    public static final int ROWS = 6;
    public static final int CELLS = ROWS * COLS;
    /** The corridor's row in the walking grid. */
    public static final int CORRIDOR_ROW = ROWS;

    /** Room Y of a box's lowest layer, which is also where the buttons sit. */
    public static final int BOX_Y = 65;
    /** Room Y of the plank layer that, together with {@link #BOX_Y}, tells a box from an empty cell. */
    public static final int BOX_CHECK_Y = 66;

    /** Every cell of the walking grid: the board and the corridor. */
    static final long WALK = (1L << ((ROWS + 1) * COLS)) - 1;
    /** The board's cells, where boxes are. */
    public static final long BOARD = (1L << CELLS) - 1;
    static final long CORRIDOR = WALK & ~BOARD;
    /** Row 0: reaching any cell of it opens the way to the chest. */
    static final long BACK_ROW = (1L << COLS) - 1;
    private static final long FIRST_COL;
    private static final long LAST_COL;

    static {
        long first = 0L;
        long last = 0L;
        for (int row = 0; row <= ROWS; row++) {
            first |= 1L << (row * COLS);
            last |= 1L << (row * COLS + COLS - 1);
        }
        FIRST_COL = first;
        LAST_COL = last;
    }

    /**
     * Which way a box moves, seen from the corridor looking at the chest. The handle the player
     * clicks is on the opposite face. {@code dx}/{@code dz} are the move in room coordinates.
     */
    public enum Push {
        /** Towards the chest, room +Z. */
        FORWARD(-1, 0, 0, 1),
        /** Room -X: right, for someone facing the chest. */
        RIGHT(0, 1, -1, 0),
        /** Room +X. */
        LEFT(0, -1, 1, 0),
        /** Back towards the corridor, room -Z. No known plan needs it, and the face may have no handle. */
        BACK(1, 0, 0, -1);

        public final int dRow;
        public final int dCol;
        public final int dx;
        public final int dz;

        Push(int dRow, int dCol, int dx, int dz) {
            this.dRow = dRow;
            this.dCol = dCol;
            this.dx = dx;
            this.dz = dz;
        }
    }

    private BoulderBoard() {}

    public static int index(int row, int col) {
        return row * COLS + col;
    }

    public static int row(int index) {
        return index / COLS;
    }

    public static int col(int index) {
        return index % COLS;
    }

    /** Room X of a column's centre. */
    public static int centreX(int col) {
        return 24 - 3 * col;
    }

    /** Room Z of a row's centre; row 6 is the corridor. */
    public static int centreZ(int row) {
        return 24 - 3 * row;
    }

    // --- keys, for tests and the debug line ---

    /** A board from its 42-character key, row 0 first, '1' for a box. */
    public static long parse(String key) {
        if (key.length() != CELLS) throw new IllegalArgumentException("a board key has 42 cells: " + key);
        long boxes = 0L;
        for (int i = 0; i < CELLS; i++) {
            char c = key.charAt(i);
            if (c == '1') boxes |= 1L << i;
            else if (c != '0') throw new IllegalArgumentException("not a board key: " + key);
        }
        return boxes;
    }

    public static String key(long boxes) {
        StringBuilder out = new StringBuilder(CELLS);
        for (int i = 0; i < CELLS; i++) out.append((boxes >>> i & 1L) != 0 ? '1' : '0');
        return out.toString();
    }

    // --- walking and pushing ---

    /** The cells of the walking grid the player can get to from the corridor, around the boxes. */
    public static long reachable(long boxes) {
        long free = WALK & ~boxes;
        long reach = CORRIDOR;
        while (true) {
            long grown = reach
                    | (reach >>> COLS)
                    | (reach << COLS)
                    | ((reach & ~LAST_COL) << 1)
                    | ((reach & ~FIRST_COL) >>> 1);
            grown &= free;
            if (grown == reach) return reach;
            reach = grown;
        }
    }

    /** Whether the player can already walk from the corridor to the far row: nothing left to push. */
    public static boolean open(long boxes) {
        return (reachable(boxes) & BACK_ROW) != 0;
    }

    /**
     * The walking-grid cell the player stands in to click the handle for pushing the box at
     * {@code index} in {@code push}, or -1 when that is off the grid.
     */
    public static int handleCell(int index, Push push) {
        int row = row(index) - push.dRow;
        int col = col(index) - push.dCol;
        if (row < 0 || row > CORRIDOR_ROW || col < 0 || col >= COLS) return -1;
        return index(row, col);
    }

    /** The cell the box at {@code index} moves into, or -1 when that is off the board. */
    public static int targetCell(int index, Push push) {
        int row = row(index) + push.dRow;
        int col = col(index) + push.dCol;
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return -1;
        return index(row, col);
    }

    /**
     * The board after pushing the box at {@code index}, or -1 when that push cannot happen: no box
     * there, the target is off the board or not empty, or the player cannot reach the handle.
     * {@code reach} is {@link #reachable(long)} of {@code boxes}.
     */
    public static long push(long boxes, long reach, int index, Push push) {
        if ((boxes >>> index & 1L) == 0) return -1L;
        int target = targetCell(index, push);
        if (target < 0 || (boxes >>> target & 1L) != 0) return -1L;
        int handle = handleCell(index, push);
        if (handle < 0 || (reach >>> handle & 1L) == 0) return -1L;
        return (boxes & ~(1L << index)) | (1L << target);
    }

    // --- where things are in the room ---

    /**
     * The block a box's handle normally sits in, in room coordinates: just outside the face the
     * player clicks, on the bottom layer.
     */
    public static BlockPos handleSpot(int index, Push push) {
        return new BlockPos(centreX(col(index)) - 2 * push.dx, BOX_Y, centreZ(row(index)) - 2 * push.dz);
    }

    /** The 3 x 3 cells of a box from {@link #BOX_Y} up to {@code topY}, in room coordinates. */
    public static AABB boxVolume(int index, int topY) {
        int cx = centreX(col(index));
        int cz = centreZ(row(index));
        return new AABB(cx - 1, BOX_Y, cz - 1, cx + 2, topY, cz + 2);
    }
}
