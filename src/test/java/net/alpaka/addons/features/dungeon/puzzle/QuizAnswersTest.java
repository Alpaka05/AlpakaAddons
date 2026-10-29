package net.alpaka.addons.features.dungeon.puzzle;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Quiz answer table (puzzles-chat.md 3.4, 3.5) as it ships in assets/alpaka/dungeon/quiz_answers.json. */
class QuizAnswersTest {

    /** 2026-10-25 02:00 UTC, in the middle of SkyBlock year 522. */
    private static final long MID_YEAR_522 = 1_792_893_600_000L;
    /** 100 seconds after year 522 began (17:55 epoch); by the 18:00 epoch it is still 521. */
    private static final long JUST_AFTER_522 = 1_792_850_200_000L;

    private static final QuizAnswers TABLE = QuizAnswers.bundled();

    @Test
    void theTableLoads() {
        assertTrue(TABLE.size() >= 20, "questions: " + TABLE.size());
    }

    @Test
    void singleAnswers() {
        assertEquals(Set.of("stalker"), answers("What is the status of The Watcher?"));
        assertEquals(Set.of("new necromancer"), answers("What is the status of Bonzo?"));
        assertEquals(Set.of("the wither lords"), answers("What is the status of Maxor, Storm, Goldor, and Necron?"));
        // The single-boss variants only Skyblocker and the wikis list.
        assertEquals(Set.of("the wither lords"), answers("What is the status of Goldor?"));
        assertEquals(Set.of("wool weaver"), answers("What is the name of the vendor in the Hub who sells stained glass?"));
        assertEquals(Set.of("61 minions"), answers("How many unique minions are there?"));
        assertEquals(Set.of("jamie"), answers("Which villager in the Village gives you a Rogue Sword?"));
    }

    @Test
    void spellingDoesNotMatter() {
        // Hypixel and the tables write the Spider's Den both ways.
        assertEquals(Set.of("rick"), answers("Which brother is on the Spider's Den?"));
        assertEquals(Set.of("rick"), answers("Which brother is on the Spiders Den?"));
        assertEquals(Set.of("rick"), answers("Which brother is on the Spider’s Den?"));
        assertEquals(Set.of("rick"), answers("  which brother is   on the spider's den?  "));
    }

    @Test
    void manyAnswers() {
        Set<String> dragons = answers("Which of these is not a dragon in The End?");
        assertTrue(dragons.contains("zoomer dragon"));
        assertTrue(dragons.contains("zombie dragon"));
        assertTrue(dragons.contains("stable dragon"));
        // The real dragons are never an answer.
        for (String real : List.of("Protector Dragon", "Old Dragon", "Wise Dragon", "Unstable Dragon",
                "Young Dragon", "Strong Dragon", "Superior Dragon")) {
            assertFalse(dragons.contains(QuizAnswers.normaliseOption(real)), real);
        }
        assertEquals(Set.of("zombie villager", "ghast"), answers("Which of these monsters only spawns at night?"));
        assertEquals(6, answers("Which of these enemies does not spawn in the Spiders Den?").size());
    }

    @Test
    void totalFairySoulsIsInDoubt() {
        // The real total is 289 since July 2026, the wiki saw 273 in the Quiz: both count.
        assertEquals(Set.of("289 fairy souls", "273 fairy souls"), answers("How many total Fairy Souls are there?"));
    }

    @Test
    void fairySoulsByZone() {
        assertEquals(Set.of("80 fairy souls"), answers("How many Fairy Souls are there in Hub?"));
        assertEquals(Set.of("80 fairy souls"), answers("How many Fairy Souls are there in The Hub?"));
        assertEquals(Set.of("19 fairy souls"), answers("How many Fairy Souls are there in Spiders Den?"));
        assertEquals(Set.of("19 fairy souls"), answers("How many Fairy Souls are there in the Spider's Den?"));
        assertEquals(Set.of("12 fairy souls"), answers("How many Fairy Souls are there in The End?"));
        assertEquals(Set.of("20 fairy souls"), answers("How many Fairy Souls are there in The Farming Islands?"));
        assertEquals(Set.of("29 fairy souls"), answers("How many Fairy Souls are there in the Crimson Isle?"));
        assertEquals(Set.of("12 fairy souls"), answers("How many Fairy Souls are there in The Park?"));
        assertEquals(Set.of("5 fairy souls"), answers("How many Fairy Souls are there in Jerry's Workshop?"));
        assertEquals(Set.of("21 fairy souls"), answers("How many Fairy Souls are there in Deep Caverns?"));
        assertEquals(Set.of("12 fairy souls"), answers("How many Fairy Souls are there in Gold Mine?"));
        assertEquals(Set.of("7 fairy souls"), answers("How many Fairy Souls are there in Dungeon Hub?"));
        assertEquals(Set.of("5 fairy souls"), answers("How many Fairy Souls are there in Backwater Bayou?"));
        // A zone nobody has seen in the Quiz is not guessed.
        assertNull(answers("How many Fairy Souls are there in Dwarven Mines?"));
    }

    @Test
    void unknownQuestions() {
        assertNull(answers("What is the status of Alpaka?"));
        assertNull(answers("glass?"));
        assertNull(answers(""));
        assertNull(TABLE.candidates(null, MID_YEAR_522));
        assertFalse(TABLE.knows("What is the status of Alpaka?"));
        assertTrue(TABLE.knows("What SkyBlock year is it?"));
    }

    @Test
    void theYear() {
        assertEquals(Set.of("year 522"), TABLE.candidates("What SkyBlock year is it?", MID_YEAR_522));
        assertEquals(List.of(522), QuizAnswers.yearCandidates(MID_YEAR_522));
        // Year 522 began 2026-10-24 13:55 UTC, year 523 begins 2026-10-29 17:55 UTC.
        assertEquals(List.of(522), QuizAnswers.yearCandidates(1_792_850_100_000L + QuizAnswers.YEAR_DOUBT_MS));
        assertEquals(List.of(522), QuizAnswers.yearCandidates(1_793_296_500_000L - QuizAnswers.YEAR_DOUBT_MS - 1));
    }

    @Test
    void theYearNearItsTurnIsInDoubt() {
        // The two epochs in use say 522 and 521 here.
        assertEquals(List.of(521, 522), QuizAnswers.yearCandidates(JUST_AFTER_522));
        assertEquals(Set.of("year 521", "year 522"), TABLE.candidates("What SkyBlock year is it?", JUST_AFTER_522));
        // Just before year 523 begins.
        assertEquals(List.of(522, 523), QuizAnswers.yearCandidates(1_793_296_500_000L - 60_000L));
    }

    @Test
    void normalising() {
        assertEquals("spiders den", QuizAnswers.normalise(" Spider’s   Den "));
        assertEquals("elle", QuizAnswers.normaliseOption("Elle."));
        assertEquals("61 minions", QuizAnswers.normaliseOption("61 Minions"));
    }

    @Test
    void readsATableOfItsOwn() {
        QuizAnswers small = QuizAnswers.read(new StringReader("""
                {"questions": [{"q": "Is this a test?", "a": ["Yes"]}],
                 "fairySoulZones": {"The Rift": 3}}"""));
        assertEquals(Set.of("yes"), small.candidates("is this a test?", MID_YEAR_522));
        assertEquals(Set.of("3 fairy souls"), small.candidates("How many Fairy Souls are there in Rift?", MID_YEAR_522));
        assertEquals(1, small.size());
    }

    private static Set<String> answers(String question) {
        return TABLE.candidates(question, MID_YEAR_522);
    }
}
