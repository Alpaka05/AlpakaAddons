package net.alpaka.addons.features.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an item has sold for on the Auction House over the last day, on average, from SkyCofl's
 * price summary, for the Price Tooltip.
 *
 * Unlike the lowest BINs there is no list of every item, so each item is asked for on its own the
 * first time its tooltip shows. Answers are kept for a while and requests held to a modest rate, so
 * moving the mouse over a full chest asks for a handful at a time. Only asked for while Network
 * Features is on; a request names the item and, for a pet, its rarity - nothing about the player.
 */
public final class AveragePrice {
    private AveragePrice() {}

    private static final String BASE = "https://sky.coflnet.com/api/item/price/";
    private static final long FRESH_MS = 10 * 60_000L;
    private static final long RETRY_MS = 60_000L;
    private static final int PER_MINUTE = 20;
    /** A menu full of different items must not grow the cache without end. */
    private static final int MAX_ENTRIES = 2048;

    public enum State { LOADING, READY, NONE, FAILED }

    /** The average sale for one of the item, in coins; NaN unless READY. */
    public record Result(State state, double average) {}

    private static final class Entry {
        volatile State state = State.LOADING;
        volatile double average = Double.NaN;
        volatile long fetchedAt = 0L;
        volatile long retryAt = 0L;
        volatile boolean fetching = false;
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ArrayDeque<Long> SENT = new ArrayDeque<>();
    private static HttpClient client;

    /** The average for this item, asking SkyCofl in the background if it is not known or has gone stale. Never waits. */
    public static Result get(LowestBin.Item item) {
        if (!AlpakaConfig.instance.allowApiCalls) return new Result(State.NONE, Double.NaN);
        String key = item.tag() + "|" + item.rarity();
        if (CACHE.size() > MAX_ENTRIES && !CACHE.containsKey(key)) CACHE.clear();
        Entry entry = CACHE.computeIfAbsent(key, k -> new Entry());
        long now = System.currentTimeMillis();
        boolean due = entry.fetchedAt == 0L || entry.state == State.FAILED ? now >= entry.retryAt : now - entry.fetchedAt > FRESH_MS;
        if (due && !entry.fetching && take(now)) fetch(entry, url(item));
        return new Result(entry.state, entry.average);
    }

    static String url(LowestBin.Item item) {
        String url = BASE + URLEncoder.encode(item.tag(), StandardCharsets.UTF_8);
        return item.rarity() == null ? url : url + "?Rarity=" + URLEncoder.encode(item.rarity(), StandardCharsets.UTF_8);
    }

    private static synchronized boolean take(long now) {
        while (!SENT.isEmpty() && now - SENT.peekFirst() > 60_000L) SENT.pollFirst();
        if (SENT.size() >= PER_MINUTE) return false;
        SENT.addLast(now);
        return true;
    }

    private static synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client;
    }

    private static void fetch(Entry entry, String url) {
        entry.fetching = true;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).whenComplete((response, error) -> {
            long now = System.currentTimeMillis();
            try {
                if (error != null || response == null) {
                    fail(entry, now, error != null ? error.toString() : "no response", url);
                    return;
                }
                int status = response.statusCode();
                // An item SkyCofl does not track: nothing to show, nothing to retry soon.
                if (status == 404 || status == 400) {
                    answer(entry, Double.NaN, now);
                    return;
                }
                if (status != 200) {
                    fail(entry, now, "HTTP " + status, url);
                    return;
                }
                answer(entry, mean(response.body()), now);
            } catch (RuntimeException e) {
                fail(entry, now, e.toString(), url);
            } finally {
                entry.fetching = false;
            }
        });
    }

    private static void answer(Entry entry, double average, long now) {
        entry.average = average;
        entry.state = Double.isNaN(average) ? State.NONE : State.READY;
        entry.fetchedAt = now;
    }

    private static void fail(Entry entry, long now, String why, String url) {
        AlpakaAddons.LOGGER.warn("Could not load the average price from {} ({})", url, why);
        // A price already shown stays; only without one does the line say it failed.
        if (entry.fetchedAt == 0L) entry.state = State.FAILED;
        entry.retryAt = now + RETRY_MS;
    }

    /** The mean sale price from SkyCofl's summary, {@code {"min":..,"median":..,"mean":..,"volume":..}}; NaN without sales. */
    static double mean(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return Double.NaN;
        JsonObject o = root.getAsJsonObject();
        JsonElement volume = o.get("volume");
        if (volume != null && volume.isJsonPrimitive() && volume.getAsJsonPrimitive().isNumber() && volume.getAsDouble() <= 0) {
            return Double.NaN;
        }
        JsonElement mean = o.get("mean");
        if (mean == null || !mean.isJsonPrimitive() || !mean.getAsJsonPrimitive().isNumber()) return Double.NaN;
        double value = mean.getAsDouble();
        return value > 0 && Double.isFinite(value) ? value : Double.NaN;
    }
}
