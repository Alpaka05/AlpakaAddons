package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.TeleportMazeTracker.Outcome;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teleport Maze pads and the pad to the end (puzzles-entities.md 4.2, 4.5 and the fixtures in 4.9). */
class TeleportMazeTrackerTest {

    /** The unturned room with its clay at 0, 0: room coordinates are world coordinates. */
    private static TeleportMazeTracker unturned() {
        return new TeleportMazeTracker(TeleportMazePads.CELL_PADS, TeleportMazePads.CENTRE_PADS);
    }

    private static BlockPos pad(int x, int z) {
        return new BlockPos(x, TeleportMazePads.PAD_Y, z);
    }

    /** Where a pad teleport puts the player: the pad's centre, at Y 69.5. */
    private static Vec3 onto(BlockPos pad) {
        return new Vec3(pad.getX() + 0.5, TeleportMazeTracker.LANDING_Y, pad.getZ() + 0.5);
    }

    /** The yaw that faces {@code to} from {@code from}, the way the server turns the player. */
    private static float facing(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    private static Set<BlockPos> pads(int... xz) {
        Set<BlockPos> out = new HashSet<>();
        for (int i = 0; i < xz.length; i += 2) out.add(pad(xz[i], xz[i + 1]));
        return out;
    }

    @Test
    void theRoomHasTwentyEightCellPadsAndTwoInTheMiddle() {
        // The positions Odin and Skyblocker both list.
        Set<BlockPos> expected = pads(4, 12, 4, 6, 10, 12, 10, 6, 4, 20, 4, 14, 10, 20, 10, 14, 4, 28, 4, 22,
                10, 28, 10, 22, 12, 28, 12, 22, 18, 28, 18, 22, 20, 28, 20, 22, 26, 28, 26, 22, 26, 20, 26, 14,
                20, 20, 20, 14, 26, 12, 26, 6, 20, 12, 20, 6);
        assertEquals(28, TeleportMazePads.CELL_PADS.size());
        assertEquals(expected, new HashSet<>(TeleportMazePads.CELL_PADS));
        assertEquals(List.of(pad(15, 12), pad(15, 14)), TeleportMazePads.CENTRE_PADS);
    }

    /** One fixture of 4.9: two landings, each facing the final pad, and the candidates after each. */
    private static void narrowsDown(BlockPos end, BlockPos first, Set<BlockPos> afterFirst, BlockPos second, Set<BlockPos> afterSecond) {
        TeleportMazeTracker maze = unturned();
        assertEquals(Outcome.AIMED, maze.onTeleport(null, onto(first), facing(first, end)));
        assertEquals(afterFirst, maze.candidates(), "after landing on " + first);
        assertEquals(Outcome.AIMED, maze.onTeleport(null, onto(second), facing(second, end)));
        assertEquals(afterSecond, maze.candidates(), "after landing on " + second);
        assertEquals(end, maze.finalPad());
        assertEquals(Set.of(first, second), maze.used());
    }

    @Test
    void twoTeleportsFindTheWayToTheEnd() {
        narrowsDown(pad(26, 22), pad(4, 6), pads(26, 22), pad(10, 20), pads(26, 22));
        narrowsDown(pad(4, 28), pad(20, 12), pads(4, 28, 10, 22), pad(26, 20), pads(4, 28));
        narrowsDown(pad(12, 22), pad(4, 6), pads(10, 20, 12, 22), pad(26, 6), pads(12, 22));
        narrowsDown(pad(18, 28), pad(10, 6), pads(18, 28), pad(26, 14), pads(18, 28));
    }

    @Test
    void theFixtureYawsAreTheOnesTheServerWouldSet() {
        assertEquals(-53.9726f, facing(pad(4, 6), pad(26, 22)), 1.0e-3f);
        assertEquals(-82.8750f, facing(pad(10, 20), pad(26, 22)), 1.0e-3f);
        assertEquals(45.0f, facing(pad(20, 12), pad(4, 28)), 1.0e-3f);
        assertEquals(70.0169f, facing(pad(26, 20), pad(4, 28)), 1.0e-3f);
        // The ray from (10, 20) to (26, 22) passes (18, 22) just within the tolerance.
        assertEquals(pads(18, 22, 20, 22, 26, 22), new HashSet<>(TeleportMazeTracker.onLine(TeleportMazePads.CELL_PADS, onto(pad(10, 20)), -82.875f, 1.0)));
    }

    @Test
    void aKnownSourcePadIsUsedToo() {
        TeleportMazeTracker maze = unturned();
        assertEquals(Outcome.AIMED, maze.onTeleport(TeleportMazePads.START, onto(pad(4, 6)), facing(pad(4, 6), pad(26, 22))));
        assertEquals(Set.of(TeleportMazePads.START, pad(4, 6)), maze.used());
        // Standing on a pad: on its top, anywhere on the block.
        assertEquals(pad(4, 6), maze.padUnder(new Vec3(4.9, 69.8125, 6.1)));
        assertEquals(pad(4, 6), maze.padUnder(new Vec3(5.2, 69.8125, 6.5)), "half the player's width over the edge");
        assertNull(maze.padUnder(new Vec3(5.4, 69.8125, 6.5)));
        assertNull(maze.padUnder(new Vec3(4.5, 72.0, 6.5)), "far above it");
    }

    @Test
    void otherTeleportsAreIgnored() {
        TeleportMazeTracker maze = unturned();
        // Aspect of the End from the floor: lands anywhere, not where a pad puts the player.
        assertEquals(Outcome.IGNORED, maze.onTeleport(null, new Vec3(10.37, 69.0, 12.81), 30.0f));
        // Etherwarp onto a pad: on top of the block, half a block too high, even from a pad.
        assertEquals(Outcome.IGNORED, maze.onTeleport(pad(4, 6), new Vec3(10.5, 70.0, 12.5), 30.0f));
        // Near a pad but not exactly on it, and nobody knows where from.
        assertEquals(Outcome.IGNORED, maze.onTeleport(null, new Vec3(10.8, 69.5, 12.5), 30.0f));
        // Pulled back onto the pad the player stood on.
        assertEquals(Outcome.IGNORED, maze.onTeleport(pad(10, 12), onto(pad(10, 12)), 30.0f));
        assertTrue(maze.used().isEmpty());
        assertTrue(maze.candidates().isEmpty());
        assertNull(maze.finalPad());
    }

    @Test
    void landingInTheMiddleNotesThePadsButDrawsNoLine() {
        TeleportMazeTracker maze = unturned();
        maze.onTeleport(null, onto(pad(20, 12)), facing(pad(20, 12), pad(4, 28)));
        Set<BlockPos> before = maze.candidates();
        assertEquals(Outcome.CENTRE, maze.onTeleport(pad(10, 6), onto(TeleportMazePads.START), 0.0f));
        assertEquals(before, maze.candidates());
        assertEquals(Set.of(pad(20, 12), pad(10, 6), TeleportMazePads.START), maze.used());
        // The start pad is the way back into the maze, never a pad not to take.
        assertEquals(Set.of(pad(20, 12), pad(10, 6)), maze.usedCellPads());
    }

    @Test
    void aUsedPadIsNoCandidateAnyMore() {
        TeleportMazeTracker maze = unturned();
        maze.onTeleport(null, onto(pad(20, 12)), facing(pad(20, 12), pad(4, 28)));
        assertEquals(pads(4, 28, 10, 22), maze.candidates());
        // The player tried (10, 22) and came out somewhere in the middle.
        maze.onTeleport(pad(10, 22), onto(TeleportMazePads.START), 0.0f);
        assertEquals(pads(4, 28), maze.candidates());
        assertEquals(pad(4, 28), maze.finalPad());
    }

    @Test
    void linesThatShareNoPadStartOverFromTheLatest() {
        TeleportMazeTracker maze = unturned();
        maze.onTeleport(null, onto(pad(4, 6)), facing(pad(4, 6), pad(26, 22)));
        assertEquals(pads(26, 22), maze.candidates());
        float off = facing(pad(10, 20), pad(26, 22)) + 20.0f;
        Set<BlockPos> latest = new HashSet<>(TeleportMazeTracker.onLine(TeleportMazePads.CELL_PADS, onto(pad(10, 20)), off, 1.0));
        latest.removeAll(Set.of(pad(4, 6), pad(10, 20)));
        assertTrue(!latest.isEmpty() && !latest.contains(pad(26, 22)));
        maze.onTeleport(null, onto(pad(10, 20)), off);
        assertEquals(latest, maze.candidates());
    }

    @Test
    void aPadInTheLandingCellIsSuggested() {
        TeleportMazeTracker maze = unturned();
        assertNull(maze.suggested(), "nothing before the first teleport");
        // Landed on (4, 6), turned towards (26, 22) in another cell: of the three other pads of this
        // cell, (10, 12) lies nearest that way.
        maze.onTeleport(null, onto(pad(4, 6)), facing(pad(4, 6), pad(26, 22)));
        assertEquals(pads(26, 22), maze.candidates());
        assertEquals(pad(10, 12), maze.suggested());
        // Back in the middle: no cell, no suggestion.
        assertEquals(Outcome.CENTRE, maze.onTeleport(pad(10, 12), onto(TeleportMazePads.START), 0.0f));
        assertNull(maze.suggested());
    }

    @Test
    void noSuggestionBesideACandidateInTheSameCell() {
        TeleportMazeTracker maze = unturned();
        // From (20, 12) the line runs to (4, 28) and (10, 22); landing in (10, 22)'s cell next.
        maze.onTeleport(null, onto(pad(20, 12)), facing(pad(20, 12), pad(4, 28)));
        maze.onTeleport(null, onto(pad(10, 28)), facing(pad(10, 28), pad(4, 28)));
        assertTrue(maze.candidates().contains(pad(4, 28)));
        assertNull(maze.suggested(), "the candidate in this cell is marked already");
    }

    @Test
    void theSuggestionSkipsUsedPadsAndWrapsAroundTheYawSeam() {
        // Came from (10, 6) onto (10, 12), turned to face -Z, where only the used (10, 6) lies:
        // the line hits no open pad, and a pad of the cell is still suggested.
        for (float yaw : new float[]{179.9f, -179.9f}) {
            TeleportMazeTracker maze = unturned();
            assertEquals(Outcome.AIMED, maze.onTeleport(pad(10, 6), onto(pad(10, 12)), yaw));
            assertTrue(maze.candidates().isEmpty());
            // (4, 6) lies 45 degrees off; 179.9 and -179.9 are 0.2 degrees apart, not 359.8.
            assertEquals(pad(4, 6), maze.suggested(), "yaw " + yaw);
        }
    }

    @Test
    void aPadSeenToLeadIntoTheEndPadsCellComesFirst() {
        TeleportMazeTracker maze = unturned();
        BlockPos end = pad(26, 22);
        maze.onTeleport(null, onto(pad(4, 6)), facing(pad(4, 6), end));
        assertEquals(end, maze.finalPad());
        // (10, 12) leads into the end pad's cell; from there (20, 28) leads back into the first cell.
        maze.onTeleport(pad(10, 12), onto(pad(20, 22)), facing(pad(20, 22), end));
        maze.onTeleport(pad(20, 28), onto(pad(4, 12)), facing(pad(4, 12), end));
        assertEquals(end, maze.finalPad());
        assertEquals(pad(10, 12), maze.suggested(), "used, but known to lead the right way");
    }

    @Test
    void aTurnedRoomGivesTheSameAnswer() {
        // Clay in the north-east corner of cell (3, 1): the room is turned clockwise, and so is the view.
        int cx = RoomFrame.centre(3);
        int cz = RoomFrame.centre(1);
        RoomFrame frame = RoomFrame.of(3, 1, cx + 15, cz - 15, 100);
        TeleportMazeTracker maze = new TeleportMazeTracker(
                TeleportMazePads.inWorld(frame, TeleportMazePads.CELL_PADS),
                TeleportMazePads.inWorld(frame, TeleportMazePads.CENTRE_PADS));
        BlockPos end = pad(4, 28);
        for (BlockPos land : List.of(pad(20, 12), pad(26, 20))) {
            Vec3 at = frame.toWorld(new Vec3(land.getX() + 0.5, TeleportMazeTracker.LANDING_Y, land.getZ() + 0.5));
            // A quarter turn clockwise adds 90 degrees to the yaw.
            assertEquals(Outcome.AIMED, maze.onTeleport(null, at, facing(land, end) + 90.0f));
        }
        assertEquals(frame.toWorld(end), maze.finalPad());
        assertEquals(frame.toWorld(end), maze.landingPad(frame.toWorld(new Vec3(4.5, 69.5, 28.5))));
    }
}
