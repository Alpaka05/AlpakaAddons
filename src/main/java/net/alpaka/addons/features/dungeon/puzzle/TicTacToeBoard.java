package net.alpaka.addons.features.dungeon.puzzle;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Tic Tac Toe position from the dungeon puzzle and the best reply for the player, without the
 * game, so it can be tested.
 *
 * The computer plays X and always moves first; the player is O (puzzles-chat.md 4.1). A tie solves
 * the puzzle, a loss fails it. Cells are numbered row by row from the top left as the player sees
 * the board: 0 1 2 / 3 4 5 / 6 7 8.
 *
 * The best reply comes from a full minimax search, which for a 3 x 3 board is a few thousand
 * positions at most. A win scores more the sooner it comes and a loss less the later, so when the
 * computer slips the reply that wins at once is preferred. Among replies that score the same, the
 * centre comes first, then the corners, then the edges, so the mark does not jump between equal
 * moves from one tick to the next.
 */
public final class TicTacToeBoard {

    public static final char X = 'X';
    public static final char O = 'O';
    public static final char EMPTY = '.';

    /** The pixel in the middle of a 128 x 128 map, whose color tells X from O. */
    public static final int CENTRE_PIXEL = 64 * 128 + 64;
    /** Map color byte of an X: red, bright (MapColor COLOR_RED id 28, brightness HIGH). */
    public static final int PIXEL_X = 114;
    /** Map color byte of an O: white (MapColor SNOW id 8, brightness NORMAL). */
    public static final int PIXEL_O = 33;

    /**
     * Where the board hangs in the room frame (Skyblocker's positions, puzzles-chat.md 4.2): the maps
     * and the buttons are in the plane room X 8, the rows at Y 72, 71, 70 from the top, the columns at
     * room Z 17, 16, 15 from the player's left.
     */
    public static final int PLANE_X = 8;
    public static final int TOP_Y = 72;
    public static final int LEFT_Z = 17;

