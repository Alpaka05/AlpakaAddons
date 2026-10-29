package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.CreeperBeamsLayout.Kind;
import net.alpaka.addons.features.dungeon.puzzle.CreeperBeamsLayout.Pair;
import net.alpaka.addons.features.dungeon.puzzle.CreeperBeamsLayout.State;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creeper Beams pairing (puzzles-entities.md 3.3, 3.5 and the fixtures in 3.8). */
class CreeperBeamsLayoutTest {

    /** The room of the fixtures: cell (2, 4), centre column at (-121, -57). */
    private static final int CELL_X = 2;
    private static final int CELL_Z = 4;

    /** A made-up world: lanterns where put, anything else elsewhere. */
    private static final class World implements CreeperBeamsLayout.BlockView {
        final Map<BlockPos, Kind> blocks = new HashMap<>();

        World put(Kind kind, int... xyz) {
            for (int i = 0; i < xyz.length; i += 3) blocks.put(new BlockPos(xyz[i], xyz[i + 1], xyz[i + 2]), kind);
            return this;
        }

        @Override
        public Kind kindAt(BlockPos pos) {
            return blocks.getOrDefault(pos, Kind.OTHER);
        }
    }

    /** The deduplicated pair with this index (u0..u10 in the spec). */
    private static BlockPos[] u(int i) {
        return CreeperBeamsPairs.PAIRS.get(i);
    }

    /** The fixture's lanterns for pairs u0, u1, u4 and u9 with the room turned by k = 0. */
    private static World unturned() {
        return new World().put(Kind.LANTERN,
                -121, 74, -57, -121, 84, -59,
                -121, 78, -69, -121, 76, -45,
                -109, 78, -58, -134, 75, -56,
                -118, 81, -51, -127, 69, -69);
    }

    /** The same pairs with the room turned by k = 1 ((dx, dz) -> (-dz, dx)). */
    private static World turned() {
        return new World().put(Kind.LANTERN,
                -121, 74, -57, -119, 84, -57,
                -109, 78, -57, -133, 76, -57,
                -120, 78, -45, -122, 75, -70,
                -127, 81, -54, -109, 69, -63);
    }

    private static Set<Set<BlockPos>> asSets(List<Pair> pairs) {
        Set<Set<BlockPos>> out = new HashSet<>();
        for (Pair p : pairs) out.add(Set.of(p.a(), p.b()));
        return out;
    }

    private static Set<Set<BlockPos>> tablePairsIn(RoomFrame frame, int... indices) {
        Set<Set<BlockPos>> out = new HashSet<>();
        for (int i : indices) out.add(Set.of(frame.toWorld(u(i)[0]), frame.toWorld(u(i)[1])));
        return out;
    }

    @Test
    void theTableHasElevenPairsAndNoLanternTwice() {
        assertEquals(11, CreeperBeamsPairs.PAIRS.size());
        Set<BlockPos> ends = new HashSet<>();
        for (BlockPos[] pair : CreeperBeamsPairs.PAIRS) {
            ends.add(pair[0]);
            ends.add(pair[1]);
        }
        assertEquals(22, ends.size());
        assertEquals(new BlockPos(15, CreeperBeamsLayout.BASE_Y, 15), u(0)[0], "the first pair starts under the creeper");
    }

    @Test
    void theBaseIsTheRoomsCentreColumn() {
        assertEquals(new BlockPos(-121, 74, -57), CreeperBeamsLayout.base(CELL_X, CELL_Z));
        for (Rotation r : Rotation.values()) {
            assertEquals(new BlockPos(-121, 74, -57), CreeperBeamsLayout.frame(CELL_X, CELL_Z, r).toWorld(new BlockPos(15, 74, 15)), r.name());
        }
    }

