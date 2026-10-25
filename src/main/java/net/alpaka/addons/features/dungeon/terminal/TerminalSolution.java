package net.alpaka.addons.features.dungeon.terminal;

import java.util.Arrays;

/**
 * What a terminal's solution says about each chest slot: whether to click it, in which order, and
 * how often. Pure data, built by {@link TerminalSolver} through {@link Builder}; the overlay and the
 * custom window only read it.
 *
 * It describes the chest as the server last confirmed it. A cell the player has just clicked shows
 * as pending through {@link TerminalSession#pending}, not here, unless Predict Clicks is on and the
 * solver was told to treat pending clicks as done.
 *
 * Click the Button on Time marks only the active row's button ({@link Mark#CLICK}); the target
 * column and the moving pane come from {@link #inTargetColumn} and {@link #movingSlot}, drawn the
 * same way whether or not they line up.
 */
public final class TerminalSolution {

    /** The biggest chest a terminal uses: six rows. */
    public static final int MAX_SLOTS = 54;

    public enum Mark {
        /** Click this (Click in Order: the next one). */
        CLICK,
        /** Click in Order: the one after. */
        SECOND,
        /** Click in Order: the third. */
        THIRD,
        /** Part of the puzzle and already right (a lime pane, a clicked item). */
        DONE,
        /** Not part of the solution: filler, a wrong item, an empty slot. */
        OTHER
    }

    private final TerminalKind kind;
    private final Mark[] marks;
    private final int[] clicks;
    private final String[] labels;
    private final boolean loaded;
    private final boolean solved;
    private final int columnTarget;
    private final int movingColumn;
    private final int activeRow;
    private final int buttonSlot;

    private TerminalSolution(Builder b) {
        this.kind = b.kind;
        this.marks = b.marks.clone();
        this.clicks = b.clicks.clone();
        this.labels = b.labels.clone();
        this.loaded = b.loaded;
        this.solved = b.solved;
        this.columnTarget = b.columnTarget;
        this.movingColumn = b.movingColumn;
        this.activeRow = b.activeRow;
        this.buttonSlot = b.buttonSlot;
    }

    public static Builder builder(TerminalKind kind) {
        return new Builder(kind);
    }

    /** Nothing known yet: every slot OTHER, not loaded. */
    public static TerminalSolution empty(TerminalKind kind) {
        return builder(kind).build();
    }

    public TerminalKind kind() {
        return kind;
    }

    public Mark mark(int slot) {
        return inRange(slot) ? marks[slot] : Mark.OTHER;
    }

    /** Change All to Same Color: clicks still needed, positive for left clicks, negative for right; 0 otherwise. */
    public int clicks(int slot) {
        return inRange(slot) ? clicks[slot] : 0;
    }

    /** The text drawn on the cell ("1", "2", "-1"), or null. */
    public String label(int slot) {
        return inRange(slot) ? labels[slot] : null;
    }

    /** Whether every play slot holds an item; until then nothing is certain. */
    public boolean loaded() {
        return loaded;
    }

    /** Whether nothing is left to click. */
    public boolean solved() {
        return solved;
    }

    /**
     * Whether a click on {@code slot} with {@code button} (0 left, 1 right) would be wrong, for the
     * optional wrong-click refusal. Never for Click the Button on Time, and never while the chest is
     * not fully loaded: when in doubt the click goes through. Change All to Same Color wants the
     * button its count says; the others want a {@link Mark#CLICK} slot.
     */
    public boolean wrongClick(int slot, int button) {
        if (kind.isMelody() || !loaded) return false;
        if (kind == TerminalKind.RUBIX) {
            int needed = clicks(slot);
            if (needed == 0) return true;
            return needed > 0 ? button != 0 : button != 1;
        }
        return mark(slot) != Mark.CLICK;
    }

    // --- Click the Button on Time; -1 where unknown or for the other terminals ---

    /** The column of the magenta target panes. */
    public int columnTarget() {
        return columnTarget;
    }

