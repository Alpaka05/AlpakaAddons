package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hoppity's collection, for the Rabbits page: how many rabbits of each rarity there are, and which
 * rarity each rabbit is. Hypixel only names the rabbits a profile found and how often; the whole
 * collection comes from the NEU repository's {@code constants/hoppity.json}.
 */
final class Hoppity {
    private Hoppity() {}

    /** The rarities from common up, as NEU names them. */
    static final String[] RARITIES = {"common", "uncommon", "rare", "epic", "legendary", "mythic", "divine"};

    private static final String URL = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/hoppity.json";
    private static volatile Map<String, Integer> rabbits;
    /** Each rabbit's rarity, by the rabbit's id. */
    private static volatile Map<String, String> rarities = Map.of();
    private static volatile boolean fetching;
    private static volatile long retryAt;

    /** The rabbits of each rarity, or null while they load; asking starts loading them. */
    static Map<String, Integer> rabbits() {
        Map<String, Integer> loaded = rabbits;
        if (loaded == null) load();
        return loaded;
    }

    private static synchronized void load() {
        if (fetching || rabbits != null || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        ProfileApi.neuItem(URL).whenComplete((json, failure) -> {
            try {
                if (failure != null) throw new IllegalStateException(failure.toString());
                rarities = rarities(json);
                rabbits = parse(json);
            } catch (RuntimeException e) {
                AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't load NEU's rabbits: {}", e.toString());
                retryAt = System.currentTimeMillis() + 60_000L;
            }
            fetching = false;
        });
    }

    /**
     * The rabbits a profile found, by rarity in the order of {@link #RARITIES}: how many different
     * ones, and how many with duplicates. Null while the collection loads. {@code found} is
     * Hypixel's list of the profile's rabbits, each with how often it turned up.
     */
    static int[][] found(com.google.gson.JsonObject found) {
        if (rabbits() == null) return null;
        return found(found, rarities);
    }

    static int[][] found(com.google.gson.JsonObject found, Map<String, String> rarityOf) {
        int[][] out = new int[2][RARITIES.length];
        for (var e : found.entrySet()) {
            String rarity = rarityOf.get(e.getKey());
            double times = Json.num(e.getValue());
            if (rarity == null || Double.isNaN(times) || times <= 0) continue;
            for (int i = 0; i < RARITIES.length; i++) {
                if (!RARITIES[i].equals(rarity)) continue;
                out[0][i]++;
                out[1][i] += (int) times;
            }
        }
        return out;
    }

    /** Every rabbit's rarity, by its id. */
    static Map<String, String> rarities(String json) {
        JsonElement root = JsonParser.parseString(json);
        Map<String, String> out = new java.util.HashMap<>();
        for (String rarity : RARITIES) {
            for (JsonElement rabbit : Json.arr(root, "hoppity.rarities." + rarity + ".rabbits")) {
                if (rabbit.isJsonPrimitive()) out.put(rabbit.getAsString(), rarity);
            }
        }
        return out;
    }

    static Map<String, Integer> parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String rarity : RARITIES) {
            int n = Json.arr(root, "hoppity.rarities." + rarity + ".rabbits").size();
            if (n > 0) out.put(rarity, n);
        }
        return out;
    }
}
