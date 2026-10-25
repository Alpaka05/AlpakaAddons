package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.QuizReader.Status;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Quiz conversation in chat, line by line (puzzles-chat.md 3.2, 3.3, 3.8). The lines are as the
 * solvers get them: formatting stripped, trimmed, blank lines left out.
 */
class QuizReaderTest {

    private static final long MID_YEAR_522 = 1_792_893_600_000L;
    private static final long JUST_AFTER_522 = 1_792_850_200_000L;

    private static final String A = "ⓐ";
    private static final String B = "ⓑ";
    private static final String C = "ⓒ";

    private static QuizReader reader() {
        return new QuizReader(QuizAnswers.bundled());
    }

    private static Status feed(QuizReader quiz, long now, String... lines) {
        Status status = quiz.status();
        for (String line : lines) status = quiz.accept(line, now);
        return status;
    }

    private static Status ask(QuizReader quiz, long now, List<String> question, String a, String b, String c) {
        feed(quiz, now, "Question #1");
        feed(quiz, now, question.toArray(new String[0]));
        return feed(quiz, now, A + " " + a, B + " " + b, C + " " + c);
    }

    @Test
    void aSimpleQuestion() {
        QuizReader quiz = reader();
        feed(quiz, MID_YEAR_522,
                "[STATUE] Oruo the Omniscient: I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know.",
                "Question #1",
                "What is the status of The Watcher?");
        // Nothing until all three options are in.
        assertEquals(Status.OPTIONS, quiz.accept(A + " Stalker", MID_YEAR_522));
        assertEquals(-1, quiz.answer());
        assertEquals(Status.OPTIONS, quiz.accept(B + " Master Necromancer", MID_YEAR_522));
        assertEquals(Status.ANSWER, quiz.accept(C + " Professor", MID_YEAR_522));
        assertEquals(0, quiz.answer());
        assertEquals("What is the status of The Watcher?", quiz.question());
        assertEquals(Arrays.asList("Stalker", "Master Necromancer", "Professor"), quiz.options());
    }

    @Test
    void aQuestionOnTwoLines() {
        QuizReader quiz = reader();
        Status status = ask(quiz, MID_YEAR_522,
                List.of("What is the name of the vendor in the Hub who sells stained", "glass?"),
                "Wool Weaver", "Marco", "Elle");
        assertEquals(Status.ANSWER, status);
        assertEquals(0, quiz.answer());
        assertEquals("What is the name of the vendor in the Hub who sells stained glass?", quiz.question());
    }

    @Test
    void aQuestionWithManyAnswersTakesTheOneListed() {
        QuizReader quiz = reader();
        assertEquals(Status.ANSWER, ask(quiz, MID_YEAR_522, List.of("Which of these is not a dragon in The End?"),
                "Protector Dragon", "Zoomer Dragon", "Old Dragon"));
        assertEquals(1, quiz.answer());
    }

    @Test
    void twoListedOptionsMarkNothing() {
        QuizReader quiz = reader();
        assertEquals(Status.AMBIGUOUS, ask(quiz, MID_YEAR_522, List.of("Which of these is not a dragon in The End?"),
                "Stonk Dragon", "Holy Dragon", "Wise Dragon"));
        assertEquals(-1, quiz.answer());
    }

    @Test
    void apostropheVariants() {
        QuizReader quiz = reader();
        assertEquals(Status.ANSWER, ask(quiz, MID_YEAR_522, List.of("How many Fairy Souls are there in Spiders Den?"),
                "12 Fairy Souls", "19 Fairy Souls", "20 Fairy Souls"));
        assertEquals(1, quiz.answer());
    }

    @Test
    void theYear() {
        QuizReader quiz = reader();
        assertEquals(Status.ANSWER, ask(quiz, MID_YEAR_522, List.of("What SkyBlock year is it?"),
                "Year 521", "Year 522", "Year 523"));
        assertEquals(1, quiz.answer());
    }

    @Test
    void theYearRightAfterItsTurnIsNotGuessed() {
        QuizReader quiz = reader();
        assertEquals(Status.AMBIGUOUS, ask(quiz, JUST_AFTER_522, List.of("What SkyBlock year is it?"),
                "Year 521", "Year 522", "Year 523"));
        assertEquals(-1, quiz.answer());
        // With only one of the two years among the options it is safe again.
        QuizReader other = reader();
        assertEquals(Status.ANSWER, ask(other, JUST_AFTER_522, List.of("What SkyBlock year is it?"),
                "Year 520", "Year 522", "Year 524"));
        assertEquals(1, other.answer());
    }

    @Test
    void totalFairySouls() {
        QuizReader quiz = reader();
        assertEquals(Status.ANSWER, ask(quiz, MID_YEAR_522, List.of("How many total Fairy Souls are there?"),
                "273 Fairy Souls", "254 Fairy Souls", "301 Fairy Souls"));
        assertEquals(0, quiz.answer());
        QuizReader both = reader();
        assertEquals(Status.AMBIGUOUS, ask(both, MID_YEAR_522, List.of("How many total Fairy Souls are there?"),
                "273 Fairy Souls", "289 Fairy Souls", "301 Fairy Souls"));
    }

    @Test
    void aSuggestionIsNotAnOption() {
        QuizReader quiz = reader();
        ask(quiz, MID_YEAR_522, List.of("What is the status of The Watcher?"), "Stalker", "Master Necromancer", "Professor");
        assertEquals(Status.ANSWER, quiz.accept(
                "[STATUE] Oruo the Omniscient: Alpaka05 thinks the answer is " + A + " Stalker! Lock in your party's answer in my Chamber!",
                MID_YEAR_522));
        assertEquals(0, quiz.answer());
    }

