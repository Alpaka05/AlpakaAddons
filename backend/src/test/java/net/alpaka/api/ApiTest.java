package net.alpaka.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTest {
    private static final String PLAYER = "8f16f5a695d3468392aed51c1a5bb037";
    private static final String OTHER = "0123456789abcdef0123456789abcdef";

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<String> asked = new ArrayList<>();
    private int status = 200;
    private int remaining = 299;

    private final Api api = new Api(
            (username, serverId) -> username.equals("Alpakaa") && serverId.equals("abc123") ? PLAYER : null,
            path -> {
                asked.add(path);
                return new Api.Upstream.Result(status, "{\"success\":" + (status == 200) + ",\"path\":\"" + path + "\"}", remaining, 120);
            },
            now::get);

    private Api.Response get(String path, Map<String, String> headers) {
        Map<String, String> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        return api.handle(new Api.Request("GET", path, lower, "1.2.3.4"));
    }

    private String login() {
        Api.Response r = get("/v1/auth", Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", "abc123"));
        assertEquals(200, r.status(), r.body());
        String body = r.body();
        int at = body.indexOf("\"token\":\"") + 9;
        return body.substring(at, body.indexOf('"', at));
    }

    @Test
    void aLoginMojangConfirmsGivesAToken() {
        assertNotNull(login());
        assertEquals(401, get("/v1/auth", Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", "def456")).status());
        assertEquals(400, get("/v1/auth", Map.of("X-Minecraft-Username", "no spaces allowed", "X-Minecraft-Server", "abc123")).status());
    }

    @Test
    void nothingWithoutAToken() {
        assertEquals(401, get("/v1/skyblock/profiles/" + PLAYER, Map.of()).status());
        assertEquals(401, get("/v1/skyblock/profiles/" + PLAYER, Map.of("Authorization", "Bearer made-up")).status());
        assertTrue(asked.isEmpty());
    }

    @Test
    void profilesMuseumAndGardenGoToHypixelOnceAndAreCached() {
        String token = login();
        Map<String, String> auth = Map.of("Authorization", "Bearer " + token);
        Api.Response first = get("/v1/skyblock/profiles/8f16f5a6-95d3-4683-92ae-d51c1a5bb037", auth);
        assertEquals(200, first.status());
        assertEquals("MISS", first.headers().get("X-Cache"));
        Api.Response again = get("/v1/skyblock/profiles/" + PLAYER, auth);
        assertEquals("HIT", again.headers().get("X-Cache"));
        assertEquals(List.of("/v2/skyblock/profiles?uuid=" + PLAYER), asked);

        get("/v1/skyblock/museum/" + OTHER, auth);
        get("/v1/skyblock/garden/" + OTHER, auth);
        assertEquals("/v2/skyblock/museum?profile=" + OTHER, asked.get(1));
        assertEquals("/v2/skyblock/garden?profile=" + OTHER, asked.get(2));

        // After five minutes the profile is asked for again.
        now.addAndGet(5 * 60_000L + 1);
        get("/v1/skyblock/profiles/" + PLAYER, auth);
        assertEquals(4, asked.size());
    }

    @Test
    void onlyTheThreeKindsAndOnlyUuids() {
        Map<String, String> auth = Map.of("Authorization", "Bearer " + login());
        assertEquals(404, get("/v1/skyblock/auctions/" + PLAYER, auth).status());
        assertEquals(400, get("/v1/skyblock/profiles/Alpakaa", auth).status());
        assertEquals(400, get("/v1/skyblock/profiles/" + PLAYER + "&key=x", auth).status());
        assertEquals(404, get("/v2/skyblock/profiles", auth).status());
        assertTrue(asked.isEmpty());
    }

    @Test
    void aPlayerCantSpendTheKey() {
        Map<String, String> auth = Map.of("Authorization", "Bearer " + login());
        for (int i = 0; i < 20; i++) {
            assertEquals(200, get("/v1/skyblock/profiles/" + String.format("%032x", i), auth).status());
        }
        Api.Response refused = get("/v1/skyblock/profiles/" + String.format("%032x", 99), auth);
        assertEquals(429, refused.status());
        assertNotNull(refused.headers().get("Retry-After"));
        assertEquals(20, asked.size());
        // What is already cached still comes.
        assertEquals(200, get("/v1/skyblock/profiles/" + String.format("%032x", 3), auth).status());
    }

    @Test
    void theKeysLastRequestsAreKept() {
        Map<String, String> auth = Map.of("Authorization", "Bearer " + login());
        remaining = Api.KEY_RESERVE - 1;
        get("/v1/skyblock/profiles/" + PLAYER, auth);
        Api.Response busy = get("/v1/skyblock/profiles/" + OTHER, auth);
        assertEquals(503, busy.status());
        assertEquals(1, asked.size());
        // Once Hypixel's window has passed, it goes on.
        now.addAndGet(121_000L);
        remaining = 299;
        assertEquals(200, get("/v1/skyblock/profiles/" + OTHER, auth).status());
    }

    @Test
    void hypixelsRefusalsAreNotCached() {
        Map<String, String> auth = Map.of("Authorization", "Bearer " + login());
        status = 429;
        assertEquals(503, get("/v1/skyblock/profiles/" + PLAYER, auth).status());
        status = 403;
        assertEquals(503, get("/v1/skyblock/profiles/" + PLAYER, auth).status());
        status = 200;
        assertEquals("MISS", get("/v1/skyblock/profiles/" + PLAYER, auth).headers().get("X-Cache"));
    }

    @Test
    void loginsPerAddressAreLimited() {
        for (int i = 0; i < 10; i++) login();
        assertEquals(429, get("/v1/auth", Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", "abc123")).status());
    }

    @Test
    void theRootSaysWhatItIs() {
        Api.Response root = get("/", Map.of());
        assertEquals(200, root.status());
        assertTrue(root.body().contains("Not affiliated with Hypixel"));
        assertEquals(405, api.handle(new Api.Request("POST", "/v1/auth", Map.of(), "1.2.3.4")).status());
    }

    @Test
    void tokensRunOut() throws IOException {
        Map<String, String> auth = Map.of("Authorization", "Bearer " + login());
        now.addAndGet(Sessions.LIFETIME_MS + 1);
        assertEquals(401, get("/v1/skyblock/profiles/" + PLAYER, auth).status());
    }
}
