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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an item looks like when Hypixel's list does not have it: pets, runes, attribute shards, dyes
 * and many skins. Read from the NotEnoughUpdates item repository, which Skyblocker and most other
 * SkyBlock mods use too, one small file per item and only for items a suggestion actually shows.
 * The requests name nothing about the player.
 *
 * What the repository answered is kept on disk ({@code config/alpaka-neu-items.json}) for a while,
 * so the next session draws a big profile's hundreds of items without asking GitHub for each again.
 * When GitHub doesn't answer, nothing more is asked for a while, longer each time in a row.
 */
final class NeuItems {
    private NeuItems() {}

    private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/";
    /** How many files may be on their way at once: about the rows the list shows, not all its matches in one frame. */
    private static final int MAX_IN_FLIGHT = 6;
    private static final long RETRY_MS = 60_000L;
    /** The longest pause after GitHub failed several times in a row. */
    private static final long MAX_PAUSE_MS = 15 * 60_000L;
    private static final long DAY_MS = 24 * 3_600_000L;
    /** How long a look learned is kept on disk; the repository rarely changes one. */
    static final long FOUND_KEEP_MS = 14 * DAY_MS;
    /** How long it is remembered that the repository has no file for a tag; it may get one. */
    static final long MISSING_KEEP_MS = 3 * DAY_MS;
    /** How long a change to the kept looks waits for others before the file is written. */
    private static final long SAVE_DELAY_MS = 10_000L;