    @Test
    void theLanternsTellAnUnturnedRoom() {
        World world = unturned();
        assertEquals(Rotation.NONE, CreeperBeamsLayout.findRotation(world, CELL_X, CELL_Z));
        RoomFrame frame = CreeperBeamsLayout.frame(CELL_X, CELL_Z, Rotation.NONE);
        List<Pair> pairs = CreeperBeamsLayout.knownPairs(world, frame);
        assertEquals(tablePairsIn(frame, 0, 1, 4, 9), asSets(pairs));
        for (Pair pair : pairs) assertEquals(State.TODO, pair.state(world));
    }

    @Test
    void theLanternsTellATurnedRoom() {
        World world = turned();
        assertEquals(Rotation.CLOCKWISE_90, CreeperBeamsLayout.findRotation(world, CELL_X, CELL_Z));
        RoomFrame frame = CreeperBeamsLayout.frame(CELL_X, CELL_Z, Rotation.CLOCKWISE_90);
        assertEquals(new BlockPos(-119, 84, -57), frame.toWorld(new BlockPos(15, 84, 13)), "rooms.md cross-check value");
        assertEquals(tablePairsIn(frame, 0, 1, 4, 9), asSets(CreeperBeamsLayout.knownPairs(world, frame)));
        // In the wrong rotation only the lantern under the creeper lines up.
        assertEquals(1, CreeperBeamsLayout.score(world, CreeperBeamsLayout.frame(CELL_X, CELL_Z, Rotation.NONE)));
        assertTrue(CreeperBeamsLayout.knownPairs(world, CreeperBeamsLayout.frame(CELL_X, CELL_Z, Rotation.NONE)).isEmpty());
    }

    @Test
    void turnedByAQuarterTheKnownLanternsShareOnlyTheBase() {
        for (Rotation placed : Rotation.values()) {
            World world = new World();
            RoomFrame frame = CreeperBeamsLayout.frame(CELL_X, CELL_Z, placed);
            for (BlockPos[] pair : CreeperBeamsPairs.PAIRS) {
                world.blocks.put(frame.toWorld(pair[0]), Kind.LANTERN);
                world.blocks.put(frame.toWorld(pair[1]), Kind.LANTERN);
            }
            assertEquals(placed, CreeperBeamsLayout.findRotation(world, CELL_X, CELL_Z));
            for (Rotation other : Rotation.values()) {
                int score = CreeperBeamsLayout.score(world, CreeperBeamsLayout.frame(CELL_X, CELL_Z, other));
                assertEquals(other == placed ? 22 : 1, score, placed + " read as " + other);
            }
        }
    }

    @Test
    void litLanternsMakeAPairHalfOrFullyDone() {
        RoomFrame frame = CreeperBeamsLayout.frame(CELL_X, CELL_Z, Rotation.CLOCKWISE_90);
        World both = turned().put(Kind.PRISMARINE, -120, 78, -45, -122, 75, -70);
        // Prismarine still counts as a lantern of the room, so the rotation and the pairs hold.
        assertEquals(Rotation.CLOCKWISE_90, CreeperBeamsLayout.findRotation(both, CELL_X, CELL_Z));
        Set<BlockPos> u4 = Set.of(frame.toWorld(u(4)[0]), frame.toWorld(u(4)[1]));
        for (Pair pair : CreeperBeamsLayout.knownPairs(both, frame)) {
            boolean isU4 = Set.of(pair.a(), pair.b()).equals(u4);
            assertEquals(isU4 ? State.DONE : State.TODO, pair.state(both));
        }

        World one = turned().put(Kind.PRISMARINE, -120, 78, -45);
        for (Pair pair : CreeperBeamsLayout.knownPairs(one, frame)) {
            boolean isU4 = Set.of(pair.a(), pair.b()).equals(u4);
            assertEquals(isU4 ? State.HALF : State.TODO, pair.state(one));
        }
    }