    /** The column the lime pane is in now. */
    public int movingColumn() {
        return movingColumn;
    }

    /** The row the lime pane moves in. */
    public int activeRow() {
        return activeRow;
    }

    /** The active row's button slot. */
    public int buttonSlot() {
        return buttonSlot;
    }

    /** The slot of the moving pane, or -1. */
    public int movingSlot() {
        return activeRow >= 0 && movingColumn >= 0 ? activeRow * 9 + movingColumn : -1;
    }

    /**
     * Click the Button on Time: whether {@code slot} is in the target column, from the magenta pane
     * at the top to the one at the bottom (the rows of the play area). False for the other terminals.
     */
    public boolean inTargetColumn(int slot) {
        if (!kind.isMelody() || columnTarget < 0 || !inRange(slot) || slot % 9 != columnTarget) return false;
        int[] area = kind.playArea();
        int row = slot / 9;
        return row >= area[0] && row <= area[1];
    }

    /** How many slots are still to click (marked {@link Mark#CLICK}). */
    public int toClick() {
        int n = 0;
        for (Mark mark : marks) {
            if (mark == Mark.CLICK) n++;
        }
        return n;
    }

    /**
     * Whether the moving pane is in the target column. For tests and the debug dump only: drawing
     * anything differently at that moment would be a "click now" cue (design.md 1.8), so the
     * highlights stay the same all the time.
     */
    public boolean aligned() {
        return movingColumn >= 0 && movingColumn == columnTarget;
    }

    private static boolean inRange(int slot) {
        return slot >= 0 && slot < MAX_SLOTS;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(kind.name()).append(loaded ? " loaded" : " loading");
        if (solved) out.append(" solved");
        if (kind.isMelody()) {
            out.append(" column=").append(columnTarget).append(" moving=").append(movingColumn)
                    .append(" row=").append(activeRow).append(" button=").append(buttonSlot);
        }
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            if (marks[slot] == Mark.OTHER && clicks[slot] == 0) continue;
            out.append(' ').append(slot).append('=').append(marks[slot]);
            if (clicks[slot] != 0) out.append('(').append(clicks[slot]).append(')');
        }
        return out.toString();
    }

    /** Builds a solution; every slot starts as {@link Mark#OTHER} with no clicks and no label. */
    public static final class Builder {
        private final TerminalKind kind;
        private final Mark[] marks = new Mark[MAX_SLOTS];
        private final int[] clicks = new int[MAX_SLOTS];
        private final String[] labels = new String[MAX_SLOTS];
        private boolean loaded = false;
        private boolean solved = false;
        private int columnTarget = -1;
        private int movingColumn = -1;
        private int activeRow = -1;
        private int buttonSlot = -1;

        private Builder(TerminalKind kind) {
            if (kind == null) throw new IllegalArgumentException("kind");
            this.kind = kind;
            Arrays.fill(marks, Mark.OTHER);
        }

        public Builder mark(int slot, Mark mark) {
            if (inRange(slot)) marks[slot] = mark == null ? Mark.OTHER : mark;
            return this;
        }

        /** Change All to Same Color: signed clicks still needed on the slot. */
        public Builder clicks(int slot, int signedClicks) {
            if (inRange(slot)) clicks[slot] = signedClicks;
            return this;
        }

        public Builder label(int slot, String label) {
            if (inRange(slot)) labels[slot] = label;
            return this;
        }

        public Builder loaded(boolean loaded) {
            this.loaded = loaded;
            return this;
        }

        public Builder solved(boolean solved) {
            this.solved = solved;
            return this;
        }

        /** Click the Button on Time's state; -1 for what is not known. */
        public Builder melody(int columnTarget, int movingColumn, int activeRow, int buttonSlot) {
            this.columnTarget = columnTarget;
            this.movingColumn = movingColumn;
            this.activeRow = activeRow;
            this.buttonSlot = buttonSlot;
            return this;
        }

        public TerminalSolution build() {
            return new TerminalSolution(this);
        }
    }
}
