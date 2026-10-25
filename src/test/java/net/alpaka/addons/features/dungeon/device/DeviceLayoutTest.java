package net.alpaka.addons.features.dungeon.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four devices' coordinates against each other: each lies in its own Goldor section and in
 * the phase's height band, and everything a device marks is within the 32 blocks around the player
 * that DungeonSolvers lets device marks through, seen from where the device is worked.
 */
class DeviceLayoutTest {

    /** DungeonSolvers.DEVICE_REACH. */
    private static final double DEVICE_REACH = 32.0;

    private static List<BlockPos> simonSays() {
        List<BlockPos> all = new ArrayList<>();
        for (int y = SimonSaysSequence.MIN_Y; y <= SimonSaysSequence.MAX_Y; y++) {
            for (int z = SimonSaysSequence.MIN_Z; z <= SimonSaysSequence.MAX_Z; z++) {
                all.add(new BlockPos(SimonSaysSequence.DISPLAY_X, y, z));
                all.add(new BlockPos(SimonSaysSequence.BUTTON_X, y, z));
            }
        }
        all.add(SimonSaysSequence.START_BUTTON);
        return all;
    }

    private static List<BlockPos> arrowAlign() {
        List<BlockPos> all = new ArrayList<>();
        for (int cell = 0; cell < ArrowAlignPatterns.CELLS; cell++) all.add(ArrowAlignPatterns.framePos(cell));
        return all;
    }

    private static List<BlockPos> sharpshooter() {
        List<BlockPos> all = new ArrayList<>(SharpshooterTargets.TARGETS);
        all.add(SharpshooterTargets.PLATE);
        return all;
    }

    @Test
    void eachDeviceIsInItsSection() {
        assertSection(1, simonSays());
        assertSection(2, LightsDevice.LEVERS);
        assertSection(3, arrowAlign());
        assertSection(4, sharpshooter());
    }

    private static void assertSection(int section, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            assertEquals(section, GoldorPhase.sectionAt(pos.getX() + 0.5, pos.getZ() + 0.5), pos.toString());
            assertEquals(3, GoldorPhase.phaseAt(pos.getY()), pos.toString());
        }
    }

    @Test
    void lightsHasSixLeversOnTheWallAtZ142() {
        assertEquals(6, LightsDevice.LEVERS.size());
        assertEquals(6, new HashSet<>(LightsDevice.LEVERS).size());
        for (BlockPos lever : LightsDevice.LEVERS) {
            assertEquals(142, lever.getZ());
            assertTrue(lever.getX() >= 58 && lever.getX() <= 62, lever.toString());
            assertTrue(lever.getY() >= 133 && lever.getY() <= 136, lever.toString());
        }
    }

    /** Where the player works each device from, every mark it draws gets through the device clip. */
    @Test
    void marksAreWithinTheDeviceClipFromWhereTheDeviceIsWorked() {
        // In front of the Simon Says buttons; at the Arrow Align wall; on the Sharpshooter plate; in front of the levers.
        assertWithinReach(new Vec3(108.5, 121, 93.5), simonSays());
        assertWithinReach(ArrowAlignPatterns.STAND, arrowAlign());
        assertWithinReach(new Vec3(63.5, 127, 35.5), sharpshooter());
        assertWithinReach(new Vec3(60.5, 133, 139.5), LightsDevice.LEVERS);
        for (int cell = 0; cell < ArrowAlignPatterns.CELLS; cell++) {
            Vec3 label = ArrowAlignPatterns.labelPos(cell);
            assertTrue(Math.abs(label.x - ArrowAlignPatterns.STAND.x) < DEVICE_REACH);
            assertTrue(Math.abs(label.z - ArrowAlignPatterns.STAND.z) < DEVICE_REACH);
        }
        // The plate and the targets are both in the Sharpshooter room.
        for (BlockPos pos : sharpshooter()) assertTrue(Sharpshooter.ROOM.contains(Vec3.atCenterOf(pos)), pos.toString());
    }

    private static void assertWithinReach(Vec3 player, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            assertTrue(Math.abs(pos.getX() + 0.5 - player.x) + 0.5 < DEVICE_REACH, pos.toString());
            assertTrue(Math.abs(pos.getZ() + 0.5 - player.z) + 0.5 < DEVICE_REACH, pos.toString());
        }
    }

    /** The devices are far enough apart that the clip around one never lets another's marks through. */
    @Test
    void noOtherDeviceIsWithinTheClip() {
        List<List<BlockPos>> devices = List.of(simonSays(), LightsDevice.LEVERS, arrowAlign(), sharpshooter());
        List<Vec3> standing = List.of(new Vec3(108.5, 121, 93.5), new Vec3(60.5, 133, 139.5), ArrowAlignPatterns.STAND,
                new Vec3(63.5, 127, 35.5));
        for (int at = 0; at < standing.size(); at++) {
            for (int other = 0; other < devices.size(); other++) {
                if (other == at) continue;
                for (BlockPos pos : devices.get(other)) {
                    boolean inside = Math.abs(pos.getX() + 0.5 - standing.get(at).x) < DEVICE_REACH
                            && Math.abs(pos.getZ() + 0.5 - standing.get(at).z) < DEVICE_REACH;
                    assertFalse(inside, "device " + other + " at " + pos + " is visible from device " + at);
                }
            }
        }
    }
}