    @Test
    void aPairWithAGoneLanternHasNoState() {
        World world = turned();
        Pair pair = new Pair(new BlockPos(-120, 78, -45), new BlockPos(-122, 75, -70));
        world.blocks.remove(new BlockPos(-122, 75, -70));
        assertNull(pair.state(world));
    }

    @Test
    void tooFewLanternsSettleNoRotation() {
        World three = new World().put(Kind.LANTERN, -121, 74, -57, -121, 84, -59, -121, 78, -69);
        assertNull(CreeperBeamsLayout.findRotation(three, CELL_X, CELL_Z));
        assertNull(CreeperBeamsLayout.findRotation(new World(), CELL_X, CELL_Z));
    }

    @Test
    void lineDistancesFromTheCreeper() {
        // The spec's distances were measured from the point closest to all the table's lines
        // (15.51, 77.23, 15.57), not from the rounded (15.5, 77.25, 15.5) its text names; from there
        // they come out exactly as listed.
        Vec3 creeper = new Vec3(15.51, 77.23, 15.57);
        Map<Integer, Double> far = Map.of(0, 0.60, 2, 1.19, 7, 2.03);
        for (int i : new int[]{0, 1, 2, 3, 4, 5, 7, 8, 9, 10}) {
            double d = CreeperBeamsLayout.lineDistance(creeper, Vec3.atCenterOf(u(i)[0]), Vec3.atCenterOf(u(i)[1]));
            if (far.containsKey(i)) assertEquals(far.get(i), d, 0.01, "u" + i);
            else assertTrue(d <= 0.37, "u" + i + " passes close: " + d);
        }
    }

    @Test
    void geometryPairsTheRightLanterns() {
        assertEquals(new Vec3(-120.5, 77.25, -56.5), CreeperBeamsLayout.crossing(CELL_X, CELL_Z));
        // The same point in room coordinates, where the table's pairs are written.
        Vec3 creeper = new Vec3(15.5, CreeperBeamsLayout.CROSSING_Y, 15.5);
        List<BlockPos> lanterns = new ArrayList<>();
        for (int i : new int[]{1, 3, 4, 9}) {
            lanterns.add(u(i)[0]);
            lanterns.add(u(i)[1]);
        }
        List<Pair> pairs = CreeperBeamsLayout.fillUp(lanterns, creeper, CreeperBeamsLayout.BEAMS);
        Set<Set<BlockPos>> expected = new HashSet<>();
        for (int i : new int[]{1, 3, 4, 9}) expected.add(Set.of(u(i)[0], u(i)[1]));
        assertEquals(expected, asSets(pairs));
    }

    @Test
    void geometryWorksInATurnedRoom() {
        RoomFrame frame = CreeperBeamsLayout.frame(CELL_X, CELL_Z, Rotation.COUNTERCLOCKWISE_90);
        List<BlockPos> lanterns = new ArrayList<>();
        for (int i : new int[]{1, 3, 4, 9}) {
            lanterns.add(frame.toWorld(u(i)[0]));
            lanterns.add(frame.toWorld(u(i)[1]));
        }
        List<Pair> pairs = CreeperBeamsLayout.fillUp(lanterns, CreeperBeamsLayout.crossing(CELL_X, CELL_Z), CreeperBeamsLayout.BEAMS);
        assertEquals(tablePairsIn(frame, 1, 3, 4, 9), asSets(pairs));
    }

    @Test
    void geometryStopsAtTheLimitAndFarLines() {
        Vec3 creeper = new Vec3(15.5, 77.25, 15.5);
        List<BlockPos> lanterns = List.of(u(1)[0], u(1)[1], u(3)[0], u(3)[1]);
        assertEquals(1, CreeperBeamsLayout.fillUp(lanterns, creeper, 1).size());
        // u7's line passes two blocks from the creeper: too far to pair by geometry.
        assertTrue(CreeperBeamsLayout.fillUp(List.of(u(7)[0], u(7)[1]), creeper, 4).isEmpty());
    }
}
