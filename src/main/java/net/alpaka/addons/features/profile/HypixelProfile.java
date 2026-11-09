package net.alpaka.addons.features.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hypixel's own profile data, worked out into what the dashboard shows. Hypixel hands out raw
 * numbers - experience, kills, lists of things unlocked - and leaves the levels, the totals and the
 * names to whoever reads them; this does that reading once, into one object the pages draw from
 * ({@link ProfileData#raw}), so a frame never works anything out.
 *
 * The input is the AlpakaAddons API's answer ({@link AlpakaApi#dashboard}): the player, their
 * profiles, and of one profile the player's member data, what its co-op shares, and its garden.
 * No game and no network is needed here, so every number can be checked in a test.
 */
final class HypixelProfile {
    private HypixelProfile() {}

    /** The profile in the answer as the dashboard reads it; the inventories it lists come from {@link #inventories}. */
    static JsonObject model(JsonElement answer, Map<String, JsonElement> inventories) {
        JsonElement profile = Json.at(answer, "profile");
        JsonElement m = Json.at(profile, "member");
        JsonElement garden = Json.at(answer, "garden");
        JsonObject out = new JsonObject();
        out.addProperty("playerUuid", Json.str(answer, "player.uuid"));
        out.addProperty("profileId", Json.str(profile, "profile_id"));
        out.addProperty("profileName", Json.str(profile, "cute_name"));
        out.addProperty("lastUpdated", (long) (Json.num(answer, "fetchedAt", 0) / 1000));

        boolean skills = !Json.obj(m, "player_data.experience").isEmpty();
        boolean items = Json.at(m, "inventory.inv_contents") != null;
        JsonObject api = new JsonObject();
        api.addProperty("skills", skills);
        api.addProperty("inventories", items);
        api.addProperty("collections", Json.at(m, "collection") != null);
        // Hypixel only says the museum is switched off by handing out an empty one; see ProfileSession.
        api.addProperty("museum", items);
        out.add("api", api);

        out.addProperty("skyblockXp", Json.num(m, "leveling.experience", 0));
        out.addProperty("purse", Json.num(m, "currencies.coin_purse", 0));
        out.addProperty("bankBalance", Json.num(profile, "bank", 0));
        out.addProperty("personalBank", Json.num(m, "profile.bank_account", 0));

        out.add("collections", Json.obj(m, "collection"));
        out.add("collectionTiers", Json.obj(profile, "coop.collection_tiers"));
        out.add("sacks", sacks(m));
        JsonArray pets = pets(m);
        out.add("pets", pets);
        out.add("petSummary", petSummary(pets));
        out.add("inventories", inventoryList(m, inventories));
        out.add("minionSummary", minions(profile));

        JsonObject unparsed = new JsonObject();
        unparsed.addProperty("copper", Json.num(m, "garden_player_data.copper", 0));
        out.add("unparsed", unparsed);

        JsonObject jacob = jacob(m);
        out.add("jacob", jacob);
        out.add("farmingWeight", FarmingWeight.of(m, profile, jacob));
        if (garden != null) out.add("garden", garden(garden));
        if (Json.at(m, "events.easter") != null) out.add("chocolateFactory", chocolate(m));

        JsonObject stats = new JsonObject();
        if (skills) stats.add("skills", skills(m));
        stats.add("dungeons", dungeons(m));
        stats.add("slayer", slayers(m));
        stats.add("mining", mining(m));
        stats.add("foraging", foraging(m));
        stats.add("fishing", fishing(m));
        stats.add("crimsonIsle", crimsonIsle(m));
        if (Json.at(m, "rift") != null) stats.add("rift", rift(m));
        stats.add("playerStats", playerStats(m));
        stats.add("bestiary", bestiary(m));
        stats.add("bestiaryDerivations", bestiaryTotals(m));

        JsonObject misc = new JsonObject();
        misc.addProperty("pelts", Json.num(m, "quests.trapper_quest.pelt_count", 0));
        misc.addProperty("highestPetScore", Json.num(m, "leveling.highest_pet_score", 0));
        JsonObject festivals = new JsonObject();
        festivals.addProperty("fishingFestivalSharksKilled", Json.num(m, "leveling.fishing_festival_sharks_killed", 0));
        festivals.addProperty("miningFiestaOresMined", Json.num(m, "leveling.mining_fiesta_ores_mined", 0));
        misc.add("festivals", festivals);
        stats.add("miscStats", misc);

        JsonObject accessories = new JsonObject();
        accessories.addProperty("highestMagicalPower", Json.num(m, "accessory_bag_storage.highest_magical_power", 0));
        stats.add("accessories", accessories);
        out.add("stats", stats);
        return out;
    }

    // ---- The account ----------------------------------------------------------------------------

    /**
     * The player with their profiles, in the shape {@link ProfileSession#profiles} reads: Hypixel
     * only lists profiles the player is still on, so none is deleted or left.
     */
    static JsonObject account(JsonElement answer) {
        JsonObject out = new JsonObject();
        String uuid = Json.str(answer, "player.uuid");
        out.addProperty("id", uuid);
        out.addProperty("name", Json.str(answer, "player.name"));
        JsonObject skin = new JsonObject();
        skin.addProperty("texture", Json.str(answer, "player.skin"));
        out.add("skin", skin);
        JsonArray profiles = new JsonArray();
        for (JsonElement p : Json.arr(answer, "profiles")) {
            JsonObject ref = new JsonObject();
            ref.addProperty("profileId", Json.str(p, "profile_id"));
            ref.addProperty("profileName", Json.str(p, "cute_name"));
            ref.addProperty("gameMode", Json.str(p, "game_mode"));
            ref.addProperty("selected", Json.bool(p, "selected"));
            JsonArray members = new JsonArray();
            int count = Math.max(1, (int) Json.num(p, "members", 1));
            for (int i = 0; i < count; i++) {
                JsonObject member = new JsonObject();
                // Only the player's own id is known, and only the count of the others matters.
                if (i == 0) member.addProperty("uuid", uuid);
                member.addProperty("active", true);
                members.add(member);
            }
            ref.add("members", members);
            profiles.add(ref);
        }
        out.add("profiles", profiles);
        return out;
    }

    // ---- Skills ---------------------------------------------------------------------------------

    /** The level cap of a skill for this member: some start lower and are raised by the member. */
    static int skillCap(String id, JsonElement m) {
        return switch (id) {
            case "farming" -> 50 + (int) Math.min(10, Json.num(m, "jacobs_contest.perks.farming_level_cap", 0));
            case "taming" -> 50 + Math.min(10, Json.arr(m, "pets_data.pet_care.pet_types_sacrificed").size());
            case "foraging" -> 54 + (int) Math.min(3, Json.num(m, "player_data.experience.SKILL_FORAGING_extra_level_cap", 0));
            case "fishing", "alchemy", "carpentry", "hunting" -> 50;
            case "runecrafting", "social" -> 25;
            default -> 60;
        };
    }

    private static JsonObject skills(JsonElement m) {
        JsonObject levels = new JsonObject();
        double total = 0;
        double sum = 0;
        int counted = 0;
        for (String id : ProfileData.SKILL_ORDER) {
            double xp = Json.num(m, "player_data.experience.SKILL_" + id.toUpperCase(Locale.ROOT), Double.NaN);
            if (Double.isNaN(xp)) continue;
            int cap = skillCap(id, m);
            JsonObject level = level(Levels.tableFor(id), xp, cap);
            level.addProperty("maxLevel", cap);
            levels.add(id, level);
            // The cosmetic skills are left out of the total and the average, as the game leaves them out.
            if (id.equals("runecrafting") || id.equals("social")) continue;
            total += xp;
            sum += level.get("level").getAsInt();
            counted++;
        }
        JsonObject out = new JsonObject();
        out.add("levels", levels);
        out.addProperty("totalXp", total);
        out.addProperty("average", counted == 0 ? 0 : Math.round(sum / counted * 10) / 10.0);
        return out;
    }

    /** A level from a table of XP per level: the whole level, the XP into it and what the next takes. */
    private static JsonObject level(long[] table, double xp, int cap) {
        int level = 0;
        double left = Math.max(0, xp);
        while (level < cap && level < table.length && left >= table[level]) {
            left -= table[level];
            level++;
        }
        boolean maxed = level >= cap || level >= table.length;
        JsonObject out = new JsonObject();
        out.addProperty("level", level);
        out.addProperty("xp", xp);
        out.addProperty("xpCurrent", left);
        out.addProperty("xpForNext", maxed ? 0 : table[level]);
        out.addProperty("progress", maxed ? 1 : left / table[level]);
        return out;
    }

    // ---- Dungeons -------------------------------------------------------------------------------

    private static JsonObject dungeons(JsonElement m) {
        JsonElement d = Json.at(m, "dungeons");
        JsonObject out = new JsonObject();
        double xp = Json.num(d, "dungeon_types.catacombs.experience", 0);
        out.addProperty("catacombsExperience", xp);
        out.addProperty("catacombsLevel", level(Levels.CATACOMBS, xp, 50).get("level").getAsInt());
        out.addProperty("selectedClass", Json.str(d, "selected_dungeon_class"));
        out.addProperty("secretsFound", Json.num(d, "secrets", 0));
        JsonObject classes = new JsonObject();
        double levels = 0;
        for (String id : ProfileData.CLASSES) {
            double classXp = Json.num(d, "player_classes." + id + ".experience", Double.NaN);
            if (Double.isNaN(classXp)) continue;
            JsonObject level = level(Levels.CATACOMBS, classXp, 50);
            levels += level.get("level").getAsInt();
            classes.add(id, level);
        }
        out.add("classLevels", classes);
        out.addProperty("classAverage", levels / ProfileData.CLASSES.length);
        out.add("floors", floors(Json.at(d, "dungeon_types.catacombs")));
        out.add("masterFloors", floors(Json.at(d, "dungeon_types.master_catacombs")));
        return out;
    }

    private static JsonObject floors(JsonElement type) {
        JsonObject out = new JsonObject();
        for (int tier = 0; tier <= 7; tier++) {
            double completions = Json.num(type, "tier_completions." + tier, 0);
            if (completions <= 0) continue;
            JsonObject floor = new JsonObject();
            floor.addProperty("completions", completions);
            floor.addProperty("fastestTime", Json.num(type, "fastest_time." + tier, 0));
            floor.addProperty("fastestTimeS", Json.num(type, "fastest_time_s." + tier, 0));
            floor.addProperty("fastestTimeSPlus", Json.num(type, "fastest_time_s_plus." + tier, 0));
            floor.addProperty("bestScore", Json.num(type, "best_score." + tier, 0));
            out.add(String.valueOf(tier), floor);
        }
        return out;
    }

    // ---- Slayers --------------------------------------------------------------------------------

    private static final long[] SLAYER_ZOMBIE = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] SLAYER_SPIDER = {5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] SLAYER_OTHER = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] SLAYER_VAMPIRE = {20, 75, 240, 840, 2400};

    /** The total XP each level of a slayer boss is reached at. */
    static long[] slayerTable(String boss) {
        return switch (boss) {
            case "zombie" -> SLAYER_ZOMBIE;
            case "spider" -> SLAYER_SPIDER;
            case "vampire" -> SLAYER_VAMPIRE;
            default -> SLAYER_OTHER;
        };
    }

    private static JsonObject slayers(JsonElement m) {
        JsonObject bosses = new JsonObject();
        double total = 0;
        for (String[] boss : ProfileData.SLAYERS) {
            JsonElement b = Json.at(m, "slayer.slayer_bosses." + boss[0]);
            if (b == null) continue;
            double xp = Json.num(b, "xp", 0);
            total += xp;
            long[] table = slayerTable(boss[0]);
            int level = 0;
            while (level < table.length && xp >= table[level]) level++;
            boolean maxed = level >= table.length;
            long from = level == 0 ? 0 : table[level - 1];
            JsonObject out = new JsonObject();
            out.addProperty("xp", xp);
            out.addProperty("level", level);
            out.addProperty("maxLevel", table.length);
            out.addProperty("xpForNext", maxed ? 0 : table[level] - xp);
            out.addProperty("progress", maxed ? 1 : (xp - from) / (table[level] - from));
            JsonObject kills = new JsonObject();
            for (int tier = 0; tier < 5; tier++) {
                double n = Json.num(b, "boss_kills_tier_" + tier, 0);
                if (n > 0) kills.addProperty(String.valueOf(tier), n);
            }
            out.add("bossKills", kills);
            bosses.add(boss[0], out);
        }
        JsonObject out = new JsonObject();
        out.add("bosses", bosses);
        out.addProperty("totalXp", total);
        return out;
    }

    // ---- Pets -----------------------------------------------------------------------------------

    /** XP for each pet level from 2 on; a rarity starts further along it, see {@link #petOffset}. */
    private static final int[] PET_LEVELS = {
            100, 110, 120, 130, 145, 160, 175, 190, 210, 230, 250, 275, 300, 330, 360, 400, 440, 490, 540, 600, 660, 730, 800,
            880, 960, 1050, 1150, 1260, 1380, 1510, 1650, 1800, 1960, 2130, 2310, 2500, 2700, 2920, 3160, 3420, 3700, 4000,
            4350, 4750, 5200, 5700, 6300, 7000, 7800, 8700, 9700, 10800, 12000, 13300, 14700, 16200, 17800, 19500, 21300,
            23200, 25200, 27400, 29800, 32400, 35200, 38200, 41400, 44800, 48400, 52200, 56200, 60400, 64800, 69400, 74200,
            79200, 84700, 90700, 97200, 104200, 111700, 119700, 128200, 137200, 146700, 156700, 167700, 179700, 192700,
            206700, 221700, 237700, 254700, 272700, 291700, 311700, 333700, 357700, 383700, 411700, 441700, 476700, 516700,
            561700, 611700, 666700, 726700, 791700, 861700, 936700, 1016700, 1101700, 1191700, 1286700, 1386700, 1496700,
            1616700, 1746700, 1886700};

    /** The dragons that go on to level 200: nothing for 101, a little for 102, then the table's last step each level. */
    private static final java.util.Set<String> PETS_TO_200 = java.util.Set.of("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON");

    private static int petOffset(String tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 6;
            case "RARE" -> 11;
            case "EPIC" -> 16;
            default -> 20;
        };
    }

    /** The XP the step from {@code level} to the next takes for a pet of this kind and rarity. */
    private static double petStep(String type, int offset, int level) {
        if (level < 100) return PET_LEVELS[Math.min(PET_LEVELS.length - 1, offset + level - 1)];
        if (!PETS_TO_200.contains(type)) return Double.POSITIVE_INFINITY;
        return level == 100 ? 0 : level == 101 ? 5555 : PET_LEVELS[PET_LEVELS.length - 1];
    }

    private static JsonArray pets(JsonElement m) {
        JsonArray out = new JsonArray();
        for (JsonElement e : Json.arr(m, "pets_data.pets")) {
            String type = Json.str(e, "type");
            if (type == null) continue;
            String tier = Json.str(e, "tier");
            double exp = Json.num(e, "exp", 0);
            int max = PETS_TO_200.contains(type) ? 200 : 100;
            int offset = petOffset(tier);
            int level = 1;
            double left = exp;
            double toMax = 0;
            for (int l = 1; l < max; l++) toMax += petStep(type, offset, l);
            while (level < max && left >= petStep(type, offset, level)) {
                left -= petStep(type, offset, level);
                level++;
            }
            JsonObject pet = new JsonObject();
            pet.addProperty("uuid", Json.str(e, "uuid"));
            pet.addProperty("type", type);
            pet.addProperty("tier", tier);
            pet.addProperty("exp", exp);
            pet.addProperty("active", Json.bool(e, "active"));
            pet.addProperty("heldItem", Json.str(e, "heldItem"));
            pet.addProperty("skin", Json.str(e, "skin"));
            pet.addProperty("candyUsed", Json.num(e, "candyUsed", 0));
            pet.addProperty("level", level);
            pet.addProperty("maxLevel", max);
            pet.addProperty("progress", level >= max ? 0 : left / petStep(type, offset, level));
            pet.addProperty("xpMaxLevel", toMax);
            out.add(pet);
        }
        return out;
    }

    private static JsonObject petSummary(JsonArray pets) {
        int maxed = 0;
        int skins = 0;
        for (JsonElement p : pets) {
            if (Json.num(p, "level", 0) >= Json.num(p, "maxLevel", 100)) maxed++;
            if (Json.str(p, "skin") != null) skins++;
        }
        JsonObject out = new JsonObject();
        out.addProperty("totalPets", pets.size());
        out.addProperty("maxLevelPets", maxed);
        out.addProperty("totalSkins", skins);
        return out;
    }

    // ---- Coins, sacks, minions ------------------------------------------------------------------

    private static JsonObject sacks(JsonElement m) {
        JsonObject out = new JsonObject();
        for (var e : Json.obj(m, "inventory.sacks_counts").entrySet()) {
            double n = Json.num(e.getValue());
            if (n > 0) out.addProperty(e.getKey(), n);
        }
        return out;
    }

    /** The unique minions crafted at which each further minion slot opens, after the five every profile has. */
    private static final int[] MINION_SLOTS = {5, 15, 30, 50, 75, 100, 125, 150, 175, 200, 225, 250, 275, 300, 350, 400, 450,
            500, 550, 600, 650, 700, 750, 800, 850};

    private static JsonObject minions(JsonElement profile) {
        int crafted = Json.arr(profile, "coop.crafted_generators").size();
        int slots = 5;
        int next = -1;
        for (int need : MINION_SLOTS) {
            if (crafted >= need) {
                slots++;
            } else {
                next = need;
                break;
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("uniqueTiersCrafted", crafted);
        out.addProperty("currentSlots", slots);
        out.addProperty("maxSlots", 5 + MINION_SLOTS.length);
        if (next > 0) out.addProperty("uniqueTiersToNextSlot", next - crafted);
        return out;
    }

    // ---- Farming --------------------------------------------------------------------------------

    /**
     * The crops: Hypixel's id, the key in the garden data, the name without spaces Jacob's numbers
     * go by, and the name as it is written.
     */
    static final String[][] CROPS = {
            {"WHEAT", "wheat", "Wheat", "Wheat"}, {"CARROT_ITEM", "carrot", "Carrot", "Carrot"},
            {"POTATO_ITEM", "potato", "Potato", "Potato"}, {"PUMPKIN", "pumpkin", "Pumpkin", "Pumpkin"},
            {"MELON", "melon", "Melon", "Melon"}, {"MUSHROOM_COLLECTION", "mushroom", "Mushroom", "Mushroom"},
            {"CACTUS", "cactus", "Cactus", "Cactus"}, {"SUGAR_CANE", "sugarCane", "SugarCane", "Sugar Cane"},
            {"NETHER_STALK", "netherWart", "NetherWart", "Nether Wart"}, {"INK_SACK:3", "cocoaBeans", "CocoaBeans", "Cocoa Beans"},
            {"DOUBLE_PLANT", "sunflower", "Sunflower", "Sunflower"}, {"MOONFLOWER", "moonflower", "Moonflower", "Moonflower"},
            {"WILD_ROSE", "wildRose", "WildRose", "Wild Rose"}};

    private static String[] crop(String hypixelId) {
        for (String[] crop : CROPS) if (crop[0].equals(hypixelId)) return crop;
        return null;
    }

    static final String[] MEDALS = {"bronze", "silver", "gold", "platinum", "diamond"};

    /**
     * The medal a contest ended with: the one claimed, or the one the place among the participants
     * earns. Null for a contest without a medal or one never claimed.
     */
    static String medal(JsonElement contest) {
        String claimed = Json.str(contest, "claimed_medal");
        if (claimed != null) return claimed;
        double position = Json.num(contest, "claimed_position");
        double participants = Json.num(contest, "claimed_participants");
        if (Double.isNaN(position) || Double.isNaN(participants)) return null;
        if (position <= Math.floor(participants * 0.02)) return "diamond";
        if (position <= Math.floor(participants * 0.05)) return "platinum";
        if (position <= Math.floor(participants * 0.1)) return "gold";
        if (position <= Math.floor(participants * 0.3)) return "silver";
        if (position <= Math.floor(participants * 0.6)) return "bronze";
        return null;
    }

    /** Fewer crops than this in a contest don't count as taking part, as Jacob doesn't count them. */
    private static final int CONTEST_MINIMUM = 100;

    private static JsonObject jacob(JsonElement m) {
        JsonElement j = Json.at(m, "jacobs_contest");
        Map<String, int[]> medalsByCrop = new LinkedHashMap<>();
        Map<String, Integer> contestsByCrop = new LinkedHashMap<>();
        int[] earned = new int[MEDALS.length];
        int participations = 0;
        for (var e : Json.obj(j, "contests").entrySet()) {
            // "275:12_3:CARROT_ITEM": the year, the month and day, and the crop, whose id may hold a colon itself.
            String key = e.getKey();
            int first = key.indexOf(':');
            int second = first < 0 ? -1 : key.indexOf(':', first + 1);
            String[] crop = second < 0 ? null : crop(key.substring(second + 1));
            if (crop == null || Json.num(e.getValue(), "collected", 0) < CONTEST_MINIMUM) continue;
            participations++;
            contestsByCrop.merge(crop[2], 1, Integer::sum);
            String medal = medal(e.getValue());
            for (int i = 0; i < MEDALS.length; i++) {
                if (!MEDALS[i].equals(medal)) continue;
                earned[i]++;
                medalsByCrop.computeIfAbsent(crop[2], k -> new int[MEDALS.length])[i]++;
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("participations", participations);
        JsonObject earnedMedals = new JsonObject();
        for (int i = 0; i < MEDALS.length; i++) earnedMedals.addProperty(MEDALS[i], earned[i]);
        out.add("earnedMedals", earnedMedals);
        out.add("medals", Json.obj(j, "medals_inv"));
        // A crop counts once it brought gold or better.
        java.util.Set<String> golden = new java.util.HashSet<>();
        for (String bracket : new String[] {"gold", "platinum", "diamond"}) {
            for (JsonElement crop : Json.arr(j, "unique_brackets." + bracket)) golden.add(crop.getAsString());
        }
        out.addProperty("uniqueGoldMedals", golden.size());
        JsonObject perks = new JsonObject();
        perks.addProperty("doubleDrops", Json.num(j, "perks.double_drops", 0));
        perks.addProperty("levelCap", Json.num(j, "perks.farming_level_cap", 0));
        out.add("perks", perks);

        JsonObject bests = new JsonObject();
        for (var e : Json.obj(j, "personal_bests").entrySet()) {
            String[] crop = crop(e.getKey());
            if (crop != null) bests.add(crop[2], e.getValue());
        }
        JsonObject crops = new JsonObject();
        for (var e : contestsByCrop.entrySet()) {
            JsonObject crop = new JsonObject();
            crop.addProperty("participations", e.getValue());
            JsonObject medals = new JsonObject();
            int[] counts = medalsByCrop.getOrDefault(e.getKey(), new int[MEDALS.length]);
            for (int i = 0; i < MEDALS.length; i++) medals.addProperty(MEDALS[i], counts[i]);
            crop.add("medals", medals);
            crops.add(e.getKey(), crop);
        }
        JsonObject stats = new JsonObject();
        stats.add("personalBests", bests);
        stats.add("crops", crops);
        out.add("stats", stats);
        return out;
    }

    /** XP each Garden level takes; past the last, every further level takes as much again as that one. */
    private static final long[] GARDEN_LEVELS = {70, 70, 140, 240, 600, 1500, 2000, 2500, 3000, 10000, 10000, 10000, 10000, 10000};

    private static JsonObject garden(JsonElement g) {
        JsonObject out = new JsonObject();
        double xp = Json.num(g, "garden_experience", 0);
        // The Garden starts at level 1.
        JsonObject level = level(GARDEN_LEVELS, xp, GARDEN_LEVELS.length);
        out.addProperty("experience", xp);
        out.addProperty("gardenLevel", level.get("level").getAsInt() + 1);
        out.addProperty("gardenProgress", level.get("xpForNext").getAsDouble() > 0 ? level.get("progress").getAsDouble() : 0);
        out.addProperty("completedVisitors", Json.num(g, "commission_data.total_completed", 0));
        out.addProperty("uniqueVisitors", Json.num(g, "commission_data.unique_npcs_served", 0));
        JsonObject crops = new JsonObject();
        JsonObject upgrades = new JsonObject();
        for (String[] crop : CROPS) {
            double amount = Json.num(g, "resources_collected." + crop[0], Double.NaN);
            if (!Double.isNaN(amount)) crops.addProperty(crop[1], amount);
            double upgrade = Json.num(g, "crop_upgrade_levels." + crop[0], Double.NaN);
            if (!Double.isNaN(upgrade)) upgrades.addProperty(crop[1], upgrade);
        }
        out.add("crops", crops);
        out.add("cropUpgrades", upgrades);
        out.add("plots", Json.arr(g, "unlocked_plots_ids"));
        JsonObject composter = new JsonObject();
        composter.add("upgrades", Json.obj(g, "composter_data.upgrades"));
        out.add("composter", composter);
        return out;
    }

    // ---- Mining and foraging --------------------------------------------------------------------

    /** The total XP each tier of the Heart of the Mountain and of the Forest is reached at, tier 1 first. */
    private static final long[] HEART_TIERS = {0, 3000, 12000, 37000, 97000, 197000, 347000, 557000, 847000, 1247000};

    private static int heartTier(double xp) {
        int tier = 0;
        while (tier < HEART_TIERS.length && xp >= HEART_TIERS[tier]) tier++;
        return tier;
    }

    /** A skill tree's perks and which of them are switched off, as Hypixel keeps them in one object. */
    private static void nodes(JsonElement m, String tree, JsonObject out) {
        JsonObject nodes = new JsonObject();
        JsonObject toggles = new JsonObject();
        for (var e : Json.obj(m, "skill_tree.nodes." + tree).entrySet()) {
            if (e.getKey().startsWith("toggle_")) toggles.add(e.getKey().substring("toggle_".length()), e.getValue());
            else nodes.add(e.getKey(), e.getValue());
        }
        out.add("nodes", nodes);
        out.add("nodeToggles", toggles);
    }

    private static final String[] RARITY_STEPS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};

    /** The rarity a pet milestone (ores mined, sea creatures killed) stands at; null before the first. */
    private static JsonObject milestone(double progress, long[] steps) {
        JsonObject out = new JsonObject();
        String rarity = null;
        for (int i = 0; i < steps.length; i++) if (progress >= steps[i]) rarity = RARITY_STEPS[i];
        if (rarity != null) out.addProperty("currentRarity", rarity);
        out.addProperty("progress", progress);
        return out;
    }

    private static JsonObject mining(JsonElement m) {
        JsonObject out = new JsonObject();
        double xp = Json.num(m, "skill_tree.experience.mining", 0);
        out.addProperty("experience", xp);
        out.addProperty("hotmLevel", heartTier(xp));
        out.addProperty("peakOfTheMountainLevel", Json.num(m, "skill_tree.nodes.mining.core_of_the_mountain", 0));
        out.addProperty("tokensSpent", Json.num(m, "skill_tree.tokens_spent.mountain", 0));
        out.addProperty("selectedPickaxeAbility", Json.str(m, "skill_tree.selected_ability.mining"));
        out.addProperty("crystalHollowsLastAccess", Json.num(m, "mining_core.greater_mines_last_access", 0));

        // A Nucleus run places all five of these; the one placed least often is how many runs were whole.
        double runs = Double.MAX_VALUE;
        for (String crystal : new String[] {"jade", "amber", "amethyst", "sapphire", "topaz"}) {
            runs = Math.min(runs, Json.num(m, "mining_core.crystals." + crystal + "_crystal.total_placed", 0));
        }
        out.addProperty("crystalNucleusRuns", runs == Double.MAX_VALUE ? 0 : runs);

        int commissions = 0;
        for (JsonElement step : Json.arr(m, "objectives.tutorial")) {
            String name = step.isJsonPrimitive() ? step.getAsString() : "";
            if (!name.startsWith(COMMISSION_STEP)) continue;
            try {
                commissions = Math.max(commissions, Integer.parseInt(name.substring(COMMISSION_STEP.length())));
            } catch (NumberFormatException ignored) {
                // Another reward of the same milestones.
            }
        }
        out.addProperty("commissionMilestone", commissions);

        JsonObject powder = new JsonObject();
        for (String type : new String[] {"mithril", "gemstone", "glacite"}) {
            double available = Json.num(m, "mining_core.powder_" + type, Double.NaN);
            if (Double.isNaN(available)) continue;
            double spent = Json.num(m, "mining_core.powder_spent_" + type, 0);
            JsonObject p = new JsonObject();
            p.addProperty("available", available);
            p.addProperty("spent", spent);
            p.addProperty("total", available + spent);
            powder.add(type, p);
        }
        out.add("powder", powder);

        JsonObject crystals = new JsonObject();
        for (var e : Json.obj(m, "mining_core.crystals").entrySet()) {
            JsonObject crystal = new JsonObject();
            crystal.addProperty("state", Json.str(e.getValue(), "state"));
            crystal.addProperty("totalPlaced", Json.num(e.getValue(), "total_placed", 0));
            crystal.addProperty("totalFound", Json.num(e.getValue(), "total_found", 0));
            crystals.add(e.getKey().replace("_crystal", ""), crystal);
        }
        out.add("crystals", crystals);
        nodes(m, "mining", out);

        JsonObject glacite = new JsonObject();
        glacite.addProperty("fossilsDonatedCount", Json.arr(m, "glacite_player_data.fossils_donated").size());
        glacite.addProperty("fossilDust", Json.num(m, "glacite_player_data.fossil_dust", 0));
        glacite.addProperty("mineshaftsEntered", Json.num(m, "glacite_player_data.mineshafts_entered", 0));
        glacite.add("corpsesLooted", Json.obj(m, "glacite_player_data.corpses_looted"));
        out.add("glacite", glacite);
        out.add("rockMilestone", milestone(Json.num(m, "player_stats.pets.milestone.ores_mined", 0),
                new long[] {2500, 7500, 20000, 100000, 250000}));
        return out;
    }

    private static final String COMMISSION_STEP = "commission_milestone_reward_mining_xp_tier_";

    private static JsonObject foraging(JsonElement m) {
        JsonObject out = new JsonObject();
        double xp = Json.num(m, "skill_tree.experience.foraging", 0);
        out.addProperty("hotfExperience", xp);
        out.addProperty("hotfLevel", xp > 0 ? heartTier(xp) : 0);
        out.addProperty("centerOfTheForestLevel", Json.num(m, "skill_tree.nodes.foraging.center_of_the_forest", 0));
        out.addProperty("hotfTokensSpent", Json.num(m, "skill_tree.tokens_spent.forest", 0));
        out.addProperty("selectedAxeAbility", Json.str(m, "skill_tree.selected_ability.foraging"));
        out.addProperty("hinaChapterTier", Json.num(m, "foraging.hina.tasks.tier_claimed", 0));

        // The whispers ever gathered, and what the tree with the most in it took of them.
        double total = Json.num(m, "foraging_core.whispers.forest.total", Double.NaN);
        double spent = 0;
        for (var e : Json.obj(m, "foraging_core.whispers.forest").entrySet()) {
            spent = Math.max(spent, Json.num(e.getValue(), "spent", 0));
        }
        if (!Double.isNaN(total)) {
            out.addProperty("forestsWhispers", Math.max(0, total - spent));
            out.addProperty("forestsWhispersSpent", spent);
        }
        nodes(m, "foraging", out);

        JsonObject gifts = new JsonObject();
        for (var e : Json.obj(m, "foraging.tree_gifts").entrySet()) {
            double n = Json.num(e.getValue());
            if (Double.isNaN(n)) continue;
            JsonObject tree = new JsonObject();
            tree.addProperty("totalGifts", n);
            tree.addProperty("milestoneTier", Json.num(m, "foraging.tree_gifts.milestone_tier_claimed." + e.getKey(), 0));
            gifts.add(e.getKey().toLowerCase(Locale.ROOT), tree);
        }
        out.add("treeGifts", gifts);
        out.add("personalBests", Json.obj(m, "foraging.starlyn.personal_bests"));
        return out;
    }

    // ---- Fishing --------------------------------------------------------------------------------

    private static final String[] TROPHY_RANKS = {"Novice", "Adept", "Expert", "Master"};

    private static JsonObject fishing(JsonElement m) {
        JsonObject out = new JsonObject();
        JsonElement fish = Json.at(m, "trophy_fish");
        out.addProperty("totalTrophyFishCaught", Json.num(fish, "total_caught", 0));
        out.addProperty("lastCaughtTrophyFish", Json.str(fish, "last_caught"));
        int rank = 0;
        for (JsonElement reward : Json.arr(fish, "rewards")) {
            double r = Json.num(reward);
            if (r >= 1 && r <= TROPHY_RANKS.length) rank = Math.max(rank, (int) r);
        }
        if (rank > 0) out.addProperty("trophyRank", TROPHY_RANKS[rank - 1]);

        JsonObject counts = new JsonObject();
        JsonObject tiers = new JsonObject();
        double[] byTier = new double[TrophyFish.TIERS.length];
        for (var e : (fish != null && fish.isJsonObject() ? fish.getAsJsonObject() : new JsonObject()).entrySet()) {
            double n = Json.num(e.getValue());
            if (Double.isNaN(n) || e.getKey().equals("total_caught")) continue;
            counts.addProperty(e.getKey(), n);
            for (int t = 0; t < TrophyFish.TIERS.length; t++) {
                if (e.getKey().endsWith("_" + TrophyFish.TIERS[t])) byTier[t] += n;
            }
        }
        for (int t = 0; t < TrophyFish.TIERS.length; t++) tiers.addProperty(TrophyFish.TIERS[t], byTier[t]);
        out.add("trophyFishCounts", counts);
        out.add("trophyFishTierCounts", tiers);

        out.addProperty("itemsFished", Json.num(m, "player_stats.items_fished.total", 0));
        out.addProperty("normalCatches", Json.num(m, "player_stats.items_fished.normal", 0));
        out.addProperty("treasuresCaught", Json.num(m, "player_stats.items_fished.treasure", 0));
        out.addProperty("largeTreasuresCaught", Json.num(m, "player_stats.items_fished.large_treasure", 0));
        out.addProperty("seaCreatureKills", Json.num(m, "player_stats.sea_creature_kills", 0));
        out.add("dolphinMilestone", milestone(Json.num(m, "player_stats.pets.milestone.sea_creatures_killed", 0),
                new long[] {250, 1000, 2500, 5000, 10000}));
        return out;
    }

    // ---- Combat ---------------------------------------------------------------------------------

    private static final String[] KUUDRA_TIERS = {"none", "hot", "burning", "fiery", "infernal"};
    private static final String[] DOJO_TESTS = {"mob_kb", "wall_jump", "archer", "sword_swap", "snake", "fireball", "lock_head"};

    /** The Dojo's belt letter for a test's points. */
    static String dojoRank(double points) {
        if (points >= 1000) return "S";
        if (points >= 800) return "A";
        if (points >= 600) return "B";
        if (points >= 400) return "C";
        if (points >= 200) return "D";
        return "F";
    }

    private static JsonObject crimsonIsle(JsonElement m) {
        JsonElement n = Json.at(m, "nether_island_player_data");
        JsonObject out = new JsonObject();
        out.addProperty("selectedFaction", Json.str(n, "selected_faction"));
        out.addProperty("barbariansReputation", Json.num(n, "barbarians_reputation", 0));
        out.addProperty("magesReputation", Json.num(n, "mages_reputation", 0));
        JsonObject completions = new JsonObject();
        JsonObject waves = new JsonObject();
        double runs = 0;
        String highest = null;
        for (String tier : KUUDRA_TIERS) {
            double done = Json.num(n, "kuudra_completed_tiers." + tier, 0);
            if (done <= 0) continue;
            runs += done;
            highest = tier;
            completions.addProperty(tier, done);
            waves.addProperty(tier, Json.num(n, "kuudra_completed_tiers.highest_wave_" + tier, 0));
        }
        out.addProperty("highestKuudraTier", highest);
        out.addProperty("totalKuudraRuns", runs);
        out.add("kuudraCompletions", completions);
        out.add("kuudraHighestWaves", waves);
        JsonObject tests = new JsonObject();
        double points = 0;
        for (String test : DOJO_TESTS) {
            double p = Json.num(n, "dojo.dojo_points_" + test, Double.NaN);
            if (Double.isNaN(p)) continue;
            points += p;
            JsonObject t = new JsonObject();
            t.addProperty("points", p);
            t.addProperty("time", Json.num(n, "dojo.dojo_time_" + test, 0));
            t.addProperty("rank", dojoRank(p));
            tests.add(test, t);
        }
        out.addProperty("dojoPointsTotal", points);
        out.add("dojoTests", tests);
        return out;
    }

    /** How many Enigma Souls the Rift holds. */
    private static final int ENIGMA_SOULS = 52;

    private static JsonObject rift(JsonElement m) {
        JsonElement r = Json.at(m, "rift");
        JsonObject out = new JsonObject();
        out.addProperty("motesPurse", Json.num(m, "currencies.motes_purse", 0));
        out.addProperty("lifetimeMotesEarned", Json.num(m, "player_stats.rift.lifetime_motes_earned", 0));
        out.addProperty("motesOrbPickup", Json.num(m, "player_stats.rift.motes_orb_pickup", 0));
        out.addProperty("visits", Json.num(m, "player_stats.rift.visits", 0));
        out.addProperty("consumedPrism", Json.bool(r, "access.consumed_prism"));
        out.addProperty("unlockedRiftCatPet", Json.bool(r, "dead_cats.unlocked_pet"));
        out.addProperty("enigmaSoulsFound", Json.arr(r, "enigma.found_souls").size());
        out.addProperty("enigmaSoulsTotal", ENIGMA_SOULS);
        out.addProperty("grubberStacks", Json.num(r, "castle.grubber_stacks", 0));
        out.add("foundCats", Json.arr(r, "dead_cats.found_cats"));
        out.add("securedTrophies", Json.arr(r, "gallery.secured_trophies"));
        out.add("unlockedEyes", Json.arr(r, "wither_cage.killed_eyes"));
        out.add("lifetimePurchasedBoundaries", Json.arr(r, "lifetime_purchased_boundaries"));
        return out;
    }

    private static JsonObject playerStats(JsonElement m) {
        JsonElement s = Json.at(m, "player_stats");
        JsonObject out = new JsonObject();

        // Hypixel counts a kill under the mob's bare id here and under its id and level in the
        // Bestiary; both go in, since some mobs only count in one of the two.
        JsonObject kills = new JsonObject();
        List<Map.Entry<String, Double>> top = new ArrayList<>();
        for (var e : Json.obj(s, "kills").entrySet()) {
            double n = Json.num(e.getValue());
            if (Double.isNaN(n) || e.getKey().equals("total")) continue;
            kills.addProperty(e.getKey(), n);
            top.add(Map.entry(e.getKey(), n));
        }
        for (var e : Json.obj(m, "bestiary.kills").entrySet()) {
            if (!kills.has(e.getKey())) kills.add(e.getKey(), e.getValue());
        }
        double totalKills = Json.num(s, "kills.total", 0);
        double totalDeaths = Math.max(Json.num(s, "deaths.total", 0), Json.num(m, "player_data.death_count", 0));
        JsonObject killsDeaths = new JsonObject();
        killsDeaths.addProperty("totalKills", totalKills);
        killsDeaths.addProperty("totalDeaths", totalDeaths);
        killsDeaths.add("kills", kills);
        out.add("killsDeaths", killsDeaths);

        JsonObject derivations = new JsonObject();
        if (totalDeaths > 0) derivations.addProperty("killDeathRatio", totalKills / totalDeaths);
        top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        JsonArray topKills = new JsonArray();
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            JsonObject mob = new JsonObject();
            mob.addProperty("mobId", top.get(i).getKey());
            mob.addProperty("count", top.get(i).getValue());
            topKills.add(mob);
        }
        derivations.add("topKills", topKills);
        out.add("derivations", derivations);

        JsonObject damage = new JsonObject();
        damage.addProperty("highestDamage", Math.max(Json.num(s, "highest_damage", 0), Json.num(s, "highest_critical_damage", 0)));
        out.add("damage", damage);

        JsonElement fight = Json.at(s, "end_island.dragon_fight");
        JsonObject dragons = new JsonObject();
        dragons.add("amountSummoned", Json.obj(fight, "amount_summoned"));
        dragons.add("mostDamage", Json.obj(fight, "most_damage"));
        double crystals = Json.num(fight, "ender_crystals_destroyed", Double.NaN);
        if (!Double.isNaN(crystals)) dragons.addProperty("enderCrystalsDestroyed", crystals);
        out.add("dragonFight", dragons);

        JsonObject mythos = new JsonObject();
        for (String[] mob : MYTHOS) {
            double n = Json.num(s, "kills." + mob[0], Double.NaN);
            if (!Double.isNaN(n)) mythos.addProperty(mob[1], n);
        }
        mythos.add("burrowsDugNext", Json.obj(s, "mythos.burrows_dug_next"));
        mythos.add("burrowsDugTreasure", Json.obj(s, "mythos.burrows_dug_treasure"));
        mythos.add("burrowsChainsComplete", Json.obj(s, "mythos.burrows_chains_complete"));
        out.add("mythos", mythos);

        JsonObject souls = new JsonObject();
        double collected = Json.num(m, "fairy_soul.total_collected", Double.NaN);
        if (!Double.isNaN(collected)) souls.addProperty("collected", collected);
        out.add("fairySouls", souls);
        return out;
    }

    private static final String[][] MYTHOS = {{"gaia_construct", "killsGaiaConstruct"}, {"minos_champion", "killsMinosChampion"},
            {"minos_hunter", "killsMinosHunter"}, {"minos_inquisitor", "killsMinosInquisitor"}, {"minotaur", "killsMinotaur"},
            {"siamese_lynx", "killsSiameseLynx"}};

    private static JsonObject bestiary(JsonElement m) {
        JsonObject out = new JsonObject();
        out.addProperty("milestoneLevel", Json.num(m, "bestiary.milestone.last_claimed_milestone", 0));
        out.add("mobKills", Json.obj(m, "bestiary.kills"));
        return out;
    }

    private static JsonObject bestiaryTotals(JsonElement m) {
        JsonObject out = new JsonObject();
        out.addProperty("totalKills", Json.sum(m, "bestiary.kills"));
        out.addProperty("totalDeaths", Json.sum(m, "bestiary.deaths"));
        return out;
    }

    // ---- The Chocolate Factory ------------------------------------------------------------------

    private static JsonObject chocolate(JsonElement m) {
        JsonElement e = Json.at(m, "events.easter");
        JsonObject out = new JsonObject();
        out.addProperty("chocolate", Json.num(e, "chocolate", 0));
        out.addProperty("totalChocolate", Json.num(e, "total_chocolate", 0));
        out.addProperty("chocolateSincePrestige", Json.num(e, "chocolate_since_prestige", 0));
        out.addProperty("chocolateSpent", Json.num(e, "shop.chocolate_spent", 0));
        out.addProperty("prestige", Json.num(e, "chocolate_level", 0));
        out.addProperty("lastViewed", Json.num(e, "last_viewed_chocolate_factory", 0));
        out.addProperty("refinedTrufflesConsumed", Json.num(e, "refined_dark_cacao_truffles", 0));
        out.addProperty("cocoaFortuneUpgrades", Json.num(e, "shop.cocoa_fortune_upgrades", 0));
        // Every rabbit found, with how often; Hoppity's list says which rarity each is.
        JsonObject rabbits = new JsonObject();
        for (var r : Json.obj(e, "rabbits").entrySet()) {
            double n = Json.num(r.getValue());
            if (!Double.isNaN(n) && n > 0) rabbits.addProperty(r.getKey(), n);
        }
        out.add("rabbits", rabbits);
        out.addProperty("unlockedZorro", rabbits.has("zorro"));
        return out;
    }

    // ---- Inventories ----------------------------------------------------------------------------

    /** The Rift's inventories among the others, by the names the pages ask for. */
    private static final String[][] RIFT_INVENTORIES = {{"rift_inventory", "inv_contents"}, {"rift_armor", "inv_armor"},
            {"rift_equipment", "equipment_contents"}, {"rift_ender_chest", "ender_chest_contents"}};

    private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

    /**
     * Every inventory of the member, read out of Hypixel's packed form, by the name the pages know
     * it under: "inventory", "ender_chest", "backpack_3", "wardrobe", "rift_ender_chest". Each is
     * {"items": {"0": item, ...}}; one that can't be read is left out and logged by the caller.
     */
    static Map<String, JsonElement> inventories(JsonElement answer, java.util.function.BiConsumer<String, Exception> failed) {
        JsonElement m = Json.at(answer, "profile.member");
        JsonElement inv = Json.at(m, "inventory");
        Map<String, JsonElement> out = new LinkedHashMap<>();
        blob(out, "inventory", Json.at(inv, "inv_contents"), failed);
        blob(out, "armor", Json.at(inv, "inv_armor"), failed);
        blob(out, "equipment", Json.at(inv, "equipment_contents"), failed);
        blob(out, "ender_chest", Json.at(inv, "ender_chest_contents"), failed);
        blob(out, "personal_vault", Json.at(inv, "personal_vault_contents"), failed);
        for (var bag : Json.obj(inv, "bag_contents").entrySet()) blob(out, bag.getKey(), bag.getValue(), failed);
        for (var pack : Json.obj(inv, "backpack_contents").entrySet()) blob(out, "backpack_" + pack.getKey(), pack.getValue(), failed);

        // The icon each backpack is drawn with, under the backpack's number.
        JsonObject icons = new JsonObject();
        for (var icon : Json.obj(inv, "backpack_icons").entrySet()) {
            JsonElement item = single(icon.getValue(), "icons_backpack", failed);
            if (item != null) icons.add(icon.getKey(), item);
        }
        if (!icons.isEmpty()) out.put("icons_backpack", items(icons));

        sets(out, "wardrobe", Json.obj(m, "loadout.armor"), ARMOR_PIECES, failed);
        sets(out, "equipment_wardrobe", Json.obj(m, "loadout.equipment"),
                new String[] {"EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4"}, failed);
        toolkit(out, "farming_toolkit", Json.obj(m, "garden_player_data.farming_toolkit"), failed);
        toolkit(out, "hunting_toolkit", Json.obj(m, "foraging.hunting_toolkit"), failed);
        for (String[] names : RIFT_INVENTORIES) blob(out, names[0], Json.at(m, "rift.inventory." + names[1]), failed);
        return out;
    }

    /**
     * What was donated to a profile's museum, from the API's museum answer, as one inventory: every
     * piece with its SkyBlock data. One taken out again is marked with the
     * attribute {@code museum_borrowing}, which {@link Museum} reads.
     */
    static JsonObject museum(JsonElement answer, java.util.function.BiConsumer<String, Exception> failed) {
        JsonObject items = new JsonObject();
        int slot = 0;
        JsonElement museum = Json.at(answer, "museum");
        List<JsonElement> donations = new ArrayList<>();
        for (var e : Json.obj(museum, "items").entrySet()) donations.add(e.getValue());
        for (JsonElement special : Json.arr(museum, "special")) donations.add(special);
        for (JsonElement donation : donations) {
            String data = Json.str(donation, "items.data");
            if (data == null || data.isBlank()) continue;
            try {
                for (var item : Json.obj(HypixelItems.inventory(data), "items").entrySet()) {
                    if (!item.getValue().isJsonObject()) continue;
                    if (Json.bool(donation, "borrowing")) {
                        Json.obj(item.getValue(), "attributes").addProperty("museum_borrowing", "1");
                    }
                    items.add(String.valueOf(slot++), item.getValue());
                }
            } catch (java.io.IOException | RuntimeException e) {
                failed.accept("museum", e);
            }
        }
        return items(items);
    }

    private static JsonObject items(JsonObject items) {
        JsonObject out = new JsonObject();
        out.add("items", items);
        return out;
    }

    private static void blob(Map<String, JsonElement> out, String name, JsonElement blob,
                             java.util.function.BiConsumer<String, Exception> failed) {
        String data = Json.str(blob, "data");
        if (data == null || data.isBlank()) return;
        try {
            out.put(name, HypixelItems.inventory(data));
        } catch (java.io.IOException | RuntimeException e) {
            failed.accept(name, e);
        }
    }

    /** The one item of a blob that holds just one, such as a wardrobe piece; null when it is empty. */
    private static JsonElement single(JsonElement blob, String name, java.util.function.BiConsumer<String, Exception> failed) {
        String data = Json.str(blob, "data");
        if (data == null || data.isBlank()) return null;
        try {
            JsonElement item = HypixelItems.single(data);
            return item.isJsonObject() ? item : null;
        } catch (java.io.IOException | RuntimeException e) {
            failed.accept(name, e);
            return null;
        }
    }

    /**
     * The wardrobe's sets as one inventory, a piece under "set:PIECE" as the wardrobe page looks
     * them up. The set worn is on the player, not in here; which one that is goes with the list of
     * inventories, see {@link #inventoryList}.
     */
    private static void sets(Map<String, JsonElement> out, String name, JsonObject sets, String[] pieces,
                             java.util.function.BiConsumer<String, Exception> failed) {
        if (sets.isEmpty()) return;
        JsonObject items = new JsonObject();
        for (var set : sets.entrySet()) {
            if (!set.getValue().isJsonObject()) continue;
            for (String piece : pieces) {
                JsonElement item = single(set.getValue().getAsJsonObject().get(piece), name, failed);
                if (item != null) items.add(set.getKey() + ":" + piece, item);
            }
        }
        out.put(name, items(items));
    }

    /** A tool kit's tools in a row, in the order Hypixel lists the kinds. */
    private static void toolkit(Map<String, JsonElement> out, String name, JsonObject kit,
                                java.util.function.BiConsumer<String, Exception> failed) {
        JsonObject items = new JsonObject();
        int slot = 0;
        for (var kind : kit.entrySet()) {
            if (!kind.getValue().isJsonArray()) continue;
            for (JsonElement tool : kind.getValue().getAsJsonArray()) {
                JsonElement item = single(tool, name, failed);
                if (item != null) items.add(String.valueOf(slot), item);
                slot++;
            }
        }
        if (slot > 0) out.put(name, items(items));
    }

    /** The inventories as the profile lists them: name, how many slots, and what else a page needs to know. */
    private static JsonArray inventoryList(JsonElement m, Map<String, JsonElement> inventories) {
        JsonArray out = new JsonArray();
        for (var e : inventories.entrySet()) {
            String name = e.getKey();
            JsonObject items = Json.obj(e.getValue(), "items");
            JsonObject ref = new JsonObject();
            ref.addProperty("id", name);
            ref.addProperty("name", name);
            JsonObject metadata = new JsonObject();
            int slots = items.size();
            if (name.equals("wardrobe") || name.equals("equipment_wardrobe")) {
                String source = name.equals("wardrobe") ? "loadout.armor" : "loadout.equipment";
                int worn = (int) Json.num(m, source + ".equipped_set", -1);
                // As many sets as the highest that holds anything or is worn, four pieces each.
                int highest = Math.max(0, worn);
                for (String key : items.keySet()) {
                    try {
                        highest = Math.max(highest, Integer.parseInt(key.substring(0, key.indexOf(':'))));
                    } catch (RuntimeException ignored) {
                        // Not "set:PIECE".
                    }
                }
                slots = highest * 4;
                if (worn > 0) metadata.addProperty("equipped_set", String.valueOf(worn));
            } else if (name.equals("icons_backpack")) {
                slots = 0;
            } else if (name.endsWith("_toolkit")) {
                // Empty places in a tool kit are not in its items, but still count as slots.
                for (String key : items.keySet()) slots = Math.max(slots, Integer.parseInt(key) + 1);
            }
            ref.addProperty("slotCount", slots);
            ref.add("metadata", metadata);
            out.add(ref);
        }
        return out;
    }
}
