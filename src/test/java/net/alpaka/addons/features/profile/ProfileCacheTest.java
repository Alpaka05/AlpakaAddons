package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileCacheTest {
    private static final String ME = "8f16f5a695d3468392aed51c1a5bb037";

    @TempDir
    Path dir;

    @Test
    void theLastLookComesBackAsItWasKept() {
        JsonElement answer = JsonParser.parseString("""
                {"success": true, "fetchedAt": 5, "profile": {"profile_id": "b135", "member": {"currencies": {"coin_purse": 1.5}}},
                 "museum": {"value": 9}}""");
        ProfileCache.write(dir, ME, answer).join();
        assertEquals(answer, ProfileCache.read(dir, ME));
        // Kept again, the newer one replaces it.
        JsonElement newer = JsonParser.parseString("{\"fetchedAt\": 6, \"profile\": {\"profile_id\": \"b135\", \"member\": {}}}");
        ProfileCache.write(dir, ME, newer).join();
        assertEquals(newer, ProfileCache.read(dir, ME));
    }

    @Test
    void nothingButAProfileAndAUuidMakesAFile() throws Exception {
        assertNull(ProfileCache.read(dir, ME));
        // Only a UUID names a file.
        ProfileCache.write(dir, "../evil", JsonParser.parseString("{\"profile\": {\"member\": {}}}")).join();
        try (var files = Files.list(dir)) {
            assertFalse(files.findAny().isPresent());
        }
        // An answer without a profile is none to show.
        ProfileCache.write(dir, ME, JsonParser.parseString("{\"success\": true}")).join();
        assertNull(ProfileCache.read(dir, ME));
        // Nor is a broken file.
        Files.writeString(dir.resolve(ME + ".json.gz"), "not packed");
        assertNull(ProfileCache.read(dir, ME));
    }
}
