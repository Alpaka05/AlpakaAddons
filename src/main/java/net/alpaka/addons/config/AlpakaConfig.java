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
    /**
     * Keeps Hypixel's own drop line out of the chat log when the tracker reports that same drop,
     * so a slayer drop is announced once rather than twice.
     */
    public boolean hideHypixelDropMessage = true;
    /**
     * Post a slayer's headline RNG drop - the one the slayer HUD counts a dry streak against, e.g.
     * High Class Archfiend Dice for Inferno - to the guild chat too, in the same words the tracker
     * writes to the player's own chat.
     *
     * Off by default: it sends a message on the player's behalf, which is something to opt into.
     */
    public boolean slayerRngDropGuildChatEnabled = false;
    public boolean fullbrightEnabled = false;

    // Cosmetics. Both are drawn by this client for this client only - nothing is sent, nobody else
    // sees them - and both apply to the player's own model in third person.
    /** Draw the player's own name tag with effects instead of vanilla's (which never shows it). */
    public boolean customNameTagEnabled = false;
    /** Index into {@code CustomNameTagFeature.COLOR_MODE_NAMES}: vanilla, rainbow, gradient, pulse. */
    public int nameTagColorMode = 1;
    /** Index into {@code CustomNameTagFeature.MOTION_MODE_NAMES}: none, wave, bounce, shake. */
    public int nameTagMotionMode = 1;
    /** Apply the colour effect to the rank prefix as well, not only to the name itself. */
    public boolean nameTagColorWholeTag = false;
    public boolean nameTagOutlineEnabled = false;
    public boolean nameTagShadowEnabled = false;
    /** A thin frame around the backdrop whose colours sweep along it. */
    public boolean nameTagChromaBorderEnabled = true;
    /** Whether the dark box behind the letters is drawn at all. */
    public boolean nameTagBackgroundEnabled = true;
    /** Backdrop darkness in percent. Vanilla's default text background is 25. */
    public float nameTagBackgroundOpacity = 25.0f;
    public float nameTagScale = 1.0f;
    /** Extra height above vanilla's position, in blocks. Negative brings the tag down. */
    public float nameTagHeightOffset = 0.0f;
    public float nameTagAnimationSpeed = 1.0f;
    /** The two ends of the gradient colour mode. */
    public int nameTagGradientStart = 0xFF55FFFF;
    public int nameTagGradientEnd = 0xFFFF55FF;

    /** A translucent, chroma-lit samurai hat on the player's own head. */
    public boolean chromaHatEnabled = false;
    /** Hat opacity in percent. */
    public float chromaHatOpacity = 55.0f;
    public float chromaHatSize = 1.0f;
    public float chromaHatSpeed = 1.0f;
    /** Raises (positive) or lowers the hat from where it rests on the head, in blocks. */
    public float chromaHatHeightOffset = 0.0f;
    /**
     * Whether a notice slides in when somebody says the player's name in chat.
     *
     * On by default, unlike most of what the mod adds: it changes nothing about how the game looks
     * until the moment somebody actually addresses the player, which is a moment worth not missing.
     */
    public boolean mentionNotificationEnabled = true;
    /**
     * How long a notification stays on screen, in seconds, before it slides back out.
     *
     * Counts the still part only. The two slides are the same length whatever this is set to, since
     * they are how the notice arrives and leaves rather than part of how long it is readable.
     */
    public float notificationHoldSeconds = 4.5f;
    /**
     * Which screen corner notices slide in at. Index into {@code AlpakaNotifications.CORNER_NAMES}:
     * bottom right, bottom left, top right, top left.
     */
    public int notificationCorner = 0;
    /**
     * Whether a notice slides in when a slayer boss spawns.
     *
     * Separate from the boss timer even though the same moment drives both: one is a clock, this is
     * a nudge for somebody who was looking elsewhere while the quest finished spawning.
     */
    public boolean bossSpawnAlertEnabled = true;
    public boolean inventorySnowEnabled = true;
    public float inventorySnowSpeed = 1.0f;
    public float containerBgOpacity = 0.75f;
    public boolean containerBgFadeInEnabled = true;
    public int containerBgFadeInDurationMs = 250;
    public boolean cleanBlazeEnabled = true;
    /**
     * Hides Hypixel's slayer chatter - quest started/complete, level-ups, the radio lines.
     *
     * Used to be part of cleanBlazeEnabled, which forced a choice between the blaze effects and a
     * quiet chat. Defaults to true so that split changes nothing for anyone who had clean blaze on.
     */
    public boolean hideSlayerChatMessages = true;
    public boolean stopBlazeSpinning = true;
    public boolean smoothPerspectiveEnabled = true;
    public boolean disableFrontPerspective = false;
    public int smoothPerspectiveDurationMs = 350;
    public boolean customSoundsEnabled = false;
    public float customSoundsVolume = 1.0f;
    public boolean customSoundButtonClick = true;
    public boolean customSoundHotbarScroll = true;
    public boolean customSoundRareDrop = true;
    public boolean customSoundNotification = true;
    public boolean customSoundPlayerHurt = true;
    public boolean customSoundInventoryOpenClose = true;
    public boolean customSoundLowHpHeartbeat = true;
    public boolean customSoundBlazeDeath = true;
    public boolean customSoundInventoryClick = true;
    public boolean customSoundZombieRemedy = true;
    public boolean customSoundSuccessfulHit = true;
    public boolean muteVanillaSoundsInBlazeSlayer = false;
    public float lowHpHeartbeatThreshold = 0.30f;
    public int menuAccentColor = 0xFFE5B849; // Default Warm Gold
    public boolean customEscapeMenuEnabled = true;
    public boolean customMainMenuEnabled = false;
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
    public boolean worldAgeHudEnabled = false;
    public int worldAgeHudX = 10;
    public int worldAgeHudY = 10;
    public float worldAgeHudScale = 1.0f;

    // Slayer session HUD. Each line has its own toggle so the HUD can be trimmed to just the
    // numbers being watched; the whole HUD hides unless a slayer quest is active.
    public boolean slayerHudEnabled = false;
    public int slayerHudX = 10;
    public int slayerHudY = 60;
    public float slayerHudScale = 1.0f;
    public boolean slayerHudShowTitle = true;
    public boolean slayerHudShowTotalXp = true;
    public boolean slayerHudShowSessionXp = true;
    public boolean slayerHudShowXpPerHour = true;
    public boolean slayerHudShowAvgBossTime = true;
    public boolean slayerHudShowBossCount = true;
    public boolean slayerHudShowBossesPerHour = true;
    public boolean slayerHudShowSessionTime = true;
    public boolean slayerHudShowSinceRngDrop = true;
    /** Seconds of standing still before the session clock stops. */
    public float slayerHudAfkPauseSeconds = 60.0f;
    /** Stop the clock the moment the sidebar says the player is no longer in the slayer's zone. */
    public boolean slayerHudPauseOutsideArea = false;
    /**
     * Restrict the HUD to areas the slayer is actually run in, matching where SkyHanni's slayer
     * profit tracker appears. Off shows it wherever a quest is active, island and area regardless.
     */
    public boolean slayerHudOnlyInSlayerAreas = true;

    // Slayer boss timer. Times a single boss fight over the boss entity's own lifetime - the way
    // SkyHanni does - and remembers the fastest ever per slayer. The sidebar is only the fallback
    // for a fight where the boss entity could not be watched, so there is no setting for it.
    /**
     * Whether the mod may read public Hypixel data over the network.
     *
     * Everything else in this mod works from what the client already has. This is the one setting
     * that lets it reach outside, currently for a single endpoint describing the running election -
     * no API key, no player named, the same answer for everybody. Off means no request is made and
     * nothing derived from one is used.
     */
    public boolean allowApiCalls = true;

    /**
     * Folder holding the slayer record, or empty for this machine's shared default.
     *
     * Point it at a folder a cloud client keeps in sync and the record follows the player to another
     * PC - the file is already keyed by account and Skyblock profile, so two machines writing to it
     * describe the same history rather than competing ones. No server and no account of this mod's
     * own is involved; the sync is whatever the player already uses.
     *
     * Kept with the per-instance settings on purpose, and never inside the record itself: it is a
     * fact about this machine, and the path to the same synced folder differs on the next one.
     */
    public String statsDirectory = "";
    public boolean slayerTimerEnabled = true;
    /** Announce each boss's time in chat once it dies. */
    public boolean slayerTimerChatEnabled = true;
    /** Show the running time on screen while the boss is up. */
    public boolean slayerTimerHudEnabled = true;
    public int slayerTimerHudX = 10;
    public int slayerTimerHudY = 120;
    public float slayerTimerHudScale = 1.0f;
    public boolean worldAgeJoinMessageEnabled = true;
    public int worldAgeRecentThresholdSec = 60;
    public boolean onlyCritDamageEnabled = true;

    // Pangolin highlight (Torrhus Canyon critters)
    public boolean pangolinHighlightEnabled = false;
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

    // Inventory HUD. Shows the 27 main-inventory slots on screen; position and scale are set in
    // the HUD editor, or it can ride directly above the hotbar.
    public boolean inventoryHudEnabled = false;
    public int inventoryHudX = 10;
    public int inventoryHudY = 10;
    public float inventoryHudScale = 1.0f;
    public boolean inventoryHudAttachToHotbar = true;
    public boolean inventoryHudAlwaysVisible = true;
    public boolean inventoryHudShowOnItemChange = false;
    /** Backdrop strength behind the slots, in percent. 0 leaves only the accent frame. */
    public float inventoryHudBackgroundOpacity = 70.0f;
    /**
     * Whether the panel is drawn from Minecraft's own inventory texture instead of a flat backdrop.
     *
     * The point is that a resource pack reaches the HUD: the slots are cut out of
     * textures/gui/container/inventory.png, the same file the inventory screen uses, so whatever
     * pack is loaded paints them.
     *
     * Off restores the flat panel with the accent frame.
     */
    public boolean inventoryHudVanillaTexture = false;

    // Guild bridge-bot chat formatting. The name is the in-game account that relays Discord.
    public boolean bridgeBotFormatterEnabled = false;
    public String bridgeBotName = "";

    /**
     * Replace Hypixel's "Guild >" marker with the guild's own tag.
     *
     * The text may carry `&`-style colour codes, which are translated to section signs when the
     * line is drawn - `§` cannot be typed on a keyboard, so `&` is the convention every Hypixel mod
     * uses for this.
     */
    public boolean guildPrefixEnabled = false;
    public String guildPrefixText = "";

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
    public boolean itemSizeFeatureEnabled = false;
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

        /**
         * Fastest boss kill ever recorded for this slayer, in milliseconds, or -1 when none is.
         *
         * All-time rather than per session: a personal best that resets when the game restarts is
         * not a personal best. Lives here with the kill and drop history because this is the part of
         * the slayer data that is meant to persist.
         */
        public long bestBossMs = -1L;

        /**
         * Last known lifetime slayer XP for this slayer, or -1 when it has never been observed.
         *
         * Hypixel does not tell the client this figure during play - the only place it appears is
         * the Slayer menu's own item text - so it is remembered here once seen and the session's
         * own gains are added on top for display.
         */
        public long totalXp = -1L;

        /**
         * When the mod last added a boss kill's XP to {@link #totalXp}, as a wall-clock millisecond
         * timestamp, or 0 when it never has.
         *
         * Persisted because it decides whether a lower figure read from the Slayer menu is a stale
         * leaderboard entry or a real correction, and that question comes up right after relaunching
         * too. See SlayerXpTracker.
         */
        public long lastXpCreditedAtMs = 0L;
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
        this.hideHypixelDropMessage = false;
        this.slayerRngDropGuildChatEnabled = false;
        this.customNameTagEnabled = false;
        this.nameTagOutlineEnabled = false;
        this.nameTagShadowEnabled = false;
        this.nameTagChromaBorderEnabled = false;
        this.chromaHatEnabled = false;
        this.pangolinHighlightEnabled = false;
        this.fullbrightEnabled = false;
        this.inventorySnowEnabled = false;
        this.containerBgFadeInEnabled = false;
        this.cleanBlazeEnabled = false;
        this.hideSlayerChatMessages = false;
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
        this.customSoundBlazeDeath = false;
        this.customSoundInventoryClick = false;
        this.customSoundZombieRemedy = false;
        this.customSoundSuccessfulHit = false;
        this.muteVanillaSoundsInBlazeSlayer = false;
        this.customEscapeMenuEnabled = false;
        this.customMainMenuEnabled = false;
        this.playerModelEnabled = false;
        this.playerModelOnlyActions = false;
        this.playerModelDisableMovement = false;
        this.playerModelHideArmor = false;
        this.playerModelShowInGuis = false;
        this.playerModelSlowSwing = false;
        this.worldAgeHudEnabled = false;
        this.slayerHudEnabled = false;
        this.slayerHudPauseOutsideArea = false;
        this.slayerHudOnlyInSlayerAreas = false;
        this.allowApiCalls = false;
        this.slayerTimerEnabled = false;
        this.slayerTimerChatEnabled = false;
        this.slayerTimerHudEnabled = false;
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
        this.inventoryHudEnabled = false;
        this.bridgeBotFormatterEnabled = false;
        this.guildPrefixEnabled = false;
        this.itemSizeFeatureEnabled = false;
        this.itemSwayDisabled = false;
        this.itemSwingTranslationDisabled = false;
        this.itemNoEquipEnabled = false;
        this.itemSwingAlwaysFinishEnabled = false;
        this.itemIgnoreEmptyHandEnabled = false;
        save();
    }

    /**
     * While set, {@link #save()} only records that a write is due instead of performing it.
     *
     * Every option setter saves, which is right for a click but wrong for a drag: a slider fires its
     * setter on every mouse-move event, and each one serialised the whole config and wrote it to
     * disk from the render thread. Holding the writes back for the length of the drag turns a few
     * dozen file writes into one.
     */
    private static boolean deferSaves = false;
    private static boolean saveDueAfterDefer = false;

    /** Starts holding writes back. Must be paired with {@link #endDeferredSaves()}. */
    public static void beginDeferredSaves() {
        deferSaves = true;
    }

    /** Stops holding writes back, and performs the pending one if any setting changed meanwhile. */
    public static void endDeferredSaves() {
        deferSaves = false;
        if (saveDueAfterDefer) {
            saveDueAfterDefer = false;
            save();
        }
    }

    public static void save() {
        if (deferSaves) {
            saveDueAfterDefer = true;
            return;
        }
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to save config", e);
        }
    }
}
