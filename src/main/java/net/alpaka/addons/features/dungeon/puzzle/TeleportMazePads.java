package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the Teleport Maze's pads are, in room coordinates (X/Z in the room frame, Y absolute).
 *
 * Seven walled cells with four pads each, set three blocks out from the cell's middle on both
 * axes, and two pads in the room's middle: the start (15, 69, 12) and the end by the reward chest
 * (15, 69, 14). Every pad is an end portal frame at Y 69. Odin and Skyblocker list the same
 * thirty positions (puzzles-entities.md 4.2), so they are the room's, not either mod's.
 */
public final class TeleportMazePads {

    /** The height of every pad block. */
    public static final int PAD_Y = 69;

    /** The middles of the seven cells, {x, z}; each cell's pads are at x +-3, z +-3 from it. */
    private static final int[][] CELLS = {{7, 9}, {7, 17}, {7, 25}, {15, 25}, {23, 25}, {23, 17}, {23, 9}};

    /** The 28 cell pads, cell by cell. */
    public static final List<BlockPos> CELL_PADS = cellPads();
    /** The start pad, where the player comes in. */
    public static final BlockPos START = new BlockPos(15, PAD_Y, 12);
    /** The end pad, next to the reward chest. */
    public static final BlockPos END = new BlockPos(15, PAD_Y, 14);
    public static final List<BlockPos> CENTRE_PADS = List.of(START, END);

    private TeleportMazePads() {}

    private static List<BlockPos> cellPads() {
        List<BlockPos> pads = new ArrayList<>();
        for (int[] cell : CELLS) {
            for (int dx = -3; dx <= 3; dx += 6) {
                for (int dz = -3; dz <= 3; dz += 6) pads.add(new BlockPos(cell[0] + dx, PAD_Y, cell[1] + dz));
            }
        }
        return List.copyOf(pads);
    }

    /** {@code rel} turned into the world with {@code frame}. */
    public static List<BlockPos> inWorld(RoomFrame frame, List<BlockPos> rel) {
        return rel.stream().map(frame::toWorld).toList();
    }
}
