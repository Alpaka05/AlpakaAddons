package net.alpaka.addons.features.dungeon.puzzle;

import java.util.Arrays;

/**
 * Finds a walk over an Ice Fill floor that steps on every free cell exactly once, from a given
 * cell to a given cell, moving only straight: a Hamiltonian path on a small grid
 * (puzzles-floor.md 2.6).
 *
 * The search goes depth-first and cuts a branch as soon as it cannot work any more: a cell left
 * behind with fewer than two ways in and out (one for the last cell), or the cells still to visit
 * split apart. A chessboard count rules out impossible floors before it starts. With that, the
 * known layouts take a few dozen to a few hundred steps.
 *
 * Every path that covers the floor is a solution, but one with fewer turns is easier to walk, and
 * a fast run with many turns is how the server sees a diagonal step and resets the floor. So after
 * the first path the search goes on looking for one with fewer turns, keeping the best, until it
 * has tried them all or used up its step budget; the budget keeps an odd floor from stalling the
 * game, and the answer then is simply the best path found so far.
 *
 * Cells are numbered {@code row * width + col} on a grid of at most 64 cells. Pure: no game here.
 */
public final class IceFillSearch {

    /** Steps the search may take in all; well above what the known floors need. */
    public static final int DEFAULT_BUDGET = 200_000;

    /** A path from the start to the end cell, its number of turns, and whether no path has fewer. */
    public record Path(int[] cells, int turns, boolean fewestTurns) {
        public int length() {
            return cells.length;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Path p && Arrays.equals(cells, p.cells) && turns == p.turns && fewestTurns == p.fewestTurns;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(cells) * 31 + turns;
        }

        @Override
        public String toString() {
            return "Path" + Arrays.toString(cells) + " turns=" + turns + (fewestTurns ? "" : " (not proven fewest)");
        }
    }

    private final int width;
    private final long free;
    private final int end;
    private final long[] neighbours;
    private final long firstCol;
    private final long lastCol;
    private final int budget;

    private int steps;
    private final int[] path;
    private int[] bestPath;
    private int bestTurns;
    private boolean outOfBudget;

    private IceFillSearch(int width, int height, long free, int end, int budget) {
        this.width = width;
        this.free = free;
        this.end = end;
        this.budget = budget;
        this.neighbours = new long[width * height];
        long first = 0L;
        long last = 0L;
        for (int row = 0; row < height; row++) {
            first |= 1L << (row * width);
            last |= 1L << (row * width + width - 1);
        }
        this.firstCol = first;
        this.lastCol = last;
        for (int cell = 0; cell < width * height; cell++) {
            if ((free >>> cell & 1L) != 0) neighbours[cell] = spread(1L << cell) & free;
        }
        this.path = new int[Long.bitCount(free)];
    }

    /**
     * A path over every cell of {@code free} from {@code start} to {@code end}, with as few turns
     * as the budget allows to find, or null when there is none (or the budget ran out before any).
     */
    public static Path solve(int width, int height, long free, int start, int end, int budget) {
        if (width <= 0 || height <= 0 || width * height > 64) throw new IllegalArgumentException("grid " + width + "x" + height);
        if ((free >>> start & 1L) == 0 || (free >>> end & 1L) == 0) return null;
        if (!parityAllows(width, free, start, end)) return null;
        IceFillSearch search = new IceFillSearch(width, height, free, end, budget);
        return search.run(start);
    }

    public static Path solve(int width, int height, long free, int start, int end) {
        return solve(width, height, free, start, end, DEFAULT_BUDGET);
    }

    /**
     * The chessboard test: each step changes colour, so a path over all cells needs the two colours
     * to be balanced to fit its two ends.
     */
    static boolean parityAllows(int width, long free, int start, int end) {
        int[] count = new int[2];
        for (long rest = free; rest != 0; rest &= rest - 1) {
            int cell = Long.numberOfTrailingZeros(rest);
            count[colour(width, cell)]++;
        }
        int total = count[0] + count[1];
        if (total == 1) return start == end;
        if (start == end) return false;
        int s = colour(width, start);
        int e = colour(width, end);
        if (s == e) return count[s] == count[1 - s] + 1;
        return count[0] == count[1];
    }

