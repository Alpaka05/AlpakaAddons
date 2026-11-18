package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How much of each crop every Crop Milestone takes, for the Farming page. Hypixel hands out how
 * much of a crop was farmed in the Garden and leaves the milestone to be read off a table; the
 * tables come from the NEU repository's {@code constants/garden.json}.
 */
final class GardenTables {
    private GardenTables() {}

    private static final String URL = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/garden.json";
    private static volatile Map<String, long[]> milestones;
    private static volatile boolean fetching;
    private static volatile long retryAt;

    /**
     * The Crop Milestone reached with {@code amount} of the crop farmed, by the crop's name ("Sugar
     * Cane"); -1 while the tables load or for a crop they don't have. Asking starts loading them.
     */
    static int milestone(String crop, double amount) {
        Map<String, long[]> loaded = milestones;
        if (loaded == null) {
            load();
            return -1;
        }
        return milestone(loaded, crop, amount);
    }

    static int milestone(Map<String, long[]> tables, String crop, double amount) {
        long[] steps = tables.get(crop.toUpperCase(Locale.ROOT).replace(' ', '_'));
        if (steps == null) return -1;
        int level = 0;
        double left = amount;
        while (level < steps.length && left >= steps[level]) {
            left -= steps[level];
            level++;
        }
        return level;
    }

    private static synchronized void load() {
        if (fetching || milestones != null || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        ProfileApi.neuItem(URL).thenApply(GardenTables::parse).whenComplete((parsed, failure) -> {
            if (failure != null) {
                AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't load NEU's crop milestones: {}", failure.toString());
                retryAt = System.currentTimeMillis() + 60_000L;
            } else {
                milestones = parsed;
            }
            fetching = false;
        });
    }

    /** What each milestone of each crop takes, the first milestone first, by NEU's name for the crop ("SUGAR_CANE"). */
    static Map<String, long[]> parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        Map<String, long[]> out = new HashMap<>();
        for (var crop : Json.obj(root, "crop_milestones").entrySet()) {
            if (!crop.getValue().isJsonArray()) continue;
            var steps = crop.getValue().getAsJsonArray();
            long[] table = new long[steps.size()];
            for (int i = 0; i < table.length; i++) table[i] = (long) Json.num(steps.get(i));
            out.put(crop.getKey(), table);
        }
        return out;
    }
}
