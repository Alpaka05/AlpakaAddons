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
    public boolean fullbrightEnabled = false;
    public boolean nameHighlightingEnabled = true;
    public boolean inventorySnowEnabled = true;
    public float inventorySnowSpeed = 1.0f;
    public boolean cleanBlazeEnabled = true;
    public boolean smoothPerspectiveEnabled = true;
    public int smoothPerspectiveDurationMs = 350;
    public boolean customSoundsEnabled = true;
    public float customSoundsVolume = 1.0f;
    public boolean customSoundButtonClick = true;
    public boolean customSoundToggle = true;
    public boolean customSoundRareDrop = true;
    public boolean customSoundNotification = true;
    public boolean customEscapeMenuEnabled = true;
    public boolean playerModelEnabled = true;
    public boolean playerModelOnlyActions = true;
    public int playerModelScale = 30;
    public int playerModelX = 40;
    public int playerModelY = 85;
    public boolean playerModelDisableMovement = false;

    // Block Overlay options
    public boolean blockOverlayEnabled = false;
    public boolean blockOutlineEnabled = true;
    public float blockOutlineThickness = 2.0f;
    public int blockOutlineColor = 0xFF0000FF; // Default blue
    public boolean blockChromaEnabled = false;
    public float blockChromaSpeed = 1.0f;
    public boolean blockIgnoreDepth = false;
    public boolean blockFillEnabled = false;
    public int blockFillColor = 0x440000FF; // Default semi-transparent blue

    // Chat options
    public boolean expandChatHistory = true;

    // Item viewmodel options
    public boolean itemSizeFeatureEnabled = true;
    public float itemScale = 1.0f;
    public float itemXOffset = 0.0f;
    public float itemYOffset = 0.0f;
    public float itemZOffset = 0.0f;
    public float itemRotationX = 0.0f;
    public float itemRotationY = 0.0f;
    public float itemRotationZ = 0.0f;
    public float itemSwingSpeed = 1.0f;
    public boolean itemSwayDisabled = false;
    public boolean itemSwingTranslationDisabled = false;
    public boolean itemNoEquipEnabled = false;
    public boolean itemSwingAlwaysFinishEnabled = false;
    public boolean itemIgnoreEmptyHandEnabled = false;

    // Swing Animation Customizations
    public float swingDriftX = 0.0f;
    public float swingDriftY = 0.0f;
    public float swingDriftZ = 0.0f;
    public float swingArcX = 0.0f;
    public float swingArcY = 0.0f;
    public float swingArcZ = 0.0f;

    public static class ItemPreset {
        public float scale = 1.0f;
        public float xOffset = 0.0f;
        public float yOffset = 0.0f;
        public float zOffset = 0.0f;
        public float rotationX = 0.0f;
        public float rotationY = 0.0f;
        public float rotationZ = 0.0f;
        public float swingSpeed = 1.0f;
        public boolean swayDisabled = false;
        public boolean swingTranslationDisabled = false;
        public boolean noEquipEnabled = false;
        public boolean swingAlwaysFinishEnabled = false;
        public float swingDriftX = 0.0f;
        public float swingDriftY = 0.0f;
        public float swingDriftZ = 0.0f;
        public float swingArcX = 0.0f;
        public float swingArcY = 0.0f;
        public float swingArcZ = 0.0f;

        public ItemPreset() {}

        public ItemPreset(float scale, float xOffset, float yOffset, float zOffset, float rotationX, float rotationY, float rotationZ, float swingSpeed, boolean swayDisabled, boolean swingTranslationDisabled, boolean noEquipEnabled, boolean swingAlwaysFinishEnabled, float swingDriftX, float swingDriftY, float swingDriftZ, float swingArcX, float swingArcY, float swingArcZ) {
            this.scale = scale;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.zOffset = zOffset;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.swingSpeed = swingSpeed;
            this.swayDisabled = swayDisabled;
            this.swingTranslationDisabled = swingTranslationDisabled;
            this.noEquipEnabled = noEquipEnabled;
            this.swingAlwaysFinishEnabled = swingAlwaysFinishEnabled;
            this.swingDriftX = swingDriftX;
            this.swingDriftY = swingDriftY;
            this.swingDriftZ = swingDriftZ;
            this.swingArcX = swingArcX;
            this.swingArcY = swingArcY;
            this.swingArcZ = swingArcZ;
        }
    }

    public ItemPreset[] itemPresets = new ItemPreset[] {
        new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
        new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
        new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    };

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

        if (instance.itemPresets == null || instance.itemPresets.length < 3) {
            instance.itemPresets = new ItemPreset[] {
                new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
            };
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
