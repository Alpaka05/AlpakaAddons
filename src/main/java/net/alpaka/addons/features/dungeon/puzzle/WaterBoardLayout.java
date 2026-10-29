package net.alpaka.addons.features.dungeon.puzzle;

import net.minecraft.core.BlockPos;

/**
 * Reading the Water Board room: is this the room, which of the four board layouts it has, which
 * doors are closed, which levers were flipped, and whether water is on the board.
 *
 * All positions are room-relative with absolute Y (puzzles-water.md 1.1): the board is the wall at
 * z 26 between x 6..24 and y 58..81, with the recess for retracted blocks right behind it at z 27;
 * the five doors to the reward chest stand in a row at x 15, y 56..57, z 19 down to 15. Door i (the
 * index the solution tables use) is the one at z 19 - i.
 *
 * Pure: the world is read through {@link View}, so the tests can build a room from a few blocks.
 */
public final class WaterBoardLayout {

    /** What the reader needs to know about a block. */
    public enum Cell {
        /** The chunk is not loaded: nothing can be said, try again later. */
        UNLOADED,
        AIR,
        WATER,
        LEVER,
        COAL,
        GOLD,
        QUARTZ,
        DIAMOND,
        EMERALD,
        TERRACOTTA,
        OTHER
    }

    /** The room's blocks, by room-relative position. */
    @FunctionalInterface
    public interface View {
        Cell at(int rx, int y, int rz);

        default Cell at(BlockPos rel) {
            return at(rel.getX(), rel.getY(), rel.getZ());
        }
    }

    public static final int BOARD_Z = 26;
    public static final int BEHIND_Z = 27;
    public static final int BOARD_MIN_X = 6;
    public static final int BOARD_MAX_X = 24;
    public static final int BOARD_MIN_Y = 58;
    public static final int BOARD_MAX_Y = 81;

    /**
     * The top of the board right below the water's source. The stream runs through here whenever
     * the water is on, before any lever's block can stop it (puzzles-water.md 3.5), and it dries up
     * soon after the water is switched off, so it tells whether water is flowing.
     */
    public static final int ENTRANCE_X = 15;
    public static final int ENTRANCE_MIN_Y = 80;
    public static final int ENTRANCE_MAX_Y = 81;

    public static final int DOOR_X = 15;
    /** Odin and Desco1 read the doors at y 56, Skyblocker at y 57; both work, so a door spans both. */
    public static final int DOOR_LOW_Y = 56;
    public static final int DOOR_HIGH_Y = 57;
    public static final int DOORS = 5;

    /** The box at the entrance (3 wide, 2 high, both planes) whose pair of materials tells the layouts apart. */
    private static final int PAIR_MIN_X = 14;
    private static final int PAIR_MAX_X = 16;
    private static final int PAIR_MIN_Y = 77;
    private static final int PAIR_MAX_Y = 78;

    private WaterBoardLayout() {}

    // --- the room ---

    /** Whether all seven levers stand where the Water Board has them; no other room does. */
    public static boolean isRoom(View view) {
        for (WaterBoardLever lever : WaterBoardLever.values()) {
            if (view.at(lever.lever()) != Cell.LEVER) return false;
        }
        return true;
    }

    // --- the layout ---

    /**
     * The board layout, 1..4, or 0 when unknown. Each layout has its own pair of lever materials
     * right at the entrance (puzzles-water.md 1.2). The materials are collected from both planes of
     * the box, so a lever that was flipped already (moving its block to the other plane) does not
     * hide the layout. Pairs are tried in order 1..4, as Skyblocker and Desco1 do.
     */
    public static int variant(View view) {
        boolean coal = false;
        boolean gold = false;
        boolean quartz = false;
        boolean diamond = false;
        boolean emerald = false;
        boolean terracotta = false;
        for (int x = PAIR_MIN_X; x <= PAIR_MAX_X; x++) {
            for (int y = PAIR_MIN_Y; y <= PAIR_MAX_Y; y++) {
                for (int z = BOARD_Z; z <= BEHIND_Z; z++) {
                    switch (view.at(x, y, z)) {
                        case UNLOADED -> {
                            return 0;
                        }
                        case COAL -> coal = true;
                        case GOLD -> gold = true;
                        case QUARTZ -> quartz = true;
                        case DIAMOND -> diamond = true;
                        case EMERALD -> emerald = true;
                        case TERRACOTTA -> terracotta = true;
                        default -> {
                        }
                    }
                }
            }
        }
        if (gold && terracotta) return 1;
        if (emerald && quartz) return 2;
        if (quartz && diamond) return 3;
        if (gold && quartz) return 4;
        return 0;
    }

