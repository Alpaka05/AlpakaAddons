package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.config.AlpakaConfig;

import java.util.function.Predicate;

/**
 * The ten Catacombs puzzles that are live (rooms.md 1; Bomb Defuse was removed in 0.20.5).
 *
 * {@link #tabName()} is exactly how the tab list's Puzzles section names the puzzle, capital O in
 * "Higher Or Lower" included; {@link net.alpaka.addons.features.dungeon.room.PuzzleTab} matches on it.
 */
public enum DungeonPuzzle {
    THREE_WEIRDOS("Three Weirdos", c -> c.solveThreeWeirdos),
    QUIZ("Quiz", c -> c.solveQuiz),
    TIC_TAC_TOE("Tic Tac Toe", c -> c.solveTicTacToe),
    HIGHER_OR_LOWER("Higher Or Lower", c -> c.solveHigherOrLower),
    CREEPER_BEAMS("Creeper Beams", c -> c.solveCreeperBeams),
    TELEPORT_MAZE("Teleport Maze", c -> c.solveTeleportMaze),
    WATER_BOARD("Water Board", c -> c.solveWaterBoard),
    BOULDER("Boulder", c -> c.solveBoulder),
    ICE_FILL("Ice Fill", c -> c.solveIceFill),
    ICE_PATH("Ice Path", c -> c.solveIcePath);

    private final String tabName;
    private final Predicate<AlpakaConfig> toggle;

    DungeonPuzzle(String tabName, Predicate<AlpakaConfig> toggle) {
        this.tabName = tabName;
        this.toggle = toggle;
    }

    /** The name in the tab list, which is also the name shown to the player. */
    public String tabName() {
        return tabName;
    }

    /** This puzzle's own switch, without the Solvers master. */
    public boolean toggledOn(AlpakaConfig cfg) {
        return toggle.test(cfg);
    }

    /** The Solvers master and this puzzle's switch. */
    public boolean enabled(AlpakaConfig cfg) {
        return cfg.dungeonSolversEnabled && toggle.test(cfg);
    }

    public boolean enabled() {
        return enabled(AlpakaConfig.instance);
    }

    /** The puzzle the tab list calls {@code name}, or null. */
    public static DungeonPuzzle byTabName(String name) {
        if (name == null) return null;
        for (DungeonPuzzle puzzle : values()) {
            if (puzzle.tabName.equals(name)) return puzzle;
        }
        return null;
    }
}
