package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.puzzle.IcePathSearch.Route;
import net.alpaka.addons.features.dungeon.puzzle.IcePathSearch.Slide;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.features.dungeon.room.PuzzleTab;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Ice Path (the silverfish): reads the 17 x 17 board and the silverfish's position, searches the
 * fewest slides that bring it to the gate, and marks the direction to hit it next.
 *
 * The room counts as Ice Path while a visible silverfish is on the board and the board's floor is
 * ice (puzzles-floor.md 3.2). Blocks standing on the board are read every tick; anything that is not
 * air stops a slide.
 *
 * The route is searched again only once the silverfish has come to rest: while it slides, its cell
 * changes every tick, and a search from the middle of a slide would show a route it cannot take. If
 * it stops where the route said, the route simply moves on by one hit. Wherever else it stops (a
 * teammate hit it, a block appeared), it gets a fresh search from there.
 *
 * Draws, depth-tested and lifted a little above the ice: the first slide in the Solution color with
 * an arrow head, a mark on the cell to hit it from, and the slides after it in the Next color with
 * the stops numbered.
 */
public final class IcePath extends PuzzleSolver {

    /** Ticks the silverfish has to stay on one cell to count as resting. */
    private static final int REST_TICKS = 3;
    /** How long a room that did not look like Ice Path is left alone before it is looked at again. */
    private static final int RECHECK_TICKS = 10;
    /** Lifts lines off the ice so the ice does not hide them. */
    private static final double LIFT = 0.05;
    private static final double MARK_INSET = 0.18;

    private RoomFrame frame;
    private boolean inRoom;
    private RoomFrame notHere;
    private long notHereUntil;
    private long ticks;

    private int fishCell = -1;
    private int restTicks;
    private Route route;
    private boolean[] routeBoard;
    private int failedCell = -1;
    private boolean[] failedBoard;

