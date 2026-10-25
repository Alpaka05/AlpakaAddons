package net.alpaka.addons.features.dungeon.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The room frame's transforms (rooms.md 2.6 and 3.4), in all four rotations. */
class RoomFrameTest {

    /** The four frames of cell (2, 3): centre (-121, -89), clay at each corner in turn. */
    private static List<RoomFrame> cornersOfCell23() {
        int cx = RoomFrame.centre(2);
        int cz = RoomFrame.centre(3);
        return List.of(
                RoomFrame.of(2, 3, cx - 15, cz - 15, 80),
                RoomFrame.of(2, 3, cx + 15, cz - 15, 80),
                RoomFrame.of(2, 3, cx + 15, cz + 15, 80),
                RoomFrame.of(2, 3, cx - 15, cz + 15, 80));
    }

    @Test
    void theCornerDecidesTheRotation() {
        List<RoomFrame> frames = cornersOfCell23();
        assertEquals(Rotation.NONE, frames.get(0).rotation());
        assertEquals(Rotation.CLOCKWISE_90, frames.get(1).rotation());
        assertEquals(Rotation.CLOCKWISE_180, frames.get(2).rotation());
        assertEquals(Rotation.COUNTERCLOCKWISE_90, frames.get(3).rotation());
        assertEquals(List.of("NW", "NE", "SE", "SW"), frames.stream().map(RoomFrame::corner).toList());
        assertThrows(IllegalArgumentException.class, () -> RoomFrame.of(2, 3, RoomFrame.centre(2), RoomFrame.centre(3), 80));
    }

    @Test
    void cellGeometryMatchesTheGrid() {
        assertEquals(-185, RoomFrame.centre(0));
        assertEquals(-153, RoomFrame.centre(1));
        assertEquals(-200, RoomFrame.minCorner(0));
        assertEquals(-168, RoomFrame.minCorner(1));
    }

    @Test
    void theClayIsTheOriginAndTheOppositeCornerIs30_30() {
        int x0 = RoomFrame.minCorner(2);
        int z0 = RoomFrame.minCorner(3);
        for (RoomFrame frame : cornersOfCell23()) {
            assertEquals(new BlockPos(0, 70, 0), frame.toRel(new BlockPos(frame.originX(), 70, frame.originZ())), frame.corner());
            int oppositeX = frame.originX() == x0 ? x0 + 30 : x0;
            int oppositeZ = frame.originZ() == z0 ? z0 + 30 : z0;
            assertEquals(new BlockPos(30, 70, 30), frame.toRel(new BlockPos(oppositeX, 70, oppositeZ)), frame.corner());
            assertEquals(new BlockPos(oppositeX, 70, oppositeZ), frame.toWorld(30, 70, 30), frame.corner());
        }
    }

    /** rooms.md 3.4: the NE frame of cell (0, 0), clay at (-170, -200); Odin's Quiz answer (20, 70, 6). */
    @Test
    void crossCheckValueFromTheReferences() {
        RoomFrame ne = RoomFrame.of(0, 0, -170, -200, 90);
        assertEquals(Rotation.CLOCKWISE_90, ne.rotation());
        assertEquals(new BlockPos(-176, 70, -180), ne.toWorld(new BlockPos(20, 70, 6)));
        assertEquals(new BlockPos(20, 70, 6), ne.toRel(new BlockPos(-176, 70, -180)));
    }

    @Test
    void theTableOfRooms26Holds() {
        // rel -> world for (rx, rz) = (3, 7), (ox, oz) the clay.
        RoomFrame nw = RoomFrame.of(0, 0, -200, -200, 80);
        RoomFrame ne = RoomFrame.of(0, 0, -170, -200, 80);
        RoomFrame se = RoomFrame.of(0, 0, -170, -170, 80);
        RoomFrame sw = RoomFrame.of(0, 0, -200, -170, 80);
        assertEquals(new BlockPos(-200 + 3, 1, -200 + 7), nw.toWorld(3, 1, 7));
        assertEquals(new BlockPos(-170 - 7, 1, -200 + 3), ne.toWorld(3, 1, 7));
        assertEquals(new BlockPos(-170 - 3, 1, -170 - 7), se.toWorld(3, 1, 7));
        assertEquals(new BlockPos(-200 + 7, 1, -170 - 3), sw.toWorld(3, 1, 7));
    }

