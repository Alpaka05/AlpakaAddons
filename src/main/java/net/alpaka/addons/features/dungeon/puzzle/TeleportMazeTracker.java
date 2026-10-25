package net.alpaka.addons.features.dungeon.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one visit to the Teleport Maze has shown so far: the pads used, and which pad leads to the
 * end.
 *
 * The room is a grid of walled cells with four teleport pads each (end portal frames at Y 69), and
 * two more pads in the middle: the one the player starts from and the one by the reward chest.
 * Exactly one cell pad leads there. After every teleport the server turns the player to face that
 * pad (the wiki says so, and Odin and old Skytils rely on it). So the pads along the line of sight
 * from where the player lands are the candidates, and every further teleport narrows them down to
 * the pads that lie on its line as well (puzzles-entities.md 4.5).
 *
 * A teleport counts only if it went from one pad to another: it lands on a pad's centre, at the
 * height the pads put the player, and the player stood on a different pad just before. That leaves
 * out Aspect of the End, Etherwarp and the server pulling the player back. Should two lines not
 * share a pad (lag, or the player turned before the rotation arrived), the latest line wins.
 *
 * Pure: positions in, pads out. Unit-tested in TeleportMazeTrackerTest.
 */
public final class TeleportMazeTracker {

    /** How far a pad's centre may lie off the line the player faces and still be on it. */
    public static final double AIM_TOLERANCE = 1.0;
    /** Pads nearer than this along the line are the one the player stands on, or beside it. */
    public static final double MIN_ALONG = 1.0;
    /** The room is 31 blocks wide; nothing further away is in it. */
    public static final double MAX_ALONG = 40.0;
    /** The height a pad puts the player at. */
    public static final double LANDING_Y = 69.5;
    /** How far from a pad's centre a landing still counts as on it. */
    public static final double LANDING_REACH = 0.6;
    /**
     * How far from {@link #LANDING_Y} a landing still counts as a pad's. Etherwarp onto a pad puts
     * the player on top of the block, half a block higher, and must not count.
     */
    public static final double LANDING_HEIGHT_REACH = 0.25;
    /**
     * A landing this close to a pad's centre and the pad height is a pad teleport even when it is
     * not known where the player came from: no other teleport puts a player exactly there.
     */
    public static final double EXACT = 0.01;
    /** Half a player's width: standing on a pad means the player's box overlaps the pad block. */
    private static final double PLAYER_HALF_WIDTH = 0.3;
    /** Feet this far above a pad block's bottom count as on it: on its top (13/16) or a little above. */
    private static final double STAND_LOW = 0.4;
    private static final double STAND_HIGH = 1.2;

    /** What a teleport turned out to be. */
    public enum Outcome {
        /** Not a pad teleport, or not in this maze: nothing changed. */
        IGNORED,
        /** Onto one of the two pads in the middle: the start or the end. Pads used are noted, no line. */
        CENTRE,
        /** Onto a cell pad: pads used are noted and the candidates narrowed. */
        AIMED
    }

    private final List<BlockPos> cellPads;
    private final List<BlockPos> centrePads;
    private final List<BlockPos> all;
    private final Set<BlockPos> used = new LinkedHashSet<>();
    /** The pads that may lead to the end; null until the first teleport onto a cell pad. */
    private Set<BlockPos> candidates = null;

    /**
     * @param cellPads   the 28 pads in the cells, in world coordinates
     * @param centrePads the start and end pads in the middle
     */
    public TeleportMazeTracker(Collection<BlockPos> cellPads, Collection<BlockPos> centrePads) {
        this.cellPads = List.copyOf(cellPads);
        this.centrePads = List.copyOf(centrePads);
        List<BlockPos> both = new ArrayList<>(this.cellPads);
        both.addAll(this.centrePads);
        this.all = List.copyOf(both);
    }

    /** Every pad of the maze, cell pads first. */
    public List<BlockPos> pads() {
        return all;
    }

    /** The pads used this visit, the ones stepped on and the ones landed on. */
    public Set<BlockPos> used() {
        return Set.copyOf(used);
    }

