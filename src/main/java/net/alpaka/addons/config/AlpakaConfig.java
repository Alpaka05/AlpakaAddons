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
    public float containerBgOpacity = 0.75f;
    public boolean containerBgFadeInEnabled = true;
    public int containerBgFadeInDurationMs = 250;
    public boolean cleanBlazeEnabled = true;
    public boolean stopBlazeSpinning = true;
    public boolean smoothPerspectiveEnabled = true;
    public boolean disableFrontPerspective = false;
    public int smoothPerspectiveDurationMs = 350;
    public boolean customSoundsEnabled = true;
    public float customSoundsVolume = 1.0f;
    public boolean customSoundButtonClick = true;
    public boolean customSoundHotbarScroll = true;
    public boolean customSoundRareDrop = true;
    public boolean customSoundNotification = true;
    public boolean customSoundPlayerHurt = true;
    public boolean customSoundInventoryOpenClose = true;
    public boolean customSoundLowHpHeartbeat = true;
    public float lowHpHeartbeatThreshold = 0.30f;
    public int menuAccentColor = 0xFFE5B849; // Default Warm Gold
    public boolean customEscapeMenuEnabled = true;
    public boolean customMainMenuEnabled = true;
    public boolean playerModelEnabled = true;
    public boolean playerModelOnlyActions = true;
    public int playerModelScale = 30;
    public int playerModelX = 40;
    public int playerModelY = 85;
    public boolean playerModelDisableMovement = false;
    public boolean playerModelHideArmor = false;
    public boolean playerModelShowInGuis = false;
    public boolean playerModelSlowSwing = false;

    // World Age HUD & Join Notification options
    public boolean worldAgeHudEnabled = true;
    public int worldAgeHudX = 10;
    public int worldAgeHudY = 10;
    public float worldAgeHudScale = 1.0f;
    public boolean worldAgeJoinMessageEnabled = true;
    public int worldAgeRecentThresholdSec = 60;
    public boolean onlyCritDamageEnabled = true;

    // Pangolin highlight (Torrhus Canyon critters)
    public boolean pangolinHighlightEnabled = true;
    public int pangolinHighlightColor = 0xFFFFAA00; // Default orange/gold

    // Block Overlay options
    public boolean blockOverlayEnabled = false;
    public boolean blockFadeInEnabled = true;
    public int blockFadeInDurationMs = 450;
    public boolean blockOutlineEnabled = true;
    public float blockOutlineThickness = 2.0f;
    public int blockOutlineColor = 0xFF0000FF; // Default blue
    public boolean blockChromaEnabled = false;
    public float blockChromaSpeed = 1.0f;
    public boolean blockIgnoreDepth = false;
    public boolean blockIgnorePlants = false;
    public boolean blockFillEnabled = false;
    public int blockFillColor = 0x440000FF; // Default semi-transparent blue
    public boolean blockHideOnEtherwarp = false;

    // Chat options
    public boolean expandChatHistory = true;

    // Quick Command Menu options
    public java.util.List<String> commandWheelCommands = new java.util.ArrayList<>(java.util.List.of(
            "/hub",
            "/island",
            "/warp dh",
            "/wardrobe",
            "/pets",
            "/pv"
    ));

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

    public int activeItemPresetIndex = 0;

    public void loadPreset(int index) {
        if (itemPresets != null && index >= 0 && index < itemPresets.length) {
            ItemPreset preset = itemPresets[index];
            this.itemScale = preset.scale;
            this.itemXOffset = preset.xOffset;
            this.itemYOffset = preset.yOffset;
            this.itemZOffset = preset.zOffset;
            this.itemRotationX = preset.rotationX;
            this.itemRotationY = preset.rotationY;
            this.itemRotationZ = preset.rotationZ;
            this.itemSwingSpeed = preset.swingSpeed;
            this.itemSwayDisabled = preset.swayDisabled;
            this.itemSwingTranslationDisabled = preset.swingTranslationDisabled;
            this.itemNoEquipEnabled = preset.noEquipEnabled;
            this.itemSwingAlwaysFinishEnabled = preset.swingAlwaysFinishEnabled;
            this.swingDriftX = preset.swingDriftX;
            this.swingDriftY = preset.swingDriftY;
            this.swingDriftZ = preset.swingDriftZ;
            this.swingArcX = preset.swingArcX;
            this.swingArcY = preset.swingArcY;
            this.swingArcZ = preset.swingArcZ;
            this.activeItemPresetIndex = index;
            save();
        }
    }

    /**
     * Restores the item viewmodel and swing values to their defaults.
     *
     * The saved presets and the active preset index are deliberately left alone, so this is a way
     * back to stock without losing tuned setups - a preset can be re-applied afterwards. The
     * feature's own master toggle ({@link #itemSizeFeatureEnabled}) is also left as-is, since
     * resetting values should not switch a feature back on behind the user's back.
     */
    public void resetItemViewmodel() {
        this.itemScale = 1.0f;
        this.itemXOffset = 0.0f;
        this.itemYOffset = 0.0f;
        this.itemZOffset = 0.0f;
        this.itemRotationX = 0.0f;
        this.itemRotationY = 0.0f;
        this.itemRotationZ = 0.0f;
        this.itemSwingSpeed = 1.0f;
        this.itemSwayDisabled = false;
        this.itemSwingTranslationDisabled = false;
        this.itemNoEquipEnabled = false;
        this.itemSwingAlwaysFinishEnabled = false;
        this.itemIgnoreEmptyHandEnabled = false;

        this.swingDriftX = 0.0f;
        this.swingDriftY = 0.0f;
        this.swingDriftZ = 0.0f;
        this.swingArcX = 0.0f;
        this.swingArcY = 0.0f;
        this.swingArcZ = 0.0f;

        save();
    }

    public void savePreset(int index) {
        if (itemPresets == null || itemPresets.length < 3) {
            itemPresets = new ItemPreset[] {
                new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                new ItemPreset(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, false, false, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
            };
        }
        if (index >= 0 && index < itemPresets.length) {
            itemPresets[index] = new ItemPreset(
                    this.itemScale,
                    this.itemXOffset,
                    this.itemYOffset,
                    this.itemZOffset,
                    this.itemRotationX,
                    this.itemRotationY,
                    this.itemRotationZ,
                    this.itemSwingSpeed,
                    this.itemSwayDisabled,
                    this.itemSwingTranslationDisabled,
                    this.itemNoEquipEnabled,
                    this.itemSwingAlwaysFinishEnabled,
                    this.swingDriftX,
                    this.swingDriftY,
                    this.swingDriftZ,
                    this.swingArcX,
                    this.swingArcY,
                    this.swingArcZ
            );
            this.activeItemPresetIndex = index;
            save();
        }
    }

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

    public void disableAllFeatures() {
        this.renderHandInThirdPerson = false;
        this.slayerDropTrackerEnabled = false;
        this.pangolinHighlightEnabled = false;
        this.fullbrightEnabled = false;
        this.nameHighlightingEnabled = false;
        this.inventorySnowEnabled = false;
        this.containerBgFadeInEnabled = false;
        this.cleanBlazeEnabled = false;
        this.stopBlazeSpinning = false;
        this.smoothPerspectiveEnabled = false;
        this.disableFrontPerspective = false;
        this.customSoundsEnabled = false;
        this.customSoundButtonClick = false;
        this.customSoundHotbarScroll = false;
        this.customSoundRareDrop = false;
        this.customSoundNotification = false;
        this.customSoundPlayerHurt = false;
        this.customSoundInventoryOpenClose = false;
        this.customSoundLowHpHeartbeat = false;
        this.customEscapeMenuEnabled = false;
        this.customMainMenuEnabled = false;
        this.playerModelEnabled = false;
        this.playerModelOnlyActions = false;
        this.playerModelDisableMovement = false;
        this.playerModelHideArmor = false;
        this.playerModelShowInGuis = false;
        this.playerModelSlowSwing = false;
        this.worldAgeHudEnabled = false;
        this.worldAgeJoinMessageEnabled = false;
        this.onlyCritDamageEnabled = false;
        this.blockOverlayEnabled = false;
        this.blockFadeInEnabled = false;
        this.blockOutlineEnabled = false;
        this.blockChromaEnabled = false;
        this.blockIgnoreDepth = false;
        this.blockIgnorePlants = false;
        this.blockFillEnabled = false;
        this.blockHideOnEtherwarp = false;
        this.expandChatHistory = false;
        this.itemSizeFeatureEnabled = false;
        this.itemSwayDisabled = false;
        this.itemSwingTranslationDisabled = false;
        this.itemNoEquipEnabled = false;
        this.itemSwingAlwaysFinishEnabled = false;
        this.itemIgnoreEmptyHandEnabled = false;
        save();
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to save config", e);
        }
    }
}