    private static int colour(int width, int cell) {
        return (cell / width + cell % width) & 1;
    }

    private Path run(int start) {
        bestTurns = Integer.MAX_VALUE;
        path[0] = start;
        long visited = 1L << start;
        if (visited == free) return new Path(new int[]{start}, 0, true);
        search(start, visited, 1, -1, 0);
        if (bestPath == null) return null;
        return new Path(bestPath, bestTurns, !outOfBudget);
    }

    /**
     * Extends the path standing on {@code cell}, which is {@code length} cells long and came in
     * with {@code lastDir}; tries going straight on first, since that is how short paths are found
     * early and the turn bound starts cutting.
     */
    private void search(int cell, long visited, int length, int lastDir, int turns) {
        if (++steps > budget) {
            outOfBudget = true;
            return;
        }
        long options = neighbours[cell] & ~visited;
        for (int i = 0; i < 4; i++) {
            // Straight on first, then the other ways in a fixed order.
            int dir = lastDir < 0 ? i : (i == 0 ? lastDir : (i <= lastDir ? i - 1 : i));
            int next = step(cell, dir);
            if (next < 0 || (options >>> next & 1L) == 0) continue;
            int nextTurns = turns + (lastDir >= 0 && dir != lastDir ? 1 : 0);
            if (nextTurns >= bestTurns) continue;
            long nextVisited = visited | 1L << next;
            path[length] = next;
            if (nextVisited == free) {
                if (next == end) {
                    bestTurns = nextTurns;
                    bestPath = Arrays.copyOf(path, length + 1);
                }
                continue;
            }
            if (next == end || !stillPossible(next, nextVisited)) continue;
            search(next, nextVisited, length + 1, dir, nextTurns);
            if (outOfBudget) return;
        }
    }

    /** The cell one step from {@code cell} in {@code dir} (0 +col, 1 -col, 2 +row, 3 -row), or -1 off the grid. */
    private int step(int cell, int dir) {
        int col = cell % width;
        return switch (dir) {
            case 0 -> col == width - 1 ? -1 : cell + 1;
            case 1 -> col == 0 ? -1 : cell - 1;
            case 2 -> cell + width < neighbours.length ? cell + width : -1;
            default -> cell - width;
        };
    }

    /** Whether the cells not visited yet can still all be walked, now that the path stands on {@code cell}. */
    private boolean stillPossible(int cell, long visited) {
        long left = free & ~visited;
        long open = left | 1L << cell;
        for (long rest = left; rest != 0; rest &= rest - 1) {
            int q = Long.numberOfTrailingZeros(rest);
            int ways = Long.bitCount(neighbours[q] & open);
            if (ways < (q == end ? 1 : 2)) return false;
        }
        long reached = neighbours[cell] & left;
        if (reached == 0) return false;
        while (true) {
            long grown = (reached | spread(reached)) & left;
            if (grown == reached) return grown == left;
            reached = grown;
        }
    }

    /** Every cell next to one in {@code cells}, on the grid. */
    private long spread(long cells) {
        long all = (neighbours.length == 64) ? -1L : (1L << neighbours.length) - 1;
        return ((cells << width) | (cells >>> width) | ((cells & ~lastCol) << 1) | ((cells & ~firstCol) >>> 1)) & all;
    }

    /** Direction changes along a path of neighbouring cells; the first step is not a turn. */
    public static int turns(int[] cells) {
        int turns = 0;
        int last = 0;
        for (int i = 1; i < cells.length; i++) {
            int d = cells[i] - cells[i - 1];
            if (i > 1 && d != last) turns++;
            last = d;
        }
        return turns;
    }
}
