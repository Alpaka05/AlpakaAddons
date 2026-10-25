package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.IceFillBoard.Floor;

import java.util.Arrays;

/**
 * The walk shown on one Ice Fill floor, and how far along it the floor is. Pure.
 *
 * Progress is read from the blocks: a cell is walked once its ice turned to packed ice. While the
 * packed cells are exactly the first cells of the walk, the plan stands and only the rest of it is
 * drawn. If someone walks off it without the floor resetting yet, a new walk is searched from the
 * cell they are on over the ice that is left, to the same end. When the floor resets (all ice
 * again) the walk from the start comes back.
 *
 * A plan made mid-floor remembers which cells were packed when it was made, and its route starts
 * on the cell the walker stood on.
 */
public final class IceFillPlan {

    /** {@link #position} when the packed cells do not follow this plan. */
    public static final int OFF_PLAN = -2;

    private final long free;
    private final long packedBefore;
    private final int[] route;
    private final boolean fromWalker;
    private final int turns;

    private IceFillPlan(long free, long packedBefore, int[] route, boolean fromWalker, int turns) {
        this.free = free;
        this.packedBefore = packedBefore;
        this.route = route;
        this.fromWalker = fromWalker;
        this.turns = turns;
    }

    /** A plan for an untouched floor: the route runs from the start to the end. */
    public static IceFillPlan fromStart(long free, IceFillSearch.Path path) {
        return new IceFillPlan(free, 0L, path.cells().clone(), false, path.turns());
    }

    /** A plan made mid-floor: {@code path} starts on the walker's cell, which is packed already. */
    public static IceFillPlan fromWalker(long free, long packed, IceFillSearch.Path path) {
        return new IceFillPlan(free, packed, path.cells().clone(), true, path.turns());
    }

    /** The cells in walking order; the first is the start, or the walker's cell for a plan made mid-floor. */
    public int[] route() {
        return route.clone();
    }

    public int turns() {
        return turns;
    }

    /** The free cells this plan was made for. */
    public long free() {
        return free;
    }

    /**
     * Where in {@link #route()} the walker is, going by the packed cells: -1 before the first step,
     * or {@link #OFF_PLAN} when the packed cells are not the start of this route.
     */
    public int position(long packed) {
        if ((packed & packedBefore) != packedBefore) return OFF_PLAN;
        long walked = packed & ~packedBefore;
        int first = fromWalker ? 1 : 0;
        int count = Long.bitCount(walked);
        if (first + count > route.length) return OFF_PLAN;
        for (int i = 0; i < count; i++) {
            if ((walked >>> route[first + i] & 1L) == 0) return OFF_PLAN;
        }
        return first + count - 1;
    }

    /**
     * The cells still ahead, starting with the one the walker is on (or with the start before the
     * first step), or null when the packed cells do not follow this plan.
     */
    public int[] ahead(long packed) {
        int at = position(packed);
        if (at == OFF_PLAN) return null;
        return Arrays.copyOfRange(route, Math.max(at, 0), route.length);
    }

    /**
     * The plan to show for a floor whose free and packed cells are as given: {@code current} while
     * the floor still follows it, otherwise a new one. {@code walker} is the cell the player stands
     * on if they are on this floor, else -1. Null when the floor is done, or when no walk can
     * finish it from here (it has to reset first) or none could be found.
     */
    public static IceFillPlan update(IceFillPlan current, Floor floor, long free, long packed, int walker, int budget) {
        if (free == 0 || (free & ~packed) == 0) return null;
        if (current != null && current.free == free && current.position(packed) != OFF_PLAN) return current;
        if (packed == 0) {
            IceFillSearch.Path path = IceFillSearch.solve(floor.size(), floor.size(), free, floor.start(), floor.end(), budget);
            return path == null ? null : fromStart(free, path);
        }
        int at = walker >= 0 && (packed >>> walker & 1L) != 0 ? walker : IceFillBoard.walkerFromTrail(floor, packed);
        if (at < 0 || at == floor.end()) return null;
        long open = (free & ~packed) | 1L << at;
        IceFillSearch.Path path = IceFillSearch.solve(floor.size(), floor.size(), open, at, floor.end(), budget);
        return path == null ? null : fromWalker(free, packed, path);
    }
}
