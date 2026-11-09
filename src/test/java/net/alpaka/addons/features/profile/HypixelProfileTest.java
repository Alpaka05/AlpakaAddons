package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hypixel's raw numbers become what the pages show: the API's answer in, the dashboard's profile out. */
class HypixelProfileTest {

    /** The API's answer for a small profile, in Hypixel's own names, with a few packed inventories. */
    private static JsonElement answer() throws IOException {
        String chest = HypixelItemsTest.riftInventory();
        String helmet = HypixelItemsTest.oneItem("WISE_DRAGON_HELMET", "§6Wise Dragon Helmet");
        String tool = HypixelItemsTest.bareTool("CACTUS_KNIFE_3");
        return JsonParser.parseString("""
                {"success": true, "fetchedAt": 1794257419000,
                 "player": {"uuid": "8f16f5a695d3468392aed51c1a5bb037", "name": "Alpakaa", "skin": "9c04"},
                 "profiles": [
                   {"profile_id": "b135", "cute_name": "Peach", "selected": false, "members": 2},
                   {"profile_id": "93ea", "cute_name": "Kiwi", "game_mode": "ironman", "selected": true, "members": 1}],
                 "garden": {"garden_experience": 150, "unlocked_plots_ids": ["beginner_1", "beginner_2"],
                   "resources_collected": {"WHEAT": 1000, "INK_SACK:3": 77}, "crop_upgrade_levels": {"WHEAT": 4},
                   "composter_data": {"upgrades": {"speed": 3}},
                   "commission_data": {"total_completed": 12, "unique_npcs_served": 5}},
                 "profile": {"profile_id": "b135", "cute_name": "Peach", "bank": 500.5,
                   "coop": {"collection_tiers": {"WHEAT": 5},
                     "crafted_generators": ["WHEAT_1", "WHEAT_2", "WHEAT_12", "COW_1", "COW_2", "CACTUS_12"]},
                   "member": {
                     "player_data": {"experience": {"SKILL_FARMING": 55172425, "SKILL_MINING": 175, "SKILL_RUNECRAFTING": 150,
                       "SKILL_FORAGING": 0, "SKILL_FORAGING_extra_level_cap": 2}},
                     "profile": {"bank_account": 20},
                     "currencies": {"coin_purse": 1000.25, "motes_purse": 9, "essence": {"WITHER": {"current": 4}}},
                     "leveling": {"experience": 12345, "highest_pet_score": 77},
                     "collection": {"WHEAT": 900},
                     "objectives": {"tutorial": ["commission_milestone_reward_mining_xp_tier_2",
                       "commission_milestone_reward_mining_xp_tier_4", "commission_milestone_reward_carlton"]},
                     "accessory_bag_storage": {"highest_magical_power": 321},
                     "dungeons": {"selected_dungeon_class": "mage", "secrets": 40,
                       "player_classes": {"mage": {"experience": 50}},
                       "dungeon_types": {
                         "catacombs": {"experience": 135, "tier_completions": {"1": 3}, "fastest_time_s": {"1": 100000}, "best_score": {"1": 301}},
                         "master_catacombs": {"tier_completions": {"2": 1}}}},
                     "slayer": {"slayer_bosses": {
                       "zombie": {"xp": 220, "boss_kills_tier_0": 4, "boss_kills_tier_3": 2},
                       "enderman": {"xp": 30}}},
                     "pets_data": {"pet_care": {"pet_types_sacrificed": ["A", "B", "C"]}, "pets": [
                       {"uuid": "p1", "type": "TIGER", "tier": "LEGENDARY", "exp": 25353230, "active": true, "skin": "TIGER_TWILIGHT"},
                       {"uuid": "p2", "type": "GOLDEN_DRAGON", "tier": "LEGENDARY", "exp": 25358785, "heldItem": "PET_ITEM_X"},
                       {"uuid": "p3", "type": "BEE", "tier": "COMMON", "exp": 155}]},
                     "inventory": {
                       "inv_contents": {"type": 0, "data": "%1$s"},
                       "backpack_contents": {"2": {"type": 0, "data": "%1$s"}},
                       "backpack_icons": {"2": {"type": 0, "data": "%2$s"}},
                       "sacks_counts": {"WHEAT": 0, "INK_SACK:3": 5}},
                     "loadout": {"armor": {"equipped_set": 2, "1": {"id": 1, "HELMET": {"type": 0, "data": "%2$s"}},
                       "2": {"id": 2}, "3": {"id": 3, "BOOTS": {"type": 0, "data": "%2$s"}}, "7": {"id": 7}}},
                     "garden_player_data": {"copper": 44, "farming_toolkit": {"IS_UNLOCKED": true, "IN_USE": {"CACTUS": {"0": true}},
                       "WHEAT": [{"type": 0, "data": ""}], "CACTUS": [{"type": 0, "data": "%3$s"}]}},
                     "jacobs_contest": {"perks": {"double_drops": 4, "farming_level_cap": 3},
                       "unique_brackets": {"gold": ["INK_SACK:3"], "diamond": ["WHEAT", "INK_SACK:3"], "silver": ["CACTUS"]},
                       "personal_bests": {"INK_SACK:3": 900},
                       "contests": {
                         "1:1_1:WHEAT": {"collected": 500, "claimed_position": 1, "claimed_participants": 100},
                         "1:1_2:INK_SACK:3": {"collected": 200, "claimed_medal": "gold"},
                         "1:1_3:WHEAT": {"collected": 50},
                         "1:1_4:WHEAT": {"collected": 400, "claimed_position": 90, "claimed_participants": 100}}},
                     "skill_tree": {"experience": {"mining": 12000}, "tokens_spent": {"mountain": 7},
                       "selected_ability": {"mining": "pickobulus"},
                       "nodes": {"mining": {"mining_speed": 50, "toggle_mining_speed": false, "core_of_the_mountain": 3}}},
                     "mining_core": {"powder_mithril": 100, "powder_spent_mithril": 50, "greater_mines_last_access": 99,
                       "crystals": {"jade_crystal": {"state": "FOUND", "total_placed": 2, "total_found": 3},
                         "amber_crystal": {"total_placed": 4}, "amethyst_crystal": {"total_placed": 2},
                         "sapphire_crystal": {"total_placed": 3}, "topaz_crystal": {"total_placed": 5}}},
                     "nether_island_player_data": {"selected_faction": "mages", "mages_reputation": 12,
                       "kuudra_completed_tiers": {"none": 2, "highest_wave_none": 5, "burning": 1, "highest_wave_burning": 9},
                       "dojo": {"dojo_points_archer": 1000, "dojo_time_archer": 5, "dojo_points_snake": 799}},
                     "trophy_fish": {"total_caught": 7, "rewards": [1, 2, 99], "last_caught": "blobfish/silver",
                       "blobfish": 5, "blobfish_bronze": 4, "blobfish_silver": 1},
                     "player_stats": {"kills": {"total": 30, "zombie": 20, "minos_hunter": 10}, "deaths": {"total": 3},
                       "highest_damage": 5, "highest_critical_damage": 9, "sea_creature_kills": 2600,
                       "items_fished": {"total": 11, "treasure": 2},
                       "pets": {"milestone": {"sea_creatures_killed": 2600, "ores_mined": 10}}},
                     "bestiary": {"kills": {"zombie_1": 20, "pest_mite_1": 60}, "deaths": {"zombie_1": 2},
                       "milestone": {"last_claimed_milestone": 8}},
                     "events": {"easter": {"total_chocolate": 5000, "chocolate_level": 2, "shop": {"chocolate_spent": 40},
                       "rabbits": {"collected_eggs": {"lunch": 1}, "zorro": 2, "aaron": 1}}},
                     "rift": {"dead_cats": {"found_cats": ["first", "second"], "unlocked_pet": true},
                       "enigma": {"found_souls": ["A", "B", "C"]}, "castle": {"grubber_stacks": 4},
                       "inventory": {"ender_chest_contents": {"type": 0, "data": "%1$s"}}}}}}
                """.formatted(chest, helmet, tool));
    }

