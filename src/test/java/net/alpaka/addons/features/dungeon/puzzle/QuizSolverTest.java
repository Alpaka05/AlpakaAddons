package net.alpaka.addons.features.dungeon.puzzle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The Quiz solver's chat side, without a world: what it has worked out, through its debug line. */
class QuizSolverTest {

    @Test
    void followsTheQuestionsInChat() {
        Quiz quiz = new Quiz();
        assertNull(quiz.debugState());
        quiz.onChat("Question #1");
        quiz.onChat("What is the status of The Watcher?");
        quiz.onChat("ⓐ Stalker");
        quiz.onChat("ⓑ Master Necromancer");
        quiz.onChat("ⓒ Professor");
        assertEquals("\"What is the status of The Watcher?\" [Stalker, Master Necromancer, Professor] -> answer ⓐ",
                quiz.debugState());
        // Without the Quiz room around the player nothing is claimed or drawn.
        assertFalse(quiz.claimsRoom());

        quiz.onChat("[STATUE] Oruo the Omniscient: Alpaka05 answered Question #1 correctly!");
        assertNull(quiz.debugState());
    }

    @Test
    void anUnknownQuestionIsSaidSo() {
        Quiz quiz = new Quiz();
        quiz.onChat("What is the status of Alpaka?");
        quiz.onChat("ⓐ Llama");
        quiz.onChat("ⓑ Camel");
        quiz.onChat("ⓒ Vicuna");
        assertEquals("\"What is the status of Alpaka?\" [Llama, Camel, Vicuna] -> not in the table", quiz.debugState());
        quiz.reset();
        assertNull(quiz.debugState());
    }
}
