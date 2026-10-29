package net.alpaka.addons.features.dungeon;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.device.ArrowAlign;
import net.alpaka.addons.features.dungeon.device.GoldorPhase;
import net.alpaka.addons.features.dungeon.device.LightsDevice;
import net.alpaka.addons.features.dungeon.device.Sharpshooter;
import net.alpaka.addons.features.dungeon.device.SimonSays;
import net.alpaka.addons.features.dungeon.puzzle.Boulder;
import net.alpaka.addons.features.dungeon.puzzle.CreeperBeams;
import net.alpaka.addons.features.dungeon.puzzle.HigherOrLower;
import net.alpaka.addons.features.dungeon.puzzle.IceFill;
import net.alpaka.addons.features.dungeon.puzzle.IcePath;
import net.alpaka.addons.features.dungeon.puzzle.PuzzleSolver;
import net.alpaka.addons.features.dungeon.puzzle.Quiz;
import net.alpaka.addons.features.dungeon.puzzle.TeleportMaze;
import net.alpaka.addons.features.dungeon.puzzle.ThreeWeirdos;
import net.alpaka.addons.features.dungeon.puzzle.TicTacToe;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoard;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.features.dungeon.room.PuzzleTab;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.alpaka.addons.features.dungeon.terminal.TerminalSession;
import net.alpaka.addons.utils.AlpakaPerf;
import net.alpaka.addons.utils.DungeonState;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The dungeon solvers: one instance of every puzzle and device solver, and the hooks that feed them.
 *
 * Each client tick decides who runs. Nothing does outside a Catacombs run on SkyBlock. Puzzle
 * solvers run in the room grid before the boss room, unless the tab list already shows their
 * puzzle solved or failed; device solvers run in the F7/M7 boss's Goldor phase. A solver also needs
 * its master and its own switch; one that is switched off is reset. The solvers that run get the
 * tick, then this frame's {@link SolverDraw}; chat lines, server block changes, server teleports and
 * the player's own clicks on blocks and entities reach the same solvers in between.
 *
 * Everything here only reads. Nothing is sent, no interaction is cancelled or changed, and the marks
 * are drawn with depth testing, so walls hide them (design.md section 1). A solver that throws is
 * logged once and reset instead of taking the game down with it.
 */
public final class DungeonSolvers {

    /** Puzzle marks may reach this far past the player's cell (a box on the room's edge). */
    private static final double CLIP_MARGIN = 1.0;
    /**
     * Device marks stay within this many blocks of the player on X and Z. Every device fits well
     * inside it from where it is used (Sharpshooter's targets are 15 blocks from its plate), and
     * the four devices are 60 and more blocks apart, so only the one the player is at can show.
     */
    private static final double DEVICE_REACH = 32.0;

    private static final List<PuzzleSolver> PUZZLES = List.of(
            new ThreeWeirdos(), new Quiz(), new TicTacToe(), new HigherOrLower(), new CreeperBeams(),
            new TeleportMaze(), new WaterBoard(), new Boulder(), new IceFill(), new IcePath());
    private static final List<DungeonSolver> DEVICES = List.of(
            new SimonSays(), new ArrowAlign(), new LightsDevice(), new Sharpshooter());
    private static final List<DungeonSolver> ALL;

    static {
        List<DungeonSolver> all = new ArrayList<>(PUZZLES);
        all.addAll(DEVICES);
        ALL = List.copyOf(all);
    }

    private static final boolean[] WAS_ENABLED = new boolean[ALL.size()];
    /** The solvers ticked this tick; they draw. */
    private static final List<DungeonSolver> RUNNING = new ArrayList<>();
    /** The solvers events go to: the running ones, and puzzle solvers outside the grid in a run. */
    private static final List<DungeonSolver> LISTENING = new ArrayList<>();
    private static final SolverDraw DRAW = new SolverDraw();
    /** Solvers whose failure was logged already; one line each is enough. */
    private static final Set<DungeonSolver> REPORTED = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private DungeonSolvers() {}

