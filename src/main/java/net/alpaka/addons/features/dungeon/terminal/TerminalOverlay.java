package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.function.Supplier;

/**
 * The terminal solution drawn on the vanilla chest screen, and the refusal of clicks there
 * (design.md 4.4 and 5.1). Called from AbstractContainerScreenMixin, only while the Terminal Solvers
 * master is on; every method returns at once for a screen whose menu has no
 * {@link TerminalSession}.
 *
 * Drawing: behind the item of each solution slot a fill in its color (TerminalPalette), over it
 * the label (a click count, a number), and a grey veil on a slot clicked but not answered yet.
 * With Hide Other Items the slots that are not part of the solution are drawn empty once the
 * chest is loaded; Click the Button on Time only loses its filler, so the panes the player times
 * the click by stay. Display only: the menu is never written to. The custom window's own grid
 * never passes through here, but a vanilla-drawn view inside it (its Classic View) does, guards
 * included.
 *
 * Clicks: {@link #allowSlotClick} sees every click vanilla is about to send from the screen,
 * whatever its kind, and refuses it (nothing is sent, vanilla does not even handle it) when
 * {@link TerminalClicks} says so. A click on a terminal cell gets every guard; any other click on
 * this window - a number key, the drop key, a click in the inventory below - only the first-click
 * and rate guards, since it reaches the terminal's window on the server all the same. A drag is
 * one decision, made when it starts, so that it is never sent in part; the "collect" vanilla adds
 * when a double click is released follows the click it belongs to when that one was refused. With
 * Block Wrong Clicks on, a collect on a terminal cell and every drag are refused outright, and
 * vanilla's shift double click, which would shift-click every matching pane, is never sent in a
 * terminal ({@link #allowShiftCollect}). A refused click is never turned into another one. A click
 * let through is recorded on the session only once vanilla handed it to the server
 * ({@link #afterSlotClickSent}), so one another mod cancels in between leaves no trace.
 */
public final class TerminalOverlay {

    /** Over a clicked cell the server has not answered yet: a neutral grey veil. */
    private static final int PENDING_VEIL = 0xA0707070;
    /** A refused click's slot gets a red frame for this long. */
    private static final long REFUSED_FLASH_MS = 300L;
    private static final int REFUSED_FRAME = 0xFFFF5555;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    /**
     * Over the items the solution does not ask for, or that are done: a dark veil, so the ones to
     * click stand out. Without it a red pane on its green fill reads as green - glass is
     * see-through - and looks just like the lime panes already done.
     */
    private static final int REST_VEIL = 0x99000000;

    // This frame's reading, so the chest is solved once per frame rather than once per slot. A new
    // GuiGraphicsExtractor is made for every frame.
    private static GuiGraphicsExtractor frame = null;
    private static TerminalSession frameSession = null;
    private static TerminalSolution frameSolution = null;
    private static TerminalPalette framePalette = null;
    private static boolean frameHide = false;
    private static long frameNow = 0L;

    private static final Sequence SEQUENCE = new Sequence();

    private TerminalOverlay() {}

    // --- drawing ---

