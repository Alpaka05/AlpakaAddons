package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Farming Weight, the score Elite (elitebot.dev) ranks farmers by: every crop's collection divided
 * by how many of that crop count as one point, plus bonuses for the farming level, contest medals,
 * maxed farming minions and Anita's perk. Crops that pests dropped are taken off first, since they
 * were not farmed.
 *
 * The rules and every number are Elite's, from their published calculator
 * (github.com/EliteFarmers/FarmingWeight, MIT License, Copyright (c) Elite Network Development
 * LLC), so the score here is the one their leaderboard shows.
 */
final class FarmingWeight {
    private FarmingWeight() {}

    /** How much of each crop's collection makes one point, by the crop's name. */
    private static final Map<String, Double> PER_POINT = Map.ofEntries(
            Map.entry("Cactus", 178730.65), Map.entry("Carrot", 300000.0), Map.entry("Cocoa Beans", 276733.75),
            Map.entry("Melon", 488435.88), Map.entry("Mushroom", 90944.27), Map.entry("Nether Wart", 248606.81),
            Map.entry("Potato", 298328.17), Map.entry("Pumpkin", 99236.12), Map.entry("Sugar Cane", 198885.45),
            Map.entry("Wheat", 100000.0), Map.entry("Sunflower", 200000.0), Map.entry("Moonflower", 200000.0),
            Map.entry("Wild Rose", 200000.0));

    /** The farming minions whose twelfth tier counts. */
    private static final Set<String> TIER_12_MINIONS = Set.of("WHEAT_12", "CARROT_12", "POTATO_12", "PUMPKIN_12", "MELON_12",
            "MUSHROOM_12", "COCOA_12", "CACTUS_12", "SUGAR_CANE_12", "NETHER_WARTS_12", "FLOWER_12", "SUNFLOWER_12");

    /** The pests, by their id in the Bestiary ("pest_fly_1"), with the name shown and the crop each drops. */
    private static final String[][] PESTS = {
            {"beetle", "beetle", "Nether Wart"}, {"cricket", "cricket", "Carrot"}, {"fly", "fly", "Wheat"},
            {"locust", "locust", "Potato"}, {"mite", "mite", "Cactus"}, {"mosquito", "mosquito", "Sugar Cane"},
            {"moth", "moth", "Cocoa Beans"}, {"rat", "rat", "Pumpkin"}, {"slug", "slug", "Mushroom"},
            {"worm", "earthworm", "Melon"}, {"mouse", "mouse", null}, {"dragonfly", "dragonfly", "Sunflower"},
            {"firefly", "firefly", "Moonflower"}, {"praying_mantis", "mantis", "Wild Rose"}, {"lunar_moth", "lunarMoth", null}};

    /** The kill counts at which a pest's drops step up: the more kills, the more farming fortune they were made with. */
    private static final int[] BRACKETS = {0, 50, 100, 250, 500, 750, 1000};

    /** Crops one kill dropped beyond what counts as farmed, for each bracket, by the pest's id. */
    private static final Map<String, double[]> DROPS = Map.of(
            "mite", new double[] {0, 392.81451, 669.92375, 947.033, 1224.14223, 1390.40778, 1556.67335},
            "cricket", new double[] {0, 489.75238, 902.70476, 1315.65714, 1728.60951, 1976.38094, 2224.15239},
            "moth", new double[] {0, 298.18964, 495.41087, 692.63211, 889.85334, 1008.18609, 1126.51884},
            "worm", new double[] {0, 213.57277, 364.23674, 514.9007, 665.56466, 755.96304, 846.36143},
            "slug", new double[] {0, 167.24639, 285.22962, 403.21285, 521.19607, 591.986, 662.77594},
            "beetle", new double[] {0, 853.9207, 1428.26244, 2002.60421, 2576.94593, 2921.55098, 3266.15608},
            "locust", new double[] {0, 593.16202, 1049.22929, 1505.29657, 1961.36382, 2235.00418, 2508.64457},
            "rat", new double[] {0, 14.10777, 24.06004, 34.0123, 43.96457, 49.93593, 55.90729},
            "mosquito", new double[] {0, 20.58415, 35.10514, 49.62613, 64.14713, 72.85973, 81.57232});

    /** XP of Farming 50 and 60. */
    private static final double FARMING_50 = 55_172_425;
    private static final double FARMING_60 = 111_672_425;

