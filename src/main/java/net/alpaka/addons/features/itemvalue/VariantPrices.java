package net.alpaka.addons.features.itemvalue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.utils.ModVersion;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the rare forms of items cost ({@link ItemValue#VARIANTS}): the median of what they sold for
 * on the Auction House over the last month, from SkyCofl's price history filtered by the attribute
 * that marks them. The lowest BIN list has one price per id, the ordinary item's.
 *
 * Rare forms sell seldom, and a month can pass without one; the last price found is kept on disk
 * and used until another sale comes. One small request per form, asked for again after an hour.
 */
final class VariantPrices {
    private VariantPrices() {}

    private static final String HISTORY_URL = "https://sky.coflnet.com/api/item/price/%s/history/month?%s";
    private static final long FRESH_MS = 60 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;

    private static final class Entry {
        final ItemValue.Variant variant;
        volatile double price = Double.NaN;
        /** Whether an answer came, or a price from disk: until then the price is still on its way. */
        volatile boolean settled;
        volatile long fetchedAt;
        volatile long retryAt;
        volatile boolean fetching;

        Entry(ItemValue.Variant variant) {
            this.variant = variant;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        for (ItemValue.Variant v : ItemValue.VARIANTS) ENTRIES.put(v.key(), new Entry(v));
    }

    private static boolean diskRead;

    /** The price kept under a variant's key; null for any other id, or while none is known. */
    static Double price(String key) {
        Entry entry = ENTRIES.get(key);
        return entry == null || Double.isNaN(entry.price) ? null : entry.price;
    }

    /** Whether some variant has neither a price from disk nor an answer yet. */
    static boolean loading() {
        for (Entry entry : ENTRIES.values()) if (!entry.settled) return true;
        return false;
    }

    static void retry() {
        for (Entry entry : ENTRIES.values()) entry.retryAt = 0L;
    }

    /** Asks for whichever price is missing or older than an hour, in the background; {@code changed} runs on news. */
    static void refresh(HttpClient client, Runnable changed) {
        readDisk();
        long now = System.currentTimeMillis();
        for (Entry entry : ENTRIES.values()) {
            synchronized (entry) {
                if (entry.fetching || now < entry.retryAt || (entry.fetchedAt > 0 && now - entry.fetchedAt < FRESH_MS)) continue;
                entry.fetching = true;
            }
            fetch(client, entry, changed);
        }
    }

    private static void fetch(HttpClient client, Entry entry, Runnable changed) {
        ItemValue.Variant v = entry.variant;
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(HISTORY_URL, v.id(), v.coflFilter())))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error != null || response == null || response.statusCode() != 200) {
                    String why = error != null ? error.toString() : "HTTP " + (response == null ? "?" : response.statusCode());
                    AlpakaAddons.LOGGER.warn("Could not load the sales of {} ({})", v.key(), why);
                    entry.retryAt = System.currentTimeMillis() + RETRY_MS;
                    return;
                }
                Double median = parseHistory(new StringReader(response.body()));
                entry.fetchedAt = System.currentTimeMillis();
                // No sale this month: the last price found stays.
                if (median != null) {
                    entry.price = median;
                    writeDisk();
                }
            } catch (IOException | RuntimeException e) {
                AlpakaAddons.LOGGER.warn("Could not read the sales of {}", v.key(), e);
                entry.retryAt = System.currentTimeMillis() + RETRY_MS;
            } finally {
                entry.settled = true;
                entry.fetching = false;
                changed.run();
            }
        });
    }

    /**
     * The median sale in SkyCofl's history, which comes as one row per day with the average price
     * and the number of sales; null for a month without any.
     */
    static Double parseHistory(Reader reader) throws IOException {
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (RuntimeException e) {
            throw new IOException("not JSON", e);
        }
        if (!root.isJsonArray()) return null;
        record Day(double avg, int sales) {}
        List<Day> days = new ArrayList<>();
        int total = 0;
        for (JsonElement row : root.getAsJsonArray()) {
            if (!row.isJsonObject()) continue;
            JsonObject o = row.getAsJsonObject();
            double avg = o.has("avg") && o.get("avg").isJsonPrimitive() ? o.get("avg").getAsDouble() : Double.NaN;
            int sales = o.has("volume") && o.get("volume").isJsonPrimitive() ? (int) o.get("volume").getAsDouble() : 0;
            if (!(avg > 0) || !Double.isFinite(avg) || sales <= 0) continue;
            days.add(new Day(avg, sales));
            total += sales;
        }
        if (total == 0) return null;
        days.sort((a, b) -> Double.compare(a.avg(), b.avg()));
        // The sale halfway up when every sale is counted at its day's average.
        int half = (total + 1) / 2;
        int seen = 0;
        for (Day day : days) {
            seen += day.sales();
            if (seen >= half) return day.avg();
        }
        return days.getLast().avg();
    }

    // ---- Kept on disk ---------------------------------------------------------------------------

    private static File file() {
        return FabricLoader.getInstance().getConfigDir().resolve("alpaka-variant-prices.json").toFile();
    }

    private static synchronized void readDisk() {
        if (diskRead) return;
        diskRead = true;
        File file = file();
        if (!file.isFile()) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                Entry entry = ENTRIES.get(e.getKey());
                if (entry == null || !e.getValue().isJsonPrimitive()) continue;
                double price = e.getValue().getAsDouble();
                if (price > 0 && Double.isFinite(price)) {
                    entry.price = price;
                    entry.settled = true;
                }
            }
        } catch (IOException | RuntimeException e) {
            AlpakaAddons.LOGGER.warn("Could not read {}", file, e);
        }
    }

    private static synchronized void writeDisk() {
        JsonObject root = new JsonObject();
        for (Entry entry : ENTRIES.values()) if (!Double.isNaN(entry.price)) root.addProperty(entry.variant.key(), entry.price);
        File file = file();
        try {
            Files.writeString(file.toPath(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