    /** The order equal moves are preferred in: centre, corners, edges. */
    private static final int[] PREFERENCE = {4, 0, 2, 6, 8, 1, 3, 5, 7};
    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}};

    /** How the game ends from here if both sides play their best. */
    public enum Outcome { WIN, DRAW, LOSS }

    /** The move to make and where it leads; {@code cell} is -1 when it is not the player's move. */
    public record Advice(int cell, Outcome outcome) {
        public static final Advice NONE = new Advice(-1, null);

        public boolean any() {
            return cell >= 0;
        }
    }

    private final char[] cells;

    private TicTacToeBoard(char[] cells) {
        this.cells = cells;
    }

    public static TicTacToeBoard empty() {
        char[] cells = new char[9];
        Arrays.fill(cells, EMPTY);
        return new TicTacToeBoard(cells);
    }

    /** A board from nine characters, row by row: X, O, and anything else for an empty cell. */
    public static TicTacToeBoard of(String nine) {
        if (nine == null || nine.length() != 9) throw new IllegalArgumentException("need 9 cells: " + nine);
        char[] cells = new char[9];
        for (int i = 0; i < 9; i++) {
            char c = Character.toUpperCase(nine.charAt(i));
            cells[i] = c == X || c == O ? c : EMPTY;
        }
        return new TicTacToeBoard(cells);
    }

    /** This board with {@code mark} in {@code cell}. */
    public TicTacToeBoard with(int cell, char mark) {
        char[] copy = cells.clone();
        copy[cell] = mark;
        return new TicTacToeBoard(copy);
    }

    public char at(int cell) {
        return cells[cell];
    }

    public int count(char mark) {
        int n = 0;
        for (char c : cells) if (c == mark) n++;
        return n;
    }

    /** X or O if that side has three in a row, otherwise {@link #EMPTY}. */
    public char winner() {
        return winner(cells);
    }

    public boolean full() {
        return count(EMPTY) == 0;
    }

    /**
     * Whether the player (O) is to move: the computer has made one move more, nobody has won, and a
     * cell is free. Right after the player's move the counts are equal until the computer answers,
     * and nothing should be shown in between.
     */
    public boolean playersTurn() {
        return count(X) == count(O) + 1 && winner() == EMPTY && !full();
    }

    /** The best move for the player and where it leads, or {@link Advice#NONE} when it is not their move. */
    public Advice advise() {
        if (!playersTurn()) return Advice.NONE;
        int[] scores = scores();
        int best = -1;
        for (int cell : PREFERENCE) {
            if (scores[cell] == Integer.MIN_VALUE) continue;
            if (best < 0 || scores[cell] > scores[best]) best = cell;
        }
        int score = scores[best];
        return new Advice(best, score > 0 ? Outcome.WIN : score < 0 ? Outcome.LOSS : Outcome.DRAW);
    }

    /** Every move that scores as well as the best one, in cell order; empty when it is not the player's move. */
    public List<Integer> bestMoves() {
        List<Integer> out = new ArrayList<>();
        if (!playersTurn()) return out;
        int[] scores = scores();
        int top = Integer.MIN_VALUE;
        for (int s : scores) top = Math.max(top, s);
        for (int cell = 0; cell < 9; cell++) {
            if (scores[cell] != Integer.MIN_VALUE && scores[cell] == top) out.add(cell);
        }
        return out;
    }

    /**
     * The minimax score of each move for O, from O's side: positive wins, negative loses, zero draws.
     * {@code Integer.MIN_VALUE} for an occupied cell.
     */
    int[] scores() {
        int[] memo = new int[19683];
        Arrays.fill(memo, Integer.MIN_VALUE);
        int[] out = new int[9];
        for (int cell = 0; cell < 9; cell++) {
            if (cells[cell] != EMPTY) {
                out[cell] = Integer.MIN_VALUE;
                continue;
            }
            char[] next = cells.clone();
            next[cell] = O;
            out[cell] = search(next, X, memo);
        }
        return out;
    }

    /**
     * The score for O of the position with {@code toMove} next, both sides playing their best. A
     * finished game scores 10 minus the marks on the board, so a quicker win is worth more and a
     * quicker loss costs more.
     */
    private static int search(char[] board, char toMove, int[] memo) {
        int key = encode(board);
        if (memo[key] != Integer.MIN_VALUE) return memo[key];
        char won = winner(board);
        int marks = 0;
        for (char c : board) if (c != EMPTY) marks++;
        int result;
        if (won == O) {
            result = 10 - marks;
        } else if (won == X) {
            result = -(10 - marks);
        } else if (marks == 9) {
            result = 0;
        } else {
            result = toMove == O ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE;
            char other = toMove == O ? X : O;
            for (int cell = 0; cell < 9; cell++) {
                if (board[cell] != EMPTY) continue;
                board[cell] = toMove;
                int s = search(board, other, memo);
                board[cell] = EMPTY;
                result = toMove == O ? Math.max(result, s) : Math.min(result, s);
            }
        }
        memo[key] = result;
        return result;
    }

    private static int encode(char[] board) {
        int key = 0;
        for (char c : board) key = key * 3 + (c == X ? 1 : c == O ? 2 : 0);
        return key;
    }

    private static char winner(char[] board) {
        for (int[] line : LINES) {
            char c = board[line[0]];
            if (c != EMPTY && c == board[line[1]] && c == board[line[2]]) return c;
        }
        return EMPTY;
    }

    @Override
    public String toString() {
        return new String(cells);
    }

    // --- reading the board in the room ---

    /** The mark a map shows, from the color byte of its centre pixel; {@link #EMPTY} if it is neither. */
    public static char markOfPixel(int colorByte) {
        int c = colorByte & 0xFF;
        if (c == PIXEL_X) return X;
        if (c == PIXEL_O) return O;
        return EMPTY;
    }

    /** The cell a map at this room position marks, or -1 if the position is not on the board. */
    public static int cellAt(int relX, int relY, int relZ) {
        if (relX != PLANE_X) return -1;
        int row = TOP_Y - relY;
        int col = LEFT_Z - relZ;
        if (row < 0 || row > 2 || col < 0 || col > 2) return -1;
        return row * 3 + col;
    }

    /** The button for a cell, in the room frame. */
    public static BlockPos buttonRel(int cell) {
        return new BlockPos(PLANE_X, TOP_Y - cell / 3, LEFT_Z - cell % 3);
    }
}
