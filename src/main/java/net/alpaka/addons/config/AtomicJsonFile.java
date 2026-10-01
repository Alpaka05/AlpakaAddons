package net.alpaka.addons.config;

import net.alpaka.addons.AlpakaAddons;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reading and writing the mod's JSON files without ever leaving a half-written one behind.
 *
 * A file is written next to its target and moved into place, so a crash, a full disk or a killed
 * game in the middle of a write leaves the previous file intact instead of an empty one. The config
 * used to be written straight into place with a truncating writer, and an empty alpaka.json then
 * crashed every launch.
 *
 * The caller serialises to a string first, so a serialisation error can never reach the disk either.
 */
public final class AtomicJsonFile {
    private AtomicJsonFile() {}

    /** The file's text, or null when it does not exist. */
    public static String read(File file) throws IOException {
        if (!file.exists()) return null;
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * Writes {@code json} to {@code file}: to a temporary file beside it first, then moved over it.
     *
     * An atomic move where the filesystem offers one, a plain replace where it does not. A drive a
     * cloud client provides may refuse both, and then the text is written in place; that gives up the
     * atomicity rather than the save, and callers that care keep a backup for that window.
     */
    public static void write(File file, String json) throws IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory()) Files.createDirectories(dir.toPath());

        Path target = file.toPath();
        Path temp = new File(dir, file.getName() + ".tmp").toPath();
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException moveFailed) {
            AlpakaAddons.LOGGER.warn("Could not move {} into place; writing it in place", file.getAbsolutePath());
            Files.writeString(target, json, StandardCharsets.UTF_8);
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Moves a file that could not be read out of the way, as {@code <name>.corrupt-<time>}, so the
     * next write does not replace it and whatever it held can still be recovered by hand.
     */
    public static File quarantine(File file) {
        File aside = new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return aside;
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Could not move the unreadable {} aside", file.getAbsolutePath(), e);
            return null;
        }
    }

    /** Copies {@code file} to {@code backup}, replacing it. Failures are logged, not thrown. */
    public static void backup(File file, File backup) {
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Could not back up {}", file.getAbsolutePath(), e);
        }
    }
}
