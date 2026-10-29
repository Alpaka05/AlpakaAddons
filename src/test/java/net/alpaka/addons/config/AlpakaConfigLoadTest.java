package net.alpaka.addons.config;

import com.google.gson.JsonObject;
import net.alpaka.addons.features.slayer.SlayerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loading the settings file: what an unusable value, an old layout or a damaged file turns into. */
class AlpakaConfigLoadTest {
    private static final int OLD_GOLD = 0xFFE5B849;
    private static final int TEAL = 0xFF29B6B2;

    @TempDir
    Path dir;

    private AlpakaConfig.ConfigStore store() {
        return new AlpakaConfig.ConfigStore(dir.resolve("alpaka.json").toFile(), dir.resolve("alpaka.json.bak").toFile());
    }

    private AlpakaConfig load(String json) throws IOException {
        Files.writeString(dir.resolve("alpaka.json"), json, StandardCharsets.UTF_8);
        AlpakaConfig.load(store());
        return AlpakaConfig.instance;
    }

    private String saved() throws IOException {
        return Files.readString(dir.resolve("alpaka.json"), StandardCharsets.UTF_8);
    }

    @Test
    void aFreshInstallGetsDefaultsAndAFileOfTheCurrentLayout() throws IOException {
        AlpakaConfig.load(store());

        assertFalse(AlpakaConfig.existedAtStart());
        assertEquals(AlpakaConfig.CONFIG_VERSION, AlpakaConfig.instance.configVersion);
        assertTrue(saved().contains("\"configVersion\": " + AlpakaConfig.CONFIG_VERSION));
    }

    @Test
    void nanAndInfinityGoBackToTheirDefaults() throws IOException {
        AlpakaConfig config = load("{\"configVersion\": 1, \"notificationHoldSeconds\": NaN, \"itemScale\": -Infinity, \"chatHistoryLimit\": 300}");

        assertEquals(4.5f, config.notificationHoldSeconds);
        assertEquals(1.0f, config.itemScale);
        assertEquals(300, config.chatHistoryLimit);
        assertFalse(saved().contains("NaN"), "the repaired values are written back");
    }

    @Test
    void aNanSetLaterNoLongerBlocksEverySave() throws IOException {
        load("{\"configVersion\": 1}");
        AlpakaConfig.instance.notificationHoldSeconds = Float.NaN;
        AlpakaConfig.instance.chatHistoryLimit = 1234;
        AlpakaConfig.save();

        AlpakaConfig reloaded = load(saved());

        assertEquals(1234, reloaded.chatHistoryLimit, "the save went through");
        assertEquals(4.5f, reloaded.notificationHoldSeconds);
    }

    @Test
    void nullTextsListsAndPresetsGetUsableValues() throws IOException {
        AlpakaConfig config = load("""
                {"configVersion": 1, "bridgeBotName": null, "statsDirectory": null,
                 "itemPresets": [null, {"scale": NaN, "xOffset": 0.5}, {}],
                 "commandWheelPages": [null, ["/hub", null, "/pv"]],
                 "slayerBossMap": {"BLAZE": null}}
                """);

        assertEquals("", config.bridgeBotName);
        assertEquals("", config.statsDirectory);
        assertEquals(2, config.itemPresets.size(), "presets can be deleted here, so a null one is dropped");
        assertEquals(1.0f, config.itemPresets.get(0).scale);
        assertEquals(0.5f, config.itemPresets.get(0).xOffset);
        assertEquals(List.of(List.of("/hub", "/pv")), config.commandWheelPages);
        assertNotNull(config.slayerBossMap.get(SlayerType.BLAZE));
    }

    @Test
    void theOldGoldBecomesTealOnceAndAChosenGoldStays() throws IOException {
        JsonObject old = new JsonObject();
        old.addProperty("menuAccentColor", OLD_GOLD);
        old.addProperty("chatHistoryLimit", 20000);

        AlpakaConfig config = load(old.toString());
        assertEquals(TEAL, config.menuAccentColor);
        assertEquals(5000, config.chatHistoryLimit, "the history slider ends at 5000");
        assertEquals(AlpakaConfig.CONFIG_VERSION, config.configVersion);

        // Picked again by hand: from now on it is a choice.
        config.menuAccentColor = OLD_GOLD;
        AlpakaConfig.save();
        assertEquals(OLD_GOLD, load(saved()).menuAccentColor);
    }

    @Test
    void aHistoryPastTheSlidersEndIsCutOnceAndAShorterOneStays() throws IOException {
        assertEquals(5000, load("{\"configVersion\": 1, \"chatHistoryLimit\": 20000}").chatHistoryLimit);
        assertEquals(3000, load("{\"configVersion\": 1, \"chatHistoryLimit\": 3000}").chatHistoryLimit);
        assertEquals(1000, load("{}").chatHistoryLimit, "1000 by default");
    }

    @Test
    void aFileFromANewerVersionGetsNoStepsAndKeepsItsNumber() throws IOException {
        JsonObject newer = new JsonObject();
        newer.addProperty("configVersion", 99);
        newer.addProperty("menuAccentColor", OLD_GOLD);

        AlpakaConfig config = load(newer.toString());

        assertEquals(OLD_GOLD, config.menuAccentColor);
        assertEquals(99, config.configVersion);
    }

    @Test
    void theWorldAgeHudBecomesShowDayOnce() throws IOException {
        AlpakaConfig config = load("""
                {"configVersion": 1, "worldAgeHudEnabled": true, "worldAgeHudX": 10, "worldAgeHudY": 20,
                 "worldAgeHudScale": 1.5, "performanceHudEnabled": false}
                """);

        assertTrue(config.performanceHudEnabled);
        assertTrue(config.performanceHudShowDay);
        assertFalse(config.performanceHudShowFps || config.performanceHudShowPing || config.performanceHudShowTps);
        assertEquals(10, config.performanceHudX);
        assertEquals(20, config.performanceHudY);
        assertEquals(1.5f, config.performanceHudScale);
        assertNull(config.worldAgeHudEnabled);

        AlpakaConfig.save();
        assertFalse(saved().contains("worldAgeHud"), "the legacy fields are gone from the file");
    }

    @Test
    void anEmptyFileFallsBackToTheBackup() throws IOException {
        Files.writeString(dir.resolve("alpaka.json.bak"), "{\"configVersion\": 1, \"chatHistoryLimit\": 777}", StandardCharsets.UTF_8);

        AlpakaConfig config = load("");

        assertEquals(777, config.chatHistoryLimit);
        String notice = AlpakaConfig.takeLoadNotice();
        assertNotNull(notice);
        assertTrue(notice.contains("last good copy"));
        File[] kept = dir.toFile().listFiles((d, name) -> name.startsWith("alpaka.json.corrupt-"));
        assertNotNull(kept);
        assertEquals(1, kept.length, "the damaged file is kept aside");
    }

    @Test
    void anEmptyFileWithoutABackupGivesDefaults() throws IOException {
        AlpakaConfig config = load("");

        assertEquals(new AlpakaConfig().chatHistoryLimit, config.chatHistoryLimit);
        String notice = AlpakaConfig.takeLoadNotice();
        assertNotNull(notice);
        assertTrue(notice.contains("reset"));
    }
}
