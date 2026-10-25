package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Tic Tac Toe: reads the board from the maps in the item frames on the room's wall (room X 8,
 * Y 70..72, Z 15..17), works out the best move for the player (O, against the computer's X; a tie
 * solves the puzzle, a loss fails it) and marks that button. The board and its search go into a pure
 * class (TicTacToeBoard).
 *
 * Spec: puzzles-chat.md section 4 (frames, map pixel colors, best move, fixtures).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class TicTacToe extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.TIC_TAC_TOE;
    }
}
