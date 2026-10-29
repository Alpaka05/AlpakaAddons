package net.alpaka.addons.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateHelperTest {
    private static final byte[] OLD_JAR = "old jar".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEW_JAR = "new jar".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private Path mods() throws Exception {
        return Files.createDirectories(dir.resolve("mods"));
    }

    private Path staged() throws Exception {
        Path staged = Files.createDirectories(dir.resolve(".alpaka_update/staged")).resolve("alpaka-1.4.0-mc26.2.jar");
        Files.write(staged, NEW_JAR);
        return staged;
    }

    /** No parent process to wait for, and short retries so a failure ends quickly. */
    private int run(Path staged, Path dest, Path old, String sha256, String... extra) {
        String[] base = {
                "--parent-pid", "-1",
                "--staged", staged.toString(), "--dest", dest.toString(), "--old", old.toString(),
                "--sha256", sha256, "--log", dir.resolve("helper.log").toString(), "--retry-ms", "200"};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return UpdateHelper.run(args);
    }

    @Test
    void swapsTheNewJarInAndDeletesTheOldOne() throws Exception {
        Path old = Files.write(mods().resolve("alpaka-1.3.0-mc26.2.jar"), OLD_JAR);
        Path staged = staged();
        Path dest = mods().resolve("alpaka-1.4.0-mc26.2.jar");

        assertEquals(UpdateHelper.APPLIED, run(staged, dest, old, UpdateHelper.sha256(staged)));
        assertArrayEquals(NEW_JAR, Files.readAllBytes(dest));
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(staged));
    }

    @Test
    void copiesThroughATemporaryNameWhenMovingIsNotPossible() throws Exception {
        Path old = Files.write(mods().resolve("alpaka-1.3.0-mc26.2.jar"), OLD_JAR);
        Path staged = staged();
        Path dest = mods().resolve("alpaka-1.4.0-mc26.2.jar");

        assertEquals(UpdateHelper.APPLIED, run(staged, dest, old, UpdateHelper.sha256(staged), "--copy"));
        assertArrayEquals(NEW_JAR, Files.readAllBytes(dest));
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(dest.resolveSibling(dest.getFileName() + ".alpaka-part")));
    }

    @Test
    void leavesEverythingAloneWhenTheStagedJarDoesNotMatchItsChecksum() throws Exception {
        Path old = Files.write(mods().resolve("alpaka-1.3.0-mc26.2.jar"), OLD_JAR);
        Path staged = staged();
        Path dest = mods().resolve("alpaka-1.4.0-mc26.2.jar");

        assertEquals(UpdateHelper.NOT_APPLIED, run(staged, dest, old, "0".repeat(64)));
        assertArrayEquals(OLD_JAR, Files.readAllBytes(old));
        assertFalse(Files.exists(dest));
    }

    @Test
    void takesTheNewJarOutWhenTheOldOneCannotBeDeleted() throws Exception {
        // A folder with something in it cannot be deleted on any system, unlike a locked file,
        // which only stays put on Windows.
        Path old = Files.createDirectories(mods().resolve("alpaka-1.3.0-mc26.2.jar"));
        Files.write(old.resolve("inside"), OLD_JAR);
        Path staged = staged();
        Path dest = mods().resolve("alpaka-1.4.0-mc26.2.jar");

        assertEquals(UpdateHelper.NOT_APPLIED, run(staged, dest, old, UpdateHelper.sha256(staged)));
        assertTrue(Files.exists(old));
        assertFalse(Files.exists(dest), "the mod must not be in the folder twice");
    }

    @Test
    void acceptsANewJarThatIsAlreadyInPlace() throws Exception {
        Path old = Files.write(mods().resolve("alpaka-1.3.0-mc26.2.jar"), OLD_JAR);
        Path staged = staged();
        Path dest = Files.write(mods().resolve("alpaka-1.4.0-mc26.2.jar"), NEW_JAR);

        assertEquals(UpdateHelper.APPLIED, run(staged, dest, old, UpdateHelper.sha256(staged)));
        assertFalse(Files.exists(old));
    }

    @Test
    void neverOverwritesAnotherFileWithTheNewName() throws Exception {
        Path old = Files.write(mods().resolve("alpaka-1.3.0-mc26.2.jar"), OLD_JAR);
        Path staged = staged();
        Path dest = Files.write(mods().resolve("alpaka-1.4.0-mc26.2.jar"), "something else".getBytes(StandardCharsets.UTF_8));

        assertEquals(UpdateHelper.NOT_APPLIED, run(staged, dest, old, UpdateHelper.sha256(staged)));
        assertTrue(Files.exists(old));
        assertEquals("something else", Files.readString(dest));
    }

    @Test
    void refusesPathsOutsideTheOldJarsFolderOrOverTheRunningJar() throws Exception {
        Path old = Files.write(mods().resolve("alpaka-1.3.0-mc26.2.jar"), OLD_JAR);
        Path staged = staged();

        assertNull(UpdateHelper.refusal(staged, mods().resolve("alpaka-1.4.0-mc26.2.jar"), old));
        assertNotNull(UpdateHelper.refusal(staged, dir.resolve("alpaka-1.4.0-mc26.2.jar"), old));
        assertNotNull(UpdateHelper.refusal(staged, mods().resolve("ALPAKA-1.3.0-MC26.2.JAR"), old));
        assertNotNull(UpdateHelper.refusal(staged, mods().resolve("alpaka-1.4.0-mc26.2.zip"), old));
        assertNotNull(UpdateHelper.refusal(dir.resolve("missing.jar"), mods().resolve("alpaka-1.4.0-mc26.2.jar"), old));
    }

    @Test
    void rejectsIncompleteArguments() {
        assertEquals(UpdateHelper.BAD_ARGUMENTS, UpdateHelper.run(new String[]{"--staged"}));
        assertEquals(UpdateHelper.BAD_ARGUMENTS, UpdateHelper.run(new String[]{"--unknown"}));
        assertEquals(UpdateHelper.BAD_ARGUMENTS, UpdateHelper.run(new String[]{"--sha256", "not-hex"}));
    }
}
