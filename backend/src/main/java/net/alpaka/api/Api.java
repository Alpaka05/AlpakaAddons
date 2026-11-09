package net.alpaka.api;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * What the API answers, apart from the HTTP plumbing so it can be tested on its own.
 *
 * The mod logs in by proving its Minecraft account to Mojang, as joining a server does: it asks
 * this server for a challenge, tells Mojang's session server it joins a server with that id, and
 * this side asks Mojang whether that player did ({@code hasJoined}). No password or token of the
 * player ever reaches this server, and a challenge works once only (see {@link Challenges}).
 *
 * Logged in, a player may ask for what the Profile Dashboard shows of another player
 * ({@code /v1/dashboard/<uuid>}). It is fetched from Hypixel with the server's key only when asked
 * for, cut down to the dashboard's fields ({@link Dashboard}), cached, and limited per player, so
 * the key's budget can't be spent by one of them. It is not a copy of Hypixel's API: there is no
 * way to ask for Hypixel's answer as it is. Nothing is fetched on its own and nothing is kept about
 * who asked for what.
 */
final class Api {
    record Request(String method, String path, Map<String, String> headers, String ip) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    record Response(int status, String contentType, String body, Map<String, String> headers) {
        static Response json(int status, String body) {
            return new Response(status, "application/json", body, Map.of());
        }

        static Response error(int status, String cause) {
            return json(status, "{\"success\":false,\"cause\":\"" + cause + "\"}");
        }

        Response with(String name, String value) {
            Map<String, String> more = new java.util.HashMap<>(headers);
            more.put(name, value);
            return new Response(status, contentType, body, Map.copyOf(more));
        }
    }

    /** Mojang's {@code hasJoined}: the player's UUID without dashes if they did, else null. */
    interface SessionCheck {
        String hasJoined(String username, String serverId) throws IOException;
    }

    /** One request to Hypixel's API, with how much of the key's budget is left after it. */
    interface Upstream {
        record Result(int status, String body, int remaining, long resetSeconds) {}

        Result get(String pathAndQuery) throws IOException;
    }

    /**
     * How long another player's answer is kept: Hypixel suggests asking for the same player no more
     * than once an hour.
     */
    static final long MAX_AGE_MS = 60 * 60_000L;
    /** A player's own profile may be fresher, since they just changed it in the game. */
    static final long OWN_MAX_AGE_MS = 10 * 60_000L;
    /** About 48 MB of answers; the service runs with a 512 MB heap. */
    static final long CACHE_CHARS = 24_000_000L;

    /** Below this much of the key's budget, players wait for it to refill rather than use it up. */
    static final int KEY_RESERVE = 15;

    private final SessionCheck mojang;
    private final Upstream hypixel;
    private final LongSupplier clock;
    private final Sessions sessions;
    private final Challenges challenges;
    private final ResponseCache cache;
    private final RateLimit logins;
    private final RateLimit requests;
    private final RateLimit fetches;
    private final Map<String, CompletableFuture<Fetched>> inFlight = new ConcurrentHashMap<>();
    private volatile int keyRemaining = Integer.MAX_VALUE;
    private volatile long keyResetAt = 0L;

    Api(SessionCheck mojang, Upstream hypixel, LongSupplier clock) {
        this.mojang = mojang;
        this.hypixel = hypixel;
        this.clock = clock;
        this.sessions = new Sessions(clock);
        this.challenges = new Challenges(clock);
        this.cache = new ResponseCache(CACHE_CHARS, clock);
        // Ten challenges, so ten logins, per address in ten minutes; a player logs in once per game start.
        this.logins = new RateLimit(10, 10 * 60_000L, clock);
        // Opening a profile takes one request; sixty in five minutes is a busy player.
        this.requests = new RateLimit(60, 5 * 60_000L, clock);
        // Of those, at most twenty may go to Hypixel, a fifteenth of the key's 300.
        this.fetches = new RateLimit(20, 5 * 60_000L, clock);
    }

    Response handle(Request r) {
        if (!r.method().equals("GET")) return Response.error(405, "Only GET");
        String path = r.path();
        if (path.equals("/")) {
            return new Response(200, "text/plain; charset=utf-8",
                    "AlpakaAddons API, the backend of the mod's Profile Dashboard. Not a public API.\n"
                            + "Not affiliated with Hypixel. https://github.com/Alpaka05/AlpakaAddons\n", Map.of());
        }
        if (path.equals("/health")) return new Response(200, "text/plain; charset=utf-8", "ok\n", Map.of());
        if (path.equals("/v1/auth/challenge")) return challenge(r);
        if (path.equals("/v1/auth")) return login(r);
        if (path.startsWith("/v1/dashboard/")) return dashboard(r, path.substring("/v1/dashboard/".length()));
        return Response.error(404, "Not found");
    }

