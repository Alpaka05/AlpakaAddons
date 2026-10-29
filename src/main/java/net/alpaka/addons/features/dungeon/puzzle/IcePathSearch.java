package net.alpaka.addons.features.dungeon.puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Ice Path board and the fewest hits that slide the silverfish to the gate. Pure.
 *
 * The board is 17 x 17 cells of ice, walked at room Y 67, with blocks standing on some cells
 * (puzzles-floor.md 3.2). Row 0 is the far side with the gate, row 16 the near side; column 0 is
 * room X 23 and column 16 room X 7. So a cell's room position is {@code x = 23 - col},
 * {@code z = 24 - row}.
 *
 * A hit sends the silverfish the way the player faces, and it slides until the next cell is a
 * block or the edge. The search is breadth-first over the cells where it can come to rest, so the
 * route has as few hits as possible. It counts as done once a slide towards the gate stops in row
 * 0 in front of the gate (columns 7 to 9, room X 14 to 16): from there the silverfish slides on
 * through it.
 */
public final class IcePathSearch {

    public static final int SIZE = 17;
    public static final int CELLS = SIZE * SIZE;
    /** Room Y the silverfish slides at; the ice is the layer below. */
    public static final int WALK_Y = 67;
    /** The columns in front of the gate. */
    public static final int GATE_MIN_COL = 7;
    public static final int GATE_MAX_COL = 9;

    /** A slide direction, named by its way in room coordinates. */
    public enum Slide {
        /** Towards the gate. */
        PLUS_Z(-1, 0, 0, 1),
        MINUS_Z(1, 0, 0, -1),
        PLUS_X(0, -1, 1, 0),
        MINUS_X(0, 1, -1, 0);

        public final int dRow;
        public final int dCol;
        public final int dx;
        public final int dz;

        Slide(int dRow, int dCol, int dx, int dz) {
            this.dRow = dRow;
            this.dCol = dCol;
            this.dx = dx;
            this.dz = dz;
        }
    }

    /**
     * The silverfish's resting cells from where it is now to the gate, and the slide that leads to
     * each next one; {@code slides.size() == stops.size() - 1}.
     */
    public record Route(List<Integer> stops, List<Slide> slides) {
        public int hits() {
            return slides.size();
        }

        /** The same route after its first hit was made. */
        public Route rest() {
            return new Route(stops.subList(1, stops.size()), slides.subList(1, slides.size()));
        }
    }

    private IcePathSearch() {}

    public static int cell(int row, int col) {
        return row * SIZE + col;
    }

    public static int row(int cell) {
        return cell / SIZE;
    }

    public static int col(int cell) {
        return cell % SIZE;
    }

    public static int relX(int cell) {
        return 23 - col(cell);
    }

    public static int relZ(int cell) {
        return 24 - row(cell);
    }

    /** The cell at room X/Z, or -1 off the board. */
    public static int cellAt(int relX, int relZ) {
        int col = 23 - relX;
        int row = 24 - relZ;
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return -1;
        return cell(row, col);
    }

    /** The neighbouring cell in {@code slide}, or -1 off the board. */
    public static int step(int cell, Slide slide) {
        int row = row(cell) + slide.dRow;
        int col = col(cell) + slide.dCol;
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return -1;
        return cell(row, col);
    }

    /** Where a slide from {@code from} comes to rest. */
    public static int slide(boolean[] blocked, int from, Slide slide) {
        int at = from;
        while (true) {
            int next = step(at, slide);
            if (next < 0 || blocked[next]) return at;
            at = next;
        }
    }

    /** Whether the silverfish has reached the gate: row 0 in front of it. */
    public static boolean atGate(int cell) {
        return row(cell) == 0 && col(cell) >= GATE_MIN_COL && col(cell) <= GATE_MAX_COL;
    }

    /**
     * The fewest hits from {@code start} to the gate, or null when it cannot get there. A route of
     * no hits means it is there already.
     */
    public static Route solve(boolean[] blocked, int start) {
        if (blocked.length != CELLS) throw new IllegalArgumentException("board of " + blocked.length);
        if (atGate(start)) return new Route(List.of(start), List.of());
        int[] parent = new int[CELLS];
        Slide[] via = new Slide[CELLS];
        Arrays.fill(parent, -2);
        parent[start] = -1;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int at = queue.poll();
            for (Slide slide : Slide.values()) {
                int stop = slide(blocked, at, slide);
                if (stop == at) continue;
                // Checked before "seen": a gate cell reached sideways earlier still counts when a
                // later slide comes in straight towards the gate.
                if (slide == Slide.PLUS_Z && atGate(stop)) return routeTo(at, parent, via, stop, slide);
                if (parent[stop] != -2) continue;
                parent[stop] = at;
                via[stop] = slide;
                queue.add(stop);
            }
        }
        return null;
    }

    private static Route routeTo(int last, int[] parent, Slide[] via, int gate, Slide into) {
        List<Integer> stops = new ArrayList<>();
        List<Slide> slides = new ArrayList<>();
        stops.add(gate);
        slides.add(into);
        for (int at = last; at >= 0; at = parent[at]) {
            stops.add(at);
            if (parent[at] >= 0) slides.add(via[at]);
        }
        Collections.reverse(stops);
        Collections.reverse(slides);
        return new Route(List.copyOf(stops), List.copyOf(slides));
    }
}
