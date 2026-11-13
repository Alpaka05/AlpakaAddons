package net.alpaka.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * What the Profile Dashboard gets from Hypixel: of {@code /v2/skyblock/profiles} the player's
 * profiles and, for each, the parts of the player's own member data the dashboard draws; of
 * {@code /v2/skyblock/garden} and {@code /v2/skyblock/museum} likewise. Co-op members' data is left
 * out, but for the two things a co-op shares and the dashboard shows: the collection tiers
 * unlocked and the minions crafted.
 *
 * This is what keeps the server the dashboard's backend rather than a copy of Hypixel's API: it
 * answers with what one screen of the mod draws, nothing more. A new dashboard page that needs
 * more of Hypixel's data adds its fields to the lists here.
 */
final class Dashboard {
    private Dashboard() {}

    /** Kept of the player's member data, as dotted paths into it. */
    static final List<String> MEMBER_FIELDS = List.of(
            // Levels, coins and what the overview shows.
            "player_data.experience",
            "player_data.death_count",
            "profile.bank_account",
            "profile.first_join",
            "currencies",
            "leveling.experience",
            "leveling.highest_pet_score",
            "leveling.fishing_festival_sharks_killed",
            "leveling.mining_fiesta_ores_mined",
            "fairy_soul",
            "item_data.soulflow",
            "accessory_bag_storage.highest_magical_power",
            "accessory_bag_storage.selected_power",
            "collection",
            "objectives.tutorial",
            // Dungeons and slayers.
            "dungeons.dungeon_types.catacombs.experience",
            "dungeons.dungeon_types.catacombs.tier_completions",
            "dungeons.dungeon_types.catacombs.fastest_time",
            "dungeons.dungeon_types.catacombs.fastest_time_s",
            "dungeons.dungeon_types.catacombs.fastest_time_s_plus",
            "dungeons.dungeon_types.catacombs.best_score",
            "dungeons.dungeon_types.master_catacombs.tier_completions",
            "dungeons.dungeon_types.master_catacombs.fastest_time",
            "dungeons.dungeon_types.master_catacombs.fastest_time_s",
            "dungeons.dungeon_types.master_catacombs.fastest_time_s_plus",
            "dungeons.dungeon_types.master_catacombs.best_score",
            "dungeons.player_classes",
            "dungeons.selected_dungeon_class",
            "dungeons.secrets",
            "slayer.slayer_bosses",
            // Pets.
            "pets_data.pets",
            "pets_data.pet_care.pet_types_sacrificed",
            // Every inventory, the wardrobe and the tool kits.
            "inventory",
            "loadout.armor",
            "loadout.equipment",
            "garden_player_data.farming_toolkit",
            "foraging.hunting_toolkit",
            // The skill pages.
            "jacobs_contest.medals_inv",
            "jacobs_contest.perks",
            "jacobs_contest.contests",
            "jacobs_contest.unique_brackets",
            "jacobs_contest.personal_bests",
            "garden_player_data.copper",
            "skill_tree.nodes",
            "skill_tree.selected_ability",
            "skill_tree.tokens_spent",
            "skill_tree.experience",
            "mining_core",
            "glacite_player_data",
            "foraging.tree_gifts",
            "foraging.starlyn.personal_bests",
            "foraging.hina.tasks.tier_claimed",
            "foraging_core.whispers",
            "trophy_fish",
            "quests.trapper_quest.pelt_count",
            // Combat, the Crimson Isle and the Bestiary.
            "nether_island_player_data.kuudra_completed_tiers",
            "nether_island_player_data.dojo",
            "nether_island_player_data.selected_faction",
            "nether_island_player_data.mages_reputation",
            "nether_island_player_data.barbarians_reputation",
            "bestiary.kills",
            "bestiary.deaths",
            "bestiary.milestone",
            "player_stats.kills",
            "player_stats.deaths",
            "player_stats.highest_damage",
            "player_stats.highest_critical_damage",
            "player_stats.items_fished",
            "player_stats.sea_creature_kills",
            "player_stats.mythos",
            "player_stats.end_island.dragon_fight",
            "player_stats.pets.milestone",
            "player_stats.rift.visits",
            "player_stats.rift.lifetime_motes_earned",
            "player_stats.rift.motes_orb_pickup",
            // The Rift and the Chocolate Factory.
            "rift.access",
            "rift.castle",
            "rift.dead_cats",
            "rift.enigma",
            "rift.gallery",
            "rift.wither_cage",
            "rift.lifetime_purchased_boundaries",
            "rift.inventory",
            "events.easter.rabbits",
            "events.easter.chocolate",
            "events.easter.total_chocolate",
            "events.easter.chocolate_since_prestige",
            "events.easter.last_viewed_chocolate_factory",
            "events.easter.chocolate_level",
            "events.easter.refined_dark_cacao_truffles",
            "events.easter.shop.chocolate_spent",
            "events.easter.shop.cocoa_fortune_upgrades");

    /** Kept of a profile's garden. */
    static final List<String> GARDEN_FIELDS = List.of(
            "garden_experience",
            "unlocked_plots_ids",
            "resources_collected",
            "crop_upgrade_levels",
            "composter_data.upgrades",
            "commission_data.total_completed",
            "commission_data.unique_npcs_served");

    /** Kept of the player's part of a profile's museum. */
    static final List<String> MUSEUM_FIELDS = List.of("value", "appraisal", "items", "special");

