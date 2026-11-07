package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Terminal practice ("/alpakaterminal"): the F7/M7 terminals in the custom terminal window,
 * anywhere, played against {@link TerminalPracticeBoard} instead of Hypixel.
 *
 * Nothing here talks to the server. The chest is a local container whose menu is never the
 * player's open menu, the window has no terminal session, and its clicks, closing included, end
 * here ({@link TerminalScreen#send}, {@link TerminalScreen#onClose}). A simulated ping only holds a
 * click back before the board takes it, so the window shows it pending as it would in a run.
 *
 * The solution is drawn as in a run: only for the terminals whose solver is switched on.
 */
public final class TerminalPractice {

    /** How long a solved terminal stays up, with its time, before the next one opens. */
    static final long NEXT_DELAY_MS = 1200L;
    public static final int MAX_PING_MS = 1000;

    private static List<TerminalPracticeBoard.Choice> startsWithItems;

    /** The terminal to practise, or null for a random one each time. */
    private final TerminalKind fixed;
    private final int pingMs;
    private final Random random = new Random();
    private final List<Delayed> delayed = new ArrayList<>();

    private TerminalPracticeBoard board;
    private TerminalSession.State state;
    private SimpleContainer chest;
    private long startedMs;
    private long solvedAtMs = -1L;
    private boolean newBest = false;

    private record Delayed(int slot, int button, long dueMs) {}

    private TerminalPractice(TerminalKind fixed, int pingMs) {
        this.fixed = fixed;
        this.pingMs = Math.max(0, Math.min(MAX_PING_MS, pingMs));
    }

    /** Opens practice for {@code kind} (null: a random terminal each round). False without a world to open it in. */
    public static boolean open(TerminalKind kind, int pingMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        new TerminalPractice(kind, pingMs).next();
        return true;
    }

    /** Deals the next terminal and shows it in a new window. */
    private void next() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        long now = Util.getMillis();
        TerminalKind kind = fixed != null ? fixed : randomKind();
        board = TerminalPracticeBoard.create(kind, random, startsWithItems(), now);
        state = new TerminalSession.State().withPredict(AlpakaConfig.instance.terminalPredictClicks);
        delayed.clear();
        solvedAtMs = -1L;
        newBest = false;

        int rows = kind.rows();
        chest = new SimpleContainer(rows * 9);
        for (int slot = 0; slot < board.size(); slot++) sync(slot);
        Inventory inventory = mc.player.getInventory();
        ChestMenu menu = new ChestMenu(type(rows), 9_300 + kind.ordinal(), inventory, chest, rows);
        mc.gui.setScreen(TerminalScreen.practice(menu, inventory, board.title(), kind, this));
        startedMs = now;
    }

    /** Another terminal than the last, so random practice does not deal the same one twice in a row. */
    private TerminalKind randomKind() {
        TerminalKind[] kinds = TerminalKind.values();
        TerminalKind last = board == null ? null : board.kind();
        TerminalKind kind;
        do {
            kind = kinds[random.nextInt(kinds.length)];
        } while (kind == last);
        return kind;
    }

    // ---- What the window asks -------------------------------------------------------------------

    /** The clicks and pending clicks the solver and the window go by. */
    TerminalSession.State state() {
        return state;
    }

    int pingMs() {
        return pingMs;
    }

    boolean solved() {
        return solvedAtMs >= 0;
    }

    /** The time so far, or the final time once solved. */
    long elapsedMs(long nowMs) {
        return Math.max(0L, (solved() ? solvedAtMs : nowMs) - startedMs);
    }

    boolean newBest() {
        return newBest;
    }

    /** The best time for {@code kind} at this ping, or -1. */
    long bestMs(TerminalKind kind) {
        Long best = bests().get(bestKey(kind));
        return best == null ? -1L : best;
    }

    /** The stored best times; a hand-edited config may have dropped the map. */
    private static Map<String, Long> bests() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (cfg.terminalPracticeBestMs == null) cfg.terminalPracticeBestMs = new HashMap<>();
        return cfg.terminalPracticeBestMs;
    }

    /** Best times are kept per ping, since a simulated ping makes every terminal slower. */
    private String bestKey(TerminalKind kind) {
        return pingMs == 0 ? kind.name() : kind.name() + "@" + pingMs;
    }

    // ---- Playing --------------------------------------------------------------------------------

    /** A click the player made in the window; it reaches the board after the simulated ping. */
    void click(int slot, int button, long nowMs) {
        if (solved() || slot < 0 || slot >= board.size()) return;
        state.add(new TerminalSession.Pending(slot, button, nowMs, null, board.item(slot)));
        if (pingMs == 0) apply(slot, button, nowMs);
        else delayed.add(new Delayed(slot, button, nowMs + pingMs));
    }

    /** Called every frame: due clicks and the moving pane of Click the Button on Time. */
    void update(long nowMs) {
        Iterator<Delayed> it = delayed.iterator();
        while (it.hasNext()) {
            Delayed click = it.next();
            if (click.dueMs() > nowMs) break;
            it.remove();
            if (board.advance(click.dueMs())) syncAll();
            apply(click.slot(), click.button(), click.dueMs());
        }
        if (board.advance(nowMs)) syncAll();
    }

    /** Every tick: once a solved terminal has shown its time long enough, the next one opens. */
    void nextIfDue(long nowMs) {
        if (solved() && nowMs - solvedAtMs >= NEXT_DELAY_MS) next();
    }

    /** The board takes the click, and answers it as the server would: the slot as it is now. */
    private void apply(int slot, int button, long atMs) {
        if (solved()) return;
        if (board.click(slot, button, atMs)) {
            if (board.kind().isMelody()) syncAll();
            else sync(slot);
        }
        state.answer(slot, board.item(slot));
        if (board.solved()) finish(atMs);
    }

    private void finish(long atMs) {
        solvedAtMs = atMs;
        delayed.clear();
        long time = atMs - startedMs;
        String key = bestKey(board.kind());
        Long best = bests().get(key);
        if (best == null || time < best) {
            newBest = best != null;
            bests().put(key, time);
            AlpakaConfig.save();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), newBest ? 2.0f : 1.5f, 0.6f));
        }
    }

    // ---- Items ----------------------------------------------------------------------------------

    private void syncAll() {
        for (int slot = 0; slot < board.size(); slot++) sync(slot);
    }

    private void sync(int slot) {
        chest.setItem(slot, stack(board.item(slot)));
    }

    /** The stack for a cell: the item under its English name, the count, and the glint Hypixel marks a click with. */
    static ItemStack stack(TerminalItem item) {
        if (item.empty()) return ItemStack.EMPTY;
        Item type = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(item.itemId()));
        if (type == null || type == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(type, Math.max(1, item.count()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(item.name()).withStyle(s -> s.withItalic(false)));
        if (item.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /**
     * What What Starts With picks from: every item in the game under its English name, written
     * from its id ("sugar_cane" -> "Sugar Cane"), leaving out panes (the filler is one) and the
     * operator-only items no survival chest holds.
     */
    private static List<TerminalPracticeBoard.Choice> startsWithItems() {
        if (startsWithItems != null) return startsWithItems;
        List<TerminalPracticeBoard.Choice> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (item == Items.AIR || id.contains("pane") || id.contains("command_block") || id.startsWith("structure_")
                    || id.startsWith("test_") || id.equals("jigsaw") || id.equals("barrier") || id.equals("debug_stick")
                    || id.equals("light") || id.equals("knowledge_book")) {
                continue;
            }
            items.add(new TerminalPracticeBoard.Choice(id, TerminalPracticeBoard.words(id)));
        }
        startsWithItems = List.copyOf(items);
        return startsWithItems;
    }

    private static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
