package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.market.Coins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the perks of the Heart of the Mountain and the Heart of the Forest sit, so the dashboard
 * can draw each as the tree it is in the game rather than as a list.
 *
 * The layouts come from the NEU repository ({@code constants/hotmlayout.json} and
 * {@code hotflayout.json}), which every perk's place, name and top level is kept up to date in;
 * their ids are the ones Hypixel keeps the profile's levels under.
 */
final class SkillTrees {
    private SkillTrees() {}

    /**
     * One perk: its grid position, {@code x} 0-6 left to right and {@code y} 0 at the top tier, its
     * top level, and whether it is a pickaxe or axe ability rather than a perk with levels.
     */
    record Perk(String id, String name, int x, int y, int maxLevel, boolean ability, JsonObject raw) {}

    /** {@code costLines} by powder or whisper name: the line that says what the next level costs. */
    record Tree(List<Perk> perks, int columns, int rows, Map<String, String> costLines) {}

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
                    (int) Json.num(p, "maxLevel", 1), ability, p.getAsJsonObject()));
            columns = Math.max(columns, x + 1);
            rows = Math.max(rows, y + 1);
        }
        perks.sort(Comparator.comparingInt(Perk::y).thenComparingInt(Perk::x));
        Map<String, String> costLines = new HashMap<>();
        for (var e : Json.obj(root, key + ".powders").entrySet()) {
            String line = Json.str(e.getValue(), "costLine");
            if (line != null) costLines.put(e.getKey(), line);
        }
        return new Tree(List.copyOf(perks), columns, rows, Map.copyOf(costLines));
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_\\-]+)}");

    /**
     * The perk's tooltip as the game and NEU's profile viewer show it, in Hypixel's colour codes:
     * the name (green maxed, yellow under way, red not taken), the level, the lore with its numbers
     * worked out for that level, and the cost of the next level while there is one.
     *
     * @param levels the profile's perk levels by id
     * @param core the tree's core perk ({@code core_of_the_mountain}), whose level the stats
     *             of some abilities follow ({@code potm} in NEU's files)
     * @param tier the Heart of the Mountain or Forest tier ({@code hotm})
     */
    static List<String> tooltip(Tree tree, Perk perk, JsonObject levels, String core, int tier, Boolean enabled) {
        int level = (int) Json.num(levels, perk.id(), 0);
        Map<String, Object> vars = new HashMap<>();
        vars.put("level", (double) Math.max(1, level));
        vars.put("level0", (double) level);
        vars.put("maxLevel", (double) perk.maxLevel());
        vars.put("potm", Json.num(levels, core, 0));
        vars.put("hotm", (double) tier);
        java.util.function.Function<String, Double> other = id -> Json.num(levels, id, 0);

        List<String> lines = new ArrayList<>();
        String color = level >= perk.maxLevel() ? "§a" : level == 0 ? "§c" : "§e";
        lines.add(color + perk.name());
        if (perk.maxLevel() > 1) {
            lines.add(level >= perk.maxLevel() ? "§7Level " + level : "§7Level " + level + "§8/" + perk.maxLevel());
            lines.add("");
        }
        for (JsonElement line : Json.arr(perk.raw(), "lore")) {
            String text;
            if (line.isJsonPrimitive()) {
                text = line.getAsString();
            } else {
                String onlyIf = Json.str(line, "onlyIf");
                if (onlyIf != null && !NeuLisp.truthy(NeuLisp.eval(onlyIf, vars, other))) continue;
                text = Json.str(line, "text");
                if (text == null) continue;
            }
            lines.add(fill(text, perk, vars, other));
        }
        if (level > 0 && level < perk.maxLevel()) {
            Map<String, Object> named = new HashMap<>(vars);
            for (String powder : tree.costLines().keySet()) named.put(powder, powder);
            Object powder = NeuLisp.eval(Json.str(perk.raw(), "powder"), named, other);
            String costLine = powder == null ? null : tree.costLines().get(String.valueOf(powder));
            if (costLine != null) {
                lines.add("");
                lines.add(fill(costLine, perk, vars, other));
            }
        }
        if (enabled != null && level > 0) {
            lines.add("");
            lines.add(enabled ? "§a§lENABLED" : "§c§lDISABLED");
        }
        return lines;
    }

    /** The line with every {name} replaced by that expression of the perk, worked out. */
    private static String fill(String text, Perk perk, Map<String, Object> vars, java.util.function.Function<String, Double> other) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object value = NeuLisp.eval(Json.str(perk.raw(), key), vars, other);
            String shown;
            if (value instanceof Double d) shown = Coins.full(key.equals("cost") ? Math.floor(d) : d);
            else if (value != null) shown = String.valueOf(value);
            else shown = "?";
            m.appendReplacement(out, Matcher.quoteReplacement(shown));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The perk at this place, or null for an empty spot. */
    static Perk at(Tree tree, int x, int y) {
        for (Perk p : tree.perks()) if (p.x() == x && p.y() == y) return p;
        return null;
    }
}
