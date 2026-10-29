package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.ThreeWeirdosRiddle.Statement;
import net.alpaka.addons.features.dungeon.puzzle.ThreeWeirdosRiddle.Verdict;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Three Weirdos: the statements and the decision (puzzles-chat.md 2.3, 2.4, 2.8). */
class ThreeWeirdosRiddleTest {

    private static final Verdict R = Verdict.REWARD;
    private static final Verdict E = Verdict.EMPTY;

    /** The six cases of the official wiki, as the three NPCs say them; the first one has the reward each time. */
    private static final List<List<String>> CASES = List.of(
            List.of("The reward is not in my chest!",
                    "One of us is telling the truth!",
                    "They are both telling the truth. The reward isn't in Ardis's chest."),
            List.of("The reward isn't in any of our chests.",
                    "The reward is not in my chest. They are both lying.",
                    "The reward is in my chest!"),
            List.of("My chest doesn't have the reward. We are all telling the truth.",
                    "My chest doesn't have the reward. At least one of the others is telling the truth!",
                    "One of the others is lying!"),
            List.of("My chest has the reward and I'm telling the truth!",
                    "They are both lying, the reward is in my chest!",
                    "They are both telling the truth, the reward is in Baxter's chest!"),
            List.of("At least one of them is lying, and the reward is not in Carver's chest!",
                    "We are all telling the truth!",
                    "Benson is telling the truth and the reward is in his chest."),
            List.of("Both of them are telling the truth. Also, Benson has the reward in their chest!",
                    "Carver is telling the truth.",
                    "My chest has the reward!"));

    @Test
    void everyCaseHasExactlyOneRewardStatement() {
        for (List<String> lines : CASES) {
            assertEquals(R, ThreeWeirdosRiddle.classify(lines.get(0)), lines.get(0));
            assertEquals(E, ThreeWeirdosRiddle.classify(lines.get(1)), lines.get(1));
            assertEquals(E, ThreeWeirdosRiddle.classify(lines.get(2)), lines.get(2));
        }
    }

    @Test
    void fixturesFromTheSpec() {
        assertStatement("[NPC] Baxter: My chest doesn't have the reward. We are all telling the truth.", "Baxter", R);
        assertStatement("[NPC] Hope: The reward isn't in any of our chests.", "Hope", R);
        assertStatement("[NPC] Ardis: The reward is not in my chest!", "Ardis", R);
        assertStatement("[NPC] Carver: My chest has the reward and I'm telling the truth!", "Carver", R);
        assertStatement("[NPC] Rose: At least one of them is lying, and the reward is not in Hugo's chest!", "Rose", R);
        assertStatement("[NPC] Victoria: Both of them are telling the truth. Also, Elmo has the reward in their chest!", "Victoria", R);
        assertStatement("[NPC] Benson: One of us is telling the truth!", "Benson", E);
        assertStatement("[NPC] Lino: They are both telling the truth. The reward isn't in Ardis's chest.", "Lino", E);
        assertStatement("[NPC] Morris: Winona is telling the truth and the reward is in his chest.", "Morris", E);
        assertStatement("[NPC] Ramsey: Willmar is telling the truth.", "Ramsey", E);
        assertStatement("[NPC] Madelia: The reward is in my chest.", "Madelia", E);
        assertStatement("[NPC] Madelia: The reward is in my chest!", "Madelia", E);
        assertStatement("[NPC] Melrose: One of the others is lying!", "Melrose", E);
        assertStatement("[NPC] Melrose: One of the others is lying.", "Melrose", E);
    }

    @Test
    void aStatementThatStartsAnotherIsNotTakenForIt() {
        // "The reward is not in my chest" is how this one begins, but it is the other case.
        assertStatement("[NPC] Hugo: The reward is not in my chest. They are both lying.", "Hugo", E);
        // "My chest has the reward" begins the reward line "... and I'm telling the truth!".
        assertStatement("[NPC] Eveleth: My chest has the reward!", "Eveleth", E);
        assertStatement("[NPC] Eveleth: My chest has the reward and I'm telling the truth!", "Eveleth", R);
        // "<name> is telling the truth" begins "<name> is telling the truth and the reward is in his chest".
        assertStatement("[NPC] Ramsey: Willmar is telling the truth and the reward is in his chest.", "Ramsey", E);
    }

