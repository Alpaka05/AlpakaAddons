package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Museum as the game's menu has it: its categories, the items and armor sets each holds, and
 * which of them a profile donated.
 *
 * The donated items come as the member's {@code museum} inventory ({@link HypixelProfile#museum}),
 * armor sets split into their pieces. Items taken out of the museum stay in it, marked {@code museum_borrowing}:
 * they still count as donated, as in the game, while the networth's museum category leaves them out. What the categories hold, which pieces make up a set and which items count as
 * donated with a better one come from the NEU repository's {@code constants/museum.json}, as in the
 * other profile viewers. What is donated but in none of the categories is a special item.
 */
final class Museum {
    private Museum() {}

    /** A category as NEU lists it: its id ("combat") and its items and sets, by NEU's ids. */
    record CategoryDef(String id, List<String> items) {
        String name() {
            return id.equals("dungeoneering") ? "Dungeons" : ProfileData.title(id);
        }
    }

    /**
     * NEU's file: the categories, each set's pieces and the piece it is shown by, the lower item each
     * upgrade donates along ("children") and the ids that stand for another (starred dungeon items).
     */
    record Repo(List<CategoryDef> categories, Map<String, List<String>> sets, Map<String, String> setIcons,
                Map<String, String> children, Map<String, String> mapped) {
        int total() {
            int n = 0;
            for (CategoryDef c : categories) n += c.items().size();
            return n;
        }
    }

    /**
     * One item or set of a category on a profile: whether it counts as donated, and the donated
     * items it stands for (empty when it only counts through a better one).
     */
    record Entry(String id, boolean donated, List<JsonElement> items) {
        /** Taken out of the museum right now: donated, but every piece of it is with the player. */
        boolean borrowed() {
            if (items.isEmpty()) return false;
            for (JsonElement item : items) if (!Museum.borrowed(item)) return false;
            return true;
        }
    }

    /** Whether a donated item is out of the museum, with the player, right now. */
    static boolean borrowed(JsonElement item) {
        String flag = Json.str(item, "attributes.museum_borrowing");
        return flag != null && !flag.equals("0") && !flag.equalsIgnoreCase("false");
    }

    record Category(CategoryDef def, List<Entry> entries) {
        String name() {
            return def.name();
        }

        int donated() {
            int n = 0;
            for (Entry e : entries) if (e.donated()) n++;
            return n;
        }

        double progress() {
            return entries.isEmpty() ? 0 : donated() / (double) entries.size();
        }
    }

    /** A profile's museum: the categories, and the special items donated outside them. */
    record Donated(List<Category> categories, List<JsonElement> special) {
        int donated() {
            int n = 0;
            for (Category c : categories) n += c.donated();
            return n;
        }

        /** The entries and special items taken out right now. */
        int borrowed() {
            int n = 0;
            for (Category c : categories) for (Entry e : c.entries()) if (e.borrowed()) n++;
            for (JsonElement item : special) if (Museum.borrowed(item)) n++;
            return n;
        }
    }

    private static final String URL = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/museum.json";
    private static volatile Repo repo;
    private static volatile boolean fetching;
    private static volatile long retryAt;

    /** NEU's museum, or null while it loads; asking starts loading it. */
    static Repo repo() {
        Repo loaded = repo;
        if (loaded == null) load();
        return loaded;
    }

    static boolean failed() {
        return repo == null && !fetching && retryAt > 0;
    }

