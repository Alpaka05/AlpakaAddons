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

/**
 * The AlpakaAddons API at api.alpaka.yt: where the Profile Dashboard gets a profile from. It asks
 * Hypixel's API, which needs a key that may never ship in a mod, so the key stays on that server
 * (see {@code backend/} in the repository) and the server hands out what the dashboard shows.
 *
 * Logging in works as joining a server does: the API hands out a one-time challenge, the game tells
 * Mojang's session server that the player joins a server with that id, and the API asks Mojang
 * whether they did. The player's access token
 * only ever goes to Mojang, never to the API. The API then hands out a token of its own for half a
 * day. Only used from the Profile Dashboard, while Network Features is on, and only for a profile
 * someone opened.
 */
final class AlpakaApi {
    private AlpakaApi() {}

    static final String BASE = "https://api.alpaka.yt";

    /** Thrown for an answer the API gave on purpose, with its reason for the player. */
    static final class Refused extends RuntimeException {
        final int status;

        Refused(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static HttpClient client;
    private static volatile String token;
    private static volatile long tokenUntil;
    private static CompletableFuture<String> login;

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

    private static CompletableFuture<JsonElement> get(String path, boolean mayLogInAgain) {
        return token().thenCompose(t -> send(path, t)).exceptionallyCompose(e -> {
            Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
            if (mayLogInAgain && cause instanceof Refused r && r.status == 401) {
                // The server was restarted and forgot the login: once more with a new one.
                forgetToken();
                return get(path, false);
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    private static synchronized void forgetToken() {
        token = null;
        tokenUntil = 0L;
    }

    /** The API's token, logging in first when there is none or it is about to run out. */
    private static synchronized CompletableFuture<String> token() {
        String known = token;
        if (known != null && System.currentTimeMillis() < tokenUntil) return CompletableFuture.completedFuture(known);
        if (login != null && !login.isDone()) return login;
        login = CompletableFuture.supplyAsync(AlpakaApi::logIn);
        return login;
    }

    private static String logIn() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        // The server id comes from the API, so a login can't be made from any other server's join.
        String serverId = challenge();
        try {
            mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.warn("AlpakaAddons API: Mojang refused the login: {}", e.toString());
            throw new Refused(401, "Couldn't log in to the AlpakaAddons API: Mojang refused. Is the game logged in to a Microsoft account?");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v1/auth"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .header("X-Minecraft-Username", user.getName())
                .header("X-Minecraft-Server", serverId)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new Refused(0, "The AlpakaAddons API can't be reached right now.");
        }
        JsonElement body = parse(response.body());
        String t = Json.str(body, "token");
        if (response.statusCode() != 200 || t == null) throw new Refused(response.statusCode(), reason(response.statusCode(), body));
        long seconds = (long) Json.num(body, "expiresIn", 3600);
        synchronized (AlpakaApi.class) {
            token = t;
            // A few minutes early, so a token never runs out on the way.
            tokenUntil = System.currentTimeMillis() + Math.max(60, seconds - 300) * 1000L;
        }
        return t;
    }

    private static String challenge() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/v1/auth/challenge"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString());
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
    private static String reason(int status, JsonElement body) {
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
