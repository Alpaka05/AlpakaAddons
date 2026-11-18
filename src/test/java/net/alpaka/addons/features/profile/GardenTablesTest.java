package net.alpaka.addons.features.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GardenTablesTest {
    /** The start of NEU's constants/garden.json. */
    private static final String GARDEN = """
            {"garden_exp": [0, 70], "crop_milestones": {"WHEAT": [30, 50, 80], "SUGAR_CANE": [60, 100, 160]}}""";

    @Test
    void aCropsMilestoneIsReadOffItsTable() {
        Map<String, long[]> tables = GardenTables.parse(GARDEN);
        assertArrayEquals(new long[] {30, 50, 80}, tables.get("WHEAT"));
        assertEquals(0, GardenTables.milestone(tables, "Wheat", 29));
        assertEquals(1, GardenTables.milestone(tables, "Wheat", 30));
        // Each milestone takes its amount on top of the ones before.
        assertEquals(1, GardenTables.milestone(tables, "Wheat", 79));
        assertEquals(2, GardenTables.milestone(tables, "Wheat", 80));
        assertEquals(3, GardenTables.milestone(tables, "Wheat", 1_000_000));
        // By the crop's name as it is written.
        assertEquals(2, GardenTables.milestone(tables, "Sugar Cane", 160));
        assertEquals(-1, GardenTables.milestone(tables, "Starfruit", 5));
    }

    @Test
    void rabbitsCountByTheirRarity() {
        Map<String, String> rarities = Hoppity.rarities("""
                {"hoppity": {"rarities": {"common": {"rabbits": ["aaron", "able"]}, "mythic": {"rabbits": ["zorro"]}}}}""");
        assertEquals("mythic", rarities.get("zorro"));
        JsonObject found = JsonParser.parseString("{\"aaron\": 3, \"zorro\": 2, \"able\": 0, \"unknown\": 4}").getAsJsonObject();
        int[][] counts = Hoppity.found(found, rarities);
        // Common is the first rarity, mythic the sixth: one of each found, with duplicates three and two.
        assertEquals(1, counts[0][0]);
        assertEquals(3, counts[1][0]);
        assertEquals(1, counts[0][5]);
        assertEquals(2, counts[1][5]);
    }
}
