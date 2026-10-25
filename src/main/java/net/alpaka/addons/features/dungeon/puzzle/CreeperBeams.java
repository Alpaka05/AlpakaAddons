package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.puzzle.CreeperBeamsLayout.Kind;
import net.alpaka.addons.features.dungeon.puzzle.CreeperBeamsLayout.Pair;
import net.alpaka.addons.features.dungeon.puzzle.CreeperBeamsLayout.State;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Creeper Beams: marks which sea lanterns to shoot together, so that each beam passes through the
 * creeper in the middle of the room. Both lanterns of a pair get a box and a line between them, in
 * a color of their own, and the same number, which tells the pairs apart without the colors. A pair
 * whose beam is done (both lanterns turned to prismarine) is no longer shown; one with a single lit
 * lantern keeps only an outline there.
 *
 * The room is the one whose centre column holds a lantern at Y 74 with a creeper standing on it.
 * The pairs come from {@link CreeperBeamsLayout}: the known pairs whose lanterns are there, in the
 * rotation the room frame gives (or that the lanterns themselves give), and, should those be fewer
 * than four, pairs by geometry from the rest. Geometric pairs are logged once with their room
 * coordinates, so the table can be extended with them.
 *
 * Spec: puzzles-entities.md section 3. The pair table is adapted from Odin (BSD-3, see
 * {@link CreeperBeamsPairs} and CREDITS.md).
 */
public final class CreeperBeams extends PuzzleSolver {

    /** One color per pair, told apart easily; the alpha comes from the Solution Color. */
    private static final int[] PALETTE = {
            0x3FA8FF, // blue
            0xFF9A3C, // orange
            0xFF55FF, // magenta
            0x7CFF4D, // lime
            0x4DFFF0, // cyan
            0xFFE14D, // yellow
            0x9B6BFF, // violet
            0xF2F2F2, // white
    };
    /** How far a number sits from its lantern's centre, towards the other lantern: out of the block. */
    private static final double LABEL_OFFSET = 0.9;

    /** A pair to draw this tick, with its color and number. */
    private record Shown(Pair pair, boolean aLit, boolean bLit, int color, int number) {}

