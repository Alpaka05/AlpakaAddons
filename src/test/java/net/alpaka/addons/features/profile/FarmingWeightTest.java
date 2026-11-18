package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FarmingWeightTest {
    private static final double[] MITE = {0, 392.81451, 669.92375, 947.033, 1224.14223, 1390.40778, 1556.67335};
    private static final double[] BEETLE = {0, 853.9207, 1428.26244, 2002.60421, 2576.94593, 2921.55098, 3266.15608};
    private static final double[] RAT = {0, 14.10777, 24.06004, 34.0123, 43.96457, 49.93593, 55.90729};

    @Test
    void pestDropsComeOffAsEliteTakesThemOff() {
        // Kill counts of real profiles, with what Elite's own API said was uncounted for them.
        assertEquals(836807, FarmingWeight.uncounted(MITE, 926));
        assertEquals(143577, FarmingWeight.uncounted(MITE, 285));
        assertEquals(1547600, FarmingWeight.uncounted(BEETLE, 840));
        assertEquals(245511, FarmingWeight.uncounted(BEETLE, 242));
        assertEquals(35420, FarmingWeight.uncounted(RAT, 1048));
        // The first fifty kills of a pest drop nothing that is taken off.
        assertEquals(0, FarmingWeight.uncounted(MITE, 50));
        assertEquals(0, FarmingWeight.uncounted(MITE, 0));
    }

    @Test
    void cropsAndBonusesAddUp() {
        JsonElement member = JsonParser.parseString("""
                {"collection": {"WHEAT": 1000000, "CACTUS": 361390.3, "MUSHROOM_COLLECTION": 272832.81},
                 "bestiary": {"kills": {"pest_mite_1": 60, "pest_mouse_1": 7}},
                 "player_data": {"experience": {"SKILL_FARMING": 60000000}},
                 "jacobs_contest": {"perks": {"double_drops": 4, "farming_level_cap": 10}}}""");
        JsonElement profile = JsonParser.parseString("""
                {"coop": {"crafted_generators": ["WHEAT_12", "CACTUS_12", "COW_12", "WHEAT_11"]}}""");
        JsonObject jacob = JsonParser.parseString("""
                {"earnedMedals": {"diamond": 1, "platinum": 0, "gold": 2, "silver": 9}}""").getAsJsonObject();
        JsonObject weight = FarmingWeight.of(member, profile, jacob);

        assertEquals(10, Json.num(weight, "cropWeight.Wheat", 0), 1e-9);
        // Sixty mites dropped 3,929 cactus that was not farmed.
        assertEquals(2, Json.num(weight, "cropWeight.Cactus", 0), 1e-6);
        // Two of the fifteen points are from a crop that breaks two blocks at once, where mushrooms count half.
        assertEquals(2.8, Json.num(weight, "cropWeight.Mushroom", 0), 1e-6);
        assertEquals(100, Json.num(weight, "bonusWeight.Farming Level 50", 0));
        assertFalse(Json.obj(weight, "bonusWeight").has("Farming Level 60"));
        // Only farming minions count, and only at tier 12.
        assertEquals(10, Json.num(weight, "bonusWeight.Farming Minions", 0));
        assertEquals(8, Json.num(weight, "bonusWeight.Anita", 0));
        assertEquals(1.25, Json.num(weight, "bonusWeight.Contest Medals", 0), 1e-9);
        assertEquals(14.8 + 100 + 10 + 8 + 1.25, Json.num(weight, "totalWeight", 0), 1e-6);
        assertEquals(60, Json.num(weight, "pests.mite", 0));
        assertEquals(7, Json.num(weight, "pests.mouse", 0));
    }

    @Test
    void farmingSixtyNeedsTheLevelCap() {
        JsonElement member = JsonParser.parseString("""
                {"player_data": {"experience": {"SKILL_FARMING": 200000000}},
                 "jacobs_contest": {"perks": {"farming_level_cap": 10}}}""");
        JsonObject weight = FarmingWeight.of(member, new JsonObject(), new JsonObject());
        assertEquals(250, Json.num(weight, "bonusWeight.Farming Level 60", 0));
        assertEquals(250, Json.num(weight, "totalWeight", 0), 1e-9);
    }
}
