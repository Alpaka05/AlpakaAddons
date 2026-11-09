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
 * Logged in, a player may ask for what the Profile Dashboard shows of a player
 * ({@code /v1/dashboard/<name or uuid>}). It is fetched from Hypixel with the server's key only when asked for, cut down to the dashboard's
 * fields ({@link Dashboard}), cached, and limited per player, so the key's budget can't be spent by
 * one of them. It is not a copy of Hypixel's API: there is no way to ask for Hypixel's answer as it
 * is. Nothing is fetched on its own and nothing is kept about who asked for what.
 */
final class Api {
    record Request(String method, String path, String query, Map<String, String> headers, String ip) {
        Request(String method, String path, Map<String, String> headers, String ip) {
            this(method, path, null, headers, ip);
        }

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        /** A parameter of the query string, as written; null when it isn't there. */
        String param(String name) {
            if (query == null) return null;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
            }
            return null;
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

    /** Mojang's account lookup: who a name or a UUID belongs to, with the skin for the face. */
    interface Accounts {
        /** {@code uuid} without dashes; {@code skin} the texture's hash, or null. */
        record Account(String uuid, String name, String skin) {}

        /** The account with this name or UUID, or null when there is none. */
        Account find(String nameOrUuid) throws IOException;
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
    /** With the dashboard's Refresh, another player's answer may be this young at the least. */
    static final long REFRESH_AGE_MS = 5 * 60_000L;
    /** A player's own profile may be fresher, since they just changed it in the game. */
    static final long OWN_MAX_AGE_MS = 3 * 60_000L;
    /** With the dashboard's Refresh, their own may be this young; Hypixel's answer hardly changes faster. */
    static final long OWN_REFRESH_AGE_MS = 60_000L;
    /** A name keeps its account, and an account its skin, this long. */
    static final long ACCOUNT_AGE_MS = 6 * 60 * 60_000L;
    /** About 48 MB of answers; the service runs with a 512 MB heap. */
    static final long CACHE_CHARS = 48_000_000L;

    /** Below this much of the key's budget, players wait for it to refill rather than use it up. */
    static final int KEY_RESERVE = 15;

