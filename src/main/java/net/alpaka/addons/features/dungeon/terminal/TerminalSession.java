package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.DungeonState;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One terminal while its chest is open: when it opened (in time and in server ticks, for the
 * first-click guard), the clicks the player made in it, and its solution, kept up to date with
 * the chest's contents.
 *
 * Opened when Hypixel opens a chest with a terminal title, in the vanilla screen
 * ({@link #onVanillaMenuOpened}) or in the custom window (TerminalScreen calls {@link #open});
 * ended once that chest is no longer the open menu. Since 0.27.1 a terminal stays one window
 * whose slots change in place. Should Hypixel replace the open window with the same terminal right
 * after a click, as it did before, the session carries on rather than starting over: the
 * first-click guard is not armed again for what is one terminal to the player (compliance.md T3).
 * That needs the new window to arrive while the old one is still the open menu
 * ({@link #beforeMenuOpens}); anything else - the server closing it first, the player closing it
 * and opening it again - is a new session with the guard armed.
 *
 * The solution describes what the server confirmed. A clicked cell is "pending" until the
 * server's update for its slot arrives, or {@link #PENDING_TIMEOUT_MS} passes; a vanilla click
 * moves the item onto the cursor at once, so while it is pending the slot is read as it was
 * before the click instead of as empty.
 *
 * Client thread only.
 */
public final class TerminalSession {

    /** A click's pending mark goes after this long without an update for its slot (Odin's value). */
    public static final long PENDING_TIMEOUT_MS = 600L;
    /** A same-title reopen this soon after a click carries on the session. */
    public static final long REOPEN_WINDOW_MS = 1000L;
    /** How Hypixel's answer to clicking too fast starts; the clicks it ignored will not be confirmed. */
    public static final String THROTTLED_PREFIX = "This menu has been throttled!";

    private static TerminalSession current = null;
    /**
     * The session whose window was still the open menu when the next window started to open; only
     * such a session may be carried on. Set at MenuScreens.create's head, used up by {@link #open}.
     */
    private static TerminalSession replacing = null;

    private final TerminalKind kind;
    private final String title;
    private AbstractContainerMenu menu;
    private final long openedAtMs;
    private final long openedAtTick;
    private final boolean continued;
    private final State state;

    private long lastClickMs = Long.MIN_VALUE / 2;
    private int clickCount = 0;
    private long signature = 0L;
    private List<TerminalItem> items = List.of();
    private TerminalSolution solution;

    private TerminalSession(TerminalKind kind, String title, AbstractContainerMenu menu, long openedAtMs,
                            long openedAtTick, boolean continued, State state) {
        this.kind = kind;
        this.title = title;
        this.menu = menu;
        this.openedAtMs = openedAtMs;
        this.openedAtTick = openedAtTick;
        this.continued = continued;
        this.state = state;
        this.solution = TerminalSolution.empty(kind);
    }

    // --- the open session ---

    /** The terminal open now, or null. */
    public static TerminalSession current() {
        return current;
    }

    /** The session of {@code menu}, or null when that menu is not an open terminal. */
    public static TerminalSession current(AbstractContainerMenu menu) {
        TerminalSession session = current;
        return session != null && menu != null && session.menu == menu ? session : null;
    }

    /**
     * Starts the session for a terminal chest that is opening, or carries on the current one when
     * Hypixel puts the same terminal in its place right after a click. Returns the session.
     */
    public static TerminalSession open(TerminalKind kind, AbstractContainerMenu menu, String plainTitle) {
        long now = Util.getMillis();
        TerminalSession previous = current;
        TerminalSession replaced = replacing;
        replacing = null;
        if (previous != null && previous.menu == menu) return previous;
        if (previous != null && previous == replaced && previous.kind == kind && previous.title.equals(plainTitle)
                && now - previous.lastClickMs <= REOPEN_WINDOW_MS) {
            TerminalSession next = new TerminalSession(kind, plainTitle, menu, previous.openedAtMs,
                    previous.openedAtTick, true, previous.state);
            next.lastClickMs = previous.lastClickMs;
            next.clickCount = previous.clickCount;
            next.state.pending.clear();
            next.state.changed();
            current = next;
            return next;
        }
        current = new TerminalSession(kind, plainTitle, menu, now, ServerTicks.count(), false, new State());
        return current;
    }

    /**
     * From MenuScreensMixin, before any screen is made for a window the server opens: notes whether
     * the terminal open now is being replaced while it is still open, the one case in which
     * {@link #open} may carry its session on. Reads only; cheap when no terminal is open.
     */
    public static void beforeMenuOpens(Minecraft mc) {
        TerminalSession session = current;
        replacing = session != null && mc.player != null && mc.player.containerMenu == session.menu ? session : null;
    }

    /**
     * From MenuScreensMixin, after vanilla opened a chest screen: starts the session when it is a
     * terminal this player has a solver switched on for, in a run.
     */
    public static void onVanillaMenuOpened(Minecraft mc, int containerId, Component title) {
        if (!AlpakaConfig.instance.terminalSolversEnabled || mc.player == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;
        String plain = SkyblockUtils.cleanColor(title.getString());
        TerminalKind kind = TerminalKind.byTitle(plain);
        if (kind == null || !kind.enabled() || !DungeonState.inDungeon()) return;
        open(kind, menu, plain);
    }

    /** Ends the session; the chest was closed or replaced by something else. */
    public static void close() {
        current = null;
        replacing = null;
    }

    /** World change or disconnect. */
    public static void reset() {
        current = null;
        replacing = null;
    }

    /** Every client tick: ends the session once its chest is no longer the open menu. */
    public static void tick(Minecraft mc) {
        TerminalSession session = current;
        if (session == null) return;
        if (mc.player == null || mc.player.containerMenu != session.menu || !AlpakaConfig.instance.terminalSolversEnabled) {
            current = null;
        }
    }

    /** A chat line: a throttle notice means the last clicks will not be confirmed. */
    public static void onChat(String plain) {
        TerminalSession session = current;
        if (session != null && plain.startsWith(THROTTLED_PREFIX)) session.onThrottled();
    }

    // --- this session ---

    public TerminalKind kind() {
        return kind;
    }

    /** The chest title, formatting stripped. */
    public String title() {
        return title;
    }

    public AbstractContainerMenu menu() {
        return menu;
    }

    /** The chest's rows as the client has it (its real size, not what the kind expects). */
    public int rows() {
        return menu instanceof ChestMenu chest ? chest.getRowCount() : kind.rows();
    }

    /** When the terminal opened, on the {@code Util.getMillis()} clock. Kept across a carried-on reopen. */
    public long openedAtMs() {
        return openedAtMs;
    }

    /** {@link ServerTicks#count()} when the terminal opened. Kept across a carried-on reopen. */
    public long openedAtTick() {
        return openedAtTick;
    }

    /** Whether this session carries on one Hypixel replaced right after a click. */
    public boolean continued() {
        return continued;
    }

    public State state() {
        return state;
    }

    /** When the last click went out, on the {@code Util.getMillis()} clock; far in the past before any. */
    public long lastClickMs() {
        return lastClickMs;
    }

    public int clickCount() {
        return clickCount;
    }

    /**
     * Records a click that was just sent for this terminal: by the custom window after
     * {@code Outbound.menuClick} returned true, or by the overlay when it lets a vanilla click through.
     */
    public void onClickSent(int slot, int button, long nowMs) {
        lastClickMs = nowMs;
        clickCount++;
        ItemStack before = slotStack(slot);
        TerminalItem item = slot >= 0 && slot < items.size() ? items.get(slot) : TerminalItem.of(slot, before);
        state.clicked.add(slot);
        state.pending.put(slot, new Pending(slot, button, nowMs, before, item));
        state.changed();
    }

    /** Whether {@code slot} was clicked and the server has not answered for it yet. */
    public boolean pending(int slot, long nowMs) {
        expire(nowMs);
        return state.pending.containsKey(slot);
    }

    /** Every pending slot. */
    public Set<Integer> pendingSlots(long nowMs) {
        expire(nowMs);
        return Collections.unmodifiableSet(state.pending.keySet());
    }

    /** Drops every pending mark, as after a throttle notice. */
    public void onThrottled() {
        if (state.pending.isEmpty()) return;
        state.pending.clear();
        state.changed();
    }

    /** The chest part as last read, one entry per slot from 0; the slot number is the index. */
    public List<TerminalItem> items() {
        solution();
        return items;
    }

    /** The solution for the chest as it is now; worked out again only when its contents changed. */
    public TerminalSolution solution() {
        long now = Util.getMillis();
        expire(now);
        boolean predict = AlpakaConfig.instance.terminalPredictClicks;
        if (predict != state.predict) {
            state.predict = predict;
            state.changed();
        }
        long sig = contentSignature() * 31 + state.version;
        if (sig != signature || items.isEmpty()) {
            signature = sig;
            items = snapshot();
            solution = TerminalSolver.solve(kind, title, items, state);
        }
        return solution;
    }

    // --- reading the chest ---

    private ItemStack slotStack(int slot) {
        if (slot < 0 || slot >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.getSlot(slot).getItem();
    }

    /**
     * A fingerprint of the chest part. Every update from the server is a new ItemStack, so the
     * stacks' identities change whenever anything does; reading them is far cheaper than parsing.
     */
    private long contentSignature() {
        long h = 17L;
        int size = Math.min(rows() * 9, menu.slots.size());
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            h = h * 31 + System.identityHashCode(stack);
            h = h * 31 + stack.getCount();
        }
        return h;
    }

    private List<TerminalItem> snapshot() {
        int size = Math.min(rows() * 9, menu.slots.size());
        List<TerminalItem> read = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            Pending pending = state.pending.get(i);
            // The vanilla click put this item on the cursor; until the server answers, it is still there.
            read.add(stack.isEmpty() && pending != null ? pending.item() : TerminalItem.of(i, stack));
        }
        return Collections.unmodifiableList(read);
    }

    /**
     * Retires pending clicks: confirmed once the slot holds a new non-empty stack from the server,
     * dropped once {@link #PENDING_TIMEOUT_MS} passed.
     */
    private void expire(long nowMs) {
        if (state.pending.isEmpty()) return;
        boolean changed = state.pending.values().removeIf(p -> {
            if (nowMs - p.atMs() > PENDING_TIMEOUT_MS) return true;
            ItemStack now = slotStack(p.slot());
            return !now.isEmpty() && now != p.stackBefore();
        });
        if (changed) state.changed();
    }

    /** A click waiting for the server: the slot's stack (null in tests) and how it read before the click. */
    public record Pending(int slot, int button, long atMs, ItemStack stackBefore, TerminalItem item) {}

    /**
     * What the solver may use besides the chest: the slots this session clicked, the clicks still
     * pending, a locked Change All to Same Color target, and whether Predict Clicks is on. Public
     * so tests can build one.
     */
    public static final class State {
        private final LinkedHashSet<Integer> clicked = new LinkedHashSet<>();
        private final Map<Integer, Pending> pending = new HashMap<>();
        private int rubixTarget = -1;
        private boolean predict = false;
        private long version = 0L;

        public State() {}

        /** Slots this session sent a click for, in order. */
        public Set<Integer> clicked() {
            return Collections.unmodifiableSet(clicked);
        }

        /** Slots clicked and not yet answered by the server. */
        public Set<Integer> pending() {
            return Collections.unmodifiableSet(pending.keySet());
        }

        public boolean isPending(int slot) {
            return pending.containsKey(slot);
        }

        /** Change All to Same Color: the locked target color's index, or -1 while none is locked. */
        public int rubixTarget() {
            return rubixTarget;
        }

        public void lockRubixTarget(int target) {
            if (rubixTarget != target) {
                rubixTarget = target;
                changed();
            }
        }

        public boolean predict() {
            return predict;
        }

        // For tests, which have no menu to click in.

        public State withClicked(int slot) {
            clicked.add(slot);
            changed();
            return this;
        }

        public State withPending(int slot, long atMs) {
            pending.put(slot, new Pending(slot, 0, atMs, null, TerminalItem.empty(slot)));
            changed();
            return this;
        }

        public State withPredict(boolean on) {
            predict = on;
            changed();
            return this;
        }

        /** Bumped on every change, so the session knows to solve again. */
        public long version() {
            return version;
        }

        void changed() {
            version++;
        }
    }
}
