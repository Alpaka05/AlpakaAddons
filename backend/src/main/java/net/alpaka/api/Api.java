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
 * The mod logs in by proving its Minecraft account to Mojang, as joining a server does: it tells
 * Mojang's session server it joins a made-up server id, and this side asks Mojang whether that
 * player did ({@code hasJoined}). No password or token of the player ever reaches this server.
 * Logged in, a player may ask for SkyBlock profiles, a museum or a garden; each is fetched from
 * Hypixel with the server's key only when asked for, cached, and limited per player, so the key's
 * budget can't be spent by one of them. Nothing is fetched on its own and nothing is kept about who
 * asked for what.
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

    /** What a player may ask for: the path on Hypixel's API, and how long an answer is kept. */
    private record Route(String hypixelPath, String query, long maxAgeMs) {}

    private static final Map<String, Route> ROUTES = Map.of(
            "profiles", new Route("/v2/skyblock/profiles", "uuid", 5 * 60_000L),
            "museum", new Route("/v2/skyblock/museum", "profile", 10 * 60_000L),
            "garden", new Route("/v2/skyblock/garden", "profile", 10 * 60_000L));

    /** Below this much of the key's budget, players wait for it to refill rather than use it up. */
    static final int KEY_RESERVE = 15;

    private final SessionCheck mojang;
    private final Upstream hypixel;
    private final LongSupplier clock;
    private final Sessions sessions;
    private final ResponseCache cache;
    private final RateLimit logins;
    private final RateLimit requests;
    private final RateLimit fetches;
    private final Map<String, CompletableFuture<Upstream.Result>> inFlight = new ConcurrentHashMap<>();
    private volatile int keyRemaining = Integer.MAX_VALUE;
    private volatile long keyResetAt = 0L;

    Api(SessionCheck mojang, Upstream hypixel, LongSupplier clock) {
        this.mojang = mojang;
        this.hypixel = hypixel;
        this.clock = clock;
        this.sessions = new Sessions(clock);
        this.cache = new ResponseCache(2000, clock);
        // Ten logins per address in ten minutes; a player logs in once per game start.
        this.logins = new RateLimit(10, 10 * 60_000L, clock);
        // Opening a profile takes one to three requests; sixty in five minutes is a busy player.
        this.requests = new RateLimit(60, 5 * 60_000L, clock);
        // Of those, at most twenty may go to Hypixel, a fifteenth of the key's 300.
        this.fetches = new RateLimit(20, 5 * 60_000L, clock);
    }

    Response handle(Request r) {
        if (!r.method().equals("GET")) return Response.error(405, "Only GET");
        String path = r.path();
        if (path.equals("/")) {
            return new Response(200, "text/plain; charset=utf-8",
                    "AlpakaAddons API, the Profile Dashboard's way to the Hypixel API.\n"
                            + "Not affiliated with Hypixel. https://github.com/Alpaka05/AlpakaAddons\n", Map.of());
        }
        if (path.equals("/health")) return new Response(200, "text/plain; charset=utf-8", "ok\n", Map.of());
        if (path.equals("/v1/auth")) return login(r);
        if (path.startsWith("/v1/skyblock/")) return skyblock(r, path.substring("/v1/skyblock/".length()));
        return Response.error(404, "Not found");
    }

    private Response login(Request r) {
        String ip = r.ip() == null ? "?" : r.ip();
        if (!logins.tryUse(ip)) {
            return Response.error(429, "Too many logins").with("Retry-After", String.valueOf(logins.retryAfterSeconds(ip)));
        }
        String username = r.header("X-Minecraft-Username");
        String serverId = r.header("X-Minecraft-Server");
        if (!Ids.username(username) || !Ids.serverId(serverId)) return Response.error(400, "Missing username or server id");
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

    private Response skyblock(Request r, String rest) {
        String auth = r.header("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring("Bearer ".length()).trim() : null;
        String player = sessions.uuidOf(token);
        if (player == null) return Response.error(401, "Log in first");

        int slash = rest.indexOf('/');
        Route route = slash > 0 ? ROUTES.get(rest.substring(0, slash)) : null;
        String id = slash > 0 ? Ids.uuid(rest.substring(slash + 1)) : null;
        if (route == null) return Response.error(404, "Not found");
        if (id == null) return Response.error(400, "Not a UUID");

        if (!requests.tryUse(player)) {
            return Response.error(429, "Too many requests").with("Retry-After", String.valueOf(requests.retryAfterSeconds(player)));
        }
        String upstreamPath = route.hypixelPath() + "?" + route.query() + "=" + id;
        ResponseCache.Entry cached = cache.get(upstreamPath, route.maxAgeMs());
        if (cached != null) return answer(cached.status(), cached.body(), "HIT", cached.fetchedAt());

        if (keyRemaining < KEY_RESERVE && clock.getAsLong() < keyResetAt) {
            long wait = Math.max(1, (keyResetAt - clock.getAsLong() + 999) / 1000);
            return Response.error(503, "Busy, try again shortly").with("Retry-After", String.valueOf(wait));
        }
        if (!fetches.tryUse(player)) {
            return Response.error(429, "Too many new lookups").with("Retry-After", String.valueOf(fetches.retryAfterSeconds(player)));
        }

        Upstream.Result result;
        try {
            result = fetch(upstreamPath);
        } catch (IOException e) {
            return Response.error(502, "Hypixel can't be reached");
        }
        if (result.status() == 429) {
            return Response.error(503, "Busy, try again shortly").with("Retry-After", String.valueOf(Math.max(1, result.resetSeconds())));
        }
        if (result.status() == 403) return Response.error(503, "The server's Hypixel key isn't working");
        if (result.status() == 200) cache.put(upstreamPath, result.status(), result.body());
        return answer(result.status(), result.body(), "MISS", clock.getAsLong());
    }

    /** One request to Hypixel per path at a time: two players opening the same profile share it. */
    private Upstream.Result fetch(String upstreamPath) throws IOException {
        CompletableFuture<Upstream.Result> mine = new CompletableFuture<>();
        CompletableFuture<Upstream.Result> running = inFlight.putIfAbsent(upstreamPath, mine);
        if (running != null) {
            try {
                return running.join();
            } catch (java.util.concurrent.CompletionException e) {
                throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
            }
        }
        try {
            Upstream.Result result = hypixel.get(upstreamPath);
            if (result.remaining() >= 0) {
                keyRemaining = result.remaining();
                keyResetAt = clock.getAsLong() + Math.max(0, result.resetSeconds()) * 1000L;
            }
            mine.complete(result);
            return result;
        } catch (IOException | RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(upstreamPath, mine);
        }
    }

    private static Response answer(int status, String body, String cache, long fetchedAt) {
        return new Response(status, "application/json", body, Map.of("X-Cache", cache, "X-Fetched-At", String.valueOf(fetchedAt)));
    }

    int sessions() {
        return sessions.size();
    }
}
