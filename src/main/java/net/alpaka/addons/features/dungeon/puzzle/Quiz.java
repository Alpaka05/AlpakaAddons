package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Quiz (Oruo the Omniscient): looks each question Oruo asks in chat up in the answer table
 * and marks the station of the right answer (room positions (20,70,6), (15,70,9), (10,70,6)).
 * The question table goes into its own data class (QuizAnswers).
 *
 * Spec: puzzles-chat.md section 3 (chat lines, parsing, the answer table, positions).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class Quiz extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.QUIZ;
    }
}
