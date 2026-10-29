package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.TicTacToeBoard.Advice;
import net.alpaka.addons.features.dungeon.puzzle.TicTacToeBoard.Outcome;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tic Tac Toe: the best reply for the player (puzzles-chat.md 4.3, 4.4, 4.7). Boards are nine
 * characters row by row from the player's top left, X the computer, O the player.
 */
class TicTacToeBoardTest {

    @Test
    void openingReplies() {
        // Centre opening: only a corner holds the draw.
        assertBest("....X....", List.of(0, 2, 6, 8), Outcome.DRAW);
        // Corner opening: only the centre does.
        assertBest("X........", List.of(4), Outcome.DRAW);
        assertBest("..X......", List.of(4), Outcome.DRAW);
    }

    @Test
    void middleGame() {
        assertBest("O...X...X", List.of(2, 6), Outcome.DRAW);
        // Opposite corners against the centre: a corner loses to a fork, an edge holds.
        assertBest("X...O...X", List.of(1, 3, 5, 7), Outcome.DRAW);
        assertBest("XX..O....", List.of(2), Outcome.DRAW);
        assertBest("X.O.X....", List.of(8), Outcome.DRAW);
        assertBest("OX..X....", List.of(7), Outcome.DRAW);
        assertBest(".X..O...X", List.of(0, 2, 3, 5), Outcome.DRAW);
        assertBest("O.X.X....", List.of(6), Outcome.DRAW);
        // puzzles-chat.md 4.7 lists "XO.OX...." as the player's move, but with two marks each it is
        // the computer's.
        assertNone("XO.OX....");
        // An edge next to the computer's corner loses: blocking the diagonal only delays the fork.
        assertBest("XO..X....", List.of(8), Outcome.LOSS);
    }

    @Test
    void aWinAtOnceIsPreferred() {
        // 7 wins now; 3 would win too, but later.
        assertBest("XOX.O.X..", List.of(7), Outcome.WIN);
    }

    @Test
    void aLostPosition() {
        // X threatens two lines at once; every reply loses next move.
        assertBest("OXX.XO...", List.of(3, 6, 7, 8), Outcome.LOSS);
    }

    @Test
    void notThePlayersMove() {
        // Right after the player moved, before the computer answers.
        assertNone("X...O....");
        assertNone(".........");
        // The computer has won.
        assertNone("XXXOO....");
        // The board is full: a tie.
        assertNone("XOXXOOOXX");
        // Impossible counts.
        assertNone("OO..X....");
    }

    @Test
    void equalMovesAreChosenTheSameWayEveryTime() {
        // Centre first, then the corners, then the edges.
        assertEquals(0, TicTacToeBoard.of("....X....").advise().cell());
        assertEquals(1, TicTacToeBoard.of("X...O...X").advise().cell());
        assertEquals(0, TicTacToeBoard.of(".X..O...X").advise().cell());
        assertEquals(6, TicTacToeBoard.of("OXX.XO...").advise().cell());
    }

    @Test
    void theBestMoveNeverLosesAgainstAnyReply() {
        // Play every game the computer could play after its centre or corner opening, the player
        // always taking the advised cell; none may end with X winning.
        for (int opening : new int[]{4, 0, 2, 6, 8}) {
            playAll(TicTacToeBoard.empty().with(opening, TicTacToeBoard.X));
        }
    }

    private static void playAll(TicTacToeBoard board) {
        Advice advice = board.advise();
        assertTrue(advice.any(), board.toString());
        assertTrue(advice.outcome() != Outcome.LOSS, board.toString());
        TicTacToeBoard afterPlayer = board.with(advice.cell(), TicTacToeBoard.O);
        if (afterPlayer.winner() == TicTacToeBoard.O || afterPlayer.full()) return;
        for (int cell = 0; cell < 9; cell++) {
            if (afterPlayer.at(cell) != TicTacToeBoard.EMPTY) continue;
            TicTacToeBoard afterComputer = afterPlayer.with(cell, TicTacToeBoard.X);
            assertFalse(afterComputer.winner() == TicTacToeBoard.X, afterComputer.toString());
            if (!afterComputer.full()) playAll(afterComputer);
        }
    }

    @Test
    void mapPixels() {
        assertEquals(TicTacToeBoard.X, TicTacToeBoard.markOfPixel((byte) 114));
        assertEquals(TicTacToeBoard.O, TicTacToeBoard.markOfPixel((byte) 33));
        assertEquals(TicTacToeBoard.EMPTY, TicTacToeBoard.markOfPixel((byte) 0));
        assertEquals(8256, TicTacToeBoard.CENTRE_PIXEL);
    }

    @Test
    void positionsInTheRoom() {
        assertEquals(0, TicTacToeBoard.cellAt(8, 72, 17));
        assertEquals(8, TicTacToeBoard.cellAt(8, 70, 15));
        assertEquals(4, TicTacToeBoard.cellAt(8, 71, 16));
        assertEquals(2, TicTacToeBoard.cellAt(8, 72, 15));
        assertEquals(-1, TicTacToeBoard.cellAt(7, 72, 17));
        assertEquals(-1, TicTacToeBoard.cellAt(8, 73, 17));
        assertEquals(-1, TicTacToeBoard.cellAt(8, 72, 18));
        assertEquals(new BlockPos(8, 72, 15), TicTacToeBoard.buttonRel(2));
        assertEquals(new BlockPos(8, 70, 17), TicTacToeBoard.buttonRel(6));
        for (int cell = 0; cell < 9; cell++) {
            BlockPos button = TicTacToeBoard.buttonRel(cell);
            assertEquals(cell, TicTacToeBoard.cellAt(button.getX(), button.getY(), button.getZ()));
        }
    }

    @Test
    void readingBoards() {
        TicTacToeBoard board = TicTacToeBoard.of("x..o.....");
        assertEquals(TicTacToeBoard.X, board.at(0));
        assertEquals(TicTacToeBoard.O, board.at(3));
        assertEquals(1, board.count(TicTacToeBoard.X));
        assertEquals("X..O.....", board.toString());
        assertEquals(TicTacToeBoard.O, TicTacToeBoard.of("OOOXX.X..").winner());
    }

    private static void assertBest(String board, List<Integer> best, Outcome outcome) {
        TicTacToeBoard b = TicTacToeBoard.of(board);
        assertTrue(b.playersTurn(), board);
        assertEquals(best, b.bestMoves(), board);
        Advice advice = b.advise();
        assertTrue(best.contains(advice.cell()), board);
        assertEquals(outcome, advice.outcome(), board);
    }

    private static void assertNone(String board) {
        TicTacToeBoard b = TicTacToeBoard.of(board);
        assertFalse(b.advise().any(), board);
        assertTrue(b.bestMoves().isEmpty(), board);
    }
}
