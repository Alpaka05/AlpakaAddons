package net.alpaka.addons.features.dungeon;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.device.GoldorPhase;
import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.features.dungeon.room.PuzzleTab;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.alpaka.addons.features.dungeon.terminal.ServerTicks;
import net.alpaka.addons.features.dungeon.terminal.TerminalItem;
import net.alpaka.addons.features.dungeon.terminal.TerminalKind;
import net.alpaka.addons.features.dungeon.terminal.TerminalSession;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.utils.DungeonState;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

/**
 * "/alpakadebug dungeon" and "/alpakadebug dungeon terminal": what the dungeon solvers see, so the
 * maintainer can check room frames, the tab list and the terminal layouts in a real run - the
 * server they are built on cannot join Hypixel. Both only read and print locally; nothing is sent.
 */
public final class DungeonDebug {

    /** How long a chest has to be open before it is written out, so its items have arrived. */
    private static final int DUMP_AFTER_TICKS = 10;

    private static boolean dumpTerminals = false;
    private static AbstractContainerMenu dumpMenu = null;
    private static int dumpMenuTicks = 0;
    private static boolean dumped = false;

    private DungeonDebug() {}

    /** World change or disconnect. The dump switch stays as it is. */
    public static void reset() {
        dumpMenu = null;
        dumpMenuTicks = 0;
        dumped = false;
    }

    // --- /alpakadebug dungeon ---

    /** Prints the run, the room, the tab list's puzzles and the solvers into chat. */
    public static void printRoom() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        say("§6--- Dungeon ---");
        boolean bossByChat = DungeonState.getInBossRoom();
        say(String.format(Locale.ROOT, "§7SkyBlock §f%s§7 · floor §f%s§7 · boss room §f%s%s",
                yes(SkyblockUtils.isOnSkyblock()), DungeonState.getFloor() == null ? "none" : DungeonState.getFloor(),
                yes(DungeonRooms.inBossRoom()),
                !bossByChat && DungeonRooms.inBossRoom() ? " §7(by position, the boss line was missed)" : ""));