    private static JsonObject model() throws IOException {
        JsonElement answer = answer();
        return HypixelProfile.model(answer, HypixelProfile.inventories(answer, (name, e) -> {
            throw new AssertionError(name, e);
        }));
    }

    @Test
    void theAccountListsTheProfilesWithTheirMembers() throws IOException {
        JsonObject account = HypixelProfile.account(answer());
        assertEquals("Alpakaa", Json.str(account, "name"));
        assertEquals("9c04", Json.str(account, "skin.texture"));
        List<ProfileSession.ProfileRef> profiles = ProfileSession.profiles(account, null, 0);
        // The selected one first, as the game's menu has them.
        assertEquals(List.of("Kiwi", "Peach"), profiles.stream().map(ProfileSession.ProfileRef::name).toList());
        assertEquals(2, profiles.get(1).members());
        assertEquals("ironman", profiles.get(0).gameMode());
    }

    @Test
    void xpBecomesLevelsWithTheMembersOwnCaps() throws IOException {
        ProfileData d = new ProfileData(model(), "Alpakaa");
        assertEquals("b135", d.profileId);
        assertEquals(1794257419L, d.lastUpdated);
        assertEquals(123.45, d.skyblockLevel, 1e-9);
        assertEquals(1000.25, d.purse, 1e-9);
        assertEquals(500.5, d.bank, 1e-9);
        assertEquals(20, d.personalBank, 1e-9);
        assertTrue(d.skillsApi && d.inventoryApi && d.collectionsApi);

        ProfileData.Level farming = d.skills.stream().filter(s -> s.id().equals("farming")).findFirst().orElseThrow();
        // Farming 50 exactly, with three of Anita's ten level caps bought: not maxed.
        assertEquals(50, farming.level());
        assertEquals(53, farming.maxLevel());
        assertEquals(0, farming.xpCurrent(), 1e-9);
        assertEquals(4_300_000, farming.xpForNext(), 1e-9);
        ProfileData.Level mining = d.skills.stream().filter(s -> s.id().equals("mining")).findFirst().orElseThrow();
        assertEquals(2, mining.level());
        assertEquals(60, mining.maxLevel());
        assertEquals(200, mining.xpForNext(), 1e-9);
        assertEquals(56, d.skills.stream().filter(s -> s.id().equals("foraging")).findFirst().orElseThrow().maxLevel());
        // Farming 50, Mining 2 and Foraging 0; Runecrafting counts neither for the average nor the total.
        assertEquals(17.3, d.skillAverage, 1e-9);
        assertEquals(55172425 + 175, d.skillTotalXp, 1e-6);
        assertEquals(53, HypixelProfile.skillCap("taming", Json.at(answer(), "profile.member")));
    }

