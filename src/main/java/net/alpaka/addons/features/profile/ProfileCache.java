package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import net.alpaka.addons.AlpakaAddons;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The last look at the player's own profile, kept on disk: the AlpakaAddons API's answer with its
 * museum, as it came. Opening the dashboard shows it at once while the new one is on its way, so
 * the dashboard is there without waiting for Hypixel and the network. Only the player's own
 * profile, one file per account, packed (about a megabyte for a big one).
 */
final class ProfileCache {
    private ProfileCache() {}

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    /** One file written at a time, off the game's threads; a newer write simply follows. */
    private static final Executor WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Alpaka Profile Cache");
        t.setDaemon(true);
        return t;
    });

    static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("alpaka-profile-cache");
    }

    /** The answer kept for the account with this UUID (without dashes), or null. Reads the disk: not on the render thread. */
    static JsonElement read(String uuid) {
        return read(dir(), uuid);
    }

    static JsonElement read(Path dir, String uuid) {
        if (!valid(uuid)) return null;
        Path file = dir.resolve(uuid + ".json.gz");
        if (!Files.isRegularFile(file)) return null;
        try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            JsonElement answer = JsonParser.parseReader(reader);
            return answer.isJsonObject() && Json.at(answer, "profile.member") != null ? answer : null;
        } catch (IOException | RuntimeException e) {
            AlpakaAddons.LOGGER.debug("Profile Dashboard: the kept profile couldn't be read: {}", e.toString());
            return null;
        }
    }

    /** Keeps the answer for the account in the background, replacing the one before. */
    static CompletableFuture<Void> write(String uuid, JsonElement answer) {
        return write(dir(), uuid, answer);
    }

    static CompletableFuture<Void> write(Path dir, String uuid, JsonElement answer) {
        if (!valid(uuid) || answer == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dir);
                Path file = dir.resolve(uuid + ".json.gz");
                Path temp = Files.createTempFile(dir, uuid, ".tmp");
                try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(temp)), StandardCharsets.UTF_8);
                     JsonWriter json = new JsonWriter(writer)) {
                    GSON.toJson(answer, json);
                }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                AlpakaAddons.LOGGER.debug("Profile Dashboard: the profile couldn't be kept: {}", e.toString());
            }
        }, WRITER);
    }

    /** Only a UUID names a file: nothing else of an answer goes into a path. */
    private static boolean valid(String uuid) {
        return uuid != null && uuid.matches("[0-9a-f]{32}");
    }
}