    private static synchronized void load() {
        if (fetching || repo != null || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        ProfileApi.neuItem(URL).thenApply(Museum::parse).whenComplete((parsed, failure) -> {
            if (failure != null) {
                AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't load NEU's museum: {}", failure.toString());
                retryAt = System.currentTimeMillis() + 60_000L;
            } else {
                repo = parsed;
            }
            fetching = false;
        });
    }

    /** The categories in NEU's order; "special" is left out, as the game puts no end to it. */
    static Repo parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        List<CategoryDef> categories = new ArrayList<>();
        for (var e : Json.obj(root, "items").entrySet()) {
            if (e.getKey().equals("special") || !e.getValue().isJsonArray()) continue;
            List<String> items = new ArrayList<>();
            for (JsonElement id : e.getValue().getAsJsonArray()) items.add(id.getAsString());
            categories.add(new CategoryDef(e.getKey(), List.copyOf(items)));
        }
        Map<String, List<String>> sets = new HashMap<>();
        for (var e : Json.obj(root, "sets_to_items").entrySet()) {
            List<String> pieces = new ArrayList<>();
            if (e.getValue().isJsonArray()) for (JsonElement id : e.getValue().getAsJsonArray()) pieces.add(id.getAsString());
            sets.put(e.getKey(), List.copyOf(pieces));
        }
        return new Repo(List.copyOf(categories), sets, strings(Json.obj(root, "armor_to_id")),
                strings(Json.obj(root, "children")), strings(Json.obj(root, "mapped_ids")));
    }

    private static Map<String, String> strings(JsonObject o) {
        Map<String, String> out = new HashMap<>();
        for (var e : o.entrySet()) if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
        return out;
    }

    /** The profile's museum from the items of the {@code museum} inventory. */
    static Donated of(Repo repo, Iterable<JsonElement> museumItems) {
        Map<String, String> pieceToSet = new HashMap<>();
        repo.sets().forEach((set, pieces) -> pieces.forEach(p -> pieceToSet.put(p, set)));
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (CategoryDef c : repo.categories()) listed.addAll(c.items());

        Map<String, List<JsonElement>> byEntry = new LinkedHashMap<>();
        List<JsonElement> special = new ArrayList<>();
        for (JsonElement item : museumItems) {
            String id = Json.str(item, "skyblockId");
            if (id == null) continue;
            String entry = entryOf(repo, pieceToSet, listed, id);
            if (entry == null) special.add(item);
            else byEntry.computeIfAbsent(entry, k -> new ArrayList<>()).add(item);
        }
        // A donated upgrade counts its lower tiers as donated too, all the way down.
        java.util.Set<String> donated = new java.util.HashSet<>(byEntry.keySet());
        List<String> queue = new ArrayList<>(donated);
        while (!queue.isEmpty()) {
            String lower = repo.children().get(queue.remove(queue.size() - 1));
            if (lower != null && donated.add(lower)) queue.add(lower);
        }

        List<Category> categories = new ArrayList<>();
        for (CategoryDef def : repo.categories()) {
            List<Entry> entries = new ArrayList<>();
            for (String id : def.items()) entries.add(new Entry(id, donated.contains(id), byEntry.getOrDefault(id, List.of())));
            categories.add(new Category(def, entries));
        }
        return new Donated(categories, special);
    }

    /** What a donated item counts as: itself, its set, or the item a starred one stands for; null for a special item. */
    private static String entryOf(Repo repo, Map<String, String> pieceToSet, java.util.Set<String> listed, String id) {
        String mapped = repo.mapped().getOrDefault(id, id);
        if (listed.contains(mapped)) return mapped;
        String set = pieceToSet.get(mapped);
        if (set != null) return set;
        // Starred dungeon armor that NEU doesn't map ("STARRED_ADAPTIVE_BOOTS") is the plain piece's.
        if (mapped.startsWith("STARRED_")) return entryOf(repo, pieceToSet, listed, mapped.substring("STARRED_".length()));
        return null;
    }

    /** The item an entry is shown by: a set by the piece NEU picks for it, else its first piece. */
    static String iconId(Repo repo, String entry) {
        String piece = repo.setIcons().get(entry);
        if (piece != null) return piece;
        List<String> pieces = repo.sets().get(entry);
        return pieces != null && !pieces.isEmpty() ? pieces.get(0) : entry;
    }

    static boolean isSet(Repo repo, String entry) {
        return repo.sets().containsKey(entry);
    }
}
