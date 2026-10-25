package net.alpaka.addons.features.dungeon.room;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.DungeonState;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Which dungeon room the player is standing in, and that room's {@link RoomFrame}.
 *
 * The Catacombs are a 6 x 6 grid of 32-block cells; the cell of a block is
 * {@code floorDiv(coord + 201, 32)} on each axis (rooms.md 2.1), which puts the one-block door gap
 * into the cell on its east/south side. A 1x1 room - every puzzle room is one - has blue terracotta
 * on its roof in one of its four corners, and which corner it is gives the room's rotation. Finding
 * it takes no data at all: the roof is the first block from Y 140 down in the room's centre column
 * that is neither air nor gold (rooms.md 3.3), and the four corners at that height are looked at.
 *
 * The result is kept per cell until the world changes. A cell whose chunks are not all loaded yet
 * reads as air, so that answer is not kept and the cell is looked at again a little later; a cell
 * without the terracotta (a larger room, the entrance, the fairy room) is kept as "no frame".
 *
 * While the player stands on a roof - ten or more blocks up from the room's floor level, the way
 * Odin tells it - the room does not count as the one they are in.
 *
 * Only reads blocks the client already has. Runs only while the Solvers master is on.
 */
public final class DungeonRooms {

    /** Ticks before a cell whose chunks were missing is looked at again. */
    private static final int RETRY_TICKS = 5;
    /** How far below the roof the player has to be to count as inside the room. */
    private static final int ROOF_MARGIN = 10;
    private static final int TOP_Y = 140;
    private static final int BOTTOM_Y = 12;

    /** What a probe found for a cell. */
    public enum Probe { FRAME, NO_CLAY, RETRY }

    /** The four corners in the order they are tried: NW, NE, SE, SW. */
    private static final int[][] CORNERS = {{-15, -15}, {15, -15}, {15, 15}, {-15, 15}};

    /** Cell key to its frame, or to {@link #NO_FRAME} for a cell without one. */
    private static final Map<Long, Object> CACHE = new HashMap<>();
    private static final Object NO_FRAME = new Object();
    private static final Map<Long, Long> RETRY_AT = new HashMap<>();
    private static ClientLevel cacheLevel = null;
    private static long ticks = 0L;

    private static int cellX = -1;
    private static int cellZ = -1;
    private static RoomFrame frame = null;
    private static boolean onRoof = false;
    private static Probe lastProbe = null;

    private DungeonRooms() {}

    /** The tick hook; called by DungeonSolvers.register, before the solvers' own tick. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DungeonRooms::tick);
    }

    /** World change or disconnect. */
    public static void reset() {
        CACHE.clear();
        RETRY_AT.clear();
        cacheLevel = null;
        clearCurrent();
    }

    private static void clearCurrent() {
        cellX = -1;
        cellZ = -1;
        frame = null;
        onRoof = false;
        lastProbe = null;
    }

    private static void tick(Minecraft mc) {
        ticks++;
        if (!AlpakaConfig.instance.dungeonSolversEnabled || mc.level == null || mc.player == null || !inRunOutsideBoss()) {
            clearCurrent();
            return;
        }
        update(mc.level, mc.player);
    }

    /** Works out the player's cell and its frame now. Also used by the debug command. */
    public static void update(ClientLevel level, LocalPlayer player) {
        if (level != cacheLevel) {
            CACHE.clear();
            RETRY_AT.clear();
            cacheLevel = level;
        }
        int cx = cellOf(player.getBlockX());
        int cz = cellOf(player.getBlockZ());
        if (!inGrid(cx, cz)) {
            clearCurrent();
            return;
        }
        cellX = cx;
        cellZ = cz;

        long key = key(cx, cz);
        Object known = CACHE.get(key);
        if (known == null && ticks >= RETRY_AT.getOrDefault(key, 0L)) {
            Probe[] result = new Probe[1];
            RoomFrame found = probe(worldView(level), cx, cz, result);
            lastProbe = result[0];
            switch (result[0]) {
                case FRAME -> CACHE.put(key, known = found);
                case NO_CLAY -> CACHE.put(key, known = NO_FRAME);
                case RETRY -> RETRY_AT.put(key, ticks + RETRY_TICKS);
            }
        } else if (known != null) {
            lastProbe = known == NO_FRAME ? Probe.NO_CLAY : Probe.FRAME;
        }
        frame = known instanceof RoomFrame f ? f : null;
        onRoof = frame != null && player.getBlockY() > frame.roofY() - ROOF_MARGIN;
    }

    /** In a run, on SkyBlock, and not yet in the floor's boss room. */
    private static boolean inRunOutsideBoss() {
        return SkyblockUtils.isOnSkyblock() && DungeonState.inDungeon() && !inBossRoom();
    }

    /**
     * Whether the player is in the floor's boss room. DungeonState knows from the boss's first chat
     * line; a player who rejoins mid-boss has missed that line, so where they stand counts too.
     */
    public static boolean inBossRoom() {
        if (DungeonState.getInBossRoom()) return true;
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && bossArea(DungeonState.getFloor(), player.getX(), player.getZ());
    }

