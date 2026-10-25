package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Boulder: reads the 7 x 6 board of boxes, searches the fewest pushes that clear a path to the
 * chest, and marks the next box to push and the button to press. Plans only push boxes into empty
 * cells: two boxes merging is a Hypixel bug and is never planned on (critic.md 3.1).
 *
 * Spec: puzzles-floor.md section 1 (board at room Y 66, push rule, search, what to draw, fixtures)
 * and its Critic additions.
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class Boulder extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.BOULDER;
    }
}
