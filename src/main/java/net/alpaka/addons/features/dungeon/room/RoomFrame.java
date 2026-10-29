package net.alpaka.addons.features.dungeon.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The coordinate frame of one 1x1 dungeon room, so puzzle data can be written once and found in
 * every rotation of the room.
 *
 * Every normal 1x1 room carries a blue terracotta block on its roof, in one of its four corners. The
 * frame's origin is that block's X/Z: relative X and Z then run 0..30 into the room, the same way in
 * every copy of the room, whichever way it was placed. Y is never moved; puzzle data uses absolute Y.
 * This is the frame Odin, Skyblocker and NoammAddons all use, so their relative positions (the Quiz
 * answers at (20,70,6), (15,70,9), (10,70,6), ...) are numbers in this frame (rooms.md 2.6).
 *
 * The rotation is the vanilla {@link Rotation} R with {@code world = origin + rel.rotate(R)}: NONE
 * for a clay corner at the north-west (min X, min Z), CLOCKWISE_90 at the north-east,
 * CLOCKWISE_180 at the south-east, COUNTERCLOCKWISE_90 at the south-west. Continuous positions turn
 * about the clay block's centre rather than its corner, or a rotated room would be off by one block.
 *
 * Pure: no world access, unit-tested in RoomFrameTest.
 *
 * @param cellX    the room's grid column, 0..5
 * @param cellZ    the room's grid row, 0..5
 * @param originX  X of the blue terracotta corner
 * @param originZ  Z of the blue terracotta corner
 * @param roofY    the roof height the corner was found at
 * @param rotation how relative coordinates turn into world ones
 */
public record RoomFrame(int cellX, int cellZ, int originX, int originZ, int roofY, Rotation rotation) {

    /** Blocks per grid step: a 31-block room plus the one-block gap where the doors are. */
    public static final int CELL_SIZE = 32;
    /** Room centre of cell i is {@code CENTRE_OFFSET + 32 * i}, on both axes. */
    public static final int CENTRE_OFFSET = -185;
    /** A room's corners are this far from its centre. */
    public static final int HALF = 15;

    public RoomFrame {
        if (rotation == null) throw new IllegalArgumentException("rotation");
    }

    /**
     * The frame of a room whose clay sits at ({@code cornerX}, {@code cornerZ}); the rotation follows
     * from which of the cell's four corners that is. Throws if it is none of them.
     */
    public static RoomFrame of(int cellX, int cellZ, int cornerX, int cornerZ, int roofY) {
        int dx = cornerX - centre(cellX);
        int dz = cornerZ - centre(cellZ);
        return new RoomFrame(cellX, cellZ, cornerX, cornerZ, roofY, rotationOfCorner(dx, dz));
    }

    /** The rotation for a clay block at (±15, ±15) from the room centre. */
    public static Rotation rotationOfCorner(int dx, int dz) {
        if (dx == -HALF && dz == -HALF) return Rotation.NONE;
        if (dx == HALF && dz == -HALF) return Rotation.CLOCKWISE_90;
        if (dx == HALF && dz == HALF) return Rotation.CLOCKWISE_180;
        if (dx == -HALF && dz == HALF) return Rotation.COUNTERCLOCKWISE_90;
        throw new IllegalArgumentException("not a room corner: " + dx + ", " + dz);
    }

    /** Centre coordinate of grid cell {@code cell} on either axis. */
    public static int centre(int cell) {
        return CENTRE_OFFSET + CELL_SIZE * cell;
    }

    /** Lowest block coordinate of the room in grid cell {@code cell}; its highest is this + 30. */
    public static int minCorner(int cell) {
        return centre(cell) - HALF;
    }

    /** "NW", "NE", "SE" or "SW": where the clay sits, for the debug output. */
    public String corner() {
        return switch (rotation) {
            case NONE -> "NW";
            case CLOCKWISE_90 -> "NE";
            case CLOCKWISE_180 -> "SE";
            case COUNTERCLOCKWISE_90 -> "SW";
        };
    }

    /** The blue terracotta block itself. */
    public BlockPos clay() {
        return new BlockPos(originX, roofY, originZ);
    }

    // --- blocks ---

    public BlockPos toWorld(int rx, int y, int rz) {
        return new BlockPos(originX + turnX(rx, rz, rotation), y, originZ + turnZ(rx, rz, rotation));
    }

    public BlockPos toWorld(BlockPos rel) {
        return toWorld(rel.getX(), rel.getY(), rel.getZ());
    }

    public BlockPos toRel(BlockPos world) {
        Rotation back = inverse(rotation);
        int dx = world.getX() - originX;
        int dz = world.getZ() - originZ;
        return new BlockPos(turnX(dx, dz, back), world.getY(), turnZ(dx, dz, back));
    }

    // --- continuous positions, turned about the clay block's centre ---

    public Vec3 toWorld(Vec3 rel) {
        double x = rel.x - 0.5;
        double z = rel.z - 0.5;
        return new Vec3(originX + 0.5 + turnX(x, z, rotation), rel.y, originZ + 0.5 + turnZ(x, z, rotation));
    }

    public Vec3 toRel(Vec3 world) {
        Rotation back = inverse(rotation);
        double x = world.x - (originX + 0.5);
        double z = world.z - (originZ + 0.5);
        return new Vec3(turnX(x, z, back) + 0.5, world.y, turnZ(x, z, back) + 0.5);
    }

    /** A box given in room coordinates, in the world. Its corners swap as the room turns; AABB sorts them. */
    public AABB toWorld(AABB rel) {
        Vec3 a = toWorld(new Vec3(rel.minX, rel.minY, rel.minZ));
        Vec3 b = toWorld(new Vec3(rel.maxX, rel.maxY, rel.maxZ));
        return new AABB(a, b);
    }

    public AABB toRel(AABB world) {
        Vec3 a = toRel(new Vec3(world.minX, world.minY, world.minZ));
        Vec3 b = toRel(new Vec3(world.maxX, world.maxY, world.maxZ));
        return new AABB(a, b);
    }

    // --- facings ---

    public Direction toWorld(Direction rel) {
        return rotation.rotate(rel);
    }

    public Direction toRel(Direction world) {
        return inverse(rotation).rotate(world);
    }

    // --- extent ---

    /** The room's own blocks, X/Z of its cell (31 x 31), every height. */
    public AABB bounds() {
        return cellBounds(cellX, cellZ);
    }

    /** The 31 x 31 blocks of grid cell ({@code cellX}, {@code cellZ}), every height. */
    public static AABB cellBounds(int cellX, int cellZ) {
        int x0 = minCorner(cellX);
        int z0 = minCorner(cellZ);
        return new AABB(x0, -64, z0, x0 + 31, 320, z0 + 31);
    }

    /** Whether a world X/Z lies on the room's blocks. */
    public boolean contains(double x, double z) {
        int x0 = minCorner(cellX);
        int z0 = minCorner(cellZ);
        return x >= x0 && x < x0 + 31 && z >= z0 && z < z0 + 31;
    }

    // --- the rotation itself, for ints and doubles alike ---

    /** The rotation that undoes {@code r}. */
    public static Rotation inverse(Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> r;
        };
    }

    // The same turns as BlockPos.rotate: CW90 (x, z) -> (-z, x), 180 (-x, -z), CCW90 (z, -x).

    private static int turnX(int x, int z, Rotation r) {
        return switch (r) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    private static int turnZ(int x, int z, Rotation r) {
        return switch (r) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    private static double turnX(double x, double z, Rotation r) {
        return switch (r) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    private static double turnZ(double x, double z, Rotation r) {
        return switch (r) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }
}
