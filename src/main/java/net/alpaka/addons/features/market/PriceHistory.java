package net.alpaka.addons.features.market;

import com.google.gson.JsonArray;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An item's price history from SkyCofl (sky.coflnet.com), for the graph on a product's or an
 * auction's page.
 *
 * The Bazaar's history gives the instant buy and sell price over time, the Auction House's the
 * average and the lowest sale. Requests run in the background and are kept for a few minutes, so
 * flipping between pages and ranges costs nothing after the first look; they are also held to a
 * modest rate, well inside what SkyCofl allows. Only asked for while Network Features is on. A
 * request names the item and nothing about the player.
 */
public final class PriceHistory {
    private PriceHistory() {}

    private static final String BASE = "https://sky.coflnet.com/api";

    /** How far back the graph goes. */
    public enum Range {
        DAY("1D", "Day"), WEEK("1W", "Week"), MONTH("1M", "Month"), ALL("All", "All Time");

        public final String chip;
        public final String label;

        Range(String chip, String label) {
            this.chip = chip;
            this.label = label;
        }

        /** The names the config's choice shows. */
        public static final String[] LABELS = {"Day", "Week", "Month", "All Time"};

        public static Range of(int index) {
            Range[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, index))];
        }
    }

    /**
     * One moment of the history. For the Bazaar {@code first} is the instant buy price and
     * {@code second} the sell price; for the Auction House the average and the lowest sale. A price
     * that was not there is NaN.
     */
    public record Point(long timeMs, double first, double second) {}

    public enum State { LOADING, READY, EMPTY, FAILED }

    /** What there is to show right now; the points of an older answer while a newer one loads. */
    public record Result(State state, List<Point> points) {}

    private static final class Entry {
        volatile State state = State.LOADING;
        volatile List<Point> points = List.of();
        volatile long fetchedAt = 0L;
        volatile long retryAt = 0L;
        volatile boolean fetching = false;
        volatile boolean everAnswered = false;
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private static HttpClient client;

    private static synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client;
    }

    /**
     * The history for this item and range, asking SkyCofl in the background if it is not known yet
     * or has gone stale. Never waits.
     */
    public static Result get(MarketKind kind, String tag, Range range) {
        String key = kind + "|" + tag + "|" + range;
        Entry entry = CACHE.computeIfAbsent(key, k -> new Entry());
        long now = System.currentTimeMillis();
        boolean due = !entry.everAnswered
                ? now >= entry.retryAt
                : entry.state == State.FAILED ? now >= entry.retryAt : now - entry.fetchedAt > freshFor(range);
        if (due && !entry.fetching && RateLimit.take(now)) fetch(entry, url(kind, tag, range, now), kind);
        return new Result(entry.state, entry.points);
    }

    /** Asks again right away for a history that failed, on the player's click. */
    public static void retry(MarketKind kind, String tag, Range range) {
        Entry entry = CACHE.get(kind + "|" + tag + "|" + range);
        if (entry != null && entry.state == State.FAILED) entry.retryAt = 0L;
    }

    /** How long an answer is shown before it is asked for again: prices move faster than a month does. */
    private static long freshFor(Range range) {
        return range == Range.DAY ? 5 * 60_000L : 15 * 60_000L;
    }

    private static void fetch(Entry entry, String url, MarketKind kind) {
        entry.fetching = true;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                long now = System.currentTimeMillis();
                if (error != null || response == null) {
                    fail(entry, now, error != null ? error.toString() : "no response", url);
                    return;
                }
                int status = response.statusCode();
                if (status == 404 || status == 400) {
                    // An item SkyCofl does not track: nothing to show, nothing to retry soon.
                    answer(entry, List.of(), now);
                    return;
                }
                if (status != 200) {
                    fail(entry, now, "HTTP " + status, url);
                    return;
                }
                List<Point> points = kind == MarketKind.BAZAAR ? parseBazaar(response.body()) : parseAuction(response.body());
                answer(entry, points, now);
            } catch (RuntimeException e) {
                fail(entry, System.currentTimeMillis(), e.toString(), url);
            } finally {
                entry.fetching = false;
            }
        });
    }

    private static void answer(Entry entry, List<Point> points, long now) {
        entry.points = points;
        entry.state = points.isEmpty() ? State.EMPTY : State.READY;
        entry.fetchedAt = now;
        entry.everAnswered = true;
    }

    private static void fail(Entry entry, long now, String why, String url) {
        AlpakaAddons.LOGGER.warn("Could not load the price history from {} ({})", url, why);
        // An older answer stays on screen; only without one does the graph say it failed.
        if (!entry.everAnswered) entry.state = State.FAILED;
        else entry.fetchedAt = now;
        entry.retryAt = now + 60_000L;
    }

    // ---- Requests -------------------------------------------------------------------------------

    /** The hour the request starts from, so a month asked for twice in an hour is the same request. */
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00:00");

    static String url(MarketKind kind, String tag, Range range, long nowMs) {
        String t = URLEncoder.encode(tag, StandardCharsets.UTF_8);
        if (kind == MarketKind.BAZAAR) {
            return switch (range) {
                case DAY -> BASE + "/bazaar/" + t + "/history/day";
                case WEEK -> BASE + "/bazaar/" + t + "/history/week";
                case MONTH -> {
                    LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
                    yield BASE + "/bazaar/" + t + "/history?start=" + HOUR.format(end.minusDays(30)) + "&end=" + HOUR.format(end);
                }
                case ALL -> BASE + "/bazaar/" + t + "/history";
            };
        }
        return BASE + "/item/price/" + t + "/history/" + switch (range) {
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
            case ALL -> "full";
        };
    }

    /** The page on SkyCofl's site that the graph's data comes from, for its attribution link. */
    public static String pageUrl(String tag) {
        return "https://sky.coflnet.com/item/" + URLEncoder.encode(tag, StandardCharsets.UTF_8);
    }

    // ---- Answers --------------------------------------------------------------------------------

    /** The Bazaar's history: the buy and sell price at each moment, oldest first. */
    static List<Point> parseBazaar(String json) {
        List<Point> points = new ArrayList<>();
        for (JsonElement element : array(json)) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            long time = time(o, "timestamp");
            double buy = number(o, "buy");
            double sell = number(o, "sell");
            if (time < 0 || (Double.isNaN(buy) && Double.isNaN(sell))) continue;
            points.add(new Point(time, buy, sell));
        }
        points.sort(Comparator.comparingLong(Point::timeMs));
        return points;
    }

    /** The Auction House's history: the average and the lowest sale at each moment, oldest first. */
    static List<Point> parseAuction(String json) {
        List<Point> points = new ArrayList<>();
        for (JsonElement element : array(json)) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            long time = time(o, "time");
            double avg = number(o, "avg");
            double min = number(o, "min");
            if (time < 0 || Double.isNaN(avg)) continue;
            points.add(new Point(time, avg, min));
        }
        points.sort(Comparator.comparingLong(Point::timeMs));
        return points;
    }

    private static JsonArray array(String json) {
        JsonElement root = JsonParser.parseString(json);
        return root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
    }

    /** A positive price, or NaN for a missing, zero or broken one - no orders, no sales. */
    private static double number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return Double.NaN;
        double v = e.getAsDouble();
        return v > 0 && Double.isFinite(v) ? v : Double.NaN;
    }

    /** SkyCofl's times are UTC, mostly written without a zone. */
    private static long time(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return -1;
        String text = e.getAsString();
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            // No zone written: UTC.
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    /** At most 20 requests a minute, whatever the player clicks: SkyCofl allows far more. */
    private static final class RateLimit {
        private static final int PER_MINUTE = 20;
        private static final ArrayDeque<Long> SENT = new ArrayDeque<>();

        static synchronized boolean take(long now) {
            while (!SENT.isEmpty() && now - SENT.peekFirst() > 60_000L) SENT.pollFirst();
            if (SENT.size() >= PER_MINUTE) return false;
            SENT.addLast(now);
            return true;
        }
    }
}