    @Test
    void dungeonsAndSlayers() throws IOException {
        ProfileData d = new ProfileData(model(), "Alpakaa");
        assertEquals(2, d.catacombs.level());
        assertEquals(10, d.catacombs.xpCurrent(), 1e-9);
        assertEquals("mage", d.selectedClass);
        assertEquals(1, d.classes.size());
        assertEquals(1, d.classes.get(0).level());
        // One class at level 1 of the five.
        assertEquals(0.2, d.classAverage, 1e-9);
        assertEquals(40, d.secretsFound, 1e-9);
        assertEquals(4, d.totalRuns);
        ProfileData.Floor f1 = d.floors.stream().filter(f -> f.label().equals("F1")).findFirst().orElseThrow();
        assertEquals(3, f1.completions());
        assertEquals(100000, f1.fastestS());
        assertEquals(301, f1.bestScore());
        assertEquals(1, d.floors.stream().filter(f -> f.label().equals("M2")).findFirst().orElseThrow().completions());

        ProfileData.Slayer zombie = d.slayers.get(0);
        assertEquals("zombie", zombie.id());
        assertEquals(3, zombie.level());
        assertEquals(780, zombie.xpForNext(), 1e-9);
        assertEquals(0.025, zombie.progress(), 1e-9);
        assertEquals(4, zombie.kills()[0]);
        assertEquals(2, zombie.kills()[3]);
        // The Voidgloom's levels come later than the Revenant's.
        ProfileData.Slayer enderman = d.slayers.get(1);
        assertEquals(2, enderman.level());
        assertEquals(220, enderman.xpForNext(), 1e-9);
        assertEquals(250, d.slayerXp, 1e-9);
    }

