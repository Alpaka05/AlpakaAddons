package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;

/**
 * A solver for one Catacombs puzzle room.
 *
 * Ticked while the player is in a run outside the boss room and inside the room grid; the frame in
 * the context is the player's room or null. The solver recognises its own room from what it reads
 * anyway (rooms.md 3.5) and draws nothing in any other room - SolverDraw also drops anything outside
 * the player's cell. Once the tab list shows the puzzle solved or failed it is no longer ticked or
 * drawn, but keeps its state: a failed puzzle reset with an Architect's First Draft carries on.
 */
public abstract class PuzzleSolver extends DungeonSolver {

    public abstract DungeonPuzzle puzzle();

    @Override
    public String name() {
        return puzzle().tabName();
    }

    @Override
    public boolean enabled() {
        return puzzle().enabled(AlpakaConfig.instance);
    }
}
