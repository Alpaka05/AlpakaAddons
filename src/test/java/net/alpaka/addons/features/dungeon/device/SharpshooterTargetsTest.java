package net.alpaka.addons.features.dungeon.device;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Sharpshooter targets and the hits of one attempt (terminals.md 4.4). */
class SharpshooterTargetsTest {

    private static final BlockPos TOP_LEFT = new BlockPos(68, 130, 50);
    private static final BlockPos MIDDLE = new BlockPos(66, 128, 50);
    private static final BlockPos BOTTOM_RIGHT = new BlockPos(64, 126, 50);

    /** A target lit up and was shot: emerald, then terracotta. */
    private static void shoot(SharpshooterTargets t, BlockPos pos) {
        t.onTarget(pos, false, true, false);
        t.onTarget(pos, true, false, true);
    }

    @Test
    void nineTargetsOnTheWallAtZ50() {
        assertEquals(9, SharpshooterTargets.TARGETS.size());
        assertEquals(9, new HashSet<>(SharpshooterTargets.TARGETS).size());
        for (BlockPos pos : SharpshooterTargets.TARGETS) {
            assertEquals(50, pos.getZ());
            assertTrue(List.of(64, 66, 68).contains(pos.getX()), pos.toString());
            assertTrue(List.of(126, 128, 130).contains(pos.getY()), pos.toString());
            assertTrue(SharpshooterTargets.isTarget(pos));
        }
        assertFalse(SharpshooterTargets.isTarget(new BlockPos(65, 128, 50)));
        assertFalse(SharpshooterTargets.isTarget(new BlockPos(66, 127, 50)));
        assertFalse(SharpshooterTargets.isTarget(new BlockPos(66, 128, 49)));
        assertFalse(SharpshooterTargets.isTarget(SharpshooterTargets.PLATE));
        assertEquals(new BlockPos(63, 127, 35), SharpshooterTargets.PLATE);
    }

    @Test
    void aShotTargetIsAHit() {
        SharpshooterTargets t = new SharpshooterTargets();
        t.onPlate(0, 1);
        shoot(t, MIDDLE);
        shoot(t, TOP_LEFT);
        assertEquals(List.of(MIDDLE, TOP_LEFT), t.hits());
        assertTrue(t.wasHit(MIDDLE));
        assertFalse(t.wasHit(BOTTOM_RIGHT));
    }

    @Test
    void aTargetThatLightsAgainIsNoLongerAHit() {
        SharpshooterTargets t = new SharpshooterTargets();
        shoot(t, MIDDLE);
        t.onTarget(MIDDLE, false, true, false);
        assertEquals(List.of(), t.hits());
    }

    @Test
    void onlyEmeraldToTerracottaIsAHit() {
        SharpshooterTargets t = new SharpshooterTargets();
        // Terracotta to terracotta (a resend), or emerald to something else.
        t.onTarget(MIDDLE, false, false, true);
        t.onTarget(TOP_LEFT, true, false, false);
        t.onTarget(new BlockPos(65, 128, 50), true, false, true);
        assertEquals(0, t.hitCount());
    }

    @Test
    void steppingOffThePlateEndsTheAttempt() {
        SharpshooterTargets t = new SharpshooterTargets();
        t.onPlate(0, 1);
        shoot(t, MIDDLE);
        t.onPlate(1, 0);
        assertEquals(List.of(), t.hits());
        assertEquals(Boolean.FALSE, t.plateOn());
        // With nobody on the plate a lit target going dark is the device resetting.
        t.onTarget(TOP_LEFT, true, false, true);
        assertEquals(List.of(), t.hits());
    }

    @Test
    void steppingOnStartsANewAttempt() {
        SharpshooterTargets t = new SharpshooterTargets();
        shoot(t, MIDDLE);
        t.onPlate(0, 1);
        assertEquals(List.of(), t.hits());
        shoot(t, TOP_LEFT);
        // A second player stepping on changes the power but not the attempt.
        t.onPlate(1, 2);
        assertEquals(List.of(TOP_LEFT), t.hits());
        assertEquals(Boolean.TRUE, t.plateOn());
    }

    /** Without any plate update (another block there than expected) hits still count. */
    @Test
    void hitsCountWhileThePlateIsUnknown() {
        SharpshooterTargets t = new SharpshooterTargets();
        assertNull(t.plateOn());
        shoot(t, BOTTOM_RIGHT);
        assertEquals(List.of(BOTTOM_RIGHT), t.hits());
        t.reset();
        assertEquals(List.of(), t.hits());
        assertNull(t.plateOn());
    }
}
