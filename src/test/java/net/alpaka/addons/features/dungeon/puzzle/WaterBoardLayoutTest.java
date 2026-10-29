package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.WaterBoardLayout.Cell;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoardLayout.DoorRead;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoardLayout.Doors;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the Water Board room from its blocks (puzzles-water.md 1, 2 and the fixtures in 8.1, 8.2). */
class WaterBoardLayoutTest {

    /** Blocks by room-relative position; air elsewhere. */
    static final class Room implements WaterBoardLayout.View {
        final Map<BlockPos, Cell> blocks = new HashMap<>();
        boolean unloaded = false;

        Room put(int x, int y, int z, Cell cell) {
            blocks.put(new BlockPos(x, y, z), cell);
            return this;
        }

        @Override
        public Cell at(int rx, int y, int rz) {
            if (unloaded) return Cell.UNLOADED;
            return blocks.getOrDefault(new BlockPos(rx, y, rz), Cell.AIR);
        }
    }

    /** A room with the doors at {@code closed} (door indices) shut on both blocks. */
    private static Room doors(int... closed) {
        Room room = new Room();
        for (int i : closed) {
            room.put(15, 56, 19 - i, Cell.OTHER);
            room.put(15, 57, 19 - i, Cell.OTHER);
        }
        return room;
    }

    /** Every lever's representative block of {@code variant}, on its start plane or moved to the other one. */
    private static Room board(int variant, boolean moved) {
        Room room = new Room();
        for (WaterBoardLever lever : WaterBoardLever.values()) {
            BlockPos start = lever.representative(variant);
            if (start == null) continue;
            int z = moved ? (start.getZ() == 26 ? 27 : 26) : start.getZ();
            room.put(start.getX(), start.getY(), z, lever.material());
        }
        return room;
    }

    // --- 8.1: the layout from the pair at the entrance ---

    @Test
    void layoutFromTheEntrancePair() {
        assertEquals(1, WaterBoardLayout.variant(new Room().put(16, 77, 26, Cell.GOLD).put(14, 77, 27, Cell.TERRACOTTA)));
        assertEquals(2, WaterBoardLayout.variant(new Room().put(14, 78, 26, Cell.QUARTZ).put(16, 78, 27, Cell.EMERALD)));
        assertEquals(3, WaterBoardLayout.variant(new Room().put(16, 78, 26, Cell.QUARTZ).put(14, 78, 27, Cell.DIAMOND)));
        assertEquals(4, WaterBoardLayout.variant(new Room().put(16, 78, 26, Cell.GOLD).put(14, 78, 27, Cell.QUARTZ)));
    }

    @Test
    void layoutStillReadsAfterTheLeversWereFlipped() {
        // Both blocks moved to the other plane: the box spans both, so the pair is still there.
        assertEquals(1, WaterBoardLayout.variant(new Room().put(16, 77, 27, Cell.GOLD).put(14, 77, 26, Cell.TERRACOTTA)));
        for (int variant = 1; variant <= 4; variant++) {
            assertEquals(variant, WaterBoardLayout.variant(board(variant, false)), "layout " + variant + " as built");
            assertEquals(variant, WaterBoardLayout.variant(board(variant, true)), "layout " + variant + " all flipped");
        }
    }

    @Test
    void anIncompletePairIsUnknownAndTheFirstMatchingPairWins() {
        assertEquals(0, WaterBoardLayout.variant(new Room().put(16, 78, 26, Cell.GOLD)));
        assertEquals(0, WaterBoardLayout.variant(new Room()));
        // Gold, terracotta and quartz: layouts are tried in order, so 1 before 4.
        assertEquals(1, WaterBoardLayout.variant(new Room().put(16, 77, 26, Cell.GOLD)
                .put(14, 77, 27, Cell.TERRACOTTA).put(14, 78, 27, Cell.QUARTZ)));
        Room unloaded = board(2, false);
        unloaded.unloaded = true;
        assertEquals(0, WaterBoardLayout.variant(unloaded));
    }

    // --- 8.2: the doors ---

    @Test
    void closedDoorsMakeTheKey() {
        Doors a = WaterBoardLayout.doors(doors(0, 2, 4), false);
        assertTrue(a.clear());
        assertEquals("024", a.key());
        assertEquals(3, a.closedCount());
        assertEquals("123", WaterBoardLayout.doors(doors(1, 2, 3), false).key());
        Doors two = WaterBoardLayout.doors(doors(0, 1), false);
        assertEquals(2, two.closedCount());
        assertFalse(two.allOpen());
        assertTrue(WaterBoardLayout.doors(doors(), false).allOpen());
        assertEquals(List.of(19, 18, 17, 16, 15), List.of(WaterBoardLayout.doorZ(0), WaterBoardLayout.doorZ(1),
                WaterBoardLayout.doorZ(2), WaterBoardLayout.doorZ(3), WaterBoardLayout.doorZ(4)));
    }