    /**
     * Before vanilla draws a slot (pose already at the screen's corner, so slot.x/slot.y are right).
     * Returns true to skip vanilla's drawing of the item - Hide Other Items - in which case
     * {@link #overSlot} is not called for that slot either.
     */
    public static boolean underSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
        TerminalSession session = sessionOf(screen.getMenu(), slot);
        if (session == null) return false;
        read(session, graphics);
        int index = slot.index;
        if (frameHide && hidden(frameSolution, index)) {
            marks(graphics, session, slot);
            return true;
        }
        int fill = framePalette.fill(frameSolution, index);
        if (fill != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fill);
        return false;
    }

    /** After vanilla drew a slot's item: labels and marks that go on top of it. */
    public static void overSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
        TerminalSession session = sessionOf(screen.getMenu(), slot);
        if (session == null) return;
        read(session, graphics);
        int index = slot.index;
        if (resting(frameSolution, framePalette, index)) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, REST_VEIL);
        // Click in Order's number is the stack's own count, which vanilla already writes in the
        // corner; a second one in the middle would read as "44".
        String label = frameSolution.kind() == TerminalKind.NUMBERS ? null : frameSolution.label(index);
        if (label != null) {
            Font font = Minecraft.getInstance().font;
            int x = slot.x + 8 - font.width(label) / 2;
            int y = slot.y + 8 - font.lineHeight / 2;
            graphics.text(font, label, x, y, LABEL_COLOR, true);
        }
        marks(graphics, session, slot);
    }

    /** The open terminal, when {@code slot} is one of its chest slots and its solver is on. */
    private static TerminalSession sessionOf(AbstractContainerMenu menu, Slot slot) {
        TerminalSession session = TerminalSession.current(menu);
        if (session == null || slot == null || !session.kind().enabled()) return null;
        return slot.index >= 0 && slot.index < session.chestSize() ? session : null;
    }

    private static void read(TerminalSession session, GuiGraphicsExtractor graphics) {
        if (graphics == frame && session == frameSession) return;
        frame = graphics;
        frameSession = session;
        frameNow = Util.getMillis();
        frameSolution = session.solution();
        framePalette = TerminalPalette.current();
        frameHide = AlpakaConfig.instance.terminalHideOtherItems;
    }

    /**
     * Whether Hide Other Items leaves {@code slot} empty: nothing until the chest is loaded, never a
     * part of the solution, and in Click the Button on Time only what is outside its rows and
     * buttons and the target column.
     */
    static boolean hidden(TerminalSolution solution, int slot) {
        if (!solution.loaded()) return false;
        TerminalKind kind = solution.kind();
        if (kind.isMelody()) return !kind.isPlaySlot(slot) && !solution.inTargetColumn(slot);
        return solution.mark(slot) == TerminalSolution.Mark.OTHER;
    }

    /**
     * Whether {@code slot} is a terminal cell the solution leaves alone (done, or not asked for), once
     * the chest is loaded: it gets the dark veil. Anything with a fill of its own - the cells to click,
     * Click the Button on Time's column, moving pane and button - never does.
     */
    static boolean resting(TerminalSolution solution, TerminalPalette palette, int slot) {
        if (!solution.loaded() || !solution.kind().isPlaySlot(slot) || palette.fill(solution, slot) != 0) return false;
        TerminalSolution.Mark mark = solution.mark(slot);
        return mark == TerminalSolution.Mark.DONE || mark == TerminalSolution.Mark.OTHER;
    }

    /** The pending veil and a refused click's frame. */
    private static void marks(GuiGraphicsExtractor graphics, TerminalSession session, Slot slot) {
        int x = slot.x;
        int y = slot.y;
        if (session.state().isPending(slot.index)) graphics.fill(x, y, x + 16, y + 16, PENDING_VEIL);
        TerminalSession.Refusal refusal = session.lastRefusal();
        if (refusal != null && refusal.slot() == slot.index && frameNow - refusal.atMs() < REFUSED_FLASH_MS) {
            graphics.fill(x, y, x + 16, y + 1, REFUSED_FRAME);
            graphics.fill(x, y + 15, x + 16, y + 16, REFUSED_FRAME);
            graphics.fill(x, y + 1, x + 1, y + 15, REFUSED_FRAME);
            graphics.fill(x + 15, y + 1, x + 16, y + 15, REFUSED_FRAME);
        }
    }

    // --- clicks ---

    /**
     * The click {@link #allowSlotClick} let through, until vanilla has handed it to the server
     * ({@link #afterSlotClickSent}). Cleared at every slotClicked head, so a click another mod
     * cancelled after the head never counts.
     */
    private static Allowed allowed = null;

    /** A click let through and not yet sent; {@code click} only for a click on a cell. */
    private record Allowed(TerminalSession session, Input kind, long atMs, TerminalSession.VanillaClick click) {}

    /**
     * A click about to be sent from the vanilla chest; false refuses it (nothing is sent).
     * Records nothing yet: a click let through is recorded once it went out. {@code slot} is null
     * for a click outside the window.
     */
    public static boolean allowSlotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button,
                                         ContainerInput input) {
        allowed = null;
        TerminalSession session = TerminalSession.current(screen.getMenu());
        if (session == null || !session.kind().enabled()) return true;
        // What vanilla does with it too: the slot's own index wins over the id passed along.
        int id = slot != null ? slot.index : slotId;
        long now = Util.getMillis();
        Input kind = inputOf(session, id, button, input);
        boolean onCell = id >= 0 && id < session.chestSize();
        boolean blockWrong = TerminalSettings.current().blockWrongClicks() && !session.kind().isMelody();
        TerminalClicks.Verdict verdict = SEQUENCE.decide(session, kind, now, () -> judge(kind, onCell, blockWrong,
                () -> TerminalClicks.check(session, id, button, now),
                () -> TerminalClicks.checkGuards(session, now)));
        if (verdict != TerminalClicks.Verdict.ALLOW) {
            // A collect is no click of its own to the player; a red frame for it would land on a
            // cell they may well have clicked right.
            if (kind != Input.COLLECT) session.onRefused(id, verdict, now);
            return false;
        }
        allowed = new Allowed(session, kind, now, kind == Input.CELL ? session.vanillaClickAllowed(id, button, now) : null);
        return true;
    }

    /**
     * Right after slotClicked handed the click it let through to the server, and vanilla handled it
     * on the client: the session records it and notes what the slot holds now, so that it can tell
     * the server's answer from it at once (see TerminalSession).
     */
    public static void afterSlotClickSent(AbstractContainerScreen<?> screen) {
        Allowed click = allowed;
        allowed = null;
        if (click == null || TerminalSession.current(screen.getMenu()) != click.session()) return;
        switch (click.kind()) {
            case CELL -> click.session().onVanillaClickSent(click.click());
            case DRAG_START, COLLECT, OTHER -> click.session().onOtherClickSent(click.atMs());
            case DRAG_MORE -> { }
        }
    }

    /**
     * Vanilla's shift double click: on the release it shift-clicks every slot of the window that
     * holds the same item as the one shift-clicked last. In a terminal those are clicks on cells
     * the player never aimed at - every red pane at once - and a guard that refused the presses
     * would let the first of them through on a pane nobody clicked. So none of them is sent in a
     * terminal, whatever the presses got; the presses themselves are judged like any click.
     *
     * Part of the Click Rate Guard: the whole volley goes out within one tick, which is what that
     * guard is for. With the guard switched off, vanilla's volley goes out as it always would.
     */
    public static boolean allowShiftCollect(AbstractContainerScreen<?> screen) {
        TerminalSession session = TerminalSession.current(screen.getMenu());
        return session == null || !session.kind().enabled() || !TerminalSettings.current().rateGuard();
    }

    /** Lets go of everything kept from the last terminal: on a world change, disconnect or when it closed. */
    static void reset() {
        frame = null;
        frameSession = null;
        frameSolution = null;
        framePalette = null;
        allowed = null;
        SEQUENCE.reset();
    }

    /** The click kinds the guards tell apart. */
    enum Input {
        /** A left or right click, or a shift click, on a terminal cell: every guard. */
        CELL,
        /** The start of a drag: the first-click and rate guards, for the whole drag; refused with Block Wrong Clicks. */
        DRAG_START,
        /** The rest of a drag: whatever its start got. */
        DRAG_MORE,
        /**
         * The collect a double click sends on release: refused with the click before it when that
         * one was refused; else the first-click and rate guards, and refused on a terminal cell with
         * Block Wrong Clicks.
         */
        COLLECT,
        /** Anything else: the first-click and rate guards. */
        OTHER
    }

    private static Input inputOf(TerminalSession session, int slot, int button, ContainerInput input) {
        if (input == ContainerInput.QUICK_CRAFT) {
            return AbstractContainerMenu.getQuickcraftHeader(button) == 0 ? Input.DRAG_START : Input.DRAG_MORE;
        }
        if (input == ContainerInput.PICKUP_ALL) return Input.COLLECT;
        boolean cell = slot >= 0 && slot < session.chestSize() && (button == 0 || button == 1);
        if (cell && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) return Input.CELL;
        return Input.OTHER;
    }

    /**
     * What a click of this kind gets on its own, before the clicks around it count: a click on a
     * cell every guard, anything else the first-click and rate guards. With Block Wrong Clicks on,
     * a collect on a terminal cell and every drag are refused outright: no terminal needs either,
     * a drag's cells are not known when it starts, and a collect can come without a press of its
     * own reaching the guards (with the clicked pane still on the cursor, vanilla's second press of
     * a double click sends nothing and the collect on its release is all that goes out), so judging
     * it by the guards alone would let a click on a done cell through. Pure.
     *
     * @param onCell     the click is on one of the terminal's own slots
     * @param blockWrong Block Wrong Clicks is on and the terminal is not Click the Button on Time
     */
    static TerminalClicks.Verdict judge(Input input, boolean onCell, boolean blockWrong,
                                        Supplier<TerminalClicks.Verdict> cell, Supplier<TerminalClicks.Verdict> guards) {
        return switch (input) {
            case CELL -> cell.get();
            case DRAG_START -> blockWrong ? TerminalClicks.Verdict.WRONG : guards.get();
            case COLLECT -> blockWrong && onCell ? TerminalClicks.Verdict.WRONG : guards.get();
            case DRAG_MORE, OTHER -> guards.get();
        };
    }

    /**
     * What the clicks before this one decided, where it matters for this one: the verdict of the
     * drag under way, and whether the last click was refused. Pure, apart from asking the check.
     */
    static final class Sequence {
        /** A double click's collect comes this soon after the click it belongs to, at most. */
        static final long COLLECT_MS = 500L;

        private TerminalSession session = null;
        private TerminalClicks.Verdict drag = null;
        private TerminalClicks.Verdict last = TerminalClicks.Verdict.ALLOW;
        private long lastMs = Long.MIN_VALUE / 2;

        /**
         * The verdict for a click of kind {@code input}; {@code own} is what it gets on its own
         * ({@link #judge}), asked only when the clicks before it do not decide.
         */
        TerminalClicks.Verdict decide(TerminalSession forSession, Input input, long nowMs, Supplier<TerminalClicks.Verdict> own) {
            if (forSession != session) {
                reset();
                session = forSession;
            }
            TerminalClicks.Verdict verdict = switch (input) {
                case CELL, OTHER -> own.get();
                case DRAG_START -> drag = own.get();
                // A drag is sent whole or not at all; one that started before the session is let be.
                case DRAG_MORE -> drag == null ? TerminalClicks.Verdict.ALLOW : drag;
                case COLLECT -> last != TerminalClicks.Verdict.ALLOW && nowMs - lastMs <= COLLECT_MS ? last : own.get();
            };
            if (input != Input.DRAG_MORE) {
                last = verdict;
                lastMs = nowMs;
            }
            return verdict;
        }

        /** Forgets the session and everything decided in it. */
        void reset() {
            session = null;
            drag = null;
            last = TerminalClicks.Verdict.ALLOW;
            lastMs = Long.MIN_VALUE / 2;
        }
    }
}
