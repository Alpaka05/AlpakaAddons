package net.alpaka.addons.features.dungeon.puzzle;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the three Ice Fill floors are, and how their cells are numbered. Pure.
 *
 * The room climbs in three stages: a 3 x 3 floor, a 5 x 5 one a block higher and a 7 x 7 one a
 * block above that (puzzles-floor.md 2.2). On each, the player comes in at the middle of the
 * near edge and leaves at the middle of the far edge, towards the chest at the high-Z side of the
 * room frame. A floor's free cells are ice one block below the walking level with air above it;
 * stepping on one turns the ice to packed ice.
 *
 * A cell's number is {@code a * size + b}: {@code a} counts room Z from the near edge, {@code b}
 * room X from the floor's low-X side. The start is then {@code (0, size / 2)} and the end
 * {@code (size - 1, size / 2)}, both at room X 15.
 */
public final class IceFillBoard {

    /**
     * One floor: its number (0 lowest), the room Y the player walks at (the ice is one below), and
     * its low corner and size in room X/Z.
     */
    public record Floor(int number, int walkY, int x0, int z0, int size) {

        public int cells() {
            return size * size;
        }

        public int cell(int a, int b) {
            return a * size + b;
        }

        /** Where the player steps on: the middle of the near edge. */
        public int start() {
            return cell(0, size / 2);
        }

        /** Where the player steps off: the middle of the far edge. */
        public int end() {
            return cell(size - 1, size / 2);
        }

        public int relX(int cell) {
            return x0 + cell % size;
        }

        public int relZ(int cell) {
            return z0 + cell / size;
        }

        /** The cell at room X/Z, or -1 off this floor. */
        public int cellAt(int relX, int relZ) {
            int b = relX - x0;
            int a = relZ - z0;
            if (a < 0 || a >= size || b < 0 || b >= size) return -1;
            return cell(a, b);
        }

        /** Room Z of the far edge, where the floor is left. */
        public int exitZ() {
            return z0 + size;
        }

        /** The cells next to {@code cell} on this floor, as a mask. */
        public long neighbours(int cell) {
            int a = cell / size;
            int b = cell % size;
            long out = 0L;
            if (b > 0) out |= 1L << (cell - 1);
            if (b < size - 1) out |= 1L << (cell + 1);
            if (a > 0) out |= 1L << (cell - size);
            if (a < size - 1) out |= 1L << (cell + size);
            return out;
        }
    }

    /** The three floors, lowest first (puzzles-floor.md 2.2; Skyblocker, Odin and Skytils agree). */
    public static final List<Floor> FLOORS = List.of(
            new Floor(0, 70, 14, 7, 3),
            new Floor(1, 71, 13, 12, 5),
            new Floor(2, 72, 12, 19, 7));

    private IceFillBoard() {}

    /**
     * The cell the walker is on, read from the packed cells alone: following them from the start,
     * each next packed cell must be the only one left beside the last. -1 when they do not make one
     * clear walk from the start (a cell nobody walked, or two ways the walk could have gone).
     */
    public static int walkerFromTrail(Floor floor, long packed) {
        int at = floor.start();
        if ((packed >>> at & 1L) == 0) return -1;
        long walked = 1L << at;
        while (true) {
            long next = floor.neighbours(at) & packed & ~walked;
            if (next == 0) break;
            if (Long.bitCount(next) > 1) return -1;
            at = Long.numberOfTrailingZeros(next);
            walked |= next;
        }
        return walked == packed ? at : -1;
    }

    /** The cells of a path where it turns, plus its two ends: enough to draw it as straight lines. */
    public static int[] corners(int[] cells) {
        if (cells.length <= 2) return cells.clone();
        List<Integer> out = new ArrayList<>();
        out.add(cells[0]);
        for (int i = 1; i < cells.length - 1; i++) {
            if (cells[i] - cells[i - 1] != cells[i + 1] - cells[i]) out.add(cells[i]);
        }
        out.add(cells[cells.length - 1]);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