    @Test
    void aDoorWhoseBlocksDisagreeIsReadAgain() {
        Room room = doors(0, 4).put(15, 56, 17, Cell.OTHER); // door 2: lower block shut, upper air
        Doors doors = WaterBoardLayout.doors(room, false);
        assertEquals(DoorRead.AMBIGUOUS, doors.read());
        assertFalse(doors.clear());
        assertFalse(doors.allOpen());
        // Reading the lower block alone, as Odin does, settles it.
        assertEquals("024", WaterBoardLayout.doors(room, true).key());
        Room unloaded = doors(0, 2, 4);
        unloaded.unloaded = true;
        assertEquals(DoorRead.UNLOADED, WaterBoardLayout.doors(unloaded, false).read());
    }

    @Test
    void doorKeysAreAscendingIndices() {
        assertEquals("013", WaterBoardLayout.doorKey(0b01011));
        assertEquals("234", WaterBoardLayout.doorKey(0b11100));
        assertEquals("", WaterBoardLayout.doorKey(0));
    }

    // --- the levers' state ---

    @Test
    void aLeverIsFlippedWhenItsBlockIsOnTheOtherPlane() {
        for (int variant = 1; variant <= 4; variant++) {
            Room built = board(variant, false);
            Room moved = board(variant, true);
            for (WaterBoardLever lever : WaterBoardLever.values()) {
                if (lever.representative(variant) == null) {
                    assertNull(WaterBoardLayout.flipped(built, lever, variant), lever + " in " + variant);
                    continue;
                }
                assertEquals(Boolean.FALSE, WaterBoardLayout.flipped(built, lever, variant), lever + " in " + variant);
                assertEquals(Boolean.TRUE, WaterBoardLayout.flipped(moved, lever, variant), lever + " in " + variant);
            }
        }
        // On neither plane (moving, or not there): unknown.
        assertNull(WaterBoardLayout.flipped(new Room(), WaterBoardLever.GOLD, 1));
        assertNull(WaterBoardLayout.flipped(new Room(), WaterBoardLever.WATER, 1));
    }

    @Test
    void layoutThreeHasNoCoalOrTerracotta() {
        assertNull(WaterBoardLever.COAL.representative(3));
        assertNull(WaterBoardLever.TERRACOTTA.representative(3));
        assertNull(WaterBoardLever.GOLD.representative(0));
        assertNull(WaterBoardLever.GOLD.representative(5));
        assertEquals(new BlockPos(14, 68, 27), WaterBoardLever.EMERALD.representative(1));
    }

    @Test
    void leversByPositionAndKey() {
        assertEquals(WaterBoardLever.COAL, WaterBoardLever.at(new BlockPos(20, 61, 10)));
        assertEquals(WaterBoardLever.TERRACOTTA, WaterBoardLever.at(new BlockPos(10, 61, 10)));
        assertEquals(WaterBoardLever.WATER, WaterBoardLever.at(new BlockPos(15, 60, 5)));
        assertNull(WaterBoardLever.at(new BlockPos(15, 61, 5)));
        assertEquals(WaterBoardLever.TERRACOTTA, WaterBoardLever.byKey("terracotta"));
        assertNull(WaterBoardLever.byKey("hardened_clay"));
    }

    // --- water ---

    @Test
    void waterOnTheBoardAndAtTheEntrance() {
        assertEquals(Boolean.FALSE, WaterBoardLayout.waterOnBoard(board(2, false)));
        assertEquals(Boolean.TRUE, WaterBoardLayout.waterOnBoard(new Room().put(6, 58, 26, Cell.WATER)));
        // Behind the board is not on it.
        assertEquals(Boolean.FALSE, WaterBoardLayout.waterOnBoard(new Room().put(10, 70, 27, Cell.WATER)));
        assertEquals(Boolean.FALSE, WaterBoardLayout.waterAtEntrance(new Room().put(10, 70, 26, Cell.WATER)));
        assertEquals(Boolean.TRUE, WaterBoardLayout.waterAtEntrance(new Room().put(15, 81, 26, Cell.WATER)));
        Room unloaded = new Room();
        unloaded.unloaded = true;
        assertNull(WaterBoardLayout.waterOnBoard(unloaded));
        assertNull(WaterBoardLayout.waterAtEntrance(unloaded));
    }

