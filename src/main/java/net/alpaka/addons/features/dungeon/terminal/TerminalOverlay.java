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
 * when a double click is released follows the click it belongs to. A refused click is never turned
 * into another one.
 */
public final class TerminalOverlay {

    /** Over a clicked cell the server has not answered yet: a neutral grey veil. */
    private static final int PENDING_VEIL = 0xA0707070;
    /** A refused click's slot gets a red frame for this long. */
    private static final long REFUSED_FLASH_MS = 300L;
    private static final int REFUSED_FRAME = 0xFFFF5555;
    private static final int LABEL_COLOR = 0xFFFFFFFF;

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
        String label = frameSolution.label(slot.index);
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
     * A click about to be sent from the vanilla chest; false refuses it (nothing is sent).
     * When it returns true it records the click on the session. {@code slot} is null for a click
     * outside the window.
     */
    public static boolean allowSlotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button,
                                         ContainerInput input) {
        TerminalSession session = TerminalSession.current(screen.getMenu());
        if (session == null || !session.kind().enabled()) return true;
        // What vanilla does with it too: the slot's own index wins over the id passed along.
        int id = slot != null ? slot.index : slotId;
        long now = Util.getMillis();
        Input kind = inputOf(session, id, button, input);
        TerminalClicks.Verdict verdict = SEQUENCE.decide(session, kind, now,
                () -> TerminalClicks.check(session, id, button, now),
                () -> TerminalClicks.checkGuards(session, now));
        if (verdict != TerminalClicks.Verdict.ALLOW) {
            session.onRefused(id, verdict, now);
            return false;
        }
        switch (kind) {
            case CELL -> session.onVanillaClickAllowed(id, button, now);
            case DRAG_START, COLLECT, OTHER -> session.onOtherClickSent(now);
            case DRAG_MORE -> { }
        }
        return true;
    }

    /**
     * At slotClicked's return, once vanilla handled a click it was let through: lets the session
     * note what the clicked slot holds now, so that it can tell the server's answer from it at
     * once (see TerminalSession). Without this hook it notes it a frame later, which is nearly
     * always just as good.
     */
    public static void afterSlotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId) {
        TerminalSession session = TerminalSession.current(screen.getMenu());
        if (session != null) session.afterVanillaClick(slot != null ? slot.index : slotId);
    }

    /** The click kinds the guards tell apart. */
    enum Input {
        /** A left or right click, or a shift click, on a terminal cell: every guard. */
        CELL,
        /** The start of a drag: the first-click and rate guards, for the whole drag. */
        DRAG_START,
        /** The rest of a drag: whatever its start got. */
        DRAG_MORE,
        /** The collect a double click sends on release: refused with the click before it. */
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
     * What the clicks before this one decided, where it matters for this one: the verdict of the
     * drag under way, and whether the last click was refused. Pure, apart from asking the guards.
     */
    static final class Sequence {
        /** A double click's collect comes this soon after the click it belongs to, at most. */
        static final long COLLECT_MS = 500L;

        private TerminalSession session = null;
        private TerminalClicks.Verdict drag = null;
        private TerminalClicks.Verdict last = TerminalClicks.Verdict.ALLOW;
        private long lastMs = Long.MIN_VALUE / 2;

        TerminalClicks.Verdict decide(TerminalSession forSession, Input input, long nowMs,
                                      Supplier<TerminalClicks.Verdict> cell, Supplier<TerminalClicks.Verdict> guards) {
            if (forSession != session) {
                session = forSession;
                drag = null;
                last = TerminalClicks.Verdict.ALLOW;
                lastMs = Long.MIN_VALUE / 2;
            }
            TerminalClicks.Verdict verdict = switch (input) {
                case CELL -> cell.get();
                case DRAG_START -> drag = guards.get();
                // A drag is sent whole or not at all; one that started before the session is let be.
                case DRAG_MORE -> drag == null ? TerminalClicks.Verdict.ALLOW : drag;
                case COLLECT -> last != TerminalClicks.Verdict.ALLOW && nowMs - lastMs <= COLLECT_MS ? last : guards.get();
                case OTHER -> guards.get();
            };
            if (input != Input.DRAG_MORE) {
                last = verdict;
                lastMs = nowMs;
            }
            return verdict;
        }
    }
}