    // --- the doors ---

    /** How the doors read. */
    public enum DoorRead {
        /** Each door is clearly open or closed. */
        CLEAR,
        /** A door's two blocks disagree, most likely a piston moving it; read again a little later. */
        AMBIGUOUS,
        /** A door's chunk is not loaded. */
        UNLOADED
    }

    /**
     * The doors: which are closed as a bit mask (bit i = door i closed), and whether the read can
     * be trusted.
     */
    public record Doors(DoorRead read, int closedMask) {

        public boolean clear() {
            return read == DoorRead.CLEAR;
        }

        public int closedCount() {
            return Integer.bitCount(closedMask);
        }

        public boolean allOpen() {
            return clear() && closedMask == 0;
        }

        /** The solution tables' key: the closed doors' indices in ascending order, e.g. "024". */
        public String key() {
            return doorKey(closedMask);
        }
    }

    /**
     * Reads the five doors. A door is closed when both its blocks hold something and open when both
     * are air. With {@code lowOnly} only the lower block counts, the way Odin reads it, for when the
     * upper one keeps disagreeing.
     */
    public static Doors doors(View view, boolean lowOnly) {
        int closed = 0;
        boolean ambiguous = false;
        for (int i = 0; i < DOORS; i++) {
            int z = doorZ(i);
            Cell low = view.at(DOOR_X, DOOR_LOW_Y, z);
            Cell high = lowOnly ? low : view.at(DOOR_X, DOOR_HIGH_Y, z);
            if (low == Cell.UNLOADED || high == Cell.UNLOADED) return new Doors(DoorRead.UNLOADED, 0);
            boolean lowShut = low != Cell.AIR;
            boolean highShut = high != Cell.AIR;
            if (lowShut != highShut) ambiguous = true;
            else if (lowShut) closed |= 1 << i;
        }
        return new Doors(ambiguous ? DoorRead.AMBIGUOUS : DoorRead.CLEAR, ambiguous ? 0 : closed);
    }

    /** The z of door {@code index} (0..4). */
    public static int doorZ(int index) {
        return 19 - index;
    }

    /** The closed doors' indices as ascending digits, e.g. "013". */
    public static String doorKey(int closedMask) {
        StringBuilder key = new StringBuilder(3);
        for (int i = 0; i < DOORS; i++) {
            if ((closedMask & (1 << i)) != 0) key.append((char) ('0' + i));
        }
        return key.toString();
    }

    // --- the levers' state ---

    /**
     * Whether {@code lever} has been flipped an odd number of times since the room was built, read
     * from where one of its blocks is: TRUE when the block left its starting plane for the other
     * one, FALSE when it is where it started, null when neither holds it (a block moving, a chunk not
     * loaded) or the lever moves nothing in this layout.
     */
    public static Boolean flipped(View view, WaterBoardLever lever, int variant) {
        BlockPos start = lever.representative(variant);
        if (start == null) return null;
        Cell there = view.at(start);
        if (there == lever.material()) return Boolean.FALSE;
        int otherZ = start.getZ() == BOARD_Z ? BEHIND_Z : BOARD_Z;
        Cell other = view.at(start.getX(), start.getY(), otherZ);
        if (other == lever.material()) return Boolean.TRUE;
        return null;
    }

    // --- water ---

    /** Whether any water is on the board; null while part of it is not loaded. */
    public static Boolean waterOnBoard(View view) {
        for (int y = BOARD_MAX_Y; y >= BOARD_MIN_Y; y--) {
            for (int x = BOARD_MIN_X; x <= BOARD_MAX_X; x++) {
                Cell cell = view.at(x, y, BOARD_Z);
                if (cell == Cell.UNLOADED) return null;
                if (cell == Cell.WATER) return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /** Whether water is falling in at the entrance, i.e. the water is on; null while not loaded. */
    public static Boolean waterAtEntrance(View view) {
        boolean wet = false;
        for (int y = ENTRANCE_MIN_Y; y <= ENTRANCE_MAX_Y; y++) {
            Cell cell = view.at(ENTRANCE_X, y, BOARD_Z);
            if (cell == Cell.UNLOADED) return null;
            if (cell == Cell.WATER) wet = true;
        }
        return wet;
    }
}