    @Test
    void punctuationAndApostrophesFromOtherSourcesStillMatch() {
        // The fandom wiki's wording of the same lines.
        assertEquals(R, ThreeWeirdosRiddle.classify("My chest doesn't have the reward we are all telling the truth."));
        assertEquals(R, ThreeWeirdosRiddle.classify("At least one of them is lying, and the reward is not in Hugo's chest."));
        assertEquals(R, ThreeWeirdosRiddle.classify("Both of them are telling the truth. Also, Elmo has the reward in their chest."));
        assertEquals(E, ThreeWeirdosRiddle.classify("They are both telling the truth, the reward is in Hugo's chest."));
        assertEquals(E, ThreeWeirdosRiddle.classify("My chest has the reward."));
        // A typographic apostrophe.
        assertEquals(R, ThreeWeirdosRiddle.classify("My chest has the reward and I’m telling the truth!"));
        assertEquals(R, ThreeWeirdosRiddle.classify("The reward isn’t in any of our chests."));
    }

    @Test
    void otherLinesAreIgnored() {
        assertNull(ThreeWeirdosRiddle.parse("[NPC] Mort: Here, I found this map when I first entered the dungeon."));
        // Party chat quoting a statement is not the NPC.
        assertNull(ThreeWeirdosRiddle.parse("Party > [MVP+] Foo: [NPC] Baxter: The reward is not in my chest!"));
        assertNull(ThreeWeirdosRiddle.parse("Baxter: The reward is not in my chest!"));
        assertNull(ThreeWeirdosRiddle.parse("PUZZLE SOLVED! Alpaka05 wasn't fooled by Rose! Good job!"));
        assertNull(ThreeWeirdosRiddle.parse(""));
        assertNull(ThreeWeirdosRiddle.parse(null));
        assertNull(ThreeWeirdosRiddle.classify("The reward is somewhere."));
        assertNull(ThreeWeirdosRiddle.classify("!"));
        // The speaker of an unknown line is still known, for the log.
        assertEquals("Mort", ThreeWeirdosRiddle.speaker("[NPC] Mort: Good luck."));
        assertTrue(ThreeWeirdosRiddle.knownName("Baxter"));
        assertTrue(!ThreeWeirdosRiddle.knownName("Mort"));
    }

    @Test
    void theAnswerByElimination() {
        // Hugo (slot 0) and Benson (slot 1) both have empty chests: Rose's (slot 2) holds the reward.
        assertEquals(2, ThreeWeirdosRiddle.answer(new Verdict[]{E, E, null}));
        assertArrayEquals(new boolean[]{true, true, false}, ThreeWeirdosRiddle.empty(new Verdict[]{E, E, null}));
        assertEquals(0, ThreeWeirdosRiddle.answer(new Verdict[]{null, E, E}));
    }

    @Test
    void oneEmptyChestIsNotEnough() {
        Verdict[] hugoOnly = {E, null, null};
        assertEquals(-1, ThreeWeirdosRiddle.answer(hugoOnly));
        assertArrayEquals(new boolean[]{true, false, false}, ThreeWeirdosRiddle.empty(hugoOnly));
        assertEquals(-1, ThreeWeirdosRiddle.answer(new Verdict[3]));
    }

    @Test
    void aRewardStatementDecidesAtOnce() {
        assertEquals(2, ThreeWeirdosRiddle.answer(new Verdict[]{null, null, R}));
        assertEquals(2, ThreeWeirdosRiddle.answer(new Verdict[]{E, null, R}));
        // Once the reward's chest is known, the other two are empty, asked or not.
        assertArrayEquals(new boolean[]{true, true, false}, ThreeWeirdosRiddle.empty(new Verdict[]{null, null, R}));
    }

    @Test
    void contradictionsDecideNothing() {
        assertEquals(-1, ThreeWeirdosRiddle.answer(new Verdict[]{R, R, null}));
        assertEquals(-1, ThreeWeirdosRiddle.answer(new Verdict[]{E, E, E}));
    }

    @Test
    void positionsInTheRoom() {
        assertEquals(new BlockPos(14, 69, 24), ThreeWeirdosRiddle.chestRel(0));
        assertEquals(new BlockPos(16, 69, 25), ThreeWeirdosRiddle.chestRel(1));
        assertEquals(new BlockPos(18, 69, 24), ThreeWeirdosRiddle.chestRel(2));
        assertEquals(0, ThreeWeirdosRiddle.slotAt(13, 24));
        assertEquals(1, ThreeWeirdosRiddle.slotAt(15, 25));
        assertEquals(2, ThreeWeirdosRiddle.slotAt(17, 24));
        assertEquals(-1, ThreeWeirdosRiddle.slotAt(14, 24));
        assertEquals(-1, ThreeWeirdosRiddle.slotAt(15, 24));
    }

    private static void assertStatement(String line, String npc, Verdict verdict) {
        Statement s = ThreeWeirdosRiddle.parse(line);
        assertNotNull(s, line);
        assertEquals(npc, s.npc(), line);
        assertEquals(verdict, s.verdict(), line);
    }
}
