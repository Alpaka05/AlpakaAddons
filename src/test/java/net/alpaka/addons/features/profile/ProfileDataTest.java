package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileDataTest {

    /** Cut down from a real answer of Elite's /profile endpoint. */
    private static final String MEMBER = """
            {"profileId":"p1","playerUuid":"u1","profileName":"Apple",
             "api":{"inventories":true,"collections":true,"skills":true},
             "skyblockXp":54691,"purse":3046605291.7,"bankBalance":1253163663.8,"personalBank":1052275638.9,
             "networth":{"normal":259177212311.5,"liquid":86932742481.3},
             "collections":{"WHEAT":9396494,"CARROT_ITEM":12},"collectionTiers":{"WHEAT":11},
             "pets":[{"type":"GOLDEN_DRAGON","tier":"LEGENDARY","level":200,"maxLevel":200,"progress":0,"active":true,"heldItem":"MINOS_RELIC"},
                     {"type":"PARROT","tier":"EPIC","level":56,"maxLevel":100,"progress":0.3}],
             "petSummary":{"maxLevelPets":1,"totalSkins":3},
             "stats":{
               "skills":{"average":53.4,"totalXp":2675286773.8,"levels":{
                 "farming":{"level":50,"maxLevel":50,"xp":260056683.0,"xpCurrent":204884258.0,"xpForNext":0},
                 "hunting":{"level":21,"maxLevel":50,"xp":256850,"xpCurrent":56850,"xpForNext":400000}}},
               "dungeons":{"catacombsExperience":30741859.6,"catacombsLevel":38,"selectedClass":"mage","classAverage":44.8,
                 "classLevels":{"mage":{"level":43,"xp":118899330.3,"xpCurrent":9339690.3,"xpForNext":30000000}},
                 "floors":{"0":{"completions":1},"7":{"completions":19,"fastestTimeSPlus":245000}},
                 "masterFloors":{"7":{"completions":420,"fastestTimeS":330000}}},
               "slayer":{"totalXp":1000630,"bosses":{
                 "enderman":{"xp":400005,"level":8,"maxLevel":9,"progress":0.0000083,"xpForNext":599995,"bossKills":{"0":5,"3":100}},
                 "vampire":{"xp":625,"level":3,"maxLevel":5,"progress":0.64,"xpForNext":215,"bossKills":{"0":1}}}},
               "accessories":{"highestMagicalPower":2030}},
             "lastUpdated":1792966494}""";

    private static ProfileData data() {
        return new ProfileData(JsonParser.parseString(MEMBER).getAsJsonObject(), "Someone");
    }

    @Test
    void readsTheProfile() {
        ProfileData d = data();
        assertEquals("Apple", d.profileName);
        assertEquals(546.91, d.skyblockLevel, 1e-9);
        assertEquals(2, d.skills.size());
        assertEquals("farming", d.skills.get(0).id());
        assertTrue(d.skills.get(0).maxed());
        assertEquals(56850.0 / 400000, d.skills.get(1).progress(), 1e-9);
        assertEquals(38, d.catacombs.level());
        assertFalse(d.catacombs.maxed());
        assertEquals(30741859.6 - Levels.total(Levels.CATACOMBS, 38), d.catacombs.xpCurrent(), 1e-3);
        // Entrance, F1 to F7 and M1 to M7, played or not.
        assertEquals(15, d.floors.size());
        assertEquals(1 + 19 + 420, d.totalRuns);
        assertEquals(245000, d.floors.get(7).fastestSPlus());
        // Slayers in the game's order; Elite's xpForNext is what is still missing.
        assertEquals("enderman", d.slayers.get(0).id());
        assertEquals(599995, d.slayers.get(0).xpForNext());
        // Pets by rarity, then level.
        assertEquals("Golden Dragon", d.pets.get(0).name());
        assertTrue(d.pets.get(0).active());
    }

    @Test
    void metricsLeaveOutWhatIsMissing() {
        Map<String, Double> m = data().metrics();
        assertEquals(259177212311.5, m.get(Metrics.NETWORTH));
        assertEquals(256850.0, m.get(Metrics.SKILL + "hunting"));
        assertEquals(53.4, m.get(Metrics.SKILL_AVG));
        assertEquals(2030.0, m.get(Metrics.MAGICAL_POWER));
        assertNull(m.get(Metrics.SKILL + "mining"));
        assertNull(m.get(Metrics.GARDEN));
    }

    @Test
    void collectionsTakeTheirTiersFromTheResources() {
        JsonElement resources = JsonParser.parseString("""
                {"collections":{"FARMING":{"name":"Farming","items":{
                  "WHEAT":{"name":"Wheat","maxTiers":11,"tiers":[{"amountRequired":50},{"amountRequired":100}]},
                  "CARROT_ITEM":{"name":"Carrot","maxTiers":2,"tiers":[{"amountRequired":10},{"amountRequired":250}]}}}}}""");
        List<ProfileData.Collection> list = data().collections(resources);
        assertEquals(2, list.size());
        assertTrue(list.get(0).maxed());
        // Without a tier from Elite it is worked out from the amount.
        ProfileData.Collection carrot = list.get(1);
        assertEquals(1, carrot.tier());
        assertEquals(250, carrot.nextAt());
        assertEquals(10, carrot.prevAt());
    }

    @Test
    void eliteSkillHistoryBecomesSeriesWithAnAverage() {
        JsonElement graph = JsonParser.parseString("""
                [{"timestamp":1000,"skillsApi":true,"skills":{"farming":55172425,"mining":50,"runecrafting":94450}},
                 {"timestamp":2000,"skillsApi":false,"skills":{"farming":1}}]""");
        Map<String, List<ProfileHistory.Point>> series = ProfileSession.skillSeries(List.of(graph), Map.of("farming", 60));
        assertEquals(1, series.get(Metrics.SKILL + "farming").size());
        assertEquals(1_000_000L, series.get(Metrics.SKILL + "farming").get(0).timeMs());
        // Farming 50 and Mining 1; Runecrafting does not count towards the average.
        assertEquals(25.5, series.get(Metrics.SKILL_AVG).get(0).value(), 1e-9);
    }

    @Test
    void namesAndNumbersReadWell() {
        assertEquals("Golden Dragon", ProfileData.title("GOLDEN_DRAGON"));
        assertEquals("Farming XP", Metrics.label(Metrics.SKILL + "farming"));
        assertEquals("Voidgloom Seraph XP", Metrics.label(Metrics.SLAYER + "enderman"));
        assertEquals("1.25B", Metrics.format(Metrics.NETWORTH, 1_250_000_000));
        assertEquals("+1.2M", Metrics.delta(Metrics.SKILL + "farming", 1_200_000));
        assertEquals("Level 38.04", Metrics.levelNote(Metrics.CATACOMBS, 30_741_859.63, 0));
        assertEquals("4 days", Metrics.duration(4.2));
        assertEquals("4:05", Metrics.clock(245_000));
        JsonObject empty = new JsonObject();
        assertTrue(Double.isNaN(Json.num(empty, "a.b")));
    }
}
