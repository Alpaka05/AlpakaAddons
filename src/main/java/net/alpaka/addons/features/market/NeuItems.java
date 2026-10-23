package net.alpaka.addons.features.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.utils.ModVersion;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an item looks like when Hypixel's list does not have it: pets, runes, attribute shards, dyes
 * and many skins. Read from the NotEnoughUpdates item repository, which Skyblocker and most other
 * SkyBlock mods use too, one small file per item and only for items a suggestion actually shows.
 * The requests name nothing about the player.
 */
final class NeuItems {
    private NeuItems() {}

    private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/";
    /** How many files may be on their way at once: a search that lights up ten new pets should not fire ten requests in one frame. */
    private static final int MAX_IN_FLIGHT = 4;
    private static final long RETRY_MS = 60_000L;

    private static final Pattern TEXTURE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)\\\\?\"");
    private static final Pattern MODEL = Pattern.compile("ItemModel:\\\\?\"minecraft:([a-z0-9_]+)\\\\?\"");
    private static final Pattern COLOR = Pattern.compile("\\bcolor:(\\d+)");

    /** What each tag looks like; an empty answer for a tag the repository does not know. */
    private static final Map<String, Optional<ItemIcons.Info>> LOOKS = new ConcurrentHashMap<>();
    /** Tags whose file is on its way, and when one that failed may be asked for again. */
    private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    /** The repository's ids for the Bazaar's shards, SHARD_CRETAN_BULL being ATTRIBUTE_SHARD_MYTHOLOGICAL_RESISTANCE;1. */
    private static volatile Map<String, String> shardIds = null;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * How the item with SkyCofl's tag looks, or null while that is not known yet or never will be;
     * asking starts finding out. Call it again on a later frame.
     */
    static ItemIcons.Info look(String tag) {
        Optional<ItemIcons.Info> known = LOOKS.get(tag);
        if (known != null) return known.orElse(null);
        if (tag.startsWith("SHARD_") && shardIds == null) {
            fetchShardIds();
            return null;
        }
        Long waitUntil = PENDING.get(tag);
        if (waitUntil != null && (waitUntil == Long.MAX_VALUE || System.currentTimeMillis() < waitUntil)) return null;
        if (IN_FLIGHT.get() >= MAX_IN_FLIGHT) return null;
        PENDING.put(tag, Long.MAX_VALUE);
        IN_FLIGHT.incrementAndGet();
        fetch(tag, candidates(tag, shardIds), 0);
        return null;
    }

    /** Whether the repository is known not to have the item, or its file says nothing usable. */
    static boolean missing(String tag) {
        Optional<ItemIcons.Info> known = LOOKS.get(tag);
        return known != null && known.isEmpty();
    }

    /**
     * The repository's names to try for SkyCofl's tag, best guess first. Pets are filed under their
     * rarity ({@code GRIFFIN;4}), which the tag does not say, so every rarity is tried, Legendary
     * first; runes under their level, Bazaar shards under the attribute they carry.
     */
    static List<String> candidates(String tag, Map<String, String> shards) {
        List<String> out = new ArrayList<>();
        if (shards != null && shards.containsKey(tag)) out.add(shards.get(tag));
        if (tag.startsWith("PET_") && !tag.startsWith("PET_SKIN_") && !tag.startsWith("PET_ITEM_")) {
            String pet = tag.substring(4);
            for (int rarity : new int[] {4, 3, 5, 2, 1, 0, 6, 7, 8}) out.add(pet + ";" + rarity);
        } else if (tag.startsWith("UNIQUE_RUNE_")) {
            out.add(tag.substring(12) + "_RUNE;3");
        } else if (tag.startsWith("RUNE_")) {
            String rune = tag.substring(5) + "_RUNE;";
            for (int level = 1; level <= 3; level++) out.add(rune + level);
        }
        out.add(tag);
        return out;
    }

    private static void fetch(String tag, List<String> names, int index) {
        if (index >= names.size()) {
            LOOKS.put(tag, Optional.empty());
            done(tag, null);
            return;
        }
        String file = URLEncoder.encode(names.get(index), StandardCharsets.UTF_8).replace("+", "%20");
        HTTP.sendAsync(request(REPO + "items/" + file + ".json"), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || (response.statusCode() != 200 && response.statusCode() != 404)) {
                            done(tag, System.currentTimeMillis() + RETRY_MS);
                            return;
                        }
                        if (response.statusCode() == 404) {
                            fetch(tag, names, index + 1);
                            return;
                        }
                        ItemIcons.Info info = parse(response.body());
                        LOOKS.put(tag, Optional.ofNullable(info));
                        done(tag, null);
                    } catch (RuntimeException e) {
                        AlpakaAddons.LOGGER.debug("Could not read the NEU item {}", tag, e);
                        LOOKS.put(tag, Optional.empty());
                        done(tag, null);
                    }
                });
    }

    /** One request less on its way; with a time, the tag may be asked for again from then on. */
    private static void done(String tag, Long retryAt) {
        if (retryAt == null) PENDING.remove(tag);
        else PENDING.put(tag, retryAt);
        IN_FLIGHT.decrementAndGet();
    }

    private static synchronized void fetchShardIds() {
        if (shardIds != null) return;
        Long waitUntil = PENDING.get("#shards");
        // Long.MAX_VALUE while the list is on its way.
        if (waitUntil != null && System.currentTimeMillis() < waitUntil) return;
        PENDING.put("#shards", Long.MAX_VALUE);
        HTTP.sendAsync(request(REPO + "constants/bazaarstocks.json"), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || response.statusCode() != 200) {
                            PENDING.put("#shards", System.currentTimeMillis() + RETRY_MS);
                            return;
                        }
                        shardIds = shardIds(response.body());
                        PENDING.remove("#shards");
                    } catch (RuntimeException e) {
                        // Shards go by their own tag then, which the repository mostly does not know.
                        shardIds = Map.of();
                        PENDING.remove("#shards");
                    }
                });
    }

    /** The shard entries of the repository's Bazaar list: SHARD_ tag to the repository's id. */
    static Map<String, String> shardIds(String json) {
        Map<String, String> out = new HashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) return out;
        for (JsonElement e : root.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (!o.has("stock") || !o.has("id")) continue;
            String stock = o.get("stock").getAsString();
            if (stock.startsWith("SHARD_")) out.put(stock, o.get("id").getAsString());
        }
        return out;
    }

    /**
     * How a repository file says the item looks. Its item id is mostly the 1.8 one with a damage
     * value, as in Hypixel's list; where it also names today's model, that is taken instead.
     */
    static ItemIcons.Info parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return null;
        JsonObject o = root.getAsJsonObject();
        String itemId = o.has("itemid") ? o.get("itemid").getAsString() : "";
        if (itemId.isEmpty()) return null;
        int damage = o.has("damage") ? o.get("damage").getAsInt() : 0;
        String nbt = o.has("nbttag") ? o.get("nbttag").getAsString() : "";

        String material = itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId;
        if (material.equals("skull")) material = "skull_item";
        Matcher model = MODEL.matcher(nbt);
        if (model.find()) {
            material = model.group(1);
            damage = 0;
        }
        Matcher texture = TEXTURE.matcher(nbt);
        String skin = texture.find() ? texture.group(1) : null;
        boolean glowing = nbt.contains("ench:[");
        String color = null;
        Matcher dye = COLOR.matcher(nbt);
        if (dye.find()) {
            try {
                int rgb = Integer.parseInt(dye.group(1));
                color = (rgb >> 16 & 0xFF) + "," + (rgb >> 8 & 0xFF) + "," + (rgb & 0xFF);
            } catch (NumberFormatException ignored) {
                // Left undyed.
            }
        }
        return new ItemIcons.Info(material, damage, skin, glowing, color);
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
    }
}
