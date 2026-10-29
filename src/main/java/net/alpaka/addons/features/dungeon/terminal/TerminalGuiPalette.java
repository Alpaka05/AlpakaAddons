package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.config.AlpakaConfig;

/**
 * The custom terminal window's colors: the terminal colors from the settings, and which of them a
 * cell gets. Pure apart from {@link #current()}, so tests build one by hand.
 *
 * Every color here is steady: a cell's color follows from what the solution says about its slot,
 * never from the moment. In particular Click the Button on Time's button keeps its color whether or
 * not the moving pane is lined up with the target, so nothing turns into a "click now" cue
 * (design.md 1.8, critic.md C4).
 *
 * @param solution   what to click (Click in Order: the next one; Click the Button on Time: the button)
 * @param next       Click in Order: the one after
 * @param third      Click in Order: the third
 * @param rubixLeft  Change All to Same Color: panes that want left clicks
 * @param rubixRight Change All to Same Color: panes that want right clicks
 * @param melody     Click the Button on Time: the target column and the moving pane
 * @param background the window's background
 */
public record TerminalGuiPalette(int solution, int next, int third, int rubixLeft, int rubixRight, int melody,
                                 int background) {

    /** Text on a dark background, and on a light one. */
    public static final int TEXT_ON_DARK = 0xFFEDEDED;
    public static final int TEXT_ON_LIGHT = 0xFF161616;
    public static final int MUTED_ON_DARK = 0xFF8C8C8C;
    public static final int MUTED_ON_LIGHT = 0xFF5C5C5C;

    /** The colors as they are set now. */
    public static TerminalGuiPalette current() {
        AlpakaConfig c = AlpakaConfig.instance;
        return new TerminalGuiPalette(c.terminalSolutionColor, c.terminalNextColor, c.terminalThirdColor,
                c.terminalRubixLeftColor, c.terminalRubixRightColor, c.terminalMelodyColor, c.terminalGuiBackgroundColor);
    }

    /**
     * The color the solution gives a slot's cell, or 0 for none (done, not part of it, unknown).
     * Click the Button on Time: the target column's two panes (rows 0 and 4) and the moving pane in
     * the Melody color, the active row's button in the Solution color. Change All to Same Color:
     * the click direction's color. The others by their mark.
     */
    public int fill(TerminalSolution s, int slot) {
        TerminalKind kind = s.kind();
        if (kind.isMelody()) {
            int target = s.columnTarget();
            if (target >= 0 && (slot == target || slot == 4 * 9 + target)) return melody;
            if (s.activeRow() >= 0 && s.movingColumn() >= 0 && slot == s.activeRow() * 9 + s.movingColumn()) return melody;
            if (s.buttonSlot() >= 0 && slot == s.buttonSlot()) return solution;
        }
        if (kind == TerminalKind.RUBIX) {
            int clicks = s.clicks(slot);
            if (clicks != 0) return clicks > 0 ? rubixLeft : rubixRight;
        }
        return switch (s.mark(slot)) {
            case CLICK -> solution;
            case SECOND -> next;
            case THIRD -> third;
            default -> 0;
        };
    }

    /**
     * Whether text needs to be light on this background. A see-through background lets the dimmed
     * game show through, taken to be fairly dark, so it counts for the rest.
     */
    public static boolean dark(int background) {
        float a = (background >>> 24) / 255.0f;
        float r = ((background >> 16) & 0xFF) / 255.0f;
        float g = ((background >> 8) & 0xFF) / 255.0f;
        float b = (background & 0xFF) / 255.0f;
        float seen = a * (0.2126f * r + 0.7152f * g + 0.0722f * b) + (1.0f - a) * 0.2f;
        return seen < 0.5f;
    }

    /** Readable text on {@link #background}. */
    public int text() {
        return dark(background) ? TEXT_ON_DARK : TEXT_ON_LIGHT;
    }

    /** Quieter text on {@link #background}. */
    public int mutedText() {
        return dark(background) ? MUTED_ON_DARK : MUTED_ON_LIGHT;
    }

    /** A neutral tint for cells and lines: the text color, faint. */
    public int neutral(int alpha) {
        return withAlpha(text(), alpha);
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    /** The color with its alpha scaled by {@code factor} (0..1). */
    public static int scaleAlpha(int argb, float factor) {
        float f = Math.max(0.0f, Math.min(1.0f, factor));
        return withAlpha(argb, Math.round((argb >>> 24) * f));
    }
}
