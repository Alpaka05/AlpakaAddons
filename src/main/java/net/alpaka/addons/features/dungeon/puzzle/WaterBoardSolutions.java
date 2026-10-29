// Solution data adapted from Odin by odtheking, BSD-3-Clause, see CREDITS.md.
package net.alpaka.addons.features.dungeon.puzzle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Water Board's solution tables: for each board layout and each set of three closed doors, when
 * to flip which lever.
 *
 * The board is not solved live. Its solutions were worked out by hand and checked in game: one run
 * of the water, with every lever flip timed in seconds after the water lever is switched on. A 0.0
 * on a board lever means "flip it before the water"; the water's own list starts with that 0.0
 * (switching it on starts the clock) and a second time switches it off again. Four layouts times ten
 * door sets make 40 solutions per set.
 *
 * Two sets ship in {@code assets/alpaka/dungeon/water_board.json}, converted from Odin's
 * {@code water-solutions.json} with every number unchanged: Standard, the solutions by derek1984,
 * Jade and bonsai that Desco1/WaterSolver collected, and Fast, Odin's own quicker ones with flips
 * closer together. Credits and Odin's licence are in CREDITS.md.
 */
public final class WaterBoardSolutions {

    /** The two solution sets in the data file. */
    public enum Table {
        STANDARD("standard"),
        FAST("fast");

        private final String key;

        Table(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public static final String RESOURCE = "/assets/alpaka/dungeon/water_board.json";

    /** Layouts 1..4, each with every door key; levers in their data order. */
    private final Map<Table, Map<Integer, Map<String, Map<WaterBoardLever, double[]>>>> sets;

    private static WaterBoardSolutions bundled;
    private static boolean bundledFailed;

    private WaterBoardSolutions(Map<Table, Map<Integer, Map<String, Map<WaterBoardLever, double[]>>>> sets) {
        this.sets = sets;
    }

    /**
     * The tables shipped with the mod, read on first use; null if the file is missing or broken,
     * which is logged once. Client thread.
     */
    public static WaterBoardSolutions bundled() {
        if (bundled != null || bundledFailed) return bundled;
        try (InputStream in = WaterBoardSolutions.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("missing " + RESOURCE);
            bundled = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            bundledFailed = true;
            AlpakaAddons.LOGGER.error("Water Board solutions could not be read; the solver stays off", e);
        }
        return bundled;
    }

    /** Reads tables in the data file's format. Throws on anything that is not that format. */
    public static WaterBoardSolutions parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject setsJson = root.getAsJsonObject("sets");
        if (setsJson == null) throw new JsonParseException("no \"sets\"");
        Map<Table, Map<Integer, Map<String, Map<WaterBoardLever, double[]>>>> sets = new EnumMap<>(Table.class);
        for (Table set : Table.values()) {
            JsonObject variants = setsJson.getAsJsonObject(set.key());
            if (variants == null) throw new JsonParseException("no set \"" + set.key() + "\"");
            Map<Integer, Map<String, Map<WaterBoardLever, double[]>>> byVariant = new HashMap<>();
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                int number = Integer.parseInt(variant.getKey());
                Map<String, Map<WaterBoardLever, double[]>> byDoors = new HashMap<>();
                for (Map.Entry<String, JsonElement> doors : variant.getValue().getAsJsonObject().entrySet()) {
                    Map<WaterBoardLever, double[]> levers = new EnumMap<>(WaterBoardLever.class);
                    for (Map.Entry<String, JsonElement> lever : doors.getValue().getAsJsonObject().entrySet()) {
                        WaterBoardLever which = WaterBoardLever.byKey(lever.getKey());
                        if (which == null) throw new JsonParseException("unknown lever \"" + lever.getKey() + "\"");
                        JsonArray times = lever.getValue().getAsJsonArray();
                        double[] seconds = new double[times.size()];
                        for (int i = 0; i < seconds.length; i++) seconds[i] = times.get(i).getAsDouble();
                        levers.put(which, seconds);
                    }
                    byDoors.put(doors.getKey(), levers);
                }
                byVariant.put(number, byDoors);
            }
            sets.put(set, byVariant);
        }
        return new WaterBoardSolutions(sets);
    }

