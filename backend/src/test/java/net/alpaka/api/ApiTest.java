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
    private static final String LEFT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String APPLE = "11111111111111111111111111111111";
    private static final String KIWI = "22222222222222222222222222222222";

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<String> asked = new ArrayList<>();
    /** Server ids Mojang was told a player joined, by username. */
    private final Map<String, String> joined = new HashMap<>();
    private int status = 200;
    private int gardenStatus = 200;
    private int remaining = 299;
    private boolean mojangDown;
    private int lookups;

    private final Api api = new Api(
            (username, serverId) -> serverId.equals(joined.get(username)) ? PLAYER : null,
            nameOrUuid -> {
                lookups++;
                if (mojangDown) throw new IOException("down");
                if (nameOrUuid.equalsIgnoreCase("Alpakaa") || nameOrUuid.equals(PLAYER)) return new Api.Accounts.Account(PLAYER, "Alpakaa", "9c04");
                if (nameOrUuid.equalsIgnoreCase("Nobody")) return null;
                return Ids.uuid(nameOrUuid) == null ? null : new Api.Accounts.Account(nameOrUuid, "Player" + nameOrUuid.substring(0, 4), null);
            },
            path -> {
                asked.add(path);
                if (path.startsWith("/v2/skyblock/garden")) {
                    return new Api.Upstream.Result(gardenStatus, gardenStatus == 200 ? GARDEN : "{\"success\":false}", remaining, 120);
                }
                if (path.startsWith("/v2/skyblock/museum")) return new Api.Upstream.Result(status, MUSEUM, remaining, 120);
                String uuid = path.substring(path.indexOf("uuid=") + 5);
                return new Api.Upstream.Result(status, uuid.equals(LEFT) ? "{\"success\":true,\"profiles\":null}" : hypixelProfiles(uuid), remaining, 120);
            },
            now::get);

    /**
     * Hypixel's answer shape: a co-op with the player, someone else and someone who left, an
     * Ironman profile, and one the player left; with lots the dashboard doesn't show.
     */
    private static String hypixelProfiles(String uuid) {
        // A profile id of the player's own, as no two co-ops share one.
        String apple = uuid.equals(PLAYER) ? "11111111-1111-1111-1111-111111111111" : Ids.dashed(new StringBuilder(uuid).reverse().toString());
        return "{\"success\":true,\"profiles\":["
                + "{\"profile_id\":\"" + apple + "\",\"cute_name\":\"Apple\",\"selected\":true,"
                + "\"banking\":{\"balance\":123.5,\"transactions\":[{\"amount\":1}]},\"members\":{"
                + "\"" + uuid + "\":{\"player_data\":{\"experience\":{\"SKILL_FARMING\":5},\"visited_zones\":[\"hub\"],"
                + "\"unlocked_coll_tiers\":[\"WHEAT_3\",\"INK_SACK:3_2\"],\"crafted_generators\":[\"WHEAT_1\"]},"
                + "\"currencies\":{\"coin_purse\":5},\"inventory\":{\"inv_contents\":{\"type\":0,\"data\":\"AAA\"}},"
                + "\"rift\":{\"village_plaza\":{\"murder\":1},\"inventory\":{\"ender_chest_contents\":{\"type\":0,\"data\":\"BBB\"}}},"
                + "\"quests\":{\"harp_quest\":{\"claimed\":true},\"trapper_quest\":{\"pelt_count\":4,\"last_task_time\":9}}},"
                + "\"" + COOP + "\":{\"player_data\":{\"unlocked_coll_tiers\":[\"WHEAT_7\"],\"crafted_generators\":[\"WHEAT_2\",\"WHEAT_1\"]},"
                + "\"currencies\":{\"coin_purse\":9}},"
                + "\"" + LEFT + "\":{\"profile\":{\"deletion_notice\":{\"timestamp\":1}},\"player_data\":{\"crafted_generators\":[\"COW_1\"]}}}},"
                + "{\"profile_id\":\"22222222-2222-2222-2222-222222222222\",\"cute_name\":\"Kiwi\",\"game_mode\":\"ironman\",\"selected\":false,"
                + "\"members\":{\"" + uuid + "\":{\"currencies\":{\"coin_purse\":1}}}},"
                + "{\"profile_id\":\"33333333-3333-3333-3333-333333333333\",\"cute_name\":\"Lime\","
                + "\"members\":{\"" + uuid + "\":{\"profile\":{\"deletion_notice\":{\"timestamp\":2}}}}}]}";
    }

    private static final String GARDEN = "{\"success\":true,\"garden\":{\"uuid\":\"x\",\"garden_experience\":150,"
            + "\"unlocked_plots_ids\":[\"beginner_1\"],\"selected_barn_skin\":\"barn\","
            + "\"commission_data\":{\"visits\":{\"jerry\":3},\"total_completed\":7,\"unique_npcs_served\":2}}}";

    private static final String MUSEUM = "{\"success\":true,\"members\":{"
            + "\"" + PLAYER + "\":{\"value\":9,\"appraisal\":true,\"items\":{\"SWORD\":{\"donated_time\":1,\"items\":{\"type\":0,\"data\":\"MMM\"}}},\"special\":[]},"
            + "\"" + COOP + "\":{\"value\":1,\"items\":{\"BOW\":{\"items\":{\"type\":0,\"data\":\"NNN\"}}}}}}";

    private Api.Response get(String pathAndQuery, Map<String, String> headers) {
        Map<String, String> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        int q = pathAndQuery.indexOf('?');
        return api.handle(new Api.Request("GET", q < 0 ? pathAndQuery : pathAndQuery.substring(0, q),
                q < 0 ? null : pathAndQuery.substring(q + 1), lower, "1.2.3.4"));
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
        assertEquals(401, get("/v1/museum/" + PLAYER + "/" + APPLE, Map.of()).status());
        assertTrue(asked.isEmpty());
        assertEquals(0, lookups);
    }

    @Test
    void theAnswerHasThePlayerTheirProfilesAndOneProfilesData() {
        Api.Response r = get("/v1/dashboard/alpakaa", auth());
        assertEquals(200, r.status(), r.body());
        String body = r.body();
        // Who it is, as Mojang writes the name, with the skin for the face.
        assertTrue(body.contains("\"player\":{\"uuid\":\"" + PLAYER + "\",\"name\":\"Alpakaa\",\"skin\":\"9c04\"}"), body);
        // The profiles the player is on, with how many are on each: not the one they left.
        assertTrue(body.contains("\"profiles\":[{\"profile_id\":\"" + APPLE + "\",\"cute_name\":\"Apple\",\"selected\":true,\"members\":2},"
                + "{\"profile_id\":\"" + KIWI + "\",\"cute_name\":\"Kiwi\",\"game_mode\":\"ironman\",\"selected\":false,\"members\":1}]"), body);
        assertFalse(body.contains("Lime"), body);
        // The selected profile's data: the dashboard's fields of the player's own member.
        assertTrue(body.contains("\"profile\":{\"profile_id\":\"" + APPLE + "\""), body);
        assertTrue(body.contains("\"bank\":123.5"), body);
        assertTrue(body.contains("\"experience\":{\"SKILL_FARMING\":5}"), body);
        assertTrue(body.contains("\"currencies\":{\"coin_purse\":5}"), body);
        assertTrue(body.contains("\"inv_contents\":{\"type\":0,\"data\":\"AAA\"}"), body);
        assertTrue(body.contains("\"ender_chest_contents\":{\"type\":0,\"data\":\"BBB\"}"), body);
        assertTrue(body.contains("\"trapper_quest\":{\"pelt_count\":4}"), body);
        // What the co-op shares: the highest tier anyone unlocked, and every minion anyone crafted, once.
        assertTrue(body.contains("\"collection_tiers\":{\"WHEAT\":7,\"INK_SACK:3\":2}"), body);
        assertTrue(body.contains("\"crafted_generators\":[\"COW_1\",\"WHEAT_1\",\"WHEAT_2\"]"), body);
        // Not the co-op members themselves, their coins, or what no page shows.
        assertFalse(body.contains(COOP), body);
        assertFalse(body.contains("\"coin_purse\":9"), body);
        assertFalse(body.contains("transactions"), body);
        assertFalse(body.contains("visited_zones"), body);
        assertFalse(body.contains("village_plaza"), body);
        assertFalse(body.contains("harp_quest"), body);
        assertFalse(body.contains("last_task_time"), body);
        // The garden of that profile, cut down the same way.
        assertTrue(body.contains("\"garden\":{\"garden_experience\":150,\"unlocked_plots_ids\":[\"beginner_1\"],"
                + "\"commission_data\":{\"total_completed\":7,\"unique_npcs_served\":2}}"), body);
        assertEquals(List.of("/v2/skyblock/profiles?uuid=" + PLAYER,
                "/v2/skyblock/garden?profile=11111111-1111-1111-1111-111111111111"), asked);
    }

    @Test
    void anotherProfileIsAskedForByItsId() {
        Map<String, String> auth = auth();
        Api.Response r = get("/v1/dashboard/" + PLAYER + "?profile=22222222-2222-2222-2222-222222222222", auth);
        assertEquals(200, r.status(), r.body());
        assertTrue(r.body().contains("\"profile\":{\"profile_id\":\"" + KIWI + "\",\"cute_name\":\"Kiwi\""), r.body());
        assertTrue(r.body().contains("\"coin_purse\":1"), r.body());
        // The other profile comes out of the same answer of Hypixel's; only its garden is new.
        get("/v1/dashboard/" + PLAYER, auth);
        assertEquals(1, asked.stream().filter(p -> p.contains("profiles")).count());
        assertEquals(2, asked.stream().filter(p -> p.contains("garden")).count());
        // An id the player has no profile with falls back to the selected one.
        assertTrue(get("/v1/dashboard/" + PLAYER + "?profile=" + "9".repeat(32), auth).body().contains("\"profile\":{\"profile_id\":\"" + APPLE + "\""));
    }

    @Test
    void aPlayerWithoutSkyBlockGetsNoProfile() {
        Api.Response r = get("/v1/dashboard/" + LEFT, auth());
        assertEquals(200, r.status(), r.body());
        assertTrue(r.body().contains("\"profiles\":[],\"profile\":null,\"garden\":null"), r.body());
        assertEquals(1, asked.size());
    }

    @Test
    void aNameNobodyHasIsNotFound() {
        Map<String, String> auth = auth();
        assertEquals(404, get("/v1/dashboard/Nobody", auth).status());
        // Remembered, so the same typo doesn't ask Mojang again.
        assertEquals(404, get("/v1/dashboard/nobody", auth).status());
        assertEquals(1, lookups);
        assertEquals(400, get("/v1/dashboard/not%20a%20name", auth).status());
        assertEquals(400, get("/v1/dashboard/" + PLAYER + "&key=x", auth).status());
        assertTrue(asked.isEmpty());
    }

    @Test
    void withoutMojangAUuidStillWorksAndANameDoesNot() {
        Map<String, String> auth = auth();
        mojangDown = true;
        assertEquals(502, get("/v1/dashboard/Alpakaa", auth).status());
        Api.Response r = get("/v1/dashboard/" + PLAYER, auth);
        assertEquals(200, r.status(), r.body());
        assertTrue(r.body().contains("\"player\":{\"uuid\":\"" + PLAYER + "\",\"name\":null,\"skin\":null}"), r.body());
        // Not remembered: once Mojang is back, the name is too.
        mojangDown = false;
        assertTrue(get("/v1/dashboard/" + PLAYER, auth).body().contains("\"name\":\"Alpakaa\""));
    }

    @Test
    void answersAreCachedForAnHourAndTheOwnForThreeMinutes() {
        Map<String, String> auth = auth();
        Api.Response first = get("/v1/dashboard/8f16f5a6-95d3-4683-92ae-d51c1a5bb037", auth);
        assertEquals("MISS", first.headers().get("X-Cache"));
        assertEquals(String.valueOf(now.get()), first.headers().get("X-Fetched-At"));
        get("/v1/dashboard/" + OTHER, auth);
        assertEquals(4, asked.size());

        now.addAndGet(Api.OWN_MAX_AGE_MS + 1);
        assertEquals("HIT", get("/v1/dashboard/" + OTHER, auth).headers().get("X-Cache"));
        assertEquals("MISS", get("/v1/dashboard/" + PLAYER, auth).headers().get("X-Cache"));
        assertEquals(6, asked.size());

        now.addAndGet(Api.MAX_AGE_MS);
        assertEquals("MISS", get("/v1/dashboard/" + OTHER, auth).headers().get("X-Cache"));
        assertEquals(8, asked.size());
    }

    @Test
    void refreshAsksAgainButNotAtOnce() {
        Map<String, String> auth = auth();
        get("/v1/dashboard/" + PLAYER, auth);
        get("/v1/dashboard/" + OTHER, auth);
        // Hammering Refresh gets the same answer.
        assertEquals("HIT", get("/v1/dashboard/" + PLAYER + "?fresh=1", auth).headers().get("X-Cache"));
        now.addAndGet(Api.OWN_REFRESH_AGE_MS + 1);
        assertEquals("MISS", get("/v1/dashboard/" + PLAYER + "?fresh=1", auth).headers().get("X-Cache"));
        // Someone else's profile is asked for again less readily.
        assertEquals("HIT", get("/v1/dashboard/" + OTHER + "?fresh=1", auth).headers().get("X-Cache"));
        now.addAndGet(Api.REFRESH_AGE_MS);
        assertEquals("MISS", get("/v1/dashboard/" + OTHER + "?fresh=1", auth).headers().get("X-Cache"));
    }

    @Test
    void aProfileIsShownWithoutItsGarden() {
        Map<String, String> auth = auth();
        gardenStatus = 404;
        Api.Response r = get("/v1/dashboard/" + PLAYER, auth);
        assertEquals(200, r.status(), r.body());
        assertTrue(r.body().contains("\"garden\":null"), r.body());
        // That there is none is remembered like a garden would be.
        gardenStatus = 200;
        assertTrue(get("/v1/dashboard/" + PLAYER, auth).body().contains("\"garden\":null"));
        assertEquals(2, asked.size());
        // A garden Hypixel failed to hand out is asked for again with the next look.
        gardenStatus = 500;
        assertTrue(get("/v1/dashboard/" + OTHER, auth).body().contains("\"garden\":null"));
        gardenStatus = 200;
        assertTrue(get("/v1/dashboard/" + OTHER, auth).body().contains("\"garden\":{"));
    }

    @Test
    void theMuseumIsThePlayersOwnPart() {
        Map<String, String> auth = auth();
        Api.Response r = get("/v1/museum/" + PLAYER + "/" + APPLE, auth);
        assertEquals(200, r.status(), r.body());
        assertTrue(r.body().contains("\"museum\":{\"value\":9,\"appraisal\":true,"
                + "\"items\":{\"SWORD\":{\"donated_time\":1,\"items\":{\"type\":0,\"data\":\"MMM\"}}},\"special\":[]}"), r.body());
        assertFalse(r.body().contains("BOW"), r.body());
        assertEquals(List.of("/v2/skyblock/museum?profile=11111111-1111-1111-1111-111111111111"), asked);
        assertEquals("HIT", get("/v1/museum/" + PLAYER + "/" + APPLE, auth).headers().get("X-Cache"));
        // Someone who donated nothing has an empty museum.
        assertTrue(get("/v1/museum/" + OTHER + "/" + APPLE, auth).body().contains("\"museum\":{}"));
        assertEquals(400, get("/v1/museum/" + PLAYER, auth).status());
        assertEquals(400, get("/v1/museum/Alpakaa/" + APPLE, auth).status());
    }

    @Test
    void nothingButTheDashboard() {
        Map<String, String> auth = auth();
        assertEquals(404, get("/v1/skyblock/profiles/" + PLAYER, auth).status());
        assertEquals(404, get("/v1/skyblock/museum/" + PLAYER, auth).status());
        assertEquals(404, get("/v2/skyblock/profiles", auth).status());
        assertEquals(404, get("/v1/garden/" + APPLE, auth).status());
        assertTrue(asked.isEmpty());
    }

    @Test
    void aPlayerCantSpendTheKey() {
        Map<String, String> auth = auth();
        // A profile not seen before takes two requests to Hypixel; forty-five are a player's in five minutes.
        for (int i = 0; i < 22; i++) {
            assertEquals(200, get("/v1/dashboard/" + String.format("%032x", i), auth).status());
        }
        assertEquals(44, asked.size());
        // The last one goes to the profile, which is then shown without its garden.
        Api.Response last = get("/v1/dashboard/" + String.format("%032x", 50), auth);
        assertEquals(200, last.status());
        assertTrue(last.body().contains("\"garden\":null"));
        Api.Response refused = get("/v1/dashboard/" + String.format("%032x", 99), auth);
        assertEquals(429, refused.status());
        assertNotNull(refused.headers().get("Retry-After"));
        assertEquals(45, asked.size());
        // What is already cached still comes.
        assertEquals(200, get("/v1/dashboard/" + String.format("%032x", 3), auth).status());
    }

    @Test
    void theKeysLastRequestsAreKept() {
        Map<String, String> auth = auth();
        remaining = Api.KEY_RESERVE - 1;
        assertEquals(200, get("/v1/dashboard/" + PLAYER, auth).status());
        Api.Response busy = get("/v1/dashboard/" + OTHER, auth);
        assertEquals(503, busy.status());
        assertNotNull(busy.headers().get("Retry-After"));
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
        Api.Response key = get("/v1/dashboard/" + PLAYER, auth);
        assertEquals(503, key.status());
        assertTrue(key.body().contains("Hypixel key"), key.body());
        status = 500;
        assertEquals(502, get("/v1/dashboard/" + PLAYER, auth).status());
        assertEquals(502, get("/v1/museum/" + PLAYER + "/" + APPLE, auth).status());
        status = 200;
        assertEquals("MISS", get("/v1/dashboard/" + PLAYER, auth).headers().get("X-Cache"));
        assertEquals("MISS", get("/v1/museum/" + PLAYER + "/" + APPLE, auth).headers().get("X-Cache"));
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
    void aSkinIsReadFromMojangsProfile() {
        String textures = java.util.Base64.getEncoder().encodeToString(
                "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/9c041a2c1d72eadd5477f1a2fa859919e1f78201\"}}}".getBytes());
        Object profile = Json.parse("{\"id\":\"x\",\"name\":\"Alpakaa\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + textures + "\"}]}");
        assertEquals("9c041a2c1d72eadd5477f1a2fa859919e1f78201", AlpakaApiServer.skin(profile));
        assertNull(AlpakaApiServer.skin(Json.parse("{\"id\":\"x\",\"properties\":[]}")));
        assertNull(AlpakaApiServer.skin(Json.parse("{\"properties\":[{\"name\":\"textures\",\"value\":\"not base64!\"}]}")));
    }

    @Test
    void jsonGoesThroughUnchanged() {
        String text = "{\"a\":[1,-2.5e3,true,false,null],\"b\":\"q\\\"\\\\\\n\\u00e9\",\"c\":{}}";
        assertEquals("{\"a\":[1,-2.5e3,true,false,null],\"b\":\"q\\\"\\\\\\n\u00e9\",\"c\":{}}", Json.write(Json.parse(text)));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[".repeat(100) + "]".repeat(100)));
    }
}
