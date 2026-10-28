package net.alpaka.addons.config;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

import net.alpaka.addons.features.slayer.SlayerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlpakaConfig {
    /**
     * Where the settings are kept: the file, and the last file that loaded cleanly, copied aside at
     * start-up (see {@link #load()}).
     */
    record ConfigStore(File file, File backup) {
        /** alpaka.json in Fabric's config folder. */
        static ConfigStore inConfigDir() {
            java.nio.file.Path dir = FabricLoader.getInstance().getConfigDir();
            return new ConfigStore(dir.resolve("alpaka.json").toFile(), dir.resolve("alpaka.json.bak").toFile());
        }
    }

    /**
     * Looked up on first use rather than when the class loads: asking Fabric for its config folder
     * then kept the class - and loading and migrating with it - from being used outside the game.
     */
    private static ConfigStore store;

    private static ConfigStore store() {
        if (store == null) store = ConfigStore.inConfigDir();
        return store;
    }

    // NaN and infinite floats are written as such rather than refused. One in the file loaded
    // without complaint, and every save after it threw - quietly, so no setting was written again.
    // sanitize() puts such a value back to its default on load; this keeps one that slips in later
    // from blocking the file.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

    public static AlpakaConfig instance = new AlpakaConfig();

    /** The settings layout the file was written in; 0 for files from before the field. See {@link #migrate()}. */
    public int configVersion = 0;

    /**
     * Draws the first-person hand over the third-person back view. Off for fresh installs: a
     * floating arm in F5 surprised players who never asked for it.
     */
    public boolean renderHandInThirdPerson = false;

    public boolean slayerDropTrackerEnabled = true;
    /**
     * Keeps Hypixel's own drop line out of the chat log when the tracker reports that same drop,
     * so a slayer drop is announced once rather than twice.
     */
    public boolean hideHypixelDropMessage = true;
    /**
     * Adds "Share: [Guild] [Party]" buttons to the tracker's line for a slayer's headline RNG drop -
     * the one the slayer HUD counts a dry streak against, e.g. High Class Archfiend Dice for Inferno.
     *
     * A click only fills the chat box; the player sends it with Enter. This used to post to guild
     * chat by itself, which sent a message without any action from the player. The key keeps its old
     * name so the setting carries over for players who had turned the announcement on.
     */
    public boolean slayerRngDropGuildChatEnabled = false;
    /** What those share buttons post: 0 the tracker's own line, 1 Hypixel's drop message as it came. */
    public int slayerShareMessage = 0;
    public boolean fullbrightEnabled = false;
    /** Sky Effects: animated skies, drawn by this client only. Each sky has its own switch. */
    public boolean skyEffectsEnabled = false;
    public boolean skyEffectsNether = true;
    public boolean skyEffectsOverworld = true;
    public boolean skyEffectsEnd = true;
    /** How strongly the effects show, 0.1..1; at 1 the skies are drawn in full. */
    public float skyEffectsIntensity = 1.0f;
    /** How fast they move, 0.25..3; 1 is the designed pace. */
    public float skyEffectsSpeed = 1.0f;

    // Cosmetics. All are drawn by this client for this client only - nothing is sent, nobody else
    // sees them - and both apply to the player's own model in third person.
    /** Draw the player's own name tag with effects instead of vanilla's (which never shows it). */
    public boolean customNameTagEnabled = false;
    /** Index into {@code CustomNameTagFeature.COLOR_MODE_NAMES}: vanilla, rainbow, gradient, pulse. */
    public int nameTagColorMode = 1;
    /** Index into {@code CustomNameTagFeature.MOTION_MODE_NAMES}: none, wave, bounce, shake. */
    public int nameTagMotionMode = 1;
    /** Apply the color effect to the rank prefix as well, not only to the name itself. */
    public boolean nameTagColorWholeTag = false;
    public boolean nameTagOutlineEnabled = false;
    public boolean nameTagShadowEnabled = false;
    /** A thin frame around the backdrop whose colors sweep along it. */
    public boolean nameTagChromaBorderEnabled = true;
    /** Whether the dark box behind the letters is drawn at all. */
    public boolean nameTagBackgroundEnabled = true;
    /** Backdrop darkness in percent. Vanilla's default text background is 25. */
    public float nameTagBackgroundOpacity = 25.0f;
    public float nameTagScale = 1.0f;
    /** Extra height above vanilla's position, in blocks. Negative brings the tag down. */
    public float nameTagHeightOffset = 0.0f;
    public float nameTagAnimationSpeed = 1.0f;
    /** The two ends of the gradient color mode. */
    public int nameTagGradientStart = 0xFF55FFFF;
    public int nameTagGradientEnd = 0xFFFF55FF;

    /** A translucent sensei hat on the player's own head. Plain straw by default; see chromaHatRainbow. */
    public boolean chromaHatEnabled = false;
    /** Rainbow colors sweeping around the hat, self-lit, instead of the plain color. */
    public boolean chromaHatRainbow = false;
    /** Color of the plain hat. Default: straw. */
    public int chromaHatColor = 0xFFC8A65A;
    /** Hat opacity in percent. */
    public float chromaHatOpacity = 55.0f;
    public float chromaHatSize = 1.0f;
    public float chromaHatSpeed = 1.0f;
    /** Raises (positive) or lowers the hat from where it rests on the head, in blocks. */
    public float chromaHatHeightOffset = 0.0f;

    /** An animated aura of light around the player's own model in third person. */
    public boolean auraEnabled = false;
    /** Index into {@code AuraFeature.EFFECT_NAMES}: spirit orbs, helix, sigil, inferno, galaxy, fireflies, storm. */
    public int auraEffect = 0;
    /** Index into {@code AuraFeature.COLOR_MODE_NAMES}: the effect's own colors, rainbow, or the two below. */
    public int auraColorMode = 0;
    /** The two ends of the aura's gradient in the Custom color mode. */
    public int auraPrimaryColor = 0xFF55FFFF;
    public int auraSecondaryColor = 0xFFFF55FF;
    /** How far the aura reaches out from the player, as a factor. */
    public float auraSize = 1.0f;
    public float auraSpeed = 1.0f;
    /** How strongly the aura glows, in percent. */
    public float auraBrightness = 80.0f;

    // Player scale. The model is drawn stretched or shrunk per axis, in the world only; the hitbox,
    // eye height, camera and reach are untouched, and nothing is sent, so nobody else sees it.
    public boolean playerScaleEnabled = false;
    /** Width across the shoulders, as a factor of vanilla. */
    public float playerScaleX = 1.0f;
    /** Height, feet to head. The feet stay on the ground. */
    public float playerScaleY = 1.0f;
    /** Depth, front to back. */
    public float playerScaleZ = 1.0f;
    /** Draw every other player at the same scale too, not only the player's own model. */
    public boolean playerScaleOthers = false;
    /**
     * Whether a notice slides in when somebody says the player's name in chat.
     *
     * On by default, unlike most of what the mod adds: it changes nothing about how the game looks
     * until the moment somebody actually addresses the player, which is a moment worth not missing.
     */
    public boolean mentionNotificationEnabled = true;
    /** Only mentions from real players: lines from NPCs, bosses and the server itself are ignored. */
    public boolean mentionIgnoreServer = true;
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
    /**
     * Whether a notice slides in when the slayer boss spawns inside a cocoon.
     *
     * The cocoon takes a few seconds to hatch, so this comes first and the spawn alert follows once
     * the boss is actually up.
     */
    public boolean bossCocoonAlertEnabled = true;
    /** Snow over container screens and the main menu. Off for fresh installs; it is seasonal decoration. */
    public boolean inventorySnowEnabled = false;
    public float inventorySnowSpeed = 1.0f;
    /** Multiplier on the number of flakes; 1 is the original 105. */
    public float inventorySnowAmount = 1.0f;
    /** Which Inventory Particles are shown, an index into SnowOverlayRenderer.TYPE_NAMES; 0 is snow. */
    public int inventoryParticleType = 0;
    public float containerBgOpacity = 0.75f;
    public boolean containerBgFadeInEnabled = true;
    public int containerBgFadeInDurationMs = 250;
    /** Hides the potion effect list vanilla draws next to the inventory. */
    public boolean hideInventoryEffects = false;
    /**
     * Clean Blaze, only on SkyBlock in the Blaze slayer's zones. The fields after it pick what it
     * hides.
     */
    public boolean cleanBlazeEnabled = true;
    public boolean cleanBlazeParticles = true;
    /** The potion swirls, enchanting glyphs and ender particles around the boss and its demons. */
    public boolean cleanBlazeEffectParticles = true;
    /** The dust in the Hellion Shield attunement's colour around the boss and its demons. */
    public boolean cleanBlazeAttunementDust = true;
    public boolean cleanBlazeFire = true;
    public boolean cleanBlazeFireballs = true;
    public boolean cleanBlazeLightning = true;
    /** Off by default: the health on blaze name tags is what a grinder watches. */
    public boolean cleanBlazeNameTags = false;
    /** "Strike using the ... attunement on your dagger!" lines in the Blaze zones. */
    public boolean cleanBlazeWrongDaggerChat = true;
    /** "Your hit was reduced by Hellion Shield!" and "You took ... damage from ..." in the Blaze zones. */
    public boolean cleanBlazeDamageChat = true;
    /** During the player's own Blaze slayer boss, a glowing column over each fire pit block. */
    public boolean blazeFirePitsEnabled = true;
    /** ARGB color of the fire pit column; its alpha scales the column's own fade. Default hot red-orange. */
    public int blazeFirePitColor = 0xFFFF4714;
    /**
     * Hides Hypixel's slayer chatter - quest started/complete, level-ups, the radio lines.
     *
     * Used to be part of cleanBlazeEnabled, which forced a choice between the blaze effects and a
     * quiet chat. Defaults to true so that split changes nothing for anyone who had clean blaze on.
     */
    public boolean hideSlayerChatMessages = true;
    /** Which groups of slayer chatter hideSlayerChatMessages hides. All on, as before the split. */
    public boolean hideSlayerChatQuest = true;
    public boolean hideSlayerChatRadio = true;
    public boolean hideSlayerChatLevelUp = true;
    public boolean hideSlayerChatHealing = true;
    public boolean hideSlayerChatSunshade = true;
    public boolean stopBlazeSpinning = true;
    /** Hides the red hurt flash on mobs and players. Cosmetic only, the hit itself is untouched. */
    public boolean hideHurtOverlayEnabled = false;
    /** Stops drawing a mob the moment it dies, skipping vanilla's tip-over. Cosmetic only. */
    public boolean hideMobDeathsEnabled = false;
    /** With Hide Mob Deaths: also no puff of smoke when the server removes the body. */
    public boolean hideMobDeathSmoke = false;
    /** Hides explosion particles, server smoke puffs and explosion debris. Cosmetic only. */
    public boolean hideExplosions = false;
    /** Hides the flames on burning mobs, players and yourself in third person. Cosmetic only. */
    public boolean hideEntityFire = false;
    /** Opacity of the fire covering the screen while you burn in first person, 0..1; 1 is vanilla, 0 hides it. */
    public float firstPersonFireOpacity = 1.0f;
    /** How high that fire reaches up the screen, 0..1 of vanilla's height; 1 is vanilla. */
    public float firstPersonFireHeight = 1.0f;
    /** Draws that fire as one still frame instead of the flickering animation. */
    public boolean firstPersonFireStatic = false;
    /**
     * The box behind every nametag. The default is vanilla's own box, which then stays vanilla's,
     * accessibility opacity setting included; zero opacity drops the box and leaves only the text.
     */
    public int nameTagBackgroundColor = DEFAULT_NAME_TAG_BACKGROUND;
    public static final int DEFAULT_NAME_TAG_BACKGROUND = 0x3F000000;
    /** Legacy, before the background color. Read once on load and then cleared. */
    public Boolean hideNameTagBackground = null;
    /** Shows the player their own vanilla nametag in third person. */
    public boolean showOwnNameTag = false;
    /** Gives the text of every name tag the usual drop shadow, which vanilla leaves off in the world. */
    public boolean nameTagTextShadow = false;
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
    /**
     * With the Blaze slayer mute on: every world sound instead of only the fight's own noise
     * (blazes, fire, lava, ghasts, lightning). The UI, master, voice and music channels never are.
     */
    public boolean blazeMuteAllWorldAudio = false;
    public float lowHpHeartbeatThreshold = 0.30f;
    public int menuAccentColor = 0xFF29B6B2; // Default teal
    /** Index into {@code AlpakaStyle.Theme}: how the Alpaka menus look. 0 is the dark default. */
    public int menuTheme = 0;
    /** The player's own presets in the color picker, ARGB, oldest first; shared by every color option. */
    public java.util.List<Integer> customColorPresets = new java.util.ArrayList<>();
    /**
     * Index into {@code GuiFont.NAMES}: the typeface the Alpaka menus are written in. 0 is the
     * vanilla pixel font; the default is Poppins.
     */
    public int menuFont = 2;
    /** Set once a config still on the old default font (Inter) has been moved to the new default. */
    public boolean menuFontNunitoApplied = false;
    /** Chat messages in the menu font rather than the pixel font. */
    public boolean menuFontInChat = false;
    /** The scoreboard sidebar in the menu font rather than the pixel font. */
    public boolean menuFontInScoreboard = false;
    /**
     * The mod's own HUDs: each on a blurred panel like the chat's, and each in the menu font, both
     * per HUD. See HudPanel.
     */
    public boolean slayerHudBlur = true;
    public boolean slayerTimerHudBlur = true;
    public boolean performanceHudBlur = true;
    public boolean notificationBlur = true;
    public boolean slayerHudSmoothFont = true;
    public boolean slayerTimerHudSmoothFont = true;
    public boolean performanceHudSmoothFont = true;
    public boolean notificationSmoothFont = true;
    public boolean customEscapeMenuEnabled = true;
    public boolean customMainMenuEnabled = false;
    /** Inventory Particles behind the custom main menu as well; off keeps them to container screens. */
    public boolean customMainMenuParticles = false;
    /** The small player model on the HUD. Off for fresh installs. */
    public boolean playerModelEnabled = false;
    public boolean playerModelOnlyActions = true;
    public int playerModelScale = 30;
    public int playerModelX = net.alpaka.addons.client.hud.HudDefaults.PLAYER_MODEL_X;
    public int playerModelY = net.alpaka.addons.client.hud.HudDefaults.PLAYER_MODEL_Y;
    public boolean playerModelDisableMovement = false;
    public boolean playerModelHideArmor = false;
    /** The name tag above the HUD avatar. */
    public boolean playerModelShowNameTag = true;
    public boolean playerModelShowInGuis = false;

    /**
     * Legacy, from the World Age HUD, which became the Performance HUD's Show Day. Read once by
     * {@link #migrateWorldAgeHud} and then cleared.
     */
    public Boolean worldAgeHudEnabled = null;
    public Integer worldAgeHudX = null;
    public Integer worldAgeHudY = null;
    public Float worldAgeHudScale = null;
    public Boolean worldAgeHudBlur = null;
    public Boolean worldAgeHudSmoothFont = null;

    /** Performance HUD: the day, FPS, ping and the server's TPS on one line; see PerformanceStats. */
    public boolean performanceHudEnabled = false;
    public boolean performanceHudShowDay = true;
    public boolean performanceHudShowFps = true;
    public boolean performanceHudShowPing = true;
    public boolean performanceHudShowTps = true;
    public boolean performanceHudAnimate = false;
    public int performanceHudX = net.alpaka.addons.client.hud.HudDefaults.PERFORMANCE_X;
    public int performanceHudY = net.alpaka.addons.client.hud.HudDefaults.PERFORMANCE_Y;
    public float performanceHudScale = 1.0f;

    // Slayer session HUD. Each line has its own toggle so the HUD can be trimmed to just the
    // numbers being watched; the whole HUD hides unless a slayer quest is active.
    /** The slayer session HUD, the mod's flagship. On for fresh installs. */
    public boolean slayerHudEnabled = true;
    public int slayerHudX = net.alpaka.addons.client.hud.HudDefaults.SLAYER_SESSION_X;
    public int slayerHudY = net.alpaka.addons.client.hud.HudDefaults.SLAYER_SESSION_Y;
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
    /** The "Bear" line: time left on Smoldering Polarization, on every slayer's HUD. */
    public boolean slayerHudShowBear = true;
    /** Keeps the "Bear" line to the Blaze HUD, where the effect matters most. */
    public boolean slayerHudBearBlazeOnly = false;
    /** The "Radio" line on the Blaze HUD: players on the Blazetekk Ham Radio's channel. */
    public boolean slayerHudShowRadio = true;
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
     * Whether the mod may contact anything over the network ("Network Features").
     *
     * Everything else in this mod works from what the client already has. This is the one setting
     * that lets it reach outside, for four things: a public endpoint describing the running election
     * (no API key, no player named, the same answer for everybody), the main menu's player
     * count, an ordinary status ping to mc.hypixel.net like the server list sends, the update
     * check against the mod's public GitHub releases, and the main menu's What's New card, which
     * reads the newest of those releases' notes. Off means no
     * request is made and nothing derived from one is used. The JSON key keeps its old name.
     */
    public boolean allowApiCalls = true;
    /** A chat line on joining a world when a newer release is on GitHub. Needs allowApiCalls. */
    public boolean updateNotificationsEnabled = true;
    /** Which releases the update check offers: 0 full releases only, 1 pre-releases as well (UpdateChecker.STREAM_NAMES). */
    public int updateStream = 0;
    /** The release whose notes the player last opened from the main menu; its What's New card shows a dot until then. */
    public String releaseNotesSeen = "";
    /** The What's New card on the custom main menu, with the newest release's notes. */
    public boolean customMainMenuWhatsNew = true;

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
    /**
     * Whether the record has been read from or written to {@link #statsDirectory} before.
     *
     * A chosen folder that is there but no longer holds the file it once held is most likely a
     * cloud folder still syncing, so the record waits for the file instead of starting from zero and
     * then writing that over the real one. Reset whenever the folder setting changes.
     */
    public boolean statsDirectoryHadFile = false;
    /**
     * The mod version that last ran with this config, so an update can say once what changed.
     * Empty in a config written before this field existed. See StartupNotices.
     */
    public String lastSeenVersion = "";
    public boolean slayerTimerEnabled = true;
    /** Announce each boss's time in chat once it dies. */
    public boolean slayerTimerChatEnabled = true;
    /** Show the running time on screen while the boss is up. */
    public boolean slayerTimerHudEnabled = true;
    /** Highlight the player's own slayer boss. */
    public boolean slayerBossHighlightEnabled = true;
    /** How the boss is highlighted, an index into SlayerBossHighlight.STYLE_NAMES. */
    public int slayerBossHighlightStyle = 0;
    public int slayerBossHighlightColor = 0xFFFF5555;
    /** On the Blaze slayer, color the boss and its demons by their current attunement. */
    public boolean slayerBossHighlightAttunement = true;
    /** The Ragnarock's cast countdown under the crosshair instead of in the action bar. */
    // Saved as ragAxeCountdownEnabled before the feature was renamed; read that too so the setting carries over.
    @SerializedName(value = "ragnarockEnabled", alternate = "ragAxeCountdownEnabled")
    public boolean ragnarockEnabled = true;

    // Deployable timer: the strongest power orb or flare in range, with its time left.
    public boolean deployableTimerEnabled = false;
    public int deployableTimerHudX = net.alpaka.addons.client.hud.HudDefaults.DEPLOYABLE_TIMER_X;
    public int deployableTimerHudY = net.alpaka.addons.client.hud.HudDefaults.DEPLOYABLE_TIMER_Y;
    public float deployableTimerHudScale = 1.0f;
    public boolean deployableTimerHudBlur = true;
    public boolean deployableTimerHudSmoothFont = true;
    public int slayerTimerHudX = net.alpaka.addons.client.hud.HudDefaults.SLAYER_TIMER_X;
    public int slayerTimerHudY = net.alpaka.addons.client.hud.HudDefaults.SLAYER_TIMER_Y;
    public float slayerTimerHudScale = 1.0f;
    public boolean worldAgeJoinMessageEnabled = true;
    public int worldAgeRecentThresholdSec = 60;
    public boolean onlyCritDamageEnabled = true;
    /**
     * The mod draws damage splashes itself, animated and in gradients, in place of Hypixel's.
     * Off by default while it is being tried out.
     */
    public boolean customDamageTagsEnabled = false;
    public float customDamageTagScale = 1.5f;
    public int customDamageTagDurationMs = 1100;
    /** 0..1: how far the tag overshoots its size when it pops up. */
    public float customDamageTagPopStrength = 0.6f;
    /** Blocks the tag drifts upwards over its lifetime. */
    public float customDamageTagRise = 0.6f;
    /** One of CustomDamageTagFeature.FORMAT_*. */
    public int customDamageTagFormat = 0;
    public boolean customDamageTagFlowingGradient = true;
    public boolean customDamageTagShadow = true;
    /** No custom tag for a splash next to a slayer boss, any player's, like SkyHanni's option. */
    public boolean customDamageTagHideNearSlayerBoss = false;
    /** One of CustomDamageTagFeature.DUNGEON_*. */
    public int customDamageTagDungeonMode = 0;
    public int customDamageTagNormalStart = 0xFFFFFFFF;
    public int customDamageTagNormalEnd = 0xFF9FB4D0;
    public int customDamageTagCritStart = 0xFFFFE15A;
    public int customDamageTagCritEnd = 0xFFFF4A3A;

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

    // Etherwarp overlay. Marks the block an Etherwarp teleport would land on while it is aimed.
    // Display only - the ray is worked out the way the server does it, nothing is sent.
    public boolean etherwarpOverlayEnabled = false;
    /** Box color when the warp will succeed. Default: translucent gold. */
    public int etherwarpOverlayColor = 0xD9FFAA00;
    /** Also mark a target the warp will refuse (no room to stand), in the fail color. */
    public boolean etherwarpOverlayShowFail = true;
    public int etherwarpOverlayFailColor = 0xD9FF5555;
    public boolean etherwarpOverlayFill = false;
    public float etherwarpOverlayThickness = 2.0f;
    public boolean etherwarpOverlayThroughWalls = false;
    /** A line from the player's feet to the target block. Always drawn on top, so it never vanishes into terrain. */
    public boolean etherwarpLineEnabled = false;
    public float etherwarpLineWidth = 5.0f;
    /** Replace Hypixel's warp sound with the mod's own. Independent of the Custom Sounds toggle. */
    public boolean etherwarpSoundEnabled = false;
    /** Index into {@code EtherwarpOverlayFeature.SOUND_NAMES}: whoosh, pling, thud, chime, pop. */
    public int etherwarpSoundIndex = 0;
    public float etherwarpSoundVolume = 1.0f;

    // Inventory HUD. Shows the 27 main-inventory slots on screen; position and scale are set in
    // the HUD editor, or it can ride directly above the hotbar.
    public boolean inventoryHudEnabled = false;
    public int inventoryHudX = net.alpaka.addons.client.hud.HudDefaults.INVENTORY_X;
    public int inventoryHudY = net.alpaka.addons.client.hud.HudDefaults.INVENTORY_Y;
    public float inventoryHudScale = 1.0f;
    public boolean inventoryHudAttachToHotbar = true;
    /**
     * When the panel shows: always, briefly on an item change, or only on the toggle key (see
     * {@code InventoryHudVisibility}). Key press for fresh installs: attached above
     * the hotbar, an always-open panel sits where the player looks for health and the action bar.
     */
    public int inventoryHudVisibility = net.alpaka.addons.features.inventoryhud.InventoryHudVisibility.KEY_PRESS;
    /** Legacy, before the visibility choice. Read once by {@link #migrateInventoryHudVisibility} and then cleared. */
    public Boolean inventoryHudAlwaysVisible = null;
    public Boolean inventoryHudShowOnItemChange = null;
    /** Backdrop strength behind the slots, in percent. 0 leaves only the gradient frame. */
    public float inventoryHudBackgroundOpacity = 70.0f;
    /**
     * Whether the panel is drawn from Minecraft's own inventory texture instead of a flat backdrop.
     *
     * The point is that a resource pack reaches the HUD: the slots are cut out of
     * textures/gui/container/inventory.png, the same file the inventory screen uses, so whatever
     * pack is loaded paints them.
     *
     * Off restores the flat panel with the gradient frame.
     */
    public boolean inventoryHudVanillaTexture = false;
    /** Flat style only: the panel shows the world behind it blurred, like the chat's background. */
    public boolean inventoryHudBlur = true;
    /** Flat style only: the gradient frame around the panel. Off leaves the backdrop on its own. */
    public boolean inventoryHudFrame = true;
    /** The two ends of the flat panel's frame gradient, top left to bottom right. */
    public int inventoryHudFrameStart = 0xFF29B6B2;
    public int inventoryHudFrameEnd = 0xFFB04FFF;

    // Guild bridge-bot chat formatting. The name is the in-game account that relays Discord.
    public boolean bridgeBotFormatterEnabled = false;
    public String bridgeBotName = "";

    /**
     * Replace Hypixel's "Guild >" marker with the guild's own tag.
     *
     * The text may carry `&`-style color codes, which are translated to section signs when the
     * line is drawn - `§` cannot be typed on a keyboard, so `&` is the convention every Hypixel mod
     * uses for this.
     */
    public boolean guildPrefixEnabled = false;
    public String guildPrefixText = "";

    // Chat options
    /**
     * Better Chat: the main switch over everything that changes how the chat looks - Smooth Chat,
     * the opening animation, the smooth scrolling, the blurred background and the smooth font. Each keeps its own switch
     * under it.
     */
    public boolean betterChatEnabled = true;
    /** A new message slides up into the chat and fades in instead of appearing at once. */
    public boolean smoothChatEnabled = true;
    /** How long the slide takes, 1 (quick) to 10 (slow); each step is 60 ms. */
    public int smoothChatStrength = 5;
    /** Opening the chat unrolls it upwards and slides the input line in instead of showing it at once. */
    public boolean chatOpenAnimationEnabled = true;
    /**
     * HUD Style's main switches: off, no HUD gets the blurred panel or the smooth font, whatever its
     * own switch says. Each HUD's own switch sits in that feature's settings.
     */
    public boolean hudBlurAll = true;
    public boolean hudSmoothFontAll = true;

    /** A HUD's own blur switch, as far as the main switch lets it through. */
    public boolean blurOn(boolean own) {
        return hudBlurAll && own;
    }

    /** A HUD's own smooth font switch, as far as the main switch lets it through. */
    public boolean smoothOn(boolean own) {
        return hudSmoothFontAll && own;
    }

    /** One rounded, padded panel behind the chat that shows the world blurred through it. */
    public boolean chatBlurEnabled = true;
    /**
     * How strongly the world is blurred behind the chat, in percent of the full radius. 0 leaves
     * the world sharp behind the tint. The inventory HUD and the command wheel share the chat's
     * blurred copy of the frame, so they follow it; the main menu keeps its own full blur.
     */
    public float chatBlurStrength = 100.0f;
    /** One rounded, blurred panel with a soft shadow behind the scoreboard sidebar; see ScoreboardBlurFeature. */
    public boolean scoreboardBlurEnabled = true;
    /** Writes "AlpakaAddons" in a gradient over Hypixel's www.hypixel.net line; see ScoreboardFooterFeature. */
    public boolean scoreboardAlpakaFooter = true;
    /** Ctrl+F in the chat turns the input line into a search that filters the visible messages. */
    public boolean chatSearchEnabled = true;
    /** Right-clicking a message in the open chat copies its text; see ChatCopyFeature. */
    public boolean chatCopyEnabled = true;
    /** Scrolling the chat glides the lines into place instead of jumping a line per notch. */
    public boolean chatSmoothScrollEnabled = true;
    /** The open chat's scrollbar is a wider, rounded bar that can be dragged with the mouse. */
    public boolean chatScrollbarEnabled = true;
    /** The chat is a HUD element: the HUD editor can move it and resize it. */
    public boolean chatMovable = false;
    /**
     * Where the moved chat sits, as an offset from vanilla's place (its bottom-left corner, 40 GUI
     * pixels above the screen's bottom), so it stays put relative to the bottom when the window or
     * the GUI scale changes.
     */
    public int chatOffsetX = 0;
    public int chatOffsetY = 0;
    /** Moving forward sprints on its own, without holding the sprint key. */
    /** Off for fresh installs. Vanilla's own toggle-sprint option already does this for most players. */
    public boolean alwaysSprintEnabled = false;
    /** Always sprint stays off on the Garden, where farming wants walking speed. */
    public boolean alwaysSprintIgnoreGarden = false;
    /** The cursor stays put when Hypixel swaps one menu for the next (backpack pages and the like). */
    public boolean keepCursorPositionEnabled = true;
    /** Quick Join's main switch. Off for fresh installs: it sends a command nobody typed. */
    public boolean quickJoinEnabled = false;
    /** Quick Join sends /skyblock once after joining Hypixel. */
    public boolean quickJoinSkyblock = true;
    /** Quick Join also connects to Hypixel as soon as the game has started. */
    public boolean quickJoinOnLaunch = false;
    public boolean expandChatHistory = true;
    /** How many chat lines Expand Chat History keeps; vanilla keeps 100. */
    public int chatHistoryLimit = 5000;

    /** How much history the chat keeps right now: the limit above while Expand Chat History is on, else vanilla's 100. */
    public int chatHistoryLength() {
        return expandChatHistory ? Math.max(100, chatHistoryLimit) : 100;
    }
    /** Hold the Chat Peek key to see the whole chat, scrollable, without opening it. */
    public boolean chatPeekEnabled = true;
    /** Chat Peek also works with an inventory or another menu open, drawn on top of it. */
    public boolean chatPeekInMenus = true;
    /** Better Screenshots: everything below. The key keeps its old name so the setting carries over. */
    public boolean betterScreenshotMessageEnabled = true;
    /** Open, Copy and Delete buttons on the screenshot notice instead of vanilla's line. */
    public boolean screenshotChatButtons = true;
    /** The screenshot shrinking into the corner for a moment after it is taken. */
    public boolean screenshotPreviewEnabled = true;
    /** How long the preview stays in the corner, in seconds. */
    public float screenshotPreviewSeconds = 3.0f;
    /** The preview's width, in percent of the screen's. */
    public float screenshotPreviewSize = 20.0f;
    /** Which parts the screenshot line shows. */
    public boolean screenshotShowFileName = false;
    public boolean screenshotShowOpen = true;
    public boolean screenshotShowCopy = true;
    public boolean screenshotShowDelete = true;
    public boolean screenshotShowFolder = false;
    /** What clicking the preview does; an index into ScreenshotMessageFeature.PREVIEW_CLICK_NAMES. */
    public int screenshotPreviewClickAction = 1;
    /** Every screenshot goes to the clipboard the moment it is taken. */
    public boolean autoCopyScreenshots = false;
    /** A message repeated right after itself becomes one line with (x2), (x3)... */
    public boolean compactChatEnabled = true;
    /** Seconds since the last identical message within which a repeat still stacks; 0 = back-to-back only. */
    public int compactChatWindowSeconds = 30;
    /** Hides Hypixel chat spam; which kinds is up to the switches below. */
    public boolean messageFilterEnabled = false;
    public boolean messageFilterEmpty = true;
    public boolean messageFilterWelcome = true;
    public boolean messageFilterGuildExp = true;
    public boolean messageFilterKillCombo = true;
    public boolean messageFilterProfileJoin = true;
    public boolean messageFilterRewardBundles = true;
    public boolean messageFilterAutopet = true;
    public boolean messageFilterFireSale = true;
    public boolean messageFilterWatchdog = true;
    public boolean messageFilterStash = true;
    public boolean messageFilterSacks = true;
    /** Dungeon chatter, only hidden inside a Catacombs run; one switch per group. */
    public boolean messageFilterDungeonPrepare = true;
    public boolean messageFilterDungeonStart = true;
    public boolean messageFilterDungeonAmbience = true;
    public boolean messageFilterDungeonPickup = true;
    public boolean messageFilterDungeonReminder = true;
    public boolean messageFilterDungeonBlessing = true;
    public boolean messageFilterDungeonNotPossible = true;
    public boolean messageFilterDungeonDamage = true;
    public boolean messageFilterDungeonAbility = true;
    public boolean messageFilterDungeonPuzzle = true;
    public boolean messageFilterDungeonEnd = true;
    /** The player's own filters, made in the Message Filter's sub menu. */
    public java.util.List<net.alpaka.addons.features.chat.CustomMessageFilter> messageFilterCustom = new java.util.ArrayList<>();
    /** All / Party / Guild / PMs tabs above the chat input. */
    public boolean chatTabsEnabled = true;
    /** With chat tabs: a plain message typed on a channel tab goes to that channel (/pc, /gc, /r). */
    public boolean chatTabsSendToChannel = false;
    /** With chat tabs: the row also shows while peeking, and Tab cycles the tabs in chat and while peeking. */
    public boolean chatTabsTabKey = true;
    /** Keep the chosen chat tab, and so the filtered HUD chat, after the chat closes. Off: back to All. */
    public boolean chatTabsKeepAfterClose = false;
    /** With chat tabs: guild messages show only on the Guild tab, not on All. Ignored while that tab is hidden. */
    public boolean chatTabsGuildOnlyInGuildTab = false;
    /** Which channel tabs the row offers. All is always there; a hidden channel's lines stay on All. */
    public boolean chatTabsShowParty = true;
    public boolean chatTabsShowGuild = true;
    public boolean chatTabsShowCoop = true;
    public boolean chatTabsShowPrivate = true;
    public boolean chatTabsShowNpc = true;

    // Interface options
    /** The mouse wheel scrolls a tooltip that is taller than the screen. */
    public boolean scrollableTooltipsEnabled = true;
    /** The wheel moves every tooltip, not only one taller than the screen. */
    public boolean scrollableTooltipsAlways = false;
    /** GUI pixels one wheel notch moves a tooltip. */
    public float scrollableTooltipsSpeed = 12.0f;
    /** The tooltip glides to where the wheel sends it instead of jumping. */
    public boolean scrollableTooltipsSmooth = true;
    /** Wheel down moves the tooltip down instead of reading further down it. */
    public boolean scrollableTooltipsInvert = false;
    /** The held item's name above the hotbar stays up instead of fading out. */
    public boolean persistentItemNameEnabled = false;
    /** Master stars show as red stars, as they used to, instead of a number behind the gold ones. */
    public boolean oldMasterStarsEnabled = false;
    /**
     * One long row of twenty hearts in place of the two short ones, the hunger bar and the armor bar,
     * neither of which SkyBlock needs; absorption hearts sit on the row above it.
     */
    public boolean wideHealthBarEnabled = false;
    /** The row of twenty in one piece, centred over the hotbar; the level number moves onto the XP bar. */
    public boolean wideHealthBarCentered = false;
    /** Absorption hearts: 0 a row of their own, 1 gold over the first hearts, 2 hidden. */
    public int wideHealthBarAbsorption = 0;

    /** A Hypixel party invite raises a notification: Y joins, N dismisses. */
    public boolean partyInvitePromptEnabled = true;

    /** Keeps Hypixel's server resource pack loaded between joins; see ServerPackCache. */
    public boolean cacheResourcePackEnabled = false;

    /**
     * The Auction House and the Bazaar in the mod's own screen. It only redraws Hypixel's menus:
     * every click on it is one click on the slot it stands for, see MarketScreen.
     */
    public boolean customAuctionHouseEnabled = true;
    public boolean customBazaarEnabled = true;
    /**
     * Number keys, arrows and the like press the market screen's buttons, one click per press; see
     * MarketScreen.keyPressed. One switch for each market.
     */
    public boolean auctionHouseHotkeysEnabled = true;
    public boolean bazaarHotkeysEnabled = true;
    /** Hypixel's sign prompts - a search, a price, an amount - as an input bar; see MarketInputScreen. */
    public boolean customSignInputEnabled = true;
    /** SkyCofl's price history on a product's or an auction's page; see PriceGraph. Needs allowApiCalls. */
    public boolean marketPriceHistoryEnabled = true;
    /** How far back it goes, an index into PriceHistory.Range: day, week, month, all time. */
    public int marketPriceHistoryRange = 1;
    /** The Item Value key shows what the hovered item is worth, part by part; see ItemValuePopup. Needs allowApiCalls. */
    public boolean itemValueEnabled = true;
    /** Which Bazaar price the parts count at: 0 what they cost to buy now, 1 what they sell for now. */
    public int itemValueBazaarPrice = 0;
    /** The Chest Value panel beside a menu: what everything in it is worth; see ChestValuePanel. Needs allowApiCalls. */
    public boolean chestValueEnabled = true;
    /** Whether the panel is showing: its key switches it, and it stays so from menu to menu until pressed again. */
    public boolean chestValueShown = false;
    /** Whether a chest's panel counts the player's own inventory below it too. */
    public boolean chestValueIncludeInventory = false;
    /** Which side of the menu the panel sits on: 0 left, 1 right (where the item list mods usually are). */
    public int chestValueSide = 0;
    /** How its list is ordered, an index into ChestValue.Sort: value, price each, amount. */
    public int chestValueSort = 0;
    /** Where the panel was moved to, as a share of the screen's width and height; -1 beside the menu. */
    public float chestValueX = -1f;
    public float chestValueY = -1f;

    // Command Wheel options
    /**
     * The quick command wheel, one list per page. Null until CommandWheelPages first folds the
     * legacy flat list below into pages; from then on this is the record and the flat list is null.
     */
    public java.util.List<java.util.List<String>> commandWheelPages = null;

    /**
     * Wheels of single Skyblock profiles, keyed "account uuid:profile", pages as above. A profile
     * listed here opens its own wheel; every other one opens {@link #commandWheelPages}.
     */
    public java.util.Map<String, java.util.List<java.util.List<String>>> commandWheelProfilePages = new java.util.HashMap<>();

    /**
     * Off: hold the wheel key to open the wheel, release it to run the aimed command. On: one press
     * opens it, a click runs a command, and pressing the key again closes it.
     */
    public boolean commandWheelToggleMode = false;

    /**
     * The mod's keys, by key mapping name ("key.alpaka.zoom") to the bound key's name
     * ("key.keyboard.c"). A key left out is on its default. They are set in the config menu and kept
     * here rather than in options.txt; see AlpakaKeyBinds.
     */
    public Map<String, String> keyBinds = new HashMap<>();
    /** Whether keys bound in vanilla's Controls before have been taken over from options.txt. */
    public boolean keyBindsImported = false;

    /** Holding the Zoom key zooms in; scrolling while holding it zooms further. */
    public boolean zoomEnabled = true;
    /** Off: zoom while the key is held. On: one press zooms in, the next zooms back out. */
    public boolean zoomToggleMode = false;
    /** How far the zoom goes in, as a factor of the normal view. */
    public float zoomAmount = 4.0f;
    /** Scrolling while zoomed changes the zoom instead of the hotbar slot. */
    public boolean zoomScrollEnabled = true;
    /** How the zoom moves in and out: an index into ZoomFeature.ANIMATIONS. */
    public int zoomAnimation = 1;
    /** How long the zoom animation takes, in milliseconds. */
    public int zoomAnimationMs = 250;
    /** Turns on vanilla's cinematic camera while zoomed. */
    public boolean zoomCinematicCamera = false;
    /** Turns the mouse down by the zoom factor, so aiming feels the same zoomed in. */
    public boolean zoomReduceSensitivity = true;

    /** Legacy, pre-pages. Read once by CommandWheelPages and then cleared. */
    public java.util.List<String> commandWheelCommands = new java.util.ArrayList<>(java.util.List.of(
            "/hub",
            "/island",
            "/warp dh",
            "/wardrobe",
            "/pets",
            "/pv"
    ));

    // Command Shortcuts; see CommandShortcuts.
    public boolean commandShortcutsEnabled = true;
    /** Typed aliases: "/v Steve" goes out as "/visit Steve". */
    public java.util.List<CommandAlias> commandAliases = new java.util.ArrayList<>();
    /** Keys that run a command with no screen open: Y for "/sb menu". */
    public java.util.List<KeyShortcut> commandKeyShortcuts = new java.util.ArrayList<>();

    /** One alias, both sides without their slash: "v" stands for "visit". */
    public static class CommandAlias {
        public String alias = "";
        public String command = "";

        public CommandAlias() {}

        public CommandAlias(String alias, String command) {
            this.alias = alias;
            this.command = command;
        }
    }

    /**
     * One key shortcut: the key by its InputConstants name ("key.keyboard.y"), the modifier keys
     * held with it (GLFW's MOD_SHIFT, MOD_CONTROL, MOD_ALT, MOD_SUPER bits), and the command
     * without its slash. An empty key is not bound yet.
     */
    public static class KeyShortcut {
        public String key = "";
        public int modifiers = 0;
        public String command = "";

        public KeyShortcut() {}

        public KeyShortcut(String key, int modifiers, String command) {
            this.key = key;
            this.modifiers = modifiers;
            this.command = command;
        }
    }

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
    /** Drops the directional shading from the first-person held item; see HandItemLightingFeature. */
    public boolean itemLightingDisabled = false;
    public boolean itemSwingTranslationDisabled = false;
    public boolean itemNoEquipEnabled = false;
    public boolean itemSwingAlwaysFinishEnabled = false;
    public boolean itemIgnoreEmptyHandEnabled = false;

    // Starlight: a drifting, twinkling galaxy over the held item; see HandItemStarlightFeature.
    public boolean itemStarlightEnabled = false;
    /** The nebula's color; the stars are tinged with it. */
    public int itemStarlightColor = 0xFF9B5CFF;
    /** How strongly the galaxy covers the item, 0..1. */
    public float itemStarlightStrength = 0.8f;
    /** 0: over every held item. 1: in place of the enchantment glint, so only on glinting items. */
    public int itemStarlightMode = 0;

    // Swing motion blur: faded copies trail behind the held item while it swings; see ItemMotionBlurFeature.
    public boolean itemMotionBlurEnabled = false;
    /** How far back in time the trail reaches, in milliseconds. */
    public float itemMotionBlurLength = 80.0f;
    /** Opacity of the newest ghost copy; the older ones fade out from there. */
    public float itemMotionBlurOpacity = 0.35f;
    /** Number of ghost copies spread over the trail length. */
    public float itemMotionBlurSteps = 8.0f;

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
        // Newer than the constructor below; presets saved before these existed load with the defaults.
        public boolean motionBlurEnabled = false;
        public float motionBlurLength = 80.0f;
        public float motionBlurOpacity = 0.35f;
        public float motionBlurSteps = 8.0f;
        /** What the player called it; empty for none, and it shows as "Preset n" then. */
        public String name = "";

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

    /**
     * The saved presets, in the order they were made. As many as the player wants; a config from
     * before presets could be added and deleted had exactly three, and reads in unchanged.
     */
    public List<ItemPreset> itemPresets = new ArrayList<>(List.of(new ItemPreset(), new ItemPreset(), new ItemPreset()));

    /** The preset last loaded or saved, which the menu marks; -1 for none. */
    public int activeItemPresetIndex = 0;

    /** The name a preset goes by: its own, or "Preset n" when it has none. */
    public String presetName(int index) {
        String name = index >= 0 && index < itemPresets.size() ? itemPresets.get(index).name : null;
        return name == null || name.isBlank() ? "Preset " + (index + 1) : name;
    }

    public void loadPreset(int index) {
        if (index >= 0 && index < itemPresets.size()) {
            ItemPreset preset = itemPresets.get(index);
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
            this.itemMotionBlurEnabled = preset.motionBlurEnabled;
            this.itemMotionBlurLength = preset.motionBlurLength;
            this.itemMotionBlurOpacity = preset.motionBlurOpacity;
            this.itemMotionBlurSteps = preset.motionBlurSteps;
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
        this.itemLightingDisabled = false;
        this.itemSwingTranslationDisabled = false;
        this.itemNoEquipEnabled = false;
        this.itemSwingAlwaysFinishEnabled = false;
        this.itemIgnoreEmptyHandEnabled = false;
        this.itemStarlightEnabled = false;
        this.itemStarlightColor = 0xFF9B5CFF;
        this.itemStarlightStrength = 0.8f;
        this.itemStarlightMode = 0;
        this.itemMotionBlurEnabled = false;
        this.itemMotionBlurLength = 80.0f;
        this.itemMotionBlurOpacity = 0.35f;
        this.itemMotionBlurSteps = 8.0f;

        this.swingDriftX = 0.0f;
        this.swingDriftY = 0.0f;
        this.swingDriftZ = 0.0f;
        this.swingArcX = 0.0f;
        this.swingArcY = 0.0f;
        this.swingArcZ = 0.0f;

        save();
    }

    public void savePreset(int index) {
        if (index >= 0 && index < itemPresets.size()) {
            ItemPreset preset = currentAsPreset();
            preset.name = itemPresets.get(index).name;
            itemPresets.set(index, preset);
            this.activeItemPresetIndex = index;
            save();
        }
    }

    /** Adds a preset holding the current settings at the end of the list, and makes it the active one. */
    public void addPreset() {
        itemPresets.add(currentAsPreset());
        this.activeItemPresetIndex = itemPresets.size() - 1;
        save();
    }

    /** Deletes a preset; the ones after it move up. The current settings stay as they are. */
    public void deletePreset(int index) {
        if (index < 0 || index >= itemPresets.size()) return;
        itemPresets.remove(index);
        if (activeItemPresetIndex == index) activeItemPresetIndex = -1;
        else if (activeItemPresetIndex > index) activeItemPresetIndex--;
        save();
    }

    private ItemPreset currentAsPreset() {
        ItemPreset preset = new ItemPreset(
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
        preset.motionBlurEnabled = this.itemMotionBlurEnabled;
        preset.motionBlurLength = this.itemMotionBlurLength;
        preset.motionBlurOpacity = this.itemMotionBlurOpacity;
        preset.motionBlurSteps = this.itemMotionBlurSteps;
        return preset;
    }

    public Map<SlayerType, SlayerData> slayerBossMap = new HashMap<>();

    public static class SlayerData {
        public int kills = 0;
        public Map<String, Integer> drops = new HashMap<>();

        /**
         * The old single best time per slayer, no longer read.
         *
         * It was kept across every tier, so one quick tier 2 boss stood as the best for tier 5 as
         * well, and it was written by fights the timer had not followed from start to end. Neither
         * can be told apart afterwards, so these figures are dropped rather than carried over; the
         * field stays only so that files which still hold one load without complaint.
         */
        @Deprecated
        public long bestBossMs = -1L;

        /**
         * Fastest boss kill ever recorded for this slayer, in milliseconds, keyed by quest tier 1-5.
         *
         * Per tier because that is what a boss time means: a tier 5 and a tier 3 boss are different
         * fights. All-time rather than per session, since a personal best that resets when the game
         * restarts is not one.
         */
        public Map<Integer, Long> bestBossMsByTier = new HashMap<>();

        /**
         * When each tier's best in {@link #bestBossMsByTier} was set, wall clock, by tier; missing
         * for bests older than this field. Read by the merge with the file, together with
         * {@link #bestsClearedAtMs}.
         */
        public Map<Integer, Long> bestBossAtMsByTier = new HashMap<>();

        /**
         * When this slayer's bests were last cleared ({@code /alpakaslayer reset pb}), wall clock,
         * or 0 when never. The merge with the file drops every best set before it, wherever it is
         * kept: the file, or another instance's memory, would otherwise hand the cleared best back
         * within a second.
         */
        public long bestsClearedAtMs = 0L;

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
        /**
         * XP credited since the run of credits that ended at lastXpCreditedAtMs began, so a lower
         * menu reading can be told apart: lag of about this much is the leaderboard cache, a much
         * larger gap is a total that drifted high and is corrected. See SlayerXpTracker.
         */
        public long recentlyCreditedXp = 0L;

        /**
         * When {@link #totalXp} was last read from the Slayer menu, wall clock, or 0 when never.
         * The merge with the file keeps the newer reading rather than the larger number, so a total
         * corrected down is not taken back by the old one still on the other side.
         */
        public long totalXpObservedAtMs = 0L;

        /** Records a new best time for a tier, and when it was set. */
        public void recordBest(int tier, long ms) {
            if (bestBossMsByTier == null) bestBossMsByTier = new HashMap<>();
            if (bestBossAtMsByTier == null) bestBossAtMsByTier = new HashMap<>();
            bestBossMsByTier.put(tier, ms);
            bestBossAtMsByTier.put(tier, System.currentTimeMillis());
        }

        /** Forgets every best time of this slayer, for good; returns whether there was one. */
        public boolean clearBests() {
            boolean had = bestBossMsByTier != null
                    && bestBossMsByTier.values().stream().anyMatch(best -> best != null && best > 0L);
            if (bestBossMsByTier != null) bestBossMsByTier.clear();
            if (bestBossAtMsByTier != null) bestBossAtMsByTier.clear();
            bestsClearedAtMs = System.currentTimeMillis();
            return had;
        }
    }

    public AlpakaConfig() {
        for (SlayerType type : SlayerType.values()) {
            slayerBossMap.put(type, new SlayerData());
        }
    }

    /**
     * What to tell the player once they are in game, when the file could not be read at start-up.
     * Taken by {@link #takeLoadNotice()}.
     */
    private static String loadNotice = null;

    /** Whether this start found an existing settings file, as opposed to a fresh install. */
    private static boolean existedAtStart = false;

    public static boolean existedAtStart() {
        return existedAtStart;
    }

    /**
     * Every setting back to its default, after the player confirmed it in the config.
     *
     * What is not a setting stays: slayer kills and drops, the saved viewmodel presets, where the
     * stats record is synced to (a fact about this machine - losing it would look like losing the
     * stats), and the bookkeeping of which version last ran and which one-off migrations are done.
     */
    public static void resetToDefaults() {
        AlpakaConfig old = instance;
        AlpakaConfig fresh = new AlpakaConfig();
        fresh.slayerBossMap = old.slayerBossMap;
        fresh.itemPresets = old.itemPresets;
        fresh.statsDirectory = old.statsDirectory;
        fresh.statsDirectoryHadFile = old.statsDirectoryHadFile;
        fresh.lastSeenVersion = old.lastSeenVersion;
        fresh.configVersion = old.configVersion;
        fresh.menuFontNunitoApplied = true;
        // The keys go back to their defaults too; options.txt is not read again for the old ones.
        fresh.keyBindsImported = true;
        instance = fresh;
        applyMenuTheme();
        save();
    }

    public static void load() {
        load(store());
    }

    /** Loads the settings from {@code from}, which later saves go to as well. Used directly by tests. */
    static void load(ConfigStore from) {
        store = from;
        existedAtStart = from.file().exists();
        if (!existedAtStart) {
            instance = new AlpakaConfig();
        } else {
            AlpakaConfig loaded = read(from.file());
            if (loaded != null) {
                instance = loaded;
                // A copy of the last file that loaded, taken once per start rather than on every
                // save: every write is atomic now, so this only has to cover damage from outside.
                AtomicJsonFile.backup(from.file(), from.backup());
            } else {
                recoverUnreadable();
            }
        }

        int sanitized = ConfigSanitizer.sanitize(instance, new AlpakaConfig());
        if (sanitized > 0) {
            AlpakaAddons.LOGGER.warn("Put {} unusable value(s) in alpaka.json back to their defaults", sanitized);
        }
        boolean migrated = instance.migrate();

        // The default font moved from Inter to Poppins with the redesign. Once only, so picking Inter
        // again afterwards sticks.
        if (!instance.menuFontNunitoApplied) {
            if (instance.menuFont == 1) instance.menuFont = 2;
            instance.menuFontNunitoApplied = true;
        }

        // Settings that were replaced keep the other pattern: a legacy field, read once and then
        // cleared, so it needs no number.
        instance.migrateNameTagBackground();
        instance.migrateInventoryHudVisibility();
        instance.migrateWorldAgeHud();

        for (SlayerType type : SlayerType.values()) {
            if (!instance.slayerBossMap.containsKey(type)) {
                instance.slayerBossMap.put(type, new SlayerData());
            }
        }

        // Deleting presets is allowed now, so fewer than three is a choice; a hand-edited null entry
        // has already been dropped by the sanitizer.
        if (instance.activeItemPresetIndex >= instance.itemPresets.size()) {
            instance.activeItemPresetIndex = -1;
        }

        if (!existedAtStart || sanitized > 0 || migrated) save();
        applyMenuTheme();
    }

    /** Puts the saved Menu Theme on every menu. */
    public static void applyMenuTheme() {
        AlpakaStyle.applyTheme(AlpakaStyle.Theme.of(instance.menuTheme));
    }

    /**
     * One-time changes to the settings, in order: the step at index i brings a file from layout i to
     * i + 1. Each runs once per file, the first time a version that has it loads that file, so a
     * value the player picks afterwards is never changed back. They used to run on every start: an
     * accent color set to exactly the old gold turned back into teal each time.
     */
    private static final java.util.List<java.util.function.Consumer<AlpakaConfig>> MIGRATIONS = java.util.List.of(
            AlpakaConfig::migrateTo1
    );

    /** The settings layout this version writes. A file with a lower number gets the steps it has not had. */
    static final int CONFIG_VERSION = MIGRATIONS.size();

    /** Layout 0 to 1: the changes that ran on every start before there was a layout number. */
    private void migrateTo1() {
        // The menu's default accent moved from warm gold to teal. A config still on the exact old
        // default never chose gold, so it follows the new default; any other color is a choice.
        if (menuAccentColor == 0xFFE5B849) {
            menuAccentColor = 0xFF29B6B2;
        }
    }

    /**
     * Runs the steps of {@link #MIGRATIONS} this file has not had. Returns whether the layout number
     * moved. A file from a newer version keeps its higher number and gets no steps.
     */
    boolean migrate() {
        if (configVersion >= CONFIG_VERSION) return false;
        for (int step = Math.max(0, configVersion); step < MIGRATIONS.size(); step++) {
            MIGRATIONS.get(step).accept(this);
        }
        configVersion = CONFIG_VERSION;
        return true;
    }

    /** Hide Name Tag Background became a color; hiding is now zero opacity. */
    private void migrateNameTagBackground() {
        if (hideNameTagBackground == null) return;
        if (hideNameTagBackground) nameTagBackgroundColor = 0;
        hideNameTagBackground = null;
    }

    /**
     * The file is there but unusable: empty, cut short, or not valid JSON.
     *
     * It is moved aside rather than overwritten, the backup is tried next, and only then does the
     * config fall back to defaults. Either way the player hears about it once in game, instead of
     * finding their settings silently reset. An empty file used to crash every launch, because Gson
     * reads it as null, and a malformed one was replaced with defaults without a word.
     */
    private static void recoverUnreadable() {
        File aside = AtomicJsonFile.quarantine(store().file());
        AlpakaConfig fromBackup = read(store().backup());
        String where = aside != null ? " The damaged file was kept as " + aside.getName() + "." : "";
        if (fromBackup != null) {
            instance = fromBackup;
            loadNotice = "§eYour Alpaka Addons settings file was damaged, so the last good copy was restored." + where;
            AlpakaAddons.LOGGER.warn("alpaka.json was unreadable; restored the backup.{}", where);
        } else {
            instance = new AlpakaConfig();
            loadNotice = "§eYour Alpaka Addons settings file was damaged and there was no backup, so the settings were reset." + where;
            AlpakaAddons.LOGGER.warn("alpaka.json was unreadable and no backup could be read; using defaults.{}", where);
        }
        save();
    }

    /** The inventory HUD's two visibility switches became one choice. */
    private void migrateInventoryHudVisibility() {
        if (inventoryHudAlwaysVisible == null && inventoryHudShowOnItemChange == null) return;
        inventoryHudVisibility = net.alpaka.addons.features.inventoryhud.InventoryHudVisibility.fromLegacy(
                inventoryHudAlwaysVisible, inventoryHudShowOnItemChange);
        inventoryHudAlwaysVisible = null;
        inventoryHudShowOnItemChange = null;
    }

    /**
     * The World Age HUD became the Performance HUD's Show Day. Whoever had it on keeps seeing the
     * day where and how it was; if the Performance HUD was off, it comes on with only the day shown,
     * so nothing appears that was not there before.
     */
    private void migrateWorldAgeHud() {
        if (worldAgeHudEnabled == null && worldAgeHudX == null && worldAgeHudY == null && worldAgeHudScale == null
                && worldAgeHudBlur == null && worldAgeHudSmoothFont == null) return;
        if (Boolean.TRUE.equals(worldAgeHudEnabled)) {
            performanceHudShowDay = true;
            if (!performanceHudEnabled) {
                performanceHudEnabled = true;
                performanceHudShowFps = false;
                performanceHudShowPing = false;
                performanceHudShowTps = false;
                if (worldAgeHudX != null) performanceHudX = worldAgeHudX;
                if (worldAgeHudY != null) performanceHudY = worldAgeHudY;
                if (worldAgeHudScale != null) performanceHudScale = worldAgeHudScale;
                if (worldAgeHudBlur != null) performanceHudBlur = worldAgeHudBlur;
                if (worldAgeHudSmoothFont != null) performanceHudSmoothFont = worldAgeHudSmoothFont;
            }
        }
        worldAgeHudEnabled = null;
        worldAgeHudX = null;
        worldAgeHudY = null;
        worldAgeHudScale = null;
        worldAgeHudBlur = null;
        worldAgeHudSmoothFont = null;
    }

    /** The config in {@code file}, or null when it is missing, empty or cannot be parsed. */
    private static AlpakaConfig read(File file) {
        try {
            String json = AtomicJsonFile.read(file);
            if (json == null || json.isBlank()) return null;
            return GSON.fromJson(json, AlpakaConfig.class);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to read {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /** The one-time message about a damaged settings file, or null. Cleared once taken. */
    public static String takeLoadNotice() {
        String notice = loadNotice;
        loadNotice = null;
        return notice;
    }

    /** Whether a Better Chat part is on: its own switch and Better Chat's both. */
    public boolean betterChatOn(boolean part) {
        return betterChatEnabled && part;
    }

    /**
     * Switches every feature off: each feature's own switch (every custom sound is a feature of its
     * own here), and the lone switches that belong to no feature (Hide Mob Fire, the scoreboard's
     * blur and footer, ...). Nothing else is touched. A feature's settings, and every font, color,
     * slider, choice and HUD style switch, stay as they are, so a feature switched back on comes back
     * the way it was. This used to switch the settings off and reset values too, and the backup made
     * that stick after the next start: Block Overlay came back with no outline at all.
     *
     * DisableAllFeaturesTest lists the switches this has to cover and the settings it must leave.
     */
    public void disableAllFeatures() {
        this.renderHandInThirdPerson = false;
        this.slayerDropTrackerEnabled = false;
        this.hideHypixelDropMessage = false;
        this.slayerRngDropGuildChatEnabled = false;
        this.customNameTagEnabled = false;
        this.chromaHatEnabled = false;
        this.auraEnabled = false;
        this.playerScaleEnabled = false;
        this.fullbrightEnabled = false;
        this.skyEffectsEnabled = false;
        this.inventorySnowEnabled = false;
        this.containerBgFadeInEnabled = false;
        this.hideInventoryEffects = false;
        this.cleanBlazeEnabled = false;
        this.blazeFirePitsEnabled = false;
        this.slayerBossHighlightEnabled = false;
        this.hideSlayerChatMessages = false;
        this.stopBlazeSpinning = false;
        this.hideHurtOverlayEnabled = false;
        this.hideMobDeathsEnabled = false;
        this.hideExplosions = false;
        this.hideEntityFire = false;
        this.firstPersonFireStatic = false;
        this.showOwnNameTag = false;
        this.nameTagTextShadow = false;
        this.wideHealthBarEnabled = false;
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
        this.performanceHudEnabled = false;
        this.slayerHudEnabled = false;
        this.allowApiCalls = false;
        this.slayerTimerEnabled = false;
        this.ragnarockEnabled = false;
        this.deployableTimerEnabled = false;
        this.customAuctionHouseEnabled = false;
        this.customBazaarEnabled = false;
        this.customSignInputEnabled = false;
        this.marketPriceHistoryEnabled = false;
        this.itemValueEnabled = false;
        this.chestValueEnabled = false;
        this.worldAgeJoinMessageEnabled = false;
        this.onlyCritDamageEnabled = false;
        this.customDamageTagsEnabled = false;
        this.blockOverlayEnabled = false;
        this.etherwarpOverlayEnabled = false;
        this.etherwarpSoundEnabled = false;
        this.betterChatEnabled = false;
        this.scoreboardBlurEnabled = false;
        this.scoreboardAlpakaFooter = false;
        this.chatSearchEnabled = false;
        this.chatCopyEnabled = false;
        this.alwaysSprintEnabled = false;
        this.keepCursorPositionEnabled = false;
        this.quickJoinEnabled = false;
        this.expandChatHistory = false;
        this.chatPeekEnabled = false;
        this.betterScreenshotMessageEnabled = false;
        this.compactChatEnabled = false;
        this.messageFilterEnabled = false;
        this.chatTabsEnabled = false;
        this.inventoryHudEnabled = false;
        this.bridgeBotFormatterEnabled = false;
        this.guildPrefixEnabled = false;
        this.itemSizeFeatureEnabled = false;
        this.itemStarlightEnabled = false;
        // Features added after the list above was written; the dialog promises every feature.
        this.updateNotificationsEnabled = false;
        this.scrollableTooltipsEnabled = false;
        this.persistentItemNameEnabled = false;
        this.mentionNotificationEnabled = false;
        this.itemMotionBlurEnabled = false;
        this.bossSpawnAlertEnabled = false;
        this.bossCocoonAlertEnabled = false;
        this.partyInvitePromptEnabled = false;
        this.oldMasterStarsEnabled = false;
        this.cacheResourcePackEnabled = false;
        this.zoomEnabled = false;
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
        try {
            AtomicJsonFile.write(store().file(), GSON.toJson(instance));
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to save config", e);
        }
    }
}
