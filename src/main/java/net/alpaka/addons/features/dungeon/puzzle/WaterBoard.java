package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoardLayout.Cell;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.alpaka.addons.features.dungeon.terminal.ServerTicks;
import net.alpaka.addons.features.performance.PerformanceStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Water Board: which levers to flip, in which order, and when.
 *
 * The room has a board of blocks that six levers push in and out, a lever that turns the water on,
 * and five doors in front of the reward chest, three of them closed. Water reaching the outlet of
 * a door's color toggles that door; the puzzle is solved when all five are open. The board comes in
 * four layouts, and for each layout and each set of closed doors there is a known solution: flip
 * some levers, switch the water on, then flip levers at set times while the water runs
 * ({@link WaterBoardSolutions}).
 *
 * In the room this reads the layout and the doors from the blocks, takes the matching solution and
 * marks every lever that still has something to do: a box in the Solution Color on the lever for
 * the next step, the Next Color on the others, the Wrong Color on a lever to flip back and on the
 * water lever while flips before the water are left, and above each lever one label per step,
 * "#2 Flip", "#5 Water On", "#6 3.4s". The countdowns run in server ticks from the moment the
 * water came on, so they slow down with the server, and they end in plain text - no sound, flash
 * or color change marks the moment a flip is due (design.md rule 8). Flips are tracked from the
 * board itself, so teammates' flips, a lever flipped by mistake (it gets a "Flip Back" step) and
 * walking in on a half-done board all work out; the player's own click only moves the marks a
 * little earlier.
 *
 * Everything is read, nothing is acted on: the player's lever clicks are observed after vanilla
 * made them and never changed (changing one makes the client try the off hand too, which Hypixel
 * takes as a second flip), and every mark is drawn depth-tested in the room the player is in.
 *
 * Spec: puzzles-water.md section 7, fixtures in section 8.
 */
public final class WaterBoard extends PuzzleSolver {

    /** Ticks between looks for the room while the player is in a room that is not it. */
    private static final int SEARCH_TICKS = 5;
    /** Ticks between reads of the doors and the board. */
    private static final int CHECK_TICKS = 10;
    /** Reads with a door's two blocks disagreeing before only the lower block counts. */
    private static final int AMBIGUOUS_READS = 6;
    /** Reads with a lever's block on neither plane before it is taken as not flipped. */
    private static final int UNKNOWN_READS = 3;
    /**
     * This long after the plan's last step was due, with every step done, the water still on and the
     * doors not all open, the run reads as missed. The last door opens up to about five seconds after
     * the last flip (measured finish times, puzzles-water.md 8.4), so this leaves room.
     */
    private static final int MISSED_TICKS = 200;
    /** This long after the last step was due, the water off and the board dry end a run that was missed. */
    private static final int DRAIN_TICKS = 40;
    /** Label lines above a lever start this high above its block and are this far apart. */
    private static final double LABEL_BASE = 1.25;
    private static final double LABEL_STEP = 0.3;
    private static final double BOX_INFLATE = 0.02;

    private enum Stage {
        /** The player has not been in the room yet. */
        SEARCHING,
        /** In the room; reading the board until it is in a state with a solution. */
        WAITING,
        /** A solution is being played out. */
        PLANNED,
        /** All doors open. */
        DONE
    }

    /**
     * Why there is no solution to show, as shown above the water lever. These describe the board
     * rather than order anything: the water may be a teammate's run that is going right. Only the
     * two that mean the solver cannot help here are in the Wrong Color; the rest are in the Next
     * Color, so that red above the water lever never reads as "flip it".
     */
    private enum Problem {
        WATER_RUNNING("Water is running: no solution until it is off and drained", false),
        WATER_DRAINING("Water is draining: waiting for a dry board", false),
        DOORS("Doors are not in a start state", false),
        LAYOUT("Unknown board layout", true),
        DATA("No solution for this board", true),
        /** The run is over without the doors, and they are three closed: a new solution follows. */
        MISSED_RETRY("Missed: turn the water off for a new solution", false),
        /** The run is over without the doors, and they are no longer in a state with a solution. */
        MISSED_STUCK("Missed: doors are not in a start state", false);

        final String text;
        final boolean error;

        Problem(String text, boolean error) {
            this.text = text;
            this.error = error;
        }