    // --- 8.3.9: the room in every rotation ---

    /** The four frames of the room whose cell starts at (-200, -136): clay at NW, NE, SE, SW. */
    private static List<RoomFrame> frames() {
        int x0 = -200;
        int z0 = -136;
        return List.of(
                RoomFrame.of(0, 2, x0, z0, 84),
                RoomFrame.of(0, 2, x0 + 30, z0, 84),
                RoomFrame.of(0, 2, x0 + 30, z0 + 30, 84),
                RoomFrame.of(0, 2, x0, z0 + 30, 84));
    }

    @Test
    void roomPositionsInEveryRotation() {
        List<RoomFrame> frames = frames();
        assertPositions(frames, WaterBoardLever.COAL.lever(), "-180,61,-126", "-180,61,-116", "-190,61,-116", "-190,61,-126");
        assertPositions(frames, WaterBoardLever.WATER.lever(), "-185,60,-131", "-175,60,-121", "-185,60,-111", "-195,60,-121");
        assertPositions(frames, new BlockPos(15, 56, 22), "-185,56,-114", "-192,56,-121", "-185,56,-128", "-178,56,-121");
        assertPositions(frames, new BlockPos(15, 56, WaterBoardLayout.doorZ(0)), "-185,56,-117", "-189,56,-121", "-185,56,-125", "-181,56,-121");
        assertPositions(frames, new BlockPos(15, 78, 26), "-185,78,-110", "-196,78,-121", "-185,78,-132", "-174,78,-121");
        for (RoomFrame frame : frames) {
            for (WaterBoardLever lever : WaterBoardLever.values()) {
                assertEquals(lever, WaterBoardLever.at(frame.toRel(frame.toWorld(lever.lever()))), frame.corner());
            }
        }
    }

    private static void assertPositions(List<RoomFrame> frames, BlockPos rel, String... expected) {
        for (int i = 0; i < frames.size(); i++) {
            BlockPos world = frames.get(i).toWorld(rel);
            assertEquals(expected[i], world.getX() + "," + world.getY() + "," + world.getZ(), frames.get(i).corner() + " " + rel);
        }
    }

    /**
     * The same board built in the world in each rotation and read back through the frame, the way
     * the solver reads it: the room, its layout and its doors come out the same every time.
     */
    @Test
    void theRoomReadsTheSameInEveryRotation() {
        Room relative = board(3, false);
        for (int i : new int[] {1, 2, 4}) {
            relative.put(15, 56, 19 - i, Cell.OTHER).put(15, 57, 19 - i, Cell.OTHER);
        }
        for (WaterBoardLever lever : WaterBoardLever.values()) {
            relative.put(lever.lever().getX(), lever.lever().getY(), lever.lever().getZ(), Cell.LEVER);
        }
        for (RoomFrame frame : frames()) {
            Map<BlockPos, Cell> world = new HashMap<>();
            relative.blocks.forEach((rel, cell) -> world.put(frame.toWorld(rel), cell));
            WaterBoardLayout.View view = (rx, y, rz) -> world.getOrDefault(frame.toWorld(rx, y, rz), Cell.AIR);
            assertTrue(WaterBoardLayout.isRoom(view), frame.corner());
            assertEquals(3, WaterBoardLayout.variant(view), frame.corner());
            assertEquals("124", WaterBoardLayout.doors(view, false).key(), frame.corner());
            // The same blocks read in the wrong rotation are not the room.
            RoomFrame turned = RoomFrame.of(0, 2, frame.originX() == -200 ? -170 : -200, frame.originZ(), 84);
            WaterBoardLayout.View wrong = (rx, y, rz) -> world.getOrDefault(turned.toWorld(rx, y, rz), Cell.AIR);
            assertFalse(WaterBoardLayout.isRoom(wrong), frame.corner() + " read as " + turned.corner());
        }
    }

    @Test
    void theRoomNeedsAllSevenLevers() {
        Room room = new Room();
        for (WaterBoardLever lever : WaterBoardLever.values()) {
            room.put(lever.lever().getX(), lever.lever().getY(), lever.lever().getZ(), Cell.LEVER);
        }
        assertTrue(WaterBoardLayout.isRoom(room));
        room.put(15, 60, 5, Cell.AIR);
        assertFalse(WaterBoardLayout.isRoom(room));
    }
}