    @Test
    void aSuggestionBeforeTheOptionsChangesNothing() {
        QuizReader quiz = reader();
        feed(quiz, MID_YEAR_522, "Question #1", "What is the status of The Watcher?",
                "[STATUE] Oruo the Omniscient: Alpaka05 thinks the answer is " + A + " Stalker! Lock in your party's answer in my Chamber!");
        // The suggestion stood between the question and its options, so the question is lost: no guess.
        assertEquals(Status.IDLE, feed(quiz, MID_YEAR_522, A + " Stalker", B + " Master Necromancer", C + " Professor"));
    }

    @Test
    void anAnswerClearsTheMark() {
        QuizReader quiz = reader();
        ask(quiz, MID_YEAR_522, List.of("What is the status of The Watcher?"), "Stalker", "Master Necromancer", "Professor");
        assertEquals(Status.IDLE, quiz.accept("[STATUE] Oruo the Omniscient: Alpaka05 answered Question #1 correctly!", MID_YEAR_522));
        assertEquals(-1, quiz.answer());
        assertNull(quiz.question());
    }

    @Test
    void theEndAndAFailClearTheMark() {
        for (String end : List.of(
                "[STATUE] Oruo the Omniscient: Alpaka05 answered the final question correctly!",
                "[STATUE] Oruo the Omniscient: I bestow upon you all the power of a hundred years!",
                "[STATUE] Oruo the Omniscient: Yikes",
                "[STATUE] Oruo the Omniscient: Alpaka05 chose the wrong answer! I shall never forget this moment of misrememberance.")) {
            QuizReader quiz = reader();
            ask(quiz, MID_YEAR_522, List.of("What is the status of The Watcher?"), "Stalker", "Master Necromancer", "Professor");
            assertEquals(Status.IDLE, quiz.accept(end, MID_YEAR_522), end);
        }
    }

    @Test
    void anUnknownQuestionMarksNothing() {
        QuizReader quiz = reader();
        assertEquals(Status.UNKNOWN, ask(quiz, MID_YEAR_522, List.of("What is the status of Alpaka?"), "Llama", "Camel", "Vicuna"));
        assertEquals("What is the status of Alpaka?", quiz.question());
        assertEquals(-1, quiz.answer());
    }

    @Test
    void anOptionTheTableDoesNotHaveMarksNothing() {
        // The table says 61 minions; if Hypixel moves on to 62, the stale 61 is not among the options.
        QuizReader quiz = reader();
        assertEquals(Status.UNKNOWN, ask(quiz, MID_YEAR_522, List.of("How many unique minions are there?"),
                "60 Minions", "62 Minions", "64 Minions"));
    }

    @Test
    void threeQuestionsInARow() {
        QuizReader quiz = reader();
        ask(quiz, MID_YEAR_522, List.of("What is the status of Bonzo?"), "Necromancer Lord", "Professor", "New Necromancer");
        assertEquals(2, quiz.answer());
        feed(quiz, MID_YEAR_522,
                "[STATUE] Oruo the Omniscient: Alpaka05 answered Question #1 correctly!",
                "[STATUE] Oruo the Omniscient: 2 questions left... Then you will have proven your worth to me!",
                "Question #2",
                "What is the name of Rick's brother?");
        assertEquals(-1, quiz.answer());
        feed(quiz, MID_YEAR_522, A + " Pat", B + " Rick", C + " Kat");
        assertEquals(0, quiz.answer());
        feed(quiz, MID_YEAR_522,
                "[STATUE] Oruo the Omniscient: Alpaka05 answered Question #2 correctly!",
                "[STATUE] Oruo the Omniscient: One more question!",
                "Question #3",
                "What is the name of the lady of the Nether?",
                A + " Kat", B + " Jamie", C + " Elle");
        assertEquals(2, quiz.answer());
    }

    @Test
    void otherChatBetweenTheLinesIsSkipped() {
        QuizReader quiz = reader();
        feed(quiz, MID_YEAR_522,
                "Question #1",
                "What is the name of the vendor in the Hub who sells stained",
                "Party > [MVP+] Foo: which one is it",
                "glass?",
                "[NPC] Mort: Good luck.",
                A + " Marco", B + " Wool Weaver", C + " Kat");
        assertEquals(Status.ANSWER, quiz.status());
        assertEquals(1, quiz.answer());
    }

    @Test
    void aMissedHeadingDoesNotMatter() {
        QuizReader quiz = reader();
        feed(quiz, MID_YEAR_522,
                "The BLOOD DOOR has been opened!",
                "What is the status of Thorn?",
                A + " Shaman Necromancer", B + " Necromancer Lord", C + " Stalker");
        assertEquals(0, quiz.answer());
    }

    @Test
    void optionsWithoutAQuestionAreIgnored() {
        QuizReader quiz = reader();
        assertEquals(Status.IDLE, feed(quiz, MID_YEAR_522, A + " Stalker", B + " Master Necromancer", C + " Professor"));
        // A question that was cut off (a rejoin in the middle) is not guessed from the options.
        assertEquals(Status.IDLE, feed(quiz, MID_YEAR_522, "glass", A + " Wool Weaver", B + " Marco", C + " Elle"));
    }
}
