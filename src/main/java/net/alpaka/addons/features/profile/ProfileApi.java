package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the dashboard's data comes from.
 *
 * Hypixel's own API wants a key every player would have to make and paste, so the profile comes
 * from Elite (api.elitebot.dev), which reads it from Hypixel and hands it out without one, already
 * worked out: levels, networth, pets. Elite also keeps a history of every profile's skill XP, and
 * Soopy (api.soopy.dev) one of networth, SkyBlock level, Catacombs, slayers and skills going back
 * years, which gives the graphs their past from the first look. The collection tiers and the
 * running Bingo event come from Hypixel's public resources, which need no key either.
 *
 * Answers are kept for a minute, so flipping pages costs nothing, and requests are spaced out to
 * stay well inside Elite's limit of 20 at a time. Only used while Network Features is on.
 */
public final class ProfileApi {
    private ProfileApi() {}

    static final String ELITE = "https://api.elitebot.dev";
    static final String SOOPY = "https://api.soopy.dev";
    static final String HYPIXEL_RESOURCES = "https://api.hypixel.net/v2/resources/skyblock";
    /** Where the dashboard's footer links to, as the source of the numbers. */
    public static final String ELITE_SITE = "https://elitebot.dev/@";

    private static final long FRESH_MS = 60_000L;
    /** The least time between two requests; a page load takes four. */
    private static final long SPACING_MS = 250L;

    /** Thrown into a future when Elite says a player or profile doesn't exist. */
    public static final class NotFound extends RuntimeException {
        NotFound(String message) {
            super(message, null, false, false);
        }
    }

    private record Cached(long at, CompletableFuture<JsonElement> future) {}

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static HttpClient client;
    private static long nextSlot = 0L;

    private static synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        return client;
    }

    /** The account with its profiles; {@code player} is a name or a UUID. */
    static CompletableFuture<JsonElement> account(String player) {
        return get(ELITE + "/account/" + encode(player), FRESH_MS);
    }

    /** One member of one profile, everything about them. */
    static CompletableFuture<JsonElement> member(String uuid, String profileId, boolean fresh) {
        return get(ELITE + "/profile/" + uuid + "/" + profileId, fresh ? 0 : FRESH_MS);
    }

    /** The networth with every item that makes it up; over a megabyte, so only asked for on its page. */
    static CompletableFuture<JsonElement> networth(String uuid, String profileId) {
        return get(ELITE + "/profile/" + uuid + "/" + profileId + "/networth", 5 * FRESH_MS);
    }

    /**
     * Elite's skill XP history: 29 days ending {@code daysBack} days ago at the latest, the most
     * it hands out per request.
     */
    static CompletableFuture<JsonElement> skillGraph(String uuid, String profileId, int daysBack) {
        String url = ELITE + "/graph/" + uuid + "/" + profileId + "/skills?days=29";
        if (daysBack > 0) {
            long from = System.currentTimeMillis() / 1000L - (long) (daysBack + 29) * 86_400L;
            url += "&from=" + from;
        }
        return get(url, 10 * FRESH_MS);
    }

    /**
     * One page of Soopy's stat history for a profile: about a hundred snapshots, newest first, page 0
     * the most recent. Soopy has kept these for years for every profile looked up through it. An
     * empty page is the end.
     */
    static CompletableFuture<JsonElement> soopyHistory(String uuid, String profileId, int page) {
        // The first page "realtime", as Soopy's own site asks for it: Soopy reads the profile now
        // and adds that as its newest snapshot, which is what lines its networth up with Elite's.
        String url = SOOPY + "/skyblock/stathistory/" + uuid + "/" + profileId + "/" + page + (page == 0 ? "/realtime" : "");
        return get(url, 10 * FRESH_MS);
    }

    /** The current or last Bingo event, with its start and end. */
    static CompletableFuture<JsonElement> bingo() {
        return get(HYPIXEL_RESOURCES + "/bingo", 60 * FRESH_MS);
    }

    /** A file of the NEU item repository, as text; see {@link PetIcons}. */
    static CompletableFuture<String> neuItem(String url) {
        // Not spaced out like Elite's requests: GitHub serves these files from its CDN, and a page
        // of pets asks for dozens at once.
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                    .GET()
                    .build();
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
                if (error != null || response == null) {
                    future.completeExceptionally(error != null ? error : new IllegalStateException("no response"));
                } else if (response.statusCode() == 404) {
                    future.completeExceptionally(new NotFound("No such item."));
                } else if (response.statusCode() / 100 != 2) {
                    future.completeExceptionally(new IllegalStateException("Answered " + response.statusCode()));
                } else {
                    future.complete(response.body());
                }
            });
        });
        return future;
    }

    /** Every collection with its tiers, from Hypixel's resources. Changes with game updates only. */
    static CompletableFuture<JsonElement> collections() {
        return get(HYPIXEL_RESOURCES + "/collections", 6 * 60 * FRESH_MS);
    }

    private static String encode(String part) {
        return URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static CompletableFuture<JsonElement> get(String url, long freshMs) {
        long now = System.currentTimeMillis();
        Cached cached = CACHE.get(url);
        if (cached != null && freshMs > 0 && now - cached.at() < freshMs && !cached.future().isCompletedExceptionally()) {
            return cached.future();
        }
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        CACHE.put(url, new Cached(now, future));
        long delay = reserveSlot(now);
        CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> send(url, future));
        return future;
    }

    private static synchronized long reserveSlot(long now) {
        long slot = Math.max(now, nextSlot);
        nextSlot = slot + SPACING_MS;
        return slot - now;
    }

    private static void send(String url, CompletableFuture<JsonElement> future) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .header("Accept", "application/json")
                .GET()
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null || response == null) {
                AlpakaAddons.LOGGER.debug("Profile request to {} failed: {}", url, error);
                CACHE.remove(url);
                future.completeExceptionally(error != null ? error : new IllegalStateException("no response"));
                return;
            }
            int status = response.statusCode();
            if (status == 404) {
                // Not worth asking again a moment later, so it stays cached like an answer.
                future.completeExceptionally(new NotFound(notFoundMessage(response.body())));
                return;
            }
            if (status < 200 || status >= 300) {
                AlpakaAddons.LOGGER.debug("Profile request to {} answered {}", url, status);
                CACHE.remove(url);
                future.completeExceptionally(new IllegalStateException(status == 429
                        ? "Elite is busy (too many requests). Try again in a minute."
                        : "Elite answered " + status + "."));
                return;
            }
            try {
                future.complete(JsonParser.parseString(response.body()));
            } catch (RuntimeException parse) {
                CACHE.remove(url);
                future.completeExceptionally(parse);
            }
        });
    }

    /** Elite's reason, such as "Minecraft account not found", or a plain one. */
    private static String notFoundMessage(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            var errors = Json.arr(root, "errors.generalErrors");
            if (!errors.isEmpty()) return errors.get(0).getAsString() + ".";
            String message = Json.str(root, "message");
            if (message != null && !message.isBlank() && !message.startsWith("One or more")) return message;
        } catch (RuntimeException ignored) {
            // Not JSON; the plain message below says enough.
        }
        return "Not found.";
    }

    /** Forgets everything, for the refresh button. */
    static void forget(String uuid) {
        CACHE.keySet().removeIf(url -> url.contains(uuid));
    }
}