    private Response challenge(Request r) {
        String ip = r.ip() == null ? "?" : r.ip();
        if (!logins.tryUse(ip)) {
            return Response.error(429, "Too many logins").with("Retry-After", String.valueOf(logins.retryAfterSeconds(ip)));
        }
        String id = challenges.issue();
        if (id == null) return Response.error(503, "Busy, try again shortly").with("Retry-After", "60");
        return Response.json(200, "{\"success\":true,\"challenge\":\"" + id + "\",\"expiresIn\":"
                + Challenges.LIFETIME_MS / 1000 + "}");
    }

    private Response login(Request r) {
        String username = r.header("X-Minecraft-Username");
        String serverId = r.header("X-Minecraft-Server");
        if (!Ids.username(username) || !Ids.serverId(serverId)) return Response.error(400, "Missing username or server id");
        // Only a challenge this server handed out, once: see Challenges for why.
        if (!challenges.consume(serverId)) return Response.error(401, "Unknown or used challenge");
        String uuid;
        try {
            uuid = Ids.uuid(mojang.hasJoined(username, serverId));
        } catch (IOException e) {
            return Response.error(503, "Mojang can't be reached");
        }
        if (uuid == null) return Response.error(401, "Mojang doesn't know this login");
        String token = sessions.issue(uuid);
        return Response.json(200, "{\"success\":true,\"token\":\"" + token + "\",\"uuid\":\"" + uuid
                + "\",\"expiresIn\":" + Sessions.LIFETIME_MS / 1000 + "}");
    }

    private Response dashboard(Request r, String rest) {
        String auth = r.header("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring("Bearer ".length()).trim() : null;
        String player = sessions.uuidOf(token);
        if (player == null) return Response.error(401, "Log in first");

        String id = Ids.uuid(rest);
        if (id == null) return Response.error(400, "Not a UUID");

        if (!requests.tryUse(player)) {
            return Response.error(429, "Too many requests").with("Retry-After", String.valueOf(requests.retryAfterSeconds(player)));
        }
        String key = "dashboard/" + id;
        ResponseCache.Entry cached = cache.get(key, id.equals(player) ? OWN_MAX_AGE_MS : MAX_AGE_MS);
        if (cached != null) return answer(cached.status(), cached.body(), "HIT", cached.fetchedAt());

        if (keyRemaining < KEY_RESERVE && clock.getAsLong() < keyResetAt) {
            long wait = Math.max(1, (keyResetAt - clock.getAsLong() + 999) / 1000);
            return Response.error(503, "Busy, try again shortly").with("Retry-After", String.valueOf(wait));
        }
        if (!fetches.tryUse(player)) {
            return Response.error(429, "Too many new lookups").with("Retry-After", String.valueOf(fetches.retryAfterSeconds(player)));
        }

        Fetched fetched;
        try {
            fetched = fetch(id);
        } catch (IOException e) {
            return Response.error(502, "Hypixel can't be reached");
        }
        Upstream.Result result = fetched.result();
        if (result.status() == 429) {
            return Response.error(503, "Busy, try again shortly").with("Retry-After", String.valueOf(Math.max(1, result.resetSeconds())));
        }
        if (result.status() == 403) return Response.error(503, "The server's Hypixel key isn't working");
        if (result.status() != 200) return Response.error(502, "Hypixel answered " + result.status());
        if (fetched.body() == null) return Response.error(502, "Hypixel's answer couldn't be read");
        cache.put(key, 200, fetched.body());
        return answer(200, fetched.body(), "MISS", clock.getAsLong());
    }

    /** Hypixel's answer, and the dashboard's part of it when it was a readable 200. */
    private record Fetched(Upstream.Result result, String body) {}

    /** One request to Hypixel per player at a time: two people opening the same profile share it. */
    private Fetched fetch(String uuid) throws IOException {
        CompletableFuture<Fetched> mine = new CompletableFuture<>();
        CompletableFuture<Fetched> running = inFlight.putIfAbsent(uuid, mine);
        if (running != null) {
            try {
                return running.join();
            } catch (java.util.concurrent.CompletionException e) {
                throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
            }
        }
        try {
            Upstream.Result result = hypixel.get("/v2/skyblock/profiles?uuid=" + uuid);
            if (result.remaining() >= 0) {
                keyRemaining = result.remaining();
                keyResetAt = clock.getAsLong() + Math.max(0, result.resetSeconds()) * 1000L;
            }
            String body = null;
            if (result.status() == 200) {
                try {
                    body = Dashboard.of(result.body(), uuid);
                } catch (IllegalArgumentException e) {
                    System.err.println("Hypixel's profiles answer couldn't be read: " + e.getMessage());
                }
            }
            Fetched fetched = new Fetched(result, body);
            mine.complete(fetched);
            return fetched;
        } catch (IOException | RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(uuid, mine);
        }
    }

    private static Response answer(int status, String body, String cache, long fetchedAt) {
        return new Response(status, "application/json", body, Map.of("X-Cache", cache, "X-Fetched-At", String.valueOf(fetchedAt)));
    }

    int sessions() {
        return sessions.size();
    }
}