    @Test
    void petsLevelByTheirRarity() throws IOException {
        ProfileData d = new ProfileData(model(), "Alpakaa");
        Map<String, ProfileData.Pet> pets = new java.util.HashMap<>();
        for (ProfileData.Pet p : d.pets) pets.put(p.type(), p);
        assertEquals(100, pets.get("TIGER").level());
        assertEquals(25353230, pets.get("TIGER").expForMax(), 1e-6);
        assertTrue(pets.get("TIGER").active());
        // A dragon goes on to 200: nothing for 101, 5,555 for 102.
        assertEquals(102, pets.get("GOLDEN_DRAGON").level());
        assertEquals(200, pets.get("GOLDEN_DRAGON").maxLevel());
        assertEquals(210255385, pets.get("GOLDEN_DRAGON").expForMax(), 1e-6);
        // A common pet starts at the table's beginning: 100 XP for level 2, 110 for level 3.
        assertEquals(2, pets.get("BEE").level());
        assertEquals(0.5, pets.get("BEE").progress(), 1e-9);
        assertEquals(1, d.petsMaxed);
        assertEquals(1, d.petSkins);
        assertEquals(77, d.petScore);
    }

    @Test
    void whatTheCoopSharesAndTheMinionSlots() throws IOException {
        JsonObject model = model();
        assertEquals(5, Json.num(model, "collectionTiers.WHEAT", 0));
        assertEquals(900, Json.num(model, "collections.WHEAT", 0));
        // Six unique minions crafted: one slot past the five every profile has, the next at fifteen.
        assertEquals(6, Json.num(model, "minionSummary.uniqueTiersCrafted", 0));
        assertEquals(6, Json.num(model, "minionSummary.currentSlots", 0));
        assertEquals(9, Json.num(model, "minionSummary.uniqueTiersToNextSlot", 0));
        // Only what a sack holds is listed.
        assertEquals(1, Json.obj(model, "sacks").size());
        assertEquals(5, Json.num(Json.obj(model, "sacks").get("INK_SACK:3")));
    }

    @Test
    void jacobsContestsCountByCropAndMedal() throws IOException {
        JsonObject model = model();
        // The contest with fifty collected was no participation.
        assertEquals(3, Json.num(model, "jacob.participations", 0));
        // First of a hundred is the best two percent; a claimed medal is taken as it is; 90th is none.
        assertEquals(1, Json.num(model, "jacob.earnedMedals.diamond", 0));
        assertEquals(1, Json.num(model, "jacob.earnedMedals.gold", 0));
        assertEquals(0, Json.num(model, "jacob.earnedMedals.bronze", 0));
        assertEquals(2, Json.num(model, "jacob.uniqueGoldMedals", 0));
        assertEquals(900, Json.num(model, "jacob.stats.personalBests.CocoaBeans", 0));
        assertEquals(2, Json.num(model, "jacob.stats.crops.Wheat.participations", 0));
        assertEquals(1, Json.num(model, "jacob.stats.crops.CocoaBeans.medals.gold", 0));
        assertEquals(4, Json.num(model, "jacob.perks.doubleDrops", 0));
        assertEquals("diamond", HypixelProfile.medal(JsonParser.parseString("{\"claimed_position\": 2, \"claimed_participants\": 100}")));
        assertNull(HypixelProfile.medal(JsonParser.parseString("{\"collected\": 5}")));
    }

    @Test
    void theGarden() throws IOException {
        JsonObject model = model();
        // 70 XP for level 2, 70 more for level 3, and 10 of the 140 towards level 4.
        assertEquals(3, Json.num(model, "garden.gardenLevel", 0));
        assertEquals(10 / 140.0, Json.num(model, "garden.gardenProgress", 0), 1e-9);
        assertEquals(12, Json.num(model, "garden.completedVisitors", 0));
        assertEquals(5, Json.num(model, "garden.uniqueVisitors", 0));
        assertEquals(1000, Json.num(model, "garden.crops.wheat", 0));
        assertEquals(77, Json.num(model, "garden.crops.cocoaBeans", 0));
        assertEquals(4, Json.num(model, "garden.cropUpgrades.wheat", 0));
        assertEquals(2, Json.arr(model, "garden.plots").size());
        assertEquals(3, Json.num(model, "garden.composter.upgrades.speed", 0));
        assertEquals(44, Json.num(model, "unparsed.copper", 0));
    }

