package net.alpaka.addons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.alpaka.addons.AlpakaAddons;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import net.alpaka.addons.features.slayer.SlayerType;
import java.util.HashMap;
import java.util.Map;

public class AlpakaConfig {
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("alpaka.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static AlpakaConfig instance = new AlpakaConfig();

    public boolean renderHandInThirdPerson = true;

    public boolean slayerDropTrackerEnabled = true;
    public boolean partyCommandsEnabled = true;
    public boolean nameHighlightingEnabled = true;
    public boolean inventorySnowEnabled = true;
    public float inventorySnowSpeed = 1.0f;
    public boolean cleanBlazeEnabled = true;
    public boolean smoothPerspectiveEnabled = true;
    public Map<SlayerType, SlayerData> slayerBossMap = new HashMap<>();

    public static class SlayerData {
        public int kills = 0;
        public Map<String, Integer> drops = new HashMap<>();
    }

    public AlpakaConfig() {
        for (SlayerType type : SlayerType.values()) {
            slayerBossMap.put(type, new SlayerData());
        }
    }

    public static void load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                instance = GSON.fromJson(reader, AlpakaConfig.class);
            } catch (Exception e) {
                AlpakaAddons.LOGGER.error("Failed to load config", e);
            }
        } else {
            save();
        }

        if (instance.slayerBossMap == null) {
            instance.slayerBossMap = new HashMap<>();
        }
        for (SlayerType type : SlayerType.values()) {
            if (!instance.slayerBossMap.containsKey(type)) {
                instance.slayerBossMap.put(type, new SlayerData());
            }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to save config", e);
        }
    }
}
