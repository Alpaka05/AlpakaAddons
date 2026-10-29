package net.alpaka.addons.client.gui;

import java.util.Locale;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.damagetags.CustomDamageTagFeature;
import net.alpaka.addons.features.etherwarp.EtherwarpOverlayFeature;
import net.alpaka.addons.features.inventoryhud.InventoryHudVisibility;
import net.alpaka.addons.features.cosmetics.AuraFeature;
import net.alpaka.addons.features.nametag.CustomNameTagFeature;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.playerscale.PlayerScaleFeature;
import net.alpaka.addons.features.snow.SnowOverlayRenderer;
import net.alpaka.addons.utils.UpdateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AlpakaConfigRegistry {
    private static final List<ConfigOption> OPTIONS = new ArrayList<>();
    private static final String[] CHEST_VALUE_SIDES = {"Left", "Right"};
    private static final java.util.Map<ConfigCategory, List<ConfigOption>> CATEGORY_CACHE = new java.util.EnumMap<>(ConfigCategory.class);
    private static final String[] BOSS_MAP_MODES = {"Arena Map", "Saved Map", "Both", "Hidden"};
    private static final String[] PLAYER_ICONS = {"Heads", "Upright Heads", "Arrows"};
    private static final String[] MAP_COLORS = {"Alpaka", "Hypixel"};

    static {
        registerAllOptions();
    }

    /** The Compact Chat Window value: "Back-to-Back Only", "45 s", "1 min", "2 min 30 s". */
    private static String formatWindow(float value) {
        if (Math.round(value) == 0) return "Back-to-Back Only";
        return formatDuration(value);
    }

    /** A slider value in seconds: "45 s", "1 min", "2 min 30 s". */
    private static String formatDuration(float value) {
        int seconds = Math.round(value);
        if (seconds < 60) return seconds + " s";
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return rest == 0 ? minutes + " min" : minutes + " min " + rest + " s";
    }

    private static void registerAllOptions() {
        OPTIONS.clear();

        // The General tab is what opens first, so it holds only what a new player needs before
        // anything else: whether the mod may reach the network at all, how the mod's own menus look
        // and whether they replace vanilla's, the one place HUDs are arranged, and the way back out
        // of everything.
        OPTIONS.add(new ConfigOption("allow_api_calls", "Network Features",
                "Lets the mod go online for the mayor's slayer XP buff, the main menu's player count, the update check, SkyCofl's price history and the Profile Dashboard.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.allowApiCalls,
                v -> { AlpakaConfig.instance.allowApiCalls = v; AlpakaConfig.save(); },
                "api network internet hypixel request mayor election offline privacy allow network features"));

        OPTIONS.add(new ConfigOption("update_notifications", "Update Check",
                "Tells you in chat when a new version is out, with a button to install it. Needs Network Features.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.updateNotificationsEnabled,
                v -> { AlpakaConfig.instance.updateNotificationsEnabled = v; AlpakaConfig.save(); },
                "update new version release github download install notify check outdated update notifications"));

        OPTIONS.add(new ConfigOption("update_stream", "Update Stream",
                "Which versions the update check tells you about: full releases only, or pre-releases as well.",
                ConfigCategory.GENERAL,
                () -> (float) UpdateChecker.stream(),
                v -> { AlpakaConfig.instance.updateStream = Math.round(v); AlpakaConfig.save(); UpdateChecker.streamChanged(); },
                0.0f, (float) (UpdateChecker.STREAM_NAMES.length - 1),
                val -> UpdateChecker.STREAM_NAMES[Math.round(val)],
                "update stream channel release pre-release prerelease beta alpha snapshot test version").choices(UpdateChecker.STREAM_NAMES));

        // Duration, position and a test notice, together as one feature with the test on top.
        OPTIONS.add(new ConfigOption("Notification Customization", ConfigCategory.GENERAL));

        OPTIONS.add(new ConfigOption("notification_test", "Send Test Notification",
                "Shows a sample notification with the duration and position set below.",
                ConfigCategory.GENERAL,
                "Send Test Notification",
                parent -> AlpakaNotifications.send("Test Notification",
                        "This is how a notification looks. It slides away after the duration you set."),
                "notification test preview sample try show notice"));

        OPTIONS.add(new ConfigOption("notification_hold", "Duration",
                "How long a notification stays on screen before sliding away.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.notificationHoldSeconds,
                v -> { AlpakaConfig.instance.notificationHoldSeconds = v; AlpakaConfig.save(); },
                1.0f, 15.0f, val -> String.format(Locale.ROOT, "%.1f s", val),
                "notification duration hold seconds popup toast time linger notice corner"));

        OPTIONS.add(new ConfigOption("notification_corner", "Position",
                "Where notifications appear: a corner or the top center.",
                ConfigCategory.GENERAL,
                () -> (float) AlpakaConfig.instance.notificationCorner,
                v -> { AlpakaConfig.instance.notificationCorner = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (AlpakaNotifications.CORNER_NAMES.length - 1),
                val -> AlpakaNotifications.CORNER_NAMES[Math.round(val)],
                "notification corner position bottom top left right center centre popup toast side notices").choices(AlpakaNotifications.CORNER_NAMES));


        // The menu's own typeface sits here rather than under Visuals: it changes the screen the
        // player is looking at right now, so it belongs on the tab that screen opens on.
        OPTIONS.add(new ConfigOption("menu_font", "Menu Font",
                "The typeface the Alpaka menus are written in, and the HUDs that use Smooth Font.",
                ConfigCategory.GENERAL,
                () -> (float) GuiFont.selectedIndex(),
                v -> { AlpakaConfig.instance.menuFont = Math.round(v); AlpakaConfig.save(); GuiFont.refreshChat(); },
                0.0f, (float) (GuiFont.NAMES.length - 1),
                val -> GuiFont.NAMES[Math.round(val)],
                "menu font typeface text smooth modern inter poppins varela round outfit nunito minecraft pixel gui bold").choices(GuiFont.NAMES));

        OPTIONS.add(new ConfigOption("menu_theme", "Menu Theme",
                "How the Alpaka menus look: light, dark, pure black for OLED screens, or coffee brown.",
                ConfigCategory.GENERAL,
                () -> (float) java.util.Arrays.asList(AlpakaStyle.Theme.MENU_ORDER).indexOf(AlpakaStyle.Theme.of(AlpakaConfig.instance.menuTheme)),
                v -> { AlpakaConfig.instance.menuTheme = AlpakaStyle.Theme.MENU_ORDER[Math.round(v)].ordinal(); AlpakaConfig.save(); AlpakaConfig.applyMenuTheme(); },
                0.0f, (float) (AlpakaStyle.THEME_NAMES.length - 1),
                val -> AlpakaStyle.THEME_NAMES[Math.round(val)],
                "menu theme look mode dark light white bright oled black amoled night day appearance gui colors cappuccino coffee brown espresso mocha warm").choices(AlpakaStyle.THEME_NAMES));

        // How the HUDs look, chat and scoreboard included: main switches for the blurred panel and
        // the menu font, and the one blur strength they share. Each HUD's own switches sit in its
        // settings; see registerHudStyleToggles.
        OPTIONS.add(new ConfigOption("HUD Style", ConfigCategory.GENERAL));

        OPTIONS.add(new ConfigOption("hud_blur_all", "Blur",
                "Main switch for the blurred panels behind the HUDs, chat and scoreboard included. Each HUD also has its own Blur switch.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.hudBlurAll,
                v -> { AlpakaConfig.instance.hudBlurAll = v; AlpakaConfig.save(); },
                "blur blurred background panel glass frosted rounded shadow hud all main own settings"));

        OPTIONS.add(new ConfigOption("hud_smooth_font_all", "Smooth Font",
                "Main switch for writing the HUDs in the Menu Font. Each HUD also has its own Smooth Font switch.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.hudSmoothFontAll,
                v -> { AlpakaConfig.instance.hudSmoothFontAll = v; AlpakaConfig.save(); GuiFont.refreshChat(); },
                "smooth font menu font typeface text nunito hud all main own settings"));

        OPTIONS.add(new ConfigOption("hud_blur_strength", "Blur Strength",
                "How strongly the world is blurred behind every blurred panel, the command wheel's included.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.chatBlurStrength,
                v -> { AlpakaConfig.instance.chatBlurStrength = v; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Off (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "blur strength radius amount intensity frosted glass background lighter clearer sharp"));

        OPTIONS.add(ConfigOption.color("menu_accent_color", "Menu Accent Color",
                "The highlight color of every Alpaka menu.",
                ConfigCategory.GENERAL,
                c -> c.menuAccentColor,
                v -> { AlpakaConfig.instance.menuAccentColor = v; AlpakaConfig.save(); },
                "menu accent color theme custom picker border highlight gui teal gold cyan red green blue"));

        OPTIONS.add(new ConfigOption("custom_escape_menu", "Custom Pause Menu",
                "Replaces the pause menu with the Alpaka one.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customEscapeMenuEnabled,
                v -> { AlpakaConfig.instance.customEscapeMenuEnabled = v; AlpakaConfig.save(); },
                "custom escape menu pause screen esc button design visuals alpaka-styled"));

        OPTIONS.add(new ConfigOption("custom_main_menu", "Custom Main Menu",
                "Replaces the main menu with the Alpaka one.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customMainMenuEnabled,
                v -> { AlpakaConfig.instance.customMainMenuEnabled = v; AlpakaConfig.save(); },
                "custom main menu title screen hypixel start launch background GUI hypixel-themed"));

        OPTIONS.add(new ConfigOption("custom_main_menu_parallax", "Main Menu Parallax",
                "The custom main menu's background drifts and moves with the mouse. Off keeps it still.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customMainMenuParallax,
                v -> { AlpakaConfig.instance.customMainMenuParallax = v; AlpakaConfig.save(); },
                "custom main menu parallax background mouse move motion depth effect title screen"));

        OPTIONS.add(new ConfigOption("custom_main_menu_particles", "Inventory Particles in Main Menu",
                "Shows your Inventory Particles behind the custom main menu too.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customMainMenuParticles,
                v -> { AlpakaConfig.instance.customMainMenuParticles = v; AlpakaConfig.save(); },
                "custom main menu inventory particles snow rain fireflies cherry petals leaves glyphs ash title screen hide"));

        OPTIONS.add(new ConfigOption("disable_all_features", "Disable All Features",
                "Turns off every feature of the mod, after asking first.",
                ConfigCategory.GENERAL,
                "Disable All Features",
                parent -> AlpakaConfig.instance.disableAllFeatures(),
                "disable all features turn off reset everywhere confirm prompt disables confirmation")
                .confirm("Disable All Features",
                        "Turns off every feature of Alpaka Addons. Their settings stay, but which features "
                                + "were on is not remembered. This cannot be undone.",
                        "Disable All", true));

        OPTIONS.add(new ConfigOption("reset_all_settings", "Reset All Settings",
                "Puts every setting of the mod back to its default, after asking first.",
                ConfigCategory.GENERAL,
                "Reset All Settings",
                parent -> {
                    AlpakaConfig.resetToDefaults();
                    net.alpaka.addons.client.AlpakaKeyBinds.applyAll();
                    GuiFont.refreshChat();
                },
                "reset all settings default defaults factory restore clear confirm prompt confirmation")
                .confirm("Reset All Settings",
                        "Puts every setting of Alpaka Addons back to its default, HUD positions, keys and Command Wheel commands included. "
                                + "Slayer stats and saved viewmodel presets are kept. This cannot be undone.",
                        "Reset All", true));

        // A setting of Custom Main Menu (ConfigTree.PARENTS), registered here rather than right
        // under it so it does not collide with the other main menu settings added there.
        OPTIONS.add(new ConfigOption("custom_main_menu_whats_new", "What's New Card",
                "Shows the newest release's changes at the top right of the custom main menu. Needs Network Features.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customMainMenuWhatsNew,
                v -> { AlpakaConfig.instance.customMainMenuWhatsNew = v; AlpakaConfig.save(); },
                "custom main menu what's new whats new release notes changelog changes update card news"));

        // --- 1. VISUALS & RENDERING ---

        OPTIONS.add(new ConfigOption("World & Mobs", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("fullbright", "Fullbright",
                "Lights up the whole world at full brightness, no torches needed.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.fullbrightEnabled,
                v -> { AlpakaConfig.instance.fullbrightEnabled = v; AlpakaConfig.save(); },
                "gamma brightness light fullbright vision enables maximum everywhere torches"));

        OPTIONS.add(new ConfigOption("sky_effects", "Sky Effects",
                "Animates the sky: a firestorm over the Nether, auroras and shooting stars over the Overworld at night and a glowing sun by day, a swirling nebula in the End.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.skyEffectsEnabled,
                v -> { AlpakaConfig.instance.skyEffectsEnabled = v; AlpakaConfig.save(); },
                "sky skies animated fancy firestorm fire storm nether crimson isle aurora northern lights borealis shooting stars meteor milky way galaxy nebula end space sun rays glow embers"));

        OPTIONS.add(new ConfigOption("sky_effects_skies", "Animated Skies",
                "Pick which skies are animated.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Nether Firestorm",
                                () -> AlpakaConfig.instance.skyEffectsNether,
                                v -> { AlpakaConfig.instance.skyEffectsNether = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Overworld Aurora",
                                () -> AlpakaConfig.instance.skyEffectsOverworld,
                                v -> { AlpakaConfig.instance.skyEffectsOverworld = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("End Nebula",
                                () -> AlpakaConfig.instance.skyEffectsEnd,
                                v -> { AlpakaConfig.instance.skyEffectsEnd = v; AlpakaConfig.save(); })
                ),
                "sky skies nether firestorm overworld aurora sun rays end nebula dimension pick which"));

        OPTIONS.add(new ConfigOption("sky_effects_intensity", "Sky Intensity",
                "How strongly the effects show over the plain sky.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.skyEffectsIntensity,
                v -> { AlpakaConfig.instance.skyEffectsIntensity = Math.round(v * 100.0f) / 100.0f; AlpakaConfig.save(); },
                0.1f, 1.0f, val -> String.format(Locale.ROOT, "%.0f%%", val * 100.0f),
                "sky intensity strength opacity brightness subtle strong effects"));

        OPTIONS.add(new ConfigOption("sky_effects_speed", "Sky Animation Speed",
                "How fast the clouds, lights and stars move.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.skyEffectsSpeed,
                v -> { AlpakaConfig.instance.skyEffectsSpeed = Math.round(v * 20.0f) / 20.0f; AlpakaConfig.save(); },
                0.25f, 3.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "sky animation speed fast slow motion clouds drift"));

        OPTIONS.add(new ConfigOption("clean_blaze", "Clean Blaze",
                "Hides visual clutter in the Blaze slayer's zones, on SkyBlock only.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.cleanBlazeEnabled,
                v -> { AlpakaConfig.instance.cleanBlazeEnabled = v; AlpakaConfig.save(); },
                "blaze smoke particles fire nametag clear view slayer inferno crimson clears noise zones"));

        OPTIONS.add(new ConfigOption("clean_blaze_parts", "Clean Blaze Parts",
                "Pick what Clean Blaze hides.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Flame, Smoke & Lava Particles",
                                () -> AlpakaConfig.instance.cleanBlazeParticles,
                                v -> { AlpakaConfig.instance.cleanBlazeParticles = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Potion & Magic Particles",
                                () -> AlpakaConfig.instance.cleanBlazeEffectParticles,
                                v -> { AlpakaConfig.instance.cleanBlazeEffectParticles = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Attunement Dust",
                                () -> AlpakaConfig.instance.cleanBlazeAttunementDust,
                                v -> { AlpakaConfig.instance.cleanBlazeAttunementDust = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Fire on Blazes & Demons",
                                () -> AlpakaConfig.instance.cleanBlazeFire,
                                v -> { AlpakaConfig.instance.cleanBlazeFire = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Fireballs",
                                () -> AlpakaConfig.instance.cleanBlazeFireballs,
                                v -> { AlpakaConfig.instance.cleanBlazeFireballs = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Lightning",
                                () -> AlpakaConfig.instance.cleanBlazeLightning,
                                v -> { AlpakaConfig.instance.cleanBlazeLightning = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Name Tags",
                                () -> AlpakaConfig.instance.cleanBlazeNameTags,
                                v -> { AlpakaConfig.instance.cleanBlazeNameTags = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Wrong Dagger Messages",
                                () -> AlpakaConfig.instance.cleanBlazeWrongDaggerChat,
                                v -> { AlpakaConfig.instance.cleanBlazeWrongDaggerChat = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Damage Messages",
                                () -> AlpakaConfig.instance.cleanBlazeDamageChat,
                                v -> { AlpakaConfig.instance.cleanBlazeDamageChat = v; AlpakaConfig.save(); })
                ),
                "blaze flame smoke angry villager lava drip particles potion effect swirl enchant enchanting glyph ender portal witch spell attunement dust ashen spirit crystal auric hellion shield fire burning overlay fireball projectile lightning bolt flash thunder nametag name tag health smoldering miniboss demon chat message wrong dagger strike damage true damage beam reduced clean hide"));

        OPTIONS.add(new ConfigOption("stop_blaze_spinning", "Stop Blaze Spinning",
                "Stops blaze rods from spinning around blaze mobs.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.stopBlazeSpinning,
                v -> { AlpakaConfig.instance.stopBlazeSpinning = v; AlpakaConfig.save(); },
                "blaze rods spin spinning rotation animation stop mob"));

        OPTIONS.add(new ConfigOption("mute_vanilla_sounds_blaze_slayer", "Mute Blaze Noise",
                "Mutes blazes, fire, lava and ghasts during a Blaze quest. Menu and mod sounds still play.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.muteVanillaSoundsInBlazeSlayer,
                v -> { AlpakaConfig.instance.muteVanillaSoundsInBlazeSlayer = v; AlpakaConfig.save(); },
                "mute silence vanilla game audio blaze slayer quiet boss spawn hear silence blaze slayer noise"));

        OPTIONS.add(new ConfigOption("blaze_mute_all_world_audio", "Mute All World Sounds",
                "Mutes every world sound during a Blaze quest, not just the fight's noise.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.blazeMuteAllWorldAudio,
                v -> { AlpakaConfig.instance.blazeMuteAllWorldAudio = v; AlpakaConfig.save(); },
                "mute silence all world audio blaze slayer quiet silence: all world audio sounds"));

        OPTIONS.add(new ConfigOption("blaze_fire_pits", "Fire Pit Highlight",
                "Marks the fire pits on the floor with a glowing column during your Blaze slayer boss.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.blazeFirePitsEnabled,
                v -> { AlpakaConfig.instance.blazeFirePitsEnabled = v; AlpakaConfig.save(); },
                "blaze slayer fire pit pits clay terracotta floor flames aura highlight inferno demonlord boss warning"));

        OPTIONS.add(ConfigOption.color("blaze_fire_pit_color", "Fire Pit Color",
                "Color of the fire pit column.",
                ConfigCategory.VISUALS,
                c -> c.blazeFirePitColor,
                v -> { AlpakaConfig.instance.blazeFirePitColor = v; AlpakaConfig.save(); },
                "blaze slayer fire pit pits highlight column aura color colour picker"));

        OPTIONS.add(new ConfigOption("hide_hurt_overlay", "Hide Damage Flash",
                "Hides the red flash on mobs and players when they take a hit.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideHurtOverlayEnabled,
                v -> { AlpakaConfig.instance.hideHurtOverlayEnabled = v; AlpakaConfig.save(); },
                "hurt damage red flash tint overlay hit mob entity hide"));

        OPTIONS.add(new ConfigOption("hide_mob_deaths", "Hide Mob Deaths",
                "Makes mobs and their name tags vanish the moment they die instead of tipping over first.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideMobDeathsEnabled,
                v -> { AlpakaConfig.instance.hideMobDeathsEnabled = v; AlpakaConfig.save(); },
                "hide mob death deaths animation tip over fall corpse vanish instant remove"));

        OPTIONS.add(new ConfigOption("hide_mob_death_smoke", "Hide Death Smoke",
                "Removes the puff of smoke a dead mob leaves behind as well.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideMobDeathSmoke,
                v -> { AlpakaConfig.instance.hideMobDeathSmoke = v; AlpakaConfig.save(); },
                "hide mob death smoke poof particles cloud puff"));

        OPTIONS.add(new ConfigOption("hide_explosions", "Hide Explosions",
                "Hides the flash, smoke and debris of explosions.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideExplosions,
                v -> { AlpakaConfig.instance.hideExplosions = v; AlpakaConfig.save(); },
                "hide explosion explosions explode tnt creeper hyperion wither impact implosion bonzo particles flash cloud smoke"));

        OPTIONS.add(new ConfigOption("Hide Fire Overlay", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("hide_entity_fire", "Hide Mob Fire",
                "Hides the flames on burning mobs and players, and on yourself in third person.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideEntityFire,
                v -> { AlpakaConfig.instance.hideEntityFire = v; AlpakaConfig.save(); },
                "hide fire flame flames burning burn mob entity player overlay on fire"));

        OPTIONS.add(new ConfigOption("first_person_fire_opacity", "First-Person Fire Opacity",
                "How strongly the fire on your screen shows while you burn. 100% is vanilla, 0% hides it.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.firstPersonFireOpacity * 100.0f,
                v -> { AlpakaConfig.instance.firstPersonFireOpacity = Math.round(v) / 100.0f; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Hidden (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "hide first person fire flame opacity transparency fade weaker screen overlay burning low fire"));

        OPTIONS.add(new ConfigOption("first_person_fire_height", "First-Person Fire Height",
                "How far the fire on your screen reaches up while you burn. 100% is vanilla.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.firstPersonFireHeight * 100.0f,
                v -> { AlpakaConfig.instance.firstPersonFireHeight = Math.round(v) / 100.0f; AlpakaConfig.save(); },
                10.0f, 100.0f, val -> String.format(Locale.ROOT, "%.0f%%", val),
                "first person fire flame height low lower fire smaller screen overlay burning"));

        OPTIONS.add(new ConfigOption("first_person_fire_static", "Disable Fire Animation",
                "Shows the fire on your screen as a still image instead of flickering. Fire in the world keeps moving.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.firstPersonFireStatic,
                v -> { AlpakaConfig.instance.firstPersonFireStatic = v; AlpakaConfig.save(); },
                "first person fire flame animation animated flicker still static freeze screen overlay burning"));

        OPTIONS.add(new ConfigOption("Nametags", ConfigCategory.VISUALS));

        OPTIONS.add(ConfigOption.color("name_tag_background_color", "Background Color",
                "The box behind every nametag. Set the opacity to 0 to remove it and leave only the text.",
                ConfigCategory.VISUALS,
                c -> c.nameTagBackgroundColor,
                v -> { AlpakaConfig.instance.nameTagBackgroundColor = v; AlpakaConfig.save(); },
                "name tag nametag background backdrop box dark color colour opacity hide remove transparent player mob"));

        OPTIONS.add(new ConfigOption("name_tag_text_shadow", "Text Shadow",
                "Draws the text of every nametag with the usual Minecraft drop shadow.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.nameTagTextShadow,
                v -> { AlpakaConfig.instance.nameTagTextShadow = v; AlpakaConfig.save(); },
                "name tag nametag text shadow drop readable player mob"));

        OPTIONS.add(new ConfigOption("show_own_name_tag", "Show Own Nametag",
                "Shows your own nametag above you in third person. Custom Name Tag draws its animated one instead.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.showOwnNameTag,
                v -> { AlpakaConfig.instance.showOwnNameTag = v; AlpakaConfig.save(); },
                "own name tag nametag self third person f5 see my name show"));

        OPTIONS.add(new ConfigOption("Camera", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("render_hand_third_person", "First-Person Hand in F5",
                "Draws your first-person hand and item in the third-person back view too.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.renderHandInThirdPerson,
                v -> { AlpakaConfig.instance.renderHandInThirdPerson = v; AlpakaConfig.save(); },
                "hand view third person render"));

        OPTIONS.add(new ConfigOption("smooth_perspective", "Smooth Perspective",
                "Animates the camera smoothly when you switch perspective.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.smoothPerspectiveEnabled,
                v -> { AlpakaConfig.instance.smoothPerspectiveEnabled = v; AlpakaConfig.save(); },
                "smooth perspective camera transition f5 third person first person"));

        OPTIONS.add(new ConfigOption("disable_front_perspective", "Disable Front Perspective",
                "Makes F5 skip the front view and switch only between first and third person.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.disableFrontPerspective,
                v -> { AlpakaConfig.instance.disableFrontPerspective = v; AlpakaConfig.save(); },
                "disable front perspective skip view f5 third person first person camera 1st 3rd toggles"));

        OPTIONS.add(new ConfigOption("smooth_perspective_duration", "Transition Duration",
                "How long the perspective transition takes.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.smoothPerspectiveDurationMs,
                v -> { AlpakaConfig.instance.smoothPerspectiveDurationMs = Math.round(v); AlpakaConfig.save(); },
                100.0f, 1000.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "smooth perspective camera speed duration transition time ms"));

        OPTIONS.add(new ConfigOption("zoom", "Zoom",
                "Zooms in while you hold the Zoom key, or from one press to the next with Toggle Zoom.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.zoomEnabled,
                v -> { AlpakaConfig.instance.zoomEnabled = v; AlpakaConfig.save(); },
                "zoom optifine spyglass binoculars magnify fov camera scroll hold"));

        OPTIONS.add(ConfigOption.keybind("zoom_key", "Key",
                "The key to hold for zooming. C by default.",
                ConfigCategory.VISUALS,
                () -> net.alpaka.addons.features.zoom.ZoomFeature.ZOOM_KEY,
                "zoom optifine"));

        OPTIONS.add(new ConfigOption("zoom_toggle", "Toggle Zoom",
                "Press the key once to zoom in and again to zoom out, instead of holding it.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.zoomToggleMode,
                v -> { AlpakaConfig.instance.zoomToggleMode = v; AlpakaConfig.save(); },
                "zoom toggle hold press mode sticky"));

        OPTIONS.add(new ConfigOption("zoom_amount", "Zoom Amount",
                "How far the zoom goes in at first.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.zoomAmount,
                v -> { AlpakaConfig.instance.zoomAmount = Math.round(v * 2.0f) / 2.0f; AlpakaConfig.save(); },
                1.5f, 20.0f, val -> String.format(Locale.ROOT, "%.1fx", val),
                "zoom amount factor strength level how far magnification fov"));

        OPTIONS.add(new ConfigOption("zoom_scroll", "Scroll to Zoom",
                "Scrolling while zoomed zooms further in or out instead of changing the hotbar slot.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.zoomScrollEnabled,
                v -> { AlpakaConfig.instance.zoomScrollEnabled = v; AlpakaConfig.save(); },
                "zoom scroll wheel mouse adjust further in out hotbar"));

        OPTIONS.add(new ConfigOption("zoom_animation", "Animation",
                "How the view moves in and out: at once, easing smoothly, or with a little bounce.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.zoomAnimation,
                v -> { AlpakaConfig.instance.zoomAnimation = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.zoom.ZoomFeature.ANIMATIONS.length - 1),
                val -> net.alpaka.addons.features.zoom.ZoomFeature.ANIMATIONS[Math.round(val)],
                "zoom animation effect smooth instant bouncy spring ease transition")
                .choices(net.alpaka.addons.features.zoom.ZoomFeature.ANIMATIONS));

        OPTIONS.add(new ConfigOption("zoom_animation_duration", "Animation Duration",
                "How long zooming in or out takes. Has no effect with the Instant animation.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.zoomAnimationMs,
                v -> { AlpakaConfig.instance.zoomAnimationMs = Math.round(v / 10.0f) * 10; AlpakaConfig.save(); },
                50.0f, 1000.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "zoom animation duration speed smoothness time ms slow fast"));

        OPTIONS.add(new ConfigOption("zoom_cinematic_camera", "Cinematic Camera",
                "Smooths the mouse while zoomed, like vanilla's cinematic camera.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.zoomCinematicCamera,
                v -> { AlpakaConfig.instance.zoomCinematicCamera = v; AlpakaConfig.save(); },
                "zoom cinematic camera smooth mouse f8 optifine"));

        OPTIONS.add(new ConfigOption("zoom_reduce_sensitivity", "Lower Sensitivity",
                "Slows the mouse down with the zoom, so aiming feels the same zoomed in.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.zoomReduceSensitivity,
                v -> { AlpakaConfig.instance.zoomReduceSensitivity = v; AlpakaConfig.save(); },
                "zoom mouse sensitivity slow aim precise reduce"));

        OPTIONS.add(new ConfigOption("Interfaces", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("container_bg_opacity", "Container Background Opacity",
                "How dark the background behind container menus is. Vanilla is 75%.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.containerBgOpacity * 100.0f,
                v -> { AlpakaConfig.instance.containerBgOpacity = v / 100.0f; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Off (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "container background darkening opacity overlay tint inventory chest GUI darkening guis disabled off"));

        OPTIONS.add(new ConfigOption("container_bg_fade_in", "Container Background Fade-In",
                "Fades in the dark background when a container opens.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.containerBgFadeInEnabled,
                v -> { AlpakaConfig.instance.containerBgFadeInEnabled = v; AlpakaConfig.save(); },
                "container background fade in smooth transition gui smoothly opening guis"));

        OPTIONS.add(new ConfigOption("container_bg_fade_duration", "Fade-In Duration",
                "How long the background takes to fade in.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.containerBgFadeInDurationMs,
                v -> { AlpakaConfig.instance.containerBgFadeInDurationMs = Math.round(v); AlpakaConfig.save(); },
                50.0f, 1000.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "container background fade speed duration time ms transition container fade speed"));

        OPTIONS.add(new ConfigOption("hide_inventory_effects", "Hide Effects",
                "Hides the list of active potion effects next to your inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideInventoryEffects,
                v -> { AlpakaConfig.instance.hideInventoryEffects = v; AlpakaConfig.save(); },
                "hide effects potion effect status buffs inventory list side absorption night vision haste strength god potion"));

        // The id keeps its old name so existing configs and search links still find the option.
        OPTIONS.add(new ConfigOption("inventory_snow", "Inventory Particles",
                "Lets particles drift over container screens, like chests and your inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowEnabled,
                v -> { AlpakaConfig.instance.inventorySnowEnabled = v; AlpakaConfig.save(); },
                "snow winter effect gui inventory snowflakes particles fireflies ash cherry petals leaves autumn enchanting glyphs rain"));

        OPTIONS.add(new ConfigOption("inventory_particle_type", "Particle Type",
                "Which particles drift over container screens.",
                ConfigCategory.VISUALS,
                () -> (float) SnowOverlayRenderer.clampType(AlpakaConfig.instance.inventoryParticleType),
                v -> { AlpakaConfig.instance.inventoryParticleType = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (SnowOverlayRenderer.TYPE_NAMES.length - 1),
                val -> SnowOverlayRenderer.TYPE_NAMES[SnowOverlayRenderer.clampType(Math.round(val))],
                "inventory particle type kind snowflakes fireflies ash snow embers cherry petals blossom falling leaves autumn fall enchanting glyphs runes enchant rain").choices(SnowOverlayRenderer.TYPE_NAMES));

        OPTIONS.add(new ConfigOption("inventory_snow_speed", "Particle Speed",
                "How fast the particles move.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowSpeed,
                v -> { AlpakaConfig.instance.inventorySnowSpeed = v; AlpakaConfig.save(); },
                0.1f, 5.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "snow speed animation winter velocity particles snow fall speed"));

        OPTIONS.add(new ConfigOption("inventory_snow_amount", "Particle Amount",
                "How many particles are on screen at once.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowAmount,
                v -> { AlpakaConfig.instance.inventorySnowAmount = v; AlpakaConfig.save(); },
                SnowOverlayRenderer.MIN_AMOUNT, SnowOverlayRenderer.MAX_AMOUNT,
                val -> String.format(Locale.ROOT, "%.2fx", val),
                "snow amount count density number flakes winter more less particles snowflake amount"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips", "Scrollable Tooltips",
                "Lets the mouse wheel scroll a tooltip that is taller than the screen.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsEnabled,
                v -> { AlpakaConfig.instance.scrollableTooltipsEnabled = v; AlpakaConfig.save(); },
                "tooltip scroll wheel long tall item lore overflow cut off screen"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips_always", "Always Scrollable",
                "Lets the wheel move every tooltip, even one that fits on screen.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsAlways,
                v -> { AlpakaConfig.instance.scrollableTooltipsAlways = v; AlpakaConfig.save(); },
                "tooltip scroll always short fits move wheel"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips_speed", "Scroll Speed",
                "How far one notch of the wheel moves a tooltip.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsSpeed,
                v -> { AlpakaConfig.instance.scrollableTooltipsSpeed = (float) Math.round(v); AlpakaConfig.save(); },
                4.0f, 40.0f, val -> Math.round(val) + " px",
                "tooltip scroll speed step distance wheel fast slow"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips_smooth", "Smooth Scrolling",
                "Makes the tooltip glide instead of jumping.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsSmooth,
                v -> { AlpakaConfig.instance.scrollableTooltipsSmooth = v; AlpakaConfig.save(); },
                "tooltip scroll smooth glide animation glides jumping"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips_invert", "Invert Direction",
                "Makes wheel down move the tooltip down instead of reading further down it.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsInvert,
                v -> { AlpakaConfig.instance.scrollableTooltipsInvert = v; AlpakaConfig.save(); },
                "tooltip scroll invert reverse direction natural"));

        OPTIONS.add(new ConfigOption("persistent_item_name", "Keep Item Name Visible",
                "Keeps the held item's name above the hotbar from fading out.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.persistentItemNameEnabled,
                v -> { AlpakaConfig.instance.persistentItemNameEnabled = v; AlpakaConfig.save(); },
                "item name hotbar held tool highlight fade permanent always show no longer fades"));

        OPTIONS.add(new ConfigOption("old_master_stars", "Old Master Stars",
                "Master stars turn the stars red again, as they used to, instead of a number behind them. In tooltips and above the hotbar.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.oldMasterStarsEnabled,
                v -> { AlpakaConfig.instance.oldMasterStarsEnabled = v; AlpakaConfig.save(); },
                "master stars red dungeon star number revert old item name tooltip hotbar"));

        OPTIONS.add(new ConfigOption("storage_preview", "Storage Preview",
                "Hovering a backpack or ender chest page in the Storage menu shows what is inside. Each one has to be opened once first.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.storagePreviewEnabled,
                v -> { AlpakaConfig.instance.storagePreviewEnabled = v; AlpakaConfig.save(); },
                "storage preview backpack ender chest enderchest page contents hover tooltip items storage menu"));

        OPTIONS.add(new ConfigOption("wide_health_bar", "Wide Health Bar",
                "Hides the hunger and armor bars and joins both heart rows into one long row.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.wideHealthBarEnabled,
                v -> { AlpakaConfig.instance.wideHealthBarEnabled = v; AlpakaConfig.save(); },
                "health hearts hunger food armor bar hide long wide single row absorption hotbar skyblock life"));

        OPTIONS.add(new ConfigOption("wide_health_bar_centered", "Center Health Bar",
                "Puts the hearts in one unbroken row over the middle of the hotbar. The level number moves onto the XP bar.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.wideHealthBarCentered,
                v -> { AlpakaConfig.instance.wideHealthBarCentered = v; AlpakaConfig.save(); },
                "center centre health bar hearts middle row unbroken level xp experience"));

        OPTIONS.add(new ConfigOption("wide_health_bar_absorption", "Absorption Hearts",
                "Where absorption hearts go: a row of their own, in gold on the first hearts (used up first), or hidden.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.wideHealthBarAbsorption,
                v -> { AlpakaConfig.instance.wideHealthBarAbsorption = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.healthbar.WideHealthBarFeature.ABSORPTION_NAMES.length - 1),
                val -> net.alpaka.addons.features.healthbar.WideHealthBarFeature.ABSORPTION_NAMES[Math.round(val)],
                "absorption hearts gold golden yellow hide merge health row gold over health on hearts own row")
                .choices(net.alpaka.addons.features.healthbar.WideHealthBarFeature.ABSORPTION_NAMES));

        OPTIONS.add(new ConfigOption("custom_tab", "Custom Tab",
                "Redraws the SkyBlock tab list as one clean panel, sorted into its categories, without the empty slots.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabEnabled,
                v -> { AlpakaConfig.instance.customTabEnabled = v; AlpakaConfig.save(); },
                "custom tab list tablist player list compact clean panel sorted widgets columns skyblock"));

        OPTIONS.add(new ConfigOption("custom_tab_layout", "Layout",
                "Side by Side puts the categories next to each other, Stacked puts them under each other.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.customTabLayout,
                v -> { AlpakaConfig.instance.customTabLayout = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.tablist.CustomTabFeature.LAYOUT_NAMES.length - 1),
                val -> net.alpaka.addons.features.tablist.CustomTabFeature.LAYOUT_NAMES[Math.round(val)],
                "tab list layout side by side stacked columns rows horizontal vertical next to under each other")
                .choices(net.alpaka.addons.features.tablist.CustomTabFeature.LAYOUT_NAMES));

        OPTIONS.add(new ConfigOption("custom_tab_hide_ads", "Hide Hypixel Ads",
                "Hides the server address and the store link Hypixel puts above and below the tab list.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabHideAds,
                v -> { AlpakaConfig.instance.customTabHideAds = v; AlpakaConfig.save(); },
                "tab list hide ads advertising advert store hypixel.net header footer ranks boosters"));

        OPTIONS.add(new ConfigOption("custom_tab_toggle", "Toggle Tab List",
                "One press of the player list key opens the tab list and the next closes it, instead of holding the key.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabToggle,
                v -> { AlpakaConfig.instance.customTabToggle = v; AlpakaConfig.save(); },
                "tab list toggle press hold key keep open player list switch"));

        OPTIONS.add(new ConfigOption("custom_tab_in_menus", "Show in Menus",
                "Keeps the tab list up while the chat, your inventory or a chest is open, drawn above it.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabInMenus,
                v -> { AlpakaConfig.instance.customTabInMenus = v; AlpakaConfig.save(); },
                "tab list menus chat inventory chest container open screen gui visible stay over"));

        OPTIONS.add(new ConfigOption("custom_tab_progress_bars", "Progress Bars",
                "Draws progress as bars: Hypixel's bars of lines, counts toward a goal like Fairy Souls and the RNG Meter, and skill percentages.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabProgressBars,
                v -> { AlpakaConfig.instance.customTabProgressBars = v; AlpakaConfig.save(); },
                "tab list progress bar bars meter skill percent rng reputation fairy souls level xp"));

        OPTIONS.add(new ConfigOption("custom_tab_highlight_changes", "Highlight Changes",
                "Lights a row up for a moment when its value changes, so you see at a glance what is new. Timers are left out.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabHighlightChanges,
                v -> { AlpakaConfig.instance.customTabHighlightChanges = v; AlpakaConfig.save(); },
                "tab list highlight changes flash glow new value changed update blink"));

        OPTIONS.add(new ConfigOption("custom_tab_section_boxes", "Section Boxes",
                "Puts each part of the tab list, like Area, Profile or Pet, in a faint box of its own so the parts are easy to tell apart.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabSectionBoxes,
                v -> { AlpakaConfig.instance.customTabSectionBoxes = v; AlpakaConfig.save(); },
                "tab list section boxes box border outline frame card widget group block separate"));

        OPTIONS.add(new ConfigOption("custom_tab_animated", "Slide Animation",
                "The tab list slides down from the top of the screen when it opens and back up when it closes, instead of appearing at once.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabAnimated,
                v -> { AlpakaConfig.instance.customTabAnimated = v; AlpakaConfig.save(); },
                "tab list slide animation animated open close smooth dock drop down appear instantly"));

        OPTIONS.add(new ConfigOption("custom_tab_background", "Background",
                "Draws the panel behind the tab list. Off leaves only the text.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabBackground,
                v -> { AlpakaConfig.instance.customTabBackground = v; AlpakaConfig.save(); },
                "tab list background panel box transparent none hide disable off text only"));

        OPTIONS.add(new ConfigOption("custom_tab_scale", "Scale",
                "How big the tab list is drawn.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customTabScale,
                v -> { AlpakaConfig.instance.customTabScale = Math.round(v * 20.0f) / 20.0f; AlpakaConfig.save(); },
                0.5f, 1.5f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "tab list scale size bigger smaller zoom"));

        OPTIONS.add(new ConfigOption("Chat", ConfigCategory.VISUALS));

        // Better Chat: everything that changes how the chat looks, its parts as its settings. The
        // background and font switches are registered with the other HUD style switches below.
        OPTIONS.add(new ConfigOption("better_chat", "Better Chat",
                "Makes the chat look better: it opens smoothly, messages slide in, scrolling glides, a blurred background and the smooth font.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.betterChatEnabled,
                v -> { AlpakaConfig.instance.betterChatEnabled = v; AlpakaConfig.save(); GuiFont.refreshChat(); },
                "better chat look design style smooth blur background font animation scroll opening main switch"));

        OPTIONS.add(new ConfigOption("smooth_chat", "Smooth Chat",
                "Slides and fades new messages in instead of showing them at once.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.smoothChatEnabled,
                v -> { AlpakaConfig.instance.smoothChatEnabled = v; AlpakaConfig.save(); },
                "smooth chat animation slide fade new message appear soft patcher"));

        OPTIONS.add(new ConfigOption("smooth_chat_strength", "Smooth Chat Strength",
                "How long the slide takes. Higher is slower and easier to see.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.smoothChatStrength,
                v -> { AlpakaConfig.instance.smoothChatStrength = Math.round(v); AlpakaConfig.save(); },
                1.0f, 10.0f, v -> Math.round(v) * 60 + " ms",
                "smooth chat strength speed duration animation slow fast slider visible time ms"));

        OPTIONS.add(new ConfigOption("chat_open_animation", "Opening Animation",
                "Opening the chat unrolls it smoothly from the input line instead of showing it at once.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatOpenAnimationEnabled,
                v -> { AlpakaConfig.instance.chatOpenAnimationEnabled = v; AlpakaConfig.save(); },
                "chat opening open animation smooth slide unroll fade appear input"));

        OPTIONS.add(new ConfigOption("chat_smooth_scroll", "Smooth Chat Scrolling",
                "Makes scrolling the chat glide instead of jumping line by line.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatSmoothScrollEnabled,
                v -> { AlpakaConfig.instance.chatSmoothScrollEnabled = v; AlpakaConfig.save(); },
                "smooth chat scroll scrolling wheel glide peek animation"));

        OPTIONS.add(new ConfigOption("chat_scrollbar", "Better Scrollbar",
                "Gives the open chat a wider, rounded scrollbar that you can drag with the mouse.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatScrollbarEnabled,
                v -> { AlpakaConfig.instance.chatScrollbarEnabled = v; AlpakaConfig.save(); },
                "chat scrollbar scroll bar drag thumb wide rounded mouse grab"));

        OPTIONS.add(new ConfigOption("chat_movable", "Movable Chat",
                "Makes the chat a HUD element, so you can move and resize it in the HUD editor.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatMovable,
                v -> { AlpakaConfig.instance.chatMovable = v; AlpakaConfig.save(); },
                "chat move movable position hud editor drag place resize scale element"));

        OPTIONS.add(new ConfigOption("chat_search", "Chat Search",
                "Filters the chat to messages containing your text. Press Ctrl+F (Cmd+F on macOS) in the chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatSearchEnabled,
                v -> { AlpakaConfig.instance.chatSearchEnabled = v; AlpakaConfig.save(); },
                "chat search find filter ctrl f messages history lookup cmd mac macos"));

        OPTIONS.add(new ConfigOption("chat_copy", "Copy Chat",
                "Right-click a message in the open chat to copy its text, Shift+right-click to copy it with its color codes.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatCopyEnabled,
                v -> { AlpakaConfig.instance.chatCopyEnabled = v; AlpakaConfig.save(); },
                "chat copy clipboard message right click text paste shift color codes formatting"));

        OPTIONS.add(new ConfigOption("expand_chat_history", "Expand Chat History",
                "Keeps far more chat than vanilla's 100 lines, even after leaving a server.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.expandChatHistory,
                v -> { AlpakaConfig.instance.expandChatHistory = v; AlpakaConfig.save(); },
                "chat history limit scroll log keep disconnect rejoin server leave"));

        OPTIONS.add(new ConfigOption("chat_history_limit", "History Length",
                "How many lines the chat keeps.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.chatHistoryLimit,
                v -> { AlpakaConfig.instance.chatHistoryLimit = Math.round(v / 500.0f) * 500; AlpakaConfig.save(); },
                500.0f, 5000.0f, val -> String.format(Locale.ROOT, "%,d lines", Math.round(val / 500.0f) * 500),
                "chat history length limit lines messages count slider"));

        OPTIONS.add(new ConfigOption("compact_chat", "Compact Chat",
                "Stacks repeated messages into one line with (x2).",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.compactChatEnabled,
                v -> { AlpakaConfig.instance.compactChatEnabled = v; AlpakaConfig.save(); },
                "compact chat duplicate repeated messages merge counter spam x2 stack"));

        OPTIONS.add(new ConfigOption("compact_chat_window", "Compact Chat Window",
                "How long ago the last identical message may be to still stack.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.compactChatWindowSeconds,
                v -> { AlpakaConfig.instance.compactChatWindowSeconds = Math.round(v); AlpakaConfig.save(); },
                0.0f, 300.0f, AlpakaConfigRegistry::formatWindow,
                "compact chat window time seconds stack repeated between other messages delay gap"));

        OPTIONS.add(new ConfigOption("message_filter", "Message Filter",
                "Hides Hypixel's chat spam. Pick below which messages.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.messageFilterEnabled,
                v -> { AlpakaConfig.instance.messageFilterEnabled = v; AlpakaConfig.save(); },
                "message filter hide spam chat block remove clean hypixel skyblock"));

        OPTIONS.add(new ConfigOption("message_filter_types", "Hidden Messages",
                "Which messages the filter hides.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Empty Lines",
                                () -> AlpakaConfig.instance.messageFilterEmpty,
                                v -> { AlpakaConfig.instance.messageFilterEmpty = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Welcome To SkyBlock",
                                () -> AlpakaConfig.instance.messageFilterWelcome,
                                v -> { AlpakaConfig.instance.messageFilterWelcome = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Guild & Event EXP",
                                () -> AlpakaConfig.instance.messageFilterGuildExp,
                                v -> { AlpakaConfig.instance.messageFilterGuildExp = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Kill Combo",
                                () -> AlpakaConfig.instance.messageFilterKillCombo,
                                v -> { AlpakaConfig.instance.messageFilterKillCombo = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Profile Join",
                                () -> AlpakaConfig.instance.messageFilterProfileJoin,
                                v -> { AlpakaConfig.instance.messageFilterProfileJoin = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Reward Bundles",
                                () -> AlpakaConfig.instance.messageFilterRewardBundles,
                                v -> { AlpakaConfig.instance.messageFilterRewardBundles = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Autopet",
                                () -> AlpakaConfig.instance.messageFilterAutopet,
                                v -> { AlpakaConfig.instance.messageFilterAutopet = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Fire Sales",
                                () -> AlpakaConfig.instance.messageFilterFireSale,
                                v -> { AlpakaConfig.instance.messageFilterFireSale = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Watchdog",
                                () -> AlpakaConfig.instance.messageFilterWatchdog,
                                v -> { AlpakaConfig.instance.messageFilterWatchdog = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Stash Reminders",
                                () -> AlpakaConfig.instance.messageFilterStash,
                                v -> { AlpakaConfig.instance.messageFilterStash = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Sacks",
                                () -> AlpakaConfig.instance.messageFilterSacks,
                                v -> { AlpakaConfig.instance.messageFilterSacks = v; AlpakaConfig.save(); })
                ),
                "message filter hidden empty blank lines welcome guild gexp event exp kill combo grandma wolf profile id join reward bundle seasonal autopet pet rule fire sale community shop watchdog ban announcement stash stashed items materials pick up reminder sacks sack items added removed"));

        OPTIONS.add(new ConfigOption("message_filter_dungeon", "Dungeon Messages",
                "Which dungeon messages the filter hides during a Catacombs run.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Preparation",
                                () -> AlpakaConfig.instance.messageFilterDungeonPrepare,
                                v -> { AlpakaConfig.instance.messageFilterDungeonPrepare = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Class Buffs & Mort",
                                () -> AlpakaConfig.instance.messageFilterDungeonStart,
                                v -> { AlpakaConfig.instance.messageFilterDungeonStart = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Ambience",
                                () -> AlpakaConfig.instance.messageFilterDungeonAmbience,
                                v -> { AlpakaConfig.instance.messageFilterDungeonAmbience = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Pickups",
                                () -> AlpakaConfig.instance.messageFilterDungeonPickup,
                                v -> { AlpakaConfig.instance.messageFilterDungeonPickup = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Key Reminders",
                                () -> AlpakaConfig.instance.messageFilterDungeonReminder,
                                v -> { AlpakaConfig.instance.messageFilterDungeonReminder = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Blessings",
                                () -> AlpakaConfig.instance.messageFilterDungeonBlessing,
                                v -> { AlpakaConfig.instance.messageFilterDungeonBlessing = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Not Possible",
                                () -> AlpakaConfig.instance.messageFilterDungeonNotPossible,
                                v -> { AlpakaConfig.instance.messageFilterDungeonNotPossible = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Damage Taken",
                                () -> AlpakaConfig.instance.messageFilterDungeonDamage,
                                v -> { AlpakaConfig.instance.messageFilterDungeonDamage = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Abilities & Healing",
                                () -> AlpakaConfig.instance.messageFilterDungeonAbility,
                                v -> { AlpakaConfig.instance.messageFilterDungeonAbility = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Puzzles & Quiz",
                                () -> AlpakaConfig.instance.messageFilterDungeonPuzzle,
                                v -> { AlpakaConfig.instance.messageFilterDungeonPuzzle = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("End Of Run",
                                () -> AlpakaConfig.instance.messageFilterDungeonEnd,
                                v -> { AlpakaConfig.instance.messageFilterDungeonEnd = v; AlpakaConfig.save(); })
                ),
                "message filter dungeon dungeons catacombs mort ready countdown class buff ambience pickup superboom revive stone wither essence orb fairy key door blessing not possible damage ability abilities heal tether cooldown mana puzzle quiz oruo end run team bonus"));

        OPTIONS.add(new ConfigOption("message_filter_custom", "Custom Filters",
                "Your own filters: paste a copied message and pick whether it must match exactly, at the start, anywhere, or as a regex.",
                ConfigCategory.VISUALS,
                "Edit Filters",
                parent -> Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.MessageFilterScreen(parent)),
                "message filter custom own add copied message regex starts with contains equals exact hide block list"));

        OPTIONS.add(new ConfigOption("mention_notification", "Chat Mentions",
                "Slides in a notification when somebody says your name in chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.mentionNotificationEnabled,
                v -> { AlpakaConfig.instance.mentionNotificationEnabled = v; AlpakaConfig.save(); },
                "mention notification popup toast chat name ping alert notice"));

        OPTIONS.add(new ConfigOption("mention_ignore_server", "Only From Players",
                "Ignores your name in lines from NPCs, bosses and the server, so only a real player's message notifies you.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.mentionIgnoreServer,
                v -> { AlpakaConfig.instance.mentionIgnoreServer = v; AlpakaConfig.save(); },
                "mention npc server hypixel skyblock boss players only real ignore filter"));

        OPTIONS.add(new ConfigOption("chat_peek", "Chat Peek",
                "Shows the full chat, scrollable, while you hold the Chat Peek key.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatPeekEnabled,
                v -> { AlpakaConfig.instance.chatPeekEnabled = v; AlpakaConfig.save(); },
                "chat peek hold key read scroll history keybind open bigger larger peek chat unbound controls"));

        OPTIONS.add(ConfigOption.keybind("chat_peek_key", "Key",
                "The key to hold for Chat Peek. Unbound by default.",
                ConfigCategory.VISUALS,
                () -> net.alpaka.addons.features.chat.ChatPeekFeature.PEEK_KEY,
                "chat peek"));

        OPTIONS.add(new ConfigOption("chat_peek_in_menus", "Chat Peek in Menus",
                "Lets Chat Peek work while an inventory or menu is open too.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatPeekInMenus,
                v -> { AlpakaConfig.instance.chatPeekInMenus = v; AlpakaConfig.save(); },
                "chat peek menu inventory gui screen container open hold key foreground peek chat"));

        OPTIONS.add(new ConfigOption("better_screenshot_message", "Better Screenshots",
                "Adds a preview in the corner and Open, Copy and Delete buttons to every screenshot.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.betterScreenshotMessageEnabled,
                v -> { AlpakaConfig.instance.betterScreenshotMessageEnabled = v; AlpakaConfig.save(); },
                "better screenshots screenshot message notice open copy delete clipboard f2 buttons preview"));

        OPTIONS.add(new ConfigOption("screenshot_preview", "Corner Preview",
                "Shrinks the screenshot into the corner for a moment, like on a phone.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.screenshotPreviewEnabled,
                v -> { AlpakaConfig.instance.screenshotPreviewEnabled = v; AlpakaConfig.save(); },
                "screenshot preview thumbnail corner flash animation ios phone screen preview"));

        OPTIONS.add(new ConfigOption("screenshot_preview_seconds", "Preview Duration",
                "How long the preview stays in the corner.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.screenshotPreviewSeconds,
                v -> { AlpakaConfig.instance.screenshotPreviewSeconds = Math.round(v * 2.0f) / 2.0f; AlpakaConfig.save(); },
                1.0f, 10.0f,
                val -> String.format(java.util.Locale.ROOT, "%.1f s", val),
                "screenshot preview duration time seconds"));

        OPTIONS.add(new ConfigOption("screenshot_preview_size", "Preview Size",
                "How wide the preview is, as a share of the screen's width.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.screenshotPreviewSize,
                v -> { AlpakaConfig.instance.screenshotPreviewSize = (float) Math.round(v); AlpakaConfig.save(); },
                10.0f, 40.0f,
                val -> Math.round(val) + "%",
                "screenshot preview size width thumbnail"));

        OPTIONS.add(new ConfigOption("screenshot_preview_click", "Preview Click",
                "What clicking the preview does while a menu or the chat is open.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.screenshotPreviewClickAction,
                v -> { AlpakaConfig.instance.screenshotPreviewClickAction = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.chat.ScreenshotMessageFeature.PREVIEW_CLICK_NAMES.length - 1),
                val -> net.alpaka.addons.features.chat.ScreenshotMessageFeature.PREVIEW_CLICK_NAMES[Math.clamp(Math.round(val), 0, net.alpaka.addons.features.chat.ScreenshotMessageFeature.PREVIEW_CLICK_NAMES.length - 1)],
                "screenshot preview click mouse open copy folder action thumbnail").choices(net.alpaka.addons.features.chat.ScreenshotMessageFeature.PREVIEW_CLICK_NAMES));

        OPTIONS.add(new ConfigOption("screenshot_chat_buttons", "Chat Buttons",
                "Shows Open, Copy and Delete buttons on the screenshot message in chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.screenshotChatButtons,
                v -> { AlpakaConfig.instance.screenshotChatButtons = v; AlpakaConfig.save(); },
                "screenshot message notice open copy delete buttons chat line"));

        OPTIONS.add(new ConfigOption("screenshot_line_parts", "Line Parts",
                "Which parts the screenshot line shows.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("File Name",
                                () -> AlpakaConfig.instance.screenshotShowFileName,
                                v -> { AlpakaConfig.instance.screenshotShowFileName = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Open Button",
                                () -> AlpakaConfig.instance.screenshotShowOpen,
                                v -> { AlpakaConfig.instance.screenshotShowOpen = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Folder Button",
                                () -> AlpakaConfig.instance.screenshotShowFolder,
                                v -> { AlpakaConfig.instance.screenshotShowFolder = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Copy Button",
                                () -> AlpakaConfig.instance.screenshotShowCopy,
                                v -> { AlpakaConfig.instance.screenshotShowCopy = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Delete Button",
                                () -> AlpakaConfig.instance.screenshotShowDelete,
                                v -> { AlpakaConfig.instance.screenshotShowDelete = v; AlpakaConfig.save(); })
                ),
                "screenshot line parts file name open folder copy delete button show hide"));

        OPTIONS.add(new ConfigOption("auto_copy_screenshots", "Auto Copy Screenshots",
                "Copies every screenshot to the clipboard right away.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.autoCopyScreenshots,
                v -> { AlpakaConfig.instance.autoCopyScreenshots = v; AlpakaConfig.save(); },
                "screenshot auto copy clipboard automatic f2 paste"));

        OPTIONS.add(new ConfigOption("chat_tabs", "Chat Tabs",
                "Adds All, Party, Guild, Co-op, PMs and NPCs tabs above the chat input.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatTabsEnabled,
                v -> { AlpakaConfig.instance.chatTabsEnabled = v; AlpakaConfig.save(); },
                "chat tabs party guild pm private messages npc npcs filter channel switch"));

        OPTIONS.add(new ConfigOption("chat_tabs_shown", "Shown Chat Tabs",
                "Pick which tabs the row offers. All is always there.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Party",
                                () -> AlpakaConfig.instance.chatTabsShowParty,
                                v -> { AlpakaConfig.instance.chatTabsShowParty = v; AlpakaConfig.save(); ChatTabsFeature.onShownTabsChanged(); }),
                        new ConfigOption.ToggleEntry("Guild",
                                () -> AlpakaConfig.instance.chatTabsShowGuild,
                                v -> { AlpakaConfig.instance.chatTabsShowGuild = v; AlpakaConfig.save(); ChatTabsFeature.onShownTabsChanged(); }),
                        new ConfigOption.ToggleEntry("Co-op",
                                () -> AlpakaConfig.instance.chatTabsShowCoop,
                                v -> { AlpakaConfig.instance.chatTabsShowCoop = v; AlpakaConfig.save(); ChatTabsFeature.onShownTabsChanged(); }),
                        new ConfigOption.ToggleEntry("PMs",
                                () -> AlpakaConfig.instance.chatTabsShowPrivate,
                                v -> { AlpakaConfig.instance.chatTabsShowPrivate = v; AlpakaConfig.save(); ChatTabsFeature.onShownTabsChanged(); }),
                        new ConfigOption.ToggleEntry("NPCs",
                                () -> AlpakaConfig.instance.chatTabsShowNpc,
                                v -> { AlpakaConfig.instance.chatTabsShowNpc = v; AlpakaConfig.save(); ChatTabsFeature.onShownTabsChanged(); })
                ),
                "chat tabs shown visible hide pick choose select party guild coop co-op pm private messages npc npcs"));

        OPTIONS.add(new ConfigOption("chat_tabs_send_to_channel", "Send to Tab Channel",
                "Sends what you type on a channel tab to that channel.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatTabsSendToChannel,
                v -> { AlpakaConfig.instance.chatTabsSendToChannel = v; AlpakaConfig.save(); },
                "chat tabs send channel prefix party guild coop reply pc gc cc r messages typed"));

        OPTIONS.add(new ConfigOption("chat_tabs_tab_key", "Tabs While Peeking & Tab Key",
                "Shows the tabs while peeking. Tab on an empty line, or Ctrl+Tab, cycles them.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatTabsTabKey,
                v -> { AlpakaConfig.instance.chatTabsTabKey = v; AlpakaConfig.save(); },
                "chat tabs tab key cycle switch next peek player list tablist"));

        OPTIONS.add(new ConfigOption("chat_tabs_keep_after_close", "Keep Tab After Closing Chat",
                "Keeps the in-game chat filtered to the chosen tab. When off, it goes back to All.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatTabsKeepAfterClose,
                v -> { AlpakaConfig.instance.chatTabsKeepAfterClose = v; AlpakaConfig.save(); },
                "chat tabs keep remember filter hud close stay"));

        OPTIONS.add(new ConfigOption("chat_tabs_guild_only", "Guild Chat Only In Guild Tab",
                "Guild messages show on the Guild tab only, not on All.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatTabsGuildOnlyInGuildTab,
                v -> { AlpakaConfig.instance.chatTabsGuildOnlyInGuildTab = v; AlpakaConfig.save(); ChatTabsFeature.onFilterChanged(); },
                "chat tabs guild only separate hide all filter gc"));

        // Player model and inventory HUD lived on their own Custom HUD tab; they are overlays like
        // everything else here, and one tab fewer is one fewer place to look.

        OPTIONS.add(new ConfigOption("Player Model", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("player_model_enabled", "Player Model HUD",
                "Shows a small 3D model of your player on the HUD.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelEnabled,
                v -> { AlpakaConfig.instance.playerModelEnabled = v; AlpakaConfig.save(); },
                "player model hud avatar skin 3d display preview miniature displays"));

        OPTIONS.add(new ConfigOption("player_model_only_actions", "Only Show on Action",
                "Only shows the avatar while you sprint, fly or swing.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelOnlyActions,
                v -> { AlpakaConfig.instance.playerModelOnlyActions = v; AlpakaConfig.save(); },
                "only action sprint fly swing dynamic hide avatar"));

        OPTIONS.add(new ConfigOption("player_model_disable_movement", "Disable Movement Sway",
                "Keeps the avatar still instead of swaying as you walk or swim.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelDisableMovement,
                v -> { AlpakaConfig.instance.playerModelDisableMovement = v; AlpakaConfig.save(); },
                "disable movement sway steady static hud player model swim swimming"));

        OPTIONS.add(new ConfigOption("player_model_show_name_tag", "Show Name Tag",
                "Draws your name tag above the HUD avatar.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelShowNameTag,
                v -> { AlpakaConfig.instance.playerModelShowNameTag = v; AlpakaConfig.save(); },
                "player model hud avatar name tag label hide show"));

        OPTIONS.add(new ConfigOption("player_model_hide_armor", "Hide Armor on Model",
                "Hides your armor on the player model.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelHideArmor,
                v -> { AlpakaConfig.instance.playerModelHideArmor = v; AlpakaConfig.save(); },
                "player model hide armor helmet chestplate leggings boots overlay pieces rendering"));

        OPTIONS.add(new ConfigOption("player_model_show_in_guis", "Show in Menus",
                "Keeps the avatar on screen while a container or menu is open.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelShowInGuis,
                v -> { AlpakaConfig.instance.playerModelShowInGuis = v; AlpakaConfig.save(); },
                "player model show in guis menus screen open container inventory HUD show in guis / menus"));

        OPTIONS.add(new ConfigOption("Performance HUD", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("performance_hud_enabled", "Performance HUD",
                "Shows the server's day, your FPS, your ping and the server's TPS on the HUD.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.performanceHudEnabled,
                v -> { AlpakaConfig.instance.performanceHudEnabled = v; AlpakaConfig.save(); },
                "performance hud fps frames ping latency ms tps ticks server lag day world age"));

        OPTIONS.add(new ConfigOption("performance_hud_day", "Show Day",
                "The server's world age in days: green below 25, orange to 30, red above.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.performanceHudShowDay,
                v -> { AlpakaConfig.instance.performanceHudShowDay = v; AlpakaConfig.save(); },
                "performance hud day world age server days time skyblock"));

        OPTIONS.add(new ConfigOption("performance_hud_fps", "Show FPS",
                "Your frames per second, from the game's own counter.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.performanceHudShowFps,
                v -> { AlpakaConfig.instance.performanceHudShowFps = v; AlpakaConfig.save(); },
                "performance hud fps frames per second framerate"));

        OPTIONS.add(new ConfigOption("performance_hud_ping", "Show Ping",
                "Your round trip to the server. Measured with one small ping every 5 seconds while shown, none while F3 or another mod already measures it.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.performanceHudShowPing,
                v -> { AlpakaConfig.instance.performanceHudShowPing = v; AlpakaConfig.save(); },
                "performance hud ping latency ms connection delay"));

        OPTIONS.add(new ConfigOption("performance_hud_tps", "Show TPS",
                "The server's ticks per second, counted from packets it sends anyway. 20 is full speed.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.performanceHudShowTps,
                v -> { AlpakaConfig.instance.performanceHudShowTps = v; AlpakaConfig.save(); },
                "performance hud tps ticks per second server lag"));

        OPTIONS.add(new ConfigOption("performance_hud_animate", "Animate Changes",
                "When a number changes, its digits roll out and fade while the new ones roll in, like a flip clock.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.performanceHudAnimate,
                v -> { AlpakaConfig.instance.performanceHudAnimate = v; AlpakaConfig.save(); },
                "performance hud animate animation roll flip clock slide fade numbers digits"));

        OPTIONS.add(new ConfigOption("Dungeon Map", ConfigCategory.DUNGEON));
        OPTIONS.add(new ConfigOption("dungeon_map_enabled", "Enable Dungeon Map",
                "Shows the dungeon map during the clear and a map of the boss arena. Move and resize it in the HUD editor.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapEnabled,
                v -> { AlpakaConfig.instance.dungeonMapEnabled = v; AlpakaConfig.save(); },
                "dungeon map minimap catacombs overlay hud boss"));

        OPTIONS.add(new ConfigOption("dungeon_map_position", "Edit Map Position",
                "Move the map, and scroll over it to resize it.", ConfigCategory.DUNGEON,
                "Open HUD Editor", parent -> Minecraft.getInstance().gui.setScreen(
                        new net.alpaka.addons.client.hud.HudEditorScreen(parent)),
                "dungeon map hud editor move position size scale"));

        OPTIONS.add(new ConfigOption("dungeon_map_boss_mode", "In Boss Room",
                "Arena Map draws the loaded terrain around you, on your level. Saved Map keeps the clear map. Both shows them side by side.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapShowBoss ? (float) AlpakaConfig.instance.dungeonMapBossMode : 3.0f,
                v -> {
                    int mode = Math.round(v);
                    AlpakaConfig.instance.dungeonMapShowBoss = mode < 3;
                    if (mode < 3) AlpakaConfig.instance.dungeonMapBossMode = mode;
                    AlpakaConfig.save();
                },
                0.0f, 3.0f, val -> BOSS_MAP_MODES[Math.round(val)],
                "dungeon map boss room arena clear saved both hidden").choices(BOSS_MAP_MODES));

        OPTIONS.add(new ConfigOption("dungeon_map_boss_range", "Arena Map Range",
                "Width of the arena map in blocks. North stays at the top.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapBossRange,
                v -> { AlpakaConfig.instance.dungeonMapBossRange = v; AlpakaConfig.save(); },
                32.0f, 192.0f, val -> String.format(Locale.ROOT, "%.0f Blocks", val),
                "dungeon map boss arena range"));

        OPTIONS.add(new ConfigOption("dungeon_map_current_room", "Show Current Room",
                "Shows the room you are in above the map, with its secrets, and the boss's name in the boss room.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapCurrentRoom,
                v -> { AlpakaConfig.instance.dungeonMapCurrentRoom = v; AlpakaConfig.save(); },
                "dungeon map current room name title header"));

        OPTIONS.add(new ConfigOption("dungeon_map_room_names", "Show Room Names",
                "Writes each room's name on the map once it is recognized from the loaded world. Entrance, blood and fairy rooms are left out.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapRoomNames,
                v -> { AlpakaConfig.instance.dungeonMapRoomNames = v; AlpakaConfig.save(); },
                "dungeon map room names labels"));

        OPTIONS.add(new ConfigOption("dungeon_map_room_secrets", "Show Room Secrets",
                "Shows found and total secrets per room, like 3/5, colored by the room's checkmark, which they replace. The found count comes from the action bar once you have been in the room; until then it shows ?.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapRoomSecrets,
                v -> { AlpakaConfig.instance.dungeonMapRoomSecrets = v; AlpakaConfig.save(); },
                "dungeon map room secrets found count labels checkmarks"));

        OPTIONS.add(new ConfigOption("dungeon_map_room_label_scale", "Room Label Size",
                "Text size of room names and secret counts.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapRoomLabelScale,
                v -> { AlpakaConfig.instance.dungeonMapRoomLabelScale = v; AlpakaConfig.save(); },
                0.3f, 1.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "dungeon map room label size scale text"));

        OPTIONS.add(new ConfigOption("dungeon_map_players", "Show Players",
                "Draws your team on the map during the clear, and nearby players on your level in the boss arena.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapPlayers,
                v -> { AlpakaConfig.instance.dungeonMapPlayers = v; AlpakaConfig.save(); },
                "dungeon map players"));

        OPTIONS.add(new ConfigOption("dungeon_map_player_icons", "Player Icons",
                "Heads turn with the player. Upright Heads stay upright with an arrow circling them. Arrows draws arrows only. Teammates are framed in their class's color, like in the Leap Menu.", ConfigCategory.DUNGEON,
                () -> !AlpakaConfig.instance.dungeonMapPlayerHeads ? 2.0f : AlpakaConfig.instance.dungeonMapRotateHeads ? 0.0f : 1.0f,
                v -> {
                    int icons = Math.round(v);
                    AlpakaConfig.instance.dungeonMapPlayerHeads = icons < 2;
                    if (icons < 2) AlpakaConfig.instance.dungeonMapRotateHeads = icons == 0;
                    AlpakaConfig.save();
                },
                0.0f, 2.0f, val -> PLAYER_ICONS[Math.round(val)],
                "dungeon map player heads skin face arrows rotate upright direction").choices(PLAYER_ICONS));

        OPTIONS.add(new ConfigOption("dungeon_map_smooth_markers", "Smooth Movement",
                "Glides players between map updates and follows those in render distance live. Off shows Hypixel's markers exactly as they arrive.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapSmoothMarkers,
                v -> { AlpakaConfig.instance.dungeonMapSmoothMarkers = v; AlpakaConfig.save(); },
                "dungeon map smooth movement interpolation marker lag"));

        OPTIONS.add(new ConfigOption("dungeon_map_names", "Show Player Names",
                "Shows names under the players.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapNames,
                v -> { AlpakaConfig.instance.dungeonMapNames = v; AlpakaConfig.save(); },
                "dungeon map names"));

        OPTIONS.add(new ConfigOption("dungeon_map_marker_scale", "Player Icon Size",
                "Size of player heads and arrows without changing the map size.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapMarkerScale,
                v -> { AlpakaConfig.instance.dungeonMapMarkerScale = v; AlpakaConfig.save(); },
                0.5f, 2.5f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "dungeon map marker icon head arrow scale size"));

        OPTIONS.add(new ConfigOption("dungeon_map_colors", "Map Colors",
                "Alpaka redraws rooms, doors and the arena in its own colors, made to read well on any background. Hypixel keeps the map's and the blocks' own colors.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapCustomColors ? 0.0f : 1.0f,
                v -> { AlpakaConfig.instance.dungeonMapCustomColors = Math.round(v) == 0; AlpakaConfig.save(); },
                0.0f, 1.0f, val -> MAP_COLORS[Math.round(val)],
                "dungeon map colors palette rooms doors hypixel").choices(MAP_COLORS));

        OPTIONS.add(ConfigOption.color("dungeon_map_self_color", "Your Color",
                "Border of your head, and your arrow.", ConfigCategory.DUNGEON,
                c -> c.dungeonMapSelfColor,
                v -> { AlpakaConfig.instance.dungeonMapSelfColor = v; AlpakaConfig.save(); },
                "dungeon map color self marker head border arrow"));

        OPTIONS.add(ConfigOption.color("dungeon_map_text_color", "Text Color",
                "Color of the current room, player names and the labels of rooms nobody has cleared yet.", ConfigCategory.DUNGEON,
                c -> c.dungeonMapTextColor,
                v -> { AlpakaConfig.instance.dungeonMapTextColor = v; AlpakaConfig.save(); },
                "dungeon map color text"));

        OPTIONS.add(new ConfigOption("dungeon_map_background", "Show Background",
                "A panel behind the map. Off leaves it fully transparent.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapBackground,
                v -> { AlpakaConfig.instance.dungeonMapBackground = v; AlpakaConfig.save(); },
                "dungeon map background"));

        OPTIONS.add(ConfigOption.color("dungeon_map_background_color", "Background Color",
                "Tint of the background panel; its alpha sets how see-through it is.", ConfigCategory.DUNGEON,
                c -> c.dungeonMapBackgroundTint,
                v -> { AlpakaConfig.instance.dungeonMapBackgroundTint = v; AlpakaConfig.save(); },
                "dungeon map color background opacity"));

        OPTIONS.add(new ConfigOption("dungeon_map_frame", "Show Frame",
                "Draws a two-color gradient frame, like the Inventory HUD.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonMapFrame,
                v -> { AlpakaConfig.instance.dungeonMapFrame = v; AlpakaConfig.save(); },
                "dungeon map frame border"));

        OPTIONS.add(ConfigOption.color("dungeon_map_frame_start", "Frame Start Color",
                "Top-left color of the frame.", ConfigCategory.DUNGEON,
                c -> c.dungeonMapFrameStart,
                v -> { AlpakaConfig.instance.dungeonMapFrameStart = v; AlpakaConfig.save(); },
                "dungeon map color frame start"));

        OPTIONS.add(ConfigOption.color("dungeon_map_frame_end", "Frame End Color",
                "Bottom-right color of the frame.", ConfigCategory.DUNGEON,
                c -> c.dungeonMapFrameEnd,
                v -> { AlpakaConfig.instance.dungeonMapFrameEnd = v; AlpakaConfig.save(); },
                "dungeon map color frame end"));

        OPTIONS.add(new ConfigOption("Door Highlight", ConfigCategory.DUNGEON));
        OPTIONS.add(new ConfigOption("dungeon_door_highlight", "Door Highlight",
                "Marks wither and blood doors during the clear: red while nobody has the key, green once your team has one. Only where you can see the door.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.dungeonDoorHighlight,
                v -> { AlpakaConfig.instance.dungeonDoorHighlight = v; AlpakaConfig.save(); },
                "dungeon door highlight wither blood key door esp"));

        OPTIONS.add(ConfigOption.color("dungeon_door_locked_color", "Locked Door Color",
                "Color of a door nobody has the key for. Its alpha sets how strongly the door is filled.", ConfigCategory.DUNGEON,
                c -> c.dungeonDoorLockedColor,
                v -> { AlpakaConfig.instance.dungeonDoorLockedColor = v; AlpakaConfig.save(); },
                "dungeon door highlight color locked"));

        OPTIONS.add(ConfigOption.color("dungeon_door_openable_color", "Openable Door Color",
                "Color of a door your team has the key for.", ConfigCategory.DUNGEON,
                c -> c.dungeonDoorOpenableColor,
                v -> { AlpakaConfig.instance.dungeonDoorOpenableColor = v; AlpakaConfig.save(); },
                "dungeon door highlight color openable key"));

        OPTIONS.add(new ConfigOption("Leap Menu", ConfigCategory.DUNGEON));
        OPTIONS.add(new ConfigOption("leap_menu_enabled", "Enable Leap Menu",
                "Shows the Spirit Leap menu as one big button per teammate, with their head, name and class, colored by class. One click leaps.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.leapMenuEnabled,
                v -> { AlpakaConfig.instance.leapMenuEnabled = v; AlpakaConfig.save(); },
                "leap menu spirit leap infinileap teleport teammate custom gui class"));

        OPTIONS.add(new ConfigOption("leap_menu_show_map", "Show Dungeon Map",
                "Shows the dungeon map next to the buttons during the clear. Click a player on the map to leap to them.", ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.leapMenuShowMap,
                v -> { AlpakaConfig.instance.leapMenuShowMap = v; AlpakaConfig.save(); },
                "leap menu map dungeon map click player"));

        OPTIONS.add(new ConfigOption("Starred Mobs Highlight", ConfigCategory.DUNGEON));

        OPTIONS.add(new ConfigOption("starred_mobs_highlight", "Starred Mobs Highlight",
                "Highlights the starred mobs a room needs killed, and the minibosses, during the dungeon clear.",
                ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.starredMobsHighlightEnabled,
                v -> { AlpakaConfig.instance.starredMobsHighlightEnabled = v; AlpakaConfig.save(); },
                "dungeon starred star mobs highlight glow outline hitbox box overlay esp clear miniboss catacombs"));

        OPTIONS.add(new ConfigOption("starred_mobs_highlight_style", "Highlight Style",
                "Outline glows around the mob, Hitbox draws a box around it, Overlay tints its model.",
                ConfigCategory.DUNGEON,
                () -> (float) AlpakaConfig.instance.starredMobsHighlightStyle,
                v -> { AlpakaConfig.instance.starredMobsHighlightStyle = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.utils.EntityHighlight.STYLE_NAMES.length - 1),
                val -> net.alpaka.addons.utils.EntityHighlight.STYLE_NAMES[Math.round(val)],
                "dungeon starred mobs highlight style mode outline glow hitbox box overlay tint").choices(net.alpaka.addons.utils.EntityHighlight.STYLE_NAMES));

        OPTIONS.add(ConfigOption.color("starred_mobs_highlight_color", "Highlight Color",
                "Color of the starred mob highlight.",
                ConfigCategory.DUNGEON,
                c -> c.starredMobsHighlightColor,
                v -> { AlpakaConfig.instance.starredMobsHighlightColor = v; AlpakaConfig.save(); },
                "dungeon starred mobs highlight color colour picker"));

        OPTIONS.add(new ConfigOption("starred_mobs_hide_names", "Hide Non-Starred Name Tags",
                "Hides the name tags of mobs without a star during the clear. Minibosses and the Mimic keep theirs.",
                ConfigCategory.DUNGEON,
                () -> AlpakaConfig.instance.starredMobsHideNames,
                v -> { AlpakaConfig.instance.starredMobsHideNames = v; AlpakaConfig.save(); },
                "dungeon hide non starred mobs name tags nametags names health clutter clear"));

        OPTIONS.add(new ConfigOption("Inventory HUD", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("inventory_hud_enabled", "Inventory HUD",
                "Shows your 27 inventory slots on screen without opening the inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudEnabled,
                v -> { AlpakaConfig.instance.inventoryHudEnabled = v; AlpakaConfig.save(); },
                "inventory hud overlay slots items show always screen backpack"));

        OPTIONS.add(new ConfigOption("inventory_hud_attach", "Attach Above Hotbar",
                "Centers the inventory just above the hotbar. When off, it stays where you put it in the HUD Editor.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudAttachToHotbar,
                v -> { AlpakaConfig.instance.inventoryHudAttachToHotbar = v; AlpakaConfig.save(); },
                "inventory hud hotbar attach above merge dock position free center centre off: free position"));

        OPTIONS.add(new ConfigOption("inventory_hud_visibility", "Visibility",
                "When the inventory shows: always, briefly when an item is picked up or dropped, or only when you press the Inventory HUD key.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.inventoryHudVisibility,
                v -> { AlpakaConfig.instance.inventoryHudVisibility = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (InventoryHudVisibility.NAMES.length - 1),
                val -> InventoryHudVisibility.NAMES[Math.round(val)],
                "inventory hud always visible permanent show on pickup drop item change peek temporary keybind key press toggle hidden unbound controls mode")
                .choices(InventoryHudVisibility.NAMES));

        OPTIONS.add(ConfigOption.keybind("inventory_hud_key", "Key",
                "Shows or hides the inventory, whatever Visibility is set to. Unbound by default.",
                ConfigCategory.VISUALS,
                () -> net.alpaka.addons.features.inventoryhud.InventoryHudFeature.TOGGLE_KEY,
                "inventory hud toggle show hide"));

        OPTIONS.add(new ConfigOption("inventory_hud_vanilla_texture", "Chest Style",
                "Draws the panel as a three-row chest, using your resource pack.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudVanillaTexture,
                v -> { AlpakaConfig.instance.inventoryHudVanillaTexture = v; AlpakaConfig.save(); },
                "inventory texture resource pack vanilla slots real look skin"));

        OPTIONS.add(new ConfigOption("inventory_hud_frame", "Show Frame",
                "Draws the gradient frame around the flat panel.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudFrame,
                v -> { AlpakaConfig.instance.inventoryHudFrame = v; AlpakaConfig.save(); },
                "inventory hud frame border outline gradient rahmen hide remove flat panel"));

        OPTIONS.add(ConfigOption.color("inventory_hud_frame_start_color", "Frame Start Color",
                "The frame gradient's start color.",
                ConfigCategory.VISUALS,
                c -> c.inventoryHudFrameStart,
                v -> { AlpakaConfig.instance.inventoryHudFrameStart = v; AlpakaConfig.save(); },
                "inventory hud frame border outline gradient color start first picker flat panel's"));

        OPTIONS.add(ConfigOption.color("inventory_hud_frame_end_color", "Frame End Color",
                "The frame gradient's end color.",
                ConfigCategory.VISUALS,
                c -> c.inventoryHudFrameEnd,
                v -> { AlpakaConfig.instance.inventoryHudFrameEnd = v; AlpakaConfig.save(); },
                "inventory hud frame border outline gradient color end second picker flat panel's"));

        OPTIONS.add(new ConfigOption("inventory_hud_bg_opacity", "Background Opacity",
                "How dark the backdrop behind the slots is. 0% leaves only the frame.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudBackgroundOpacity,
                v -> { AlpakaConfig.instance.inventoryHudBackgroundOpacity = v; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Off (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "inventory hud background opacity transparent blur tint backdrop strength tint off"));


        // --- 2. ITEM VIEWMODEL ---

        OPTIONS.add(new ConfigOption("item_size_feature", "Viewmodel Modifiers",
                "Adjusts the size, position and rotation of your hand and held item.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSizeFeatureEnabled,
                v -> { AlpakaConfig.instance.itemSizeFeatureEnabled = v; AlpakaConfig.save(); },
                "viewmodel item size scale enable custom hand"));

        OPTIONS.add(new ConfigOption("Viewmodel Adjustments", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("item_scale", "Item Scale",
                "How big the held item is drawn. 1.00x is vanilla.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemScale,
                v -> { AlpakaConfig.instance.itemScale = v; AlpakaConfig.save(); },
                0.2f, 2.5f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "scale size item hand viewmodel big small item scale (size) multiplier"));

        OPTIONS.add(new ConfigOption("item_x_offset", "X Offset (Left/Right)",
                "Moves the hand left or right.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemXOffset,
                v -> { AlpakaConfig.instance.itemXOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format(Locale.ROOT, "%.2f", val),
                "position x offset left right hand horizontal"));

        OPTIONS.add(new ConfigOption("item_y_offset", "Y Offset (Up/Down)",
                "Moves the hand up or down.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemYOffset,
                v -> { AlpakaConfig.instance.itemYOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format(Locale.ROOT, "%.2f", val),
                "position y offset up down height hand vertical"));

        OPTIONS.add(new ConfigOption("item_z_offset", "Z Offset (Forward/Back)",
                "Moves the hand forward or back.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemZOffset,
                v -> { AlpakaConfig.instance.itemZOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format(Locale.ROOT, "%.2f", val),
                "position z offset forward back depth hand backward"));

        OPTIONS.add(new ConfigOption("item_rotation_x", "Rotation Pitch (X)",
                "Tilts the hand forward or back.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationX,
                v -> { AlpakaConfig.instance.itemRotationX = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "rotation pitch x angle tilt backward"));

        OPTIONS.add(new ConfigOption("item_rotation_y", "Rotation Yaw (Y)",
                "Turns the hand left or right.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationY,
                v -> { AlpakaConfig.instance.itemRotationY = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "rotation yaw y angle turn side to side"));

        OPTIONS.add(new ConfigOption("item_rotation_z", "Rotation Roll (Z)",
                "Rolls the hand to either side.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationZ,
                v -> { AlpakaConfig.instance.itemRotationZ = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "rotation roll z angle side tilt side to side"));

        OPTIONS.add(new ConfigOption("item_swing_speed", "Swing Speed",
                "How fast the swing animation plays. 1.00x is vanilla.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingSpeed,
                v -> { AlpakaConfig.instance.itemSwingSpeed = v; AlpakaConfig.save(); },
                0.1f, 1.5f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "swing speed attack animation fast slow multiplier"));

        OPTIONS.add(new ConfigOption("item_sway_disabled", "Disable Hand Sway",
                "Stops the hand from swaying when you turn the camera.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwayDisabled,
                v -> { AlpakaConfig.instance.itemSwayDisabled = v; AlpakaConfig.save(); },
                "sway hand motion movement camera steady disables"));

        OPTIONS.add(new ConfigOption("item_lighting_disabled", "Disable Item Lighting",
                "Removes the directional shading on the held item so it stays evenly lit while moving or turning.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemLightingDisabled,
                v -> { AlpakaConfig.instance.itemLightingDisabled = v; AlpakaConfig.save(); },
                "lighting light shading shadow flat unlit held item hand flicker"));

        OPTIONS.add(new ConfigOption("item_no_equip", "Disable Re-equip Animation",
                "Skips the hand lowering and rising again when you switch slots.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemNoEquipEnabled,
                v -> { AlpakaConfig.instance.itemNoEquipEnabled = v; AlpakaConfig.save(); },
                "no equip reequip animation slot switch instant"));

        OPTIONS.add(new ConfigOption("item_ignore_empty_hand", "Ignore Empty Hand",
                "Applies the viewmodel changes only while you hold an item.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemIgnoreEmptyHandEnabled,
                v -> { AlpakaConfig.instance.itemIgnoreEmptyHandEnabled = v; AlpakaConfig.save(); },
                "ignore empty hand fist bare item only holding"));

        OPTIONS.add(new ConfigOption("Swing Customizations", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("swing_test", "Test Swing",
                "Swings the item in your hand once, only on your screen: no hit, nothing sent to the server.",
                ConfigCategory.VIEWMODEL,
                "Test Swing",
                parent -> net.alpaka.addons.features.viewmodel.SwingPreview.swing(),
                "swing test preview try animation hand item"));

        OPTIONS.add(new ConfigOption("swing_drift_x", "Swing Drift X",
                "How far the item drifts sideways during a swing.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftX,
                v -> { AlpakaConfig.instance.swingDriftX = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format(Locale.ROOT, "%.1f", val),
                "swing drift x lateral motion displacement"));

        OPTIONS.add(new ConfigOption("swing_drift_y", "Swing Drift Y",
                "How far the item drifts up or down during a swing.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftY,
                v -> { AlpakaConfig.instance.swingDriftY = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format(Locale.ROOT, "%.1f", val),
                "swing drift y vertical motion displacement"));

        OPTIONS.add(new ConfigOption("swing_drift_z", "Swing Drift Z",
                "How far the item drifts forward or back during a swing.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftZ,
                v -> { AlpakaConfig.instance.swingDriftZ = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format(Locale.ROOT, "%.1f", val),
                "swing drift z depth motion displacement"));

        OPTIONS.add(new ConfigOption("swing_arc_x", "Swing Arc Pitch",
                "Extra tilt forward or back during a swing.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcX,
                v -> { AlpakaConfig.instance.swingArcX = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "swing arc pitch angle rot additional rotation axis"));

        OPTIONS.add(new ConfigOption("swing_arc_y", "Swing Arc Yaw",
                "Extra turn left or right during a swing.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcY,
                v -> { AlpakaConfig.instance.swingArcY = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "swing arc yaw angle rot additional rotation axis"));

        OPTIONS.add(new ConfigOption("swing_arc_z", "Swing Arc Roll",
                "Extra roll to either side during a swing.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcZ,
                v -> { AlpakaConfig.instance.swingArcZ = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "swing arc roll angle rot additional rotation axis"));

        OPTIONS.add(new ConfigOption("item_swing_trans_disable", "Disable Swing Translation",
                "Keeps the item from shifting position during a swing.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingTranslationDisabled,
                v -> { AlpakaConfig.instance.itemSwingTranslationDisabled = v; AlpakaConfig.save(); },
                "disable swing translation movement offset shift disables position translation swings"));

        OPTIONS.add(new ConfigOption("item_swing_always_finish", "Always Finish Swing",
                "Finishes every swing and chains held attacks without a pause.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingAlwaysFinishEnabled,
                v -> { AlpakaConfig.instance.itemSwingAlwaysFinishEnabled = v; AlpakaConfig.save(); },
                "always finish swing complete attack animation"));

        OPTIONS.add(new ConfigOption("Starlight", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("item_starlight", "Starlight Effect",
                "Draws a drifting, twinkling galaxy over the held item, on every item or in place of the enchantment glint.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemStarlightEnabled,
                v -> { AlpakaConfig.instance.itemStarlightEnabled = v; AlpakaConfig.save(); },
                "starlight galaxy stars space nebula glint shimmer overlay effect held item cosmic"));

        OPTIONS.add(new ConfigOption("item_starlight_mode", "Shown On",
                "Every held item, or only items with an enchantment glint, in place of the glint.",
                ConfigCategory.VIEWMODEL,
                () -> (float) AlpakaConfig.instance.itemStarlightMode,
                v -> { AlpakaConfig.instance.itemStarlightMode = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.viewmodel.HandItemStarlightFeature.MODE_NAMES.length - 1),
                val -> net.alpaka.addons.features.viewmodel.HandItemStarlightFeature.MODE_NAMES[Math.round(val)],
                "starlight mode glint enchant replace enchanted only every item replace enchant glint glinting")
                .choices(net.alpaka.addons.features.viewmodel.HandItemStarlightFeature.MODE_NAMES));

        OPTIONS.add(ConfigOption.color("item_starlight_color", "Starlight Color",
                "The nebula's color. The stars are tinged with it.",
                ConfigCategory.VIEWMODEL,
                c -> c.itemStarlightColor,
                v -> { AlpakaConfig.instance.itemStarlightColor = v; AlpakaConfig.save(); },
                "starlight galaxy color nebula tint purple"));

        OPTIONS.add(new ConfigOption("item_starlight_strength", "Starlight Strength",
                "How strongly the galaxy covers the item.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemStarlightStrength,
                v -> { AlpakaConfig.instance.itemStarlightStrength = Math.round(v * 100.0f) / 100.0f; AlpakaConfig.save(); },
                0.1f, 1.0f, val -> String.format(Locale.ROOT, "%.0f%%", val * 100.0f),
                "starlight galaxy strength opacity intensity"));

        OPTIONS.add(new ConfigOption("Swing Motion Blur", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("item_motion_blur", "Swing Motion Blur",
                "Leaves a fading trail behind the held item while it swings.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurEnabled,
                v -> { AlpakaConfig.instance.itemMotionBlurEnabled = v; AlpakaConfig.save(); },
                "motion blur trail ghost smear swing smooth afterimage fight swinging"));

        OPTIONS.add(new ConfigOption("item_motion_blur_length", "Blur Length",
                "How long the trail lingers behind the item.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurLength,
                v -> { AlpakaConfig.instance.itemMotionBlurLength = (float) Math.round(v); AlpakaConfig.save(); },
                ItemMotionBlurFeature.MIN_LENGTH_MS, ItemMotionBlurFeature.MAX_LENGTH_MS,
                val -> String.format(Locale.ROOT, "%.0f ms", val),
                "motion blur trail length duration time long short far reaches"));

        OPTIONS.add(new ConfigOption("item_motion_blur_opacity", "Blur Opacity",
                "How strongly the trail is drawn.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurOpacity,
                v -> { AlpakaConfig.instance.itemMotionBlurOpacity = Math.round(v * 100.0f) / 100.0f; AlpakaConfig.save(); },
                0.05f, 1.0f, val -> String.format(Locale.ROOT, "%.0f%%", val * 100.0f),
                "motion blur trail opacity alpha strength transparency intensity"));

        OPTIONS.add(new ConfigOption("item_motion_blur_steps", "Blur Steps",
                "How many ghost copies make up the trail.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurSteps,
                v -> { AlpakaConfig.instance.itemMotionBlurSteps = (float) Math.round(v); AlpakaConfig.save(); },
                (float) ItemMotionBlurFeature.MIN_STEPS, (float) ItemMotionBlurFeature.MAX_STEPS,
                val -> String.format(Locale.ROOT, "%.0f", val),
                "motion blur trail steps copies ghosts quality samples number"));

        OPTIONS.add(new ConfigOption("item_viewmodel_reset", "Reset Viewmodel Settings",
                "Resets every viewmodel and swing value, Starlight and Swing Motion Blur included. Presets are kept.",
                ConfigCategory.VIEWMODEL,
                "Reset Viewmodel Settings",
                parent -> AlpakaConfig.instance.resetItemViewmodel(),
                "reset viewmodel default defaults revert restore clear hand item size offset rotation swing starlight motion blur")
                .confirm("Reset Viewmodel Settings",
                        "Puts every viewmodel and swing value back to its default and turns Starlight and Swing Motion Blur off. "
                                + "Viewmodel Modifiers stays as it is, and saved presets are kept. This cannot be undone.",
                        "Reset", true));

        OPTIONS.add(new ConfigOption("Viewmodel Presets", ConfigCategory.VIEWMODEL));

        OPTIONS.add(ConfigOption.presets("viewmodel_presets", "Presets", ConfigCategory.VIEWMODEL,
                "preset presets load save delete name new slot viewmodel hand size offset swing"));


        // --- 3. BLOCK OVERLAY ---

        OPTIONS.add(new ConfigOption("block_overlay_enabled", "Block Overlay",
                "Replaces the highlight on the block you aim at with a custom one.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOverlayEnabled,
                v -> { AlpakaConfig.instance.blockOverlayEnabled = v; AlpakaConfig.save(); },
                "block overlay outline highlight render custom enable"));

        OPTIONS.add(new ConfigOption("Outline", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_outline_enabled", "Outline",
                "Draws an outline around the block you aim at.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOutlineEnabled,
                v -> { AlpakaConfig.instance.blockOutlineEnabled = v; AlpakaConfig.save(); },
                "block outline border edge line draw block outline targeted borders"));

        OPTIONS.add(new ConfigOption("block_outline_thickness", "Outline Thickness",
                "How thick the outline is.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOutlineThickness,
                v -> { AlpakaConfig.instance.blockOutlineThickness = v; AlpakaConfig.save(); },
                0.5f, 10.0f, val -> String.format(Locale.ROOT, "%.1f px", val),
                "outline thickness line width border size frame"));

        OPTIONS.add(ConfigOption.color("block_outline_color", "Outline Color",
                "The outline color.",
                ConfigCategory.BLOCK_OVERLAY,
                c -> c.blockOutlineColor,
                v -> { AlpakaConfig.instance.blockOutlineColor = v; AlpakaConfig.save(); },
                "block outline color picker rgb alpha cyan red blue"));

        OPTIONS.add(new ConfigOption("Fill", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_fill_enabled", "Fill",
                "Fills the faces of the block you aim at with a translucent color.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockFillEnabled,
                v -> { AlpakaConfig.instance.blockFillEnabled = v; AlpakaConfig.save(); },
                "block fill solid transparent face shading color block fill"));

        OPTIONS.add(ConfigOption.color("block_fill_color", "Fill Color",
                "The fill color, transparency included.",
                ConfigCategory.BLOCK_OVERLAY,
                c -> c.blockFillColor,
                v -> { AlpakaConfig.instance.blockFillColor = v; AlpakaConfig.save(); },
                "block fill color picker rgb alpha transparency tint"));

        OPTIONS.add(new ConfigOption("Animation", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_fade_in", "Smooth Fade-In",
                "Fades the overlay in when you aim at a new block.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockFadeInEnabled,
                v -> { AlpakaConfig.instance.blockFadeInEnabled = v; AlpakaConfig.save(); },
                "block overlay smooth fade in transition animation visuals"));

        OPTIONS.add(new ConfigOption("block_fade_in_duration", "Fade-In Duration",
                "How long the overlay takes to fade in.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> (float) AlpakaConfig.instance.blockFadeInDurationMs,
                v -> { AlpakaConfig.instance.blockFadeInDurationMs = Math.round(v); AlpakaConfig.save(); },
                50.0f, 1000.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "block fade in duration speed time ms transition overlay fade in duration"));

        OPTIONS.add(new ConfigOption("block_chroma_enabled", "Chroma Colors",
                "Cycles the overlay through the colors of the rainbow.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockChromaEnabled,
                v -> { AlpakaConfig.instance.blockChromaEnabled = v; AlpakaConfig.save(); },
                "chroma rgb rainbow color cycle gradient block chroma rgb effect"));

        OPTIONS.add(new ConfigOption("block_chroma_speed", "Chroma Speed",
                "How fast the rainbow cycles.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockChromaSpeed,
                v -> { AlpakaConfig.instance.blockChromaSpeed = v; AlpakaConfig.save(); },
                0.1f, 2.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "chroma speed rainbow velocity rgb cycle"));

        OPTIONS.add(new ConfigOption("Filters", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_ignore_depth", "Show Through Walls",
                "Draws the outline of the block you aim at even when something is in front of it.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockIgnoreDepth,
                v -> { AlpakaConfig.instance.blockIgnoreDepth = v; AlpakaConfig.save(); },
                "ignore depth on top over walls see through blocks outline draw on top over anything only that block"));

        OPTIONS.add(new ConfigOption("block_ignore_plants", "Ignore Plants",
                "Hides the overlay when you aim at plants like grass, flowers or crops.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockIgnorePlants,
                v -> { AlpakaConfig.instance.blockIgnorePlants = v; AlpakaConfig.save(); },
                "ignore plants foliage grass flowers crops outline disable overlay disables block highlighting targeting"));

        OPTIONS.add(new ConfigOption("block_hide_on_etherwarp", "Hide While Aiming Etherwarp",
                "Hides the overlay while sneaking with an Etherwarp item.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockHideOnEtherwarp,
                v -> { AlpakaConfig.instance.blockHideOnEtherwarp = v; AlpakaConfig.save(); },
                "etherwarp ether transmission conduit aspect void end teleport sneak hide overlay skyblock"));



        // --- 5. SKYBLOCK ---

        registerDungeonOptions();

        OPTIONS.add(new ConfigOption("Etherwarp Overlay", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("etherwarp_overlay", "Etherwarp Overlay",
                "Marks the block you will land on while sneaking with an Etherwarp item.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayEnabled,
                v -> { AlpakaConfig.instance.etherwarpOverlayEnabled = v; AlpakaConfig.save(); },
                "etherwarp ether transmission conduit aspect void teleport target block highlight overlay guess"));

        OPTIONS.add(ConfigOption.color("etherwarp_overlay_color", "Target Color",
                "The box color when the warp will work.",
                ConfigCategory.SKYBLOCK,
                c -> c.etherwarpOverlayColor,
                v -> { AlpakaConfig.instance.etherwarpOverlayColor = v; AlpakaConfig.save(); },
                "etherwarp color target box gold"));

        OPTIONS.add(new ConfigOption("etherwarp_show_fail", "Show Blocked Targets",
                "Marks a target with no room to stand too, in the blocked color.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayShowFail,
                v -> { AlpakaConfig.instance.etherwarpOverlayShowFail = v; AlpakaConfig.save(); },
                "etherwarp fail blocked invalid red no space fail color"));

        OPTIONS.add(ConfigOption.color("etherwarp_fail_color", "Blocked Color",
                "The box color when the warp will be refused.",
                ConfigCategory.SKYBLOCK,
                c -> c.etherwarpOverlayFailColor,
                v -> { AlpakaConfig.instance.etherwarpOverlayFailColor = v; AlpakaConfig.save(); },
                "etherwarp fail color red blocked fail color"));

        OPTIONS.add(new ConfigOption("etherwarp_fill", "Fill Target Block",
                "Fills the box translucently instead of drawing only its edges.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayFill,
                v -> { AlpakaConfig.instance.etherwarpOverlayFill = v; AlpakaConfig.save(); },
                "etherwarp fill filled box solid"));

        OPTIONS.add(new ConfigOption("etherwarp_thickness", "Outline Thickness",
                "How thick the target box's edges are.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayThickness,
                v -> { AlpakaConfig.instance.etherwarpOverlayThickness = v; AlpakaConfig.save(); },
                0.5f, 6.0f, val -> String.format(Locale.ROOT, "%.1f px", val),
                "etherwarp outline thickness width line edge px pixels"));

        OPTIONS.add(new ConfigOption("etherwarp_through_walls", "Show Through Walls",
                "Draws the box even when terrain is in front of it.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayThroughWalls,
                v -> { AlpakaConfig.instance.etherwarpOverlayThroughWalls = v; AlpakaConfig.save(); },
                "etherwarp through walls depth on top see"));

        OPTIONS.add(new ConfigOption("etherwarp_line", "Line to Target",
                "Draws a line from your feet to the target block.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpLineEnabled,
                v -> { AlpakaConfig.instance.etherwarpLineEnabled = v; AlpakaConfig.save(); },
                "etherwarp line tracer path feet target"));

        OPTIONS.add(new ConfigOption("etherwarp_line_width", "Line Thickness",
                "How thick the line to the target is.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpLineWidth,
                v -> { AlpakaConfig.instance.etherwarpLineWidth = v; AlpakaConfig.save(); },
                1.0f, 12.0f, val -> String.format(Locale.ROOT, "%.0f px", val),
                "etherwarp line thickness width thin thick px pixels"));

        OPTIONS.add(new ConfigOption("etherwarp_sound", "Custom Warp Sound",
                "Plays the mod's own sound instead of Hypixel's when you warp.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpSoundEnabled,
                v -> { AlpakaConfig.instance.etherwarpSoundEnabled = v; AlpakaConfig.save(); },
                "etherwarp sound custom teleport audio warp"));

        OPTIONS.add(new ConfigOption("etherwarp_sound_type", "Warp Sound",
                "Which sound plays. Whoosh sweeps down, Pling and Chime ring, Thud is dull and short, Pop is a blip. Plays a preview when changed.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.etherwarpSoundIndex,
                v -> {
                    int index = Math.round(v);
                    if (index != AlpakaConfig.instance.etherwarpSoundIndex) {
                        AlpakaConfig.instance.etherwarpSoundIndex = index;
                        AlpakaConfig.save();
                        EtherwarpOverlayFeature.previewWarpSound();
                    }
                },
                0.0f, (float) (EtherwarpOverlayFeature.SOUND_NAMES.length - 1),
                val -> EtherwarpOverlayFeature.SOUND_NAMES[Math.round(val)],
                "etherwarp sound type whoosh pling thud chime pop bell dull").choices(EtherwarpOverlayFeature.SOUND_NAMES));

        OPTIONS.add(new ConfigOption("etherwarp_sound_volume", "Warp Sound Volume",
                "How loud the custom warp sound plays.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpSoundVolume,
                v -> { AlpakaConfig.instance.etherwarpSoundVolume = v; AlpakaConfig.save(); },
                0.1f, 2.0f, val -> String.format(Locale.ROOT, "%.0f%%", val * 100.0f),
                "etherwarp sound volume loud quiet"));

        OPTIONS.add(new ConfigOption("Pest Finder", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("pest_finder", "Pest Finder",
                "Helps find pests on the Garden: left-click a vacuum for a waypoint where its trail leads, and pests nearby glow.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.pestFinderEnabled,
                v -> { AlpakaConfig.instance.pestFinderEnabled = v; AlpakaConfig.save(); },
                "pest finder garden vacuum tracker waypoint guess line tracer locate find pests farming"));

        OPTIONS.add(ConfigOption.keybind("pest_finder_teleport_key", "Key",
                "Teleports you with /plottp to the plot the waypoint points at, when it marks a plot's center. Open Configure Plots at your desk once so renamed plots are known. Unbound by default.",
                ConfigCategory.SKYBLOCK,
                () -> net.alpaka.addons.features.garden.PestFinder.TELEPORT_KEY,
                "pest finder teleport plottp plot warp"));

        OPTIONS.add(ConfigOption.color("pest_finder_guess_color", "Waypoint Color",
                "Color of the waypoint where the vacuum's trail leads. A guess at a plot's center is shown in yellow.",
                ConfigCategory.SKYBLOCK,
                c -> c.pestFinderGuessColor,
                v -> { AlpakaConfig.instance.pestFinderGuessColor = v; AlpakaConfig.save(); },
                "pest finder waypoint guess color colour"));

        OPTIONS.add(new ConfigOption("pest_finder_guess_line", "Line to Waypoint",
                "Draws a line from your crosshair to the waypoint.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.pestFinderGuessLine,
                v -> { AlpakaConfig.instance.pestFinderGuessLine = v; AlpakaConfig.save(); },
                "pest finder line tracer waypoint crosshair"));

        OPTIONS.add(new ConfigOption("pest_finder_guess_seconds", "Waypoint Duration",
                "How long the waypoint stays after a click. It goes away sooner once you reach it.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.pestFinderGuessSeconds,
                v -> { AlpakaConfig.instance.pestFinderGuessSeconds = Math.round(v); AlpakaConfig.save(); },
                5.0f, 30.0f, val -> Math.round(val) + " s",
                "pest finder waypoint duration seconds time"));

        OPTIONS.add(new ConfigOption("pest_finder_hide_particles", "Hide Trail Particles",
                "Hides the vacuum's particle trail once it has been read.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.pestFinderHideParticles,
                v -> { AlpakaConfig.instance.pestFinderHideParticles = v; AlpakaConfig.save(); },
                "pest finder hide particles trail villager"));

        OPTIONS.add(new ConfigOption("pest_finder_highlight", "Highlight Pests",
                "Gives the pests around you a glowing outline, visible through walls.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.pestFinderHighlight,
                v -> { AlpakaConfig.instance.pestFinderHighlight = v; AlpakaConfig.save(); },
                "pest finder highlight outline glow esp"));

        OPTIONS.add(ConfigOption.color("pest_finder_highlight_color", "Highlight Color",
                "Color of the pests' outline and of the lines to them.",
                ConfigCategory.SKYBLOCK,
                c -> c.pestFinderHighlightColor,
                v -> { AlpakaConfig.instance.pestFinderHighlightColor = v; AlpakaConfig.save(); },
                "pest finder highlight outline color colour"));

        OPTIONS.add(new ConfigOption("pest_finder_line_to_pests", "Lines to Pests",
                "While you hold a vacuum, draws a line from your crosshair to every pest around you.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.pestFinderLineToPests,
                v -> { AlpakaConfig.instance.pestFinderLineToPests = v; AlpakaConfig.save(); },
                "pest finder line tracer pests vacuum"));

        OPTIONS.add(new ConfigOption("Slayer Tracking", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("slayer_drop_tracker", "Slayer Drop Tracker",
                "Tracks your slayer bosses' kills and loot drops.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerDropTrackerEnabled,
                v -> { AlpakaConfig.instance.slayerDropTrackerEnabled = v; AlpakaConfig.save(); },
                "slayer drop tracker hypixel loot boss kill stats counter skyblock automatically"));


        OPTIONS.add(new ConfigOption("hide_hypixel_drop_message", "Hide Hypixel Drop Line",
                "Hides Hypixel's own drop message when the tracker reports that drop.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.hideHypixelDropMessage,
                v -> { AlpakaConfig.instance.hideHypixelDropMessage = v; AlpakaConfig.save(); },
                "hide hypixel drop message duplicate chat slayer very rare clean"));


        OPTIONS.add(new ConfigOption("slayer_rng_drop_guild_chat", "Share Buttons on RNG Drops",
                "Adds Guild and Party share buttons to a slayer's RNG drop. They fill your chat box; nothing is sent until you press Enter.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerRngDropGuildChatEnabled,
                v -> { AlpakaConfig.instance.slayerRngDropGuildChatEnabled = v; AlpakaConfig.save(); },
                "guild party chat gc pc announce share button rng drop rare dice judgement core warden heart flex top drops rarest"));

        OPTIONS.add(new ConfigOption("slayer_share_message", "Shared Message",
                "What the buttons put in your chat box: the tracker's line with the boss count, or Hypixel's drop message.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.slayerShareMessage,
                v -> { AlpakaConfig.instance.slayerShareMessage = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.slayer.SlayerDropTracker.SHARE_MESSAGE_NAMES.length - 1),
                val -> net.alpaka.addons.features.slayer.SlayerDropTracker.SHARE_MESSAGE_NAMES[Math.round(val)],
                "share message hypixel tracker line drop text guild party post").choices(net.alpaka.addons.features.slayer.SlayerDropTracker.SHARE_MESSAGE_NAMES));



        OPTIONS.add(new ConfigOption("hide_slayer_chat", "Hide Slayer Chat Spam",
                "Hides Hypixel's slayer quest, level-up and radio messages.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.hideSlayerChatMessages,
                v -> { AlpakaConfig.instance.hideSlayerChatMessages = v; AlpakaConfig.save(); },
                "hide slayer chat spam quest complete started radio level up messages"));

        OPTIONS.add(new ConfigOption("hide_slayer_chat_parts", "Hide Slayer Chat Parts",
                "Pick what Hide Slayer Chat Spam hides.",
                ConfigCategory.SKYBLOCK,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Quest & Boss Slain",
                                () -> AlpakaConfig.instance.hideSlayerChatQuest,
                                v -> { AlpakaConfig.instance.hideSlayerChatQuest = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Radio Signal",
                                () -> AlpakaConfig.instance.hideSlayerChatRadio,
                                v -> { AlpakaConfig.instance.hideSlayerChatRadio = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Level-Ups",
                                () -> AlpakaConfig.instance.hideSlayerChatLevelUp,
                                v -> { AlpakaConfig.instance.hideSlayerChatLevelUp = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Kill Healing",
                                () -> AlpakaConfig.instance.hideSlayerChatHealing,
                                v -> { AlpakaConfig.instance.hideSlayerChatHealing = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Sunshade Drop",
                                () -> AlpakaConfig.instance.hideSlayerChatSunshade,
                                v -> { AlpakaConfig.instance.hideSlayerChatSunshade = v; AlpakaConfig.save(); })
                ),
                "slayer chat spam quest started complete boss slain slay combat xp radio signal enjoyer level up lvl healing heal hp sunshade netherrack rare drop hide"));

        OPTIONS.add(new ConfigOption("Slayer Boss Timer", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("boss_spawn_alert", "Boss Spawn Alert",
                "Slides in a notification the moment your slayer boss spawns.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.bossSpawnAlertEnabled,
                v -> { AlpakaConfig.instance.bossSpawnAlertEnabled = v; AlpakaConfig.save(); },
                "boss spawn alert notification slayer popup toast warning notice"));

        OPTIONS.add(new ConfigOption("boss_cocoon_alert", "Boss Cocoon Alert",
                "Slides in a notice when your slayer boss spawns in a cocoon.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.bossCocoonAlertEnabled,
                v -> { AlpakaConfig.instance.bossCocoonAlertEnabled = v; AlpakaConfig.save(); },
                "boss cocoon cocooned alert notification slayer popup toast warning hatch"));

        OPTIONS.add(new ConfigOption("slayer_timer", "Slayer Boss Timer",
                "Times each slayer boss fight, from the boss spawning until it dies.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerTimerEnabled,
                v -> { AlpakaConfig.instance.slayerTimerEnabled = v; AlpakaConfig.save(); },
                "slayer boss timer time fight duration personal best pb"));


        OPTIONS.add(new ConfigOption("slayer_timer_chat", "Boss Time in Chat",
                "Shows each boss's time in your chat, and calls out a new personal best.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerTimerChatEnabled,
                v -> { AlpakaConfig.instance.slayerTimerChatEnabled = v; AlpakaConfig.save(); },
                "slayer boss timer chat message announce personal best posts"));


        OPTIONS.add(new ConfigOption("slayer_timer_hud", "Boss Timer HUD",
                "Shows the running boss time on screen while the boss is up.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerTimerHudEnabled,
                v -> { AlpakaConfig.instance.slayerTimerHudEnabled = v; AlpakaConfig.save(); },
                "slayer boss timer hud live overlay screen"));

        OPTIONS.add(new ConfigOption("Slayer Boss Highlight", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("slayer_boss_highlight", "Boss Highlight",
                "Highlights your own slayer boss so it stands out from the crowd.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerBossHighlightEnabled,
                v -> { AlpakaConfig.instance.slayerBossHighlightEnabled = v; AlpakaConfig.save(); },
                "slayer boss highlight glow outline hitbox box overlay tint color own mark"));

        OPTIONS.add(new ConfigOption("slayer_boss_highlight_style", "Highlight Style",
                "Outline glows around the boss, Hitbox draws a box around it, Overlay tints its model.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.slayerBossHighlightStyle,
                v -> { AlpakaConfig.instance.slayerBossHighlightStyle = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.utils.EntityHighlight.STYLE_NAMES.length - 1),
                val -> net.alpaka.addons.utils.EntityHighlight.STYLE_NAMES[Math.round(val)],
                "slayer boss highlight style mode outline glow hitbox box overlay tint").choices(net.alpaka.addons.utils.EntityHighlight.STYLE_NAMES));

        OPTIONS.add(ConfigOption.color("slayer_boss_highlight_color", "Highlight Color",
                "Color of the boss highlight.",
                ConfigCategory.SKYBLOCK,
                c -> c.slayerBossHighlightColor,
                v -> { AlpakaConfig.instance.slayerBossHighlightColor = v; AlpakaConfig.save(); },
                "slayer boss highlight color colour picker"));

        OPTIONS.add(new ConfigOption("slayer_boss_highlight_attunement", "Blaze Attunement Colors",
                "On the Blaze slayer, uses the color of the current attunement for the boss and its demons.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerBossHighlightAttunement,
                v -> { AlpakaConfig.instance.slayerBossHighlightAttunement = v; AlpakaConfig.save(); },
                "blaze slayer inferno demonlord attunement hellion shield ashen spirit crystal auric dagger demon quazii typhoeus color"));

        OPTIONS.add(new ConfigOption("Slayer Session HUD", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("slayer_hud_enabled", "Slayer Session HUD",
                "Shows live session stats while a slayer quest is active.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudEnabled,
                v -> { AlpakaConfig.instance.slayerHudEnabled = v; AlpakaConfig.save(); },
                "slayer session hud stats xp bosses per hour timer rng dry streak skyblock"));


        OPTIONS.add(new ConfigOption("slayer_hud_lines", "Shown Lines",
                "Pick which lines the HUD shows.",
                ConfigCategory.SKYBLOCK,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Slayer Name",
                                () -> AlpakaConfig.instance.slayerHudShowTitle,
                                v -> { AlpakaConfig.instance.slayerHudShowTitle = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Total Slayer XP",
                                () -> AlpakaConfig.instance.slayerHudShowTotalXp,
                                v -> { AlpakaConfig.instance.slayerHudShowTotalXp = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Session XP",
                                () -> AlpakaConfig.instance.slayerHudShowSessionXp,
                                v -> { AlpakaConfig.instance.slayerHudShowSessionXp = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Slayer XP per Hour",
                                () -> AlpakaConfig.instance.slayerHudShowXpPerHour,
                                v -> { AlpakaConfig.instance.slayerHudShowXpPerHour = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Boss Count",
                                () -> AlpakaConfig.instance.slayerHudShowBossCount,
                                v -> { AlpakaConfig.instance.slayerHudShowBossCount = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Average Boss Time",
                                () -> AlpakaConfig.instance.slayerHudShowAvgBossTime,
                                v -> { AlpakaConfig.instance.slayerHudShowAvgBossTime = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Bosses per Hour",
                                () -> AlpakaConfig.instance.slayerHudShowBossesPerHour,
                                v -> { AlpakaConfig.instance.slayerHudShowBossesPerHour = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Session Time",
                                () -> AlpakaConfig.instance.slayerHudShowSessionTime,
                                v -> { AlpakaConfig.instance.slayerHudShowSessionTime = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Bosses Since RNG Drop",
                                () -> AlpakaConfig.instance.slayerHudShowSinceRngDrop,
                                v -> { AlpakaConfig.instance.slayerHudShowSinceRngDrop = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Polar Bear Effect",
                                () -> AlpakaConfig.instance.slayerHudShowBear,
                                v -> { AlpakaConfig.instance.slayerHudShowBear = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Ham Radio (Blaze)",
                                () -> AlpakaConfig.instance.slayerHudShowRadio,
                                v -> { AlpakaConfig.instance.slayerHudShowRadio = v; AlpakaConfig.save(); })
                ),
                "slayer hud lines toggle show hide xp bosses per hour session time rng dry streak average bear gummy polar smoldering polarization effect slayer hud lines name total count session time ham radio blazetekk channel enjoyers"));

        OPTIONS.add(new ConfigOption("slayer_hud_bear_blaze_only", "Bear Line Only on Blaze",
                "Shows the polar bear effect only on the Blaze HUD instead of on every slayer.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudBearBlazeOnly,
                v -> { AlpakaConfig.instance.slayerHudBearBlazeOnly = v; AlpakaConfig.save(); },
                "slayer hud bear polar gummy smoldering polarization blaze only inferno"));


        OPTIONS.add(new ConfigOption("slayer_hud_only_in_areas", "Only in Slayer Areas",
                "Shows the HUD only where that slayer is run, like SkyHanni's profit tracker.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudOnlyInSlayerAreas,
                v -> { AlpakaConfig.instance.slayerHudOnlyInSlayerAreas = v; AlpakaConfig.save(); },
                "slayer hud area island restrict show everywhere anywhere zone slayer hud only in slayer areas"));


        OPTIONS.add(new ConfigOption("slayer_hud_afk_pause", "Pause After Idle",
                "How long you can stand still before the session timer pauses.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudAfkPauseSeconds,
                v -> { AlpakaConfig.instance.slayerHudAfkPauseSeconds = v; AlpakaConfig.save(); },
                5.0f, 300.0f, AlpakaConfigRegistry::formatDuration,
                "slayer session timer pause afk idle stand still seconds duration threshold session pause after standing still idle time"));


        OPTIONS.add(new ConfigOption("slayer_hud_pause_outside_area", "Pause Outside Slayer Area",
                "Pauses the session timer once you leave the slayer's area.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudPauseOutsideArea,
                v -> { AlpakaConfig.instance.slayerHudPauseOutsideArea = v; AlpakaConfig.save(); },
                "slayer session pause outside area zone leave region timer stop pause session outside slayer area"));

        OPTIONS.add(new ConfigOption("World Age", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("world_age_join_message_enabled", "World Age on Join",
                "Shows the world's age in your chat when you join a server, and whether you were there recently.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.worldAgeJoinMessageEnabled,
                v -> { AlpakaConfig.instance.worldAgeJoinMessageEnabled = v; AlpakaConfig.save(); },
                "world age server join chat message notification alert day time server age join message posts"));


        OPTIONS.add(new ConfigOption("world_age_recent_threshold", "Recent Visit Window",
                "How recently you must have visited to be told about it.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.worldAgeRecentThresholdSec,
                v -> { AlpakaConfig.instance.worldAgeRecentThresholdSec = Math.round(v); AlpakaConfig.save(); },
                10.0f, 300.0f, AlpakaConfigRegistry::formatDuration,
                "world age recent visit threshold window seconds time minutes slider"));

        OPTIONS.add(new ConfigOption("Party", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("party_invite_prompt", "Party Invite Prompt",
                "Shows a Y/N prompt on screen when someone invites you to a party.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.partyInvitePromptEnabled,
                v -> { AlpakaConfig.instance.partyInvitePromptEnabled = v; AlpakaConfig.save(); },
                "party invite prompt accept join decline y n key popup hypixel"));

        OPTIONS.add(new ConfigOption("party_commands", "Party Commands",
                "When a party member writes a command like !warp, !tps or !pt in party chat, a Y/N prompt offers to run it for them.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.partyCommandsEnabled,
                v -> { AlpakaConfig.instance.partyCommandsEnabled = v; AlpakaConfig.save(); },
                "party commands chat !warp !w !tps !ping !fps !pt !transfer !allinvite !coords !kick !f7 !m7 odin skyhanni prompt y n"));

        OPTIONS.add(new ConfigOption("party_commands_auto_answer", "Auto Answer",
                "Info and fun commands like !tps, !ping or !dice are answered in party chat right away, without the Y/N prompt. Leader commands like !warp or !kick always ask.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.partyCommandsAutoAnswer,
                v -> { AlpakaConfig.instance.partyCommandsAutoAnswer = v; AlpakaConfig.save(); },
                "party commands auto answer reply automatic direct without confirmation prompt instant tps ping fps"));

        for (net.alpaka.addons.features.party.PartyCommand.Group group : net.alpaka.addons.features.party.PartyCommand.Group.values()) {
            java.util.List<ConfigOption.ToggleEntry> entries = new java.util.ArrayList<>();
            for (net.alpaka.addons.features.party.PartyCommand command : net.alpaka.addons.features.party.PartyCommand.values()) {
                if (command.group() != group) continue;
                entries.add(new ConfigOption.ToggleEntry(command.label(),
                        () -> net.alpaka.addons.features.party.PartyCommandsFeature.isCommandEnabled(command),
                        v -> net.alpaka.addons.features.party.PartyCommandsFeature.setCommandEnabled(command, v)));
            }
            String[] text = switch (group) {
                case LEADER -> new String[]{"party_commands_leader", "Leader Commands",
                        "Commands that need you to be the party leader. Kick, transfer, promote and demote start off."};
                case INFO -> new String[]{"party_commands_info", "Info Commands",
                        "Commands that post something about you in party chat, like your TPS or coordinates."};
                case FUN -> new String[]{"party_commands_fun", "Fun Commands",
                        "Dice, coin flip and the Magic 8-Ball, answered in party chat."};
            };
            OPTIONS.add(new ConfigOption(text[0], text[1], text[2], ConfigCategory.SKYBLOCK, entries,
                    "party commands pick which " + text[1].toLowerCase(java.util.Locale.ROOT)));
        }

        OPTIONS.add(new ConfigOption("Commands", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("shorten_warp", "Shorten Warp",
                "Type just the destination to warp there: /isle instead of /warp isle. Commands that already exist keep their meaning.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.shortenWarpEnabled,
                v -> { AlpakaConfig.instance.shortenWarpEnabled = v; AlpakaConfig.save(); },
                "shorten warp short shortcut alias destination /isle /hub /garden /dh command"));

        OPTIONS.add(new ConfigOption("warp_cooldown_queue", "Warp Cooldown",
                "A warp typed right after joining a server waits for Hypixel's cooldown and goes out on its own when it ends, instead of being refused.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.warpCooldownQueueEnabled,
                v -> { AlpakaConfig.instance.warpCooldownQueueEnabled = v; AlpakaConfig.save(); },
                "warp cooldown queue delay wait server join transfer early command you may only use this command"));

        OPTIONS.add(new ConfigOption("Resource Pack", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("cache_resource_pack", "Cache Resource Pack",
                "Keeps Hypixel's resource pack loaded, so joining SkyBlock skips the loading screen.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.cacheResourcePackEnabled,
                v -> { AlpakaConfig.instance.cacheResourcePackEnabled = v; AlpakaConfig.save(); },
                "cache resource pack server pack texture loading screen reload join hypixel skyblock fast"));

        OPTIONS.add(new ConfigOption("Markets", ConfigCategory.SKYBLOCK));

        // The market screens and their keys are left out while they aren't ready; see Markets.SCREENS_AVAILABLE.
        if (net.alpaka.addons.features.market.Markets.SCREENS_AVAILABLE) {
            OPTIONS.add(new ConfigOption("custom_auction_house", "Custom Auction House",
                    "Shows the Auction House in Alpaka's design: categories with icons, auctions as cards with price, time and seller. Each click is the same click on Hypixel's menu.",
                    ConfigCategory.SKYBLOCK,
                    () -> AlpakaConfig.instance.customAuctionHouseEnabled,
                    v -> { AlpakaConfig.instance.customAuctionHouseEnabled = v; AlpakaConfig.save(); },
                    "auction house ah auctions browser bin bid custom menu gui redesign cards market"));

            OPTIONS.add(new ConfigOption("auction_house_hotkeys", "Enable Hotkeys",
                    "Number keys open the first nine auctions or press the numbered buttons, Shift and a number picks a category, the arrow keys turn the page, / or Ctrl+F searches and Backspace goes back; the keys can be changed below. One click per key press.",
                    ConfigCategory.SKYBLOCK,
                    () -> AlpakaConfig.instance.auctionHouseHotkeysEnabled,
                    v -> { AlpakaConfig.instance.auctionHouseHotkeysEnabled = v; AlpakaConfig.save(); },
                    "auction house ah hotkeys keys keyboard shortcuts number keys quick buy category page search"));

            OPTIONS.add(ConfigOption.keybind("auction_house_previous_page_key", "Previous Page Key",
                    "Turns to the previous page. Left Arrow by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.AUCTION_HOUSE.previousPage(),
                    "auction house ah hotkeys previous page left arrow"));

            OPTIONS.add(ConfigOption.keybind("auction_house_next_page_key", "Next Page Key",
                    "Turns to the next page. Right Arrow by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.AUCTION_HOUSE.nextPage(),
                    "auction house ah hotkeys next page right arrow"));

            OPTIONS.add(ConfigOption.keybind("auction_house_back_key", "Back Key",
                    "Goes back to the menu before. Backspace by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.AUCTION_HOUSE.back(),
                    "auction house ah hotkeys back go back return backspace"));

            OPTIONS.add(ConfigOption.keybind("auction_house_search_key", "Search Key",
                    "Opens the search, in addition to / and Ctrl+F. Unbound by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.AUCTION_HOUSE.search(),
                    "auction house ah hotkeys search find query slash"));

            OPTIONS.add(ConfigOption.keybind("auction_house_create_auction_key", "Create Auction Key",
                    "Presses Create Auction in Manage Auctions. C by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.AUCTION_HOUSE.createAuction(),
                    "auction house ah hotkeys create auction sell manage"));

            OPTIONS.add(ConfigOption.keybind("auction_house_reset_settings_key", "Reset Settings Key",
                    "Presses Reset Settings, which clears the sort and filters. R by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.AUCTION_HOUSE.resetSettings(),
                    "auction house ah hotkeys reset settings sort filters clear"));

            OPTIONS.add(new ConfigOption("custom_bazaar", "Custom Bazaar",
                    "Shows the Bazaar in Alpaka's design, with buy and sell prices on every product. Each click is the same click on Hypixel's menu.",
                    ConfigCategory.SKYBLOCK,
                    () -> AlpakaConfig.instance.customBazaarEnabled,
                    v -> { AlpakaConfig.instance.customBazaarEnabled = v; AlpakaConfig.save(); },
                    "bazaar bz buy sell order offer instant custom menu gui redesign prices market"));

            OPTIONS.add(new ConfigOption("bazaar_hotkeys", "Enable Hotkeys",
                    "Number keys open the first nine products or press the numbered buttons, Shift and a number picks a category, the arrow keys turn the page, / or Ctrl+F searches and Backspace goes back; the keys can be changed below. One click per key press.",
                    ConfigCategory.SKYBLOCK,
                    () -> AlpakaConfig.instance.bazaarHotkeysEnabled,
                    v -> { AlpakaConfig.instance.bazaarHotkeysEnabled = v; AlpakaConfig.save(); },
                    "bazaar bz hotkeys keys keyboard shortcuts number keys instant buy sell category page search"));

            OPTIONS.add(ConfigOption.keybind("bazaar_previous_page_key", "Previous Page Key",
                    "Turns to the previous page. Left Arrow by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.BAZAAR.previousPage(),
                    "bazaar bz hotkeys previous page left arrow"));

            OPTIONS.add(ConfigOption.keybind("bazaar_next_page_key", "Next Page Key",
                    "Turns to the next page. Right Arrow by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.BAZAAR.nextPage(),
                    "bazaar bz hotkeys next page right arrow"));

            OPTIONS.add(ConfigOption.keybind("bazaar_back_key", "Back Key",
                    "Goes back to the menu before. Backspace by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.BAZAAR.back(),
                    "bazaar bz hotkeys back go back return backspace"));

            OPTIONS.add(ConfigOption.keybind("bazaar_search_key", "Search Key",
                    "Opens the search, in addition to / and Ctrl+F. Unbound by default.",
                    ConfigCategory.SKYBLOCK,
                    () -> net.alpaka.addons.features.market.MarketKeys.BAZAAR.search(),
                    "bazaar bz hotkeys search find query slash"));
        }

        OPTIONS.add(new ConfigOption("market_price_history", "Price History",
                net.alpaka.addons.features.market.Markets.SCREENS_AVAILABLE
                        ? "Shows the price history from SkyCofl on a product's or an auction's page. Needs Network Features."
                        : "Shows the price history from SkyCofl in the Item Value popup. Needs Network Features.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.marketPriceHistoryEnabled,
                v -> { AlpakaConfig.instance.marketPriceHistoryEnabled = v; AlpakaConfig.save(); },
                "price history graph chart coflnet skycofl bazaar auction trend range day week month"));

        OPTIONS.add(new ConfigOption("market_price_history_range", "Default Range",
                "How far back the graph goes when a page opens. The range picked on the graph is remembered here.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.marketPriceHistoryRange,
                v -> { AlpakaConfig.instance.marketPriceHistoryRange = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.market.PriceHistory.Range.LABELS.length - 1),
                val -> net.alpaka.addons.features.market.PriceHistory.Range.LABELS[Math.round(val)],
                "price history range time span day week month all").choices(net.alpaka.addons.features.market.PriceHistory.Range.LABELS));

        OPTIONS.add(new ConfigOption("custom_sign_input", "Custom Sign Input",
                "Replaces Hypixel's sign for searches, prices and amounts with an input bar that shows what an amount like 1.5m means.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.customSignInputEnabled,
                v -> { AlpakaConfig.instance.customSignInputEnabled = v; AlpakaConfig.save(); },
                "sign input search price amount bid query text field bar auction bazaar"));

        OPTIONS.add(new ConfigOption("item_value", "Item Value",
                "Press the Item Value key over an item to see what it is worth, broken down into its enchantments, stars, gemstones and upgrades at current prices. Needs Network Features.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.itemValueEnabled,
                v -> { AlpakaConfig.instance.itemValueEnabled = v; AlpakaConfig.save(); },
                "item value worth price networth net worth estimate breakdown enchantments stars gemstones upgrades hotkey key popup lowest bin bazaar graph unbound controls"));

        OPTIONS.add(ConfigOption.keybind("item_value_key", "Key",
                "The key to press over an item in a menu. Unbound by default.",
                ConfigCategory.SKYBLOCK,
                () -> net.alpaka.addons.features.itemvalue.ItemValueFeature.KEY,
                "item value worth price"));

        OPTIONS.add(new ConfigOption("item_value_bazaar_price", "Bazaar Prices",
                "Whether parts sold on the Bazaar count at what they cost to buy now or at what they would sell for now.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.itemValueBazaarPrice,
                v -> { AlpakaConfig.instance.itemValueBazaarPrice = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.itemvalue.ItemPrices.SIDES.length - 1),
                val -> net.alpaka.addons.features.itemvalue.ItemPrices.SIDES[Math.round(val)],
                "item value bazaar price instant buy sell side order offer").choices(net.alpaka.addons.features.itemvalue.ItemPrices.SIDES));

        OPTIONS.add(new ConfigOption("item_search", "Item Search",
                "A window with a search bar over everything you own: your inventory, Ender Chest, backpacks, sacks, wardrobe, Accessory Bag, Personal Vault and the chests on your island. Each place is known from the last time you opened it. Open it with its key or /alpakasearch; the key over an item in a menu searches for that item.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.itemSearchEnabled,
                v -> { AlpakaConfig.instance.itemSearchEnabled = v; AlpakaConfig.save(); },
                "item search find where storage ender chest echest backpack sack sacks wardrobe accessory bag vault island chest inventory lookup locate"));

        OPTIONS.add(ConfigOption.keybind("item_search_key", "Key",
                "Opens Item Search. In a menu, it searches for the item under the mouse. Unbound by default.",
                ConfigCategory.SKYBLOCK,
                () -> net.alpaka.addons.features.itemsearch.ItemSearchFeature.KEY,
                "item search find"));

        OPTIONS.add(new ConfigOption("item_search_open", "Open Item Search",
                "Opens the search window. Works on SkyBlock only.",
                ConfigCategory.SKYBLOCK,
                "Open",
                parent -> net.alpaka.addons.features.itemsearch.ItemSearchFeature.open(null, parent),
                "item search open window"));

        OPTIONS.add(new ConfigOption("item_search_clear", "Forget Saved Items",
                "Forgets every remembered container of the profile you are on, so the search starts empty again.",
                ConfigCategory.SKYBLOCK,
                "Forget",
                parent -> net.alpaka.addons.features.itemsearch.ItemSearchStore.clear(),
                "item search clear reset forget saved items containers chests")
                .confirm("Forget Saved Items",
                        "Item Search forgets what it remembered from your Ender Chest, backpacks, sacks, wardrobe and island chests on this profile. "
                                + "Each comes back the next time you open it.",
                        "Forget", true));

        OPTIONS.add(new ConfigOption("chest_value", "Chest Value",
                "Press the Chest Value key in a chest, backpack, Ender Chest or your inventory to show what everything in it is worth: every item with its price and share of the total. It stays shown in every menu until you press the key again. Needs Network Features.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.chestValueEnabled,
                v -> { AlpakaConfig.instance.chestValueEnabled = v; AlpakaConfig.save(); },
                "chest value worth price networth net worth total inventory backpack ender chest storage container hotkey key panel overlay share lowest bin bazaar unbound controls"));

        OPTIONS.add(ConfigOption.keybind("chest_value_key", "Key",
                "The key to press in a menu, or anywhere else, to show or hide the panel. Unbound by default.",
                ConfigCategory.SKYBLOCK,
                () -> net.alpaka.addons.features.chestvalue.ChestValueFeature.KEY,
                "chest value worth price panel"));

        OPTIONS.add(new ConfigOption("chest_value_side", "Panel Side",
                "Which side of the menu the panel sits on. Left keeps it clear of item list mods such as NEU, REI and Skyblocker, which use the right.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.chestValueSide,
                v -> { AlpakaConfig.instance.chestValueSide = Math.round(v); AlpakaConfig.save(); },
                0.0f, 1.0f,
                val -> CHEST_VALUE_SIDES[Math.round(val)],
                "chest value side left right position place").choices(CHEST_VALUE_SIDES));

        OPTIONS.add(new ConfigOption("Profile", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("profile_dashboard", "Profile Dashboard",
                "/alpakaprofile opens a dashboard of your SkyBlock profile: networth, skills, dungeons, slayers, pets and collections, with graphs of your progress. /alpakaprofile <name> shows another player. Data from Elite; needs Network Features.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.profileDashboardEnabled,
                v -> { AlpakaConfig.instance.profileDashboardEnabled = v; AlpakaConfig.save(); },
                "profile dashboard stats statistics networth skills dungeons catacombs slayer pets collections graph progress history viewer pv elite command"));

        OPTIONS.add(new ConfigOption("profile_dashboard_open", "Open Dashboard",
                "Opens the dashboard on your own profile, as /alpakaprofile does.",
                ConfigCategory.SKYBLOCK,
                "Open Dashboard",
                parent -> net.alpaka.addons.features.profile.ProfileDashboardScreen.open(null, parent),
                "profile dashboard open show"));

        OPTIONS.add(new ConfigOption("profile_dashboard_tracking", "Record Progress",
                "While you play SkyBlock, saves a snapshot of your profile now and then, so the graphs fill in without opening the dashboard. Kept on this computer only.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.profileDashboardTracking,
                v -> { AlpakaConfig.instance.profileDashboardTracking = v; AlpakaConfig.save(); },
                "profile dashboard record track progress history snapshot background hourly graph"));

        OPTIONS.add(new ConfigOption("profile_dashboard_tracking_minutes", "Record Every",
                "How often the snapshot is taken while you play.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.profileDashboardTrackingMinutes,
                v -> { AlpakaConfig.instance.profileDashboardTrackingMinutes = Math.round(v); AlpakaConfig.save(); },
                15.0f, 240.0f, v -> Math.round(v) + " min",
                "profile dashboard record interval minutes snapshot how often"));

        OPTIONS.add(new ConfigOption("profile_dashboard_skykings_key", "SkyKings API Key",
                "Needed for the dashboard's graphs: SkyKings records most players about once a day. Make a free key on skykings.net (log in with Discord, Dashboard › API Keys) and paste it here. Only sent to SkyKings, kept on this computer.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.skykingsApiKey,
                v -> { AlpakaConfig.instance.skykingsApiKey = v.trim(); },
                "sk_...",
                "skykings api key token history graph profile dashboard").secret(64));

        OPTIONS.add(new ConfigOption("Guild Chat", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("guild_prefix", "Custom Guild Tag",
                "Shows your own tag instead of \"Guild >\". Works with Chat Tabs.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.guildPrefixEnabled,
                v -> { AlpakaConfig.instance.guildPrefixEnabled = v; AlpakaConfig.save(); },
                "guild chat prefix tag name replace custom color"));

        OPTIONS.add(new ConfigOption("guild_prefix_text", "Guild Tag",
                "Shown instead of \"Guild >\". Write color codes with &, as in \"&eLEMAN >\".",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.guildPrefixText,
                v -> { AlpakaConfig.instance.guildPrefixText = v; },
                "&eLEMAN >",
                "guild tag text prefix color code ampersand"));


        OPTIONS.add(new ConfigOption("bridge_bot_formatter", "Bridge Bot Formatter",
                "Shows relayed Discord messages as [Discord] instead of the bot's name and rank.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.bridgeBotFormatterEnabled,
                v -> { AlpakaConfig.instance.bridgeBotFormatterEnabled = v; AlpakaConfig.save(); },
                "bridge bot discord guild chat relay formatter format author webhook"));

        OPTIONS.add(new ConfigOption("bridge_bot_name", "Bridge Bot Name",
                "The relay account's in-game name.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.bridgeBotName,
                v -> { AlpakaConfig.instance.bridgeBotName = v; },
                "e.g. VultureGround",
                "bridge bot name account discord guild relay username set"));

        OPTIONS.add(new ConfigOption("Abilities", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("ragnarock", "Ragnarock",
                "Shows the Ragnarock's cast countdown under your crosshair instead of in the action bar, then \"Ragnarock\" or \"Cancelled\".",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.ragnarockEnabled,
                v -> { AlpakaConfig.instance.ragnarockEnabled = v; AlpakaConfig.save(); },
                "ragnarock ragnarok rag axe sword cast casting countdown timer crosshair action bar ability strength"));

        OPTIONS.add(new ConfigOption("deployable_timer", "Deployable Timer",
                "Shows the strongest power orb or flare in range with the time it has left. Move it in the HUD editor.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.deployableTimerEnabled,
                v -> { AlpakaConfig.instance.deployableTimerEnabled = v; AlpakaConfig.save(); },
                "deployable timer power orb flare radiant mana flux overflux plasmaflux warning alert sos hud icon time left range buff"));

        OPTIONS.add(new ConfigOption("Enchantments", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("max_enchant_color", "Max Enchant Color",
                "Enchantments at their highest level get their own color in tooltips.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.maxEnchantColorEnabled,
                v -> { AlpakaConfig.instance.maxEnchantColorEnabled = v; AlpakaConfig.save(); },
                "enchantments enchants max maxed level highest color colour tooltip lore sharpness perfect"));

        OPTIONS.add(ConfigOption.color("max_enchant_color_value", "Max Level Color",
                "Color of maxed enchantments.",
                ConfigCategory.SKYBLOCK,
                c -> c.maxEnchantColor,
                v -> { AlpakaConfig.instance.maxEnchantColor = v; AlpakaConfig.save(); },
                "enchantments enchants max maxed level color colour picker tooltip"));

        OPTIONS.add(new ConfigOption("max_enchant_color_ultimate", "Include Ultimate Enchants",
                "Maxed ultimate enchantments get the color too instead of their pink.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.maxEnchantColorUltimate,
                v -> { AlpakaConfig.instance.maxEnchantColorUltimate = v; AlpakaConfig.save(); },
                "enchantments enchants ultimate pink max color tooltip wise soul eater chimera"));

        OPTIONS.add(new ConfigOption("anvil_combine_helper", "Anvil Combine Helper",
                "Put an enchanted book into the anvil and the books it combines with are highlighted in your inventory.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.anvilCombineHelperEnabled,
                v -> { AlpakaConfig.instance.anvilCombineHelperEnabled = v; AlpakaConfig.save(); },
                "anvil combine helper enchanted book books highlight matching same level merge inventory"));

        OPTIONS.add(ConfigOption.color("anvil_combine_helper_color", "Highlight Color",
                "Color of the highlighted books.",
                ConfigCategory.SKYBLOCK,
                c -> c.anvilCombineHelperColor,
                v -> { AlpakaConfig.instance.anvilCombineHelperColor = v; AlpakaConfig.save(); },
                "anvil combine helper highlight color colour picker books"));

        OPTIONS.add(new ConfigOption("experiment_solver", "Experiment Solver",
                "Shows the answers in the Experimentation Table: the next click in Chronomatron and Ultrasequencer, and every card already seen in Superpairs.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.experimentSolverEnabled,
                v -> { AlpakaConfig.instance.experimentSolverEnabled = v; AlpakaConfig.save(); },
                "experiment experiments experimentation table solver chronomatron ultrasequencer superpairs enchanting minigame add-ons"));

        OPTIONS.add(ConfigOption.color("experiment_solver_next_color", "Next Click Color",
                "The slot to click now, and Superpairs cards whose pair is known.",
                ConfigCategory.SKYBLOCK,
                c -> c.experimentSolverNextColor,
                v -> { AlpakaConfig.instance.experimentSolverNextColor = v; AlpakaConfig.save(); },
                "experiment solver next click highlight color colour picker"));

        OPTIONS.add(ConfigOption.color("experiment_solver_later_color", "Click After Color",
                "The slot to click after that.",
                ConfigCategory.SKYBLOCK,
                c -> c.experimentSolverLaterColor,
                v -> { AlpakaConfig.instance.experimentSolverLaterColor = v; AlpakaConfig.save(); },
                "experiment solver after following second highlight color colour picker"));

        OPTIONS.add(new ConfigOption("Damage Display", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("only_crit_damage", "Only Show Crit Damage",
                "Hides non-crit, fire, poison and ability damage tags on SkyBlock.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.onlyCritDamageEnabled,
                v -> { AlpakaConfig.instance.onlyCritDamageEnabled = v; AlpakaConfig.save(); },
                "damage indicator tags numbers hide non-crit fire poison abilities crit skyblock"));

        OPTIONS.add(new ConfigOption("visitor_bazaar", "Visitor Bazaar Shortcut",
                "Clicking Accept Offer at a garden visitor while you lack the items opens the bazaar for them and copies the amount.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.visitorBazaarEnabled,
                v -> { AlpakaConfig.instance.visitorBazaarEnabled = v; AlpakaConfig.save(); },
                "garden visitor bazaar bz buy missing items accept offer shortcut shopping farming"));


        // --- 6. COSMETICS ---

        OPTIONS.add(new ConfigOption("Own Name Tag", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("custom_name_tag", "Custom Name Tag",
                "Shows your own name tag in third person, drawn with effects. Only you can see it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customNameTagEnabled,
                v -> { AlpakaConfig.instance.customNameTagEnabled = v; AlpakaConfig.save(); },
                "name tag nametag own player third person f5 cosmetic animation norisk"));

        OPTIONS.add(new ConfigOption("name_tag_color_mode", "Color Effect",
                "Vanilla keeps Hypixel's colors. Rainbow, Gradient and Pulse recolor the letters.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.nameTagColorMode,
                v -> { AlpakaConfig.instance.nameTagColorMode = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (CustomNameTagFeature.COLOR_MODE_NAMES.length - 1),
                val -> CustomNameTagFeature.COLOR_MODE_NAMES[Math.round(val)],
                "name tag color rainbow chroma gradient pulse effect mode").choices(CustomNameTagFeature.COLOR_MODE_NAMES));

        OPTIONS.add(new ConfigOption("name_tag_motion_mode", "Letter Motion",
                "Wave makes the letters bob one after another, Bounce hops the whole tag, Shake jitters.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.nameTagMotionMode,
                v -> { AlpakaConfig.instance.nameTagMotionMode = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (CustomNameTagFeature.MOTION_MODE_NAMES.length - 1),
                val -> CustomNameTagFeature.MOTION_MODE_NAMES[Math.round(val)],
                "name tag motion wave wobble bounce jump shake jitter letters animation").choices(CustomNameTagFeature.MOTION_MODE_NAMES));

        OPTIONS.add(new ConfigOption("name_tag_speed", "Animation Speed",
                "How fast the colors move and the letters bob.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagAnimationSpeed,
                v -> { AlpakaConfig.instance.nameTagAnimationSpeed = v; AlpakaConfig.save(); },
                0.2f, 3.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "name tag animation speed fast slow"));

        OPTIONS.add(new ConfigOption("name_tag_scale", "Name Tag Size",
                "How big the tag is. 1.00x is vanilla.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagScale,
                v -> { AlpakaConfig.instance.nameTagScale = v; AlpakaConfig.save(); },
                0.5f, 2.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "name tag size scale big small relative"));

        OPTIONS.add(new ConfigOption("name_tag_height", "Name Tag Height",
                "Raises or lowers the tag from where vanilla would put it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagHeightOffset,
                v -> { AlpakaConfig.instance.nameTagHeightOffset = v; AlpakaConfig.save(); },
                -0.5f, 0.5f, val -> String.format(Locale.ROOT, "%+.2f blocks", val),
                "name tag height offset raise lower position up down"));

        OPTIONS.add(new ConfigOption("name_tag_color_whole", "Color the Rank Too",
                "Applies the color effect to the whole tag, rank prefix included, not just your name.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagColorWholeTag,
                v -> { AlpakaConfig.instance.nameTagColorWholeTag = v; AlpakaConfig.save(); },
                "name tag rank prefix mvp vip color whole tag"));

        OPTIONS.add(new ConfigOption("name_tag_outline", "Text Outline",
                "Draws each letter with a dark outline instead of a shadow.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagOutlineEnabled,
                v -> { AlpakaConfig.instance.nameTagOutlineEnabled = v; AlpakaConfig.save(); },
                "name tag outline border text glow"));

        OPTIONS.add(new ConfigOption("name_tag_shadow", "Text Shadow",
                "Adds the usual Minecraft drop shadow to the letters.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagShadowEnabled,
                v -> { AlpakaConfig.instance.nameTagShadowEnabled = v; AlpakaConfig.save(); },
                "name tag shadow text drop"));

        OPTIONS.add(new ConfigOption("name_tag_chroma_border", "Chroma Frame",
                "Draws a thin rainbow frame around the tag's backdrop.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagChromaBorderEnabled,
                v -> { AlpakaConfig.instance.nameTagChromaBorderEnabled = v; AlpakaConfig.save(); },
                "name tag frame border chroma rainbow backdrop"));

        OPTIONS.add(new ConfigOption("name_tag_background", "Backdrop",
                "Draws the dark box behind the letters. When off, the text floats on its own.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagBackgroundEnabled,
                v -> { AlpakaConfig.instance.nameTagBackgroundEnabled = v; AlpakaConfig.save(); },
                "name tag background backdrop box remove hide off"));

        OPTIONS.add(new ConfigOption("name_tag_bg_opacity", "Backdrop Opacity",
                "How dark the backdrop is. Vanilla is 25%.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagBackgroundOpacity,
                v -> { AlpakaConfig.instance.nameTagBackgroundOpacity = v; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Off (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "name tag background backdrop opacity dark box darkening letters"));

        OPTIONS.add(ConfigOption.color("name_tag_gradient_start_color", "Gradient Start Color",
                "The gradient's start color.",
                ConfigCategory.COSMETICS,
                c -> c.nameTagGradientStart,
                v -> { AlpakaConfig.instance.nameTagGradientStart = v; AlpakaConfig.save(); },
                "name tag gradient color start first picker"));

        OPTIONS.add(ConfigOption.color("name_tag_gradient_end_color", "Gradient End Color",
                "The gradient's end color.",
                ConfigCategory.COSMETICS,
                c -> c.nameTagGradientEnd,
                v -> { AlpakaConfig.instance.nameTagGradientEnd = v; AlpakaConfig.save(); },
                "name tag gradient color end second picker"));

        OPTIONS.add(new ConfigOption("Sensei Hat", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("chroma_hat", "Sensei Hat",
                "Puts a translucent straw sensei hat on your own head. Only you can see it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatEnabled,
                v -> { AlpakaConfig.instance.chromaHatEnabled = v; AlpakaConfig.save(); },
                "chroma hat sensei samurai kasa jingasa cosmetic head straw"));

        OPTIONS.add(new ConfigOption("chroma_hat_rainbow", "Chroma Colors",
                "Sweeps rainbow colors around the hat, glowing in the dark. When off, the hat keeps its plain color.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatRainbow,
                v -> { AlpakaConfig.instance.chromaHatRainbow = v; AlpakaConfig.save(); },
                "chroma hat rainbow colors glow emissive"));

        OPTIONS.add(ConfigOption.color("chroma_hat_color", "Hat Color",
                "The plain hat's color, used while Chroma Colors is off.",
                ConfigCategory.COSMETICS,
                c -> c.chromaHatColor,
                v -> { AlpakaConfig.instance.chromaHatColor = v; AlpakaConfig.save(); },
                "chroma hat color straw plain picker"));

        OPTIONS.add(new ConfigOption("chroma_hat_opacity", "Hat Opacity",
                "How solid the hat looks.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatOpacity,
                v -> { AlpakaConfig.instance.chromaHatOpacity = v; AlpakaConfig.save(); },
                10.0f, 100.0f, val -> String.format(Locale.ROOT, "%.0f%%", val),
                "chroma hat opacity transparent translucent alpha"));

        OPTIONS.add(new ConfigOption("chroma_hat_size", "Hat Size",
                "How big the hat is, brim width and height.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatSize,
                v -> { AlpakaConfig.instance.chromaHatSize = v; AlpakaConfig.save(); },
                0.6f, 1.6f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "chroma hat size scale big small brim"));

        OPTIONS.add(new ConfigOption("chroma_hat_height", "Hat Height",
                "Raises or lowers the hat. At 0 it rests on the head without touching the skin.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatHeightOffset,
                v -> { AlpakaConfig.instance.chromaHatHeightOffset = v; AlpakaConfig.save(); },
                -0.3f, 0.3f, val -> String.format(Locale.ROOT, "%+.2f blocks", val),
                "chroma hat height offset raise lower float sit position"));

        OPTIONS.add(new ConfigOption("chroma_hat_speed", "Chroma Speed",
                "How fast the rainbow spins around the hat. Chroma Colors only.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatSpeed,
                v -> { AlpakaConfig.instance.chromaHatSpeed = v; AlpakaConfig.save(); },
                0.2f, 3.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "chroma hat color speed rainbow spin fast slow hat color speed"));

        OPTIONS.add(new ConfigOption("Aura", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("aura", "Aura",
                "Surrounds you with an animated aura of light in third person. Only you can see it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.auraEnabled,
                v -> { AlpakaConfig.instance.auraEnabled = v; AlpakaConfig.save(); },
                "aura effect glow particles orbs helix sigil magic circle fire flames galaxy stars fireflies storm lightning cosmetic third person f5"));

        OPTIONS.add(new ConfigOption("aura_effect", "Effect",
                "Which aura you wear: orbiting spirits, a helix, a magic circle, flames, a galaxy, fireflies or lightning.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.auraEffect,
                v -> { AlpakaConfig.instance.auraEffect = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (AuraFeature.EFFECT_NAMES.length - 1),
                val -> AuraFeature.EFFECT_NAMES[Math.round(val)],
                "aura effect type style spirit orbs twin helix arcane sigil inferno galaxy fireflies storm").choices(AuraFeature.EFFECT_NAMES));

        OPTIONS.add(new ConfigOption("aura_color_mode", "Colors",
                "Effect Colors keeps each effect's own colors. Custom blends between the two colors below.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.auraColorMode,
                v -> { AlpakaConfig.instance.auraColorMode = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (AuraFeature.COLOR_MODE_NAMES.length - 1),
                val -> AuraFeature.COLOR_MODE_NAMES[Math.round(val)],
                "aura color colors rainbow chroma custom effect palette").choices(AuraFeature.COLOR_MODE_NAMES));

        OPTIONS.add(ConfigOption.color("aura_primary_color", "Primary Color",
                "The first color of the aura in the Custom color mode.",
                ConfigCategory.COSMETICS,
                c -> c.auraPrimaryColor,
                v -> { AlpakaConfig.instance.auraPrimaryColor = v; AlpakaConfig.save(); },
                "aura color primary first custom picker"));

        OPTIONS.add(ConfigOption.color("aura_secondary_color", "Secondary Color",
                "The second color of the aura in the Custom color mode.",
                ConfigCategory.COSMETICS,
                c -> c.auraSecondaryColor,
                v -> { AlpakaConfig.instance.auraSecondaryColor = v; AlpakaConfig.save(); },
                "aura color secondary second custom picker"));

        OPTIONS.add(new ConfigOption("aura_size", "Aura Size",
                "How far the aura reaches out from you.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.auraSize,
                v -> { AlpakaConfig.instance.auraSize = v; AlpakaConfig.save(); },
                0.5f, 2.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "aura size scale big small radius wide"));

        OPTIONS.add(new ConfigOption("aura_speed", "Animation Speed",
                "How fast the aura moves.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.auraSpeed,
                v -> { AlpakaConfig.instance.auraSpeed = v; AlpakaConfig.save(); },
                0.2f, 3.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "aura animation speed fast slow"));

        OPTIONS.add(new ConfigOption("aura_brightness", "Brightness",
                "How strongly the aura glows.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.auraBrightness,
                v -> { AlpakaConfig.instance.auraBrightness = v; AlpakaConfig.save(); },
                10.0f, 100.0f, val -> String.format(Locale.ROOT, "%.0f%%", val),
                "aura brightness glow strength intensity opacity"));

        OPTIONS.add(new ConfigOption("Player Scale", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("player_scale", "Player Scale",
                "Stretches or shrinks your model in third person. Purely visual, only you can see it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleEnabled,
                v -> { AlpakaConfig.instance.playerScaleEnabled = v; AlpakaConfig.save(); },
                "player scale size model body big small tall wide giant tiny stretch shrink cosmetic third person f5"));

        OPTIONS.add(new ConfigOption("player_scale_x", "Width (X)",
                "How wide your model is, across the shoulders. 1.00x is vanilla.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleX,
                v -> { AlpakaConfig.instance.playerScaleX = PlayerScaleFeature.snap(v); AlpakaConfig.save(); },
                PlayerScaleFeature.MIN_SCALE, PlayerScaleFeature.MAX_SCALE, val -> String.format(Locale.ROOT, "%.2fx", val),
                "player scale width x axis wide thin narrow broad side to side"));

        OPTIONS.add(new ConfigOption("player_scale_y", "Height (Y)",
                "How tall your model is. The feet stay on the ground, so it grows upward.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleY,
                v -> { AlpakaConfig.instance.playerScaleY = PlayerScaleFeature.snap(v); AlpakaConfig.save(); },
                PlayerScaleFeature.MIN_SCALE, PlayerScaleFeature.MAX_SCALE, val -> String.format(Locale.ROOT, "%.2fx", val),
                "player scale height y axis tall short giant tiny feet to head upwards"));

        OPTIONS.add(new ConfigOption("player_scale_z", "Depth (Z)",
                "How deep your model is, front to back. It turns with the body, whichever way you face.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleZ,
                v -> { AlpakaConfig.instance.playerScaleZ = PlayerScaleFeature.snap(v); AlpakaConfig.save(); },
                PlayerScaleFeature.MIN_SCALE, PlayerScaleFeature.MAX_SCALE, val -> String.format(Locale.ROOT, "%.2fx", val),
                "player scale depth z axis thick flat paper thin"));

        OPTIONS.add(new ConfigOption("player_scale_others", "Scale Other Players Too",
                "Scales other real players on SkyBlock too, but only ever shrinks them. Hitboxes are unchanged.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleOthers,
                v -> { AlpakaConfig.instance.playerScaleOthers = v; AlpakaConfig.save(); },
                "player scale others other players everyone all everybody shrunk never enlarged"));

        OPTIONS.add(new ConfigOption("player_scale_reset", "Reset Scale",
                "Puts all three axes back to 1.00x. The toggle stays as it is.",
                ConfigCategory.COSMETICS,
                "Reset Scale",
                parent -> {
                    AlpakaConfig.instance.playerScaleX = 1.0f;
                    AlpakaConfig.instance.playerScaleY = 1.0f;
                    AlpakaConfig.instance.playerScaleZ = 1.0f;
                    AlpakaConfig.save();
                },
                "player scale reset default vanilla normal 1.0")
                .confirm("Reset Scale",
                        "Puts all three axes of your player scale back to 1.00x. This cannot be undone.",
                        "Reset", true));

        OPTIONS.add(new ConfigOption("Damage Tags", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("custom_damage_tags", "Custom Damage Tags",
                "Draws the damage numbers in gradients that pop up, float and fade. Visual only.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagsEnabled,
                v -> { AlpakaConfig.instance.customDamageTagsEnabled = v; AlpakaConfig.save(); },
                "damage tags numbers splash custom animated pop fade gradient crit replace hypixel"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_scale", "Damage Tag Size",
                "How big the custom damage numbers are. Crits are drawn a quarter larger.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagScale,
                v -> { AlpakaConfig.instance.customDamageTagScale = v; AlpakaConfig.save(); },
                0.5f, 3.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "damage tag size scale big small"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_duration", "Damage Tag Duration",
                "How long a number stays before it has faded out.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.customDamageTagDurationMs,
                v -> { AlpakaConfig.instance.customDamageTagDurationMs = Math.round(v / 50f) * 50; AlpakaConfig.save(); },
                400.0f, 3000.0f, val -> String.format(Locale.ROOT, "%.2f s", Math.round(val / 50f) * 50 / 1000f),
                "damage tag duration time lifetime fade long short"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_pop", "Pop Strength",
                "How far a number overshoots its size when it appears. 0% just grows in.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagPopStrength * 100f,
                v -> { AlpakaConfig.instance.customDamageTagPopStrength = v / 100f; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> String.format(Locale.ROOT, "%.0f%%", val),
                "damage tag pop bounce overshoot animation"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_rise", "Float Height",
                "How far a number drifts upward while it fades.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagRise,
                v -> { AlpakaConfig.instance.customDamageTagRise = v; AlpakaConfig.save(); },
                0.0f, 2.0f, val -> String.format(Locale.ROOT, "%.2f blocks", val),
                "damage tag float rise up drift height upwards"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_format", "Number Format",
                "Hypixel's own format, shortened (1.2M), or in full with separators (1,234,567).",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.customDamageTagFormat,
                v -> { AlpakaConfig.instance.customDamageTagFormat = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (CustomDamageTagFeature.FORMAT_NAMES.length - 1),
                val -> CustomDamageTagFeature.FORMAT_NAMES[Math.round(val)],
                "damage tag number format compact short million k m b separator as hypixel compact full").choices(CustomDamageTagFeature.FORMAT_NAMES));

        OPTIONS.add(new ConfigOption("custom_damage_tag_flow", "Flowing Gradient",
                "Makes the gradient sway along the digits instead of standing still.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagFlowingGradient,
                v -> { AlpakaConfig.instance.customDamageTagFlowingGradient = v; AlpakaConfig.save(); },
                "damage tag gradient flow animated color shimmer"));


        OPTIONS.add(new ConfigOption("custom_damage_tag_shadow", "Damage Tag Shadow",
                "Draws a drop shadow behind the digits, so they read on bright backgrounds.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagShadow,
                v -> { AlpakaConfig.instance.customDamageTagShadow = v; AlpakaConfig.save(); },
                "damage tag shadow drop text"));


        OPTIONS.add(new ConfigOption("custom_damage_tag_hide_slayer", "Hide Near Slayer Bosses",
                "Hides the custom damage numbers on or right next to a slayer boss, yours or anyone's.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagHideNearSlayerBoss,
                v -> { AlpakaConfig.instance.customDamageTagHideNearSlayerBoss = v; AlpakaConfig.save(); },
                "damage tag hide slayer boss near revenant tarantula sven voidgloom inferno demonlord vampire skyhanni"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_dungeon_mode", "Hide in Dungeons",
                "Hides the custom damage numbers in the dungeon boss room, or for the whole run.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.customDamageTagDungeonMode,
                v -> { AlpakaConfig.instance.customDamageTagDungeonMode = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (CustomDamageTagFeature.DUNGEON_MODE_NAMES.length - 1),
                val -> CustomDamageTagFeature.DUNGEON_MODE_NAMES[Math.round(val)],
                "damage tag dungeon catacombs boss room floor hide master mode damage tags in dungeons show never whole run").choices(CustomDamageTagFeature.DUNGEON_MODE_NAMES));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_normal_start", "Normal Hit Start Color",
                "The start color of a normal hit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagNormalStart,
                v -> { AlpakaConfig.instance.customDamageTagNormalStart = v; AlpakaConfig.save(); },
                "damage tag gradient color picker normal start first"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_normal_end", "Normal Hit End Color",
                "The end color of a normal hit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagNormalEnd,
                v -> { AlpakaConfig.instance.customDamageTagNormalEnd = v; AlpakaConfig.save(); },
                "damage tag gradient color picker normal end last"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_crit_start", "Crit Start Color",
                "The start color of a crit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagCritStart,
                v -> { AlpakaConfig.instance.customDamageTagCritStart = v; AlpakaConfig.save(); },
                "damage tag gradient color picker crit start first"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_crit_end", "Crit End Color",
                "The end color of a crit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagCritEnd,
                v -> { AlpakaConfig.instance.customDamageTagCritEnd = v; AlpakaConfig.save(); },
                "damage tag gradient color picker crit end last"));

        // --- 7. SOUND & UTILITY ---

        // The quick command list is edited far more often than any sound is retuned, so it heads
        // the tab rather than sitting below every sound option.
        OPTIONS.add(new ConfigOption("Utility", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("command_wheel_custom_commands", "Command Wheel",
                "A wheel of your own commands on a key.",
                ConfigCategory.SOUND_MISC,
                "Edit Commands",
                parent -> Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.CommandWheelConfigScreen(parent)),
                "quick command wheel commands add remove edit custom list menu keybind quick command menu editor entries unbound controls"));

        OPTIONS.add(ConfigOption.keybind("command_wheel_key", "Key",
                "The key that opens the wheel. Unbound by default.",
                ConfigCategory.SOUND_MISC,
                () -> net.alpaka.addons.features.wheel.CommandWheelFeature.COMMAND_WHEEL_KEY,
                "quick command wheel open"));

        OPTIONS.add(new ConfigOption("command_wheel_toggle_mode", "Toggle Command Wheel",
                "Press the key once to open the wheel and click a command, instead of holding the key and releasing it.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.commandWheelToggleMode,
                v -> { AlpakaConfig.instance.commandWheelToggleMode = v; AlpakaConfig.save(); },
                "quick command wheel toggle hold press release click open mode keybind"));

        OPTIONS.add(new ConfigOption("command_shortcuts", "Command Shortcuts",
                "Short aliases for commands, such as /v for /visit (/v Steve still works), and keys that run a command.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.commandShortcutsEnabled,
                v -> { AlpakaConfig.instance.commandShortcutsEnabled = v; AlpakaConfig.save(); },
                "command shortcuts alias aliases abbreviation short keybind key bind hotkey run command visit warp skyblocker"));

        OPTIONS.add(new ConfigOption("command_shortcuts_edit", "Shortcuts",
                "Add, edit or remove aliases and key shortcuts.",
                ConfigCategory.SOUND_MISC,
                "Edit Shortcuts",
                parent -> Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.CommandShortcutsConfigScreen(parent)),
                "command shortcuts alias aliases edit add remove keybind key bind hotkey list menu"));

        OPTIONS.add(new ConfigOption("inventory_buttons", "Inventory Buttons",
                "Your own buttons around the inventory and every chest or menu. A click runs their command.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.inventoryButtonsEnabled,
                v -> { AlpakaConfig.instance.inventoryButtonsEnabled = v; AlpakaConfig.save(); },
                "inventory buttons custom command shortcut chest menu gui icon quick storage pets wardrobe"));

        OPTIONS.add(new ConfigOption("inventory_buttons_edit", "Edit Inventory Buttons",
                "Add buttons, pick their command and icon, and drag them to where you want them.",
                ConfigCategory.SOUND_MISC,
                "Edit Buttons",
                parent -> Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.InventoryButtonsEditorScreen(parent)),
                "inventory buttons edit add remove move place icon command editor"));

        OPTIONS.add(new ConfigOption("inventory_buttons_skyblock_only", "Only on SkyBlock",
                "Show the inventory buttons only while you are on Hypixel SkyBlock.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.inventoryButtonsSkyblockOnly,
                v -> { AlpakaConfig.instance.inventoryButtonsSkyblockOnly = v; AlpakaConfig.save(); },
                "inventory buttons skyblock only hypixel"));

        OPTIONS.add(new ConfigOption("inventory_buttons_style", "Button Style",
                "How the buttons look: the Alpaka menu's dark tiles, vanilla's gray slots, or the icon alone.",
                ConfigCategory.SOUND_MISC,
                () -> (float) AlpakaConfig.instance.inventoryButtonsStyle,
                v -> { AlpakaConfig.instance.inventoryButtonsStyle = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.inventorybuttons.InventoryButtonsFeature.STYLE_NAMES.length - 1),
                val -> net.alpaka.addons.features.inventorybuttons.InventoryButtonsFeature.STYLE_NAMES[Math.round(val)],
                "inventory buttons style look design alpaka vanilla icon only appearance theme")
                .choices(net.alpaka.addons.features.inventorybuttons.InventoryButtonsFeature.STYLE_NAMES));

        OPTIONS.add(ConfigOption.color("inventory_buttons_color", "Button Color",
                "The tile behind each icon in the Alpaka style, and behind a hovered icon in Icon Only.",
                ConfigCategory.SOUND_MISC,
                c -> c.inventoryButtonsColor,
                v -> { AlpakaConfig.instance.inventoryButtonsColor = v; AlpakaConfig.save(); },
                "inventory buttons color colour background tile appearance"));

        OPTIONS.add(new ConfigOption("inventory_buttons_rounding", "Corner Rounding",
                "How round the corners of the Alpaka style's tiles are.",
                ConfigCategory.SOUND_MISC,
                () -> (float) AlpakaConfig.instance.inventoryButtonsRounding,
                v -> { AlpakaConfig.instance.inventoryButtonsRounding = Math.round(v); AlpakaConfig.save(); },
                0.0f, 9.0f,
                val -> Math.round(val) + " px",
                "inventory buttons corner rounding radius round square appearance"));

        OPTIONS.add(new ConfigOption("always_sprint", "Always Sprint",
                "Sprints whenever you walk forward, no sprint key needed.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.alwaysSprintEnabled,
                v -> { AlpakaConfig.instance.alwaysSprintEnabled = v; AlpakaConfig.save(); },
                "always sprint auto run toggle movement key hold forward walking"));

        OPTIONS.add(new ConfigOption("always_sprint_ignore_garden", "Ignore Garden",
                "Always Sprint switches itself off while you are on the Garden.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.alwaysSprintIgnoreGarden,
                v -> { AlpakaConfig.instance.alwaysSprintIgnoreGarden = v; AlpakaConfig.save(); },
                "always sprint garden farming disable ignore off skyblock island"));

        OPTIONS.add(new ConfigOption("keep_cursor_position", "Keep Cursor Position",
                "The cursor stays where it is when one menu follows another, such as backpack pages, instead of jumping back to the middle.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.keepCursorPositionEnabled,
                v -> { AlpakaConfig.instance.keepCursorPositionEnabled = v; AlpakaConfig.save(); },
                "keep cursor position mouse reset center centre middle inventory menu backpack storage page gui no cursor reset"));

        OPTIONS.add(new ConfigOption("quick_join", "Quick Join",
                "Gets you into the game faster: straight into SkyBlock when you join Hypixel, and onto Hypixel when the game starts.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.quickJoinEnabled,
                v -> { AlpakaConfig.instance.quickJoinEnabled = v; AlpakaConfig.save(); },
                "quick join auto skyblock lobby warp hypixel connect fast start skip"));

        OPTIONS.add(new ConfigOption("quick_join_skyblock", "Join SkyBlock",
                "Sends /skyblock once you have joined Hypixel, unless you already landed in SkyBlock.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.quickJoinSkyblock,
                v -> { AlpakaConfig.instance.quickJoinSkyblock = v; AlpakaConfig.save(); },
                "quick join auto skyblock lobby warp command hypixel"));

        OPTIONS.add(new ConfigOption("quick_join_on_launch", "Join Hypixel on Launch",
                "Connects to Hypixel as soon as the game has started. Hold Shift while it loads to stay in the main menu.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.quickJoinOnLaunch,
                v -> { AlpakaConfig.instance.quickJoinOnLaunch = v; AlpakaConfig.save(); },
                "quick join launch startup start auto connect hypixel server main menu skip"));

        OPTIONS.add(new ConfigOption("Custom Sounds", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sounds", "Custom Sounds",
                "Main switch for every custom sound below. Still a work in progress.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsEnabled,
                v -> { AlpakaConfig.instance.customSoundsEnabled = v; AlpakaConfig.save(); },
                "custom sounds audio effects click chime notification master master custom sounds"));

        OPTIONS.add(new ConfigOption("custom_sounds_volume", "Volume",
                "How loud the custom sounds play.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsVolume,
                v -> { AlpakaConfig.instance.customSoundsVolume = v; AlpakaConfig.save(); },
                0.0f, 1.0f, val -> String.format(Locale.ROOT, "%d%%", Math.round(val * 100)),
                "custom sounds volume loudness audio gain percent level effects"));

        OPTIONS.add(new ConfigOption("Interface Sounds", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_button_click", "Button Click Sound",
                "Plays a custom chime when you click a button in a menu.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundButtonClick,
                v -> { AlpakaConfig.instance.customSoundButtonClick = v; AlpakaConfig.save(); },
                "button click sound ui audio feedback chime ui button click sound"));

        OPTIONS.add(soundVolume("custom_sound_button_click_volume", "Volume", "button click",
                () -> AlpakaConfig.instance.customSoundButtonClickVolume,
                v -> AlpakaConfig.instance.customSoundButtonClickVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_hotbar_scroll", "Hotbar Scroll Sound",
                "Plays a custom sound when you scroll or switch hotbar slots.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundHotbarScroll,
                v -> { AlpakaConfig.instance.customSoundHotbarScroll = v; AlpakaConfig.save(); },
                "hotbar scroll slot equip switch audio sound"));

        OPTIONS.add(soundVolume("custom_sound_hotbar_scroll_volume", "Volume", "hotbar scroll",
                () -> AlpakaConfig.instance.customSoundHotbarScrollVolume,
                v -> AlpakaConfig.instance.customSoundHotbarScrollVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_inventory_open_close", "Container Open & Close Sound",
                "Plays a custom sound when you open or close a container.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundInventoryOpenClose,
                v -> { AlpakaConfig.instance.customSoundInventoryOpenClose = v; AlpakaConfig.save(); },
                "inventory open close gui container sound audio custom inventory open/close sound"));

        OPTIONS.add(soundVolume("custom_sound_inventory_open_close_volume", "Volume", "open/close",
                () -> AlpakaConfig.instance.customSoundInventoryOpenCloseVolume,
                v -> AlpakaConfig.instance.customSoundInventoryOpenCloseVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_inventory_click", "Inventory Click Sound",
                "Plays a custom sound when you click an item or pick it up.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundInventoryClick,
                v -> { AlpakaConfig.instance.customSoundInventoryClick = v; AlpakaConfig.save(); },
                "inventory click item pickup container sound audio custom"));

        OPTIONS.add(soundVolume("custom_sound_inventory_click_volume", "Volume", "inventory click",
                () -> AlpakaConfig.instance.customSoundInventoryClickVolume,
                v -> AlpakaConfig.instance.customSoundInventoryClickVolume = v));

        OPTIONS.add(new ConfigOption("Gameplay Sounds", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_rare_drop", "Rare Drop Sound",
                "Plays a sound when you get a rare slayer drop.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundRareDrop,
                v -> { AlpakaConfig.instance.customSoundRareDrop = v; AlpakaConfig.save(); },
                "rare drop sound slayer loot celebration audio slayer rare drop sound"));

        OPTIONS.add(soundVolume("custom_sound_rare_drop_volume", "Volume", "rare drop",
                () -> AlpakaConfig.instance.customSoundRareDropVolume,
                v -> AlpakaConfig.instance.customSoundRareDropVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_notification", "Boss Spawn Sound",
                "Plays a sound when a slayer boss spawns.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundNotification,
                v -> { AlpakaConfig.instance.customSoundNotification = v; AlpakaConfig.save(); },
                "boss spawn sound notification alert audio"));

        OPTIONS.add(soundVolume("custom_sound_notification_volume", "Volume", "boss spawn",
                () -> AlpakaConfig.instance.customSoundNotificationVolume,
                v -> AlpakaConfig.instance.customSoundNotificationVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_player_hurt", "Player Hurt Sound",
                "Plays a custom sound when you take damage.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundPlayerHurt,
                v -> { AlpakaConfig.instance.customSoundPlayerHurt = v; AlpakaConfig.save(); },
                "player hurt sound damage hit audio custom"));

        OPTIONS.add(soundVolume("custom_sound_player_hurt_volume", "Volume", "player hurt",
                () -> AlpakaConfig.instance.customSoundPlayerHurtVolume,
                v -> AlpakaConfig.instance.customSoundPlayerHurtVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_blaze_death", "Blaze Death Sound",
                "Plays a custom sound when a blaze you fought dies. Other kills are ignored.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundBlazeDeath,
                v -> { AlpakaConfig.instance.customSoundBlazeDeath = v; AlpakaConfig.save(); },
                "blaze death sound kill slayer audio custom own kills"));

        OPTIONS.add(soundVolume("custom_sound_blaze_death_volume", "Volume", "blaze death",
                () -> AlpakaConfig.instance.customSoundBlazeDeathVolume,
                v -> AlpakaConfig.instance.customSoundBlazeDeathVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_zombie_remedy", "Zombie Remedy Sound",
                "Plays a custom sound for the zombie villager cure effect.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundZombieRemedy,
                v -> { AlpakaConfig.instance.customSoundZombieRemedy = v; AlpakaConfig.save(); },
                "zombie remedy cure villager sound audio custom"));

        OPTIONS.add(soundVolume("custom_sound_zombie_remedy_volume", "Volume", "zombie remedy",
                () -> AlpakaConfig.instance.customSoundZombieRemedyVolume,
                v -> AlpakaConfig.instance.customSoundZombieRemedyVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_successful_hit", "Successful Hit Sound",
                "Plays a custom sound on a critical or successful hit.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundSuccessfulHit,
                v -> { AlpakaConfig.instance.customSoundSuccessfulHit = v; AlpakaConfig.save(); },
                "successful hit crit critical attack sound audio custom"));

        OPTIONS.add(soundVolume("custom_sound_successful_hit_volume", "Volume", "successful hit",
                () -> AlpakaConfig.instance.customSoundSuccessfulHitVolume,
                v -> AlpakaConfig.instance.customSoundSuccessfulHitVolume = v));

        OPTIONS.add(new ConfigOption("Low Health Warning", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_low_hp_heartbeat", "Low Health Heartbeat",
                "Plays a heartbeat when your health drops below the threshold.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundLowHpHeartbeat,
                v -> { AlpakaConfig.instance.customSoundLowHpHeartbeat = v; AlpakaConfig.save(); },
                "low hp health heartbeat sound survival audio custom lowhp pulse alert low hp heartbeat sound"));

        OPTIONS.add(soundVolume("custom_sound_low_hp_heartbeat_volume", "Volume", "heartbeat",
                () -> AlpakaConfig.instance.customSoundLowHpHeartbeatVolume,
                v -> AlpakaConfig.instance.customSoundLowHpHeartbeatVolume = v));

        OPTIONS.add(new ConfigOption("low_hp_heartbeat_threshold", "Heartbeat Threshold",
                "The health percentage at which the heartbeat starts.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.lowHpHeartbeatThreshold * 100.0f,
                v -> { AlpakaConfig.instance.lowHpHeartbeatThreshold = v / 100.0f; AlpakaConfig.save(); },
                5.0f, 80.0f, val -> String.format(Locale.ROOT, "%.0f%%", val),
                "heartbeat low hp health percentage threshold trigger sound volume heartbeat hp threshold"));

        registerHudStyleToggles();
        buildCategoryCache();
    }

    /** One line of a dropdown, saved on every change. */
    private static ConfigOption.ToggleEntry toggle(String label, java.util.function.Predicate<AlpakaConfig> getter,
                                                   java.util.function.BiConsumer<AlpakaConfig, Boolean> setter) {
        return new ConfigOption.ToggleEntry(label,
                () -> getter.test(AlpakaConfig.instance),
                v -> { setter.accept(AlpakaConfig.instance, v); AlpakaConfig.save(); });
    }

    /**
     * The Dungeon section: the puzzle solvers, the F7/M7 terminal solvers with their own custom
     * window, and the F7/M7 device solvers. All of it only draws or refuses the player's own click;
     * see features/dungeon/DungeonSolvers. Four masters, each a feature row of its own (ConfigTree).
     */
    private static void registerDungeonOptions() {
        OPTIONS.add(new ConfigOption("Dungeon", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("dungeon_solvers", "Solvers",
                "Shows the solution of every Catacombs puzzle as boxes, lines and labels in the room you are in. "
                        + "It only draws, and walls hide it like any block.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.dungeonSolversEnabled,
                v -> { AlpakaConfig.instance.dungeonSolversEnabled = v; AlpakaConfig.save(); },
                "dungeon dungeons catacombs puzzle puzzles solver solvers solution helper room f1 f2 f3 f4 f5 f6 f7 m7 "
                        + "master mode three weirdos chests quiz trivia oruo tic tac toe higher or lower blaze creeper beams "
                        + "lanterns teleport maze tp pads water board levers boulder boxes ice fill ice path silverfish"));

        OPTIONS.add(new ConfigOption("dungeon_solver_puzzles", "Puzzles",
                "Pick which puzzles are solved.",
                ConfigCategory.SKYBLOCK,
                java.util.List.of(
                        toggle("Three Weirdos", c -> c.solveThreeWeirdos, (c, v) -> c.solveThreeWeirdos = v),
                        toggle("Quiz", c -> c.solveQuiz, (c, v) -> c.solveQuiz = v),
                        toggle("Tic Tac Toe", c -> c.solveTicTacToe, (c, v) -> c.solveTicTacToe = v),
                        toggle("Higher Or Lower", c -> c.solveHigherOrLower, (c, v) -> c.solveHigherOrLower = v),
                        toggle("Creeper Beams", c -> c.solveCreeperBeams, (c, v) -> c.solveCreeperBeams = v),
                        toggle("Teleport Maze", c -> c.solveTeleportMaze, (c, v) -> c.solveTeleportMaze = v),
                        toggle("Water Board", c -> c.solveWaterBoard, (c, v) -> c.solveWaterBoard = v),
                        toggle("Boulder", c -> c.solveBoulder, (c, v) -> c.solveBoulder = v),
                        toggle("Ice Fill", c -> c.solveIceFill, (c, v) -> c.solveIceFill = v),
                        toggle("Ice Path", c -> c.solveIcePath, (c, v) -> c.solveIcePath = v)
                ),
                "dungeon puzzle puzzles pick which weirdos quiz trivia tic tac toe blaze creeper beams teleport maze "
                        + "water board boulder ice fill path silverfish"));

        OPTIONS.add(new ConfigOption("water_board_fast_solutions", "Water Board: Fast Solutions",
                "Uses quicker Water Board solutions with flips closer together. They need tighter timing; "
                        + "off uses the standard solutions.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.waterBoardFastSolutions,
                v -> { AlpakaConfig.instance.waterBoardFastSolutions = v; AlpakaConfig.save(); },
                "dungeon water board levers fast quick quicker faster solutions timing"));

        OPTIONS.add(ConfigOption.color("dungeon_solver_color", "Solution Color",
                "What to do now: the right chest, answer, pad, lever or block. The device solvers use it too, and "
                        + "Creeper Beams colors its pairs from it and the Next Color.",
                ConfigCategory.SKYBLOCK,
                c -> c.dungeonSolverColor,
                v -> { AlpakaConfig.instance.dungeonSolverColor = v; AlpakaConfig.save(); },
                "dungeon solver solution color correct green highlight"));

        OPTIONS.add(ConfigOption.color("dungeon_solver_next_color", "Next Color",
                "What comes after it, such as the next blaze or the next step.",
                ConfigCategory.SKYBLOCK,
                c -> c.dungeonSolverNextColor,
                v -> { AlpakaConfig.instance.dungeonSolverNextColor = v; AlpakaConfig.save(); },
                "dungeon solver next color second step yellow"));

        OPTIONS.add(ConfigOption.color("dungeon_solver_wrong_color", "Wrong Color",
                "Used pads, lying weirdos and wrong answers.",
                ConfigCategory.SKYBLOCK,
                c -> c.dungeonSolverWrongColor,
                v -> { AlpakaConfig.instance.dungeonSolverWrongColor = v; AlpakaConfig.save(); },
                "dungeon solver wrong color red liar used pad"));

        OPTIONS.add(new ConfigOption("dungeon_solver_box_style", "Box Style",
                "How a marked block is drawn: its edges, a translucent fill, or both.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.dungeonSolverBoxStyle,
                v -> {
                    int index = Math.round(v);
                    if (index != AlpakaConfig.instance.dungeonSolverBoxStyle) {
                        AlpakaConfig.instance.dungeonSolverBoxStyle = index;
                        AlpakaConfig.save();
                    }
                },
                0.0f, (float) (net.alpaka.addons.features.dungeon.render.SolverColors.BOX_STYLES.length - 1),
                val -> net.alpaka.addons.features.dungeon.render.SolverColors.boxStyleName(Math.round(val)),
                "dungeon solver box style outline filled fill both edges")
                .choices(net.alpaka.addons.features.dungeon.render.SolverColors.BOX_STYLES));

        OPTIONS.add(new ConfigOption("dungeon_solver_line_width", "Line Thickness",
                "How thick box edges and lines are.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.dungeonSolverLineWidth,
                v -> { AlpakaConfig.instance.dungeonSolverLineWidth = Math.round(v * 10.0f) / 10.0f; AlpakaConfig.save(); },
                1.0f, 6.0f, val -> String.format(Locale.ROOT, "%.1f px", val),
                "dungeon solver line thickness width outline px pixels"));

        OPTIONS.add(new ConfigOption("dungeon_solver_labels", "Show Labels",
                "Order numbers, click counts and countdowns next to the marks.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.dungeonSolverLabels,
                v -> { AlpakaConfig.instance.dungeonSolverLabels = v; AlpakaConfig.save(); },
                "dungeon solver labels text numbers order count countdown timer"));

        OPTIONS.add(new ConfigOption("terminal_solvers", "Terminal Solvers",
                "Marks the solution of the F7 and M7 terminals in their chest window. It never clicks for you, but "
                        + "by default it ignores clicks made right after a terminal opens or within 50 ms of the last one, "
                        + "and nothing is sent for those (First Click Protection, Click Rate Guard).",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalSolversEnabled,
                v -> { AlpakaConfig.instance.terminalSolversEnabled = v; AlpakaConfig.save(); },
                "dungeon catacombs f7 m7 floor 7 goldor terminal terminals solver solvers panes rubix color numbers order "
                        + "starts with select all melody chest gui p3"));

        OPTIONS.add(new ConfigOption("terminal_solver_types", "Terminals",
                "Pick which terminals are solved.",
                ConfigCategory.SKYBLOCK,
                java.util.List.of(
                        toggle("Correct All the Panes", c -> c.solvePanesTerminal, (c, v) -> c.solvePanesTerminal = v),
                        toggle("Change All to Same Color", c -> c.solveRubixTerminal, (c, v) -> c.solveRubixTerminal = v),
                        toggle("Click in Order", c -> c.solveNumbersTerminal, (c, v) -> c.solveNumbersTerminal = v),
                        toggle("What Starts With", c -> c.solveStartsWithTerminal, (c, v) -> c.solveStartsWithTerminal = v),
                        toggle("Select All", c -> c.solveSelectAllTerminal, (c, v) -> c.solveSelectAllTerminal = v),
                        toggle("Click the Button on Time", c -> c.solveMelodyTerminal, (c, v) -> c.solveMelodyTerminal = v)
                ),
                "terminal terminals pick which panes rubix same color numbers order starts with select all melody button"));

        OPTIONS.add(new ConfigOption("terminal_hide_other_items", "Hide Other Items",
                "In the chest window, items that are not part of the solution are not drawn. Only changes what you see.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalHideOtherItems,
                v -> { AlpakaConfig.instance.terminalHideOtherItems = v; AlpakaConfig.save(); },
                "terminal hide other items wrong clutter clean"));

        OPTIONS.add(new ConfigOption("terminal_predict_clicks", "Predict Clicks",
                "Moves the marks on as soon as you click instead of when the server confirms the click. "
                        + "Off, a clicked cell only shows as pending until the server answers.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalPredictClicks,
                v -> { AlpakaConfig.instance.terminalPredictClicks = v; AlpakaConfig.save(); },
                "terminal predict prediction clicks ping lag pending confirm"));

        OPTIONS.add(new ConfigOption("terminal_block_wrong_clicks", "Block Wrong Clicks",
                "Ignores a click on a cell that is not part of the solution, or with the wrong mouse button. "
                        + "Nothing is sent for a blocked click. Never used for Click the Button on Time. Use at your own risk.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalBlockWrongClicks,
                v -> { AlpakaConfig.instance.terminalBlockWrongClicks = v; AlpakaConfig.save(); },
                "terminal block wrong clicks misclick refuse cancel protect"));

        OPTIONS.add(new ConfigOption("terminal_first_click_guard", "First Click Protection",
                "Hypixel is believed to flag clicks made right after a terminal opens, so these are ignored and nothing "
                        + "is sent for them. Turning this off is at your own risk.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalFirstClickGuard,
                v -> { AlpakaConfig.instance.terminalFirstClickGuard = v; AlpakaConfig.save(); },
                "terminal first click protection guard delay watchdog flag ban lag"));

        OPTIONS.add(new ConfigOption("terminal_first_click_ms", "First Click Delay",
                "How long after a terminal opens clicks are ignored. The server also has to tick once per 50 ms of it, "
                        + "so a lagging server waits longer.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.terminalFirstClickMs,
                v -> { AlpakaConfig.instance.terminalFirstClickMs = Math.round(v / 10.0f) * 10; AlpakaConfig.save(); },
                350.0f, 800.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "terminal first click delay ms milliseconds ticks protection"));

        OPTIONS.add(new ConfigOption("terminal_rate_guard", "Click Rate Guard",
                "Ignores a click that comes less than one server tick (50 ms) after the last click that went out, "
                        + "and the volley of clicks a shift double click would send to every matching item, "
                        + "which helps keep Hypixel from throttling the terminal. Nothing is sent for an ignored click. "
                        + "Turning this off is at your own risk.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalRateGuard,
                v -> { AlpakaConfig.instance.terminalRateGuard = v; AlpakaConfig.save(); },
                "terminal click rate guard throttle limit fast double click tick warning risk"));

        OPTIONS.add(ConfigOption.color("terminal_solution_color", "Solution Color",
                "The cells to click.",
                ConfigCategory.SKYBLOCK,
                c -> c.terminalSolutionColor,
                v -> { AlpakaConfig.instance.terminalSolutionColor = v; AlpakaConfig.save(); },
                "terminal solution color click green"));

        OPTIONS.add(ConfigOption.color("terminal_next_color", "Second Color",
                "Click in Order: the cell after the next one.",
                ConfigCategory.SKYBLOCK,
                c -> c.terminalNextColor,
                v -> { AlpakaConfig.instance.terminalNextColor = v; AlpakaConfig.save(); },
                "terminal second next color numbers order"));

        OPTIONS.add(ConfigOption.color("terminal_third_color", "Third Color",
                "Click in Order: the third cell.",
                ConfigCategory.SKYBLOCK,
                c -> c.terminalThirdColor,
                v -> { AlpakaConfig.instance.terminalThirdColor = v; AlpakaConfig.save(); },
                "terminal third color numbers order"));

        OPTIONS.add(ConfigOption.color("terminal_rubix_left_color", "Left Click Color",
                "Change All to Same Color: panes that need left clicks.",
                ConfigCategory.SKYBLOCK,
                c -> c.terminalRubixLeftColor,
                v -> { AlpakaConfig.instance.terminalRubixLeftColor = v; AlpakaConfig.save(); },
                "terminal rubix same color left click"));

        OPTIONS.add(ConfigOption.color("terminal_rubix_right_color", "Right Click Color",
                "Change All to Same Color: panes that need right clicks.",
                ConfigCategory.SKYBLOCK,
                c -> c.terminalRubixRightColor,
                v -> { AlpakaConfig.instance.terminalRubixRightColor = v; AlpakaConfig.save(); },
                "terminal rubix same color right click"));

        OPTIONS.add(ConfigOption.color("terminal_melody_color", "Melody Color",
                "Click the Button on Time: the target column and the moving pane.",
                ConfigCategory.SKYBLOCK,
                c -> c.terminalMelodyColor,
                v -> { AlpakaConfig.instance.terminalMelodyColor = v; AlpakaConfig.save(); },
                "terminal melody color button on time column"));

        OPTIONS.add(new ConfigOption("terminal_custom_gui", "Custom Terminal GUI",
                "Shows terminals in a larger, cleaner window with the solution drawn in. Needs Terminal Solvers on, "
                        + "and opens only for the terminals ticked under Terminals. Only the clicks you make are sent, "
                        + "unchanged; a click the Terminal Solvers guards ignore is not sent at all.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalCustomGui,
                v -> { AlpakaConfig.instance.terminalCustomGui = v; AlpakaConfig.save(); },
                "dungeon f7 m7 terminal custom gui window screen bigger larger clean odin grid"));

        OPTIONS.add(new ConfigOption("terminal_gui_scale", "GUI Size",
                "How large the cells of the custom window are. It shrinks to fit the screen.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.terminalGuiScale,
                v -> { AlpakaConfig.instance.terminalGuiScale = Math.round(v * 10.0f) / 10.0f; AlpakaConfig.save(); },
                1.0f, 3.0f, val -> String.format(Locale.ROOT, "%.1fx", val),
                "terminal gui size scale cells bigger larger"));

        OPTIONS.add(ConfigOption.color("terminal_gui_background_color", "Background Color",
                "The custom window's background.",
                ConfigCategory.SKYBLOCK,
                c -> c.terminalGuiBackgroundColor,
                v -> { AlpakaConfig.instance.terminalGuiBackgroundColor = v; AlpakaConfig.save(); },
                "terminal gui background color panel"));

        OPTIONS.add(new ConfigOption("device_solvers", "Device Solvers",
                "Marks what to do at the F7 and M7 devices in the Goldor phase, in the Solvers colors. It only draws, "
                        + "and walls hide it like any block.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.deviceSolversEnabled,
                v -> { AlpakaConfig.instance.deviceSolversEnabled = v; AlpakaConfig.save(); },
                "dungeon catacombs f7 m7 floor 7 goldor p3 device devices solver simon says arrow align item frames "
                        + "lights levers sharpshooter target practice shoot"));

        OPTIONS.add(new ConfigOption("device_solver_types", "Devices",
                "Pick which devices are solved.",
                ConfigCategory.SKYBLOCK,
                java.util.List.of(
                        toggle("Simon Says", c -> c.solveSimonSays, (c, v) -> c.solveSimonSays = v),
                        toggle("Arrow Align", c -> c.solveArrowAlign, (c, v) -> c.solveArrowAlign = v),
                        toggle("Lights", c -> c.solveLightsDevice, (c, v) -> c.solveLightsDevice = v),
                        toggle("Sharpshooter", c -> c.solveSharpshooter, (c, v) -> c.solveSharpshooter = v)
                ),
                "device devices pick which simon says arrow align lights levers sharpshooter"));
    }

    /**
     * Each HUD's own Blur and Smooth Font switches. Registered last and placed in the
     * HUD's feature by ConfigTree's PARENTS, so they come after its other settings. Chat and the
     * scoreboard have no feature of their own to go into, so they get one each.
     */
    private static void registerHudStyleToggles() {
        styleToggle("slayer_hud_blur", "Blur", ConfigCategory.SKYBLOCK, true,
                () -> AlpakaConfig.instance.slayerHudBlur, v -> AlpakaConfig.instance.slayerHudBlur = v);
        styleToggle("slayer_hud_smooth_font", "Smooth Font", ConfigCategory.SKYBLOCK, false,
                () -> AlpakaConfig.instance.slayerHudSmoothFont, v -> AlpakaConfig.instance.slayerHudSmoothFont = v);
        styleToggle("slayer_timer_blur", "Blur", ConfigCategory.SKYBLOCK, true,
                () -> AlpakaConfig.instance.slayerTimerHudBlur, v -> AlpakaConfig.instance.slayerTimerHudBlur = v);
        styleToggle("slayer_timer_smooth_font", "Smooth Font", ConfigCategory.SKYBLOCK, false,
                () -> AlpakaConfig.instance.slayerTimerHudSmoothFont, v -> AlpakaConfig.instance.slayerTimerHudSmoothFont = v);
        styleToggle("deployable_timer_blur", "Blur", ConfigCategory.SKYBLOCK, true,
                () -> AlpakaConfig.instance.deployableTimerHudBlur, v -> AlpakaConfig.instance.deployableTimerHudBlur = v);
        styleToggle("deployable_timer_smooth_font", "Smooth Font", ConfigCategory.SKYBLOCK, false,
                () -> AlpakaConfig.instance.deployableTimerHudSmoothFont, v -> AlpakaConfig.instance.deployableTimerHudSmoothFont = v);
        styleToggle("performance_hud_blur", "Blur", ConfigCategory.VISUALS, true,
                () -> AlpakaConfig.instance.performanceHudBlur, v -> AlpakaConfig.instance.performanceHudBlur = v);
        styleToggle("performance_hud_smooth_font", "Smooth Font", ConfigCategory.VISUALS, false,
                () -> AlpakaConfig.instance.performanceHudSmoothFont, v -> AlpakaConfig.instance.performanceHudSmoothFont = v);
        OPTIONS.add(new ConfigOption("inventory_hud_blur", "Blur",
                "Puts a blurred panel behind it, in the flat style. Needs Blur on under Design → HUD Style.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudBlur,
                v -> { AlpakaConfig.instance.inventoryHudBlur = v; AlpakaConfig.save(); },
                "blur blurred background panel inventory hud flat design > hud style"));
        styleToggle("notification_blur", "Blur", ConfigCategory.GENERAL, true,
                () -> AlpakaConfig.instance.notificationBlur, v -> AlpakaConfig.instance.notificationBlur = v);
        styleToggle("notification_smooth_font", "Smooth Font", ConfigCategory.GENERAL, false,
                () -> AlpakaConfig.instance.notificationSmoothFont, v -> AlpakaConfig.instance.notificationSmoothFont = v);
        styleToggle("dungeon_map_blur", "Blur", ConfigCategory.DUNGEON, true,
                () -> AlpakaConfig.instance.dungeonMapBlur, v -> AlpakaConfig.instance.dungeonMapBlur = v);
        styleToggle("dungeon_map_smooth_font", "Smooth Font", ConfigCategory.DUNGEON, false,
                () -> AlpakaConfig.instance.dungeonMapSmoothFont, v -> AlpakaConfig.instance.dungeonMapSmoothFont = v);

        styleToggle("custom_tab_blur", "Blur", ConfigCategory.VISUALS, true,
                () -> AlpakaConfig.instance.customTabBlur, v -> AlpakaConfig.instance.customTabBlur = v);
        styleToggle("custom_tab_smooth_font", "Smooth Font", ConfigCategory.VISUALS, false,
                () -> AlpakaConfig.instance.customTabSmoothFont, v -> AlpakaConfig.instance.customTabSmoothFont = v);

        styleToggle("chat_blur", "Blur", ConfigCategory.VISUALS, true,
                () -> AlpakaConfig.instance.chatBlurEnabled, v -> AlpakaConfig.instance.chatBlurEnabled = v);
        styleToggle("chat_smooth_font", "Smooth Font", ConfigCategory.VISUALS, false,
                () -> AlpakaConfig.instance.menuFontInChat, v -> { AlpakaConfig.instance.menuFontInChat = v; GuiFont.refreshChat(); });

        OPTIONS.add(new ConfigOption("Scoreboard", ConfigCategory.VISUALS));
        OPTIONS.add(new ConfigOption("scoreboard_alpaka_footer", "AlpakaAddons Footer",
                "Replaces www.hypixel.net at the bottom of the scoreboard with AlpakaAddons in a gradient.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scoreboardAlpakaFooter,
                v -> { AlpakaConfig.instance.scoreboardAlpakaFooter = v; AlpakaConfig.save(); },
                "scoreboard sidebar footer bottom line hypixel.net www address gradient alpakaaddons branding"));
        styleToggle("scoreboard_blur", "Blur", ConfigCategory.VISUALS, true,
                () -> AlpakaConfig.instance.scoreboardBlurEnabled, v -> AlpakaConfig.instance.scoreboardBlurEnabled = v);
        styleToggle("scoreboard_smooth_font", "Smooth Font", ConfigCategory.VISUALS, false,
                () -> AlpakaConfig.instance.menuFontInScoreboard, v -> AlpakaConfig.instance.menuFontInScoreboard = v);
    }

    private static void styleToggle(String id, String title, ConfigCategory category, boolean blur,
                                    java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
        String description = blur
                ? "Puts a rounded, blurred panel behind it. Needs Blur on under Design → HUD Style."
                : "Uses the Menu Font here instead of Minecraft's pixel font. Needs Smooth Font on under Design → HUD Style.";
        String keywords = blur ? "blur blurred background panel glass frosted rounded" : "smooth font menu font typeface text nunito";
        OPTIONS.add(new ConfigOption(id, title, description, category, getter,
                v -> { setter.accept(v); AlpakaConfig.save(); }, keywords));
    }

    /** A 0-100% volume slider for one custom sound, on top of the shared custom sounds volume. */
    private static ConfigOption soundVolume(String id, String title, String sound,
                                            Supplier<Float> getter, Consumer<Float> setter) {
        return new ConfigOption(id, title,
                "How loud this sound plays, on top of the custom sounds volume.",
                ConfigCategory.SOUND_MISC,
                getter,
                v -> { setter.accept(v); AlpakaConfig.save(); },
                0.0f, 1.0f, val -> String.format(Locale.ROOT, "%d%%", Math.round(val * 100)),
                sound + " sound volume loudness audio quiet loud percent");
    }

    private static void buildCategoryCache() {
        CATEGORY_CACHE.clear();
        for (ConfigCategory cat : ConfigCategory.values()) {
            List<ConfigOption> list = OPTIONS.stream()
                    .filter(opt -> opt.getCategory() == cat)
                    .collect(Collectors.toList());
            CATEGORY_CACHE.put(cat, java.util.Collections.unmodifiableList(list));
        }
    }

    /**
     * The filtered result of the last search term, per category.
     *
     * The config screen asks for this several times per frame - once to decide which categories have
     * any hits, once per category for its badge, once for the list it draws - and every one of those
     * used to re-filter the whole option list from scratch. The term only changes on a keystroke, so
     * the answer is worked out once per term and handed back afterwards.
     */
    private static final java.util.Map<ConfigCategory, List<ConfigOption>> SEARCH_CACHE =
            new java.util.EnumMap<>(ConfigCategory.class);
    private static String searchCacheQuery = null;

    public static List<ConfigOption> getOptions(ConfigCategory category, String searchQuery) {
        String normalized = ConfigOption.normalizeQuery(searchQuery);
        if (normalized.isEmpty()) {
            return CATEGORY_CACHE.getOrDefault(category, java.util.Collections.emptyList());
        }

        if (!normalized.equals(searchCacheQuery)) {
            SEARCH_CACHE.clear();
            searchCacheQuery = normalized;
        }

        List<ConfigOption> cached = SEARCH_CACHE.get(category);
        if (cached != null) return cached;

        List<ConfigOption> filtered = new ArrayList<>();
        for (ConfigOption option : CATEGORY_CACHE.getOrDefault(category, java.util.Collections.emptyList())) {
            if (option.matchesNormalized(normalized)) filtered.add(option);
        }
        List<ConfigOption> result = java.util.Collections.unmodifiableList(filtered);
        SEARCH_CACHE.put(category, result);
        return result;
    }

    /**
     * Brings every slider's stored value back into the slider's range, with one save for all of
     * them. A hand edit or an old file can hold a value the slider never offers - an index past the
     * end of a choice list, a negative size - and the features read the field, not the slider.
     *
     * Run once the game has started rather than while the settings load: a setter may act on the
     * game as well, and Warp Sound plays its preview. Returns how many values changed.
     */
    public static int clampStoredValues() {
        int changed = 0;
        AlpakaConfig.beginDeferredSaves();
        try {
            for (ConfigOption option : OPTIONS) {
                if (option.clampStoredValue()) changed++;
            }
        } finally {
            AlpakaConfig.endDeferredSaves();
        }
        if (changed > 0) {
            net.alpaka.addons.AlpakaAddons.LOGGER.warn("Brought {} setting(s) back into their slider's range", changed);
        }
        return changed;
    }

    /** How many non-header options a category holds for the current search term. */
    public static int countOptions(ConfigCategory category, String searchQuery) {
        int count = 0;
        for (ConfigOption option : getOptions(category, searchQuery)) {
            if (option.getType() != ConfigOption.Type.HEADER) count++;
        }
        return count;
    }
}
