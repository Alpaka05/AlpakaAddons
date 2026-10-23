package net.alpaka.addons.utils;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whether a downloaded release jar may replace the running mod, on jars built here. */
class ReleaseJarCheckTest {
    private static final String DEPENDS = """
            "depends": {
              "fabricloader": ">=0.16.0",
              "minecraft": "~26.2",
              "java": ">=25",
              "fabric-api": ">=0.159.0",
              "fabric-language-kotlin": ">=1.13.1"
            }""";

    @TempDir
    Path dir;

    /** What this game has: the versions of 1.4.0's own launch. */
    private final Map<String, String> installed = new HashMap<>(Map.of(
            "fabricloader", "0.19.5", "minecraft", "26.2", "java", "25",
            "fabric-api", "0.159.0+26.2", "fabric-language-kotlin", "1.14.1+kotlin.2.4.20"));

    private Version installedVersion(String id) {
        String version = installed.get(id);
        if (version == null) return null;
        try {
            return Version.parse(version);
        } catch (VersionParsingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path jar(String modJson) throws Exception {
        Path jar = dir.resolve("alpaka-1.5.0-mc26.2.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(modJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    private String check(String version, String rest) throws Exception {
        String json = "{\"id\": \"alpaka\", \"version\": \"" + version + "\"" + (rest.isEmpty() ? "" : ", " + rest) + "}";
        return ReleaseJarCheck.problem(jar(json), "alpaka", "1.5.0", this::installedVersion);
    }

    @Test
    void aReleaseThisGameCanRunPasses() throws Exception {
        assertNull(check("1.5.0", DEPENDS));
    }

    @Test
    void aHotfixGameMeetsTheMinecraftRange() throws Exception {
        installed.put("minecraft", "26.2.1");
        assertNull(check("1.5.0", DEPENDS));
    }

    @Test
    void aRaisedFabricApiMinimumIsRefused() throws Exception {
        String problem = check("1.5.0", DEPENDS.replace(">=0.159.0", ">=0.160.0"));
        assertEquals("it needs Fabric API >=0.160.0 and you have 0.159.0+26.2 - update Fabric API first", problem);
    }

    @Test
    void newerJavaKotlinLoaderOrMinecraftAreRefused() throws Exception {
        assertTrue(check("1.5.0", DEPENDS.replace("\">=25\"", "\">=26\"")).startsWith("it needs Java >=26"));
        assertTrue(check("1.5.0", DEPENDS.replace(">=1.13.1", ">=1.15.0")).startsWith("it needs Fabric Language Kotlin"));
        assertTrue(check("1.5.0", DEPENDS.replace(">=0.16.0", ">=0.20.0")).startsWith("it needs Fabric Loader"));
        assertTrue(check("1.5.0", DEPENDS.replace("~26.2", "~26.3")).startsWith("it needs Minecraft ~26.3"));
    }

    @Test
    void aMissingDependencyIsRefused() throws Exception {
        assertEquals("it needs owo-lib >=0.12.0, which isn't installed",
                check("1.5.0", "\"depends\": {\"owo-lib\": \">=0.12.0\"}"));
    }

    @Test
    void anyRangeOfAListWillDo() throws Exception {
        assertNull(check("1.5.0", "\"depends\": {\"minecraft\": [\"~26.1\", \"~26.2\"]}"));
    }

    @Test
    void aModItBreaksWithIsRefused() throws Exception {
        installed.put("sodium", "0.6.0");
        assertEquals("it doesn't work with sodium 0.6.0 - update or remove sodium first",
                check("1.5.0", "\"breaks\": {\"sodium\": \"<0.7.0\"}"));
        assertNull(check("1.5.0", "\"breaks\": {\"iris\": \"*\"}"));
    }

    @Test
    void unreadableRequirementsAreRefused() throws Exception {
        assertEquals("its requirements can't be read", check("1.5.0", "\"depends\": {\"fabric-api\": {\"min\": 1}}"));
        assertEquals("its requirements can't be read", check("1.5.0", "\"depends\": {\"fabric-api\": []}"));
        // Fabric reads a range that is no version as text, which no installed version meets.
        assertTrue(check("1.5.0", "\"depends\": {\"fabric-api\": \">=next\"}").startsWith("it needs Fabric API >=next"));
    }

    @Test
    void theJarHasToBeThisModAtThatVersion() throws Exception {
        assertNull(check("1.5.0+3.abc1234", DEPENDS));
        assertEquals("the download says it is version 1.4.9, not 1.5.0", check("1.4.9", DEPENDS));
        assertEquals("the download is not Alpaka Addons", ReleaseJarCheck.problem(
                jar("{\"id\": \"other\", \"version\": \"1.5.0\"}"), "alpaka", "1.5.0", this::installedVersion));
        Path notAJar = Files.writeString(dir.resolve("broken.jar"), "not a zip");
        assertEquals("the download is not a valid jar",
                ReleaseJarCheck.problem(notAJar, "alpaka", "1.5.0", this::installedVersion));
    }
}
