package net.alpaka.addons.features.market;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.itemvalue.ItemPrices;
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
import java.util.zip.GZIPInputStream;

/**
 * Every item SkyCofl knows, by its market id, with its name: the ones Hypixel's own item list leaves
 * out as well, such as runes ("Pestilence Rune I" is RUNE_ZOMBIE_SLAYER), dyes and skins. Fetched
 * once a session (about 0.6 MB, less packed) and only with Network Features on; the request names
 * nothing about the player.
 */
public final class ItemNames {
    private ItemNames() {}

    private static final String URL = "https://sky.coflnet.com/api/items";
    private static final long RETRY_MS = 60_000L;

    private static volatile Map<String, String> names;
    private static volatile boolean fetching;
    private static volatile boolean failed;
    private static volatile long retryAt;
    private static HttpClient client;

    /** Every item's name by its id; null until the list is in. */
    public static Map<String, String> all() {
        return names;
    }

    /** Whether the list could not be loaded, so nothing more will come for now. */
    public static boolean failed() {
        return failed;
    }

    /** Asks for the list if it is not there yet, in the background. Never waits. */
    public static void refresh() {
        if (!AlpakaConfig.instance.allowApiCalls || names != null) return;
        synchronized (ItemNames.class) {
            if (fetching || System.currentTimeMillis() < retryAt) return;
            fetching = true;
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((response, error) -> {
            try {
                if (error != null || response == null || response.statusCode() != 200) {
                    if (response != null) response.body().close();
                    fail(error != null ? error.toString() : "HTTP " + (response == null ? "?" : response.statusCode()));
                    return;
                }
                boolean gzip = response.headers().firstValue("Content-Encoding").map(e -> e.contains("gzip")).orElse(false);
                InputStream stream = response.body();
                ItemPrices.closeLater(stream);
                Map<String, String> parsed;
                try (InputStream raw = stream;
                     InputStream body = gzip ? new GZIPInputStream(raw) : raw;
                     Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                    parsed = parse(reader);
                }
                if (parsed.isEmpty()) {
                    fail("an empty answer");
                    return;
                }
                names = parsed;
                failed = false;
                fetching = false;
            } catch (IOException | RuntimeException e) {
                fail(e.toString());
            }
        });
    }

    private static void fail(String why) {
        AlpakaAddons.LOGGER.warn("Could not load SkyCofl's item names from {} ({})", URL, why);
        failed = true;
        retryAt = System.currentTimeMillis() + RETRY_MS;
        fetching = false;
    }

    /** SkyCofl's answer, [{"name": "Necron Dye", "tag": "DYE_NECRON", ...}, ...], as id to name. */
    static Map<String, String> parse(Reader reader) throws IOException {
        Map<String, String> out = new HashMap<>(8192);
        JsonReader json = new JsonReader(reader);
        json.beginArray();
        while (json.hasNext()) {
            String name = null;
            String tag = null;
            json.beginObject();
            while (json.hasNext()) {
                String field = json.nextName();
                if (json.peek() == JsonToken.STRING && field.equals("name")) name = json.nextString();
                else if (json.peek() == JsonToken.STRING && field.equals("tag")) tag = json.nextString();
                else json.skipValue();
            }
            json.endObject();
            // Some ids come without a name, as the text "null".
            if (tag != null && name != null && !name.isBlank() && !name.equals("null")) out.put(tag, name);
        }
        json.endArray();
        return out;
    }
}
