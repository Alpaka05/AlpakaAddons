package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.config.AlpakaConfig;

/**
 * The terminal colors, and which one a cell gets (design.md 5.1). Pure: built from the config with
 * {@link #current()} in the game, by hand in tests.
 *
 * @param solution   click this; the active row's button in Click the Button on Time
 * @param second     Click in Order: second
 * @param third      Click in Order: third
 * @param rubixLeft  Change All to Same Color: panes that want left clicks
 * @param rubixRight Change All to Same Color: panes that want right clicks
 * @param melody     Click the Button on Time: the target column and the moving pane
 */
public record TerminalPalette(int solution, int second, int third, int rubixLeft, int rubixRight, int melody) {

    public static TerminalPalette current() {
        AlpakaConfig c = AlpakaConfig.instance;
        return new TerminalPalette(c.terminalSolutionColor, c.terminalNextColor, c.terminalThirdColor,
                c.terminalRubixLeftColor, c.terminalRubixRightColor, c.terminalMelodyColor);
    }

    /**
     * The color {@code slot} is filled with, or 0 for none. Click the Button on Time colors the
     * target column and the moving pane alike, so nothing changes when the pane reaches the column:
     * no "click now" cue (design.md 1.8).
     */
    public int fill(TerminalSolution solution, int slot) {
        TerminalKind kind = solution.kind();
        if (kind.isMelody()) {
            if (slot == solution.buttonSlot()) return this.solution;
            if (slot == solution.movingSlot() || solution.inTargetColumn(slot)) return melody;
            return 0;
        }
        if (kind == TerminalKind.RUBIX) {
            int clicks = solution.clicks(slot);
            return clicks > 0 ? rubixLeft : clicks < 0 ? rubixRight : 0;
        }
        return switch (solution.mark(slot)) {
            case CLICK -> this.solution;
            case SECOND -> second;
            case THIRD -> third;
            default -> 0;
        };
    }
}