    @Test
    void theHeartOfTheMountain() throws IOException {
        JsonObject model = model();
        assertEquals(3, Json.num(model, "stats.mining.hotmLevel", 0));
        assertEquals(3, Json.num(model, "stats.mining.peakOfTheMountainLevel", 0));
        assertEquals(7, Json.num(model, "stats.mining.tokensSpent", 0));
        assertEquals(50, Json.num(model, "stats.mining.nodes.mining_speed", 0));
        // A perk's switch is kept beside it as "toggle_<perk>".
        assertFalse(Json.obj(model, "stats.mining.nodes").has("toggle_mining_speed"));
        assertFalse(Json.bool(model, "stats.mining.nodeToggles.mining_speed"));
        assertTrue(Json.obj(model, "stats.mining.nodeToggles").has("mining_speed"));
        assertEquals(100, Json.num(model, "stats.mining.powder.mithril.available", 0));
        assertEquals(150, Json.num(model, "stats.mining.powder.mithril.total", 0));
        assertEquals(3, Json.num(model, "stats.mining.crystals.jade.totalFound", 0));
        // Jade and Amethyst were placed twice, the others more often: two whole Nucleus runs.
        assertEquals(2, Json.num(model, "stats.mining.crystalNucleusRuns", 0));
        assertEquals(4, Json.num(model, "stats.mining.commissionMilestone", 0));
        assertNull(Json.str(model, "stats.mining.rockMilestone.currentRarity"));
    }

    @Test
    void combatFishingAndTheRest() throws IOException {
        JsonObject model = model();
        assertEquals("burning", Json.str(model, "stats.crimsonIsle.highestKuudraTier"));
        assertEquals(3, Json.num(model, "stats.crimsonIsle.totalKuudraRuns", 0));
        assertEquals(9, Json.num(model, "stats.crimsonIsle.kuudraHighestWaves.burning", 0));
        assertEquals("S", Json.str(model, "stats.crimsonIsle.dojoTests.archer.rank"));
        assertEquals("B", Json.str(model, "stats.crimsonIsle.dojoTests.snake.rank"));
        assertEquals(1799, Json.num(model, "stats.crimsonIsle.dojoPointsTotal", 0));

        assertEquals("Adept", Json.str(model, "stats.fishing.trophyRank"));
        assertEquals(7, Json.num(model, "stats.fishing.totalTrophyFishCaught", 0));
        assertEquals(4, Json.num(model, "stats.fishing.trophyFishTierCounts.bronze", 0));
        assertEquals(5, Json.num(model, "stats.fishing.trophyFishCounts.blobfish", 0));
        assertEquals("RARE", Json.str(model, "stats.fishing.dolphinMilestone.currentRarity"));
        assertEquals(2, Json.num(model, "stats.fishing.treasuresCaught", 0));

        assertEquals(30, Json.num(model, "stats.playerStats.killsDeaths.totalKills", 0));
        assertEquals(10, Json.num(model, "stats.playerStats.derivations.killDeathRatio", 0));
        // The total is no mob, and both ways Hypixel counts a kill are there to look up.
        assertEquals("zombie", Json.str(Json.arr(model, "stats.playerStats.derivations.topKills").get(0), "mobId"));
        assertEquals(20, Json.num(model, "stats.playerStats.killsDeaths.kills.zombie_1", 0));
        assertEquals(10, Json.num(model, "stats.playerStats.mythos.killsMinosHunter", 0));
        assertEquals(9, Json.num(model, "stats.playerStats.damage.highestDamage", 0));
        assertEquals(8, Json.num(model, "stats.bestiary.milestoneLevel", 0));
        assertEquals(80, Json.num(model, "stats.bestiaryDerivations.totalKills", 0));
        assertEquals(321, new ProfileData(model, "Alpakaa").magicalPower, 1e-9);

        assertEquals(3, Json.num(model, "stats.rift.enigmaSoulsFound", 0));
        assertEquals(2, Json.arr(model, "stats.rift.foundCats").size());
        assertTrue(Json.bool(model, "stats.rift.unlockedRiftCatPet"));
        assertEquals(9, Json.num(model, "stats.rift.motesPurse", 0));

        assertEquals(5000, Json.num(model, "chocolateFactory.totalChocolate", 0));
        assertEquals(2, Json.num(model, "chocolateFactory.prestige", 0));
        assertEquals(40, Json.num(model, "chocolateFactory.chocolateSpent", 0));
        assertTrue(Json.bool(model, "chocolateFactory.unlockedZorro"));
        // The eggs' timestamps are no rabbits.
        assertEquals(2, Json.obj(model, "chocolateFactory.rabbits").size());
    }

