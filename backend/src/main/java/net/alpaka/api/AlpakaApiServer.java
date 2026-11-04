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
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{32,36})\"");

    public static void main(String[] args) throws IOException {
        String bind = env("ALPAKA_API_BIND", "127.0.0.1");
        int port = Integer.parseInt(env("ALPAKA_API_PORT", "8787"));
        Path keyFile = Path.of(env("ALPAKA_HYPIXEL_KEY_FILE", System.getProperty("user.home") + "/.config/alpaka-api/hypixel.key"));
        String key = Files.readString(keyFile).trim();
        if (key.isEmpty()) throw new IllegalStateException("No Hypixel API key in " + keyFile);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Api api = new Api(mojang(client), hypixel(client, key), System::currentTimeMillis);

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
                response = api.handle(new Api.Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, ip));
            } catch (RuntimeException e) {
                System.err.println("Request failed: " + e);
                response = Api.Response.error(500, "Something went wrong");
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
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
            HttpResponse<String> response = send(client, HttpRequest.newBuilder(URI.create("https://api.hypixel.net" + pathAndQuery))
                    .timeout(Duration.ofSeconds(20)).header("API-Key", key).header("User-Agent", USER_AGENT).GET().build());
            int remaining = (int) number(response, "ratelimit-remaining", -1);
            long reset = number(response, "ratelimit-reset", 0);
            return new Api.Upstream.Result(response.statusCode(), response.body(), remaining, reset);
        };
    }

    private static long number(HttpResponse<?> response, String header, long fallback) {
        try {
            return response.headers().firstValue(header).map(Long::parseLong).orElse(fallback);
        } catch (NumberFormatException e) {
            return fallback;
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
