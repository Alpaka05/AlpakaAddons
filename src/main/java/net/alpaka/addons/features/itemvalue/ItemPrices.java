package net.alpaka.addons.features.itemvalue;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.market.MarketKind;
import net.alpaka.addons.utils.ModVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * What items cost right now, for the Item Value breakdown: the Bazaar's instant buy and sell price of
 * every product, from Hypixel, and the lowest BIN of everything on the Auction House, from SkyCofl.
 *
 * Each is one request for the whole list, kept for a few minutes and only asked for again while a
 * breakdown is on screen, so looking at item after item costs nothing more. Only fetched while
 * Network Features is on; a request names nothing about the player.
 */
public final class ItemPrices {
    private ItemPrices() {}

    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String LOWEST_BIN_URL = "https://sky.coflnet.com/api/prices/neu";

    /** How long a list is used before it is asked for again: the Bazaar moves by the minute. */
    private static final long FRESH_MS = 3 * 60_000L;
    private static final long RETRY_MS = 60_000L;

    /** The Bazaar prices a part can count at, as the config offers them: index 0 buys, 1 sells. */
    public static final String[] SIDES = {"Instant Buy", "Instant Sell"};

    /** What one item costs, and on which market. */
    public record Quote(double price, MarketKind market) {}

    /** A Bazaar product's prices, NaN where nobody is selling or buying. */
    record Product(double buy, double sell) {}

    /** One of the two lists: its contents once loaded, and when to ask again. */
    private static final class Source<T> {
        final String url;
        final String what;
        volatile T data = null;
        volatile long fetchedAt = 0L;
        volatile long retryAt = 0L;
        volatile boolean fetching = false;
        volatile boolean failed = false;

        Source(String url, String what) {
            this.url = url;
            this.what = what;
        }
    }

    private static final Source<Map<String, Product>> BAZAAR = new Source<>(BAZAAR_URL, "the Bazaar's prices");
    private static final Source<Map<String, Double>> LOWEST_BINS = new Source<>(LOWEST_BIN_URL, "the lowest BINs");

    /** Counts up whenever a list changes, so a breakdown knows to work itself out again. */
    private static final AtomicInteger VERSION = new AtomicInteger();

    private static HttpClient client;

