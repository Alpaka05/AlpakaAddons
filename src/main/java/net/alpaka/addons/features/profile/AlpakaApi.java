package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.utils.ModVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The AlpakaAddons API at api.alpaka.yt: where the Profile Dashboard gets a profile from. It asks
 * Hypixel's API, which needs a key that may never ship in a mod, so the key stays on that server
 * (see {@code backend/} in the repository) and the server hands out what the dashboard shows.
 *
 * Logging in works as joining a server does: the API hands out a one-time challenge, the game tells
 * Mojang's session server that the player joins a server with an id worked out from it
 * ({@link #loginServerId}), and the API asks Mojang whether they did. The player's access token
 * only ever goes to Mojang, never to the API. The API then hands out a token of its own for half a
 * day, which is kept on disk for the account it belongs to, so a game started again within that time
 * needs no new login. Only used from the Profile Dashboard, while Network Features is on, and only
 * for a profile someone opened; the one exception is {@link #warmUp}, which renews the login of a
 * player who used the dashboard before, once the game is up, so the next opening doesn't wait for it.
 */
final class AlpakaApi {
    private AlpakaApi() {}

    static final String BASE = "https://api.alpaka.yt";

    /** Thrown for an answer the API gave on purpose, with its reason for the player. */
    static final class Refused extends RuntimeException {
        final int status;
        /** Whether the answer came from the way to the API (Cloudflare) rather than from the API itself. */
        final boolean onTheWay;

        Refused(int status, String message) {
            this(status, message, false);
        }

        Refused(int status, String message, boolean onTheWay) {
            super(message);
            this.status = status;
            this.onTheWay = onTheWay;
        }
    }

    /**
     * Mojang turned the login down, before the API was even asked. Not a refused token of the API's
     * own, so {@link #get} doesn't log in once more for it: that would only ask Mojang again.
     */
    static final class MojangRefused extends RuntimeException {
        MojangRefused(String message) {
            super(message);
        }
    }

    /** How long Mojang is left alone after it said there were too many logins. */
    private static final long MOJANG_BUSY_MS = 60_000L;

    /**
     * Whether an answer came from Cloudflare in front of the API rather than from the API: the API
     * answers everything with {@code "success"} in JSON, Cloudflare's 502 and its kin are a page.
     */
    static boolean onTheWay(int status, JsonElement body) {
        boolean api = body != null && body.isJsonObject() && body.getAsJsonObject().has("success");
        return status >= 500 && !api;
    }

    /** What a failure on the way to the API is, for the player. */
    static String onTheWayReason(int status) {
        return "Couldn't get through to the AlpakaAddons API just now (" + status + "). Try again in a moment.";
    }

    /** How long to wait before the one retry of a request that failed on the way. */
    private static final long RETRY_MS = 400L;

    private static HttpClient client;
    private static volatile String token;
    private static volatile long tokenUntil;
    private static CompletableFuture<String> login;
    /**
     * The game's access token Mojang no longer accepts: it runs out after a while, and only the
     * launcher gets a new one. Asking again with it can't work and only counts towards Mojang's
     * limit, so it isn't tried again until the game has another one (a restart, an account switch).
     */
    private static volatile String rejectedAccessToken;
    private static volatile long mojangBusyUntil;

    private static synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client;
    }

    /**
     * A player and one of their SkyBlock profiles, as Hypixel has them: who they are, the profiles
     * they are on, and of one profile - {@code profileId}, or the selected one for null - the
     * player's own data, what its co-op shares, its garden and what the player gave its museum.
     * {@code player} is a name or a UUID.
     *
     * {@code hint} is the profile shown the last time, if any: the API can then ask Hypixel for
     * that profile's garden and museum while it still waits for the profiles, instead of after.
     * {@code fresh} is the Refresh button: the API then asks Hypixel again if its answer is more
     * than a minute old.
     */
    static CompletableFuture<JsonElement> dashboard(String player, String profileId, String hint, boolean fresh) {
        if (!ProfileApi.allowed()) return CompletableFuture.failedFuture(new ProfileApi.NetworkOff());
        if (!player.matches("[A-Za-z0-9_-]{1,36}")) {
            return CompletableFuture.failedFuture(new ProfileApi.NotFound("\"" + player + "\" is no Minecraft name."));
        }
        StringBuilder path = new StringBuilder("/v1/dashboard/").append(player.replace("-", ""));
        char next = '?';
        if (profileId != null) {
            path.append(next).append("profile=").append(profileId.replace("-", ""));
            next = '&';
        } else if (hint != null && hint.matches("[0-9a-fA-F-]{32,36}")) {
            path.append(next).append("hint=").append(hint.replace("-", ""));
            next = '&';
        }
        if (fresh) path.append(next).append("fresh=1");
        return get(path.toString(), true);
    }

    /**
     * What the player gave a profile's museum, as the API hands it out apart from the dashboard:
     * {@code {"museum": {...}}}, with null for a museum Hypixel didn't hand out. The dashboard's
     * request has the API ask Hypixel for it already, so it is mostly there by the time this asks.
     */
    static CompletableFuture<JsonElement> museum(String playerUuid, String profileId, boolean fresh) {
        if (!ProfileApi.allowed()) return CompletableFuture.failedFuture(new ProfileApi.NetworkOff());
        String player = playerUuid == null ? "" : playerUuid.replace("-", "");
        String profile = profileId == null ? "" : profileId.replace("-", "");
        if (!player.matches("[0-9a-fA-F]{32}") || !profile.matches("[0-9a-fA-F]{32}")) {
            return CompletableFuture.failedFuture(new ProfileApi.NotFound("No such profile."));
        }
        return get("/v1/museum/" + player + "/" + profile + (fresh ? "?fresh=1" : ""), true);
    }

    /**
     * Whether the player is online on Hypixel and where: {@code {"online": true, "gameType":
     * "SKYBLOCK", "mode": "hub"}}. {@code fresh} is the Refresh button.
     */
    static CompletableFuture<JsonElement> status(String playerUuid, boolean fresh) {
        if (!ProfileApi.allowed()) return CompletableFuture.failedFuture(new ProfileApi.NetworkOff());
        String player = playerUuid == null ? "" : playerUuid.replace("-", "");
        if (!player.matches("[0-9a-fA-F]{32}")) return CompletableFuture.failedFuture(new ProfileApi.NotFound("No such player."));
        return get("/v1/status/" + player + (fresh ? "?fresh=1" : ""), true);
    }

    /**
     * Renews the login in the background when the game is up, for a player who has logged in to the
     * API before (a login is kept on disk) and whose login ran out: the dashboard then opens without
     * the second it takes. Nothing happens for anyone who never opened the dashboard, or with
     * Network Features off.
     */
    static void warmUp() {
        if (!ProfileApi.allowed()) return;
        CompletableFuture.runAsync(() -> {
            Saved saved = readSaved();
            if (saved == null || !saved.uuid.equals(currentUuid())) return;
            token().exceptionally(e -> null);
        });
    }

    private static CompletableFuture<JsonElement> get(String path, boolean mayLogInAgain) {
        return get(path, mayLogInAgain, true);
    }

    private static CompletableFuture<JsonElement> get(String path, boolean mayLogInAgain, boolean mayRetry) {
        return token().thenCompose(t -> send(path, t)).exceptionallyCompose(e -> {
            Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
            if (mayLogInAgain && cause instanceof Refused r && r.status == 401) {
                // The login was refused (its secret changed, or it ran out on the way): once more with a new one.
                forgetToken();
                return get(path, false, mayRetry);
            }
            // Lost on the way - Cloudflare couldn't pass it on, or the connection broke - and the
            // request only reads: once more, a moment later, before the player sees anything.
            boolean lost = cause instanceof Refused r ? r.onTheWay
                    : cause instanceof java.io.IOException && !(cause instanceof java.net.http.HttpTimeoutException);
            if (mayRetry && lost) {
                Executor later = CompletableFuture.delayedExecutor(RETRY_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                return CompletableFuture.supplyAsync(() -> null, later).thenCompose(x -> get(path, mayLogInAgain, false));
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    /** A request of the login, sent once more after a failure on the way, as {@link #get} does. */
    private static HttpResponse<String> sendLogin(HttpRequest request) throws java.io.IOException, InterruptedException {
        HttpResponse<String> first;
        try {
            first = client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw e;
        } catch (java.io.IOException e) {
            Thread.sleep(RETRY_MS);
            return client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        }
        if (!onTheWay(first.statusCode(), parse(first.body()))) return first;
        Thread.sleep(RETRY_MS);
        return client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private static synchronized void forgetToken() {
        token = null;
        tokenUntil = 0L;
        tokenUuid = null;
        java.io.File file = savedFile();
        if (file.exists() && !file.delete()) AlpakaAddons.LOGGER.debug("AlpakaAddons API: couldn't delete the saved login");
    }

    /** The API's token, logging in first when there is none or it is about to run out. */
    private static synchronized CompletableFuture<String> token() {
        String known = token;
        String account = currentUuid();
        // Another account was switched to (a launcher can do that between two games): its own login.
        if (known != null && account != null && !account.equals(tokenUuid)) known = null;
        if (known != null && System.currentTimeMillis() < tokenUntil) return CompletableFuture.completedFuture(known);
        if (!savedRead) {
            savedRead = true;
            Saved saved = readSaved();
            if (saved != null && saved.uuid.equals(account) && System.currentTimeMillis() < saved.until) {
                token = saved.token;
                tokenUntil = saved.until;
                tokenUuid = saved.uuid;
                return CompletableFuture.completedFuture(saved.token);
            }
        }
        if (login != null && !login.isDone()) return login;
        login = CompletableFuture.supplyAsync(AlpakaApi::logIn, LOGIN);
        return login;
    }

    /**
     * The login waits on Mojang and the API in turn, up to a minute with its retries. It gets a
     * thread of its own: on the shared pool it would hold up everything else the mod and the game
     * hand there meanwhile (the networth, reading the kept profile).
     */
    private static final Executor LOGIN = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Alpaka API login");
        t.setDaemon(true);
        return t;
    });

    private static String logIn() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        String accessToken = user.getAccessToken();
        if (accessToken != null && accessToken.equals(rejectedAccessToken)) throw new MojangRefused(SESSION_EXPIRED);
        if (System.currentTimeMillis() < mojangBusyUntil) throw new MojangRefused(MOJANG_BUSY);
        String challenge = challenge();
        // The game tells Mojang it joins a server id worked out from the challenge, never the
        // challenge itself: the API could otherwise hand out the hash of a real server's login
        // (Hypixel's, say) and be let into that server as this player. No challenge leads to a
        // given server's hash, and the API checks the same id worked out from its own challenge.
        String serverId = loginServerId(challenge);
        try {
            mc.services().sessionService().joinServer(user.getProfileId(), accessToken, serverId);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.warn("AlpakaAddons API: Mojang refused the login: {}", e.toString());
            throw mojangRefused(e, accessToken);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v1/auth"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .header("X-Minecraft-Username", user.getName())
                .header("X-Minecraft-Server", challenge)
                .header("X-Alpaka-Login", LOGIN_VERSION)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = sendLogin(request);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new Refused(0, "The AlpakaAddons API can't be reached right now.");
        }
        JsonElement body = parse(response.body());
        String t = Json.str(body, "token");
        if (response.statusCode() != 200 || t == null) throw new Refused(response.statusCode(), reason(response.statusCode(), body));
        long seconds = (long) Json.num(body, "expiresIn", 3600);
        String uuid = user.getProfileId() == null ? null : user.getProfileId().toString().replace("-", "");
        synchronized (AlpakaApi.class) {
            token = t;
            // A few minutes early, so a token never runs out on the way.
            tokenUntil = System.currentTimeMillis() + Math.max(60, seconds - 300) * 1000L;
            tokenUuid = uuid;
            if (uuid != null) save(new Saved(uuid, t, tokenUntil));
        }
        return t;
    }

    /** Tells the API that Mojang was told {@link #loginServerId}, not the challenge itself. */
    static final String LOGIN_VERSION = "2";

    /**
     * The server id a login tells Mojang for an API challenge: SHA-1 of the challenge behind a
     * prefix of this API's own, as 40 hex digits. The API works it out the same way
     * ({@code Challenges.loginServerId} in {@code backend/}).
     */
    static String loginServerId(String challenge) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(("alpaka-api-login:" + challenge).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static final String SESSION_EXPIRED = "Couldn't log in to the AlpakaAddons API: Mojang no longer accepts this game's"
            + " Microsoft login (it runs out after a while). Restart the game to log in again.";
    static final String MOJANG_BUSY = "Couldn't log in to the AlpakaAddons API: Mojang got too many logins in a short"
            + " time. Try again in a minute.";
    static final String MOJANG_DOWN = "Couldn't log in to the AlpakaAddons API: Mojang's login server can't be reached"
            + " right now. Try again in a moment.";

    /** What Mojang's refusal means for the player, and what is kept so Mojang isn't asked in vain. */
    private static MojangRefused mojangRefused(Exception e, String accessToken) {
        if (e instanceof com.mojang.authlib.exceptions.InvalidCredentialsException) {
            rejectedAccessToken = accessToken;
            return new MojangRefused(SESSION_EXPIRED);
        }
        if (String.valueOf(e.getMessage()).contains("429")) {
            mojangBusyUntil = System.currentTimeMillis() + MOJANG_BUSY_MS;
            return new MojangRefused(MOJANG_BUSY);
        }
        if (e instanceof com.mojang.authlib.exceptions.AuthenticationUnavailableException) return new MojangRefused(MOJANG_DOWN);
        return new MojangRefused("Couldn't log in to the AlpakaAddons API: Mojang refused (" + e.getMessage()
                + "). Is the game logged in to a Microsoft account?");
    }

    // ---- The login on disk ----------------------------------------------------------------------

    /** The account the token belongs to, and the login read from disk once. */
    private static volatile String tokenUuid;
    private static boolean savedRead;

    /** A login kept for the next game: whose it is, the token, and until when it is used. */
    private record Saved(String uuid, String token, long until) {}

    private static java.io.File savedFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("alpaka-api-login.json").toFile();
    }

    /** The game's account, as the API names it: its UUID without dashes. */
    static String currentUuid() {
        User user = Minecraft.getInstance().getUser();
        return user == null || user.getProfileId() == null ? null : user.getProfileId().toString().replace("-", "");
    }

    private static Saved readSaved() {
        try {
            String json = net.alpaka.addons.config.AtomicJsonFile.read(savedFile());
            if (json == null) return null;
            JsonElement saved = JsonParser.parseString(json);
            String uuid = Json.str(saved, "uuid");
            String t = Json.str(saved, "token");
            long until = (long) Json.num(saved, "until", 0);
            return uuid == null || t == null ? null : new Saved(uuid, t, until);
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private static void save(Saved saved) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("uuid", saved.uuid());
        json.addProperty("token", saved.token());
        json.addProperty("until", saved.until());
        try {
            net.alpaka.addons.config.AtomicJsonFile.write(savedFile(), json.toString());
        } catch (java.io.IOException e) {
            AlpakaAddons.LOGGER.debug("AlpakaAddons API: couldn't keep the login: {}", e.toString());
        }
    }

    private static String challenge() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v1/auth/challenge"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = sendLogin(request);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new Refused(0, "The AlpakaAddons API can't be reached right now.");
        }
        JsonElement body = parse(response.body());
        String challenge = Json.str(body, "challenge");
        if (response.statusCode() != 200 || challenge == null || !challenge.matches("[0-9a-f]{40}")) {
            throw new Refused(response.statusCode(), reason(response.statusCode(), body));
        }
        return challenge;
    }

    private static CompletableFuture<JsonElement> send(String path, String bearer) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                // A profile is a megabyte or two of text, a third of that packed.
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        return client().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).thenApply(response -> {
            JsonElement body = parse(text(response));
            if (onTheWay(response.statusCode(), body)) {
                throw new Refused(response.statusCode(), onTheWayReason(response.statusCode()), true);
            }
            if (response.statusCode() == 404) throw new ProfileApi.NotFound(notFound(body));
            if (response.statusCode() != 200 || !Json.bool(body, "success")) {
                throw new Refused(response.statusCode(), reason(response.statusCode(), body));
            }
            return body;
        });
    }

    /** The answer's text, unpacked if it came packed. */
    private static String text(HttpResponse<byte[]> response) {
        byte[] bytes = response.body();
        boolean gzip = response.headers().firstValue("Content-Encoding").map(e -> e.contains("gzip")).orElse(false);
        if (gzip) {
            try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
                bytes = in.readAllBytes();
            } catch (java.io.IOException e) {
                return "";
            }
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String notFound(JsonElement body) {
        String cause = Json.str(body, "cause");
        return cause == null ? "Not found." : cause + ".";
    }

    private static JsonElement parse(String body) {
        try {
            return JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The API's or Hypixel's reason, in words for the player. */
    static String reason(int status, JsonElement body) {
        if (onTheWay(status, body)) return onTheWayReason(status);
        String cause = Json.str(body, "cause");
        if (cause != null && cause.contains("Hypixel key")) {
            return "The AlpakaAddons API can't reach Hypixel right now: its API key isn't working.";
        }
        if (cause != null && cause.startsWith("Too many")) return "Too many profiles opened in a short time. Try again in a few minutes.";
        if (status == 429 || status == 503) return "The AlpakaAddons API is busy. Try again in a minute.";
        if (cause != null && cause.startsWith("Hypixel")) return cause + ". Try again in a minute.";
        return "The AlpakaAddons API answered " + status + (cause != null ? ": " + cause : ".");
    }
}