    /**
     * Whether a position lies in the boss room of {@code floor} ("F1".."F7", "M1".."M7"), by the
     * corner past which each floor's boss room is built (rooms.md 2.1, the bounds Odin uses). The
     * room grid never reaches past these corners, so a room is never mistaken for the boss room.
     * False for the Entrance and anything unknown.
     */
    public static boolean bossArea(String floor, double x, double z) {
        if (floor == null || floor.length() < 2) return false;
        int number;
        try {
            number = Integer.parseInt(floor.substring(1));
        } catch (NumberFormatException e) {
            return false;
        }
        return switch (number) {
            case 1 -> x > -71 && z > -39;
            case 2, 3, 4 -> x > -39 && z > -39;
            case 5, 6 -> x > -39 && z > -7;
            case 7 -> x > -7 && z > -7;
            default -> false;
        };
    }

    /** In a run outside the boss room, inside the room grid. */
    public static boolean active() {
        return cellX >= 0 && inRunOutsideBoss();
    }

    /**
     * The frame of the room the player is in, or null: outside the grid, in a room without blue
     * terracotta, before its chunks loaded, standing on its roof, or with the Solvers master off.
     */
    public static RoomFrame current() {
        return active() && !onRoof ? frame : null;
    }

    /** The frame of the player's cell even on its roof or outside a run, or null; for the debug command. */
    public static RoomFrame cellFrame() {
        return frame;
    }

    /** The player's grid column, 0..5, or -1 outside the grid or while not tracking. */
    public static int cellX() {
        return cellX;
    }

    /** The player's grid row, 0..5, or -1 outside the grid or while not tracking. */
    public static int cellZ() {
        return cellZ;
    }

    /** The blocks of the player's cell, whether or not it has a frame; null outside the grid. */
    public static AABB cellBounds() {
        return cellX < 0 ? null : RoomFrame.cellBounds(cellX, cellZ);
    }

    /** Whether the player is up on the roof of their cell's room. */
    public static boolean onRoof() {
        return onRoof;
    }

    /** What the last look at the player's cell found, or null; for the debug command. */
    public static Probe lastProbe() {
        return lastProbe;
    }

    /** The grid cell of a block coordinate on either axis; may lie outside 0..5. */
    public static int cellOf(int blockCoord) {
        return Math.floorDiv(blockCoord + 201, RoomFrame.CELL_SIZE);
    }

    public static boolean inGrid(int cellX, int cellZ) {
        return cellX >= 0 && cellX <= 5 && cellZ >= 0 && cellZ <= 5;
    }

    private static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    // --- the probe, kept apart from the world so it can be tested ---

    /** What the probe needs to know about a block. */
    public enum Kind {
        /** Air or gold: the roof search goes on past it. */
        SKIP,
        /** The blue terracotta corner block. */
        CLAY,
        /** Anything else. */
        SOLID
    }

    /** The bits of the world the probe reads. */
    public interface RoofView {
        boolean hasChunk(int chunkX, int chunkZ);

        Kind kindAt(int x, int y, int z);
    }

    /**
     * Looks for the roof and the blue terracotta of the room in a cell. Returns the frame, or null
     * with {@code result[0]} saying whether to try again later (a chunk was missing, or the column
     * held nothing yet) or whether the room has no such corner.
     */
    public static RoomFrame probe(RoofView world, int cellX, int cellZ, Probe[] result) {
        int cx = RoomFrame.centre(cellX);
        int cz = RoomFrame.centre(cellZ);
        result[0] = Probe.RETRY;
        if (!world.hasChunk(cx >> 4, cz >> 4)) return null;
        int roofY = Integer.MIN_VALUE;
        for (int y = TOP_Y; y >= BOTTOM_Y; y--) {
            if (world.kindAt(cx, y, cz) != Kind.SKIP) {
                roofY = y;
                break;
            }
        }
        if (roofY == Integer.MIN_VALUE) return null;
        for (int[] corner : CORNERS) {
            int x = cx + corner[0];
            int z = cz + corner[1];
            // A corner in a chunk that is not here yet reads as air, which would look like no clay.
            if (!world.hasChunk(x >> 4, z >> 4)) return null;
            if (world.kindAt(x, roofY, z) == Kind.CLAY) {
                result[0] = Probe.FRAME;
                return RoomFrame.of(cellX, cellZ, x, z, roofY);
            }
        }
        result[0] = Probe.NO_CLAY;
        return null;
    }

    private static RoofView worldView(ClientLevel level) {
        Block clay = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return new RoofView() {
            @Override
            public boolean hasChunk(int chunkX, int chunkZ) {
                return level.hasChunk(chunkX, chunkZ);
            }

            @Override
            public Kind kindAt(int x, int y, int z) {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir() || state.is(Blocks.GOLD_BLOCK)) return Kind.SKIP;
                return state.is(clay) ? Kind.CLAY : Kind.SOLID;
            }
        };
    }
}