    /**
     * The cell pads among them: the ones not worth taking again. The start pad is left out, since
     * a pad that leads back to the start makes the player take it again.
     */
    public Set<BlockPos> usedCellPads() {
        Set<BlockPos> out = new LinkedHashSet<>(used);
        out.removeAll(centrePads);
        return out;
    }

    /** The pads that may lead to the end; empty before the first teleport onto a cell pad. */
    public Set<BlockPos> candidates() {
        return candidates == null ? Set.of() : Set.copyOf(candidates);
    }

    /** The pad that leads to the end once only one is left, else null. */
    public BlockPos finalPad() {
        return candidates != null && candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    /** The pad a teleport landing at {@code pos} put the player on, or null. */
    public BlockPos landingPad(Vec3 pos) {
        if (Math.abs(pos.y - LANDING_Y) > LANDING_HEIGHT_REACH) return null;
        for (BlockPos pad : all) {
            if (Math.abs(pos.x - (pad.getX() + 0.5)) <= LANDING_REACH && Math.abs(pos.z - (pad.getZ() + 0.5)) <= LANDING_REACH) {
                return pad;
            }
        }
        return null;
    }

    /** The pad a player with their feet at {@code feet} stands on, or null. */
    public BlockPos padUnder(Vec3 feet) {
        for (BlockPos pad : all) {
            if (feet.y < pad.getY() + STAND_LOW || feet.y > pad.getY() + STAND_HIGH) continue;
            if (feet.x + PLAYER_HALF_WIDTH > pad.getX() && feet.x - PLAYER_HALF_WIDTH < pad.getX() + 1
                    && feet.z + PLAYER_HALF_WIDTH > pad.getZ() && feet.z - PLAYER_HALF_WIDTH < pad.getZ() + 1) {
                return pad;
            }
        }
        return null;
    }

    /**
     * A teleport the server made.
     *
     * @param from    the pad the player stood on just before, or null if not known
     * @param landing where it put the player
     * @param yaw     the player's yaw after it, which the server set to face the pad to the end
     */
    public Outcome onTeleport(BlockPos from, Vec3 landing, float yaw) {
        BlockPos to = landingPad(landing);
        if (to == null || to.equals(from)) return Outcome.IGNORED;
        if (from == null && !exact(to, landing)) return Outcome.IGNORED;
        if (from != null) used.add(from);
        used.add(to);
        if (candidates != null) candidates.removeAll(used);
        if (centrePads.contains(to)) return Outcome.CENTRE;

        List<BlockPos> open = new ArrayList<>(cellPads);
        open.removeAll(used);
        Set<BlockPos> hits = new LinkedHashSet<>(onLine(open, landing, yaw, AIM_TOLERANCE));
        if (hits.isEmpty()) return Outcome.AIMED;
        if (candidates == null) {
            candidates = hits;
        } else {
            candidates.retainAll(hits);
            // The lines share no pad: something went wrong with an earlier one; trust the latest.
            if (candidates.isEmpty()) candidates = hits;
        }
        return Outcome.AIMED;
    }

    /**
     * The pads whose centre lies on the line from {@code from} in the direction of {@code yaw}, at
     * most {@code tolerance} off it, between {@link #MIN_ALONG} and {@link #MAX_ALONG} ahead.
     * Only X and Z: the pads are all at one height, and the pitch does not matter.
     */
    public static List<BlockPos> onLine(Collection<BlockPos> pads, Vec3 from, float yaw, double tolerance) {
        double rad = Math.toRadians(yaw);
        // Minecraft's facing: yaw 0 looks towards +Z, yaw 90 towards -X.
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pad : pads) {
            double vx = pad.getX() + 0.5 - from.x;
            double vz = pad.getZ() + 0.5 - from.z;
            double along = vx * dx + vz * dz;
            double off = Math.abs(vx * dz - vz * dx);
            if (along > MIN_ALONG && along < MAX_ALONG && off <= tolerance) out.add(pad);
        }
        return out;
    }

    /** Whether the landing is exactly on the pad's centre, at the pad height. */
    private static boolean exact(BlockPos pad, Vec3 landing) {
        return Math.abs(landing.x - (pad.getX() + 0.5)) <= EXACT && Math.abs(landing.z - (pad.getZ() + 0.5)) <= EXACT
                && Math.abs(landing.y - LANDING_Y) <= EXACT;
    }
}
