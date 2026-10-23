package net.alpaka.addons.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.features.slayer.SlayerType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a save keeps: fields and slayers of newer versions, the layout number, and what counts as progress. */
class StatsSchemaTest {
    private static final Gson GSON = new Gson();
    private static final String PLACEHOLDER = AlpakaStats.UNKNOWN_PROFILE;

    private static AlpakaStats record(String account, String profile, SlayerType type, int kills) {
        AlpakaStats stats = new AlpakaStats();
        AlpakaStats.Account entry = stats.accounts.computeIfAbsent(account, key -> new AlpakaStats.Account());
        entry.lastProfile = profile;
        AlpakaStats.ProfileStats profileStats = entry.profiles.computeIfAbsent(profile, key -> new AlpakaStats.ProfileStats());
        AlpakaConfig.SlayerData data = new AlpakaConfig.SlayerData();
        data.kills = kills;
        profileStats.slayerBossMap.put(type, data);
        return stats;
    }

    /** A file as a newer version of the mod writes it: fields and a slayer this version does not know. */
    private static final String NEWER_FILE = """
            {
              "schemaVersion": 7,
              "cloudSync": {"id": "abc"},
              "legacyImported": true,
              "accounts": {
                "main": {
                  "lastProfile": "kiwi",
                  "uiState": 3,
                  "profiles": {
                    "kiwi": {
                      "bestiary": {"zealot": 12},
                      "slayerBossMap": {
                        "BLAZE": {"kills": 40, "bestBossMsByTier": {"5": 60000}, "bestBossAtMsByTier": {"5": 1700000000000}},
                        "HUNTER": {"kills": 9},
                        "FISHER": {"kills": 2}
                      }
                    }
                  }
                }
              }
            }
            """;

    /** One save by this version over {@code diskText}: the typed merge, then what it does not know put back. */
    private static JsonObject saveOver(String diskText, AlpakaStats memory, String playing) {
        JsonObject disk = JsonParser.parseString(diskText).getAsJsonObject();
        AlpakaStats typed = GSON.fromJson(StatsMerge.knownOnly(disk), AlpakaStats.class);
        StatsMerge.mergeFromDisk(memory, typed, playing, PLACEHOLDER);
        memory.schemaVersion = AlpakaStats.SCHEMA_VERSION;
        JsonObject written = GSON.toJsonTree(memory).getAsJsonObject();
        StatsMerge.keepUnknown(disk, written);
        return written;
    }

    @Test
    void anOlderWriteKeepsWhatANewerVersionAdded() {
        AlpakaStats memory = record("main", "kiwi", SlayerType.BLAZE, 41);

        JsonObject written = saveOver(NEWER_FILE, memory, "main");

        assertEquals("abc", written.getAsJsonObject("cloudSync").get("id").getAsString());
        JsonObject account = written.getAsJsonObject("accounts").getAsJsonObject("main");
        assertEquals(3, account.get("uiState").getAsInt());
        JsonObject profile = account.getAsJsonObject("profiles").getAsJsonObject("kiwi");
        assertEquals(12, profile.getAsJsonObject("bestiary").get("zealot").getAsInt());
        JsonObject slayers = profile.getAsJsonObject("slayerBossMap");
        assertEquals(9, slayers.getAsJsonObject("HUNTER").get("kills").getAsInt());
        assertEquals(2, slayers.getAsJsonObject("FISHER").get("kills").getAsInt());
        JsonObject blaze = slayers.getAsJsonObject("BLAZE");
        assertEquals(41, blaze.get("kills").getAsInt(), "the known fields are still merged as before");
        assertEquals(60_000L, blaze.getAsJsonObject("bestBossMsByTier").get("5").getAsLong());
        assertEquals(1_700_000_000_000L, blaze.getAsJsonObject("bestBossAtMsByTier").get("5").getAsLong());
        assertFalse(slayers.has("null"));
        assertEquals(7, StatsMerge.schemaOf(written), "the layout never goes down");
    }

    @Test
    void unknownSlayersStayOutOfTheTypedRecord() {
        JsonObject disk = JsonParser.parseString(NEWER_FILE).getAsJsonObject();

        AlpakaStats typed = GSON.fromJson(StatsMerge.knownOnly(disk), AlpakaStats.class);

        Map<SlayerType, AlpakaConfig.SlayerData> slayers = typed.accounts.get("main").profiles.get("kiwi").slayerBossMap;
        assertEquals(1, slayers.size());
        assertEquals(40, slayers.get(SlayerType.BLAZE).kills);
        assertTrue(disk.getAsJsonObject("accounts").getAsJsonObject("main").getAsJsonObject("profiles")
                .getAsJsonObject("kiwi").getAsJsonObject("slayerBossMap").has("HUNTER"), "the file's own JSON is left alone");
    }

    @Test
    void keepUnknownBringsBackNothingThisVersionDecided() {
        // The playing account's placeholder is this session's to write, and a drop is a known entry:
        // neither may come back from the file just because the written record lacks it.
        String diskText = """
                {"accounts": {"main": {"profiles": {
                  "unknown-profile": {"slayerBossMap": {"WOLF": {"kills": 7}}},
                  "kiwi": {"slayerBossMap": {"WOLF": {"kills": 3, "drops": {"Overflux Capacitor": 2}}}}
                }}}}
                """;
        JsonObject disk = JsonParser.parseString(diskText).getAsJsonObject();
        JsonObject written = JsonParser.parseString("""
                {"schemaVersion": 2, "accounts": {"main": {"profiles": {
                  "kiwi": {"slayerBossMap": {"WOLF": {"kills": 3, "drops": {}}}}
                }}}}
                """).getAsJsonObject();

        StatsMerge.keepUnknown(disk, written);

        JsonObject profiles = written.getAsJsonObject("accounts").getAsJsonObject("main").getAsJsonObject("profiles");
        assertFalse(profiles.has(PLACEHOLDER));
        assertEquals(0, profiles.getAsJsonObject("kiwi").getAsJsonObject("slayerBossMap").getAsJsonObject("WOLF")
                .getAsJsonObject("drops").size());
        assertEquals(AlpakaStats.SCHEMA_VERSION, StatsMerge.schemaOf(written));
    }

    @Test
    void schemaOfAFileFromBeforeTheFieldIsOne() {
        assertEquals(1, StatsMerge.schemaOf(JsonParser.parseString("{\"accounts\": {}}").getAsJsonObject()));
        assertEquals(1, StatsMerge.schemaOf(JsonParser.parseString("{\"schemaVersion\": \"x\"}").getAsJsonObject()));
    }

    @Test
    void bestTimesAndXpCountAsProgress() {
        AlpakaConfig.SlayerData blank = new AlpakaConfig.SlayerData();
        assertFalse(AlpakaStats.hasProgress(Map.of(SlayerType.BLAZE, blank)));

        AlpakaConfig.SlayerData best = new AlpakaConfig.SlayerData();
        best.bestBossMsByTier.put(4, 31_000L);
        assertTrue(AlpakaStats.hasProgress(Map.of(SlayerType.BLAZE, best)), "a best time without the drop tracker was thrown away");

        AlpakaConfig.SlayerData xp = new AlpakaConfig.SlayerData();
        xp.totalXp = 12_500L;
        assertTrue(AlpakaStats.hasProgress(Map.of(SlayerType.BLAZE, xp)), "an XP reading without the drop tracker was thrown away");
    }
}