    @Test
    void blocksRoundTripInEveryRotation() {
        for (RoomFrame frame : cornersOfCell23()) {
            for (int rx = 0; rx <= 30; rx += 3) {
                for (int rz = 0; rz <= 30; rz += 5) {
                    BlockPos rel = new BlockPos(rx, 69, rz);
                    BlockPos world = frame.toWorld(rel);
                    assertTrue(frame.contains(world.getX() + 0.5, world.getZ() + 0.5), frame.corner() + " " + rel);
                    assertEquals(rel, frame.toRel(world), frame.corner());
                }
            }
        }
    }

    @Test
    void positionsTurnAboutBlockCentres() {
        for (RoomFrame frame : cornersOfCell23()) {
            BlockPos rel = new BlockPos(20, 70, 6);
            // A block's centre goes where the block goes.
            assertClose(Vec3.atCenterOf(frame.toWorld(rel)), frame.toWorld(Vec3.atCenterOf(rel)), frame.corner());
            Vec3 point = new Vec3(4.25, 71.5, 12.75);
            assertClose(point, frame.toRel(frame.toWorld(point)), frame.corner());
        }
    }

    @Test
    void boxesStayTheSameSize() {
        for (RoomFrame frame : cornersOfCell23()) {
            AABB rel = new AABB(10, 65, 6, 13, 66, 8);
            AABB world = frame.toWorld(rel);
            assertEquals(rel.getXsize() * rel.getZsize(), world.getXsize() * world.getZsize(), 1e-9, frame.corner());
            AABB back = frame.toRel(world);
            assertEquals(rel.minX, back.minX, 1e-9);
            assertEquals(rel.maxZ, back.maxZ, 1e-9);
            // A one-block box at rel (5, 5) is exactly that block in the world.
            AABB block = frame.toWorld(new AABB(new BlockPos(5, 70, 5)));
            assertEquals(new AABB(frame.toWorld(new BlockPos(5, 70, 5))), block);
        }
    }

    @Test
    void facingsTurnWithTheRoom() {
        RoomFrame ne = RoomFrame.of(0, 0, -170, -200, 80);
        // rel +X runs along world +Z in the NE frame.
        assertEquals(Direction.SOUTH, ne.toWorld(Direction.EAST));
        for (RoomFrame frame : cornersOfCell23()) {
            for (Direction d : Direction.values()) assertEquals(d, frame.toRel(frame.toWorld(d)), frame.corner());
            assertEquals(Direction.UP, frame.toWorld(Direction.UP));
            // The direction a block step takes agrees with the block transform.
            BlockPos a = frame.toWorld(new BlockPos(10, 70, 10));
            BlockPos b = frame.toWorld(new BlockPos(11, 70, 10));
            assertEquals(b, a.relative(frame.toWorld(Direction.EAST)), frame.corner());
        }
    }

    @Test
    void boundsCoverTheCellOnly() {
        RoomFrame frame = cornersOfCell23().get(0);
        AABB bounds = frame.bounds();
        assertEquals(RoomFrame.minCorner(2), bounds.minX);
        assertEquals(RoomFrame.minCorner(2) + 31, bounds.maxX);
        assertTrue(frame.contains(RoomFrame.minCorner(2), RoomFrame.minCorner(3)));
        assertTrue(frame.contains(RoomFrame.minCorner(2) + 30.9, RoomFrame.minCorner(3) + 30.9));
        assertFalse(frame.contains(RoomFrame.minCorner(2) + 31, RoomFrame.minCorner(3)));
        assertFalse(frame.contains(RoomFrame.minCorner(2) - 0.1, RoomFrame.minCorner(3)));
    }

    @Test
    void cellOfABlock() {
        assertEquals(0, DungeonRooms.cellOf(-200));
        assertEquals(0, DungeonRooms.cellOf(-170));
        // The one-block door gap belongs to the next cell (rooms.md, critic additions 6).
        assertEquals(1, DungeonRooms.cellOf(-169));
        assertEquals(1, DungeonRooms.cellOf(-138));
        assertEquals(5, DungeonRooms.cellOf(-10));
        assertEquals(-1, DungeonRooms.cellOf(-202));
        assertEquals(6, DungeonRooms.cellOf(-9));
        assertTrue(DungeonRooms.inGrid(0, 5));
        assertFalse(DungeonRooms.inGrid(6, 0));
    }

    private static void assertClose(Vec3 expected, Vec3 actual, String message) {
        assertEquals(expected.x, actual.x, 1e-9, message);
        assertEquals(expected.y, actual.y, 1e-9, message);
        assertEquals(expected.z, actual.z, 1e-9, message);
    }
}
