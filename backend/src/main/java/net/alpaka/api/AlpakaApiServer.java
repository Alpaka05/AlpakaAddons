package net.alpaka.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Starts the API: an HTTP server on the address and port given, behind the Cloudflare tunnel of
 * api.alpaka.yt, which is its only way in.
 *
 * Configured from the environment:
 * <ul>
 *   <li>{@code ALPAKA_API_BIND} and {@code ALPAKA_API_PORT}, default 127.0.0.1:8787;</li>
 *   <li>{@code ALPAKA_HYPIXEL_KEY_FILE}, the file holding the Hypixel API key, default
 *       {@code ~/.config/alpaka-api/hypixel.key}. The key is read from there and never logged.</li>
 * </ul>
 */
public final class AlpakaApiServer {
    private AlpakaApiServer() {}

    private static final String USER_AGENT = "AlpakaAddons-API/1.0 (+https://github.com/Alpaka05/AlpakaAddons)";
    /** Hypixel's biggest profiles answers are a few MB; anything far beyond is no answer to keep. */
    private static final int MAX_UPSTREAM_BYTES = 16 * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{32,36})\"");

    public static void main(String[] args) throws IOException {
        String bind = env("ALPAKA_API_BIND", "127.0.0.1");
        int port = Integer.parseInt(env("ALPAKA_API_PORT", "8787"));
        Path keyFile = Path.of(env("ALPAKA_HYPIXEL_KEY_FILE", System.getProperty("user.home") + "/.config/alpaka-api/hypixel.key"));
        String key = Files.readString(keyFile).trim();
        if (key.isEmpty()) throw new IllegalStateException("No Hypixel API key in " + keyFile);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Api api = new Api(mojang(client), accounts(client), hypixel(client, key), System::currentTimeMillis);

        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 64);
        server.createContext("/", exchange -> serve(api, exchange));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("AlpakaAddons API listening on " + bind + ":" + port);
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static void serve(Api api, HttpExchange exchange) throws IOException {
        try (exchange) {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (!v.isEmpty()) headers.put(k.toLowerCase(Locale.ROOT), v.get(0));
            });
            // Everything arrives through the tunnel from Cloudflare, which names the real address.
            String ip = headers.getOrDefault("cf-connecting-ip", exchange.getRemoteAddress().getAddress().getHostAddress());
            Api.Response response;
            try {
                response = api.handle(new Api.Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getRawQuery(), headers, ip));
            } catch (RuntimeException e) {
                System.err.println("Request failed: " + e);
                response = Api.Response.error(500, "Something went wrong");
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            // A profile is a megabyte or two of text that packs to a third; the mod asks for it packed.
            if (body.length >= GZIP_FROM_BYTES && headers.getOrDefault("accept-encoding", "").contains("gzip")) {
                body = gzip(body);
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            }
            exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            // Answers are a player's own lookups, so Cloudflare keeps none of them.
            exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
            response.headers().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    /** Answers smaller than this aren't worth packing. */
    private static final int GZIP_FROM_BYTES = 1024;

    private static byte[] gzip(byte[] body) throws IOException {
        java.io.ByteArrayOutputStream packed = new java.io.ByteArrayOutputStream(body.length / 3 + 64);
        try (java.util.zip.GZIPOutputStream out = new java.util.zip.GZIPOutputStream(packed)) {
            out.write(body);
        }
        return packed.toByteArray();
    }

    /**
     * Mojang's account lookup: a name's UUID, then the account's name as it is written and its
     * skin. Neither needs a login.
     */
    private static Api.Accounts accounts(HttpClient client) {
        return nameOrUuid -> {
            String uuid = Ids.uuid(nameOrUuid);
            if (uuid == null) {
                HttpResponse<String> byName = send(client, HttpRequest.newBuilder(URI.create(
                                "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(nameOrUuid, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(10)).header("User-Agent", USER_AGENT).GET().build());
                if (byName.statusCode() == 404 || byName.statusCode() == 204) return null;
                if (byName.statusCode() != 200) throw new IOException("Mojang answered " + byName.statusCode());
                uuid = Ids.uuid(string(Json.parse(byName.body()), "id"));
                if (uuid == null) return null;
            }
            HttpResponse<String> profile = send(client, HttpRequest.newBuilder(URI.create(
                            "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", USER_AGENT).GET().build());
            if (profile.statusCode() == 404 || profile.statusCode() == 204) return null;
            if (profile.statusCode() != 200) throw new IOException("Mojang answered " + profile.statusCode());
            Object root;
            try {
                root = Json.parse(profile.body());
            } catch (IllegalArgumentException e) {
                throw new IOException("Mojang's answer couldn't be read");
            }
            return new Api.Accounts.Account(uuid, string(root, "name"), skin(root));
        };
    }

    private static String string(Object object, String key) {
        return object instanceof Map<?, ?> map && map.get(key) instanceof String s ? s : null;
    }

    /**
     * The hash of the account's skin texture: the end of the skin's address in the base64 "textures"
     * property of Mojang's profile. Null for an account on a default skin.
     */
    static String skin(Object profile) {
        if (!(profile instanceof Map<?, ?> map) || !(map.get("properties") instanceof java.util.List<?> properties)) return null;
        for (Object p : properties) {
            if (!"textures".equals(string(p, "name")) || string(p, "value") == null) continue;
            try {
                Object textures = Json.parse(new String(java.util.Base64.getDecoder().decode(string(p, "value")), StandardCharsets.UTF_8));
                Object skin = textures instanceof Map<?, ?> t && t.get("textures") instanceof Map<?, ?> kinds ? kinds.get("SKIN") : null;
                String url = string(skin, "url");
                if (url == null) return null;
                String hash = url.substring(url.lastIndexOf('/') + 1);
                return hash.matches("[0-9a-f]{20,80}") ? hash : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private static Api.SessionCheck mojang(HttpClient client) {
        return (username, serverId) -> {
            URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8));
            HttpResponse<String> response = send(client, HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT).GET().build());
            if (response.statusCode() != 200) return null;
            Matcher m = ID.matcher(response.body());
            return m.find() ? m.group(1) : null;
        };
    }

    private static Api.Upstream hypixel(HttpClient client, String key) {
        return pathAndQuery -> {
            HttpResponse<java.io.InputStream> response = sendStream(client, HttpRequest.newBuilder(URI.create("https://api.hypixel.net" + pathAndQuery))
                    .timeout(Duration.ofSeconds(20)).header("API-Key", key).header("User-Agent", USER_AGENT).GET().build());
            int remaining = (int) number(response, "ratelimit-remaining", -1);
            long reset = number(response, "ratelimit-reset", 0);
            byte[] bytes;
            try (java.io.InputStream in = response.body()) {
                bytes = in.readNBytes(MAX_UPSTREAM_BYTES + 1);
            }
            if (bytes.length > MAX_UPSTREAM_BYTES) throw new IOException("Hypixel's answer is over " + MAX_UPSTREAM_BYTES + " bytes");
            return new Api.Upstream.Result(response.statusCode(), new String(bytes, StandardCharsets.UTF_8), remaining, reset);
        };
    }

    private static long number(HttpResponse<?> response, String header, long fallback) {
        try {
            return response.headers().firstValue(header).map(Long::parseLong).orElse(fallback);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static HttpResponse<java.io.InputStream> sendStream(HttpClient client, HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
