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
 * <h2>Pending clicks</h2>
 *
 * The solution describes what the server confirmed (design.md 4.7). A clicked slot is "pending"
 * until the server sends that slot again, or {@link #PENDING_TIMEOUT_MS} passes. Until then it is
 * read as it was before the click: vanilla's own handling of a click already changes the chest on
 * the client - the item goes onto the cursor, or a carried one into the slot - and none of that is
 * what the server says.
 *
 * Telling the server's update from vanilla's own change: every update from the server puts a new
 * ItemStack into the slot, while vanilla's handling of a click leaves its own stack behind. So
 * right after vanilla handled a click, the stack the slot holds is noted, and the next different
 * one is the server's answer. "Right after" is exact for both windows: the custom window reports a
 * click once it went out ({@link #onClickSent}); the vanilla chest decides at slotClicked's head
 * ({@link #vanillaClickAllowed}, which reads the slot as it is before the click) and reports the
 * click right after vanilla handed it to the server ({@link #onVanillaClickSent}). A click another
 * mod cancels in between is never recorded, so it neither counts for the rate guard nor leaves a
 * pending mark. Several answers for one slot within one frame read as one; the rest then expire.
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
    /**
     * Whether the server's pings were arriving when the terminal opened. Decided once: a server
     * that freezes after the terminal opened stops its pings too, and its first-click check pauses
     * with it, so the tick half of the first-click guard must not fall away then.
     */
    private final boolean pingsLiveAtOpen;
    private final boolean continued;
    private final State state;

    /** A session on a locally filled chest for the screenshot tour; held by its screen, not by the open menu. */
    private boolean preview = false;
    private long lastClickMs = Long.MIN_VALUE / 2;
    private int clickCount = 0;
    private Refusal lastRefusal = null;

    private long signature = 0L;
    private boolean solvedOnce = false;
    private List<TerminalItem> items = List.of();
    private TerminalSolution solution;
    private long refusalSignature = 0L;
    private TerminalSolution refusalSolution = null;

    /** Per slot: the stack it held right after vanilla's own handling of the last click on it. */
    private final ItemStack[] afterClick = new ItemStack[TerminalSolution.MAX_SLOTS];
    /** Per slot: whether {@link #afterClick} has been noted since the last click on it. */
    private final boolean[] noted = new boolean[TerminalSolution.MAX_SLOTS];

    private TerminalSession(TerminalKind kind, String title, AbstractContainerMenu menu, long openedAtMs,
                            long openedAtTick, boolean pingsLiveAtOpen, boolean continued, State state) {
        this.kind = kind;
        this.title = title;
        this.menu = menu;
        this.openedAtMs = openedAtMs;
        this.openedAtTick = openedAtTick;
        this.pingsLiveAtOpen = pingsLiveAtOpen;
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
                    previous.openedAtTick, previous.pingsLiveAtOpen, true, previous.state);
            next.lastClickMs = previous.lastClickMs;
            next.clickCount = previous.clickCount;
            // The new window's slots are new stacks; the clicks made in the old one are answered by it.
            next.state.clearPending();
            current = next;
            return next;
        }
        current = new TerminalSession(kind, plainTitle, menu, now, ServerTicks.count(), ServerTicks.live(now), false,
                new State());
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

    /**
     * For the screenshot tour: a session on a chest filled locally, which is not the player's open
     * menu, so the vanilla chest's overlay can be looked at without a server. It lasts as long as a
     * screen shows that chest; the guards count it as long open.
     */
    static TerminalSession openPreview(TerminalKind kind, AbstractContainerMenu menu, String plainTitle) {
        TerminalSession session = new TerminalSession(kind, plainTitle, menu, Util.getMillis() - 60_000L,
                ServerTicks.count() - 1_200L, false, false, new State());
        session.preview = true;
        current = session;
        replacing = null;
        return session;
    }

    /** Ends the session; the chest was closed or replaced by something else. */
    public static void close() {
        current = null;
        replacing = null;
        TerminalOverlay.reset();
    }

    /**
     * World change or disconnect. The vanilla chest's overlay lets go of the session too: through
     * its menu's inventory slots it reaches the player and with it the whole world just left.
     */
    public static void reset() {
        current = null;
        replacing = null;
        TerminalOverlay.reset();
    }

    /** Every client tick: ends the session once its chest is no longer the open menu. */
    public static void tick(Minecraft mc) {
        TerminalSession session = current;
        if (session == null) return;
        boolean shown = session.preview
                ? mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                        && screen.getMenu() == session.menu
                : mc.player != null && mc.player.containerMenu == session.menu;
        if (mc.player == null || !shown || !AlpakaConfig.instance.terminalSolversEnabled) {
            current = null;
            TerminalOverlay.reset();
        }
    }

    /**
     * A chat line (from DungeonSolvers): Hypixel's "This menu has been throttled! Please slow
     * down..." means the last clicks were ignored, so their pending marks go and nothing they did
     * counts as confirmed.
     */
    public static void onChat(String plain) {
        TerminalSession session = current;
        if (session != null && plain != null && plain.startsWith(THROTTLED_PREFIX)) session.onThrottled();
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

    /** How many slots the chest part has: menu slots below this are the terminal's, the rest the player's inventory. */
    public int chestSize() {
        return Math.min(Math.min(rows() * 9, menu.slots.size()), TerminalSolution.MAX_SLOTS);
    }

    /** When the terminal opened, on the {@code Util.getMillis()} clock. Kept across a carried-on reopen. */
    public long openedAtMs() {
        return openedAtMs;
    }

    /** {@link ServerTicks#count()} when the terminal opened. Kept across a carried-on reopen. */
    public long openedAtTick() {
        return openedAtTick;
    }

    /** Whether the server's pings were arriving when the terminal opened. Kept across a carried-on reopen. */
    public boolean pingsLiveAtOpen() {
        return pingsLiveAtOpen;
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

    /** The last click the guards refused here, for a moment's feedback; null before any. */
    public Refusal lastRefusal() {
        return lastRefusal;
    }

    /**
     * Records a click on {@code slot} that has just gone out: the custom window calls this once
     * {@code Outbound.menuClick} returned true, so vanilla has already handled it on the client.
     * A slot outside the chest part only counts for the rate guard.
     */
    public void onClickSent(int slot, int button, long nowMs) {
        record(slot, button, nowMs, inChest(slot) ? slotStack(slot) : null, before(slot));
        if (inChest(slot)) note(slot);
    }

    /**
     * A terminal click on {@code slot} the vanilla chest let through at slotClicked's head, before
     * vanilla handled it: the slot as it reads now, for {@link #onVanillaClickSent} to record once
     * the click really went out. Records nothing by itself.
     */
    VanillaClick vanillaClickAllowed(int slot, int button, long nowMs) {
        if (!inChest(slot)) return new VanillaClick(slot, button, nowMs, null, null);
        return new VanillaClick(slot, button, nowMs, slotStack(slot), before(slot));
    }

    /**
     * The vanilla chest's version of {@link #onClickSent}: the click {@link #vanillaClickAllowed}
     * let through has just been handed to the server, and vanilla has handled it on the client.
     */
    void onVanillaClickSent(VanillaClick click) {
        record(click.slot(), click.button(), click.atMs(), click.stackBefore(), click.before());
        if (inChest(click.slot())) note(click.slot());
    }

    /** Another kind of click went out (a number key, the drop key, a drag): only the rate guard needs it. */
    void onOtherClickSent(long nowMs) {
        lastClickMs = nowMs;
        clickCount++;
    }

    /** The guards refused a click on {@code slot}; nothing was sent. */
    public void onRefused(int slot, TerminalClicks.Verdict verdict, long nowMs) {
        lastRefusal = new Refusal(slot, verdict, nowMs);
    }

    /** Whether {@code slot} was clicked and the server has not answered for it yet. */
    public boolean pending(int slot, long nowMs) {
        refresh(nowMs);
        return state.isPending(slot);
    }

    /** Every pending slot. */
    public Set<Integer> pendingSlots(long nowMs) {
        refresh(nowMs);
        return state.pending();
    }

    /** Drops every pending mark, and what the clicks since then counted for, as after a throttle notice. */
    public void onThrottled() {
        state.throttled(Util.getMillis());
    }

    /** The chest part as last read, one entry per slot from 0; the slot number is the index. */
    public List<TerminalItem> items() {
        refresh(Util.getMillis());
        return items;
    }

    /** The solution for the chest as it is now; worked out again only when its contents changed. */
    public TerminalSolution solution() {
        refresh(Util.getMillis());
        return solution;
    }

    /**
     * The solution the wrong-click refusal goes by: pending clicks count as done, whether or not
     * Predict Clicks is on (design.md 4.7), so that a quick right click after a right click that is
     * still on its way is not taken for a wrong one.
     */
    public TerminalSolution refusalSolution() {
        refresh(Util.getMillis());
        if (state.predict) return solution;
        if (refusalSolution == null || refusalSignature != signature) {
            refusalSolution = TerminalSolver.solve(kind, title, items, state, true);
            refusalSignature = signature;
        }
        return refusalSolution;
    }

    /** Brings pending clicks and the solution up to date with the chest. Cheap when nothing changed. */
    void refresh(long nowMs) {
        settleAnswers();
        state.expire(nowMs);
        boolean predict = AlpakaConfig.instance.terminalPredictClicks;
        if (predict != state.predict) {
            state.predict = predict;
            state.changed();
        }
        long content = contentSignature();
        if (content * 31 + state.version != signature || !solvedOnce) {
            items = snapshot();
            solution = TerminalSolver.solve(kind, title, items, state);
            solvedOnce = true;
            // After solving: locking Change All to Same Color's target counts as a change of the
            // state, but this solution already has it.
            signature = content * 31 + state.version;
        }
    }

    // --- clicks ---

    private boolean inChest(int slot) {
        return slot >= 0 && slot < chestSize();
    }

    private void record(int slot, int button, long nowMs, ItemStack stackBefore, TerminalItem before) {
        lastClickMs = nowMs;
        clickCount++;
        if (!inChest(slot) || before == null) return;
        state.add(new Pending(slot, button, nowMs, stackBefore, before));
        noted[slot] = false;
    }

    /**
     * The slot as the server last had it: the reading taken just before the click (the guards
     * refreshed it), which for a slot that is already pending is what it read before that click.
     * Null outside the chest part.
     */
    private TerminalItem before(int slot) {
        if (!inChest(slot)) return null;
        return slot < items.size() ? items.get(slot) : TerminalItem.of(slot, slotStack(slot));
    }

    private void note(int slot) {
        afterClick[slot] = slotStack(slot);
        noted[slot] = true;
    }

    /**
     * A pending slot whose stack is no longer the one vanilla left there holds the server's answer;
     * it answers the oldest click on that slot. An empty slot is not an answer (Hypixel sends blank
     * updates in between).
     */
    private void settleAnswers() {
        if (state.pending.isEmpty()) return;
        for (int slot : state.pending()) {
            if (!noted[slot]) continue;
            ItemStack now = slotStack(slot);
            if (now.isEmpty() || now == afterClick[slot]) continue;
            afterClick[slot] = now;
            state.answer(slot, TerminalItem.of(slot, now));
        }
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
        int size = chestSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            h = h * 31 + System.identityHashCode(stack);
            h = h * 31 + stack.getCount();
        }
        return h;
    }

    private List<TerminalItem> snapshot() {
        int size = chestSize();
        List<TerminalItem> read = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Pending pending = state.firstPending(i);
            // Clicked and not answered yet: as the server last had it, not as vanilla's handling left it.
            read.add(pending != null ? pending.item() : TerminalItem.of(i, menu.getSlot(i).getItem()));
        }
        return Collections.unmodifiableList(read);
    }

    /**
     * A click waiting for the server: its slot and button, when it went out, the slot's stack at
     * that moment (null in tests) and how the slot read as the server last had it.
     */
    public record Pending(int slot, int button, long atMs, ItemStack stackBefore, TerminalItem item) {}

    /** A click the guards refused, for feedback. */
    public record Refusal(int slot, TerminalClicks.Verdict verdict, long atMs) {}

    /**
     * A vanilla-chest click let through and not yet sent: its slot and button, when, and the slot
     * as it read before vanilla handled the click (null outside the chest part).
     */
    record VanillaClick(int slot, int button, long atMs, ItemStack stackBefore, TerminalItem before) {}

    /**
     * What the solver may use besides the chest: the slots this session clicked, the clicks still
     * pending, the server's answers to them, a locked Change All to Same Color target, and whether
     * Predict Clicks is on. Public so tests can build one.
     */
    public static final class State {
        private final LinkedHashSet<Integer> clicked = new LinkedHashSet<>();
        /** Unanswered clicks, oldest first; a slot clicked twice is in it twice. */
        private final List<Pending> pending = new ArrayList<>();
        /** Per slot, how the server answered the player's last click on it. */
        private final Map<Integer, Answer> answered = new HashMap<>();
        private int rubixTarget = -1;
        private long rubixSlots = 0L;
        private boolean predict = false;
        private long version = 0L;

        public State() {}

        /** Slots this session sent a click for, in order. */
        public Set<Integer> clicked() {
            return Collections.unmodifiableSet(clicked);
        }

        /** Slots clicked and not yet answered by the server, oldest click first. */
        public Set<Integer> pending() {
            Set<Integer> slots = new LinkedHashSet<>();
            for (Pending p : pending) slots.add(p.slot());
            return Collections.unmodifiableSet(slots);
        }

        public boolean isPending(int slot) {
            return firstPending(slot) != null;
        }

        /** Unanswered clicks on {@code slot}. */
        public int pendingCount(int slot) {
            int n = 0;
            for (Pending p : pending) {
                if (p.slot() == slot) n++;
            }
            return n;
        }

        /** Change All to Same Color: the steps unanswered clicks on {@code slot} make, +1 per left click, -1 per right. */
        public int pendingSteps(int slot) {
            int steps = 0;
            for (Pending p : pending) {
                if (p.slot() == slot) steps += p.button() == 1 ? -1 : 1;
            }
            return steps;
        }

        /**
         * How the slot read when the server answered the player's click on it, or null when it has
         * not (yet). What Starts With and Select All go by for items that always glint.
         */
        public TerminalItem confirmedAs(int slot) {
            Answer answer = answered.get(slot);
            return answer == null ? null : answer.item();
        }

        /** Change All to Same Color: the locked target color's index, or -1 while none is locked. */
        public int rubixTarget() {
            return rubixTarget;
        }

        /** The slots that held panes when the target was locked, one bit per slot. */
        public long rubixSlots() {
            return rubixSlots;
        }

        public void lockRubixTarget(int target) {
            lockRubixTarget(target, rubixSlots);
        }

        /** Locks the target, noting which slots held panes then. */
        public void lockRubixTarget(int target, long paneSlots) {
            if (rubixTarget != target || rubixSlots != paneSlots) {
                rubixTarget = target;
                rubixSlots = paneSlots;
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

        /** A pending left click. */
        public State withPending(int slot, long atMs) {
            return withPending(slot, 0, atMs);
        }

        public State withPending(int slot, int button, long atMs) {
            add(new Pending(slot, button, atMs, null, TerminalItem.empty(slot)));
            return this;
        }

        /** The server answered the click on {@code slot} with {@code as}. */
        public State withConfirmed(int slot, TerminalItem as) {
            clicked.add(slot);
            answered.put(slot, new Answer(as, 0L));
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

        // What the session does with clicks.

        void add(Pending click) {
            clicked.add(click.slot());
            pending.add(click);
            changed();
        }

        Pending firstPending(int slot) {
            for (Pending p : pending) {
                if (p.slot() == slot) return p;
            }
            return null;
        }

        /**
         * The server answered the oldest click on {@code slot} with {@code now}. The clicks on it
         * still waiting now start from that answer. False when nothing was pending there.
         */
        boolean answer(int slot, TerminalItem now) {
            Pending first = firstPending(slot);
            if (first == null) return false;
            pending.remove(first);
            answered.put(slot, new Answer(now, first.atMs()));
            for (int i = 0; i < pending.size(); i++) {
                Pending p = pending.get(i);
                if (p.slot() == slot) pending.set(i, new Pending(p.slot(), p.button(), p.atMs(), p.stackBefore(), now));
            }
            changed();
            return true;
        }

        /** Drops clicks that waited longer than {@link #PENDING_TIMEOUT_MS}; they may never be answered. */
        boolean expire(long nowMs) {
            if (pending.isEmpty()) return false;
            boolean dropped = pending.removeIf(p -> nowMs - p.atMs() > PENDING_TIMEOUT_MS);
            if (dropped) changed();
            return dropped;
        }

        /**
         * A throttle notice: the clicks of the last moment were ignored. Their pending marks go,
         * and an answer to one of them - the server still sends the slot back, unchanged - does
         * not count as a confirmed click.
         */
        void throttled(long nowMs) {
            boolean changed = !pending.isEmpty();
            pending.clear();
            changed |= answered.values().removeIf(a -> nowMs - a.clickAtMs() <= PENDING_TIMEOUT_MS);
            if (changed) changed();
        }

        void clearPending() {
            if (pending.isEmpty()) return;
            pending.clear();
            changed();
        }

        private record Answer(TerminalItem item, long clickAtMs) {}
    }
}
