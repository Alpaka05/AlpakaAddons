package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How many rabbits of each rarity Hoppity's collection has, for the Rabbits page. Elite
 * only counts the rabbits a profile found; the whole collection comes from the NEU repository's
 * {@code constants/hoppity.json}.
 */
final class Hoppity {
    private Hoppity() {}

    /** The rarities from common up, as Elite and NEU name them. */
    static final String[] RARITIES = {"common", "uncommon", "rare", "epic", "legendary", "mythic", "divine"};

    private static final String URL = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/hoppity.json";
    private static volatile Map<String, Integer> rabbits;
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
        ProfileApi.neuItem(URL).thenApply(Hoppity::parse).whenComplete((parsed, failure) -> {
            if (failure != null) {
                AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't load NEU's rabbits: {}", failure.toString());
                retryAt = System.currentTimeMillis() + 60_000L;
            } else {
                rabbits = parsed;
            }
            fetching = false;
        });
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
