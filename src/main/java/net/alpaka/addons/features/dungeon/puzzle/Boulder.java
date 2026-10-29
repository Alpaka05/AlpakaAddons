package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.puzzle.BoulderBoard.Push;
import net.alpaka.addons.features.dungeon.puzzle.BoulderSearch.Step;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.features.dungeon.room.PuzzleTab;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Boulder: reads the 7 x 6 board of boxes, searches the fewest pushes that clear a path to the
 * chest, and marks the box to push next, the button to press for it, and the pushes after it.
 *
 * The board is read from the blocks every tick (puzzles-floor.md 1.3): a cell holds a box when its
 * centre has planks on the bottom layer and a block on the layer above. A cell where only one of the
 * two holds is a box half sent or half moved, and the board is left alone until it reads clean. The
 * room counts as Boulder once its board shows at least {@link #MIN_BOXES} boxes; the known layouts
 * start with 13 to 23.
 *
 * A changed board is solved again once it has read the same for {@link #STABLE_TICKS} ticks, so a
 * box on its way is not taken for a new layout. If the change is just the push the plan asked for,
 * the rest of the plan stays; it is still the shortest. Anything else (another player's push, a
 * rejoin, a merged box) gets a fresh search from what the board shows, so the solver never relies on
 * having watched the room from the start.
 *
 * Plans only push boxes into empty cells: two boxes merging is a Hypixel bug and is never planned on
 * (critic.md 3.1). The first push is only offered if a button or sign really is on that face; the
 * faces of later pushes cannot be checked before the box gets there.
 *
 * Draws, all depth-tested: the next box's outline and its handle in the Solution color with an arrow
 * on top of the box, and each later push's handle and arrow in the Next color, numbered.
 */
public final class Boulder extends PuzzleSolver {

    /**
     * Fewest boxes a board must show to be taken for the Boulder room: well under the 13 the
     * emptiest known layout starts with, and enough that a room with planks at a few of the 42
     * spots is not mistaken for it.
     */
    private static final int MIN_BOXES = 8;
    /** Ticks a changed board must stay the same before it is solved again. */
    private static final int STABLE_TICKS = 2;
    /** How long a room that did not look like Boulder is left alone before it is read again. */
    private static final int RECHECK_TICKS = 10;
    private static final long UNREADABLE = -1L;
    /** Lifts arrows off the box tops so the plank faces do not hide them. */
    private static final double LIFT = 0.05;
    /** Handles are tiny; their box grows a little so it is not lost in the plank face behind. */
    private static final double HANDLE_INFLATE = 0.02;

    /** The room recognised as Boulder, or null. */
    private RoomFrame frame;
    /** Whether the player was in that room this tick. */
    private boolean inRoom;
    private RoomFrame notHere;
    private long notHereUntil;
    private long ticks;

    /** The last board read, and for how many ticks in a row it read that way. */
    private long lastRead = UNREADABLE;
    private int sameTicks;
    /** The board the current plan starts from. */
    private long planBoard = UNREADABLE;
    private BoulderSearch.Outcome outcome;
    private List<Step> plan = List.of();
    private boolean handlesSeen;
    private long reportedBoard = UNREADABLE;

    private List<StepMarks> marks = List.of();
    private Vec3 noPlanAt;

    /** What is drawn for one push, in world coordinates. */
    private record StepMarks(int number, AABB box, AABB handle, Vec3 tail, Vec3 tip, Vec3 headA, Vec3 headB, Vec3 labelAt) {}

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.BOULDER;
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
            long board = read(level, f);
            if (board == UNREADABLE || Long.bitCount(board) < MIN_BOXES) {
                notHere = f;
                notHereUntil = ticks + RECHECK_TICKS;
                return;
            }
            reset();
            frame = f;
        }
        inRoom = true;

        long board = read(level, frame);
        // Half a box: wait for the rest of the update and keep showing the last plan meanwhile.
        if (board == UNREADABLE) return;
        if (board != lastRead) {
            lastRead = board;
            sameTicks = 0;
        } else if (sameTicks < STABLE_TICKS) {
            sameTicks++;
        }
        if (sameTicks < STABLE_TICKS || board == planBoard) return;
        replan(level, board);
    }

    private void replan(ClientLevel level, long board) {
        handlesSeen = anyHandle(level, frame, board);
        List<Step> rest = BoulderSearch.afterFirst(plan, planBoard, board);
        planBoard = board;
        if (rest != null) {
            plan = rest;
            if (plan.isEmpty()) outcome = BoulderSearch.Outcome.OPEN;
        }
        if (rest == null || (!plan.isEmpty() && !hasHandle(level, plan.getFirst().cell(), plan.getFirst().push()))) {
            BoulderSearch.Result result = BoulderSearch.solve(board, (cell, push) -> hasHandle(level, cell, push));
            outcome = result.outcome();
            plan = result.steps();
            if (!result.found() && reportedBoard != board) {
                reportedBoard = board;
                AlpakaAddons.LOGGER.info("Boulder: no plan for board {} ({}, {} boards looked at)",
                        BoulderBoard.key(board), outcome, result.boards());
            }
        }
        buildMarks(level, board);
    }

    // --- reading the room ---

    /** The board as it stands, or {@link #UNREADABLE} while a chunk is missing or a box is half there. */
    private static long read(ClientLevel level, RoomFrame f) {
        long boxes = 0L;
        for (int i = 0; i < BoulderBoard.CELLS; i++) {
            BlockPos low = f.toWorld(BoulderBoard.centreX(BoulderBoard.col(i)), BoulderBoard.BOX_Y, BoulderBoard.centreZ(BoulderBoard.row(i)));
            if (!level.hasChunk(low.getX() >> 4, low.getZ() >> 4)) return UNREADABLE;
            boolean planks = level.getBlockState(low).is(BlockTags.PLANKS);
            boolean filled = !level.getBlockState(low.atY(BoulderBoard.BOX_CHECK_Y)).isAir();
            if (planks != filled) return UNREADABLE;
            if (planks) boxes |= 1L << i;
        }
        return boxes;
    }

    /**
     * Whether a button or sign sits on the face of the box in {@code cell} that pushes it in
     * {@code push}. If the board shows no handle anywhere (a different block than expected), every
     * face counts, so the solver still works, only without this check.
     */
    private boolean hasHandle(ClientLevel level, int cell, Push push) {
        return !handlesSeen || findHandle(level, frame, cell, push) != null;
    }

    /**
     * The handle on that face, in the world, or null. Buttons sit in the middle of the face's bottom
     * layer; signs are said to work too, and where they hang is not known, so the whole face is
     * looked at.
     */
    private static BlockPos findHandle(ClientLevel level, RoomFrame f, int cell, Push push) {
        BlockPos spot = BoulderBoard.handleSpot(cell, push);
        int alongX = Math.abs(push.dz);
        int alongZ = Math.abs(push.dx);
        for (int y = BoulderBoard.BOX_Y; y <= BoulderBoard.BOX_Y + 2; y++) {
            for (int k : new int[]{0, -1, 1}) {
                BlockPos pos = f.toWorld(spot.getX() + k * alongX, y, spot.getZ() + k * alongZ);
                if (isHandle(level.getBlockState(pos))) return pos;
            }
        }
        return null;
    }

    private static boolean isHandle(BlockState state) {
        return state.is(BlockTags.BUTTONS) || state.is(BlockTags.ALL_SIGNS);
    }

    private static boolean anyHandle(ClientLevel level, RoomFrame f, long board) {
        for (long rest = board; rest != 0; rest &= rest - 1) {
            int cell = Long.numberOfTrailingZeros(rest);
            for (Push push : Push.values()) {
                if (findHandle(level, f, cell, push) != null) return true;
            }
        }
        return false;
    }

    /** Room Y of the box tops: boxes look three blocks high, but only two layers are known for sure. */
    private static int boxTop(ClientLevel level, RoomFrame f, long board) {
        int tall = 0;
        for (long rest = board; rest != 0; rest &= rest - 1) {
            int cell = Long.numberOfTrailingZeros(rest);
            BlockPos third = f.toWorld(BoulderBoard.centreX(BoulderBoard.col(cell)), BoulderBoard.BOX_Y + 2, BoulderBoard.centreZ(BoulderBoard.row(cell)));
            if (level.getBlockState(third).is(BlockTags.PLANKS)) tall++;
        }
        return BoulderBoard.BOX_Y + (2 * tall > Long.bitCount(board) ? 3 : 2);
    }

    // --- what to draw ---

    private void buildMarks(ClientLevel level, long board) {
        noPlanAt = null;
        if (outcome == BoulderSearch.Outcome.NO_PLAN || outcome == BoulderSearch.Outcome.TOO_HARD) {
            noPlanAt = frame.toWorld(new Vec3(15.5, 69.5, 16.5));
            marks = List.of();
            return;
        }
        int top = boxTop(level, frame, board);
        List<StepMarks> out = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            Step step = plan.get(i);
            int cell = step.cell();
            Push push = step.push();
            AABB box = frame.toWorld(BoulderBoard.boxVolume(cell, top)).inflate(SolverDraw.BLOCK_INFLATE);

            // The first handle is the one actually found; later ones are where the button usually is.
            BlockPos found = i == 0 && handlesSeen ? findHandle(level, frame, cell, push) : null;
            AABB handle = found != null
                    ? handleBox(level, found)
                    : new AABB(frame.toWorld(BoulderBoard.handleSpot(cell, push))).inflate(SolverDraw.BLOCK_INFLATE);

            double cx = BoulderBoard.centreX(BoulderBoard.col(cell)) + 0.5;
            double cz = BoulderBoard.centreZ(BoulderBoard.row(cell)) + 0.5;
            double y = top + LIFT;
            Vec3 tail = new Vec3(cx - push.dx, y, cz - push.dz);
            Vec3 tip = new Vec3(cx + 1.1 * push.dx, y, cz + 1.1 * push.dz);
            // The head's two strokes: back from the tip, and out to either side.
            Vec3 headA = new Vec3(tip.x - 0.5 * push.dx - 0.4 * push.dz, y, tip.z - 0.5 * push.dz + 0.4 * push.dx);
            Vec3 headB = new Vec3(tip.x - 0.5 * push.dx + 0.4 * push.dz, y, tip.z - 0.5 * push.dz - 0.4 * push.dx);
            Vec3 labelAt = new Vec3(cx, top + 0.7, cz);
            out.add(new StepMarks(i + 1, box, handle, frame.toWorld(tail), frame.toWorld(tip),
                    frame.toWorld(headA), frame.toWorld(headB), frame.toWorld(labelAt)));
        }
        marks = List.copyOf(out);
    }

    /** The handle's own outline (a button is small), a little larger. */
    private static AABB handleBox(ClientLevel level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        return box.inflate(HANDLE_INFLATE);
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!inRoom) return;
        if (noPlanAt != null) {
            draw.label(noPlanAt, "No Solution", SolverColors.wrong(), 1.5f);
            return;
        }
        // Later pushes first, so the next one is drawn over them where they meet.
        for (int i = marks.size() - 1; i >= 0; i--) {
            StepMarks m = marks.get(i);
            boolean next = m.number() == 1;
            int color = next ? SolverColors.solution() : SolverColors.next();
            if (next) draw.outline(m.box(), color);
            draw.box(m.handle(), color);
            draw.line(m.tail(), m.tip(), color);
            draw.line(m.tip(), m.headA(), color);
            draw.line(m.tip(), m.headB(), color);
            if (marks.size() > 1) draw.label(m.labelAt(), Integer.toString(m.number()), color, 1.0f);
        }
    }

    @Override
    public void reset() {
        frame = null;
        inRoom = false;
        notHere = null;
        notHereUntil = 0L;
        lastRead = UNREADABLE;
        sameTicks = 0;
        planBoard = UNREADABLE;
        outcome = null;
        plan = List.of();
        handlesSeen = false;
        marks = List.of();
        noPlanAt = null;
    }

    @Override
    public boolean claimsRoom() {
        return inRoom;
    }

    @Override
    public String debugState() {
        if (frame == null) return null;
        StringBuilder out = new StringBuilder("board ");
        out.append(planBoard == UNREADABLE ? "-" : BoulderBoard.key(planBoard));
        out.append(", ").append(outcome == null ? "not solved yet" : outcome);
        if (!plan.isEmpty()) {
            Step first = plan.getFirst();
            out.append(", ").append(plan.size()).append(" pushes, next box at (")
                    .append(BoulderBoard.centreX(BoulderBoard.col(first.cell()))).append(", ")
                    .append(BoulderBoard.centreZ(BoulderBoard.row(first.cell()))).append(") ").append(first.push());
        }
        out.append(handlesSeen ? ", handles seen" : ", no handles seen");
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
