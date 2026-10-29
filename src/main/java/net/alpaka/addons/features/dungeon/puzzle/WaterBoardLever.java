package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.WaterBoardLayout.Cell;
import net.minecraft.core.BlockPos;

/**
 * The seven levers of the Water Board room, in the order every solution table and both reference
 * mods use for ties: the six board levers, then the water.
 *
 * Positions are room-relative in the {@link net.alpaka.addons.features.dungeon.room.RoomFrame}
 * frame with absolute Y (puzzles-water.md 1.1). A board lever moves every block of its material on
 * the board between the board plane (z 26) and the recess right behind it (z 27), so whether it was
 * flipped an odd number of times shows in one of those blocks: {@link #representative(int)} is where
 * one such block sits in each layout while nothing has been flipped (puzzles-water.md 1.2). Layout 3
 * has no coal or terracotta blocks at all; its solutions never use those two levers.
 */
public enum WaterBoardLever {
    COAL("coal", "Coal", 20, 61, 10, Cell.COAL,
            pos(15, 76, 26), pos(17, 77, 27), null, pos(20, 77, 26)),
    GOLD("gold", "Gold", 20, 61, 15, Cell.GOLD,
            pos(16, 77, 26), pos(18, 76, 26), pos(11, 77, 27), pos(16, 78, 26)),
    QUARTZ("quartz", "Quartz", 20, 61, 20, Cell.QUARTZ,
            pos(16, 74, 27), pos(14, 78, 26), pos(16, 78, 26), pos(14, 78, 27)),
    DIAMOND("diamond", "Diamond", 10, 61, 20, Cell.DIAMOND,
            pos(15, 73, 27), pos(13, 77, 26), pos(14, 78, 27), pos(12, 74, 27)),
    EMERALD("emerald", "Emerald", 10, 61, 15, Cell.EMERALD,
            pos(14, 68, 27), pos(16, 78, 27), pos(9, 78, 26), pos(16, 74, 26)),
    TERRACOTTA("terracotta", "Terracotta", 10, 61, 10, Cell.TERRACOTTA,
            pos(14, 77, 27), pos(15, 75, 27), null, pos(11, 73, 27)),
    /** Turns the water on and off; at the back of the room, away from the board. */
    WATER("water", "Water", 15, 60, 5, null, null, null, null, null);

    private final String key;
    private final String displayName;
    private final BlockPos lever;
    private final Cell material;
    /** By layout 1..4 at index 0..3; null where the layout has none of this material. */
    private final BlockPos[] representatives;

    WaterBoardLever(String key, String displayName, int x, int y, int z, Cell material, BlockPos... representatives) {
        this.key = key;
        this.displayName = displayName;
        this.lever = new BlockPos(x, y, z);
        this.material = material;
        this.representatives = representatives;
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** The lever's name in the solution data. */
    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** The lever block itself, room-relative. */
    public BlockPos lever() {
        return lever;
    }

    public boolean isWater() {
        return this == WATER;
    }

    /** The block this lever moves on the board; null for the water lever. */
    public Cell material() {
        return material;
    }

    /**
     * Where one block of this lever's material sits in layout {@code variant} (1..4) before anyone
     * flips anything, room-relative; null for the water lever, for coal and terracotta in layout 3,
     * and for a layout that does not exist.
     */
    public BlockPos representative(int variant) {
        if (variant < 1 || variant > representatives.length) return null;
        return representatives[variant - 1];
    }

    /** The lever named {@code key} in the solution data, or null. */
    public static WaterBoardLever byKey(String key) {
        for (WaterBoardLever lever : values()) {
            if (lever.key.equals(key)) return lever;
        }
        return null;
    }

    /** The lever at a room-relative position, or null. */
    public static WaterBoardLever at(BlockPos rel) {
        for (WaterBoardLever lever : values()) {
            if (lever.lever.equals(rel)) return lever;
        }
        return null;
    }
}
