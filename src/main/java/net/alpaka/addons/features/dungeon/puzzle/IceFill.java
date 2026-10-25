package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.puzzle.IceFillBoard.Floor;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.features.dungeon.room.PuzzleTab;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ice Fill: reads each of the three ice floors and draws the walk that covers every ice block
 * exactly once, from the floor's entrance to its exit.
 *
 * Each floor is read from the blocks every tick (puzzles-floor.md 2.3): a cell is part of the walk
 * when there is air at walking height and ice below, a block at walking height is in the way, and
 * a cell with air on both is a chunk not here yet, which leaves that floor alone for the tick. The
 * room counts as Ice Fill when all three floors have ice where their walk starts.
 *
 * The walk is searched with as few turns as it can find ({@link IceFillSearch}): a fast run with
 * many turns is how the server sees a diagonal step and resets the floor. Walked cells turn to
 * packed ice, so only the part still ahead is drawn, from the cell the walker is on, with the next
 * cell marked. Someone stepping off the walk without the floor resetting gets a new walk from where
 * they are, if one can still finish the floor; a reset brings the whole walk back.
 *
 * Draws, depth-tested and lifted a little above the ice so the ice itself does not hide it: the
 * lowest floor not done yet in the Solution color, the ones above it in the Next color.
 */
public final class IceFill extends PuzzleSolver {

    /** How long a room that did not look like Ice Fill is left alone before it is read again. */
    private static final int RECHECK_TICKS = 10;
    /** Lifts the line off the ice so the ice does not hide it. */
    private static final double LIFT = 0.05;
    /** The next cell's mark: inset from the cell's edges, and as thin as the lift. */
    private static final double MARK_INSET = 0.18;

    private RoomFrame frame;
    private boolean inRoom;
    private RoomFrame notHere;
    private long notHereUntil;
    private long ticks;
    private final FloorState[] floors = new FloorState[IceFillBoard.FLOORS.size()];

    /** One floor as last read, its plan, and what is drawn for it in world coordinates. */
    private static final class FloorState {
        final Floor floor;
        boolean read;
        long free;
        long packed;
        IceFillPlan plan;
        /** The free and packed cells no plan could be found for, so the search is not redone every tick. */
        long failedFree = -1L;
        long failedPacked = -1L;
        boolean reported;
        List<Vec3> line = List.of();
        AABB next;

        FloorState(Floor floor) {
            this.floor = floor;
        }

        boolean done() {
            return read && free != 0 && (free & ~packed) == 0;
        }
    }

