package net.alpaka.addons.features.dungeon.room;

import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The roof and blue terracotta probe (rooms.md 3.3) on a made-up world. */
class DungeonRoomsProbeTest {

    /** A world of blocks by position, with a set of chunks that are loaded. */
    private static final class World implements DungeonRooms.RoofView {
        final Map<Long, DungeonRooms.Kind> blocks = new HashMap<>();
        final Set<Long> missingChunks = new HashSet<>();

        void put(int x, int y, int z, DungeonRooms.Kind kind) {
            blocks.put(key(x, y, z), kind);
        }

        static long key(int x, int y, int z) {
            return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        }

        @Override
        public boolean hasChunk(int chunkX, int chunkZ) {
            return !missingChunks.contains(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
        }

        @Override
        public DungeonRooms.Kind kindAt(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), DungeonRooms.Kind.SKIP);
        }
    }

    /** Cell (1, 2): a roof at Y 84 with a gold block on top of it, and the clay in the given corner. */
    private static World room(int dx, int dz) {
        World world = new World();
        int cx = RoomFrame.centre(1);
        int cz = RoomFrame.centre(2);
        for (int y = 60; y <= 84; y++) world.put(cx, y, cz, DungeonRooms.Kind.SOLID);
        world.put(cx, 85, cz, DungeonRooms.Kind.SKIP); // the gold block is passed over
        for (int[] corner : new int[][]{{-15, -15}, {15, -15}, {15, 15}, {-15, 15}}) {
            world.put(cx + corner[0], 84, cz + corner[1], DungeonRooms.Kind.SOLID);
        }
        world.put(cx + dx, 84, cz + dz, DungeonRooms.Kind.CLAY);
        return world;
    }

    @Test
    void findsTheClayInEachCorner() {
        int[][] corners = {{-15, -15}, {15, -15}, {15, 15}, {-15, 15}};
        Rotation[] expected = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
        for (int i = 0; i < 4; i++) {
            DungeonRooms.Probe[] result = new DungeonRooms.Probe[1];
            RoomFrame frame = DungeonRooms.probe(room(corners[i][0], corners[i][1]), 1, 2, result);
            assertNotNull(frame);
            assertEquals(DungeonRooms.Probe.FRAME, result[0]);
            assertEquals(expected[i], frame.rotation());
            assertEquals(84, frame.roofY());
            assertEquals(RoomFrame.centre(1) + corners[i][0], frame.originX());
        }
    }

    @Test
    void aRoomWithoutClayIsRememberedAsSuch() {
        World world = room(15, 15);
        world.put(RoomFrame.centre(1) + 15, 84, RoomFrame.centre(2) + 15, DungeonRooms.Kind.SOLID);
        DungeonRooms.Probe[] result = new DungeonRooms.Probe[1];
        assertNull(DungeonRooms.probe(world, 1, 2, result));
        assertEquals(DungeonRooms.Probe.NO_CLAY, result[0]);
    }

    @Test
    void aMissingChunkMeansTryAgain() {
        World world = room(15, 15);
        // The SE corner's chunk is not loaded: it would read as air, which must not count as "no clay".
        int x = RoomFrame.centre(1) + 15;
        int z = RoomFrame.centre(2) + 15;
        world.missingChunks.add(((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL));
        DungeonRooms.Probe[] result = new DungeonRooms.Probe[1];
        assertNull(DungeonRooms.probe(world, 1, 2, result));
        assertEquals(DungeonRooms.Probe.RETRY, result[0]);
    }

    @Test
    void anEmptyColumnMeansTryAgain() {
        DungeonRooms.Probe[] result = new DungeonRooms.Probe[1];
        assertNull(DungeonRooms.probe(new World(), 1, 2, result));
        assertEquals(DungeonRooms.Probe.RETRY, result[0]);
    }

    /** The boss room by position (rooms.md 2.1), for a player who rejoined after the boss's first line. */
    @Test
    void bossRoomsLiePastTheGrid() {
        assertTrue(DungeonRooms.bossArea("F7", 50, 80));
        assertTrue(DungeonRooms.bossArea("M7", -6, -6));
        assertFalse(DungeonRooms.bossArea("F7", -8, 50));
        assertTrue(DungeonRooms.bossArea("F1", -70, -38));
        assertFalse(DungeonRooms.bossArea("F1", -72, -38));
        assertTrue(DungeonRooms.bossArea("M3", -38, -38));
        assertFalse(DungeonRooms.bossArea("M3", -38, -40));
        assertTrue(DungeonRooms.bossArea("F6", -30, 10));
        assertFalse(DungeonRooms.bossArea("F6", -30, -8));
        // The Entrance's only boss never opens a boss room; unknown floors never count.
        assertFalse(DungeonRooms.bossArea("E", 50, 50));
        assertFalse(DungeonRooms.bossArea(null, 50, 50));
        assertFalse(DungeonRooms.bossArea("Fx", 50, 50));
        // No block of F7's full 6 x 6 grid is mistaken for the boss room.
        for (int x = RoomFrame.minCorner(0); x <= RoomFrame.minCorner(5) + 31; x++) {
            assertFalse(DungeonRooms.bossArea("F7", x + 0.99, RoomFrame.minCorner(5) + 31.99), "x " + x);
            assertFalse(DungeonRooms.bossArea("F7", RoomFrame.minCorner(5) + 31.99, x + 0.99), "z " + x);
        }
    }
}
