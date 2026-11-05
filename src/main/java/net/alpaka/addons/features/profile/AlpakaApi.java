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
 * The AlpakaAddons API at api.alpaka.yt: Hypixel's API through a server of the mod's own, for what
 * Elite doesn't hand out, such as the Rift's inventories. Hypixel's API needs a key that may never
 * ship in a mod, so the key stays on that server (see {@code backend/} in the repository).
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
     * What the dashboard shows of a player from Hypixel: their SkyBlock profiles in the shape of
     * Hypixel's {@code /v2/skyblock/profiles}, with only the player's own member data and of that only
     * the fields the dashboard draws (the Rift's inventories).
     */
    static CompletableFuture<JsonElement> dashboard(String uuid) {
        return get("/v1/dashboard/" + uuid.replace("-", ""), true);
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
                .GET()
                .build();
        return client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            JsonElement body = parse(response.body());
            if (response.statusCode() != 200 || !Json.bool(body, "success")) {
                throw new Refused(response.statusCode(), reason(response.statusCode(), body));
            }
            return body;
        });
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
        if (status == 429 || status == 503) return "The AlpakaAddons API is busy. Try again in a minute.";
        return "The AlpakaAddons API answered " + status + (cause != null ? ": " + cause : ".");
    }
}
