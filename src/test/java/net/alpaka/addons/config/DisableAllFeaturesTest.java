package net.alpaka.addons.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What Disable All Features switches off, read from the source of AlpakaConfig.disableAllFeatures.
 *
 * It has to switch off every feature and nothing else. A feature's settings, and every font, color
 * and slider, stay as they are, so that a feature switched back on is the way it was. The method
 * used to switch the settings off and reset values as well, and the backup made that stick after
 * the next start: Block Overlay came back without its outline, Custom Sounds came back silent.
 * AlpakaConfig itself cannot be initialized outside the game, hence the source.
 */
class DisableAllFeaturesTest {
    private static final Path SOURCE = Path.of("src/main/java/net/alpaka/addons/config/AlpakaConfig.java");
    private static final Pattern ASSIGNMENT = Pattern.compile("this\\.(\\w+)\\s*=\\s*([^;]+);");

    /**
     * Every feature's own switch, and the lone switches that belong to no feature. A new feature puts
     * its switch both here and in the method.
     */
    private static final Set<String> SWITCHES = Set.of(
            // General
            "allowApiCalls", "updateNotificationsEnabled", "customEscapeMenuEnabled", "customMainMenuEnabled",
            // Visuals
            "fullbrightEnabled", "cleanBlazeEnabled", "blazeFirePitsEnabled", "stopBlazeSpinning",
            "hideHurtOverlayEnabled", "hideMobDeathsEnabled", "hideEntityFire", "firstPersonFireStatic",
            "nameTagTextShadow", "showOwnNameTag", "renderHandInThirdPerson", "smoothPerspectiveEnabled",
            "disableFrontPerspective", "containerBgFadeInEnabled", "inventorySnowEnabled",
            "scrollableTooltipsEnabled", "persistentItemNameEnabled", "oldMasterStarsEnabled",
            "wideHealthBarEnabled", "betterChatEnabled", "chatSearchEnabled", "expandChatHistory",
            "compactChatEnabled", "mentionNotificationEnabled", "chatPeekEnabled", "chatTabsEnabled",
            "betterScreenshotMessageEnabled", "playerModelEnabled", "performanceHudEnabled",
            "inventoryHudEnabled", "scoreboardBlurEnabled", "scoreboardAlpakaFooter",
            "itemSizeFeatureEnabled", "itemStarlightEnabled", "itemMotionBlurEnabled", "blockOverlayEnabled",
            "etherwarpOverlayEnabled", "etherwarpSoundEnabled",
            // SkyBlock
            "slayerDropTrackerEnabled", "hideHypixelDropMessage", "slayerRngDropGuildChatEnabled",
            "hideSlayerChatMessages", "bossSpawnAlertEnabled", "bossCocoonAlertEnabled", "slayerTimerEnabled",
            "slayerHudEnabled", "worldAgeJoinMessageEnabled", "partyInvitePromptEnabled",
            "cacheResourcePackEnabled", "guildPrefixEnabled", "bridgeBotFormatterEnabled", "ragnarockEnabled",
            "onlyCritDamageEnabled", "deployableTimerEnabled",
            // Cosmetics
            "customNameTagEnabled", "chromaHatEnabled", "playerScaleEnabled", "customDamageTagsEnabled",
            // Utility and sounds
            "alwaysSprintEnabled", "customSoundsEnabled", "muteVanillaSoundsInBlazeSlayer"
    );