    public IceFill() {
        clearFloors();
    }

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.ICE_FILL;
    }

    @Override
    public void tick(SolverContext ctx) {
        ticks++;
        inRoom = false;
        RoomFrame f = roomOf(ctx);
        if (f == null || listedElsewhere(puzzle())) return;
        ClientLevel level = ctx.level();
        if (!f.equals(frame)) {
            if (f.equals(notHere) && ticks < notHereUntil) return;
            if (!looksLikeIceFill(level, f)) {
                notHere = f;
                notHereUntil = ticks + RECHECK_TICKS;
                return;
            }
            reset();
            frame = f;
        }
        inRoom = true;

        BlockPos feet = frame.toRel(ctx.player().blockPosition());
        for (FloorState state : floors) {
            int walker = feet.getY() == state.floor.walkY() ? state.floor.cellAt(feet.getX(), feet.getZ()) : -1;
            update(level, state, walker);
        }
    }

    private void update(ClientLevel level, FloorState state, int walker) {
        Floor floor = state.floor;
        long free = 0L;
        long packed = 0L;
        for (int cell = 0; cell < floor.cells(); cell++) {
            BlockPos walk = frame.toWorld(floor.relX(cell), floor.walkY(), floor.relZ(cell));
            if (!level.hasChunk(walk.getX() >> 4, walk.getZ() >> 4)) return;
            if (!level.getBlockState(walk).isAir()) continue;
            BlockState ice = level.getBlockState(walk.below());
            // Air on both: this part of the floor has not arrived yet.
            if (ice.isAir()) return;
            if (ice.is(Blocks.PACKED_ICE)) {
                free |= 1L << cell;
                packed |= 1L << cell;
            } else if (ice.is(BlockTags.ICE)) {
                free |= 1L << cell;
            }
        }
        if (state.read && free == state.free && packed == state.packed) return;
        state.read = true;
        state.free = free;
        state.packed = packed;

        if (state.done()) {
            state.plan = null;
        } else if (state.plan == null && free == state.failedFree && packed == state.failedPacked) {
            return;
        } else {
            state.plan = IceFillPlan.update(state.plan, floor, free, packed, walker, IceFillSearch.DEFAULT_BUDGET);
            if (state.plan == null) {
                state.failedFree = free;
                state.failedPacked = packed;
                // An untouched floor without a walk is a layout nobody has seen; worth one line.
                if (packed == 0 && !state.reported) {
                    state.reported = true;
                    AlpakaAddons.LOGGER.info("Ice Fill: no walk found for floor {} (free cells {})",
                            floor.number(), Long.toBinaryString(free));
                }
            }
        }
        buildMarks(state);
    }

    /** All three floors have ice under the cell their walk starts on. */
    private static boolean looksLikeIceFill(ClientLevel level, RoomFrame f) {
        for (Floor floor : IceFillBoard.FLOORS) {
            BlockPos start = f.toWorld(floor.relX(floor.start()), floor.walkY(), floor.relZ(floor.start()));
            if (!level.hasChunk(start.getX() >> 4, start.getZ() >> 4)) return false;
            if (!level.getBlockState(start).isAir() || !level.getBlockState(start.below()).is(BlockTags.ICE)) return false;
        }
        return true;
    }

    // --- what to draw ---

    private void buildMarks(FloorState state) {
        state.line = List.of();
        state.next = null;
        if (state.plan == null) return;
        int[] ahead = state.plan.ahead(state.packed);
        if (ahead == null || ahead.length == 0) return;
        Floor floor = state.floor;
        double y = floor.walkY() + LIFT;

        List<Vec3> line = new ArrayList<>();
        for (int cell : IceFillBoard.corners(ahead)) {
            line.add(frame.toWorld(new Vec3(floor.relX(cell) + 0.5, y, floor.relZ(cell) + 0.5)));
        }
        // On to the far edge of the floor, where the walk leaves it.
        line.add(frame.toWorld(new Vec3(floor.relX(floor.end()) + 0.5, y, floor.exitZ())));
        state.line = List.copyOf(line);

        // Before the first step the next cell is the start itself; after it, the one after the walker.
        boolean started = state.plan.position(state.packed) >= 0;
        int nextCell = started ? (ahead.length > 1 ? ahead[1] : -1) : ahead[0];
        if (nextCell >= 0) {
            double x = floor.relX(nextCell);
            double z = floor.relZ(nextCell);
            state.next = frame.toWorld(new AABB(x + MARK_INSET, floor.walkY() + 0.01, z + MARK_INSET,
                    x + 1 - MARK_INSET, y, z + 1 - MARK_INSET));
        }
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!inRoom) return;
        boolean first = true;
        for (FloorState state : floors) {
            if (state.done() || !state.read) continue;
            // The floor being walked now; those above it are what comes next.
            int color = first ? SolverColors.solution() : SolverColors.next();
            first = false;
            if (state.line.size() >= 2) draw.path(state.line, color);
            if (state.next != null) draw.box(state.next, color);
        }
    }

    @Override
    public void reset() {
        frame = null;
        inRoom = false;
        notHere = null;
        notHereUntil = 0L;
        clearFloors();
    }

    private void clearFloors() {
        for (int i = 0; i < floors.length; i++) floors[i] = new FloorState(IceFillBoard.FLOORS.get(i));
    }

    @Override
    public boolean claimsRoom() {
        return inRoom;
    }

    @Override
    public String debugState() {
        if (frame == null) return null;
        StringBuilder out = new StringBuilder();
        for (FloorState state : floors) {
            if (!out.isEmpty()) out.append("; ");
            out.append("floor ").append(state.floor.number()).append(": ");
            if (!state.read) out.append("not read");
            else if (state.done()) out.append("done");
            else if (state.plan == null) out.append("no walk");
            else out.append(Long.bitCount(state.packed)).append('/').append(Long.bitCount(state.free))
                        .append(" walked, ").append(state.plan.turns()).append(" turns");
        }
        return out.toString();
    }

    /**
     * The room the player is in. DungeonRooms stops counting a room once the player is within ten
     * blocks of its roof, so that walking on roofs does not count; a puzzle that climbs (Ice Fill's
     * top floor is walked at Y 72) could get that close inside the room. So the room already
     * recognised here also counts while the player is anywhere under its roof.
     */
    private RoomFrame roomOf(SolverContext ctx) {
        if (ctx.frame() != null) return ctx.frame();
        RoomFrame cell = DungeonRooms.cellFrame();
        return cell != null && cell.equals(frame) && ctx.player().getBlockY() < cell.roofY() ? cell : null;
    }

    /**
     * Whether the tab list names every puzzle of this run and this one is not among them; then this
     * room cannot be it and nothing needs reading.
     */
    private static boolean listedElsewhere(DungeonPuzzle puzzle) {
        PuzzleTab.Parsed tab = PuzzleTab.current();
        return tab.count() > 0 && tab.undiscovered() == 0 && tab.statusOf(puzzle) == null
                && tab.entries().size() >= tab.count();
    }
}
