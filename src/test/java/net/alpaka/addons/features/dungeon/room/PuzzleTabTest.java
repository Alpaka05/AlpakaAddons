package net.alpaka.addons.features.dungeon.room;

import net.alpaka.addons.features.dungeon.puzzle.DungeonPuzzle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tab list's Puzzles section (rooms.md 4), from lines in any order. */
class PuzzleTabTest {

    /** The lines as SkyblockUtils hands them over: formatting stripped, trimmed, mixed in with other widgets. */
    private static List<String> tab() {
        return new ArrayList<>(List.of(
                "Party (5)",
                "Puzzles: (4)",
                "???: [✦]",
                "Creeper Beams: [✦]",
                "Quiz: [✔]",
                "Higher Or Lower: [✖] (PlayerName)",
                "Teleport Maze: [✦] ",
                "Crypts: 5",
                "Secrets Found: 12"));
    }

    @Test
    void readsEveryStatus() {
        PuzzleTab.Parsed parsed = PuzzleTab.parse(tab());
        assertTrue(parsed.present());
        assertEquals(4, parsed.count());
        assertEquals(1, parsed.undiscovered());
        assertEquals(PuzzleTab.Status.OPEN, parsed.statusOf(DungeonPuzzle.CREEPER_BEAMS));
        assertEquals(PuzzleTab.Status.SOLVED, parsed.statusOf(DungeonPuzzle.QUIZ));
        assertEquals(PuzzleTab.Status.FAILED, parsed.statusOf(DungeonPuzzle.HIGHER_OR_LOWER));
        assertEquals("PlayerName", parsed.entries().get(DungeonPuzzle.HIGHER_OR_LOWER).player());
        // Hypixel's trailing space after Teleport Maze.
        assertEquals(PuzzleTab.Status.OPEN, parsed.statusOf(DungeonPuzzle.TELEPORT_MAZE));
        assertNull(parsed.statusOf(DungeonPuzzle.BOULDER));
        assertTrue(PuzzleTab.Status.SOLVED.done());
        assertTrue(PuzzleTab.Status.FAILED.done());
        assertFalse(PuzzleTab.Status.OPEN.done());
    }

    @Test
    void theOrderDoesNotMatter() {
        PuzzleTab.Parsed expected = PuzzleTab.parse(tab());
        Random random = new Random(7);
        for (int i = 0; i < 20; i++) {
            List<String> lines = tab();
            Collections.shuffle(lines, random);
            assertEquals(expected, PuzzleTab.parse(lines));
        }
    }

    @Test
    void withTheLeadingSpaceToo() {
        PuzzleTab.Parsed parsed = PuzzleTab.parse(List.of(" Three Weirdos: [✦]", " Tic Tac Toe: [✔]"));
        assertEquals(PuzzleTab.Status.OPEN, parsed.statusOf(DungeonPuzzle.THREE_WEIRDOS));
        assertEquals(PuzzleTab.Status.SOLVED, parsed.statusOf(DungeonPuzzle.TIC_TAC_TOE));
        assertEquals(-1, parsed.count());
        assertTrue(parsed.present());
    }

    @Test
    void everyPuzzleNameIsKnown() {
        for (DungeonPuzzle puzzle : DungeonPuzzle.values()) {
            PuzzleTab.Parsed parsed = PuzzleTab.parse(List.of(puzzle.tabName() + ": [✔]"));
            assertEquals(PuzzleTab.Status.SOLVED, parsed.statusOf(puzzle), puzzle.tabName());
            assertEquals(puzzle, DungeonPuzzle.byTabName(puzzle.tabName()));
        }
    }

    @Test
    void lookalikesAndQuotesDoNotCount() {
        PuzzleTab.Parsed parsed = PuzzleTab.parse(List.of(
                "Bomb Defuse: [✦]",                         // removed in 0.20.5, not one of ours
                "Party > Someone: Quiz: [✔]",              // not anchored at the start
                "Quiz: [x]",                                 // not a status symbol
                "Higher or Lower: [✔]"));                   // Hypixel writes "Or"
        assertFalse(parsed.present());
        assertNull(parsed.statusOf(DungeonPuzzle.QUIZ));
    }

    @Test
    void noSectionMeansNothingIsBlocked() {
        PuzzleTab.Parsed parsed = PuzzleTab.parse(List.of("Area: Catacombs", "Server: m123AB"));
        assertFalse(parsed.present());
        for (DungeonPuzzle puzzle : DungeonPuzzle.values()) assertNull(parsed.statusOf(puzzle));
    }

    @Test
    void puzzleChatLines() {
        assertEquals("Alpaka", PuzzleChat.solvedBy("PUZZLE SOLVED! Alpaka wasn't fooled by Baxter! Good job!"));
        assertEquals("Alpaka", PuzzleChat.solvedBy("PUZZLE SOLVED! [MVP+] Alpaka tied Tic Tac Toe! Good job!"));
        assertEquals("Alpaka", PuzzleChat.failedBy("PUZZLE FAIL! Alpaka killed a Blaze in the wrong order! Yikes!"));
        assertNull(PuzzleChat.failedBy("Party > [VIP] x: PUZZLE FAIL! Alpaka lost Tic Tac Toe! Yikes!"));
        assertTrue(PuzzleChat.QUIZ_FAILED.matcher("[STATUE] Oruo the Omniscient: Yikes").matches());
        assertTrue(PuzzleChat.QUIZ_FAILED.matcher(
                "[STATUE] Oruo the Omniscient: Alpaka chose the wrong answer! I shall never forget this moment of misrememberance.").matches());
    }
}
