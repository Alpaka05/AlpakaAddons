package net.alpaka.addons.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Swaps a downloaded update into the mods folder after the game has exited. The game cannot do it
 * itself: on Windows the running jar stays locked until its process ends.
 *
 * This runs in its own JVM, started by {@link UpdateInstaller}'s shutdown hook, with nothing but
 * the JDK on its class path - no Minecraft, no Fabric, no Kotlin. That is also why it is a single
 * class with no nested classes: the game copies this one class file out of its own jar into
 * {@code .alpaka_update/helper/} and starts it from there, so no jar in the mods folder is held open.
 *
 * The mods folder must never end up with no copy of the mod, two copies, or half a jar, hence the order:
 * <ol>
 *   <li>wait (at most two minutes) for the game's process to end</li>
 *   <li>check the staged jar's SHA-256</li>
 *   <li>move it in under its release name - atomically, or through a name that is not a
 *       {@code .jar} when the staging folder is on another drive</li>
 *   <li>flush it to disk and check the SHA-256 again</li>
 *   <li>only then delete the old jar</li>
 * </ol>
 * If the old jar cannot be deleted, the new one is taken out again: Fabric refuses to start with
 * the same mod twice, and the old version still works. The update is simply offered again on the
 * next start. Each move or delete is retried for up to a minute, and every step goes to the log.
 *
 * Arguments: {@code --parent-pid <pid> [--parent-start <epoch ms>] --staged <file> --dest <file>
 * --old <file> --sha256 <hex> --log <file>}, and for tests {@code --wait-ms}, {@code --retry-ms} and
 * {@code --copy} (take the other-drive path). The start time tells the game apart from a later
 * process that was given its PID.
 */
public final class UpdateHelper {
    static final int APPLIED = 0;
    static final int NOT_APPLIED = 1;
    static final int BAD_ARGUMENTS = 2;

    private static final long MAX_WAIT_MS = 120_000;
    private static final long MAX_RETRY_MS = 60_000;
    private static final long POLL_MS = 100;
    /** The other-drive copy's name until it is complete: not a {@code .jar}, so Fabric never loads it. */
    private static final String TEMPORARY_SUFFIX = ".alpaka-part";