    private final List<Shown> shown = new ArrayList<>();
    /** Each pair keeps the color it got first, however many pairs come and go. */
    private final Map<Pair, Integer> numbers = new HashMap<>();
    private long cell = Long.MIN_VALUE;
    private Rotation rotation = null;
    private boolean rotationFromFrame = false;
    /** The lanterns were looked at for the rotation and did not settle it; geometry has to do. */
    private boolean rotationUnknown = false;
    private List<Pair> known = List.of();
    /** Pairs by geometry; null until worked out, which happens once per room and only when needed. */
    private List<Pair> geometric = null;
    private boolean claims = false;

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.CREEPER_BEAMS;
    }

    @Override
    public void tick(SolverContext ctx) {
        shown.clear();
        claims = false;
        int cellX = DungeonRooms.cellX();
        int cellZ = DungeonRooms.cellZ();
        if (cellX < 0 || DungeonRooms.onRoof()) return;
        long key = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        if (key != cell) forgetRoom(key);

        ClientLevel level = ctx.level();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        CreeperBeamsLayout.BlockView view = pos -> kindOf(level.getBlockState(probe.set(pos)));
        BlockPos base = CreeperBeamsLayout.base(cellX, cellZ);
        // One block read in every other room before anything else.
        if (!view.kindAt(base).lantern()) return;
        Creeper creeper = creeperOn(level, base);
        // No creeper: not this room, or it exploded and the puzzle is done.
        if (creeper == null || !roomLoaded(level, base)) return;
        claims = true;

        if (rotation == null && !rotationUnknown) {
            RoomFrame frame = ctx.frame();
            if (frame != null) {
                rotation = frame.rotation();
                rotationFromFrame = true;
            } else {
                // The whole room is loaded, so what the lanterns say now they will keep saying.
                rotation = CreeperBeamsLayout.findRotation(view, cellX, cellZ);
                rotationUnknown = rotation == null;
            }
        }
        known = rotation == null ? List.of() : CreeperBeamsLayout.knownPairs(view, CreeperBeamsLayout.frame(cellX, cellZ, rotation));
        if (geometric == null && known.size() < CreeperBeamsLayout.BEAMS) {
            geometric = pairByGeometry(view, base, cellX, cellZ);
        }

        int alpha = SolverColors.solution() >>> 24;
        List<Pair> all = new ArrayList<>(known);
        if (geometric != null) all.addAll(geometric);
        for (Pair pair : all) {
            State state = pair.state(view);
            if (state == null || state == State.DONE) continue;
            int number = numbers.computeIfAbsent(pair, p -> numbers.size());
            int color = SolverColors.withAlpha(PALETTE[number % PALETTE.length], alpha);
            shown.add(new Shown(pair, view.kindAt(pair.a()) == Kind.PRISMARINE, view.kindAt(pair.b()) == Kind.PRISMARINE,
                    color, number + 1));
        }
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        for (Shown s : shown) {
            Vec3 a = Vec3.atCenterOf(s.pair().a());
            Vec3 b = Vec3.atCenterOf(s.pair().b());
            lantern(draw, s.pair().a(), s.aLit(), s.color());
            lantern(draw, s.pair().b(), s.bLit(), s.color());
            draw.line(a, b, s.color());
            String number = Integer.toString(s.number());
            draw.label(a.add(b.subtract(a).normalize().scale(LABEL_OFFSET)), number, s.color(), 1.0f);
            draw.label(b.add(a.subtract(b).normalize().scale(LABEL_OFFSET)), number, s.color(), 1.0f);
        }
    }

    /** A lantern still to shoot gets the full box; a lit one only its outline. */
    private static void lantern(SolverDraw draw, BlockPos pos, boolean lit, int color) {
        if (lit) draw.outline(new AABB(pos).inflate(SolverDraw.BLOCK_INFLATE), color);
        else draw.block(pos, color);
    }

    @Override
    public void reset() {
        forgetRoom(Long.MIN_VALUE);
    }

    private void forgetRoom(long key) {
        cell = key;
        shown.clear();
        numbers.clear();
        rotation = null;
        rotationFromFrame = false;
        rotationUnknown = false;
        known = List.of();
        geometric = null;
        claims = false;
    }

    @Override
    public boolean claimsRoom() {
        return claims;
    }

    @Override
    public String debugState() {
        if (!claims) return null;
        String turn = rotation == null ? "rotation unknown" : "rotation " + rotation + (rotationFromFrame ? " (frame)" : " (lanterns)");
        return String.format(Locale.ROOT, "%s, %d known + %d geometric pairs, %d to do", turn, known.size(),
                geometric == null ? 0 : geometric.size(), shown.size());
    }

    // --- world ---

    private static Kind kindOf(BlockState state) {
        if (state.is(Blocks.SEA_LANTERN)) return Kind.LANTERN;
        if (state.is(Blocks.PRISMARINE)) return Kind.PRISMARINE;
        return Kind.OTHER;
    }

    /** The visible creeper standing on the lantern, or null. */
    private static Creeper creeperOn(ClientLevel level, BlockPos base) {
        AABB above = new AABB(base.getX() - 1.5, base.getY(), base.getZ() - 1.5,
                base.getX() + 2.5, base.getY() + 6, base.getZ() + 2.5);
        List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, above,
                c -> !c.isRemoved() && c.isAlive() && !c.isInvisible());
        return creepers.isEmpty() ? null : creepers.getFirst();
    }

    /** Whether every chunk of the room has arrived; a missing one reads as air and would hide lanterns. */
    private static boolean roomLoaded(ClientLevel level, BlockPos centre) {
        int h = RoomFrame.HALF;
        for (int cx = (centre.getX() - h) >> 4; cx <= (centre.getX() + h) >> 4; cx++) {
            for (int cz = (centre.getZ() - h) >> 4; cz <= (centre.getZ() + h) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    /** The lanterns the known pairs left over, paired by geometry; logged, so the table can learn them. */
    private List<Pair> pairByGeometry(CreeperBeamsLayout.BlockView view, BlockPos base, int cellX, int cellZ) {
        Set<BlockPos> taken = new HashSet<>();
        taken.add(base);
        for (Pair pair : known) {
            taken.add(pair.a());
            taken.add(pair.b());
        }
        List<BlockPos> rest = new ArrayList<>();
        int h = RoomFrame.HALF;
        for (int x = base.getX() - h; x <= base.getX() + h; x++) {
            for (int z = base.getZ() - h; z <= base.getZ() + h; z++) {
                for (int y = CreeperBeamsLayout.LOW_Y; y <= CreeperBeamsLayout.HIGH_Y; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!taken.contains(pos) && view.kindAt(pos).lantern()) rest.add(pos);
                }
            }
        }
        List<Pair> pairs = CreeperBeamsLayout.fillUp(rest, CreeperBeamsLayout.crossing(cellX, cellZ),
                CreeperBeamsLayout.BEAMS - known.size());
        if (!pairs.isEmpty() || !rest.isEmpty()) {
            RoomFrame frame = rotation == null ? null : CreeperBeamsLayout.frame(cellX, cellZ, rotation);
            AlpakaAddons.LOGGER.info("Creeper Beams: {} known pairs; paired by geometry {} (room coordinates: {}) "
                    + "from the other lanterns {}", known.size(), pairs, relative(frame, pairs), rest);
        }
        return pairs;
    }

    private static String relative(RoomFrame frame, List<Pair> pairs) {
        if (frame == null) return "rotation unknown";
        StringBuilder out = new StringBuilder();
        for (Pair pair : pairs) {
            BlockPos a = frame.toRel(pair.a());
            BlockPos b = frame.toRel(pair.b());
            out.append(String.format(Locale.ROOT, "[%d, %d, %d, %d, %d, %d] ",
                    a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
        }
        return out.toString().trim();
    }
}
