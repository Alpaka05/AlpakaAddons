package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Teleport Maze: marks the pads the player has used in the Wrong color (stepped on or landed on;
 * the start pad in the middle stays unmarked, it is the way back in), and the pad that leads to the
 * end in the Solution color once only one is left - while several still may, each of them in the
 * Next color.
 *
 * After each teleport the server turns the player to face the pad that leads to the end; the
 * teleports arrive through {@link #onServerTeleport} with the position and rotation the server set,
 * and {@link TeleportMazeTracker} narrows the pads down from them. Only read: the player's view is
 * never turned or aimed, and the marks are depth-tested like everything else, so a pad in another
 * walled cell shows only once it is in sight.
 *
 * The room is the one with end portal frames on the pad positions of {@link TeleportMazePads}; its
 * frame comes from DungeonRooms. What the player learnt stays for the rest of the run, also when
 * they leave the room and come back; a rejoin loses it, and the next teleport finds the way again.
 *
 * Spec: puzzles-entities.md section 4.
 */
public final class TeleportMaze extends PuzzleSolver {

    /** A room counts as the maze when this many of its 30 pad positions hold an end portal frame. */
    private static final int MIN_PADS = 24;
    /** The player's last positions kept, newest last: the pad they stood on may lie a few ticks back. */
    private static final int RECENT_TICKS = 10;
    /** A pad block is 13/16 of a block high. */
    private static final double PAD_HEIGHT = 13.0 / 16.0;

    private TeleportMazeTracker tracker = null;
    private long trackerCell = Long.MIN_VALUE;
    /** Cells looked at, fully loaded, that are not the maze. */
    private final Set<Long> notMaze = new HashSet<>();
    private final ArrayDeque<Vec3> recent = new ArrayDeque<>();
    /** The player is in the maze this tick. */
    private boolean here = false;

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.TELEPORT_MAZE;
    }

    @Override
    public void tick(SolverContext ctx) {
        here = false;
        RoomFrame frame = ctx.frame();
        if (frame == null) {
            recent.clear();
            return;
        }
        long key = ((long) frame.cellX() << 32) | (frame.cellZ() & 0xFFFFFFFFL);
        if (tracker == null || trackerCell != key) {
            if (notMaze.contains(key) || !loaded(ctx.level(), frame)) {
                recent.clear();
                return;
            }
            List<BlockPos> cellPads = TeleportMazePads.inWorld(frame, TeleportMazePads.CELL_PADS);
            List<BlockPos> centrePads = TeleportMazePads.inWorld(frame, TeleportMazePads.CENTRE_PADS);
            if (padsThere(ctx.level(), cellPads) + padsThere(ctx.level(), centrePads) < MIN_PADS) {
                notMaze.add(key);
                recent.clear();
                return;
            }
            tracker = new TeleportMazeTracker(cellPads, centrePads);
            trackerCell = key;
            recent.clear();
        }
        here = true;
        recent.addLast(ctx.player().position());
        while (recent.size() > RECENT_TICKS) recent.removeFirst();
    }

    @Override
    public void onServerTeleport(Vec3 pos, float yRot, float xRot) {
        if (tracker == null) return;
        // The pad the player stood on: the teleport can arrive a few ticks after they stepped on it.
        BlockPos from = null;
        for (Iterator<Vec3> it = recent.descendingIterator(); it.hasNext() && from == null; ) {
            from = tracker.padUnder(it.next());
        }
        tracker.onTeleport(from, pos, yRot);
        // Wherever it went, the positions before it are from somewhere else now.
        recent.clear();
        recent.addLast(pos);
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!here || tracker == null) return;
        for (BlockPos pad : tracker.usedCellPads()) draw.box(padBox(pad), SolverColors.wrong());
        Set<BlockPos> candidates = tracker.candidates();
        int color = candidates.size() == 1 ? SolverColors.solution() : SolverColors.next();
        for (BlockPos pad : candidates) draw.box(padBox(pad), color);
    }

    @Override
    public void reset() {
        tracker = null;
        trackerCell = Long.MIN_VALUE;
        notMaze.clear();
        recent.clear();
        here = false;
    }

    @Override
    public boolean claimsRoom() {
        return here;
    }

    @Override
    public String debugState() {
        if (tracker == null) return null;
        BlockPos end = tracker.finalPad();
        return String.format(Locale.ROOT, "%d pads used, %d possible%s", tracker.used().size(), tracker.candidates().size(),
                end == null ? "" : ", the one at " + end.getX() + ", " + end.getZ());
    }

    // --- world ---

    private static AABB padBox(BlockPos pad) {
        return new AABB(pad.getX(), pad.getY(), pad.getZ(), pad.getX() + 1, pad.getY() + PAD_HEIGHT, pad.getZ() + 1)
                .inflate(SolverDraw.BLOCK_INFLATE);
    }

    private static int padsThere(ClientLevel level, List<BlockPos> pads) {
        int n = 0;
        for (BlockPos pad : pads) {
            if (level.getBlockState(pad).is(Blocks.END_PORTAL_FRAME)) n++;
        }
        return n;
    }

    /** Whether every chunk of the room has arrived; a missing one reads as air. */
    private static boolean loaded(ClientLevel level, RoomFrame frame) {
        AABB room = frame.bounds();
        for (int cx = ((int) room.minX) >> 4; cx <= ((int) room.maxX - 1) >> 4; cx++) {
            for (int cz = ((int) room.minZ) >> 4; cz <= ((int) room.maxZ - 1) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }
}