    private UpdateHelper() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** The whole run; returns the exit code. */
    static int run(String[] args) {
        long parentPid = -1;
        long parentStart = -1;
        Path staged = null;
        Path dest = null;
        Path old = null;
        String sha256 = null;
        Path log = null;
        long waitMs = MAX_WAIT_MS;
        long retryMs = MAX_RETRY_MS;
        boolean copy = false;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--parent-pid" -> parentPid = Long.parseLong(args[++i]);
                    case "--parent-start" -> parentStart = Long.parseLong(args[++i]);
                    case "--staged" -> staged = Path.of(args[++i]).toAbsolutePath().normalize();
                    case "--dest" -> dest = Path.of(args[++i]).toAbsolutePath().normalize();
                    case "--old" -> old = Path.of(args[++i]).toAbsolutePath().normalize();
                    case "--sha256" -> sha256 = args[++i].toLowerCase(Locale.ROOT);
                    case "--log" -> log = Path.of(args[++i]).toAbsolutePath().normalize();
                    case "--wait-ms" -> waitMs = Long.parseLong(args[++i]);
                    case "--retry-ms" -> retryMs = Long.parseLong(args[++i]);
                    case "--copy" -> copy = true;
                    default -> {
                        return BAD_ARGUMENTS;
                    }
                }
            }
        } catch (RuntimeException e) {
            return BAD_ARGUMENTS;
        }
        if (staged == null || dest == null || old == null || log == null
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            return BAD_ARGUMENTS;
        }

        log(log, "started: parent " + parentPid + ", staged " + staged + ", dest " + dest + ", old " + old);
        String refusal = refusal(staged, dest, old);
        if (refusal != null) {
            log(log, "refused: " + refusal);
            return NOT_APPLIED;
        }
        if (!waitForParent(parentPid, parentStart, waitMs, log)) return NOT_APPLIED;
        if (!sha256.equals(sha256(staged))) {
            log(log, "the staged jar's SHA-256 is not " + sha256 + "; nothing changed");
            return NOT_APPLIED;
        }
        if (Files.exists(dest)) {
            if (!sha256.equals(sha256(dest))) {
                log(log, "another file already has the name " + dest.getFileName() + "; nothing changed");
                return NOT_APPLIED;
            }
            log(log, "the new jar is already in place");
        } else if (!moveIn(staged, dest, copy, retryMs, log)) {
            return NOT_APPLIED;
        }

        // The old jar goes only once the new one's bytes are on disk, so a power cut cannot leave a
        // jar of zeros as the only copy.
        flush(dest, log);
        if (!sha256.equals(sha256(dest))) {
            log(log, "the moved jar's SHA-256 is not " + sha256 + "; taking it out again");
            takeOut(dest, retryMs, log);
            return NOT_APPLIED;
        }
        log(log, "moved in: " + dest);
        if (!delete(old, retryMs, log)) {
            log(log, "the old jar could not be deleted; taking the new one out so the mod is not there twice");
            takeOut(dest, retryMs, log);
            return NOT_APPLIED;
        }
        log(log, "deleted the old jar: " + old);
        return APPLIED;
    }

    /**
     * Why the paths may not be used, or null: the new jar goes into the old jar's folder under a
     * different {@code .jar} name, and the staged file exists.
     */
    static String refusal(Path staged, Path dest, Path old) {
        if (dest.getParent() == null || !dest.getParent().equals(old.getParent())) {
            return "the new jar would not go into the old jar's folder";
        }
        String name = dest.getFileName().toString();
        if (!name.endsWith(".jar") || name.startsWith(".")) {
            return "the new jar's name " + name + " is not a jar name";
        }
        // NTFS compares names without case.
        if (name.equalsIgnoreCase(old.getFileName().toString())) {
            return "the new jar would replace the running jar in place";
        }
        if (!Files.isRegularFile(staged)) {
            return "the staged jar is missing";
        }
        return null;
    }

    private static boolean waitForParent(long pid, long start, long waitMs, Path log) {
        Optional<ProcessHandle> parent = pid < 0 ? Optional.empty() : ProcessHandle.of(pid);
        // A process that started at another time only reuses the game's PID; the game has ended.
        if (parent.isPresent() && start >= 0) {
            Optional<Instant> started = parent.get().info().startInstant();
            if (started.isPresent() && Math.abs(started.get().toEpochMilli() - start) > 1_000) {
                parent = Optional.empty();
            }
        }
        if (parent.isEmpty()) {
            log(log, "the game's process has already ended");
            return true;
        }
        try {
            parent.get().onExit().get(waitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log(log, "the game's process did not end within " + waitMs + " ms; nothing changed");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log(log, "interrupted while waiting; nothing changed");
            return false;
        } catch (ExecutionException e) {
            log(log, "waiting failed: " + e.getCause() + "; nothing changed");
            return false;
        }
        log(log, "the game's process has ended");
        return true;
    }

    /** Moves {@code staged} to {@code dest}, never leaving a partial file under a .jar name. */
    private static boolean moveIn(Path staged, Path dest, boolean copy, long retryMs, Path log) {
        Path temporary = dest.resolveSibling(dest.getFileName() + TEMPORARY_SUFFIX);
        long deadline = System.nanoTime() + retryMs * 1_000_000;
        while (true) {
            try {
                if (!copy) {
                    try {
                        Files.move(staged, dest, StandardCopyOption.ATOMIC_MOVE);
                        return true;
                    } catch (AtomicMoveNotSupportedException otherDrive) {
                        copy = true;
                    }
                }
                Files.copy(staged, temporary, StandardCopyOption.REPLACE_EXISTING);
                flush(temporary, log);
                Files.move(temporary, dest, StandardCopyOption.ATOMIC_MOVE);
                deleteQuietly(staged);
                return true;
            } catch (IOException e) {
                deleteQuietly(temporary);
                if (System.nanoTime() >= deadline) {
                    log(log, "could not move the new jar in within " + retryMs + " ms (" + e + "); the old jar stays");
                    return false;
                }
                pause();
            }
        }
    }

    /** Removes a new jar that must not stay, so the old one is the only copy again. */
    private static void takeOut(Path dest, long retryMs, Path log) {
        if (delete(dest, retryMs, log)) {
            log(log, "took the new jar out again; the old version stays installed");
        } else {
            log(log, "the new jar could not be taken out either: delete one of the two alpaka jars by hand");
        }
    }

    /** Forces a file's bytes to the disk. */
    private static void flush(Path file, Path log) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        } catch (IOException e) {
            log(log, "could not flush " + file.getFileName() + " to disk: " + e);
        }
    }

    private static boolean delete(Path file, long retryMs, Path log) {
        long deadline = System.nanoTime() + retryMs * 1_000_000;
        while (true) {
            try {
                Files.deleteIfExists(file);
                return true;
            } catch (IOException e) {
                if (System.nanoTime() >= deadline) {
                    log(log, "could not delete " + file + " within " + retryMs + " ms: " + e);
                    return false;
                }
                pause();
            }
        }
    }

    /** The file's SHA-256 as lowercase hex, or null if it cannot be read. */
    static String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The game clears the staging folder on its next start.
        }
    }

    /** Appends one timestamped line; a log that cannot be written does not stop the swap. */
    private static void log(Path log, String line) {
        try {
            Files.createDirectories(log.getParent());
            try (Writer out = Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write(Instant.now() + " " + line + System.lineSeparator());
            }
        } catch (IOException ignored) {
            // Nothing else to tell.
        }
    }
}