    private final SessionCheck mojang;
    private final Accounts accounts;
    private final Upstream hypixel;
    private final LongSupplier clock;
    private final Sessions sessions;
    private final Challenges challenges;
    private final ResponseCache cache;
    private final RateLimit logins;
    private final RateLimit requests;
    private final RateLimit fetches;
    private final Map<String, CompletableFuture<Upstream.Result>> inFlight = new ConcurrentHashMap<>();
    /** Accounts by UUID and by name in lower case; the least recently asked for go first. */
    private final Map<String, Known> known = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Known> eldest) {
            return size() > 20_000;
        }
    });
    private volatile int keyRemaining = Integer.MAX_VALUE;
    private volatile long keyResetAt = 0L;

    /** An account, or the knowledge that there is none ({@code account} null), and when that was learned. */
    private record Known(Accounts.Account account, long at) {}

    Api(SessionCheck mojang, Accounts accounts, Upstream hypixel, LongSupplier clock) {
        this.mojang = mojang;
        this.accounts = accounts;
        this.hypixel = hypixel;
        this.clock = clock;
        this.sessions = new Sessions(clock);
        this.challenges = new Challenges(clock);
        this.cache = new ResponseCache(CACHE_CHARS, clock);
        // Ten challenges, so ten logins, per address in ten minutes; a player logs in once per game start.
        this.logins = new RateLimit(10, 10 * 60_000L, clock);
        // Opening a profile takes one request; sixty in five minutes is a busy player.
        this.requests = new RateLimit(60, 5 * 60_000L, clock);
        // Of those, at most forty-five may go to Hypixel - a profile not cached takes three - which
        // is under a sixth of the key's 300.
        this.fetches = new RateLimit(45, 5 * 60_000L, clock);
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

    /** The player a request's token belongs to, or null. */
    private String player(Request r) {
        String auth = r.header("Authorization");
        return sessions.uuidOf(auth != null && auth.startsWith("Bearer ") ? auth.substring("Bearer ".length()).trim() : null);
    }

    /** An answer that isn't the data asked for: why, for the mod to tell the player. */
    private static final class Refusal extends Exception {
        final Response response;

        Refusal(Response response) {
            super(null, null, false, false);
            this.response = response;
        }
    }

    /** What Hypixel hands out apart from a profile: its garden and the player's part of its museum, as JSON or null. */
    private record Extras(String garden, String museum) {}

    /**
     * A player with their profiles and the dashboard's data of one of them: the one named with
     * {@code ?profile=}, else the selected one. {@code ?fresh=1} is the dashboard's Refresh.
     *
     * Hypixel is asked three things for it: the profiles, and the chosen profile's garden and
     * museum. Which profile is chosen only shows in the profiles, so the other two would have to
     * wait for them. They are asked for alongside wherever a good guess can be made: the profile
     * named, the one chosen the last time this player was looked up, or the one the mod last
     * showed ({@code ?hint=}). A wrong guess costs two requests and is put right at once.
     */
    private Response dashboard(Request r, String rest) {
        String player = player(r);
        if (player == null) return Response.error(401, "Log in first");
        if (Ids.uuid(rest) == null && !Ids.username(rest)) return Response.error(400, "Not a name or UUID");
        if (!requests.tryUse(player)) {
            return Response.error(429, "Too many requests").with("Retry-After", String.valueOf(requests.retryAfterSeconds(player)));
        }
        try {
            // A name has to become a UUID first. Whose a UUID is - the name, the skin - isn't needed
            // to look its profiles up, so Mojang is asked for that alongside Hypixel.
            String direct = Ids.uuid(rest);
            Accounts.Account named = direct == null ? account(rest) : null;
            if (direct == null && named == null) return Response.error(404, "No Minecraft account with that name");
            String uuid = direct != null ? direct : named.uuid();
            CompletableFuture<Accounts.Account> alongside = direct == null ? null
                    : CompletableFuture.supplyAsync(() -> owner(direct), Thread::startVirtualThread);
            long maxAge = maxAge(uuid.equals(player), "1".equals(r.param("fresh")));
            String wanted = Ids.uuid(r.param("profile"));

            ResponseCache.Entry index = cache.get("profiles/" + uuid, maxAge);
            boolean hit = index != null;
            String guess = null;
            CompletableFuture<Extras> early = null;
            if (index == null) {
                ResponseCache.Entry last = cache.get("profiles/" + uuid, Long.MAX_VALUE);
                guess = wanted != null ? wanted : last != null ? pick(last.body(), null) : Ids.uuid(r.param("hint"));
                if (guess != null) early = extras(uuid, guess, maxAge, player);
                index = loadProfiles(uuid, player);
            }

            String id = pick(index.body(), wanted);
            ResponseCache.Entry profile = id == null ? null : cache.get("profile/" + uuid + "/" + id, Long.MAX_VALUE);
            if (id != null && (profile == null || profile.fetchedAt() < index.fetchedAt())) {
                // Pushed out of the cache while the list stayed: once more from Hypixel.
                index = loadProfiles(uuid, player);
                hit = false;
                id = pick(index.body(), wanted);
                profile = id == null ? null : cache.get("profile/" + uuid + "/" + id, Long.MAX_VALUE);
            }
            Extras extras = id == null ? new Extras(null, null)
                    : (early != null && id.equals(guess) ? early : extras(uuid, id, maxAge, player)).join();
            Accounts.Account account = alongside != null ? alongside.join() : named;
            if (account == null) return Response.error(404, "No Minecraft account with that UUID");

            int size = 256 + (profile == null ? 0 : profile.body().length()) + (extras.museum() == null ? 0 : extras.museum().length());
            StringBuilder body = new StringBuilder(size);
            body.append("{\"success\":true,\"fetchedAt\":").append(index.fetchedAt());
            body.append(",\"player\":").append(Json.write(accountJson(account)));
            body.append(",\"profiles\":").append(index.body());
            body.append(",\"profile\":").append(profile == null ? "null" : profile.body());
            body.append(",\"garden\":").append(extras.garden() == null ? "null" : extras.garden());
            body.append(",\"museum\":").append(extras.museum() == null ? "null" : extras.museum());
            body.append('}');
            return answer(body.toString(), hit, index.fetchedAt());
        } catch (Refusal refusal) {
            return refusal.response;
        }
    }

    /** A profile's garden and the player's museum on it, each asked for on a thread of its own. */
    private CompletableFuture<Extras> extras(String uuid, String profile, long maxAge, String player) {
        CompletableFuture<String> garden = CompletableFuture.supplyAsync(() -> garden(profile, maxAge, player), Thread::startVirtualThread);
        CompletableFuture<String> museum = CompletableFuture.supplyAsync(() -> museum(uuid, profile, maxAge, player), Thread::startVirtualThread);
        return garden.thenCombine(museum, Extras::new);
    }

    /**
     * What the player donated to a profile's museum as JSON - an empty object for nothing - or null
     * when Hypixel doesn't hand it out right now: a profile is worth showing without it.
     */
    private String museum(String uuid, String profile, long maxAge, String player) {
        String key = "museum/" + profile + "/" + uuid;
        ResponseCache.Entry cached = cache.get(key, maxAge);
        if (cached != null) return cached.body();
        try {
            Upstream.Result result = fetch("/v2/skyblock/museum?profile=" + Ids.dashed(profile), player);
            String museum = Dashboard.museum(result.body(), uuid);
            cache.put(key, 200, museum);
            return museum;
        } catch (Refusal refusal) {
            return null;
        } catch (RuntimeException e) {
            System.err.println("Hypixel's museum answer couldn't be read: " + e.getMessage());
            return null;
        }
    }

    private static long maxAge(boolean own, boolean refresh) {
        if (own) return refresh ? OWN_REFRESH_AGE_MS : OWN_MAX_AGE_MS;
        return refresh ? REFRESH_AGE_MS : MAX_AGE_MS;
    }

    /** The account behind a name or UUID, from Mojang at most every few hours; null when there is none. */
    private Accounts.Account account(String nameOrUuid) throws Refusal {
        String uuid = Ids.uuid(nameOrUuid);
        String key = uuid != null ? uuid : nameOrUuid.toLowerCase(Locale.ROOT);
        long now = clock.getAsLong();
        Known cached = known.get(key);
        if (cached != null && now - cached.at() < ACCOUNT_AGE_MS) return cached.account();
        Accounts.Account account;
        try {
            account = accounts.find(uuid != null ? uuid : nameOrUuid);
        } catch (IOException e) {
            // A UUID needs no name to look its profiles up; the face and the name are the mod's to fill in.
            if (uuid != null) return new Accounts.Account(uuid, null, null);
            throw new Refusal(Response.error(502, "Mojang can't be reached"));
        }
        Known learned = new Known(account, now);
        known.put(key, learned);
        if (account != null) {
            known.put(account.uuid(), learned);
            if (account.name() != null) known.put(account.name().toLowerCase(Locale.ROOT), learned);
        }
        return account;
    }

    /** The account a UUID belongs to; without Mojang, the UUID alone. Null when Mojang says there is none. */
    private Accounts.Account owner(String uuid) {
        try {
            return account(uuid);
        } catch (Refusal | RuntimeException e) {
            return new Accounts.Account(uuid, null, null);
        }
    }

    private static Map<String, Object> accountJson(Accounts.Account account) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("uuid", account.uuid());
        out.put("name", account.name());
        out.put("skin", account.skin());
        return out;
    }

    /** The id to show from the list of profiles: the wanted one if it is there, else the selected, else the first. */
    private static String pick(String index, String wanted) {
        String first = null;
        String selected = null;
        if (Json.parse(index) instanceof java.util.List<?> list) {
            for (Object p : list) {
                if (!(p instanceof Map<?, ?> profile) || !(profile.get("profile_id") instanceof String id)) continue;
                if (id.equals(wanted)) return id;
                if (first == null) first = id;
                if (selected == null && Boolean.TRUE.equals(profile.get("selected"))) selected = id;
            }
        }
        return selected != null ? selected : first;
    }

    /** Asks Hypixel for a player's profiles and keeps the list and each profile's part; the list is what comes back. */
    private ResponseCache.Entry loadProfiles(String uuid, String player) throws Refusal {
        Upstream.Result result = fetch("/v2/skyblock/profiles?uuid=" + uuid, player);
        java.util.List<Dashboard.Profile> profiles;
        try {
            profiles = Dashboard.profiles(result.body(), uuid);
        } catch (IllegalArgumentException e) {
            System.err.println("Hypixel's profiles answer couldn't be read: " + e.getMessage());
            throw new Refusal(Response.error(502, "Hypixel's answer couldn't be read"));
        }
        // One moment for the list and its profiles, which is how a profile pushed out of the cache
        // and fetched again is told from one that belongs to an older list.
        long now = clock.getAsLong();
        for (Dashboard.Profile p : profiles) cache.put("profile/" + uuid + "/" + p.id(), 200, p.json(), now);
        String index = Dashboard.index(profiles);
        cache.put("profiles/" + uuid, 200, index, now);
        return new ResponseCache.Entry(200, index, now);
    }

    /**
     * The dashboard's part of a profile's garden as JSON, or null when it has none or Hypixel
     * doesn't hand it out right now: a profile is worth showing without its garden.
     */
    private String garden(String profile, long maxAge, String player) {
        String key = "garden/" + profile;
        ResponseCache.Entry cached = cache.get(key, maxAge);
        if (cached != null) return cached.status() == 200 ? cached.body() : null;
        try {
            Upstream.Result result = fetch("/v2/skyblock/garden?profile=" + Ids.dashed(profile), player);
            String garden = Dashboard.garden(result.body());
            cache.put(key, garden == null ? 404 : 200, garden == null ? "" : garden);
            return garden;
        } catch (Refusal refusal) {
            // Hypixel answers 404 for a profile that never had a garden; that is worth remembering.
            if (refusal.response.status() == 404) cache.put(key, 404, "");
            return null;
        } catch (RuntimeException e) {
            System.err.println("Hypixel's garden answer couldn't be read: " + e.getMessage());
            return null;
        }
    }

    /**
     * One request to Hypixel for a player, if the key's budget and the player's own allow it; a
     * 200 comes back, anything else is refused in words for the mod.
     */
    private Upstream.Result fetch(String pathAndQuery, String player) throws Refusal {
        if (keyRemaining < KEY_RESERVE && clock.getAsLong() < keyResetAt) {
            long wait = Math.max(1, (keyResetAt - clock.getAsLong() + 999) / 1000);
            throw new Refusal(Response.error(503, "Busy, try again shortly").with("Retry-After", String.valueOf(wait)));
        }
        if (!fetches.tryUse(player)) {
            throw new Refusal(Response.error(429, "Too many new lookups").with("Retry-After", String.valueOf(fetches.retryAfterSeconds(player))));
        }
        Upstream.Result result;
        try {
            result = shared(pathAndQuery);
        } catch (IOException e) {
            throw new Refusal(Response.error(502, "Hypixel can't be reached"));
        }
        if (result.status() == 429) {
            throw new Refusal(Response.error(503, "Busy, try again shortly").with("Retry-After", String.valueOf(Math.max(1, result.resetSeconds()))));
        }
        if (result.status() == 403) throw new Refusal(Response.error(503, "The server's Hypixel key isn't working"));
        if (result.status() == 404) throw new Refusal(Response.error(404, "Hypixel has nothing for this"));
        if (result.status() != 200) throw new Refusal(Response.error(502, "Hypixel answered " + result.status()));
        return result;
    }

    /** One request to Hypixel per address at a time: two people opening the same profile share it. */
    private Upstream.Result shared(String pathAndQuery) throws IOException {
        CompletableFuture<Upstream.Result> mine = new CompletableFuture<>();
        CompletableFuture<Upstream.Result> running = inFlight.putIfAbsent(pathAndQuery, mine);
        if (running != null) {
            try {
                return running.join();
            } catch (java.util.concurrent.CompletionException e) {
                throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
            }
        }
        try {
            Upstream.Result result = hypixel.get(pathAndQuery);
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
            inFlight.remove(pathAndQuery, mine);
        }
    }

    private static Response answer(String body, boolean hit, long fetchedAt) {
        return new Response(200, "application/json", body, Map.of("X-Cache", hit ? "HIT" : "MISS", "X-Fetched-At", String.valueOf(fetchedAt)));
    }

    int sessions() {
        return sessions.size();
    }
}