    private static final Pattern TEXTURE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)\\\\?\"");
    private static final Pattern MODEL = Pattern.compile("ItemModel:\\\\?\"([a-z0-9_.-]+):([a-z0-9_./-]+)\\\\?\"");
    private static final Pattern COLOR = Pattern.compile("\\bcolor:(\\d+)");

    /** What each tag looks like; an empty answer for a tag the repository does not know. */
    private static final Map<String, Optional<ItemIcons.Info>> LOOKS = new ConcurrentHashMap<>();
    /** Tags whose file is on its way, and when one that failed may be asked for again. */
    private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    /** The repository's ids for the Bazaar's shards, SHARD_CRETAN_BULL being ATTRIBUTE_SHARD_MYTHOLOGICAL_RESISTANCE;1. */
    private static volatile Map<String, String> shardIds = null;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** When each look in {@link #LOOKS} was learned, for how long it is kept on disk. */
    private static final Map<String, Long> LEARNED_AT = new ConcurrentHashMap<>();
    /** When the shard ids were learned; 0 for not from the repository. */
    private static volatile long shardIdsAt = 0L;
    /** 0 before the file is read, 1 while it is, 2 once it is. */
    private static final AtomicInteger DISK = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicBoolean saveQueued = new java.util.concurrent.atomic.AtomicBoolean();
    /** Reads and writes the file off the game's threads, one after the other. */
    private static final java.util.concurrent.ExecutorService FILES = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Alpaka NEU items");
        t.setDaemon(true);
        return t;
    });
    /** Nothing new is asked for before this, after GitHub failed; see {@link #failed}. */
    private static volatile long pauseUntil = 0L;
    private static final AtomicInteger FAILURES_IN_ROW = new AtomicInteger();

    /**
     * How the item with SkyCofl's tag looks, or null while that is not known yet or never will be;
     * asking starts finding out. Call it again on a later frame.
     */
    static ItemIcons.Info look(String tag) {
        Optional<ItemIcons.Info> known = LOOKS.get(tag);
        if (known != null) return known.orElse(null);
        // What the last sessions learned first: nothing is asked for that is on disk.
        if (!diskRead()) return null;
        known = LOOKS.get(tag);
        if (known != null) return known.orElse(null);
        if (System.currentTimeMillis() < pauseUntil) return null;
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
     * first; runes under their level, which SkyCofl's tag for a price may carry ({@code
     * RUNE_FIERY_BURST_1}) and other tags leave out, and Bazaar shards under the attribute they carry.
     */
    private static final Pattern RUNE_LEVEL = Pattern.compile("^RUNE_(.+)_(\\d)$");

    static List<String> candidates(String tag, Map<String, String> shards) {
        List<String> out = new ArrayList<>();
        if (shards != null && shards.containsKey(tag)) out.add(shards.get(tag));
        if (tag.startsWith("PET_") && !tag.startsWith("PET_SKIN_") && !tag.startsWith("PET_ITEM_")) {
            String pet = tag.substring(4);
            for (int rarity : new int[] {4, 3, 5, 2, 1, 0, 6, 7, 8}) out.add(pet + ";" + rarity);
        } else if (tag.startsWith("UNIQUE_RUNE_")) {
            out.add(tag.substring(12) + "_RUNE;3");
        } else if (tag.startsWith("RUNE_")) {
            Matcher levelled = RUNE_LEVEL.matcher(tag);
            if (levelled.matches()) {
                out.add(levelled.group(1) + "_RUNE;" + levelled.group(2));
            } else {
                String rune = tag.substring(5) + "_RUNE;";
                for (int level = 1; level <= 3; level++) out.add(rune + level);
            }
        }
        out.add(tag);
        return out;
    }

    private static void fetch(String tag, List<String> names, int index) {
        if (index >= names.size()) {
            learn(tag, null);
            done(tag, null);
            return;
        }
        String file = URLEncoder.encode(names.get(index), StandardCharsets.UTF_8).replace("+", "%20");
        HTTP.sendAsync(request(REPO + "items/" + file + ".json"), HttpResponse.BodyHandlers.ofString()).orTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || (response.statusCode() != 200 && response.statusCode() != 404)) {
                            failed();
                            done(tag, System.currentTimeMillis() + RETRY_MS);
                            return;
                        }
                        FAILURES_IN_ROW.set(0);
                        if (response.statusCode() == 404) {
                            fetch(tag, names, index + 1);
                            return;
                        }
                        learn(tag, parse(response.body()));
                        done(tag, null);
                    } catch (RuntimeException e) {
                        AlpakaAddons.LOGGER.debug("Could not read the NEU item {}", tag, e);
                        learn(tag, null);
                        done(tag, null);
                    }
                });
    }

    /** Notes what the repository says the tag looks like (null: nothing usable), and keeps it on disk. */
    private static void learn(String tag, ItemIcons.Info info) {
        LOOKS.put(tag, Optional.ofNullable(info));
        LEARNED_AT.put(tag, System.currentTimeMillis());
        saveSoon();
    }

    /**
     * GitHub didn't answer or refused: nothing new is asked for a while, twice as long each time in a
     * row up to {@link #MAX_PAUSE_MS}, rather than every failed item again a minute later.
     */
    private static void failed() {
        int inRow = FAILURES_IN_ROW.incrementAndGet();
        pauseUntil = System.currentTimeMillis() + pause(inRow);
    }

    static long pause(int failuresInRow) {
        return Math.min(MAX_PAUSE_MS, RETRY_MS << Math.min(Math.max(0, failuresInRow - 1), 10));
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
        HTTP.sendAsync(request(REPO + "constants/bazaarstocks.json"), HttpResponse.BodyHandlers.ofString()).orTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || response.statusCode() != 200) {
                            failed();
                            PENDING.put("#shards", System.currentTimeMillis() + RETRY_MS);
                            return;
                        }
                        FAILURES_IN_ROW.set(0);
                        shardIds = shardIds(response.body());
                        shardIdsAt = System.currentTimeMillis();
                        saveSoon();
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
     * value, as in Hypixel's list; where it also names today's model, that is taken instead, and a
     * model from Hypixel's resource pack is kept apart, as the pack may not be loaded.
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
        String packModel = null;
        Matcher model = MODEL.matcher(nbt);
        if (model.find()) {
            if (model.group(1).equals("minecraft")) {
                material = model.group(2);
                damage = 0;
            } else {
                packModel = model.group(1) + ":" + model.group(2);
            }
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
        return new ItemIcons.Info(material, damage, skin, glowing, color, packModel);
    }

    // ---- The looks kept on disk ------------------------------------------------------------------

    private static java.nio.file.Path file() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("alpaka-neu-items.json");
    }

    /** Whether the file has been read; the first call starts reading it in the background. */
    private static boolean diskRead() {
        int state = DISK.get();
        if (state == 2) return true;
        if (state == 0 && DISK.compareAndSet(0, 1)) {
            FILES.execute(() -> {
                try {
                    String json = net.alpaka.addons.config.AtomicJsonFile.read(file().toFile());
                    if (json != null) readKept(json, System.currentTimeMillis());
                } catch (java.io.IOException | RuntimeException e) {
                    // Asked for again then; the next write replaces the file.
                    AlpakaAddons.LOGGER.debug("Could not read the kept NEU items: {}", e.toString());
                } finally {
                    DISK.set(2);
                }
            });
        }
        return false;
    }

    /** Takes the looks from the file that are still fresh enough; one learned meanwhile stays. */
    static void readKept(String json, long now) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return;
        JsonObject o = root.getAsJsonObject();
        if (o.get("items") instanceof JsonObject items) {
            for (Map.Entry<String, JsonElement> e : items.entrySet()) {
                if (!(e.getValue() instanceof JsonObject kept) || !kept.has("at")) continue;
                long at = kept.get("at").getAsLong();
                ItemIcons.Info info = kept.get("look") instanceof JsonObject look ? info(look) : null;
                if (now - at > (info == null ? MISSING_KEEP_MS : FOUND_KEEP_MS)) continue;
                if (LOOKS.putIfAbsent(e.getKey(), Optional.ofNullable(info)) == null) LEARNED_AT.put(e.getKey(), at);
            }
        }
        if (o.get("shards") instanceof JsonObject shards && shards.has("at") && shards.get("ids") instanceof JsonObject ids) {
            long at = shards.get("at").getAsLong();
            if (now - at <= FOUND_KEEP_MS && shardIds == null) {
                Map<String, String> read = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : ids.entrySet()) read.put(e.getKey(), e.getValue().getAsString());
                shardIds = read;
                shardIdsAt = at;
            }
        }
    }

    /** What is written to the file: every look learned, with when, and the shard ids. */
    static String keptJson(long now) {
        JsonObject items = new JsonObject();
        for (Map.Entry<String, Long> e : LEARNED_AT.entrySet()) {
            Optional<ItemIcons.Info> look = LOOKS.get(e.getKey());
            if (look == null || now - e.getValue() > (look.isEmpty() ? MISSING_KEEP_MS : FOUND_KEEP_MS)) continue;
            JsonObject kept = new JsonObject();
            kept.addProperty("at", e.getValue());
            look.ifPresent(info -> kept.add("look", json(info)));
            items.add(e.getKey(), kept);
        }
        JsonObject root = new JsonObject();
        root.add("items", items);
        Map<String, String> shards = shardIds;
        if (shards != null && shardIdsAt > 0) {
            JsonObject ids = new JsonObject();
            shards.forEach(ids::addProperty);
            JsonObject kept = new JsonObject();
            kept.addProperty("at", shardIdsAt);
            kept.add("ids", ids);
            root.add("shards", kept);
        }
        return root.toString();
    }

    private static JsonObject json(ItemIcons.Info info) {
        JsonObject o = new JsonObject();
        o.addProperty("material", info.material());
        o.addProperty("damage", info.damage());
        if (info.skin() != null) o.addProperty("skin", info.skin());
        if (info.glowing()) o.addProperty("glowing", true);
        if (info.color() != null) o.addProperty("color", info.color());
        if (info.model() != null) o.addProperty("model", info.model());
        return o;
    }

    private static ItemIcons.Info info(JsonObject o) {
        if (!o.has("material")) return null;
        return new ItemIcons.Info(o.get("material").getAsString(), o.has("damage") ? o.get("damage").getAsInt() : 0,
                o.has("skin") ? o.get("skin").getAsString() : null, o.has("glowing") && o.get("glowing").getAsBoolean(),
                o.has("color") ? o.get("color").getAsString() : null, o.has("model") ? o.get("model").getAsString() : null);
    }

    /** Writes the file a moment from now, together with whatever else is learned until then. */
    private static void saveSoon() {
        if (!saveQueued.compareAndSet(false, true)) return;
        CompletableFuture.delayedExecutor(SAVE_DELAY_MS, TimeUnit.MILLISECONDS, FILES).execute(() -> {
            saveQueued.set(false);
            try {
                net.alpaka.addons.config.AtomicJsonFile.write(file().toFile(), keptJson(System.currentTimeMillis()));
            } catch (java.io.IOException | RuntimeException e) {
                AlpakaAddons.LOGGER.debug("Could not keep the NEU items: {}", e.toString());
            }
        });
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
    }
}
