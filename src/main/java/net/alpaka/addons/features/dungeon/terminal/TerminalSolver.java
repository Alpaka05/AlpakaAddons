package net.alpaka.addons.features.dungeon.terminal;

import java.util.List;

/**
 * Works out a terminal's solution from its chest (terminals.md 3, critic.md C3-C6). Pure, so every
 * terminal can be tested from plain slot data.
 *
 * What it will do per terminal: Correct All the Panes marks the red panes; Change All to Same
 * Color locks the target color with the fewest total clicks once all nine panes are known and
 * gives each pane its signed click count (left forward through red, orange, yellow, green, blue,
 * right back); Click in Order marks the three lowest red panes CLICK, SECOND, THIRD with their
 * numbers; What Starts With and Select All mark the matching items not clicked yet (glint, or a
 * confirmed own click for items that always glint); Click the Button on Time fills in the melody
 * fields and marks the target column, the moving pane and the active row's button, the same way
 * all the time. Pending clicks count as done only with {@code state.predict()}.
 *
 * Not built yet: returns a solution with nothing marked, which draws nothing and refuses nothing.
 */
public final class TerminalSolver {

    private TerminalSolver() {}

    /**
     * @param kind       the terminal
     * @param plainTitle its chest title, formatting stripped (the letter or color it asks for)
     * @param chest      every chest slot from 0, in order: {@code chest.get(i).slot() == i}
     * @param state      the session's clicks, pending clicks and locks
     */
    public static TerminalSolution solve(TerminalKind kind, String plainTitle, List<TerminalItem> chest,
                                         TerminalSession.State state) {
        return TerminalSolution.empty(kind);
    }
}
