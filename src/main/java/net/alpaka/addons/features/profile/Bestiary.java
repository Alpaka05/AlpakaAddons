package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Bestiary as the game's menu has it: islands, the mob families on each, and the tier a profile
 * reached in every family.
 *
 * Elite only has the kills per mob ({@code stats.bestiary.mobKills}, "ghast_85": 1902); which mobs
 * make up a family, how many kills each tier takes and where a family caps come from the NEU
 * repository's {@code constants/bestiary.json}, as in the other profile viewers.
 */
final class Bestiary {
    private Bestiary() {}

    /** One family as NEU lists it: its mob ids, the kills of each tier up to the cap, and its icon. */
    record FamilyDef(String name, String texture, String item, List<String> mobs, long[] tiers) {}

    /** An island, or a part of one: its name, its icon (a head's texture or an item) and its families. */
    record IslandDef(String name, String texture, String item, List<FamilyDef> families) {}

    /** A family on a profile: the kills of all its mobs and the tier they reach. */
    record Family(FamilyDef def, long kills, int tier) {
        int maxTier() {
            return def.tiers().length;
        }

        boolean maxed() {
            return tier >= maxTier();
        }

        long prevAt() {
            return tier == 0 ? 0 : def.tiers()[tier - 1];
        }

        /** The kills the next tier takes; 0 once maxed. */
        long nextAt() {
            return maxed() ? 0 : def.tiers()[tier];
        }
    }

    record Island(IslandDef def, List<Family> families) {
        String name() {
            return def.name();
        }

        int maxed() {
            int n = 0;
            for (Family f : families) if (f.maxed()) n++;
            return n;
        }

        /** How far the island is, by tiers: every tier of every family counts the same, as in the milestone. */
        double progress() {
            int tiers = 0, max = 0;
            for (Family f : families) {
                tiers += f.tier();
                max += f.maxTier();
            }
            return max == 0 ? 0 : tiers / (double) max;
        }
    }

    private static final String URL = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/bestiary.json";
    private static volatile List<IslandDef> repo;
    private static volatile boolean fetching;
    private static volatile long retryAt;

    /** NEU's islands, or null while they load; asking starts loading them. */
    static List<IslandDef> repo() {
        List<IslandDef> loaded = repo;
        if (loaded == null) load();
        return loaded;
    }

    static boolean failed() {
        return repo == null && !fetching && retryAt > 0;
    }

    private static synchronized void load() {
        if (fetching || repo != null || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        ProfileApi.neuItem(URL).thenApply(Bestiary::parse).whenComplete((parsed, failure) -> {
            if (failure != null) {
                AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't load NEU's bestiary: {}", failure.toString());
                retryAt = System.currentTimeMillis() + 60_000L;
            } else {
                repo = parsed;
            }
            fetching = false;
        });
    }

    /**
     * The islands in NEU's order. Fishing and the Critter Safari come in sub-categories, which
     * become islands of their own ("Fishing · Lava"); the bracket tables are not islands at all.
     */
    static List<IslandDef> parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        List<IslandDef> islands = new ArrayList<>();
        if (!root.isJsonObject()) return islands;
        JsonObject brackets = Json.obj(root, "brackets");
        JsonObject sets = Json.obj(root, "bracketSets");
        for (var e : root.getAsJsonObject().entrySet()) {
            if (!e.getValue().isJsonObject() || e.getKey().equals("brackets") || e.getKey().equals("bracketSets")) continue;
            JsonObject island = e.getValue().getAsJsonObject();
            String name = Json.str(island, "name");
            if (Json.bool(island, "hasSubcategories")) {
                for (var sub : island.entrySet()) {
                    if (!sub.getValue().isJsonObject() || !sub.getValue().getAsJsonObject().has("mobs")) continue;
                    String subName = Json.str(sub.getValue(), "name");
                    islands.add(island(subName == null || subName.equals(name) ? name : name + " · " + subName, sub.getValue(), brackets, sets));
                }
            } else if (island.has("mobs")) {
                islands.add(island(name, island, brackets, sets));
            }
        }
        islands.removeIf(i -> i.families().isEmpty());
        return islands;
    }

    private static IslandDef island(String name, JsonElement island, JsonObject brackets, JsonObject sets) {
        List<FamilyDef> families = new ArrayList<>();
        for (JsonElement f : Json.arr(island, "mobs")) {
            String bracket = String.valueOf((int) Json.num(f, "bracket", 1));
            String type = Json.str(f, "bracketType");
            JsonElement table = type == null ? brackets.get(bracket) : Json.obj(sets, type).get(bracket);
            long cap = (long) Json.num(f, "cap", 0);
            List<Long> tiers = new ArrayList<>();
            if (table != null && table.isJsonArray()) {
                for (JsonElement t : table.getAsJsonArray()) if (t.getAsLong() <= cap) tiers.add(t.getAsLong());
            }
            List<String> mobs = new ArrayList<>();
            for (JsonElement m : Json.arr(f, "mobs")) mobs.add(m.getAsString());
            if (mobs.isEmpty() || tiers.isEmpty()) continue;
            families.add(new FamilyDef(plain(Json.str(f, "name")), Json.str(f, "texture"), Json.str(f, "item"), mobs,
                    tiers.stream().mapToLong(Long::longValue).toArray()));
        }
        return new IslandDef(name == null ? "?" : plain(name), Json.str(island, "icon.texture"), Json.str(island, "icon.item"), families);
    }

    /** NEU writes the names in the game's colour codes ("§aGhast"). */
    private static String plain(String name) {
        return name == null ? "?" : name.replaceAll("§.", "").trim();
    }

    /** Every island with the profile's tier in each family, from its kills per mob. */
    static List<Island> of(List<IslandDef> repo, JsonElement mobKills) {
        List<Island> out = new ArrayList<>();
        for (IslandDef island : repo) {
            List<Family> families = new ArrayList<>();
            for (FamilyDef def : island.families()) {
                long kills = 0;
                for (String mob : def.mobs()) kills += (long) Json.num(mobKills, mob, 0);
                int tier = 0;
                while (tier < def.tiers().length && kills >= def.tiers()[tier]) tier++;
                families.add(new Family(def, kills, tier));
            }
            out.add(new Island(island, families));
        }
        return out;
    }
}
