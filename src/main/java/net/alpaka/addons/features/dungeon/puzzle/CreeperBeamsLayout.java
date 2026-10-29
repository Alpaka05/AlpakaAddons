package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which sea lanterns of the Creeper Beams room belong together, and how far each pair is.
 *
 * The creeper stands on a lantern in the middle of the room, at Y 74. Of the lanterns on the walls
 * and ceiling, the pairs whose beam passes through the creeper are known ({@link CreeperBeamsPairs});
 * a run places a few of them. Shooting both lanterns of a pair joins them, and a lantern that is part
 * of a joined beam turns into prismarine (Odin and Skyblocker both read it this way).
 *
 * The known pairs are written in room coordinates. The room's rotation comes from the room frame
 * when there is one; without it, it is the rotation under which the most known lanterns are
 * actually there - turned by 90 degrees, the known lanterns share only the one under the creeper
 * with the unturned set, so the right rotation stands out clearly (puzzles-entities.md 3.5).
 *
 * Should a room have fewer than four known pairs (a layout Hypixel added since), the remaining
 * lanterns are paired by geometry: the pairs whose line runs closest to where the known beams cross
 * the creeper's column, each lantern used once, and only lines that pass within
 * {@link #FILL_UP_REACH} of that point.
 *
 * Pure: blocks come in through {@link BlockView}. Unit-tested in CreeperBeamsLayoutTest.
 */
public final class CreeperBeamsLayout {

    /** The lantern under the creeper sits at this height, in the room's centre column. */
    public static final int BASE_Y = 74;
    /** A puzzle needs this many beams. */
    public static final int BEAMS = 4;
    /**
     * The height at which the known beams cross the room's centre column, a little above the
     * creeper's head (the point closest to all their lines is 15.51, 77.23, 15.57 in room
     * coordinates, puzzles-entities.md 3.3). Geometric pairs are measured from there: from the
     * creeper's own middle, 1.4 blocks lower, most known beams would look like misses.
     */
    public static final double CROSSING_Y = 77.25;
    /** A geometric pair's line must pass at most this far from {@link #crossing}. */
    public static final double FILL_UP_REACH = 1.3;
    /** The lowest and highest Y a lantern of the room is looked for at, for the geometric pairs. */
    public static final int LOW_Y = 68;
    public static final int HIGH_Y = 90;
    /** How many known lanterns a rotation must find, and by how many it must beat the next best. */
    static final int MIN_SCORE = 6;
    static final int MIN_LEAD = 4;

    private static final Rotation[] ROTATIONS = {
            Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

    /** What a block is, as far as this puzzle cares. */
    public enum Kind {
        /** A sea lantern not yet part of a beam. */
        LANTERN,
        /** A lantern that is part of a beam. */
        PRISMARINE,
        OTHER;

        public boolean lantern() {
            return this != OTHER;
        }
    }

    /** How far a pair is. */
    public enum State {
        /** Neither end is lit. */
        TODO,
        /** One end is lit. */
        HALF,
        /** Both ends are lit; the beam is done. */
        DONE
    }

    /** The bits of the world the layout reads. */
    public interface BlockView {
        Kind kindAt(BlockPos pos);
    }

    /** Two lanterns that make a beam, in world coordinates. */
    public record Pair(BlockPos a, BlockPos b) {

        /** How far the pair is, or null when one of its blocks is no lantern any more. */
        public State state(BlockView view) {
            Kind ka = view.kindAt(a);
            Kind kb = view.kindAt(b);
            if (!ka.lantern() || !kb.lantern()) return null;
            int lit = (ka == Kind.PRISMARINE ? 1 : 0) + (kb == Kind.PRISMARINE ? 1 : 0);
            return lit == 2 ? State.DONE : lit == 1 ? State.HALF : State.TODO;
        }
    }

    private CreeperBeamsLayout() {}

    /** The lantern the creeper stands on, in the middle of the room in this cell. */
    public static BlockPos base(int cellX, int cellZ) {
        return new BlockPos(RoomFrame.centre(cellX), BASE_Y, RoomFrame.centre(cellZ));
    }

    /** Where the beams cross the centre column of the room in this cell. */
    public static Vec3 crossing(int cellX, int cellZ) {
        return new Vec3(RoomFrame.centre(cellX) + 0.5, CROSSING_Y, RoomFrame.centre(cellZ) + 0.5);
    }

    /** The frame of the room in this cell for a rotation; only its X/Z transform matters here. */
    public static RoomFrame frame(int cellX, int cellZ, Rotation rotation) {
        int cx = RoomFrame.centre(cellX);
        int cz = RoomFrame.centre(cellZ);
        int h = RoomFrame.HALF;
        return switch (rotation) {
            case NONE -> RoomFrame.of(cellX, cellZ, cx - h, cz - h, 0);
            case CLOCKWISE_90 -> RoomFrame.of(cellX, cellZ, cx + h, cz - h, 0);
            case CLOCKWISE_180 -> RoomFrame.of(cellX, cellZ, cx + h, cz + h, 0);
            case COUNTERCLOCKWISE_90 -> RoomFrame.of(cellX, cellZ, cx - h, cz + h, 0);
        };
    }

    /**
     * The rotation under which clearly the most known lanterns are there, or null when no rotation
     * stands out (blocks not loaded yet, or not this room).
     */
    public static Rotation findRotation(BlockView view, int cellX, int cellZ) {
        Rotation best = null;
        int bestScore = -1;
        int second = -1;
        for (Rotation rotation : ROTATIONS) {
            int score = score(view, frame(cellX, cellZ, rotation));
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = rotation;
            } else if (score > second) {
                second = score;
            }
        }
        if (bestScore < MIN_SCORE || bestScore - Math.max(second, 0) < MIN_LEAD) return null;
        return best;
    }

    /** How many of the known lanterns are there in this frame, each counted once. */
    static int score(BlockView view, RoomFrame frame) {
        Set<BlockPos> seen = new HashSet<>();
        int score = 0;
        for (BlockPos[] pair : CreeperBeamsPairs.PAIRS) {
            for (BlockPos rel : pair) {
                BlockPos world = frame.toWorld(rel);
                if (seen.add(world) && view.kindAt(world).lantern()) score++;
            }
        }
        return score;
    }

    /** The known pairs whose two lanterns are both there, in the table's order. */
    public static List<Pair> knownPairs(BlockView view, RoomFrame frame) {
        List<Pair> out = new ArrayList<>();
        for (BlockPos[] pair : CreeperBeamsPairs.PAIRS) {
            BlockPos a = frame.toWorld(pair[0]);
            BlockPos b = frame.toWorld(pair[1]);
            if (view.kindAt(a).lantern() && view.kindAt(b).lantern()) out.add(new Pair(a, b));
        }
        return out;
    }

    /**
     * Pairs among {@code lanterns} by geometry: of all lines between two of them that pass within
     * {@link #FILL_UP_REACH} of {@code target} (normally {@link #crossing}), the closest first, each
     * lantern used once, at most {@code limit} pairs. The lantern under the creeper must not be among
     * them: every line from it passes close to the target, so it would pair with anything.
     */
    public static List<Pair> fillUp(Collection<BlockPos> lanterns, Vec3 target, int limit) {
        List<BlockPos> list = new ArrayList<>(new LinkedHashSet<>(lanterns));
        // A fixed order first, so equal distances always come out the same way.
        list.sort(Comparator.<BlockPos>comparingInt(p -> p.getX()).thenComparingInt(p -> p.getY()).thenComparingInt(p -> p.getZ()));
        record Line(BlockPos a, BlockPos b, double distance) {}
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                double d = lineDistance(target, Vec3.atCenterOf(list.get(i)), Vec3.atCenterOf(list.get(j)));
                if (d <= FILL_UP_REACH) lines.add(new Line(list.get(i), list.get(j), d));
            }
        }
        lines.sort(Comparator.comparingDouble(Line::distance));
        List<Pair> out = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        for (Line line : lines) {
            if (out.size() >= limit) break;
            if (used.contains(line.a()) || used.contains(line.b())) continue;
            used.add(line.a());
            used.add(line.b());
            out.add(new Pair(line.a(), line.b()));
        }
        return out;
    }

    /** Distance from {@code p} to the infinite line through {@code a} and {@code b}. */
    public static double lineDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 dir = b.subtract(a);
        double length = dir.length();
        if (length < 1.0e-9) return p.distanceTo(a);
        return p.subtract(a).cross(dir).length() / length;
    }
}