        /** The missed run's problem for the doors as they read now. */
        static Problem missed(WaterBoardLayout.Doors doors) {
            return doors.clear() && doors.closedCount() == 3 ? MISSED_RETRY : MISSED_STUCK;
        }
    }

    private static final WaterBoardLever[] LEVERS = WaterBoardLever.values();

    private final WaterBoardClock clock = new WaterBoardClock();

    private Stage stage = Stage.SEARCHING;
    private RoomFrame room = null;
    private boolean inRoom = false;
    private long ticks = 0L;
    private long nextSearch = 0L;
    private long nextCheck = 0L;
    private int ambiguousReads = 0;
    private int unknownReads = 0;
    private Problem problem = null;

    private int variant = 0;
    private int closedMask = 0;
    private WaterBoardPlan plan = null;
    /** World positions of the levers in the room. */
    private final BlockPos[] leverPos = new BlockPos[LEVERS.length];
    /** The boxes drawn around them, from the lever's own shape; null until it was seen. */
    private final AABB[] leverBox = new AABB[LEVERS.length];
    /** Per board lever: whether it is flipped as the board last showed it. */
    private final boolean[] flipped = new boolean[LEVERS.length];
    /** Per board lever: whether its blocks can be found, so a flip of it shows on the board. */
    private final boolean[] observable = new boolean[LEVERS.length];
    private Boolean waterPowered = null;

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.WATER_BOARD;
    }

    /** The solution set in use. Fast (Odin's quicker set) needs tighter timing; Odin keeps it off by default too. */
    private static WaterBoardSolutions.Table table() {
        return AlpakaConfig.instance.waterBoardFastSolutions ? WaterBoardSolutions.Table.FAST : WaterBoardSolutions.Table.STANDARD;
    }

    // --- tick ---

    @Override
    public void tick(SolverContext ctx) {
        ticks++;
        long now = now(ctx.nowMs());
        inRoom = false;
        RoomFrame frame = ctx.frame();
        if (frame == null) return;
        if (!frame.equals(room)) {
            if (ticks < nextSearch) return;
            nextSearch = ticks + SEARCH_TICKS;
            if (!WaterBoardLayout.isRoom(view(ctx.level(), frame))) return;
            // One Water Board per run; a second one would be a new run in the same world.
            enter(frame);
        }
        inRoom = true;
        WaterBoardLayout.View view = view(ctx.level(), room);
        switch (stage) {
            case WAITING -> {
                if (ticks >= nextCheck) {
                    nextCheck = ticks + CHECK_TICKS;
                    load(ctx.level(), view);
                }
            }
            case PLANNED -> follow(ctx, view, now);
            default -> {
            }
        }
    }

    private void enter(RoomFrame frame) {
        clear();
        room = frame;
        stage = Stage.WAITING;
        nextCheck = ticks;
        for (WaterBoardLever lever : LEVERS) leverPos[lever.ordinal()] = frame.toWorld(lever.lever());
    }

    /** Reads the board and, once it is in a state with a solution, starts playing it out. */
    private void load(ClientLevel level, WaterBoardLayout.View view) {
        WaterBoardLayout.Doors doors = WaterBoardLayout.doors(view, ambiguousReads >= AMBIGUOUS_READS);
        if (!doors.clear()) {
            if (doors.read() == WaterBoardLayout.DoorRead.AMBIGUOUS) ambiguousReads++;
            return;
        }
        if (doors.allOpen()) {
            stage = Stage.DONE;
            problem = null;
            return;
        }
        Boolean water = WaterBoardLayout.waterOnBoard(view);
        if (water == null) return;
        // Solutions start from a dry board with the water off; water still running would
        // toggle doors on its own. It may be a teammate's run, so only say what is going on.
        if (water) {
            BlockState waterLever = level.getBlockState(leverPos[WaterBoardLever.WATER.ordinal()]);
            boolean running = !waterLever.is(Blocks.LEVER) || waterLever.getValue(LeverBlock.POWERED);
            problem = running ? Problem.WATER_RUNNING : Problem.WATER_DRAINING;
            return;
        }
        if (doors.closedCount() != 3) {
            problem = Problem.DOORS;
            return;
        }
        int found = WaterBoardLayout.variant(view);
        if (found == 0) {
            problem = Problem.LAYOUT;
            return;
        }
        WaterBoardSolutions solutions = WaterBoardSolutions.bundled();
        Map<WaterBoardLever, double[]> solution = solutions == null ? null : solutions.get(table(), found, doors.key());
        if (solution == null) {
            problem = Problem.DATA;
            return;
        }

        // Levers flipped before the solver saw the board (a teammate, a rejoin, a mistake): their
        // blocks are on the other plane. A block on neither plane is most likely moving; look again.
        Boolean[] read = new Boolean[LEVERS.length];
        boolean unknown = false;
        for (WaterBoardLever lever : LEVERS) {
            if (lever.isWater() || lever.representative(found) == null) continue;
            read[lever.ordinal()] = WaterBoardLayout.flipped(view, lever, found);
            if (read[lever.ordinal()] == null) unknown = true;
        }
        if (unknown && unknownReads++ < UNKNOWN_READS) return;
        BlockState waterLever = level.getBlockState(leverPos[WaterBoardLever.WATER.ordinal()]);
        if (!waterLever.is(Blocks.LEVER)) return;

        Set<WaterBoardLever> flippedNow = EnumSet.noneOf(WaterBoardLever.class);
        for (WaterBoardLever lever : LEVERS) {
            int i = lever.ordinal();
            // A lever whose block is never where the data says is taken as untouched, and its
            // clicks are trusted as they are, since the board cannot confirm them.
            observable[i] = read[i] != null;
            flipped[i] = read[i] != null && read[i];
            if (flipped[i]) flippedNow.add(lever);
        }
        waterPowered = waterLever.getValue(LeverBlock.POWERED);
        variant = found;
        closedMask = doors.closedMask();
        plan = WaterBoardPlan.of(solution, flippedNow);
        stage = Stage.PLANNED;
        problem = null;
        for (WaterBoardLever lever : LEVERS) leverBox[lever.ordinal()] = leverShape(level, leverPos[lever.ordinal()]);
    }

    /** While a solution plays out: what the board shows, every tick; the doors now and then. */
    private void follow(SolverContext ctx, WaterBoardLayout.View view, long now) {
        int rtt = rttTicks(ctx.mc());
        for (WaterBoardLever lever : LEVERS) {
            int i = lever.ordinal();
            if (lever.isWater() || !observable[i]) continue;
            Boolean seen = WaterBoardLayout.flipped(view, lever, variant);
            // Neither plane holding the block means it is on its way; the next tick tells.
            if (seen != null && seen != flipped[i]) {
                flipped[i] = seen;
                plan.observedFlip(lever, now, rtt);
            }
        }
        // The block update normally arrives through onBlockChange first; this catches one that came
        // with a chunk instead.
        BlockState waterLever = ctx.level().getBlockState(leverPos[WaterBoardLever.WATER.ordinal()]);
        if (waterLever.is(Blocks.LEVER)) {
            boolean powered = waterLever.getValue(LeverBlock.POWERED);
            if (waterPowered == null || powered != waterPowered) {
                if (waterPowered != null) plan.observedFlip(WaterBoardLever.WATER, now, rtt);
                waterPowered = powered;
            }
        }
        Boolean wet = WaterBoardLayout.waterAtEntrance(view);
        if (wet != null) plan.observedEntrance(wet, now, rtt);
        plan.expire(now);

        for (int i = 0; i < LEVERS.length; i++) {
            if (leverBox[i] == null) leverBox[i] = leverShape(ctx.level(), leverPos[i]);
        }
        if (ticks >= nextCheck) {
            nextCheck = ticks + CHECK_TICKS;
            progress(view, now);
        }
    }

    /** Whether the doors are all open, or the run is over without them and has to start again. */
    private void progress(WaterBoardLayout.View view, long now) {
        WaterBoardLayout.Doors doors = WaterBoardLayout.doors(view, ambiguousReads >= AMBIGUOUS_READS);
        if (doors.allOpen()) {
            stage = Stage.DONE;
            plan = null;
            problem = null;
            return;
        }
        if (plan.phase() == WaterBoardPlan.Phase.READY) {
            // The doors changed before the water ran (someone ran it unseen): read the board afresh.
            if (doors.clear() && doors.closedMask() != closedMask) replan();
            return;
        }
        long sinceLastStep = now - (plan.startTick() + plan.lastDueTicks());
        Boolean water = WaterBoardLayout.waterOnBoard(view);
        if (plan.offPlan() && !plan.awaitingConfirmation()) {
            // The water was switched when the plan did not ask for it: the countdowns no longer
            // match the flow. Start over as soon as the board is dry, without waiting for them.
            if (plan.waterOn()) problem = Problem.missed(doors);
            else if (Boolean.FALSE.equals(water)) replan();
            else problem = Problem.WATER_DRAINING;
            return;
        }
        if (!plan.waterOn() && Boolean.FALSE.equals(water) && !plan.awaitingConfirmation()
                && (plan.remaining() == 0 || sinceLastStep > DRAIN_TICKS)) {
            // The water ran and drained and the doors are not all open: the solution was missed.
            // The board as it is now gets a solution of its own, levers flipped back where needed.
            replan();
            return;
        }
        // Every step was due long ago (a flip-back left undone counts among them) and the doors
        // are still not all open: the run was missed.
        problem = plan.waterOn() && sinceLastStep > MISSED_TICKS ? Problem.missed(doors) : null;
    }

    private void replan() {
        stage = Stage.WAITING;
        plan = null;
        problem = null;
        nextCheck = ticks;
        unknownReads = 0;
    }

    // --- observed events ---

    @Override
    public void onOwnBlockUse(BlockPos pos, InteractionHand hand) {
        if (plan == null || room == null || hand != InteractionHand.MAIN_HAND) return;
        WaterBoardLever lever = WaterBoardLever.at(room.toRel(pos));
        if (lever == null) return;
        // A board lever with no blocks in this layout changes nothing; its solutions never use it.
        if (!lever.isWater() && lever.representative(variant) == null) return;
        plan.ownFlip(lever, now(Util.getMillis()), lever.isWater() || observable[lever.ordinal()]);
    }

    @Override
    public void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {
        BlockPos waterLever = leverPos[WaterBoardLever.WATER.ordinal()];
        if (plan == null || waterLever == null || !pos.equals(waterLever)) return;
        if (!newState.is(Blocks.LEVER) || waterPowered == null) return;
        boolean powered = newState.getValue(LeverBlock.POWERED);
        if (powered == waterPowered) return;
        // The exact moment the server switched the water, wherever the player is in the run.
        waterPowered = powered;
        plan.observedFlip(WaterBoardLever.WATER, now(Util.getMillis()), rttTicks(Minecraft.getInstance()));
    }

    // --- drawing ---

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!inRoom || room == null) return;
        if (problem != null) {
            BlockPos at = leverPos[WaterBoardLever.WATER.ordinal()];
            draw.label(new Vec3(at.getX() + 0.5, at.getY() + LABEL_BASE + 0.4, at.getZ() + 0.5), problem.text,
                    problem.error ? SolverColors.wrong() : SolverColors.next(), 1.0f);
        }
        // While a run is missed or off its plan, its countdowns and "Late" labels say nothing true.
        if (stage != Stage.PLANNED || plan == null || problem != null || plan.offPlan()) return;
        List<WaterBoardPlan.Mark> marks = plan.marks(now(Util.getMillis()));
        for (WaterBoardPlan.Mark mark : marks) {
            int i = mark.lever().ordinal();
            BlockPos pos = leverPos[i];
            AABB box = leverBox[i] != null ? leverBox[i] : new AABB(pos).inflate(SolverDraw.BLOCK_INFLATE);
            draw.box(box, color(mark.tone()));
            List<WaterBoardPlan.Line> lines = mark.lines();
            for (int k = 0; k < lines.size(); k++) {
                Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY() + LABEL_BASE + LABEL_STEP * k, pos.getZ() + 0.5);
                draw.label(at, lines.get(k).text(), color(lines.get(k).tone()), 1.0f);
            }
        }
        // Where to go after the next lever, as a line between the two; walls hide it like any block.
        if (marks.size() >= 2) {
            draw.line(centre(marks.get(0).lever()), centre(marks.get(1).lever()), SolverColors.next());
        }
    }

    private Vec3 centre(WaterBoardLever lever) {
        AABB box = leverBox[lever.ordinal()];
        return box != null ? box.getCenter() : Vec3.atCenterOf(leverPos[lever.ordinal()]);
    }

    private static int color(WaterBoardPlan.Tone tone) {
        return switch (tone) {
            case SOLUTION -> SolverColors.solution();
            case NEXT -> SolverColors.next();
            case WRONG -> SolverColors.wrong();
        };
    }

    // --- state ---

    @Override
    public void reset() {
        clear();
        room = null;
        stage = Stage.SEARCHING;
        inRoom = false;
    }

    private void clear() {
        stage = Stage.SEARCHING;
        problem = null;
        plan = null;
        variant = 0;
        closedMask = 0;
        ambiguousReads = 0;
        unknownReads = 0;
        waterPowered = null;
        nextCheck = ticks;
        Arrays.fill(leverPos, null);
        Arrays.fill(leverBox, null);
        Arrays.fill(flipped, false);
        Arrays.fill(observable, false);
    }

    @Override
    public boolean claimsRoom() {
        return room != null && inRoom;
    }

    @Override
    public String debugState() {
        if (room == null) return null;
        StringBuilder out = new StringBuilder(stage.name().toLowerCase());
        if (problem != null) out.append(", ").append(problem.text);
        if (variant != 0) {
            out.append(", layout ").append(variant).append(", doors ").append(WaterBoardLayout.doorKey(closedMask))
                    .append(", ").append(table().key());
        }
        if (plan != null) {
            long now = now(Util.getMillis());
            out.append(", ").append(plan.phase().name().toLowerCase());
            if (plan.phase() == WaterBoardPlan.Phase.RUNNING) {
                out.append(" ").append(WaterBoardPlan.seconds(now - plan.startTick()));
                if (plan.estimated()) out.append(" (estimated)");
            }
            out.append(", ").append(plan.remaining()).append(" steps left");
            List<WaterBoardPlan.Mark> marks = plan.marks(now);
            if (!marks.isEmpty()) {
                out.append(", next ").append(marks.getFirst().lever().displayName()).append(' ')
                        .append(marks.getFirst().lines().getFirst().text());
            }
        }
        return out.toString();
    }

    // --- the world ---

    /** The time now on the board's clock, in server ticks. */
    private long now(long nowMs) {
        return clock.read(ServerTicks.count(), ServerTicks.live(nowMs), nowMs);
    }

    /**
     * The round trip to the server in ticks, for a water start seen from a teammate's click. From the
     * Performance HUD's ping when it measures one, else the latency the tab list reports; reading
     * either sends nothing.
     */
    private static int rttTicks(Minecraft mc) {
        int ms = PerformanceStats.pingMs();
        if (ms < 0) {
            ClientPacketListener connection = mc.getConnection();
            PlayerInfo info = connection == null || mc.player == null ? null : connection.getPlayerInfo(mc.player.getUUID());
            ms = info == null ? 0 : info.getLatency();
        }
        return ms <= 0 ? 0 : Math.min(40, Math.round(ms / 50.0f));
    }

    /** The lever's own outline, a little larger; null while its block is not a lever. */
    private static AABB leverShape(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.LEVER)) return null;
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return new AABB(pos).inflate(SolverDraw.BLOCK_INFLATE);
        return shape.bounds().move(pos).inflate(BOX_INFLATE);
    }

    private static WaterBoardLayout.View view(ClientLevel level, RoomFrame frame) {
        return (rx, y, rz) -> {
            BlockPos pos = frame.toWorld(rx, y, rz);
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return Cell.UNLOADED;
            return cellOf(level.getBlockState(pos));
        };
    }

    private static Cell cellOf(BlockState state) {
        if (state.isAir()) return Cell.AIR;
        if (!state.getFluidState().isEmpty()) return Cell.WATER;
        Block block = state.getBlock();
        if (block == Blocks.LEVER) return Cell.LEVER;
        if (block == Blocks.COAL_BLOCK) return Cell.COAL;
        if (block == Blocks.GOLD_BLOCK) return Cell.GOLD;
        if (block == Blocks.QUARTZ_BLOCK) return Cell.QUARTZ;
        if (block == Blocks.DIAMOND_BLOCK) return Cell.DIAMOND;
        if (block == Blocks.EMERALD_BLOCK) return Cell.EMERALD;
        if (block == Blocks.TERRACOTTA) return Cell.TERRACOTTA;
        return Cell.OTHER;
    }
}
