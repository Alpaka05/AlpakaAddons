package net.alpaka.addons.features.resourcepack;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPackCacheTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void packListSurvivesARoundTrip() {
        Map<UUID, String> packs = new LinkedHashMap<>();
        packs.put(UUID.randomUUID(), HASH);
        packs.put(UUID.randomUUID(), "fedcba9876543210fedcba9876543210fedcba98");
        assertEquals(packs, ServerPackCache.fromJson(ServerPackCache.toJson(packs)));
    }

    @Test
    void badEntriesAreDroppedAndTheRestKept() {
        UUID good = UUID.randomUUID();
        String json = "{\"packs\":[{\"id\":\"not-a-uuid\",\"hash\":\"" + HASH + "\"},"
                + "{\"id\":\"" + UUID.randomUUID() + "\",\"hash\":\"../../evil\"},"
                + "{\"id\":\"" + good + "\",\"hash\":\"" + HASH.toUpperCase() + "\"}]}";
        assertEquals(Map.of(good, HASH), ServerPackCache.fromJson(json));
    }

    @Test
    void brokenFileGivesAnEmptyList() {
        assertTrue(ServerPackCache.fromJson("{oops").isEmpty());
        assertTrue(ServerPackCache.fromJson("[]").isEmpty());
    }

    @Test
    void hashComesFromTheDownloadFileName() {
        assertEquals(HASH, ServerPackCache.hashOf(Path.of("downloads", "id", HASH)));
        assertNull(ServerPackCache.hashOf(Path.of("downloads", "id", "pack.zip")));
    }
}