    /**
     * Settings that only act while their feature is on, and values. Disable All must leave them, or
     * switching the feature back on brings it back changed.
     */
    private static final Set<String> KEPT = Set.of(
            // Block Overlay
            "blockOutlineEnabled", "blockFillEnabled", "blockFadeInEnabled", "blockChromaEnabled",
            "blockIgnoreDepth", "blockIgnorePlants", "blockHideOnEtherwarp",
            // Custom Sounds: on main, every sound is a part of that one feature
            "customSoundButtonClick", "customSoundHotbarScroll", "customSoundInventoryOpenClose",
            "customSoundInventoryClick", "customSoundRareDrop", "customSoundNotification",
            "customSoundPlayerHurt", "customSoundBlazeDeath", "customSoundZombieRemedy",
            "customSoundSuccessfulHit", "customSoundLowHpHeartbeat",
            // Better Chat, Chat Peek, Chat Tabs, screenshots
            "smoothChatEnabled", "chatOpenAnimationEnabled", "chatSmoothScrollEnabled", "chatBlurEnabled",
            "chatScrollbarEnabled", "chatMovable", "chatPeekInMenus", "chatTabsSendToChannel",
            "autoCopyScreenshots",
            // Slayer timer and session HUD
            "slayerTimerChatEnabled", "slayerTimerHudEnabled", "slayerHudOnlyInSlayerAreas",
            "slayerHudPauseOutsideArea",
            // Player Model, Clean Blaze, Etherwarp Overlay, Own Name Tag, Player Scale
            "playerModelOnlyActions", "playerModelDisableMovement", "playerModelHideArmor",
            "playerModelShowInGuis", "playerModelShowNameTag", "cleanBlazeNameTags", "etherwarpLineEnabled",
            "nameTagOutlineEnabled", "nameTagShadowEnabled", "nameTagChromaBorderEnabled", "playerScaleOthers",
            // Viewmodel
            "itemSwayDisabled", "itemLightingDisabled", "itemNoEquipEnabled", "itemIgnoreEmptyHandEnabled",
            "itemSwingTranslationDisabled", "itemSwingAlwaysFinishEnabled",
            // Values
            "menuFont", "menuAccentColor", "nameTagBackgroundColor", "firstPersonFireOpacity",
            "firstPersonFireHeight", "containerBgOpacity"
    );

    /** Field name to the value disableAllFeatures assigns it, in source order. */
    private static Map<String, String> assignments() throws IOException {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("public void disableAllFeatures()");
        assertTrue(start >= 0, "disableAllFeatures not found in " + SOURCE);
        int open = source.indexOf('{', start);
        int depth = 0;
        int end = open;
        do {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        } while (depth > 0);
        Map<String, String> assigned = new LinkedHashMap<>();
        Matcher matcher = ASSIGNMENT.matcher(source.substring(open, end));
        while (matcher.find()) assigned.put(matcher.group(1), matcher.group(2).trim());
        return assigned;
    }

    @Test
    void everyFeatureIsSwitchedOff() throws IOException {
        Set<String> missing = new TreeSet<>(SWITCHES);
        missing.removeAll(assignments().keySet());
        assertEquals(Set.of(), missing, "Disable All Features leaves these features on");
    }

    @Test
    void settingsAndValuesAreLeftAlone() throws IOException {
        Set<String> touched = new TreeSet<>(assignments().keySet());
        touched.retainAll(KEPT);
        assertEquals(Set.of(), touched, "Disable All Features changes settings that should stay");
    }

    @Test
    void itOnlySwitchesThingsOff() throws IOException {
        Map<String, String> notOff = new LinkedHashMap<>();
        assignments().forEach((field, value) -> {
            if (!value.equals("false")) notOff.put(field, value);
        });
        assertEquals(Map.of(), notOff, "Disable All Features sets values instead of switching features off");
    }

    @Test
    void theListsNameRealFields() throws Exception {
        Class<?> config = Class.forName("net.alpaka.addons.config.AlpakaConfig", false,
                DisableAllFeaturesTest.class.getClassLoader());
        for (String name : SWITCHES) {
            Field field = config.getField(name);
            assertEquals(boolean.class, field.getType(), name + " is not a switch");
        }
        for (String name : KEPT) config.getField(name);
        Set<String> both = new TreeSet<>(SWITCHES);
        both.retainAll(KEPT);
        assertEquals(Set.of(), both, "listed as a switch and as a setting");
    }
}
