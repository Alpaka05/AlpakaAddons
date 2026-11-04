package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Where the perks of the Heart of the Mountain and the Heart of the Forest sit, so the dashboard
 * can draw each as the tree it is in the game rather than as a list.
 *
 * The layouts come from the NEU repository ({@code constants/hotmlayout.json} and
 * {@code hotflayout.json}), which every perk's place, name and top level is kept up to date in;
 * their ids are the ones Elite reports the profile's levels under.
 */
final class SkillTrees {
    private SkillTrees() {}

    /**
     * One perk: its grid position, {@code x} 0-6 left to right and {@code y} 0 at the top tier, its
     * top level, and whether it is a pickaxe or axe ability rather than a perk with levels.
     */
    record Perk(String id, String name, int x, int y, int maxLevel, boolean ability) {}

    record Tree(List<Perk> perks, int columns, int rows) {}

    private static final String CONSTANTS = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/";
    private static volatile Tree hotm;
    private static volatile Tree hotf;
    private static volatile boolean fetching;
    private static volatile long retryAt;

    /** The Heart of the Mountain, or null while it loads; asking starts loading both. */
    static Tree hotm() {
        load();
        return hotm;
    }

    /** The Heart of the Forest, or null while it loads. */
    static Tree hotf() {
        load();
        return hotf;
    }

    private static synchronized void load() {
        if (fetching || (hotm != null && hotf != null) || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        CompletableFuture<String> mountain = ProfileApi.neuItem(CONSTANTS + "hotmlayout.json");
        CompletableFuture<String> forest = ProfileApi.neuItem(CONSTANTS + "hotflayout.json");
        mountain.thenCombine(forest, (m, f) -> new Tree[] {parse(m, "hotm"), parse(f, "hotf")}).whenComplete((trees, failure) -> {
            if (failure != null) {
                AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't load NEU's skill tree layouts: {}", failure.toString());
                retryAt = System.currentTimeMillis() + 60_000L;
            } else {
                hotm = trees[0];
                hotf = trees[1];
            }
            fetching = false;
        });
    }

    static Tree parse(String body, String key) {
        JsonElement root = JsonParser.parseString(body);
        List<Perk> perks = new ArrayList<>();
        int columns = 0;
        int rows = 0;
        for (var e : Json.obj(root, key + ".perks").entrySet()) {
            JsonElement p = e.getValue();
            int x = (int) Json.num(p, "x", -1);
            int y = (int) Json.num(p, "y", -1);
            if (x < 0 || y < 0) continue;
            // NEU marks an ability by the item it shows - "(api ...)" - or by an isAbility key.
            String item = Json.str(p, "item");
            boolean ability = (item != null && item.startsWith("(api")) || p.getAsJsonObject().has("isAbility");
            String name = Json.str(p, "name");
            perks.add(new Perk(e.getKey(), name == null ? ProfileData.title(e.getKey()) : name, x, y,
                    (int) Json.num(p, "maxLevel", 1), ability));
            columns = Math.max(columns, x + 1);
            rows = Math.max(rows, y + 1);
        }
        perks.sort(Comparator.comparingInt(Perk::y).thenComparingInt(Perk::x));
        return new Tree(List.copyOf(perks), columns, rows);
    }

    /** The perk at this place, or null for an empty spot. */
    static Perk at(Tree tree, int x, int y) {
        for (Perk p : tree.perks()) if (p.x() == x && p.y() == y) return p;
        return null;
    }
}
