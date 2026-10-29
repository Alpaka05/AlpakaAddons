package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.BoulderBoard.Push;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the fewest pushes that open a way from the corridor to the far row of a Boulder board.
 *
 * A breadth-first search over whole boards: each push of a box the player can reach, into an empty
 * cell, is one step, and walking costs nothing. The first board that lets the player walk through
 * ends it, so the plan has as few pushes as possible. On the eight layouts known so far that is at
 * most 9 pushes and about 11 000 boards (puzzles-floor.md 1.9); an unknown layout that would take
 * far more gives up at {@link #MAX_BOARDS} rather than stall the game.
 *
 * Pushes towards the chest and to either side are tried first. Backward pushes are only used when
 * nothing else works: no known plan needs one, and whether those faces have a handle at all is not
 * known. Boxes are never pushed into each other (critic.md 3.1).
 *
 * Pure: the caller reads the board, and says which first pushes really have a handle to click.
 */
public final class BoulderSearch {

    /** Boards looked at before the search gives up. */
    public static final int MAX_BOARDS = 200_000;

    private static final Set<Push> SIDEWAYS_AND_FORWARD = Collections.unmodifiableSet(EnumSet.of(Push.FORWARD, Push.RIGHT, Push.LEFT));
    private static final Set<Push> ALL_PUSHES = Collections.unmodifiableSet(EnumSet.allOf(Push.class));

    /** One push: the box in {@code cell} moves in {@code push}. */
    public record Step(int cell, Push push) {
        /** Where the box ends up. */
        public int target() {
            return BoulderBoard.targetCell(cell, push);
        }

        /** The board after this push. */
        public long apply(long boxes) {
            return (boxes & ~(1L << cell)) | (1L << target());
        }
    }

    public enum Outcome {
        /** The way is already open; nothing to push. */
        OPEN,
        /** A plan was found. */
        PLAN,
        /** No pushes can open this board. */
        NO_PLAN,
        /** The search gave up before finding a plan. */
        TOO_HARD
    }

    /** The search's answer; {@code steps} is empty unless the outcome is {@link Outcome#PLAN}. */
    public record Result(Outcome outcome, List<Step> steps, int boards) {
        public boolean found() {
            return outcome == Outcome.OPEN || outcome == Outcome.PLAN;
        }
    }

    /** Which of the pushes possible right now may start the plan (a handle is there to click). */
    @FunctionalInterface
    public interface FirstPush {
        boolean allowed(int cell, Push push);

        FirstPush ANY = (cell, push) -> true;
    }

    private BoulderSearch() {}

    /** The fewest pushes that open {@code boxes}; the first one must pass {@code firstPush}. */
    public static Result solve(long boxes, FirstPush firstPush) {
        Result result = solve(boxes, firstPush, SIDEWAYS_AND_FORWARD, MAX_BOARDS);
        if (result.outcome() != Outcome.NO_PLAN) return result;
        return solve(boxes, firstPush, ALL_PUSHES, MAX_BOARDS);
    }

    static Result solve(long boxes, FirstPush firstPush, Set<Push> pushes, int maxBoards) {
        boxes &= BoulderBoard.BOARD;
        if (BoulderBoard.open(boxes)) return new Result(Outcome.OPEN, List.of(), 1);
        Push[] order = Arrays.stream(Push.values()).filter(pushes::contains).toArray(Push[]::new);

        // The boards in the order they were found, each with the board it came from and the push
        // that made it, so the plan can be read back from the end.
        long[] boards = new long[1024];
        int[] parent = new int[1024];
        int[] move = new int[1024];
        Set<Long> seen = new HashSet<>();
        boards[0] = boxes;
        parent[0] = -1;
        int size = 1;
        seen.add(boxes);

        for (int head = 0; head < size; head++) {
            long board = boards[head];
            long reach = BoulderBoard.reachable(board);
            for (long rest = board; rest != 0; rest &= rest - 1) {
                int cell = Long.numberOfTrailingZeros(rest);
                for (Push push : order) {
                    if (head == 0 && !firstPush.allowed(cell, push)) continue;
                    long next = BoulderBoard.push(board, reach, cell, push);
                    if (next < 0 || !seen.add(next)) continue;
                    if (size == maxBoards) return new Result(Outcome.TOO_HARD, List.of(), size);
                    if (size == boards.length) {
                        boards = Arrays.copyOf(boards, size * 2);
                        parent = Arrays.copyOf(parent, size * 2);
                        move = Arrays.copyOf(move, size * 2);
                    }
                    boards[size] = next;
                    parent[size] = head;
                    move[size] = cell * 4 + push.ordinal();
                    size++;
                    if (BoulderBoard.open(next)) return new Result(Outcome.PLAN, planTo(size - 1, parent, move), size);
                }
            }
        }
        return new Result(Outcome.NO_PLAN, List.of(), size);
    }

    private static List<Step> planTo(int node, int[] parent, int[] move) {
        List<Step> steps = new ArrayList<>();
        for (int at = node; parent[at] >= 0; at = parent[at]) {
            steps.add(new Step(move[at] / 4, Push.values()[move[at] % 4]));
        }
        Collections.reverse(steps);
        return List.copyOf(steps);
    }

    /**
     * The rest of {@code plan} when {@code board} is exactly what its first push makes of
     * {@code from}, otherwise null. The rest is then still a shortest plan: a shorter one from
     * {@code board} would have made a shorter one from {@code from} too.
     */
    public static List<Step> afterFirst(List<Step> plan, long from, long board) {
        if (plan.isEmpty() || plan.getFirst().apply(from) != board) return null;
        return plan.subList(1, plan.size());
    }

    /**
     * Whether {@code steps} can be done in order from {@code boxes}, each push legal at its turn, and
     * leaves the way open.
     */
    public static boolean works(long boxes, List<Step> steps) {
        long board = boxes & BoulderBoard.BOARD;
        for (Step step : steps) {
            long next = BoulderBoard.push(board, BoulderBoard.reachable(board), step.cell(), step.push());
            if (next < 0) return false;
            board = next;
        }
        return BoulderBoard.open(board);
    }
}
