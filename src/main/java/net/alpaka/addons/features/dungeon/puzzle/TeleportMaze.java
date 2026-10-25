package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Teleport Maze: tracks the pads the player has used (each teleport arrives through
 * onServerTeleport) and narrows down the pad that leads to the end from the direction each
 * teleport faces the player; marks used pads in the Wrong color and the likely right one.
 *
 * Spec: puzzles-entities.md section 4 (the 30 pads at room Y 69, END_PORTAL_FRAME, catching the
 * teleport, the pad choice, fixtures).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class TeleportMaze extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.TELEPORT_MAZE;
    }
}