        // Worked out now, whether or not the Solvers master is on; reading blocks is harmless.
        DungeonRooms.update(mc.level, player);
        int cellX = DungeonRooms.cellX();
        int cellZ = DungeonRooms.cellZ();
        if (cellX < 0) {
            say("§7Not in the room grid.");
        } else {
            RoomFrame frame = DungeonRooms.cellFrame();
            DungeonRooms.Probe probe = DungeonRooms.lastProbe();
            if (frame == null) {
                say(String.format(Locale.ROOT, "§7Cell §f%d,%d§7 · no frame (§f%s§7)", cellX, cellZ,
                        probe == DungeonRooms.Probe.NO_CLAY ? "no blue terracotta on the roof" : "chunks still loading"));
            } else {
                say(String.format(Locale.ROOT, "§7Cell §f%d,%d§7 · roof §f%d§7 · clay §f%s§7 at §f%d, %d§7 · §f%s%s",
                        cellX, cellZ, frame.roofY(), frame.corner(), frame.originX(), frame.originZ(), frame.rotation(),
                        DungeonRooms.onRoof() ? " §c(on the roof)" : ""));
                BlockPos feet = frame.toRel(player.blockPosition());
                say(String.format(Locale.ROOT, "§7You: rel §f%d, %d, %d", feet.getX(), feet.getY(), feet.getZ()));
                HitResult hit = mc.hitResult;
                if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos rel = frame.toRel(block.getBlockPos());
                    String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(block.getBlockPos()).getBlock()).getPath();
                    say(String.format(Locale.ROOT, "§7Looking at: rel §f%d, %d, %d§7 (%s, facing %s)",
                            rel.getX(), rel.getY(), rel.getZ(), id, frame.toRel(block.getDirection()).getName()));
                }
            }
        }

        PuzzleTab.Parsed tab = PuzzleTab.current();
        if (!tab.present()) {
            say("§7Tab list: no Puzzles section.");
        } else {
            StringBuilder line = new StringBuilder("§7Puzzles (" + tab.count() + "):");
            for (PuzzleTab.Entry entry : tab.list()) {
                line.append(" §f").append(entry.puzzle().tabName()).append("§7 ").append(entry.status().name().toLowerCase(Locale.ROOT));
                if (entry.player() != null) line.append(" (").append(entry.player()).append(')');
                line.append(',');
            }
            if (tab.undiscovered() > 0) line.append(" §f???§7 x").append(tab.undiscovered());
            say(line.toString());
        }

        if (GoldorPhase.inF7Boss()) {
            say(String.format(Locale.ROOT, "§7Boss phase by height §f%d§7 · section §f%d§7 · Goldor started §f%s",
                    GoldorPhase.phaseAt(player.getY()), GoldorPhase.sectionAt(player.getX(), player.getZ()),
                    yes(GoldorPhase.started())));
        }

        StringBuilder running = new StringBuilder();
        for (DungeonSolver solver : DungeonSolvers.running()) {
            if (!running.isEmpty()) running.append(", ");
            running.append(solver.name()).append(solver.claimsRoom() ? " §a(claims this room)§f" : "");
        }
        say("§7Running: §f" + (running.isEmpty() ? "none" : running) + "§7 · marks last frame: §f" + DungeonSolvers.lastFrameSize());
        for (DungeonSolver solver : DungeonSolvers.all()) {
            String state = solver.debugState();
            if (state != null) say("§8  " + solver.name() + ": " + state);
        }
        say("§7Server ticks: §f" + (ServerTicks.live(Util.getMillis()) ? "arriving" : "no pings"));
    }

    // --- /alpakadebug dungeon terminal ---

    /**
     * Switches the terminal dump on or off. While on, every terminal chest - and, in the F7/M7
     * boss, every chest - is written to the log half a second after it opens. Returns the new state.
     * A command cannot be typed while a chest is open, hence a switch rather than a one-off dump.
     */
    public static boolean toggleTerminalDump() {
        dumpTerminals = !dumpTerminals;
        reset();
        return dumpTerminals;
    }

    /** From DungeonSolvers' tick. */
    static void tick(Minecraft mc) {
        if (!dumpTerminals) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof ChestMenu chest)) {
            dumpMenu = null;
            return;
        }
        if (chest != dumpMenu) {
            dumpMenu = chest;
            dumpMenuTicks = 0;
            dumped = false;
        }
        if (dumped || ++dumpMenuTicks < DUMP_AFTER_TICKS) return;
        String title = SkyblockUtils.cleanColor(screen.getTitle().getString());
        TerminalKind kind = TerminalKind.byTitle(title);
        if (kind == null && !GoldorPhase.inF7Boss()) return;
        dumped = true;
        dump(chest, title, kind);
    }

    private static void dump(ChestMenu chest, String title, TerminalKind kind) {
        int rows = chest.getRowCount();
        AlpakaAddons.LOGGER.info("[dungeon terminal] title='{}' kind={} rows={} container={}", title, kind, rows, chest.containerId);
        int filled = 0;
        for (int i = 0; i < rows * 9; i++) {
            ItemStack stack = chest.getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            filled++;
            TerminalItem item = TerminalItem.of(i, stack);
            AlpakaAddons.LOGGER.info("[dungeon terminal]   slot {} (row {}, col {}): {} x{} '{}' foil={}",
                    i, i / 9, i % 9, item.itemId(), item.count(), item.name(), item.glint());
        }
        TerminalSession session = TerminalSession.current(chest);
        if (session != null) AlpakaAddons.LOGGER.info("[dungeon terminal]   solution: {}", session.solution());
        say(String.format(Locale.ROOT, "§7Wrote §f%s§7 (%s, %d rows, %d items) to the log.",
                title, kind == null ? "not a terminal" : kind.displayName(), rows, filled));
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }

    private static void say(String text) {
        SlayerDropTracker.sendModMessage(text);
    }
}
