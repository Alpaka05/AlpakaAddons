package net.alpaka.addons.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicJsonFileTest {
    @TempDir
    Path dir;

    @Test
    void writesReplacesAndLeavesNoTempFile() throws Exception {
        File file = dir.resolve("alpaka.json").toFile();
        AtomicJsonFile.write(file, "{\"a\":1}");
        AtomicJsonFile.write(file, "{\"a\":2}");
        assertEquals("{\"a\":2}", AtomicJsonFile.read(file));
        assertFalse(dir.resolve("alpaka.json.tmp").toFile().exists());
    }

    @Test
    void missingFileReadsAsNull() throws Exception {
        assertNull(AtomicJsonFile.read(dir.resolve("absent.json").toFile()));
    }

    @Test
    void quarantineMovesTheFileAside() throws Exception {
        File file = dir.resolve("alpaka.json").toFile();
        AtomicJsonFile.write(file, "");
        File aside = AtomicJsonFile.quarantine(file);
        assertNotNull(aside);
        assertFalse(file.exists());
        assertTrue(aside.getName().startsWith("alpaka.json.corrupt-"));
    }

    @Test
    void createsMissingParentFolders() throws Exception {
        File file = dir.resolve("nested/deeper/alpaka-stats.json").toFile();
        AtomicJsonFile.write(file, "{}");
        assertEquals("{}", AtomicJsonFile.read(file));
    }
}
