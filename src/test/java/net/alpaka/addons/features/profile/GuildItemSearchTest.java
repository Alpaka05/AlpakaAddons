package net.alpaka.addons.features.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GuildItemSearchTest {
    private static final String PLAYER = "6cd5a90b60c84cc692967d02bc64a0ac";
    private static final String PROFILE = "784d40eb3a8f48a8b12263368623872b";
    private static final String OTHER_PROFILE = "784d40eb3a8f48a8b12263368623872c";
    private static final String INVENTORY = "b503e012-1496-4213-872d-f70f5fe813a5";
    @TempDir Path directory;

    /** The sender and item fields are cut down from the actual fkn9 / Pineapple helmet. */
    private static JsonObject helmet() {
        return json("""
                {"skyblockId":"DCTR_SPACE_HELM","uuid":"helmet-uuid","name":"§cSpace Helmet",
                 "lore":["§7To: §6[MVP§a++§6] its_Eleminator§f","§7From: §c[ADMIN] Dctr§f"],
                 "attributes":{"sender_name":"§c[ADMIN] Dctr§f","recipient_name":"§6[MVP§a++§6] its_Eleminator§f"}}
                """).getAsJsonObject();
    }

    @Test
    void matchesTheActualHelmetRegardlessOfRecipient() {
        JsonObject item = helmet();
        assertTrue(GuildItemSearch.matches(item));
        item.getAsJsonObject("attributes").addProperty("recipient_name", "SomeoneElse");
        assertTrue(GuildItemSearch.matches(item));
        item.getAsJsonObject("attributes").remove("sender_name");
        assertTrue(GuildItemSearch.matches(item));
    }

    @Test
    void rejectsOtherItemsAndSimilarOrConflictingSenders() {
        JsonObject item = helmet();
        item.addProperty("skyblockId", "SPACE_HELMET");
        assertFalse(GuildItemSearch.matches(item));
        item = helmet();
        item.getAsJsonObject("attributes").addProperty("sender_name", "[ADMIN] Dctr2");
        assertFalse(GuildItemSearch.matches(item));
        item.getAsJsonObject("attributes").addProperty("sender_name", "[ADMIN] OtherAdmin");
        assertFalse(GuildItemSearch.matches(item));
        item.getAsJsonObject("attributes").remove("sender_name");
        item.add("lore", json("[\"To: [ADMIN] Dctr\",\"From: [ADMIN] Dctr2\"]"));
        assertFalse(GuildItemSearch.matches(item));
        assertFalse(GuildItemSearch.matches(null));
    }

    @Test
    void includesUnselectedProfilesButExcludesDeletedAndRemovedOnes() {
        JsonObject account = account();
        JsonArray profiles = account.getAsJsonArray("profiles");
        JsonObject removed = new JsonObject();
        removed.addProperty("profileId", "a".repeat(32));
        removed.addProperty("wasRemoved", true);
        profiles.add(removed);
        JsonObject deleted = removed.deepCopy();
        deleted.remove("wasRemoved");
        deleted.addProperty("deleted", true);
        profiles.add(deleted);
        profiles.add(profiles.get(0).deepCopy());
        assertEquals(List.of(PROFILE, OTHER_PROFILE), GuildItemSearch.profiles(account).stream().map(GuildItemSearch.Profile::id).toList());
    }

    @Test
    void checksTheRequestedStoresAndWornArmorWithoutFetchingIconsOrBags() {
        for (String name : List.of("inventory", "ender_chest", "wardrobe", "armor", "backpack_0", "backpack_17")) {
            assertTrue(GuildItemSearch.searchInventory(name), name);
        }
        for (String name : List.of("icons_backpack", "talisman_bag", "equipment", "backpack_wrong", "personal_vault")) {
            assertFalse(GuildItemSearch.searchInventory(name), name);
        }
        assertEquals("Backpack 1 · Slot 6", GuildItemSearch.locationName("backpack_0", "5"));
        assertEquals("Wardrobe · Set 12 · Helmet", GuildItemSearch.locationName("wardrobe", "12:HELMET"));
        assertEquals("Ender Chest · Page 2 · Slot 1", GuildItemSearch.locationName("ender_chest", "45"));
        assertEquals("Worn Armor · Helmet", GuildItemSearch.locationName("armor", "3"));
    }

    @Test
    void checkpointResumeKeepsTheGuildSnapshotAndDoesNotRepeatFinishedInventories() throws Exception {
        FakeSource first = new FakeSource();
        Path checkpoint = directory.resolve("search.json");
        GuildItemSearch search = new GuildItemSearch(checkpoint, first, () -> true);
        // Ranking, ten rosters, account, first profile, first inventory.
        for (int i = 0; i < 14; i++) { search.step(); search.checkpoint(); }
        FakeSource resumed = new FakeSource();
        search = new GuildItemSearch(checkpoint, resumed, () -> true);
        for (int i = 0; i < 14; i++) { search.step(); search.checkpoint(); }
        GuildItemSearch restored = new GuildItemSearch(checkpoint, resumed, () -> true);
        GuildItemSearch.View result = restored.view();
        assertTrue(result.complete());
        assertEquals(1, result.playersTotal()); // Same UUID occurs in all ten rosters.
        assertEquals(1, result.playersDone());
        assertEquals(2, result.profilesChecked());
        assertEquals(2, result.inventoriesChecked());
        assertEquals(2, result.matches().size());
        assertEquals("12:HELMET", result.matches().get(0).slot());
        assertEquals("Pineapple", result.matches().get(0).profile());
        assertTrue(resumed.paths.stream().noneMatch(p -> p.startsWith("/leaderboard/") || p.startsWith("/guild/") || p.startsWith("/account/")));
        assertEquals(1, resumed.paths.stream().filter(p -> p.contains("/inventories/")).count());
        assertTrue(restored.csv().contains("fkn9"));
        assertFalse(Files.readString(checkpoint).contains("Authorization"));
    }

    @Test
    void inventoryApiOffIsACoverageGapInsteadOfAClaimOfNoHelmet() throws Exception {
        FakeSource source = new FakeSource();
        source.apiOff = true;
        Path checkpoint = directory.resolve("off.json");
        GuildItemSearch search = new GuildItemSearch(checkpoint, source, () -> true);
        for (int i = 0; i < 20; i++) search.step();
        search.checkpoint();
        GuildItemSearch.View result = new GuildItemSearch(checkpoint, source, () -> true).view();
        assertTrue(result.complete());
        assertEquals(0, result.inventoriesChecked());
        assertEquals(2, result.issues().size());
        assertEquals(0, result.matches().size());
    }

    @Test
    void aFailedRosterCannotSilentlyExcludeAnEntireGuild() throws Exception {
        FakeSource fake = new FakeSource();
        GuildItemSearch.Source source = (path, sky) -> {
            if (path.startsWith("/guild/")) throw new GuildItemSearch.Failure(403, "Access refused.", 0);
            return fake.get(path, sky);
        };
        Path checkpoint = directory.resolve("failed-roster.json");
        GuildItemSearch search = new GuildItemSearch(checkpoint, source, () -> true);
        search.step();
        assertThrows(GuildItemSearch.Failure.class, search::step);
        search.checkpoint();
        var restored = new GuildItemSearch(checkpoint, source, () -> true).view();
        assertFalse(restored.complete());
        assertEquals(0, restored.guildsLoaded());
    }

    @Test
    void malformedInventoryIsNotRecordedAsSuccessfullyChecked() throws Exception {
        FakeSource fake = new FakeSource();
        GuildItemSearch.Source source = (path, sky) -> path.contains("/inventories/") ? json("{}") : fake.get(path, sky);
        GuildItemSearch search = new GuildItemSearch(directory.resolve("malformed.json"), source, () -> true);
        for (int i = 0; i < 13; i++) search.step();
        assertThrows(GuildItemSearch.Failure.class, search::step);
        search.checkpoint();
        assertEquals(0, new GuildItemSearch(directory.resolve("malformed.json"), source, () -> true).view().inventoriesChecked());
    }

    @Test
    void disablingNetworkStopsBeforeAnotherRequestAndKeepsTheCompletedAnswer() throws Exception {
        AtomicBoolean network = new AtomicBoolean(true);
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch reply = new CountDownLatch(1);
        FakeSource fake = new FakeSource();
        GuildItemSearch search = new GuildItemSearch(directory.resolve("paused.json"), (path, sky) -> {
            requested.countDown();
            assertTrue(reply.await(5, TimeUnit.SECONDS));
            return fake.get(path, sky);
        }, network::get);
        search.start(false);
        assertTrue(requested.await(5, TimeUnit.SECONDS));
        network.set(false);
        reply.countDown();
        awaitStopped(search);
        assertEquals(1, fake.paths.size());
        assertEquals(10, search.view().guilds().size());
        assertFalse(search.view().complete());
        assertTrue(search.view().message().contains("paused"));
    }

    @Test
    void pauseDuringRateLimitWaitDoesNotWaitForTheWholeResetWindow() throws Exception {
        CountDownLatch requested = new CountDownLatch(1);
        GuildItemSearch search = new GuildItemSearch(directory.resolve("rate-limited.json"), (path, sky) -> {
            requested.countDown();
            throw new GuildItemSearch.Failure(429, "Limited", System.currentTimeMillis() + 300_000L);
        }, () -> true);
        search.start(false);
        assertTrue(requested.await(5, TimeUnit.SECONDS));
        search.pause();
        awaitStopped(search);
        assertFalse(search.view().complete());
        assertTrue(search.view().message().contains("paused"));
    }

    @Test
    void theBackgroundWorkerCanResumeAfterPausingAnInFlightRequest() throws Exception {
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch reply = new CountDownLatch(1);
        FakeSource fake = new FakeSource();
        AtomicBoolean first = new AtomicBoolean(true);
        GuildItemSearch search = new GuildItemSearch(directory.resolve("resume-worker.json"), (path, sky) -> {
            if (first.getAndSet(false)) {
                requested.countDown();
                assertTrue(reply.await(5, TimeUnit.SECONDS));
            }
            return fake.get(path, sky);
        }, () -> true);
        search.start(false);
        assertTrue(requested.await(5, TimeUnit.SECONDS));
        search.pause();
        reply.countDown();
        awaitStopped(search);
        assertEquals(1, fake.paths.size());
        search.start(false);
        awaitStopped(search);
        assertTrue(search.view().complete());
        assertEquals(2, search.view().matches().size());
        assertEquals(1, fake.paths.stream().filter(p -> p.startsWith("/leaderboard/")).count());
    }

    @Test
    void noRequestStartsWhileNetworkFeaturesAreOff() {
        FakeSource source = new FakeSource();
        GuildItemSearch search = new GuildItemSearch(directory.resolve("offline.json"), source, () -> false);
        search.start(false);
        assertFalse(search.view().running());
        assertTrue(source.paths.isEmpty());
    }

    @Test
    void anEmptyRosterDoesNotCountAsACompletedGuild() throws Exception {
        FakeSource fake = new FakeSource();
        GuildItemSearch.Source source = (path, sky) -> path.startsWith("/guild/") ? json("{\"members\":[]}") : fake.get(path, sky);
        GuildItemSearch search = new GuildItemSearch(directory.resolve("empty-roster.json"), source, () -> true);
        search.step();
        assertThrows(GuildItemSearch.Failure.class, search::step);
        search.checkpoint();
        assertEquals(0, new GuildItemSearch(directory.resolve("empty-roster.json"), source, () -> true).view().guildsLoaded());
    }

    @Test
    void cancelAlsoPersistsWhenTheRunIsAlreadyPaused() throws Exception {
        Path file = directory.resolve("cancel.json");
        GuildItemSearch search = new GuildItemSearch(file, new FakeSource(), () -> true);
        search.step();
        search.cancel();
        assertTrue(search.view().cancelled());
        // A checkpoint written explicitly has the same cancelled state as the queued save.
        // Wait for the queued save before opening it to avoid racing AtomicJsonFile's temp name.
        for (int i = 0; i < 100 && !Files.exists(file); i++) Thread.sleep(10);
        assertTrue(new GuildItemSearch(file, new FakeSource(), () -> true).view().cancelled());
    }

    @Test
    void preservesAnUnreadableCheckpointAndDoesNotAutomaticallyStartRequests() throws Exception {
        Path file = directory.resolve("broken.json");
        Files.writeString(file, "{broken");
        FakeSource source = new FakeSource();
        GuildItemSearch search = new GuildItemSearch(file, source, () -> true);
        assertFalse(search.view().running());
        assertTrue(source.paths.isEmpty());
        try (var files = Files.list(directory)) {
            Path preserved = files.filter(p -> p.getFileName().toString().startsWith("broken.json.corrupt-")).findFirst().orElseThrow();
            assertEquals("{broken", Files.readString(preserved));
        }
    }

    private static void awaitStopped(GuildItemSearch search) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (search.view().running() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(search.view().running());
    }

    private static JsonElement json(String value) { return JsonParser.parseString(value); }

    private static JsonObject account() {
        return json("""
                {"profiles":[{"profileId":"%s","profileName":"Pineapple","selected":true},
                             {"profileId":"%s","profileName":"Apple","selected":false}]}
                """.formatted(PROFILE, OTHER_PROFILE)).getAsJsonObject();
    }

    private static final class FakeSource implements GuildItemSearch.Source {
        final List<String> paths = new ArrayList<>();
        boolean apiOff;

        @Override
        public JsonElement get(String path, boolean skyKings) {
            paths.add(path);
            if (path.startsWith("/leaderboard/guilds")) {
                JsonObject response = new JsonObject();
                JsonArray guilds = new JsonArray();
                for (int i = 10; i >= 1; i--) {
                    JsonObject guild = new JsonObject();
                    guild.addProperty("rank", i);
                    guild.addProperty("name", "Guild " + i);
                    guilds.add(guild);
                }
                response.add("guilds", guilds);
                return response;
            }
            if (path.startsWith("/guild/members")) return json("{\"members\":[{\"uuid\":\"" + PLAYER + "\",\"username\":\"fkn9\"}]}");
            if (path.startsWith("/account/")) return account();
            if (path.contains("/inventories/")) {
                JsonObject response = new JsonObject();
                JsonObject items = new JsonObject();
                items.add("12:HELMET", helmet());
                items.add("13:HELMET", helmet()); // Same item UUID must not produce duplicate matches.
                response.add("items", items);
                return response;
            }
            return json("""
                    {"api":{"inventories":%s},"lastUpdated":1793649912,"inventories":[
                     {"id":"%s","name":"wardrobe"},{"id":"%s","name":"icons_backpack"}]}
                    """.formatted(!apiOff, INVENTORY, INVENTORY));
        }
    }
}