    @Test
    void everyInventoryIsReadAndListed() throws IOException {
        JsonElement answer = answer();
        List<String> failed = new ArrayList<>();
        Map<String, JsonElement> inventories = HypixelProfile.inventories(answer, (name, e) -> failed.add(name));
        // The Wheat tool's data is empty: nothing in that place, and nothing to report.
        assertTrue(failed.isEmpty(), failed.toString());
        assertEquals(List.of("inventory", "backpack_2", "icons_backpack", "wardrobe", "farming_toolkit", "rift_ender_chest"),
                List.copyOf(inventories.keySet()));
        assertEquals("TIME_GUN", Json.str(Json.obj(inventories.get("backpack_2"), "items").get("0"), "skyblockId"));
        assertEquals("WISE_DRAGON_HELMET", Json.str(Json.obj(inventories.get("icons_backpack"), "items").get("2"), "skyblockId"));
        // Wardrobe pieces go by set and piece.
        JsonObject wardrobe = Json.obj(inventories.get("wardrobe"), "items");
        assertEquals(java.util.Set.of("1:HELMET", "3:BOOTS"), wardrobe.keySet());
        // The Cactus tool is the kit's second place, after the empty Wheat one.
        assertEquals("CACTUS_KNIFE_3", Json.str(Json.obj(inventories.get("farming_toolkit"), "items").get("1"), "skyblockId"));

        ProfileData d = new ProfileData(HypixelProfile.model(answer, inventories), "Alpakaa");
        assertEquals(2, d.inventories.get("inventory").slots());
        // Three sets hold or are something - the worn one is on the player - so twelve places.
        assertEquals(12, d.inventories.get("wardrobe").slots());
        assertEquals("2", Json.str(d.inventories.get("wardrobe").metadata(), "equipped_set"));
        assertEquals(2, d.inventories.get("farming_toolkit").slots());
        assertTrue(d.inventories.containsKey("icons_backpack"));
    }

    @Test
    void theMuseumMarksWhatWasTakenOut() throws IOException {
        String helmet = HypixelItemsTest.oneItem("WISE_DRAGON_HELMET", "§6Wise Dragon Helmet");
        JsonElement answer = JsonParser.parseString("""
                {"success": true, "museum": {"value": 5, "items": {
                    "WISE_DRAGON_HELMET": {"donated_time": 1, "items": {"type": 0, "data": "%1$s"}},
                    "OTHER": {"donated_time": 2, "borrowing": true, "items": {"type": 0, "data": "%1$s"}}},
                  "special": [{"donated_time": 3, "items": {"type": 0, "data": "%1$s"}}]}}
                """.formatted(helmet));
        JsonObject items = Json.obj(HypixelProfile.museum(answer, (name, e) -> {
            throw new AssertionError(name, e);
        }), "items");
        assertEquals(3, items.size());
        assertFalse(Museum.borrowed(items.get("0")));
        assertTrue(Museum.borrowed(items.get("1")));
        assertFalse(Museum.borrowed(items.get("2")));
        // Nothing donated is an empty museum, not a failure.
        assertTrue(Json.obj(HypixelProfile.museum(JsonParser.parseString("{\"museum\": {}}"), (name, e) -> { }), "items").isEmpty());
    }
}
