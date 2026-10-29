package net.alpaka.addons.features.dungeon.puzzle;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped solution tables (puzzles-water.md 7.2 and appendix A). */
class WaterBoardSolutionsTest {

    static WaterBoardSolutions shipped() throws IOException {
        try (InputStream in = WaterBoardSolutions.class.getResourceAsStream(WaterBoardSolutions.RESOURCE)) {
            assertNotNull(in, "the data file is on the classpath");
            return WaterBoardSolutions.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void theShippedTablesAreSound() throws IOException {
        WaterBoardSolutions solutions = shipped();
        assertEquals(List.of(), solutions.problems());
        for (WaterBoardSolutions.Table table : WaterBoardSolutions.Table.values()) {
            assertEquals(Set.of(1, 2, 3, 4), solutions.variants(table), table.key());
            for (int variant = 1; variant <= 4; variant++) {
                assertEquals(Set.copyOf(WaterBoardSolutions.allDoorKeys()), solutions.doorKeys(table, variant));
            }
        }
    }

    @Test
    void tenDoorKeys() {
        assertEquals(List.of("012", "013", "014", "023", "024", "034", "123", "124", "134", "234"),
                WaterBoardSolutions.allDoorKeys());
    }

    @Test
    void lookUpStandardAndFast() throws IOException {
        WaterBoardSolutions solutions = shipped();
        Map<WaterBoardLever, double[]> standard = solutions.get(WaterBoardSolutions.Table.STANDARD, 2, "013");
        assertEquals(Set.of(WaterBoardLever.QUARTZ, WaterBoardLever.EMERALD, WaterBoardLever.WATER), standard.keySet());
        assertArrayEquals(new double[] {0.0, 3.6}, standard.get(WaterBoardLever.QUARTZ));
        assertArrayEquals(new double[] {0.0, 4.7, 12.1}, standard.get(WaterBoardLever.EMERALD));
        assertArrayEquals(new double[] {0.0, 7.5}, standard.get(WaterBoardLever.WATER));

        Map<WaterBoardLever, double[]> first = solutions.get(WaterBoardSolutions.Table.STANDARD, 1, "012");
        assertArrayEquals(new double[] {0.0, 10.0}, first.get(WaterBoardLever.QUARTZ));
        assertArrayEquals(new double[] {7.9}, first.get(WaterBoardLever.EMERALD));
        assertNull(first.get(WaterBoardLever.COAL));

        Map<WaterBoardLever, double[]> fast = solutions.get(WaterBoardSolutions.Table.FAST, 4, "024");
        assertArrayEquals(new double[] {0.6, 1.8, 5.0, 6.5}, fast.get(WaterBoardLever.COAL));
        assertArrayEquals(new double[] {3.3, 7.9, 9.0}, fast.get(WaterBoardLever.TERRACOTTA));
        // Odin writes some times as integers; they read as numbers all the same.
        assertArrayEquals(new double[] {1.3, 4.8, 8.0, 9.4, 10.5},
                solutions.get(WaterBoardSolutions.Table.FAST, 3, "134").get(WaterBoardLever.EMERALD));
    }

    @Test
    void lookupsAreCopiesAndUnknownKeysAreNull() throws IOException {
        WaterBoardSolutions solutions = shipped();
        solutions.get(WaterBoardSolutions.Table.STANDARD, 2, "013").get(WaterBoardLever.QUARTZ)[0] = 99.0;
        assertEquals(0.0, solutions.get(WaterBoardSolutions.Table.STANDARD, 2, "013").get(WaterBoardLever.QUARTZ)[0]);
        assertNull(solutions.get(WaterBoardSolutions.Table.STANDARD, 5, "013"));
        assertNull(solutions.get(WaterBoardSolutions.Table.STANDARD, 2, "01"));
        assertNull(solutions.get(WaterBoardSolutions.Table.STANDARD, 2, "0123"));
    }

    @Test
    void theCheckFindsBrokenTables() {
        String json = """
                {"sets": {
                  "standard": {"3": {"012": {"coal": [1.0], "gold": [2.0, 2.0], "water": [0.5]}}},
                  "fast": {}
                }}""";
        List<String> problems = WaterBoardSolutions.parse(new StringReader(json)).problems();
        assertTrue(problems.contains("standard/3/012: water does not start at 0"), problems.toString());
        assertTrue(problems.contains("standard/3/012/gold: not ascending"), problems.toString());
        assertTrue(problems.contains("standard/3/012: uses coal, which this layout has no blocks of"), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("fast: layouts")), problems.toString());
    }

    @Test
    void unknownLeversAreRejected() {
        String json = """
                {"sets": {"standard": {"1": {"012": {"hardened_clay": [0.0]}}}, "fast": {}}}""";
        assertThrows(JsonParseException.class, () -> WaterBoardSolutions.parse(new StringReader(json)));
        assertThrows(JsonParseException.class, () -> WaterBoardSolutions.parse(new StringReader("{}")));
    }
}