    public static void register() {
        // First, so a tick sees this tick's room.
        DungeonRooms.register();
        ClientTickEvents.END_CLIENT_TICK.register(DungeonSolvers::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
        // A line another mod hides (puzzle and boss lines often are) still arrives here.
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(DungeonSolvers::submit);
    }

    /** World change or disconnect: every solver and every piece of shared state starts over. */
    public static void reset() {
        for (DungeonSolver solver : ALL) {
            try {
                solver.reset();
            } catch (RuntimeException e) {
                report(solver, "reset", e);
            }
        }
        RUNNING.clear();
        LISTENING.clear();
        DRAW.clear();
        DungeonRooms.reset();
        PuzzleTab.reset();
        GoldorPhase.reset();
        TerminalSession.reset();
        DungeonDebug.reset();
    }

    /** Every solver, puzzles first; for the debug command. */
    public static List<DungeonSolver> all() {
        return ALL;
    }

    /** The solvers that ran this tick; for the debug command. */
    public static List<DungeonSolver> running() {
        return Collections.unmodifiableList(RUNNING);
    }

    private static boolean anyMasterOn() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.dungeonSolversEnabled || cfg.terminalSolversEnabled || cfg.deviceSolversEnabled;
    }

    // --- tick ---

    private static void tick(Minecraft mc) {
        TerminalSession.tick(mc);
        DungeonDebug.tick(mc);
        RUNNING.clear();
        LISTENING.clear();

        // Switched off since the last tick: forget everything, so switching back on starts clean.
        for (int i = 0; i < ALL.size(); i++) {
            DungeonSolver solver = ALL.get(i);
            boolean on = solver.enabled();
            if (WAS_ENABLED[i] && !on) {
                try {
                    solver.reset();
                } catch (RuntimeException e) {
                    report(solver, "reset", e);
                }
            }
            WAS_ENABLED[i] = on;
        }

        if (mc.level == null || mc.player == null || !SkyblockUtils.isOnSkyblock() || !DungeonState.inDungeon()) return;
        long t = AlpakaPerf.begin(AlpakaPerf.Section.DUNGEON_SOLVERS_TICK);
        try {
            long now = Util.getMillis();
            if (!DungeonRooms.inBossRoom()) {
                tickPuzzles(mc, now);
            } else if (GoldorPhase.inP3()) {
                SolverContext ctx = new SolverContext(mc, mc.level, mc.player, null, now);
                for (int i = 0; i < DEVICES.size(); i++) {
                    DungeonSolver device = DEVICES.get(i);
                    if (!WAS_ENABLED[PUZZLES.size() + i]) continue;
                    LISTENING.add(device);
                    if (tickOne(device, ctx)) RUNNING.add(device);
                }
            }
        } finally {
            AlpakaPerf.end(AlpakaPerf.Section.DUNGEON_SOLVERS_TICK, t);
        }
    }

    private static void tickPuzzles(Minecraft mc, long now) {
        if (!AlpakaConfig.instance.dungeonSolversEnabled) return;
        PuzzleTab.Parsed tab = PuzzleTab.current();
        boolean inRoomGrid = DungeonRooms.active();
        RoomFrame frame = DungeonRooms.current();
        SolverContext ctx = new SolverContext(mc, mc.level, mc.player, frame, now);
        for (int i = 0; i < PUZZLES.size(); i++) {
            PuzzleSolver puzzle = PUZZLES.get(i);
            if (!WAS_ENABLED[i]) continue;
            // Solved or failed, by anyone: nothing left to show. The state stays, in case an
            // Architect's First Draft brings a failed puzzle back.
            PuzzleTab.Status status = tab.statusOf(puzzle.puzzle());
            if (status != null && status.done()) continue;
            LISTENING.add(puzzle);
            if (inRoomGrid && tickOne(puzzle, ctx)) RUNNING.add(puzzle);
        }
    }

    private static boolean tickOne(DungeonSolver solver, SolverContext ctx) {
        try {
            solver.tick(ctx);
            return true;
        } catch (RuntimeException e) {
            report(solver, "tick", e);
            return false;
        }
    }

    // --- events ---

    private static void onChat(Component message) {
        if (!anyMasterOn() || !DungeonState.inDungeon()) return;
        String plain = SkyblockUtils.cleanColor(message.getString());
        if (plain.isEmpty()) return;
        GoldorPhase.onChat(plain);
        TerminalSession.onChat(plain);
        for (DungeonSolver solver : LISTENING) {
            try {
                solver.onChat(plain);
            } catch (RuntimeException e) {
                report(solver, "onChat", e);
            }
        }
    }

