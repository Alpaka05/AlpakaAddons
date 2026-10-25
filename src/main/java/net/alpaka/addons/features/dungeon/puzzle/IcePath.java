package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Ice Path (the silverfish): reads the 17 x 17 board and the silverfish's position, searches the
 * fewest slides that bring it to the exit, and marks the direction to hit it next.
 *
 * Spec: puzzles-floor.md section 3 (board at room Y 67, slide search, what to draw, fixture).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class IcePath extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.ICE_PATH;
    }
}