    /** The first slide and its arrow head, in world coordinates; null without a route. */
    private Vec3[] firstSlide;
    private List<Vec3> laterSlides = List.of();
    private AABB hitFrom;
    private List<Vec3> stopLabels = List.of();

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.ICE_PATH;
    }

    @Override
    public void tick(SolverContext ctx) {
        ticks++;
        inRoom = false;
        RoomFrame f = roomOf(ctx);
        if (f == null || listedElsewhere(puzzle())) return;
        if (f.equals(notHere) && ticks < notHereUntil) return;
        ClientLevel level = ctx.level();
        Silverfish fish = findSilverfish(level, f);
        if (fish == null) {
            // Gone (the puzzle is done) or not here yet: nothing to show.
            if (f.equals(frame)) {
                clearRoute();
            } else {
                notHere = f;
                notHereUntil = ticks + RECHECK_TICKS;
            }
            return;
        }
        if (!f.equals(frame)) {
            if (!floorIsIce(level, f)) {
                notHere = f;
                notHereUntil = ticks + RECHECK_TICKS;
                return;
            }
            reset();
            frame = f;
        }
        inRoom = true;

        Vec3 rel = frame.toRel(fish.position());
        int cell = IcePathSearch.cellAt(Mth.floor(rel.x), Mth.floor(rel.z));
        if (cell < 0) return;
        if (cell != fishCell) {
            // Sliding: keep showing the route it is following until it rests.
            fishCell = cell;
            restTicks = 0;
            return;
        }
        if (restTicks < REST_TICKS) restTicks++;
        if (restTicks < REST_TICKS) return;

        boolean[] board = readBoard(level, frame);
        if (board == null || board[cell]) return;
        if (route != null && Arrays.equals(board, routeBoard)) {
            if (route.stops().getFirst() == cell) return;
            if (route.hits() > 0 && route.stops().get(1) == cell) {
                route = route.rest();
                buildMarks(board);
                return;
            }
        }
        if (route == null && cell == failedCell && Arrays.equals(board, failedBoard)) return;
        route = IcePathSearch.solve(board, cell);
        routeBoard = board;
        if (route == null) {
            if (failedCell != cell || !Arrays.equals(board, failedBoard)) {
                AlpakaAddons.LOGGER.info("Ice Path: no route from row {}, column {}", IcePathSearch.row(cell), IcePathSearch.col(cell));
            }
            failedCell = cell;
            failedBoard = board;
        }
        buildMarks(board);
    }

    /** The visible silverfish on the board nearest its middle, or null. */
    private static Silverfish findSilverfish(ClientLevel level, RoomFrame f) {
        AABB board = f.toWorld(new AABB(7, IcePathSearch.WALK_Y - 1, 8, 24, IcePathSearch.WALK_Y + 2, 25));
        Vec3 middle = f.toWorld(new Vec3(15.5, IcePathSearch.WALK_Y, 16.5));
        List<Silverfish> found = level.getEntitiesOfClass(Silverfish.class, board, e -> e.isAlive() && !e.isInvisible());
        return found.stream().min(Comparator.comparingDouble(e -> e.position().distanceToSqr(middle))).orElse(null);
    }

    /** At least half the board's floor is ice. */
    private static boolean floorIsIce(ClientLevel level, RoomFrame f) {
        int ice = 0;
        for (int cell = 0; cell < IcePathSearch.CELLS; cell++) {
            BlockPos pos = f.toWorld(IcePathSearch.relX(cell), IcePathSearch.WALK_Y - 1, IcePathSearch.relZ(cell));
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
            if (level.getBlockState(pos).is(BlockTags.ICE)) ice++;
        }
        return 2 * ice >= IcePathSearch.CELLS;
    }

    /** Which cells have a block on them, or null while part of the board is not loaded. */
    private static boolean[] readBoard(ClientLevel level, RoomFrame f) {
        boolean[] blocked = new boolean[IcePathSearch.CELLS];
        for (int cell = 0; cell < IcePathSearch.CELLS; cell++) {
            BlockPos pos = f.toWorld(IcePathSearch.relX(cell), IcePathSearch.WALK_Y, IcePathSearch.relZ(cell));
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
            blocked[cell] = !level.getBlockState(pos).isAir();
        }
        return blocked;
    }

    // --- what to draw ---

    private void buildMarks(boolean[] board) {
        firstSlide = null;
        laterSlides = List.of();
        hitFrom = null;
        stopLabels = List.of();
        if (route == null || route.hits() == 0) return;
        List<Integer> stops = route.stops();
        Slide first = route.slides().getFirst();

        Vec3 from = centre(stops.get(0));
        Vec3 to = centre(stops.get(1));
        // The arrow head at the end of the first slide.
        Vec3 headA = new Vec3(to.x - 0.6 * first.dx - 0.4 * first.dz, to.y, to.z - 0.6 * first.dz + 0.4 * first.dx);
        Vec3 headB = new Vec3(to.x - 0.6 * first.dx + 0.4 * first.dz, to.y, to.z - 0.6 * first.dz - 0.4 * first.dx);
        firstSlide = new Vec3[]{frame.toWorld(from), frame.toWorld(to), frame.toWorld(headA), frame.toWorld(headB)};

        List<Vec3> later = new ArrayList<>();
        for (int i = 1; i < stops.size(); i++) later.add(frame.toWorld(centre(stops.get(i))));
        laterSlides = later.size() >= 2 ? List.copyOf(later) : List.of();

        // The player hits it facing the way it should go, so they stand on the cell behind it.
        int behind = IcePathSearch.step(stops.get(0), opposite(first));
        if (behind >= 0 && !board[behind]) {
            double x = IcePathSearch.relX(behind);
            double z = IcePathSearch.relZ(behind);
            hitFrom = frame.toWorld(new AABB(x + MARK_INSET, IcePathSearch.WALK_Y + 0.01, z + MARK_INSET,
                    x + 1 - MARK_INSET, IcePathSearch.WALK_Y + LIFT, z + 1 - MARK_INSET));
        }

        List<Vec3> labels = new ArrayList<>();
        for (int i = 1; i < stops.size(); i++) {
            labels.add(frame.toWorld(new Vec3(IcePathSearch.relX(stops.get(i)) + 0.5, IcePathSearch.WALK_Y + 0.6,
                    IcePathSearch.relZ(stops.get(i)) + 0.5)));
        }
        stopLabels = List.copyOf(labels);
    }

    /** A cell's middle in room coordinates, just above the ice. */
    private static Vec3 centre(int cell) {
        return new Vec3(IcePathSearch.relX(cell) + 0.5, IcePathSearch.WALK_Y + LIFT, IcePathSearch.relZ(cell) + 0.5);
    }

    private static Slide opposite(Slide slide) {
        return switch (slide) {
            case PLUS_Z -> Slide.MINUS_Z;
            case MINUS_Z -> Slide.PLUS_Z;
            case PLUS_X -> Slide.MINUS_X;
            case MINUS_X -> Slide.PLUS_X;
        };
    }

    private void clearRoute() {
        fishCell = -1;
        restTicks = 0;
        route = null;
        routeBoard = null;
        firstSlide = null;
        laterSlides = List.of();
        hitFrom = null;
        stopLabels = List.of();
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!inRoom) return;
        int next = SolverColors.next();
        int now = SolverColors.solution();
        if (!laterSlides.isEmpty()) draw.path(laterSlides, next);
        if (firstSlide != null) {
            draw.line(firstSlide[0], firstSlide[1], now);
            draw.line(firstSlide[1], firstSlide[2], now);
            draw.line(firstSlide[1], firstSlide[3], now);
        }
        if (hitFrom != null) draw.box(hitFrom, now);
        for (int i = 0; i < stopLabels.size(); i++) {
            draw.label(stopLabels.get(i), Integer.toString(i + 1), i == 0 ? now : next, 0.8f);
        }
    }

    @Override
    public void reset() {
        frame = null;
        inRoom = false;
        notHere = null;
        notHereUntil = 0L;
        failedCell = -1;
        failedBoard = null;
        clearRoute();
    }

    @Override
    public boolean claimsRoom() {
        return inRoom;
    }

    @Override
    public String debugState() {
        if (frame == null) return null;
        String at = fishCell < 0 ? "no silverfish" : "silverfish at row " + IcePathSearch.row(fishCell) + ", column " + IcePathSearch.col(fishCell)
                + (restTicks >= REST_TICKS ? " (resting)" : " (moving)");
        if (route == null) return at + ", no route";
        return at + ", " + route.hits() + " hits: " + route.slides();
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