    /**
     * The weight of the profile's member {@code m}: the total, each crop's and each bonus's share,
     * and the pests killed. {@code jacob} is the member's contests as {@link HypixelProfile} counted them.
     */
    static JsonObject of(JsonElement m, JsonElement profile, JsonObject jacob) {
        JsonObject pests = new JsonObject();
        Map<String, Double> uncounted = new LinkedHashMap<>();
        for (String[] pest : PESTS) {
            double kills = Json.num(m, "bestiary.kills.pest_" + pest[0] + "_1", 0);
            if (kills <= 0) continue;
            pests.addProperty(pest[1], kills);
            if (pest[2] != null && DROPS.containsKey(pest[0])) uncounted.put(pest[2], uncounted(DROPS.get(pest[0]), (long) kills));
        }

        JsonObject cropWeight = new JsonObject();
        double total = 0;
        double doubleBreak = 0;
        for (String[] crop : HypixelProfile.CROPS) {
            double collected = Math.max(0, Json.num(m, "collection." + crop[0], 0) - uncounted.getOrDefault(crop[3], 0.0));
            double weight = collected / PER_POINT.get(crop[3]);
            total += weight;
            if (crop[3].equals("Cactus") || crop[3].equals("Sugar Cane")) doubleBreak += weight;
            cropWeight.addProperty(crop[3], weight);
        }
        // Mushrooms come with every crop broken, by the Mooshroom Cow: two with the crops that break
        // two blocks at once, one with the others. So they count by how much of the farming was which.
        if (total > 0) {
            double before = cropWeight.get("Mushroom").getAsDouble();
            // The whole collection, as Elite counts it: what slugs dropped only tells in the ratio above.
            double mushrooms = Json.num(m, "collection.MUSHROOM_COLLECTION", 0);
            double perPoint = PER_POINT.get("Mushroom");
            double weight = doubleBreak / total * (mushrooms / (perPoint * 2)) + (total - doubleBreak) / total * (mushrooms / perPoint);
            cropWeight.addProperty("Mushroom", weight);
            total += weight - before;
        }

        JsonObject bonus = new JsonObject();
        double farmingXp = Json.num(m, "player_data.experience.SKILL_FARMING", 0);
        if (farmingXp >= FARMING_60 && Json.num(m, "jacobs_contest.perks.farming_level_cap", 0) >= 10) {
            bonus.addProperty("Farming Level 60", 250);
        } else if (farmingXp >= FARMING_50) {
            bonus.addProperty("Farming Level 50", 100);
        }
        int minions = 0;
        for (JsonElement generator : Json.arr(profile, "coop.crafted_generators")) {
            if (generator.isJsonPrimitive() && TIER_12_MINIONS.contains(generator.getAsString())) minions++;
        }
        if (minions > 0) bonus.addProperty("Farming Minions", minions * 5);
        double anita = Json.num(m, "jacobs_contest.perks.double_drops", 0);
        if (anita > 0) bonus.addProperty("Anita", anita * 2);
        // The best thousand medals count, diamond first.
        double diamond = Math.min(1000, Json.num(jacob, "earnedMedals.diamond", 0));
        double platinum = Math.min(1000 - diamond, Json.num(jacob, "earnedMedals.platinum", 0));
        double gold = Math.min(1000 - diamond - platinum, Json.num(jacob, "earnedMedals.gold", 0));
        double medals = diamond * 0.75 + platinum * 0.5 + gold * 0.25;
        if (medals > 0) bonus.addProperty("Contest Medals", medals);
        for (var e : bonus.entrySet()) total += e.getValue().getAsDouble();

        JsonObject out = new JsonObject();
        out.addProperty("totalWeight", total);
        out.add("cropWeight", cropWeight);
        out.add("bonusWeight", bonus);
        out.add("pests", pests);
        return out;
    }

    /**
     * The crops this many kills of one pest dropped that don't count as farmed. Kills fill the
     * brackets from the lowest up, each kill dropping what its bracket says; all past the last
     * bracket drop that one's.
     */
    static double uncounted(double[] perKill, long kills) {
        double drops = 0;
        long counted = 0;
        for (int i = 0; i < BRACKETS.length && kills > 0; i++) {
            if (i == BRACKETS.length - 1) {
                drops += Math.ceil(perKill[i] * kills);
                break;
            }
            // As Elite counts them: a bracket takes up to its width less the kills counted in the one before.
            counted = Math.min(BRACKETS[i + 1] - counted, kills);
            kills -= counted;
            if (perKill[i] != 0) drops += Math.ceil(perKill[i] * counted);
        }
        return drops;
    }
}
