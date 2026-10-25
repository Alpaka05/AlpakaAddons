package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Ice Fill: reads each of the three ice floors and draws the path that covers every ice block
 * exactly once, from the floor's entrance to its exit. Lines are lifted a little above the floor so
 * depth testing does not hide them in it.
 *
 * Spec: puzzles-floor.md section 2 (board origins, extraction, the path search, fixtures).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class IceFill extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.ICE_FILL;
    }
}
