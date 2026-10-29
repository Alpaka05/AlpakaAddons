package net.alpaka.addons.features.dungeon.device;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Sharpshooter targets (Goldor section 4) and which of them were hit in the current attempt.
 * Pure: {@link Sharpshooter} feeds it the server's block changes, so the rules can be tested
 * without a game.
 *
 * Nine blue terracotta targets hang on the wall at Z 50, three by three. Standing on the gold
 * pressure plate at (63, 127, 35) starts the device: one target turns into an emerald block, and
 * when an arrow hits it, it turns back to terracotta and another one lights up (terminals.md 4.4).
 *
 * A hit is an emerald target turning back to terracotta; one that lights up again is not a hit any
 * more. Stepping off the plate ends the attempt, and stepping on starts a new one, so both clear
 * the hits. While the plate is known to be empty a target going dark is the device resetting, not a
 * hit. If the plate's state was never seen (it may be another block than expected), hits still count.
 */
public final class SharpshooterTargets {

    /** The targets, top row first, each row from the player's left as seen from the plate. */
    public static final List<BlockPos> TARGETS = List.of(
            new BlockPos(68, 130, 50), new BlockPos(66, 130, 50), new BlockPos(64, 130, 50),
            new BlockPos(68, 128, 50), new BlockPos(66, 128, 50), new BlockPos(64, 128, 50),
            new BlockPos(68, 126, 50), new BlockPos(66, 126, 50), new BlockPos(64, 126, 50));
    /** The gold (light weighted) pressure plate that starts the device. */
    public static final BlockPos PLATE = new BlockPos(63, 127, 35);
    public static final int TARGET_Z = 50;

    private final Set<BlockPos> hits = new LinkedHashSet<>();
    /** Someone stands on the plate; null while no plate update has been seen. */
    private Boolean plateOn = null;

    /** One of the nine targets. Cheap, for every block change in the world. */
    public static boolean isTarget(BlockPos pos) {
        if (pos.getZ() != TARGET_Z) return false;
        int x = pos.getX();
        int y = pos.getY();
        return (x == 64 || x == 66 || x == 68) && (y == 126 || y == 128 || y == 130);
    }

    /** The plate's power changed from {@code oldPower} to {@code newPower} (0 = nobody on it). */
    public void onPlate(int oldPower, int newPower) {
        if (newPower <= 0) {
            hits.clear();
            plateOn = Boolean.FALSE;
            return;
        }
        if (oldPower <= 0) hits.clear();
        plateOn = Boolean.TRUE;
    }

    /**
     * A target changed: it was an emerald block or not, and now is an emerald block, blue
     * terracotta, or something else.
     */
    public void onTarget(BlockPos pos, boolean wasEmerald, boolean nowEmerald, boolean nowTerracotta) {
        if (!isTarget(pos)) return;
        if (nowEmerald) {
            hits.remove(pos);
        } else if (wasEmerald && nowTerracotta && plateOn != Boolean.FALSE) {
            hits.add(pos.immutable());
        }
    }

    /** The targets hit in this attempt, in the order they were hit. */
    public List<BlockPos> hits() {
        return List.copyOf(hits);
    }

    public int hitCount() {
        return hits.size();
    }

    public boolean wasHit(BlockPos pos) {
        return hits.contains(pos);
    }

    /** Whether someone stands on the plate; null when unknown. */
    public Boolean plateOn() {
        return plateOn;
    }

    public void reset() {
        hits.clear();
        plateOn = null;
    }
}