    /** One of the player's profiles: what the profile list shows, and the dashboard's part of its data as JSON. */
    record Profile(String id, String name, String mode, boolean selected, int members, String json) {}

    /**
     * Hypixel's profiles answer for {@code player}, one entry for each profile the player is still
     * on. A profile the player left or was kicked from keeps their data with a deletion notice; it
     * is no longer in the game's profile menu, so it isn't here either.
     */
    static List<Profile> profiles(String hypixelBody, String player) {
        Object root = Json.parse(hypixelBody);
        List<Profile> out = new ArrayList<>();
        if (!(root instanceof Map<?, ?> r) || !(r.get("profiles") instanceof List<?> list)) return out;
        for (Object p : list) {
            if (!(p instanceof Map<?, ?> profile) || !(profile.get("profile_id") instanceof String id)) continue;
            if (!(profile.get("members") instanceof Map<?, ?> members) || !(members.get(player) instanceof Map<?, ?> member)) continue;
            if (left(member)) continue;

            int active = 0;
            Map<String, Integer> tiers = new LinkedHashMap<>();
            TreeSet<String> generators = new TreeSet<>();
            for (Object m : members.values()) {
                if (!left(m)) active++;
                // What a co-op unlocked stays unlocked, whoever did it and whether they are still there.
                Object data = m instanceof Map<?, ?> map ? map.get("player_data") : null;
                if (!(data instanceof Map<?, ?> playerData)) continue;
                if (playerData.get("unlocked_coll_tiers") instanceof List<?> unlocked) {
                    for (Object t : unlocked) {
                        if (!(t instanceof String tier)) continue;
                        int cut = tier.lastIndexOf('_');
                        if (cut <= 0) continue;
                        try {
                            tiers.merge(tier.substring(0, cut), Integer.parseInt(tier.substring(cut + 1)), Math::max);
                        } catch (NumberFormatException ignored) {
                            // Not "ITEM_3": no tier to read.
                        }
                    }
                }
                if (playerData.get("crafted_generators") instanceof List<?> crafted) {
                    for (Object g : crafted) if (g instanceof String generator) generators.add(generator);
                }
            }

            Map<String, Object> coop = new LinkedHashMap<>();
            coop.put("collection_tiers", tiers);
            coop.put("crafted_generators", new ArrayList<>(generators));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("profile_id", id.replace("-", ""));
            put(data, "cute_name", profile.get("cute_name"));
            put(data, "game_mode", profile.get("game_mode"));
            put(data, "selected", profile.get("selected"));
            put(data, "members", new Json.Num(String.valueOf(active)));
            put(data, "bank", profile.get("banking") instanceof Map<?, ?> banking ? banking.get("balance") : null);
            data.put("coop", coop);
            data.put("member", keep(member, MEMBER_FIELDS));

            out.add(new Profile(id.replace("-", ""), profile.get("cute_name") instanceof String name ? name : null,
                    profile.get("game_mode") instanceof String mode ? mode : null, Boolean.TRUE.equals(profile.get("selected")),
                    active, Json.write(data)));
        }
        return out;
    }

    /** Whether a member's data is all that is left of them on the profile. */
    private static boolean left(Object member) {
        return member instanceof Map<?, ?> m && m.get("profile") instanceof Map<?, ?> profile && profile.containsKey("deletion_notice");
    }

    /** The list of profiles as the answer has it, without their data. */
    static String index(List<Profile> profiles) {
        List<Object> out = new ArrayList<>();
        for (Profile p : profiles) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("profile_id", p.id());
            put(ref, "cute_name", p.name());
            put(ref, "game_mode", p.mode());
            ref.put("selected", p.selected());
            ref.put("members", new Json.Num(String.valueOf(p.members())));
            out.add(ref);
        }
        return Json.write(out);
    }

    /** The dashboard's part of Hypixel's garden answer as JSON, or null when there is no garden in it. */
    static String garden(String hypixelBody) {
        Object root = Json.parse(hypixelBody);
        if (!(root instanceof Map<?, ?> r) || !(r.get("garden") instanceof Map<?, ?> garden)) return null;
        return Json.write(keep(garden, GARDEN_FIELDS));
    }

    /** The player's part of Hypixel's museum answer as JSON; an empty object when they donated nothing. */
    static String museum(String hypixelBody, String player) {
        Object root = Json.parse(hypixelBody);
        Object member = root instanceof Map<?, ?> r && r.get("members") instanceof Map<?, ?> members ? members.get(player) : null;
        return Json.write(member == null ? Map.of() : keep(member, MUSEUM_FIELDS));
    }

    private static void put(Map<String, Object> into, String key, Object value) {
        if (value != null) into.put(key, value);
    }

    /** Only the given paths of {@code source}, in the same nesting; paths that aren't there are skipped. */
    static Map<String, Object> keep(Object source, List<String> paths) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String path : paths) {
            String[] parts = path.split("\\.");
            Object value = source;
            for (String part : parts) {
                value = value instanceof Map<?, ?> m ? m.get(part) : null;
                if (value == null) break;
            }
            if (value == null) continue;
            Map<String, Object> at = out;
            for (int i = 0; i < parts.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) at.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
                at = next;
            }
            at.put(parts[parts.length - 1], value);
        }
        return out;
    }
}