    private static synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client;
    }

    /** Asks for whichever list is missing or stale, in the background. Never waits. */
    public static void refresh() {
        if (!AlpakaConfig.instance.allowApiCalls) return;
        long now = System.currentTimeMillis();
        maybeFetch(BAZAAR, now, ItemPrices::parseBazaar);
        maybeFetch(LOWEST_BINS, now, ItemPrices::parseLowestBins);
    }

    /** Asks again right away for a list that failed, on the player's click. */
    public static void retry() {
        BAZAAR.retryAt = 0L;
        LOWEST_BINS.retryAt = 0L;
        refresh();
    }

    /**
     * What the item costs now: its Bazaar price if it is a product there, at the side the settings
     * pick, else its lowest BIN. Null while the lists load or for an item neither market has.
     */
    public static Quote quote(String id) {
        return quote(id, BAZAAR.data, LOWEST_BINS.data, AlpakaConfig.instance.itemValueBazaarPrice == 1);
    }

    static Quote quote(String id, Map<String, Product> bazaar, Map<String, Double> bins, boolean sellSide) {
        if (id == null) return null;
        Product product = bazaar == null ? null : bazaar.get(id);
        if (product != null) {
            // The other side stands in where one is empty: nobody selling a product leaves no buy price.
            double first = sellSide ? product.sell() : product.buy();
            double second = sellSide ? product.buy() : product.sell();
            double price = !Double.isNaN(first) ? first : second;
            if (!Double.isNaN(price)) return new Quote(price, MarketKind.BAZAAR);
        }
        Double bin = bins == null ? null : bins.get(id);
        return bin == null ? null : new Quote(bin, MarketKind.AUCTION_HOUSE);
    }

    /** Whether there is nothing to show yet because a list is still on its way. */
    public static boolean loading() {
        return (BAZAAR.data == null && !BAZAAR.failed) || (LOWEST_BINS.data == null && !LOWEST_BINS.failed);
    }

    /** Whether both lists failed to come and there is nothing to show at all. */
    public static boolean failed() {
        return BAZAAR.data == null && BAZAAR.failed && LOWEST_BINS.data == null && LOWEST_BINS.failed;
    }

    /** When the older of the two lists was fetched; 0 before both are there. */
    public static long updatedAt() {
        if (BAZAAR.data == null || LOWEST_BINS.data == null) return 0L;
        return Math.min(BAZAAR.fetchedAt, LOWEST_BINS.fetchedAt);
    }

    public static int version() {
        return VERSION.get();
    }

    // ---- Requests -------------------------------------------------------------------------------

    private interface Parser<T> {
        T parse(Reader reader) throws IOException;
    }

    private static <T> void maybeFetch(Source<T> source, long now, Parser<T> parser) {
        synchronized (source) {
            boolean due = source.data == null ? now >= source.retryAt : now - source.fetchedAt > FRESH_MS && now >= source.retryAt;
            if (!due || source.fetching) return;
            source.fetching = true;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(source.url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                // The Bazaar's answer is about 3.5 MB as text, under 0.5 MB packed.
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((response, error) -> {
            try {
                if (error != null || response == null || response.statusCode() != 200) {
                    fail(source, error != null ? error.toString() : "HTTP " + (response == null ? "?" : response.statusCode()));
                    if (response != null) response.body().close();
                    return;
                }
                boolean gzip = response.headers().firstValue("Content-Encoding").map(e -> e.contains("gzip")).orElse(false);
                T data;
                try (InputStream raw = response.body();
                     InputStream body = gzip ? new GZIPInputStream(raw) : raw;
                     Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                    data = parser.parse(reader);
                }
                if (data == null || (data instanceof Map<?, ?> map && map.isEmpty())) {
                    fail(source, "an empty answer");
                    return;
                }
                source.data = data;
                source.fetchedAt = System.currentTimeMillis();
                source.failed = false;
                VERSION.incrementAndGet();
            } catch (IOException | RuntimeException e) {
                fail(source, e.toString());
            } finally {
                source.fetching = false;
            }
        });
    }

    private static void fail(Source<?> source, String why) {
        AlpakaAddons.LOGGER.warn("Could not load {} from {} ({})", source.what, source.url, why);
        // An older list stays in use; only without one is there nothing to show.
        if (source.data == null) source.failed = true;
        source.retryAt = System.currentTimeMillis() + RETRY_MS;
        VERSION.incrementAndGet();
    }

    // ---- Answers --------------------------------------------------------------------------------

    /**
     * Hypixel's Bazaar: every product's instant buy and sell price, from its quick status. The order
     * books beside it, most of the answer, are skipped without being read into memory.
     */
    static Map<String, Product> parseBazaar(Reader reader) throws IOException {
        Map<String, Product> out = new HashMap<>(4096);
        JsonReader json = new JsonReader(reader);
        json.beginObject();
        while (json.hasNext()) {
            if (!json.nextName().equals("products") || json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue();
                continue;
            }
            json.beginObject();
            while (json.hasNext()) {
                String id = json.nextName();
                Product product = product(json);
                if (product != null) out.put(id, product);
            }
            json.endObject();
        }
        json.endObject();
        return out;
    }

    private static Product product(JsonReader json) throws IOException {
        if (json.peek() != JsonToken.BEGIN_OBJECT) {
            json.skipValue();
            return null;
        }
        double buy = Double.NaN;
        double sell = Double.NaN;
        json.beginObject();
        while (json.hasNext()) {
            if (!json.nextName().equals("quick_status") || json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue();
                continue;
            }
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if ((key.equals("buyPrice") || key.equals("sellPrice")) && json.peek() == JsonToken.NUMBER) {
                    double value = positive(json.nextDouble());
                    if (key.equals("buyPrice")) buy = value;
                    else sell = value;
                } else {
                    json.skipValue();
                }
            }
            json.endObject();
        }
        json.endObject();
        return new Product(buy, sell);
    }

    /** SkyCofl's lowest BINs, in NEU's form: one number per item id, pets as "LION;4". */
    static Map<String, Double> parseLowestBins(Reader reader) throws IOException {
        Map<String, Double> out = new HashMap<>(4096);
        JsonReader json = new JsonReader(reader);
        json.beginObject();
        while (json.hasNext()) {
            String id = json.nextName();
            if (json.peek() != JsonToken.NUMBER) {
                json.skipValue();
                continue;
            }
            double price = positive(json.nextDouble());
            if (!Double.isNaN(price)) out.put(id, price);
        }
        json.endObject();
        return out;
    }

    /** A price, or NaN for none: zero is what Hypixel writes for a side without orders. */
    private static double positive(double value) {
        return value > 0 && Double.isFinite(value) ? value : Double.NaN;
    }
}
