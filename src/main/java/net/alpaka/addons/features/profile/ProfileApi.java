package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the dashboard asks other sites for, beside the profile itself.
 *
 * The profile comes from Hypixel through the AlpakaAddons API ({@link AlpakaApi}). Here is the
 * rest: the history other sites kept - Soopy (api.soopy.dev) one of networth, SkyBlock level,
 * Catacombs, slayers and skills going back years, SkyKings one a day for the players it tracks -
 * which gives the graphs their past from the first look; the collection tiers and the running
 * Bingo event from Hypixel's public resources, which need no key; and files of the NEU repository.
 *
 * Answers are kept for a while, so flipping pages costs nothing, and requests are spaced out. Only
 * used while Network Features is on: every request of the dashboard checks {@link #allowed}, and
 * with the switch off each one fails at once, without anything sent.
 */
public final class ProfileApi {
    private ProfileApi() {}

    static final String SOOPY = "https://api.soopy.dev";
    static final String SKYKINGS = "https://api.skykings.net";
    static final String HYPIXEL_RESOURCES = "https://api.hypixel.net/v2/resources/skyblock";
    private static final long FRESH_MS = 60_000L;
    /** The least time between two requests. */
    private static final long SPACING_MS = 250L;

    /** Thrown into a future when a player, a profile or a file doesn't exist. */
    public static final class NotFound extends RuntimeException {
        NotFound(String message) {
            super(message, null, false, false);
        }
    }

    /** Thrown into a future, without asking anyone, while Network Features is off. */
    public static final class NetworkOff extends RuntimeException {
        NetworkOff() {
            super("Network Features is off.", null, false, false);
        }
    }

    /** Whether the player allows the mod to go online; the README promises nothing is sent otherwise. */
    static boolean allowed() {
        return AlpakaConfig.instance.allowApiCalls;
    }

    /** An answer and how long it may be handed out again, after which it is dropped. */
    private record Cached(long at, long keepMs, CompletableFuture<JsonElement> future) {}

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static HttpClient client;
    private static HttpClient keyedClient;
    private static long nextSlot = 0L;

    private static synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        return client;
    }

    /**
     * For requests that carry the player's own key. Follows no redirect, since the client would send
     * the key along to wherever it points, even another host; a redirect counts as a failed request.
     */
    private static synchronized HttpClient keyedClient() {
        if (keyedClient == null) keyedClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return keyedClient;
    }

    /**
     * Keeps an answer, first dropping every one past its time. Without that the cache would grow with
     * every player looked at, and SkyKings' history alone is megabytes. A dropped request still in
     * flight completes for whoever already holds it.
     */
    private static void keep(String url, Cached cached) {
        long now = cached.at();
        CACHE.values().removeIf(c -> now - c.at() >= c.keepMs());
        CACHE.put(url, cached);
    }

    /** How long an answer asked for with {@code freshMs} can still be handed out: at least a minute. */
    private static long keepMs(long freshMs) {
        return Math.max(freshMs, FRESH_MS);
    }

    /**
     * One page of Soopy's stat history for a profile: about a hundred snapshots, newest first, page 0
     * the most recent. Soopy has kept these for years for every profile looked up through it. An
     * empty page is the end.
     */
    static CompletableFuture<JsonElement> soopyHistory(String uuid, String profileId, int page) {
        // The first page "realtime", as Soopy's own site asks for it: Soopy reads the profile now
        // and adds that as its newest snapshot, which is what lines its networth up with the dashboard's.
        String url = SOOPY + "/skyblock/stathistory/" + uuid + "/" + profileId + "/" + page + (page == 0 ? "/realtime" : "");
        return get(url, 10 * FRESH_MS);
    }

    /**
     * A player's history at SkyKings, which records the players of every guild on its leaderboard
     * about once a day: skills, slayers, Catacombs, networth and more, per profile. Needs the
     * player's own key; nearly three megabytes, so kept for half an hour.
     */
    static CompletableFuture<JsonElement> skykingsUser(String uuid, String key) {
        if (!allowed()) return CompletableFuture.failedFuture(new NetworkOff());
        String url = SKYKINGS + "/leaderboard/user?uuid=" + uuid;
        long now = System.currentTimeMillis();
        Cached cached = CACHE.get(url);
        if (cached != null && now - cached.at() < 30 * FRESH_MS && !cached.future().isCompletedExceptionally()) {
            return cached.future();
        }
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        keep(url, new Cached(now, 30 * FRESH_MS, future));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .header("Authorization", key)
                .GET()
                .build();
        keyedClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).whenComplete((response, error) -> {
            if (error != null || response == null) {
                CACHE.remove(url);
                future.completeExceptionally(error != null ? error : new IllegalStateException("no response"));
                return;
            }
            int status = response.statusCode();
            if (status / 100 == 3) {
                CACHE.remove(url);
                future.completeExceptionally(new IllegalStateException("SkyKings answered with a redirect, which isn't followed with your key."));
            } else if (status == 401 || status == 403) {
                CACHE.remove(url);
                future.completeExceptionally(new IllegalStateException("SkyKings didn't accept the API key."));
            } else if (status == 404) {
                future.completeExceptionally(new NotFound("SkyKings doesn't track this player."));
            } else if (status == 429) {
                CACHE.remove(url);
                future.completeExceptionally(new IllegalStateException("SkyKings' request limit is reached; try again in a few minutes."));
            } else if (status / 100 != 2) {
                CACHE.remove(url);
                future.completeExceptionally(new IllegalStateException("SkyKings answered " + status + "."));
            } else {
                try {
                    future.complete(JsonParser.parseString(response.body()));
                } catch (RuntimeException parse) {
                    CACHE.remove(url);
                    future.completeExceptionally(parse);
                }
            }
        });
        return future;
    }

    /** The current or last Bingo event, with its start and end. */
    static CompletableFuture<JsonElement> bingo() {
        return get(HYPIXEL_RESOURCES + "/bingo", 60 * FRESH_MS);
    }

    /** A file of the NEU item repository, as text; see {@link PetIcons}. */
    static CompletableFuture<String> neuItem(String url) {
        if (!allowed()) return CompletableFuture.failedFuture(new NetworkOff());
        // Not spaced out like the other requests: GitHub serves these files from its CDN, and a
        // page of pets asks for dozens at once.
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                    .GET()
                    .build();
            client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).whenComplete((response, error) -> {
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

    private static CompletableFuture<JsonElement> get(String url, long freshMs) {
        return get(url, freshMs, SPACING_MS);
    }

    private static CompletableFuture<JsonElement> get(String url, long freshMs, long spacingMs) {
        // Before the cache too, and nothing is kept, so the switch turned on later loads afresh.
        if (!allowed()) return CompletableFuture.failedFuture(new NetworkOff());
        long now = System.currentTimeMillis();
        Cached cached = CACHE.get(url);
        if (cached != null && freshMs > 0 && now - cached.at() < freshMs && !cached.future().isCompletedExceptionally()) {
            return cached.future();
        }
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        keep(url, new Cached(now, keepMs(freshMs), future));
        long delay = reserveSlot(now, spacingMs);
        CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> send(url, future));
        return future;
    }

    private static synchronized long reserveSlot(long now, long spacingMs) {
        long slot = Math.max(now, nextSlot);
        nextSlot = slot + spacingMs;
        return slot - now;
    }

    private static void send(String url, CompletableFuture<JsonElement> future) {
        // Requests were spaced out; Network Features switched off meanwhile stops the ones not sent yet.
        if (!allowed()) {
            CACHE.remove(url);
            future.completeExceptionally(new NetworkOff());
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .header("Accept", "application/json")
                .GET()
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).whenComplete((response, error) -> {
            if (error != null || response == null) {
                AlpakaAddons.LOGGER.debug("Profile request to {} failed: {}", url, error);
                CACHE.remove(url);
                future.completeExceptionally(error != null ? error : new IllegalStateException("no response"));
                return;
            }
            int status = response.statusCode();
            if (status == 404) {
                // Not worth asking again a moment later, so it stays cached like an answer.
                future.completeExceptionally(new NotFound("Not found."));
                return;
            }
            if (status < 200 || status >= 300) {
                AlpakaAddons.LOGGER.debug("Profile request to {} answered {}", url, status);
                CACHE.remove(url);
                future.completeExceptionally(new IllegalStateException(status == 429
                        ? "Too many requests. Try again in a minute."
                        : "Answered " + status + "."));
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
}