    /**
     * A block the server is about to change, from ClientLevelMixin; every block of every update
     * comes through here, so it returns at once while no solver listens.
     */
    public static void onServerBlockChange(ClientLevel level, BlockPos pos, BlockState newState) {
        if (LISTENING.isEmpty()) return;
        BlockState oldState = level.getBlockState(pos);
        // A section update reuses one mutable position for all its blocks.
        BlockPos at = pos.immutable();
        for (int i = 0; i < LISTENING.size(); i++) {
            DungeonSolver solver = LISTENING.get(i);
            try {
                solver.onBlockChange(at, oldState, newState);
            } catch (RuntimeException e) {
                report(solver, "onBlockChange", e);
            }
        }
    }

    /** The server moved the player; from ClientPacketListenerTeleportMixin, after vanilla applied it. */
    public static void onServerTeleport(Vec3 pos, float yRot, float xRot) {
        if (LISTENING.isEmpty()) return;
        for (DungeonSolver solver : List.copyOf(LISTENING)) {
            try {
                solver.onServerTeleport(pos, yRot, xRot);
            } catch (RuntimeException e) {
                report(solver, "onServerTeleport", e);
            }
        }
    }

    /** The player's own main-hand right-click on a block went through; from MultiPlayerGameModeMixin. */
    public static void onOwnBlockUse(BlockPos pos, InteractionHand hand) {
        if (LISTENING.isEmpty()) return;
        BlockPos at = pos.immutable();
        for (DungeonSolver solver : List.copyOf(LISTENING)) {
            try {
                solver.onOwnBlockUse(at, hand);
            } catch (RuntimeException e) {
                report(solver, "onOwnBlockUse", e);
            }
        }
    }

    /** The player's own main-hand right-click on an entity; from MultiPlayerGameModeMixin. */
    public static void onOwnEntityUse(Entity target, InteractionHand hand) {
        if (LISTENING.isEmpty()) return;
        for (DungeonSolver solver : List.copyOf(LISTENING)) {
            try {
                solver.onOwnEntityUse(target, hand);
            } catch (RuntimeException e) {
                report(solver, "onOwnEntityUse", e);
            }
        }
    }

    // --- drawing ---

    private static void submit(LevelRenderContext context) {
        if (RUNNING.isEmpty()) return;
        long t = AlpakaPerf.begin(AlpakaPerf.Section.DUNGEON_SOLVERS);
        try {
            DRAW.clear();
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            AABB cell = DungeonRooms.cellBounds();
            net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            AABB nearby = new AABB(player.getX() - DEVICE_REACH, -64, player.getZ() - DEVICE_REACH,
                    player.getX() + DEVICE_REACH, 320, player.getZ() + DEVICE_REACH);
            for (DungeonSolver solver : RUNNING) {
                if (solver instanceof PuzzleSolver) {
                    // A puzzle solver draws in the player's room and nowhere else.
                    if (cell == null) continue;
                    DRAW.clip(cell.inflate(CLIP_MARGIN));
                } else {
                    // A device solver draws at the device the player is at and nowhere else.
                    DRAW.clip(nearby);
                }
                try {
                    solver.render(DRAW, partialTick);
                } catch (RuntimeException e) {
                    report(solver, "render", e);
                }
            }
            DRAW.clip(null);
            DRAW.submit(context);
        } finally {
            AlpakaPerf.end(AlpakaPerf.Section.DUNGEON_SOLVERS, t);
        }
    }

    /** How many marks the last frame held; for the debug command. */
    public static int lastFrameSize() {
        return DRAW.size();
    }

    // --- failures ---

    /** Logs a solver's exception once and resets it, so one broken solver leaves the game and the others running. */
    private static void report(DungeonSolver solver, String where, RuntimeException e) {
        if (REPORTED.add(solver)) {
            AlpakaAddons.LOGGER.error("Dungeon solver {} failed in {}; it starts over", solver.name(), where, e);
        }
        if (!where.equals("reset")) {
            try {
                solver.reset();
            } catch (RuntimeException ignored) {
                // Already reported; nothing more to do for it this tick.
            }
        }
    }
}
