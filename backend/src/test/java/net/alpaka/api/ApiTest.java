package net.alpaka.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTest {
    private static final String PLAYER = "8f16f5a695d3468392aed51c1a5bb037";
    private static final String OTHER = "0123456789abcdef0123456789abcdef";
    private static final String COOP = "fedcba9876543210fedcba9876543210";

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<String> asked = new ArrayList<>();
    /** Server ids Mojang was told a player joined, by username. */
    private final Map<String, String> joined = new HashMap<>();
    private int status = 200;
    private int remaining = 299;

    private final Api api = new Api(
            (username, serverId) -> serverId.equals(joined.get(username)) ? PLAYER : null,
            path -> {
                asked.add(path);
                String uuid = path.substring(path.indexOf("uuid=") + 5);
                return new Api.Upstream.Result(status, hypixelProfiles(uuid), remaining, 120);
            },
            now::get);

    /** Hypixel's answer shape: one co-op with the player and someone else, and lots the dashboard doesn't show. */
    private static String hypixelProfiles(String uuid) {
        return "{\"success\":true,\"profiles\":[{\"profile_id\":\"p1\",\"cute_name\":\"Apple\",\"selected\":true,"
                + "\"banking\":{\"balance\":123.5},\"members\":{"
                + "\"" + uuid + "\":{\"coin_purse\":5,\"rift\":{\"motes\":1,\"inventory\":{\"inv_contents\":{\"type\":0,\"data\":\"AAA\"},"
                + "\"ender_chest_contents\":{\"type\":0,\"data\":\"BBB\"}}},\"inventory\":{\"inv_contents\":{\"data\":\"secret\"}}},"
                + "\"" + COOP + "\":{\"coin_purse\":9}}}]}";
    }

    private Api.Response get(String path, Map<String, String> headers) {
        Map<String, String> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        return api.handle(new Api.Request("GET", path, lower, "1.2.3.4"));
    }

    private static String field(String body, String name) {
        int at = body.indexOf("\"" + name + "\":\"") + name.length() + 4;
        return body.substring(at, body.indexOf('"', at));
    }

    /** As the mod does it: a challenge, Mojang told the player joins it, then the login. */
    private String login() {
        Api.Response c = get("/v1/auth/challenge", Map.of());
        assertEquals(200, c.status(), c.body());
        String challenge = field(c.body(), "challenge");
        joined.put("Alpakaa", challenge);
        Api.Response r = get("/v1/auth", Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", challenge));
        assertEquals(200, r.status(), r.body());
        return field(r.body(), "token");
    }

    private Map<String, String> auth() {
        return Map.of("Authorization", "Bearer " + login());
    }

    @Test
    void aLoginMojangConfirmsGivesAToken() {
        assertNotNull(login());
        assertEquals(400, get("/v1/auth", Map.of("X-Minecraft-Username", "no spaces allowed", "X-Minecraft-Server", "ab")).status());
    }

    @Test
    void onlyAChallengeOfOursLogsIn() {
        // Another server's hash, which Mojang does confirm: its owner must not log in with it.
        String foreign = "0f".repeat(20);
        joined.put("Alpakaa", foreign);
        assertEquals(401, get("/v1/auth", Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", foreign)).status());
        // Signed hashes as vanilla makes them aren't even accepted as ids.
        assertEquals(400, get("/v1/auth", Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", "-7c9d5b0044c130109a5d7b5fb5c317c02b4e28c1")).status());
    }

    @Test
    void aChallengeWorksOnceAndNotForLong() {
        String challenge = field(get("/v1/auth/challenge", Map.of()).body(), "challenge");
        joined.put("Alpakaa", challenge);
        Map<String, String> headers = Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", challenge);
        assertEquals(200, get("/v1/auth", headers).status());
        assertEquals(401, get("/v1/auth", headers).status());

        String late = field(get("/v1/auth/challenge", Map.of()).body(), "challenge");
        joined.put("Alpakaa", late);
        now.addAndGet(Challenges.LIFETIME_MS + 1);
        assertEquals(401, get("/v1/auth", Map.of("X-Minecraft-Username", "Alpakaa", "X-Minecraft-Server", late)).status());
    }

    @Test
    void nothingWithoutAToken() {
        assertEquals(401, get("/v1/dashboard/" + PLAYER, Map.of()).status());
        assertEquals(401, get("/v1/dashboard/" + PLAYER, Map.of("Authorization", "Bearer made-up")).status());
        assertTrue(asked.isEmpty());
    }

    @Test
    void theAnswerHasOnlyWhatTheDashboardShows() {
        Api.Response r = get("/v1/dashboard/" + OTHER, auth());
        assertEquals(200, r.status(), r.body());
        String body = r.body();
        assertTrue(body.contains("\"profile_id\":\"p1\""), body);
        assertTrue(body.contains("\"cute_name\":\"Apple\""), body);
        assertTrue(body.contains("\"inv_contents\":{\"type\":0,\"data\":\"AAA\"}"), body);
        assertTrue(body.contains("\"ender_chest_contents\""), body);
        // Not the bank, not the purse, not the normal inventory, not the co-op member.
        assertFalse(body.contains("banking"), body);
        assertFalse(body.contains("coin_purse"), body);
        assertFalse(body.contains("secret"), body);
        assertFalse(body.contains("motes"), body);
        assertFalse(body.contains(COOP), body);
    }

    @Test
    void aPlayerWithoutSkyBlockGetsNoProfiles() {
        assertEquals("{\"success\":true,\"profiles\":[]}", Dashboard.of("{\"success\":true,\"profiles\":null}", PLAYER));
    }

    @Test
    void answersAreCachedForAnHourAndTheOwnForTenMinutes() {
        Map<String, String> auth = auth();
        Api.Response first = get("/v1/dashboard/8f16f5a6-95d3-4683-92ae-d51c1a5bb037", auth);
        assertEquals("MISS", first.headers().get("X-Cache"));
        get("/v1/dashboard/" + OTHER, auth);
        assertEquals(List.of("/v2/skyblock/profiles?uuid=" + PLAYER, "/v2/skyblock/profiles?uuid=" + OTHER), asked);

        now.addAndGet(Api.OWN_MAX_AGE_MS + 1);
        assertEquals("HIT", get("/v1/dashboard/" + OTHER, auth).headers().get("X-Cache"));
        assertEquals("MISS", get("/v1/dashboard/" + PLAYER, auth).headers().get("X-Cache"));

        now.addAndGet(Api.MAX_AGE_MS);
        assertEquals("MISS", get("/v1/dashboard/" + OTHER, auth).headers().get("X-Cache"));
        assertEquals(4, asked.size());
    }

    @Test
    void nothingButTheDashboard() {
        Map<String, String> auth = auth();
        assertEquals(404, get("/v1/skyblock/profiles/" + PLAYER, auth).status());
        assertEquals(404, get("/v1/skyblock/museum/" + PLAYER, auth).status());
        assertEquals(404, get("/v2/skyblock/profiles", auth).status());
        assertEquals(400, get("/v1/dashboard/Alpakaa", auth).status());
        assertEquals(400, get("/v1/dashboard/" + PLAYER + "&key=x", auth).status());
        assertTrue(asked.isEmpty());
    }

    @Test
    void aPlayerCantSpendTheKey() {
        Map<String, String> auth = auth();
        for (int i = 0; i < 20; i++) {
            assertEquals(200, get("/v1/dashboard/" + String.format("%032x", i), auth).status());
        }
        Api.Response refused = get("/v1/dashboard/" + String.format("%032x", 99), auth);
        assertEquals(429, refused.status());
        assertNotNull(refused.headers().get("Retry-After"));
        assertEquals(20, asked.size());
        // What is already cached still comes.
        assertEquals(200, get("/v1/dashboard/" + String.format("%032x", 3), auth).status());
    }

    @Test
    void theKeysLastRequestsAreKept() {
        Map<String, String> auth = auth();
        remaining = Api.KEY_RESERVE - 1;
        get("/v1/dashboard/" + PLAYER, auth);
        Api.Response busy = get("/v1/dashboard/" + OTHER, auth);
        assertEquals(503, busy.status());
        assertEquals(1, asked.size());
        // Once Hypixel's window has passed, it goes on.
        now.addAndGet(121_000L);
        remaining = 299;
        assertEquals(200, get("/v1/dashboard/" + OTHER, auth).status());
    }

    @Test
    void hypixelsRefusalsAreNotCached() {
        Map<String, String> auth = auth();
        status = 429;
        assertEquals(503, get("/v1/dashboard/" + PLAYER, auth).status());
        status = 403;
        assertEquals(503, get("/v1/dashboard/" + PLAYER, auth).status());
        status = 500;
        assertEquals(502, get("/v1/dashboard/" + PLAYER, auth).status());
        status = 200;
        assertEquals("MISS", get("/v1/dashboard/" + PLAYER, auth).headers().get("X-Cache"));
    }

    @Test
    void loginsPerAddressAreLimited() {
        for (int i = 0; i < 10; i++) login();
        assertEquals(429, get("/v1/auth/challenge", Map.of()).status());
    }

    @Test
    void theRootSaysWhatItIs() {
        Api.Response root = get("/", Map.of());
        assertEquals(200, root.status());
        assertTrue(root.body().contains("Not a public API"));
        assertEquals(405, api.handle(new Api.Request("POST", "/v1/auth", Map.of(), "1.2.3.4")).status());
    }

    @Test
    void tokensRunOut() throws IOException {
        Map<String, String> auth = auth();
        now.addAndGet(Sessions.LIFETIME_MS + 1);
        assertEquals(401, get("/v1/dashboard/" + PLAYER, auth).status());
    }

    @Test
    void theCacheStaysWithinItsSize() {
        ResponseCache cache = new ResponseCache(10, now::get);
        cache.put("a", 200, "12345");
        cache.put("b", 200, "12345");
        cache.get("a", 1000);
        cache.put("c", 200, "123");
        // b was used least recently, so it went; a and c fit.
        assertNull(cache.get("b", 1000));
        assertNotNull(cache.get("a", 1000));
        assertNotNull(cache.get("c", 1000));
        cache.put("huge", 200, "12345678901");
        assertNull(cache.get("huge", 1000));
    }

    @Test
    void jsonGoesThroughUnchanged() {
        String text = "{\"a\":[1,-2.5e3,true,false,null],\"b\":\"q\\\"\\\\\\n\\u00e9\",\"c\":{}}";
        assertEquals("{\"a\":[1,-2.5e3,true,false,null],\"b\":\"q\\\"\\\\\\n\u00e9\",\"c\":{}}", Json.write(Json.parse(text)));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[".repeat(100) + "]".repeat(100)));
    }
}