    /**
     * The solution for layout {@code variant} (1..4) with the doors in {@code doorKey} closed, as
     * seconds per lever (copies), or null if there is none.
     */
    public Map<WaterBoardLever, double[]> get(Table set, int variant, String doorKey) {
        Map<Integer, Map<String, Map<WaterBoardLever, double[]>>> byVariant = sets.get(set);
        if (byVariant == null) return null;
        Map<String, Map<WaterBoardLever, double[]>> byDoors = byVariant.get(variant);
        if (byDoors == null) return null;
        Map<WaterBoardLever, double[]> levers = byDoors.get(doorKey);
        if (levers == null) return null;
        Map<WaterBoardLever, double[]> copy = new EnumMap<>(WaterBoardLever.class);
        levers.forEach((lever, seconds) -> copy.put(lever, seconds.clone()));
        return copy;
    }

    /** The layouts a set has, for the tests. */
    public Set<Integer> variants(Table set) {
        return Collections.unmodifiableSet(sets.getOrDefault(set, Map.of()).keySet());
    }

    /** The door keys a layout of a set has, for the tests. */
    public Set<String> doorKeys(Table set, int variant) {
        return Collections.unmodifiableSet(sets.getOrDefault(set, Map.of()).getOrDefault(variant, Map.of()).keySet());
    }

    /**
     * Everything that would make a solution unusable, one line each; empty when the tables are
     * sound. Four layouts with all ten door sets each; every key three ascending door indices; every
     * list non-empty, ascending, not negative; the water there, starting at 0, with at most an
     * on and an off; layout 3 never using coal or terracotta, which it has no blocks of.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        List<String> allKeys = allDoorKeys();
        for (Table set : Table.values()) {
            Map<Integer, Map<String, Map<WaterBoardLever, double[]>>> byVariant = sets.getOrDefault(set, Map.of());
            if (!byVariant.keySet().equals(Set.of(1, 2, 3, 4))) {
                problems.add(set.key() + ": layouts " + byVariant.keySet());
            }
            for (Map.Entry<Integer, Map<String, Map<WaterBoardLever, double[]>>> variant : byVariant.entrySet()) {
                String where = set.key() + "/" + variant.getKey();
                if (!variant.getValue().keySet().equals(Set.copyOf(allKeys))) {
                    problems.add(where + ": door keys " + variant.getValue().keySet());
                }
                for (Map.Entry<String, Map<WaterBoardLever, double[]>> doors : variant.getValue().entrySet()) {
                    String at = where + "/" + doors.getKey();
                    Map<WaterBoardLever, double[]> levers = doors.getValue();
                    double[] water = levers.get(WaterBoardLever.WATER);
                    if (water == null || water.length == 0 || water[0] != 0.0) problems.add(at + ": water does not start at 0");
                    else if (water.length > 2) problems.add(at + ": water switched more than on and off");
                    for (Map.Entry<WaterBoardLever, double[]> lever : levers.entrySet()) {
                        double[] seconds = lever.getValue();
                        if (seconds.length == 0) problems.add(at + "/" + lever.getKey().key() + ": empty");
                        for (int i = 0; i < seconds.length; i++) {
                            if (!(seconds[i] >= 0.0) || Double.isInfinite(seconds[i])) {
                                problems.add(at + "/" + lever.getKey().key() + ": bad time " + seconds[i]);
                            }
                            if (i > 0 && !(seconds[i] > seconds[i - 1])) {
                                problems.add(at + "/" + lever.getKey().key() + ": not ascending");
                            }
                        }
                        if (lever.getKey().representative(variant.getKey()) == null && !lever.getKey().isWater()) {
                            problems.add(at + ": uses " + lever.getKey().key() + ", which this layout has no blocks of");
                        }
                    }
                }
            }
        }
        return problems;
    }

    /** The ten ways to pick three of the five doors, as keys. */
    public static List<String> allDoorKeys() {
        List<String> keys = new ArrayList<>(10);
        for (int mask = 0; mask < 1 << WaterBoardLayout.DOORS; mask++) {
            if (Integer.bitCount(mask) == 3) keys.add(WaterBoardLayout.doorKey(mask));
        }
        Collections.sort(keys);
        return keys;
    }
}
