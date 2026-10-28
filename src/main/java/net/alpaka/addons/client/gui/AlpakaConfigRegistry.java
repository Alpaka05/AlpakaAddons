package net.alpaka.addons.client.gui;

import java.util.Locale;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.damagetags.CustomDamageTagFeature;
import net.alpaka.addons.features.etherwarp.EtherwarpOverlayFeature;
import net.alpaka.addons.features.inventoryhud.InventoryHudVisibility;
import net.alpaka.addons.features.nametag.CustomNameTagFeature;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.playerscale.PlayerScaleFeature;
import net.alpaka.addons.features.sound.CustomSoundFeature;
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
    private static final java.util.Map<ConfigCategory, List<ConfigOption>> CATEGORY_CACHE = new java.util.EnumMap<>(ConfigCategory.class);

    static {
        registerAllOptions();
    }

    /** The Compact Chat Window value: "Back-to-back only", "45 s", "1 min", "2 min 30 s". */
    private static String formatWindow(float value) {
        int seconds = Math.round(value);
        if (seconds == 0) return "Back-to-back only";
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
        OPTIONS.add(new ConfigOption("allow_api_calls", "Allow Network Features",
                "Used for the mayor's XP buff, the main menu's player count and the update check.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.allowApiCalls,
                v -> { AlpakaConfig.instance.allowApiCalls = v; AlpakaConfig.save(); },
                "api network internet hypixel request mayor election offline privacy"));

        OPTIONS.add(new ConfigOption("update_notifications", "Update Notifications",
                "Tells you in chat when a new release of the mod is out, with a button to install it. Needs network features.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.updateNotificationsEnabled,
                v -> { AlpakaConfig.instance.updateNotificationsEnabled = v; AlpakaConfig.save(); },
                "update new version release github download install notify check outdated"));

        OPTIONS.add(new ConfigOption("update_stream", "Update Stream",
                "Which versions the update check tells you about: full releases only, or pre-releases as well.",
                ConfigCategory.GENERAL,
                () -> (float) UpdateChecker.stream(),
                v -> { AlpakaConfig.instance.updateStream = Math.round(v); AlpakaConfig.save(); UpdateChecker.streamChanged(); },
                0.0f, (float) (UpdateChecker.STREAM_NAMES.length - 1),
                val -> UpdateChecker.STREAM_NAMES[Math.round(val)],
                "update stream channel release pre-release prerelease beta alpha snapshot test version").choices(UpdateChecker.STREAM_NAMES));

        OPTIONS.add(new ConfigOption("notification_hold", "Notification Duration",
                "How long a notice stays in the corner before sliding away.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.notificationHoldSeconds,
                v -> { AlpakaConfig.instance.notificationHoldSeconds = v; AlpakaConfig.save(); },
                1.0f, 15.0f, val -> String.format(Locale.ROOT, "%.1fs", val),
                "notification duration hold seconds popup toast time linger"));

        OPTIONS.add(new ConfigOption("notification_corner", "Notification Position",
                "Where notices appear: a corner, or the top centre.",
                ConfigCategory.GENERAL,
                () -> (float) AlpakaConfig.instance.notificationCorner,
                v -> { AlpakaConfig.instance.notificationCorner = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (AlpakaNotifications.CORNER_NAMES.length - 1),
                val -> AlpakaNotifications.CORNER_NAMES[Math.round(val)],
                "notification corner position bottom top left right popup toast side").choices(AlpakaNotifications.CORNER_NAMES));

        // The menu's own typeface sits here rather than under Visuals: it changes the screen the
        // player is looking at right now, so it belongs on the tab that screen opens on.
        OPTIONS.add(new ConfigOption("menu_font", "Menu Font",
                "The typeface the Alpaka menus are written in.",
                ConfigCategory.GENERAL,
                () -> (float) GuiFont.selectedIndex(),
                v -> { AlpakaConfig.instance.menuFont = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (GuiFont.NAMES.length - 1),
                val -> GuiFont.NAMES[Math.round(val)],
                "menu font typeface text smooth modern inter poppins varela round outfit minecraft pixel gui bold").choices(GuiFont.NAMES));

        OPTIONS.add(ConfigOption.color("menu_accent_color", "Menu Accent Color",
                "The highlight color of every Alpaka menu.",
                ConfigCategory.GENERAL,
                c -> c.menuAccentColor,
                v -> { AlpakaConfig.instance.menuAccentColor = v; AlpakaConfig.save(); },
                "menu accent color theme custom picker border highlight gui teal gold cyan red green blue"));

        OPTIONS.add(new ConfigOption("custom_escape_menu", "Custom Escape Menu",
                "Enables custom styled Alpaka pause screen (ESC).",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customEscapeMenuEnabled,
                v -> { AlpakaConfig.instance.customEscapeMenuEnabled = v; AlpakaConfig.save(); },
                "custom escape menu pause screen esc button design visuals"));

        OPTIONS.add(new ConfigOption("custom_main_menu", "Custom Main Menu",
                "Enables Hypixel-themed custom title screen on launch.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customMainMenuEnabled,
                v -> { AlpakaConfig.instance.customMainMenuEnabled = v; AlpakaConfig.save(); },
                "custom main menu title screen hypixel start launch background GUI"));

        OPTIONS.add(new ConfigOption("custom_main_menu_whats_new", "What's New Card",
                "Shows the newest release's changes at the top right of the custom main menu. Needs Network Features.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.customMainMenuWhatsNew,
                v -> { AlpakaConfig.instance.customMainMenuWhatsNew = v; AlpakaConfig.save(); },
                "custom main menu what's new whats new release notes changelog changes update card news"));

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
                "Switches every feature off after confirmation. Their settings stay, so a feature switched back on is as it was.",
                ConfigCategory.GENERAL,
                "Disable All",
                parent -> Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                AlpakaConfig.instance.disableAllFeatures();
                            }
                            Minecraft.getInstance().gui.setScreen(parent);
                        },
                        Component.literal("§c§lDisable All Features"),
                        Component.literal("Are you sure you want to disable all features of Alpaka Addons?"),
                        Component.literal("§cDisable All"),
                        Component.literal("Cancel")
                )),
                "disable all features turn off reset everywhere confirm prompt"));

        // --- 1. VISUALS & RENDERING ---

        OPTIONS.add(new ConfigOption("World & Mobs", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("fullbright", "Fullbright",
                "Enables maximum brightness everywhere in the world without torches.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.fullbrightEnabled,
                v -> { AlpakaConfig.instance.fullbrightEnabled = v; AlpakaConfig.save(); },
                "gamma brightness light fullbright vision"));

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
                "Clears blaze noise in the Blaze slayer's zones, on SkyBlock only.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.cleanBlazeEnabled,
                v -> { AlpakaConfig.instance.cleanBlazeEnabled = v; AlpakaConfig.save(); },
                "blaze smoke particles fire nametag clear view slayer inferno crimson"));

        OPTIONS.add(new ConfigOption("clean_blaze_parts", "Clean Blaze Parts",
                "Pick what Clean Blaze hides.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Flame, smoke, angry villager and lava drip particles",
                                () -> AlpakaConfig.instance.cleanBlazeParticles,
                                v -> { AlpakaConfig.instance.cleanBlazeParticles = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Potion effect, enchanting and ender particles",
                                () -> AlpakaConfig.instance.cleanBlazeEffectParticles,
                                v -> { AlpakaConfig.instance.cleanBlazeEffectParticles = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Attunement-colored dust around the boss and demons",
                                () -> AlpakaConfig.instance.cleanBlazeAttunementDust,
                                v -> { AlpakaConfig.instance.cleanBlazeAttunementDust = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Burning on blazes, minibosses and demons",
                                () -> AlpakaConfig.instance.cleanBlazeFire,
                                v -> { AlpakaConfig.instance.cleanBlazeFire = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Fireballs",
                                () -> AlpakaConfig.instance.cleanBlazeFireballs,
                                v -> { AlpakaConfig.instance.cleanBlazeFireballs = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Lightning (bolt and sky flash)",
                                () -> AlpakaConfig.instance.cleanBlazeLightning,
                                v -> { AlpakaConfig.instance.cleanBlazeLightning = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Name tags (health included)",
                                () -> AlpakaConfig.instance.cleanBlazeNameTags,
                                v -> { AlpakaConfig.instance.cleanBlazeNameTags = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Wrong dagger attunement messages",
                                () -> AlpakaConfig.instance.cleanBlazeWrongDaggerChat,
                                v -> { AlpakaConfig.instance.cleanBlazeWrongDaggerChat = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Damage messages (Hellion Shield, boss hits)",
                                () -> AlpakaConfig.instance.cleanBlazeDamageChat,
                                v -> { AlpakaConfig.instance.cleanBlazeDamageChat = v; AlpakaConfig.save(); })
                ),
                "blaze flame smoke angry villager lava drip particles potion effect swirl enchant enchanting glyph ender portal witch spell attunement dust ashen spirit crystal auric hellion shield fire burning overlay fireball projectile lightning bolt flash thunder nametag name tag health smoldering miniboss demon chat message wrong dagger strike damage true damage beam reduced clean hide"));

        OPTIONS.add(new ConfigOption("blaze_fire_pits", "Fire Pit Highlight",
                "During your Blaze slayer boss, marks the fire pits on the floor with a glowing column.",
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

        OPTIONS.add(new ConfigOption("stop_blaze_spinning", "Stop Blaze Spinning",
                "Stops blaze rods from spinning around blaze mobs.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.stopBlazeSpinning,
                v -> { AlpakaConfig.instance.stopBlazeSpinning = v; AlpakaConfig.save(); },
                "blaze rods spin spinning rotation animation stop mob"));

        OPTIONS.add(new ConfigOption("hide_hurt_overlay", "Hide Damage Flash",
                "Hides the red flash on mobs and players when they take a hit.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideHurtOverlayEnabled,
                v -> { AlpakaConfig.instance.hideHurtOverlayEnabled = v; AlpakaConfig.save(); },
                "hurt damage red flash tint overlay hit mob entity hide"));

        OPTIONS.add(new ConfigOption("hide_mob_deaths", "Hide Mob Deaths",
                "Mobs and their name tags vanish the moment they die instead of tipping over first.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideMobDeathsEnabled,
                v -> { AlpakaConfig.instance.hideMobDeathsEnabled = v; AlpakaConfig.save(); },
                "hide mob death deaths animation tip over fall corpse vanish instant remove"));

        OPTIONS.add(new ConfigOption("hide_mob_death_smoke", "Hide Death Smoke",
                "Also removes the puff of smoke a dead mob leaves behind.",
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
                "Also draws your first-person hand and item in the third-person back view.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.renderHandInThirdPerson,
                v -> { AlpakaConfig.instance.renderHandInThirdPerson = v; AlpakaConfig.save(); },
                "hand view third person render"));

        OPTIONS.add(new ConfigOption("smooth_perspective", "Smooth Perspective",
                "Smooth transition animation when toggling perspective.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.smoothPerspectiveEnabled,
                v -> { AlpakaConfig.instance.smoothPerspectiveEnabled = v; AlpakaConfig.save(); },
                "smooth perspective camera transition f5 third person first person"));

        OPTIONS.add(new ConfigOption("disable_front_perspective", "Disable Front Perspective",
                "F5 skips the front view and only toggles 1st and 3rd person.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.disableFrontPerspective,
                v -> { AlpakaConfig.instance.disableFrontPerspective = v; AlpakaConfig.save(); },
                "disable front perspective skip view f5 third person first person camera"));

        OPTIONS.add(new ConfigOption("smooth_perspective_duration", "Transition Duration",
                "Duration of perspective switch transition in ms.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.smoothPerspectiveDurationMs,
                v -> { AlpakaConfig.instance.smoothPerspectiveDurationMs = Math.round(v); AlpakaConfig.save(); },
                100.0f, 1000.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "smooth perspective camera speed duration transition time ms"));

        OPTIONS.add(new ConfigOption("Interfaces", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("container_bg_opacity", "Container Background Opacity",
                "Darkening behind container GUIs. Vanilla is 75%, 0% is off.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.containerBgOpacity * 100.0f,
                v -> { AlpakaConfig.instance.containerBgOpacity = v / 100.0f; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Disabled (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "container background darkening opacity overlay tint inventory chest GUI"));

        OPTIONS.add(new ConfigOption("container_bg_fade_in", "Container Background Fade In",
                "Smoothly fades in the dark container background when opening GUIs.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.containerBgFadeInEnabled,
                v -> { AlpakaConfig.instance.containerBgFadeInEnabled = v; AlpakaConfig.save(); },
                "container background fade in smooth transition gui"));

        OPTIONS.add(new ConfigOption("container_bg_fade_duration", "Container Fade Speed",
                "Container fade-in duration in ms.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.containerBgFadeInDurationMs,
                v -> { AlpakaConfig.instance.containerBgFadeInDurationMs = Math.round(v); AlpakaConfig.save(); },
                50.0f, 1000.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "container background fade speed duration time ms transition"));

        OPTIONS.add(new ConfigOption("hide_inventory_effects", "Hide Effects",
                "Hides the list of active potion effects next to your inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.hideInventoryEffects,
                v -> { AlpakaConfig.instance.hideInventoryEffects = v; AlpakaConfig.save(); },
                "hide effects potion effect status buffs inventory list side absorption night vision haste strength god potion"));

        // The id keeps its old name so existing configs and search links still find the option.
        OPTIONS.add(new ConfigOption("inventory_snow", "Inventory Particles",
                "Particles drift over container screens, like chests and your inventory.",
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
                0.1f, 5.0f, val -> String.format(Locale.ROOT, "%.1fx", val),
                "snow speed animation winter velocity particles snow fall speed"));

        OPTIONS.add(new ConfigOption("inventory_snow_amount", "Particle Amount",
                "How many particles are on screen at once.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowAmount,
                v -> { AlpakaConfig.instance.inventorySnowAmount = v; AlpakaConfig.save(); },
                SnowOverlayRenderer.MIN_AMOUNT, SnowOverlayRenderer.MAX_AMOUNT,
                val -> String.format(Locale.ROOT, "%.1fx", val),
                "snow amount count density number flakes winter more less particles snowflake amount"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips", "Scrollable Tooltips",
                "Scroll a tooltip taller than the screen with the mouse wheel.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsEnabled,
                v -> { AlpakaConfig.instance.scrollableTooltipsEnabled = v; AlpakaConfig.save(); },
                "tooltip scroll wheel long tall item lore overflow cut off screen"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips_always", "Always Scrollable",
                "The wheel moves every tooltip, even one that fits on screen.",
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
                "The tooltip glides instead of jumping.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsSmooth,
                v -> { AlpakaConfig.instance.scrollableTooltipsSmooth = v; AlpakaConfig.save(); },
                "tooltip scroll smooth glide animation"));

        OPTIONS.add(new ConfigOption("scrollable_tooltips_invert", "Invert Direction",
                "Wheel down moves the tooltip down instead of reading further down it.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scrollableTooltipsInvert,
                v -> { AlpakaConfig.instance.scrollableTooltipsInvert = v; AlpakaConfig.save(); },
                "tooltip scroll invert reverse direction natural"));

        OPTIONS.add(new ConfigOption("persistent_item_name", "Keep Item Name Visible",
                "The held item's name above the hotbar no longer fades out.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.persistentItemNameEnabled,
                v -> { AlpakaConfig.instance.persistentItemNameEnabled = v; AlpakaConfig.save(); },
                "item name hotbar held tool highlight fade permanent always show"));

        OPTIONS.add(new ConfigOption("old_master_stars", "Old Master Stars",
                "Master stars turn the stars red again, as they used to, instead of a number behind them. In tooltips and above the hotbar.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.oldMasterStarsEnabled,
                v -> { AlpakaConfig.instance.oldMasterStarsEnabled = v; AlpakaConfig.save(); },
                "master stars red dungeon star number revert old item name tooltip hotbar"));

        OPTIONS.add(new ConfigOption("wide_health_bar", "Wide Health Bar",
                "Hides the hunger and armor bars and joins both heart rows into one long row.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.wideHealthBarEnabled,
                v -> { AlpakaConfig.instance.wideHealthBarEnabled = v; AlpakaConfig.save(); },
                "health hearts hunger food armor bar hide long wide single row absorption hotbar skyblock life"));

        // Everything that changes how the chat looks, under one main switch.
        OPTIONS.add(new ConfigOption("Better Chat", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("better_chat", "Better Chat",
                "Makes the chat look better: Smooth Chat, the opening animation, smooth scrolling and the blurred background. Each can be turned off below.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.betterChatEnabled,
                v -> { AlpakaConfig.instance.betterChatEnabled = v; AlpakaConfig.save(); },
                "better chat look design style smooth blur background animation scroll opening main switch"));

        OPTIONS.add(new ConfigOption("smooth_chat", "Smooth Chat",
                "New messages slide in and fade in instead of appearing at once.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.smoothChatEnabled,
                v -> { AlpakaConfig.instance.smoothChatEnabled = v; AlpakaConfig.save(); },
                "smooth chat animation slide fade new message appear soft patcher"));

        OPTIONS.add(new ConfigOption("smooth_chat_strength", "Smooth Chat Strength",
                "How long the slide takes; higher is slower and more visible.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.smoothChatStrength,
                v -> { AlpakaConfig.instance.smoothChatStrength = Math.round(v); AlpakaConfig.save(); },
                1.0f, 10.0f, v -> Math.round(v) * 60 + " ms",
                "smooth chat strength speed duration animation slow fast slider"));

        OPTIONS.add(new ConfigOption("chat_open_animation", "Opening Animation",
                "Opening the chat unrolls it smoothly from the input line instead of showing it at once.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatOpenAnimationEnabled,
                v -> { AlpakaConfig.instance.chatOpenAnimationEnabled = v; AlpakaConfig.save(); },
                "chat opening open animation smooth slide unroll fade appear input"));

        OPTIONS.add(new ConfigOption("chat_smooth_scroll", "Smooth Chat Scrolling",
                "Scrolling the chat glides instead of jumping line by line.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatSmoothScrollEnabled,
                v -> { AlpakaConfig.instance.chatSmoothScrollEnabled = v; AlpakaConfig.save(); },
                "smooth chat scroll scrolling wheel glide peek animation"));

        OPTIONS.add(new ConfigOption("chat_blur", "Blurred Chat Background",
                "One rounded, blurred panel behind the chat instead of flat line boxes. The action bar stays readable behind it.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatBlurEnabled,
                v -> { AlpakaConfig.instance.chatBlurEnabled = v; AlpakaConfig.save(); },
                "chat blur background rounded corners panel glass frosted clean padding smooth"));

        OPTIONS.add(new ConfigOption("chat_blur_strength", "Blur Strength",
                "How strongly the world behind the chat is blurred. Also applies to the scoreboard, the Inventory HUD and the command wheel.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatBlurStrength,
                v -> { AlpakaConfig.instance.chatBlurStrength = v; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "None (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "chat blur strength radius amount intensity frosted glass background lighter clearer sharp"));

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

        OPTIONS.add(new ConfigOption("Chat", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("chat_search", "Chat Search",
                "Ctrl+F (Cmd+F on Mac) in the chat filters it to messages containing your text.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatSearchEnabled,
                v -> { AlpakaConfig.instance.chatSearchEnabled = v; AlpakaConfig.save(); },
                "chat search find filter ctrl f messages history lookup"));

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

        OPTIONS.add(new ConfigOption("mention_notification", "Chat Mentions",
                "Slides in a notice when somebody says your name in chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.mentionNotificationEnabled,
                v -> { AlpakaConfig.instance.mentionNotificationEnabled = v; AlpakaConfig.save(); },
                "mention notification popup toast chat name ping alert"));

        OPTIONS.add(new ConfigOption("mention_ignore_server", "Only From Players",
                "Ignores your name in lines from NPCs, bosses and the server, so only a real player's message notifies you.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.mentionIgnoreServer,
                v -> { AlpakaConfig.instance.mentionIgnoreServer = v; AlpakaConfig.save(); },
                "mention npc server hypixel skyblock boss players only real ignore filter"));

        OPTIONS.add(new ConfigOption("chat_peek", "Chat Peek",
                "Hold the Peek Chat key to read and scroll the full chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatPeekEnabled,
                v -> { AlpakaConfig.instance.chatPeekEnabled = v; AlpakaConfig.save(); },
                "chat peek hold key read scroll history keybind open bigger larger"));

        OPTIONS.add(new ConfigOption("chat_peek_in_menus", "Chat Peek In Menus",
                "Peek Chat also works while an inventory or menu is open.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatPeekInMenus,
                v -> { AlpakaConfig.instance.chatPeekInMenus = v; AlpakaConfig.save(); },
                "chat peek menu inventory gui screen container open hold key foreground"));

        OPTIONS.add(new ConfigOption("chat_tabs", "Chat Tabs",
                "All, Party, Guild, Co-op, PMs and NPCs tabs above the chat input.",
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

        OPTIONS.add(new ConfigOption("chat_tabs_send_to_channel", "Send To Tab Channel",
                "Messages typed on a channel tab go to that channel.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatTabsSendToChannel,
                v -> { AlpakaConfig.instance.chatTabsSendToChannel = v; AlpakaConfig.save(); },
                "chat tabs send channel prefix party guild coop reply pc gc cc r"));

        OPTIONS.add(new ConfigOption("chat_tabs_tab_key", "Tabs While Peeking & Tab Key",
                "Shows the tabs while peeking. Tab on an empty line, or Ctrl+Tab, cycles them.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatTabsTabKey,
                v -> { AlpakaConfig.instance.chatTabsTabKey = v; AlpakaConfig.save(); },
                "chat tabs tab key cycle switch next peek player list tablist"));

        OPTIONS.add(new ConfigOption("chat_tabs_keep_after_close", "Keep Tab After Closing Chat",
                "The in-game chat stays filtered to the chosen tab. Off: back to All.",
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

        OPTIONS.add(new ConfigOption("Screenshots", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("better_screenshot_message", "Better Screenshots",
                "A preview in the corner and Open, Copy and Delete buttons for every screenshot.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.betterScreenshotMessageEnabled,
                v -> { AlpakaConfig.instance.betterScreenshotMessageEnabled = v; AlpakaConfig.save(); },
                "better screenshots screenshot message notice open copy delete clipboard f2 buttons preview"));

        OPTIONS.add(new ConfigOption("screenshot_preview", "Screen Preview",
                "The screenshot shrinks into the corner for a moment, like on a phone.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.screenshotPreviewEnabled,
                v -> { AlpakaConfig.instance.screenshotPreviewEnabled = v; AlpakaConfig.save(); },
                "screenshot preview thumbnail corner flash animation ios phone"));

        OPTIONS.add(new ConfigOption("screenshot_preview_seconds", "Preview Duration",
                "How long the preview stays in the corner.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.screenshotPreviewSeconds,
                v -> { AlpakaConfig.instance.screenshotPreviewSeconds = Math.round(v * 2.0f) / 2.0f; AlpakaConfig.save(); },
                1.0f, 10.0f,
                val -> String.format(java.util.Locale.ROOT, "%.1f s", val),
                "screenshot preview duration time seconds"));

        OPTIONS.add(new ConfigOption("screenshot_preview_size", "Preview Size",
                "The preview's width, as a share of the screen's.",
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
                "Open, Copy and Delete buttons on the screenshot notice in chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.screenshotChatButtons,
                v -> { AlpakaConfig.instance.screenshotChatButtons = v; AlpakaConfig.save(); },
                "screenshot message notice open copy delete buttons chat line"));

        OPTIONS.add(new ConfigOption("screenshot_line_parts", "Line Parts",
                "Which parts the screenshot line shows.",
                ConfigCategory.VISUALS,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("File name",
                                () -> AlpakaConfig.instance.screenshotShowFileName,
                                v -> { AlpakaConfig.instance.screenshotShowFileName = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Open button",
                                () -> AlpakaConfig.instance.screenshotShowOpen,
                                v -> { AlpakaConfig.instance.screenshotShowOpen = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Folder button",
                                () -> AlpakaConfig.instance.screenshotShowFolder,
                                v -> { AlpakaConfig.instance.screenshotShowFolder = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Copy button",
                                () -> AlpakaConfig.instance.screenshotShowCopy,
                                v -> { AlpakaConfig.instance.screenshotShowCopy = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Delete button",
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

        OPTIONS.add(new ConfigOption("Player Model", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("player_model_enabled", "Enable Player Model HUD",
                "Displays a miniature 3D player avatar on the HUD.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelEnabled,
                v -> { AlpakaConfig.instance.playerModelEnabled = v; AlpakaConfig.save(); },
                "player model hud avatar skin 3d display preview"));

        OPTIONS.add(new ConfigOption("player_model_only_actions", "Only Show On Action",
                "Only displays avatar while sprinting, flying, or swinging.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelOnlyActions,
                v -> { AlpakaConfig.instance.playerModelOnlyActions = v; AlpakaConfig.save(); },
                "only action sprint fly swing dynamic hide avatar"));

        OPTIONS.add(new ConfigOption("player_model_disable_movement", "Disable Movement Sway",
                "Fixes avatar position without walking or swimming sway motion.",
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
                "Hides armor pieces from rendering on the player model HUD.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelHideArmor,
                v -> { AlpakaConfig.instance.playerModelHideArmor = v; AlpakaConfig.save(); },
                "player model hide armor helmet chestplate leggings boots overlay"));

        OPTIONS.add(new ConfigOption("player_model_show_in_guis", "Show in GUIs / Menus",
                "Renders player model HUD even when container or menu GUIs are open.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.playerModelShowInGuis,
                v -> { AlpakaConfig.instance.playerModelShowInGuis = v; AlpakaConfig.save(); },
                "player model show in guis menus screen open container inventory HUD"));

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

        OPTIONS.add(new ConfigOption("Inventory HUD", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("inventory_hud_enabled", "Enable Inventory HUD",
                "Shows your 27 inventory slots on screen without opening the inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudEnabled,
                v -> { AlpakaConfig.instance.inventoryHudEnabled = v; AlpakaConfig.save(); },
                "inventory hud overlay slots items show always screen backpack"));

        OPTIONS.add(new ConfigOption("inventory_hud_attach", "Attach Above Hotbar",
                "Centres the inventory just above the hotbar. Off = free position.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudAttachToHotbar,
                v -> { AlpakaConfig.instance.inventoryHudAttachToHotbar = v; AlpakaConfig.save(); },
                "inventory hud hotbar attach above merge dock position free"));

        OPTIONS.add(new ConfigOption("inventory_hud_visibility", "Visibility",
                "When the inventory shows: always, briefly when an item is picked up or dropped, or only when you press the Inventory HUD key. The key is unbound by default; set it in Controls.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.inventoryHudVisibility,
                v -> { AlpakaConfig.instance.inventoryHudVisibility = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (InventoryHudVisibility.NAMES.length - 1),
                val -> InventoryHudVisibility.NAMES[Math.round(val)],
                "inventory hud always visible permanent show on pickup drop item change peek temporary keybind key press toggle hidden unbound controls mode")
                .choices(InventoryHudVisibility.NAMES));

        OPTIONS.add(new ConfigOption("inventory_hud_vanilla_texture", "Chest Style",
                "Draws the panel as a three-row chest, using your resource pack.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudVanillaTexture,
                v -> { AlpakaConfig.instance.inventoryHudVanillaTexture = v; AlpakaConfig.save(); },
                "inventory texture resource pack vanilla slots real look skin"));

        OPTIONS.add(new ConfigOption("inventory_hud_blur", "Blur Background",
                "Shows the world blurred behind the flat panel.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudBlur,
                v -> { AlpakaConfig.instance.inventoryHudBlur = v; AlpakaConfig.save(); },
                "inventory hud blur frosted glass background panel flat"));

        OPTIONS.add(new ConfigOption("inventory_hud_frame", "Show Frame",
                "Draws the gradient frame around the flat panel.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudFrame,
                v -> { AlpakaConfig.instance.inventoryHudFrame = v; AlpakaConfig.save(); },
                "inventory hud frame border outline gradient rahmen hide remove flat panel"));

        OPTIONS.add(ConfigOption.color("inventory_hud_frame_start_color", "Frame Start Color",
                "First color of the flat panel's frame gradient.",
                ConfigCategory.VISUALS,
                c -> c.inventoryHudFrameStart,
                v -> { AlpakaConfig.instance.inventoryHudFrameStart = v; AlpakaConfig.save(); },
                "inventory hud frame border outline gradient color start first picker"));

        OPTIONS.add(ConfigOption.color("inventory_hud_frame_end_color", "Frame End Color",
                "Second color of the flat panel's frame gradient.",
                ConfigCategory.VISUALS,
                c -> c.inventoryHudFrameEnd,
                v -> { AlpakaConfig.instance.inventoryHudFrameEnd = v; AlpakaConfig.save(); },
                "inventory hud frame border outline gradient color end second picker"));

        OPTIONS.add(new ConfigOption("inventory_hud_bg_opacity", "Background Opacity",
                "Backdrop tint behind the slots. 0% leaves only the frame.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudBackgroundOpacity,
                v -> { AlpakaConfig.instance.inventoryHudBackgroundOpacity = v; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Off (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "inventory hud background opacity transparent blur tint backdrop strength"));


        OPTIONS.add(new ConfigOption("Scoreboard", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("scoreboard_blur", "Blurred Scoreboard Background",
                "One rounded, blurred panel with a soft shadow behind the scoreboard. Uses the chat's Blur Strength.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scoreboardBlurEnabled,
                v -> { AlpakaConfig.instance.scoreboardBlurEnabled = v; AlpakaConfig.save(); },
                "scoreboard sidebar blur background rounded panel glass frosted shadow"));

        OPTIONS.add(new ConfigOption("scoreboard_alpaka_footer", "AlpakaAddons Scoreboard Footer",
                "Replaces www.hypixel.net at the bottom of the scoreboard with AlpakaAddons in a gradient.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.scoreboardAlpakaFooter,
                v -> { AlpakaConfig.instance.scoreboardAlpakaFooter = v; AlpakaConfig.save(); },
                "scoreboard sidebar footer bottom line hypixel.net www address gradient alpakaaddons branding"));

        // --- 2. ITEM VIEWMODEL ---

        OPTIONS.add(new ConfigOption("item_size_feature", "Enable Viewmodel Modifiers",
                "Master toggle for custom hand and item adjustments.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSizeFeatureEnabled,
                v -> { AlpakaConfig.instance.itemSizeFeatureEnabled = v; AlpakaConfig.save(); },
                "viewmodel item size scale enable custom hand"));

        OPTIONS.add(new ConfigOption("Viewmodel Adjustments", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("item_scale", "Item Scale (Size)",
                "Scale multiplier for held items in hand.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemScale,
                v -> { AlpakaConfig.instance.itemScale = v; AlpakaConfig.save(); },
                0.2f, 2.5f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "scale size item hand viewmodel big small"));

        OPTIONS.add(new ConfigOption("item_x_offset", "X Offset (Left/Right)",
                "Horizontal offset of the hand to the left or right.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemXOffset,
                v -> { AlpakaConfig.instance.itemXOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format(Locale.ROOT, "%.2f", val),
                "position x offset left right hand"));

        OPTIONS.add(new ConfigOption("item_y_offset", "Y Offset (Up/Down)",
                "Vertical offset of the hand up or down.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemYOffset,
                v -> { AlpakaConfig.instance.itemYOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format(Locale.ROOT, "%.2f", val),
                "position y offset up down height hand"));

        OPTIONS.add(new ConfigOption("item_z_offset", "Z Offset (Forward/Back)",
                "Depth offset of the hand forward or backward.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemZOffset,
                v -> { AlpakaConfig.instance.itemZOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format(Locale.ROOT, "%.2f", val),
                "position z offset forward back depth hand"));

        OPTIONS.add(new ConfigOption("item_rotation_x", "Rotation Pitch (X)",
                "Pitch tilt of the hand forward or backward.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationX,
                v -> { AlpakaConfig.instance.itemRotationX = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "rotation pitch x angle tilt"));

        OPTIONS.add(new ConfigOption("item_rotation_y", "Rotation Yaw (Y)",
                "Yaw turn of the hand side to side.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationY,
                v -> { AlpakaConfig.instance.itemRotationY = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "rotation yaw y angle turn"));

        OPTIONS.add(new ConfigOption("item_rotation_z", "Rotation Roll (Z)",
                "Roll tilt of the hand side to side.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationZ,
                v -> { AlpakaConfig.instance.itemRotationZ = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "rotation roll z angle side tilt"));

        OPTIONS.add(new ConfigOption("item_swing_speed", "Swing Speed",
                "Speed multiplier for swing animations.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingSpeed,
                v -> { AlpakaConfig.instance.itemSwingSpeed = v; AlpakaConfig.save(); },
                0.1f, 1.5f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "swing speed attack animation fast slow"));

        OPTIONS.add(new ConfigOption("item_sway_disabled", "Disable Hand Sway",
                "Disables hand sway when turning the camera.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwayDisabled,
                v -> { AlpakaConfig.instance.itemSwayDisabled = v; AlpakaConfig.save(); },
                "sway hand motion movement camera steady"));

        OPTIONS.add(new ConfigOption("item_lighting_disabled", "Disable Item Lighting",
                "Removes the directional shading on the held item so it stays evenly lit while moving or turning.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemLightingDisabled,
                v -> { AlpakaConfig.instance.itemLightingDisabled = v; AlpakaConfig.save(); },
                "lighting light shading shadow flat unlit held item hand flicker"));

        OPTIONS.add(new ConfigOption("item_no_equip", "Disable Re-equip Animation",
                "Disables lower re-equip animation when switching slots.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemNoEquipEnabled,
                v -> { AlpakaConfig.instance.itemNoEquipEnabled = v; AlpakaConfig.save(); },
                "no equip reequip animation slot switch instant"));

        OPTIONS.add(new ConfigOption("item_ignore_empty_hand", "Ignore Empty Hand",
                "Applies viewmodel changes only when holding an item.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemIgnoreEmptyHandEnabled,
                v -> { AlpakaConfig.instance.itemIgnoreEmptyHandEnabled = v; AlpakaConfig.save(); },
                "ignore empty hand fist bare item only"));

        OPTIONS.add(new ConfigOption("Swing Customizations", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("swing_test", "Test Swing",
                "Swings the item in your hand once, only on your screen: no hit, nothing sent to the server.",
                ConfigCategory.VIEWMODEL,
                "Test Swing",
                parent -> net.alpaka.addons.features.viewmodel.SwingPreview.swing(),
                "swing test preview try animation hand item").buttonOnly());

        OPTIONS.add(new ConfigOption("swing_drift_x", "Swing Drift X",
                "Lateral swing drift displacement.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftX,
                v -> { AlpakaConfig.instance.swingDriftX = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format(Locale.ROOT, "%.1f", val),
                "swing drift x lateral motion"));

        OPTIONS.add(new ConfigOption("swing_drift_y", "Swing Drift Y",
                "Vertical swing drift displacement.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftY,
                v -> { AlpakaConfig.instance.swingDriftY = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format(Locale.ROOT, "%.1f", val),
                "swing drift y vertical motion"));

        OPTIONS.add(new ConfigOption("swing_drift_z", "Swing Drift Z",
                "Depth swing drift displacement.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftZ,
                v -> { AlpakaConfig.instance.swingDriftZ = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format(Locale.ROOT, "%.1f", val),
                "swing drift z depth motion"));

        OPTIONS.add(new ConfigOption("swing_arc_x", "Swing Arc Pitch",
                "Additional swing rotation angle around X axis.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcX,
                v -> { AlpakaConfig.instance.swingArcX = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "swing arc pitch angle rot"));

        OPTIONS.add(new ConfigOption("swing_arc_y", "Swing Arc Yaw",
                "Additional swing rotation angle around Y axis.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcY,
                v -> { AlpakaConfig.instance.swingArcY = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "swing arc yaw angle rot"));

        OPTIONS.add(new ConfigOption("swing_arc_z", "Swing Arc Roll",
                "Additional swing rotation angle around Z axis.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcZ,
                v -> { AlpakaConfig.instance.swingArcZ = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format(Locale.ROOT, "%.0f°", val),
                "swing arc roll angle rot"));

        OPTIONS.add(new ConfigOption("item_swing_trans_disable", "Disable Swing Translation",
                "Disables position translation during swings.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingTranslationDisabled,
                v -> { AlpakaConfig.instance.itemSwingTranslationDisabled = v; AlpakaConfig.save(); },
                "disable swing translation movement offset shift"));

        OPTIONS.add(new ConfigOption("item_swing_always_finish", "Always Finish Swing",
                "Finishes every swing and chains held attacks without a pause.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingAlwaysFinishEnabled,
                v -> { AlpakaConfig.instance.itemSwingAlwaysFinishEnabled = v; AlpakaConfig.save(); },
                "always finish swing complete attack animation"));

        OPTIONS.add(new ConfigOption("Starlight", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("item_starlight", "Starlight Effect",
                "A drifting, twinkling galaxy over the held item, on every item or in place of the enchantment glint.",
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
                "starlight mode glint enchant replace enchanted only every item")
                .choices(net.alpaka.addons.features.viewmodel.HandItemStarlightFeature.MODE_NAMES));

        OPTIONS.add(ConfigOption.color("item_starlight_color", "Starlight Color",
                "The color of the nebula; the stars are tinged with it.",
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
                "The held item leaves a fading trail while swinging.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurEnabled,
                v -> { AlpakaConfig.instance.itemMotionBlurEnabled = v; AlpakaConfig.save(); },
                "motion blur trail ghost smear swing smooth afterimage fight"));

        OPTIONS.add(new ConfigOption("item_motion_blur_length", "Blur Length",
                "How far behind the item the trail reaches.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurLength,
                v -> { AlpakaConfig.instance.itemMotionBlurLength = (float) Math.round(v); AlpakaConfig.save(); },
                ItemMotionBlurFeature.MIN_LENGTH_MS, ItemMotionBlurFeature.MAX_LENGTH_MS,
                val -> String.format(Locale.ROOT, "%.0f ms", val),
                "motion blur trail length duration time long short"));

        OPTIONS.add(new ConfigOption("item_motion_blur_opacity", "Blur Opacity",
                "How strongly the trail is drawn.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurOpacity,
                v -> { AlpakaConfig.instance.itemMotionBlurOpacity = Math.round(v * 100.0f) / 100.0f; AlpakaConfig.save(); },
                0.05f, 1.0f, val -> String.format(Locale.ROOT, "%.0f%%", val * 100.0f),
                "motion blur trail opacity alpha strength transparency intensity"));

        OPTIONS.add(new ConfigOption("item_motion_blur_steps", "Blur Steps",
                "Number of ghost copies that make up the trail.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemMotionBlurSteps,
                v -> { AlpakaConfig.instance.itemMotionBlurSteps = (float) Math.round(v); AlpakaConfig.save(); },
                (float) ItemMotionBlurFeature.MIN_STEPS, (float) ItemMotionBlurFeature.MAX_STEPS,
                val -> String.format(Locale.ROOT, "%.0f", val),
                "motion blur trail steps copies ghosts quality samples"));

        OPTIONS.add(new ConfigOption("item_viewmodel_reset", "Reset Viewmodel Settings",
                "Resets every viewmodel and swing value. Presets are kept.",
                ConfigCategory.VIEWMODEL,
                "Reset",
                parent -> Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                AlpakaConfig.instance.resetItemViewmodel();
                                try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                            }
                            Minecraft.getInstance().gui.setScreen(parent);
                        },
                        Component.literal("§c§lReset Viewmodel Settings"),
                        Component.literal("Are you sure you want to reset every viewmodel and swing value to its default? Saved presets are left untouched."),
                        Component.literal("§cReset"),
                        Component.literal("Cancel")
                )),
                "reset viewmodel default defaults revert restore clear hand item size offset rotation swing"));

        OPTIONS.add(new ConfigOption("Viewmodel Presets", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("load_preset_1", "Load Preset 1",
                "Applies viewmodel and swing settings from Preset 1.",
                ConfigCategory.VIEWMODEL,
                "Load P1",
                parent -> {
                    AlpakaConfig.instance.loadPreset(0);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset load 1 slot viewmodel hand size offset").buttonOnly());

        OPTIONS.add(new ConfigOption("save_preset_1", "Save to Preset 1",
                "Saves your current viewmodel and swing settings to Preset 1.",
                ConfigCategory.VIEWMODEL,
                "Save P1",
                parent -> {
                    AlpakaConfig.instance.savePreset(0);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset save 1 slot viewmodel hand size offset").confirmFirst());

        OPTIONS.add(new ConfigOption("load_preset_2", "Load Preset 2",
                "Applies viewmodel and swing settings from Preset 2.",
                ConfigCategory.VIEWMODEL,
                "Load P2",
                parent -> {
                    AlpakaConfig.instance.loadPreset(1);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset load 2 slot viewmodel hand size offset").buttonOnly());

        OPTIONS.add(new ConfigOption("save_preset_2", "Save to Preset 2",
                "Saves your current viewmodel and swing settings to Preset 2.",
                ConfigCategory.VIEWMODEL,
                "Save P2",
                parent -> {
                    AlpakaConfig.instance.savePreset(1);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset save 2 slot viewmodel hand size offset").confirmFirst());

        OPTIONS.add(new ConfigOption("load_preset_3", "Load Preset 3",
                "Applies viewmodel and swing settings from Preset 3.",
                ConfigCategory.VIEWMODEL,
                "Load P3",
                parent -> {
                    AlpakaConfig.instance.loadPreset(2);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset load 3 slot viewmodel hand size offset").buttonOnly());

        OPTIONS.add(new ConfigOption("save_preset_3", "Save to Preset 3",
                "Saves your current viewmodel and swing settings to Preset 3.",
                ConfigCategory.VIEWMODEL,
                "Save P3",
                parent -> {
                    AlpakaConfig.instance.savePreset(2);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset save 3 slot viewmodel hand size offset").confirmFirst());


        // --- 3. BLOCK OVERLAY ---

        OPTIONS.add(new ConfigOption("block_overlay_enabled", "Enable Block Overlay",
                "Enables custom targeted block highlight rendering.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOverlayEnabled,
                v -> { AlpakaConfig.instance.blockOverlayEnabled = v; AlpakaConfig.save(); },
                "block overlay outline highlight render custom enable"));

        OPTIONS.add(new ConfigOption("Outline", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_outline_enabled", "Block Outline",
                "Draws outline borders around targeted blocks.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOutlineEnabled,
                v -> { AlpakaConfig.instance.blockOutlineEnabled = v; AlpakaConfig.save(); },
                "block outline border edge line draw"));

        OPTIONS.add(new ConfigOption("block_outline_thickness", "Outline Thickness",
                "Thickness of the block outline frame.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOutlineThickness,
                v -> { AlpakaConfig.instance.blockOutlineThickness = v; AlpakaConfig.save(); },
                0.5f, 10.0f, val -> String.format(Locale.ROOT, "%.1fpx", val),
                "outline thickness line width border size"));

        OPTIONS.add(ConfigOption.color("block_outline_color", "Outline Color",
                "Select color for the block outline.",
                ConfigCategory.BLOCK_OVERLAY,
                c -> c.blockOutlineColor,
                v -> { AlpakaConfig.instance.blockOutlineColor = v; AlpakaConfig.save(); },
                "block outline color picker rgb alpha cyan red blue"));

        OPTIONS.add(new ConfigOption("Fill", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_fill_enabled", "Block Fill",
                "Fills target block faces with transparent color.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockFillEnabled,
                v -> { AlpakaConfig.instance.blockFillEnabled = v; AlpakaConfig.save(); },
                "block fill solid transparent face shading color"));

        OPTIONS.add(ConfigOption.color("block_fill_color", "Fill Color",
                "Select fill color and transparency for targeted blocks.",
                ConfigCategory.BLOCK_OVERLAY,
                c -> c.blockFillColor,
                v -> { AlpakaConfig.instance.blockFillColor = v; AlpakaConfig.save(); },
                "block fill color picker rgb alpha transparency tint"));

        OPTIONS.add(new ConfigOption("Animation", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_fade_in", "Smooth Fade In",
                "Smoothly fades in block overlay when targeting a new block.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockFadeInEnabled,
                v -> { AlpakaConfig.instance.blockFadeInEnabled = v; AlpakaConfig.save(); },
                "block overlay smooth fade in transition animation visuals"));

        OPTIONS.add(new ConfigOption("block_fade_in_duration", "Fade In Duration",
                "Duration of block overlay fade in transition in ms.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> (float) AlpakaConfig.instance.blockFadeInDurationMs,
                v -> { AlpakaConfig.instance.blockFadeInDurationMs = Math.round(v); AlpakaConfig.save(); },
                50.0f, 1000.0f, val -> String.format(Locale.ROOT, "%d ms", Math.round(val)),
                "block fade in duration speed time ms transition overlay"));

        OPTIONS.add(new ConfigOption("block_chroma_enabled", "Chroma RGB Effect",
                "Dynamic rainbow color cycle gradient for block overlay.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockChromaEnabled,
                v -> { AlpakaConfig.instance.blockChromaEnabled = v; AlpakaConfig.save(); },
                "chroma rgb rainbow color cycle gradient block"));

        OPTIONS.add(new ConfigOption("block_chroma_speed", "Chroma Speed",
                "Speed multiplier for rainbow color cycling.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockChromaSpeed,
                v -> { AlpakaConfig.instance.blockChromaSpeed = v; AlpakaConfig.save(); },
                0.1f, 2.0f, val -> String.format(Locale.ROOT, "%.1fx", val),
                "chroma speed rainbow velocity rgb cycle"));

        OPTIONS.add(new ConfigOption("Filters", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_ignore_depth", "Draw On Top",
                "Draws the outline of the block you aim at over anything in front of it. Only that block.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockIgnoreDepth,
                v -> { AlpakaConfig.instance.blockIgnoreDepth = v; AlpakaConfig.save(); },
                "ignore depth on top over walls see through blocks outline"));

        OPTIONS.add(new ConfigOption("block_ignore_plants", "Ignore Plants",
                "Disables block highlighting when targeting foliage like grass, flowers, or crops.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockIgnorePlants,
                v -> { AlpakaConfig.instance.blockIgnorePlants = v; AlpakaConfig.save(); },
                "ignore plants foliage grass flowers crops outline disable overlay"));

        OPTIONS.add(new ConfigOption("block_hide_on_etherwarp", "Hide While Aiming Etherwarp",
                "Hides the overlay while sneaking with an Etherwarp item.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockHideOnEtherwarp,
                v -> { AlpakaConfig.instance.blockHideOnEtherwarp = v; AlpakaConfig.save(); },
                "etherwarp ether transmission conduit aspect void end teleport sneak hide overlay skyblock"));



        // --- 5. SKYBLOCK ---

        OPTIONS.add(new ConfigOption("Etherwarp Overlay", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("etherwarp_overlay", "Etherwarp Overlay",
                "Marks the block you will land on while sneaking with an Etherwarp item.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayEnabled,
                v -> { AlpakaConfig.instance.etherwarpOverlayEnabled = v; AlpakaConfig.save(); },
                "etherwarp ether transmission conduit aspect void teleport target block highlight overlay guess"));

        OPTIONS.add(ConfigOption.color("etherwarp_overlay_color", "Target Color",
                "Box color when the warp will work.",
                ConfigCategory.SKYBLOCK,
                c -> c.etherwarpOverlayColor,
                v -> { AlpakaConfig.instance.etherwarpOverlayColor = v; AlpakaConfig.save(); },
                "etherwarp color target box gold"));

        OPTIONS.add(new ConfigOption("etherwarp_show_fail", "Show Blocked Targets",
                "Also marks a target with no room to stand, in the fail color.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayShowFail,
                v -> { AlpakaConfig.instance.etherwarpOverlayShowFail = v; AlpakaConfig.save(); },
                "etherwarp fail blocked invalid red no space"));

        OPTIONS.add(ConfigOption.color("etherwarp_fail_color", "Fail Color",
                "Box color when the warp will be refused.",
                ConfigCategory.SKYBLOCK,
                c -> c.etherwarpOverlayFailColor,
                v -> { AlpakaConfig.instance.etherwarpOverlayFailColor = v; AlpakaConfig.save(); },
                "etherwarp fail color red blocked"));

        OPTIONS.add(new ConfigOption("etherwarp_fill", "Fill Target Block",
                "Fills the box translucently instead of drawing only its edges.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayFill,
                v -> { AlpakaConfig.instance.etherwarpOverlayFill = v; AlpakaConfig.save(); },
                "etherwarp fill filled box solid"));

        OPTIONS.add(new ConfigOption("etherwarp_thickness", "Outline Thickness",
                "Edge thickness of the target box.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayThickness,
                v -> { AlpakaConfig.instance.etherwarpOverlayThickness = v; AlpakaConfig.save(); },
                0.5f, 6.0f, val -> String.format(Locale.ROOT, "%.1f", val),
                "etherwarp outline thickness width line"));

        OPTIONS.add(new ConfigOption("etherwarp_through_walls", "Show Through Walls",
                "Draws the box even when terrain is in front of it.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpOverlayThroughWalls,
                v -> { AlpakaConfig.instance.etherwarpOverlayThroughWalls = v; AlpakaConfig.save(); },
                "etherwarp through walls depth on top see"));

        OPTIONS.add(new ConfigOption("etherwarp_line", "Line To Target",
                "Draws a line from your feet to the target block.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpLineEnabled,
                v -> { AlpakaConfig.instance.etherwarpLineEnabled = v; AlpakaConfig.save(); },
                "etherwarp line tracer path feet target"));

        OPTIONS.add(new ConfigOption("etherwarp_line_width", "Line Thickness",
                "Width of the line to the target.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpLineWidth,
                v -> { AlpakaConfig.instance.etherwarpLineWidth = v; AlpakaConfig.save(); },
                1.0f, 12.0f, val -> String.format(Locale.ROOT, "%.0f", val),
                "etherwarp line thickness width thin thick"));

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
                "Volume of the custom warp sound.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.etherwarpSoundVolume,
                v -> { AlpakaConfig.instance.etherwarpSoundVolume = v; AlpakaConfig.save(); },
                0.1f, 2.0f, val -> String.format(Locale.ROOT, "%.0f%%", val * 100.0f),
                "etherwarp sound volume loud quiet"));

        OPTIONS.add(new ConfigOption("Slayer Tracking", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("slayer_drop_tracker", "Slayer Drop Tracker",
                "Automatically tracks loot drops and kills for Hypixel Slayer bosses.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerDropTrackerEnabled,
                v -> { AlpakaConfig.instance.slayerDropTrackerEnabled = v; AlpakaConfig.save(); },
                "slayer drop tracker hypixel loot boss kill stats counter skyblock"));


        OPTIONS.add(new ConfigOption("hide_hypixel_drop_message", "Hide Hypixel Drop Line",
                "Hides Hypixel's own drop message when the tracker reports that drop.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.hideHypixelDropMessage,
                v -> { AlpakaConfig.instance.hideHypixelDropMessage = v; AlpakaConfig.save(); },
                "hide hypixel drop message duplicate chat slayer very rare clean"));


        OPTIONS.add(new ConfigOption("slayer_rng_drop_guild_chat", "Share Buttons On Top Drops",
                "Adds Guild and Party share buttons to a slayer's rarest drop.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerRngDropGuildChatEnabled,
                v -> { AlpakaConfig.instance.slayerRngDropGuildChatEnabled = v; AlpakaConfig.save(); },
                "guild party chat gc pc announce share button rng drop rare dice judgement core warden heart flex"));

        OPTIONS.add(new ConfigOption("slayer_share_message", "Shared Message",
                "What the buttons post: the tracker's line with the boss count, or Hypixel's drop message.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.slayerShareMessage,
                v -> { AlpakaConfig.instance.slayerShareMessage = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (net.alpaka.addons.features.slayer.SlayerDropTracker.SHARE_MESSAGE_NAMES.length - 1),
                val -> net.alpaka.addons.features.slayer.SlayerDropTracker.SHARE_MESSAGE_NAMES[Math.round(val)],
                "share message hypixel tracker line drop text guild party").choices(net.alpaka.addons.features.slayer.SlayerDropTracker.SHARE_MESSAGE_NAMES));


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
                        new ConfigOption.ToggleEntry("Quest started, complete and boss slain",
                                () -> AlpakaConfig.instance.hideSlayerChatQuest,
                                v -> { AlpakaConfig.instance.hideSlayerChatQuest = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Radio signal",
                                () -> AlpakaConfig.instance.hideSlayerChatRadio,
                                v -> { AlpakaConfig.instance.hideSlayerChatRadio = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Slayer level-ups",
                                () -> AlpakaConfig.instance.hideSlayerChatLevelUp,
                                v -> { AlpakaConfig.instance.hideSlayerChatLevelUp = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Slayer kill healing",
                                () -> AlpakaConfig.instance.hideSlayerChatHealing,
                                v -> { AlpakaConfig.instance.hideSlayerChatHealing = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Netherrack-Looking Sunshade drop",
                                () -> AlpakaConfig.instance.hideSlayerChatSunshade,
                                v -> { AlpakaConfig.instance.hideSlayerChatSunshade = v; AlpakaConfig.save(); })
                ),
                "slayer chat spam quest started complete boss slain slay combat xp radio signal enjoyer level up lvl healing heal hp sunshade netherrack rare drop hide"));

        OPTIONS.add(new ConfigOption("Slayer Boss Timer", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("boss_spawn_alert", "Boss Spawn Alert",
                "Slides in a notice the moment your slayer boss spawns.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.bossSpawnAlertEnabled,
                v -> { AlpakaConfig.instance.bossSpawnAlertEnabled = v; AlpakaConfig.save(); },
                "boss spawn alert notification slayer popup toast warning"));

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


        OPTIONS.add(new ConfigOption("slayer_timer_chat", "Boss Time In Chat",
                "Posts each boss's time in chat, and calls out a new personal best.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerTimerChatEnabled,
                v -> { AlpakaConfig.instance.slayerTimerChatEnabled = v; AlpakaConfig.save(); },
                "slayer boss timer chat message announce personal best"));


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
                0.0f, (float) (net.alpaka.addons.features.slayer.SlayerBossHighlight.STYLE_NAMES.length - 1),
                val -> net.alpaka.addons.features.slayer.SlayerBossHighlight.STYLE_NAMES[Math.round(val)],
                "slayer boss highlight style mode outline glow hitbox box overlay tint").choices(net.alpaka.addons.features.slayer.SlayerBossHighlight.STYLE_NAMES));

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


        OPTIONS.add(new ConfigOption("slayer_hud_enabled", "Enable Slayer Session HUD",
                "Live session stats while a Slayer quest is active.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudEnabled,
                v -> { AlpakaConfig.instance.slayerHudEnabled = v; AlpakaConfig.save(); },
                "slayer session hud stats xp bosses per hour timer rng dry streak skyblock"));


        OPTIONS.add(new ConfigOption("slayer_hud_lines", "Slayer HUD Lines",
                "Pick which lines the Slayer HUD shows.",
                ConfigCategory.SKYBLOCK,
                java.util.List.of(
                        new ConfigOption.ToggleEntry("Slayer name (and paused state)",
                                () -> AlpakaConfig.instance.slayerHudShowTitle,
                                v -> { AlpakaConfig.instance.slayerHudShowTitle = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Total slayer XP",
                                () -> AlpakaConfig.instance.slayerHudShowTotalXp,
                                v -> { AlpakaConfig.instance.slayerHudShowTotalXp = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Session XP",
                                () -> AlpakaConfig.instance.slayerHudShowSessionXp,
                                v -> { AlpakaConfig.instance.slayerHudShowSessionXp = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Slayer XP per hour",
                                () -> AlpakaConfig.instance.slayerHudShowXpPerHour,
                                v -> { AlpakaConfig.instance.slayerHudShowXpPerHour = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Boss count",
                                () -> AlpakaConfig.instance.slayerHudShowBossCount,
                                v -> { AlpakaConfig.instance.slayerHudShowBossCount = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Average boss time",
                                () -> AlpakaConfig.instance.slayerHudShowAvgBossTime,
                                v -> { AlpakaConfig.instance.slayerHudShowAvgBossTime = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Bosses per hour",
                                () -> AlpakaConfig.instance.slayerHudShowBossesPerHour,
                                v -> { AlpakaConfig.instance.slayerHudShowBossesPerHour = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Session time",
                                () -> AlpakaConfig.instance.slayerHudShowSessionTime,
                                v -> { AlpakaConfig.instance.slayerHudShowSessionTime = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Bosses since RNG drop",
                                () -> AlpakaConfig.instance.slayerHudShowSinceRngDrop,
                                v -> { AlpakaConfig.instance.slayerHudShowSinceRngDrop = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Polar bear effect",
                                () -> AlpakaConfig.instance.slayerHudShowBear,
                                v -> { AlpakaConfig.instance.slayerHudShowBear = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Ham radio (Blaze)",
                                () -> AlpakaConfig.instance.slayerHudShowRadio,
                                v -> { AlpakaConfig.instance.slayerHudShowRadio = v; AlpakaConfig.save(); })
                ),
                "slayer hud lines toggle show hide xp bosses per hour session time rng dry streak average bear gummy polar smoldering polarization effect ham radio blazetekk channel enjoyers"));

        OPTIONS.add(new ConfigOption("slayer_hud_bear_blaze_only", "Bear Line Only On Blaze",
                "Shows the polar bear effect only on the Blaze HUD instead of on every slayer.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudBearBlazeOnly,
                v -> { AlpakaConfig.instance.slayerHudBearBlazeOnly = v; AlpakaConfig.save(); },
                "slayer hud bear polar gummy smoldering polarization blaze only inferno"));


        OPTIONS.add(new ConfigOption("slayer_hud_only_in_areas", "Slayer HUD Only In Slayer Areas",
                "Shows the HUD only where that slayer is run, like SkyHanni's profit tracker.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudOnlyInSlayerAreas,
                v -> { AlpakaConfig.instance.slayerHudOnlyInSlayerAreas = v; AlpakaConfig.save(); },
                "slayer hud area island restrict show everywhere anywhere zone"));


        OPTIONS.add(new ConfigOption("slayer_hud_afk_pause", "Session Pause After Standing Still",
                "Idle time before the session timer pauses.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudAfkPauseSeconds,
                v -> { AlpakaConfig.instance.slayerHudAfkPauseSeconds = v; AlpakaConfig.save(); },
                5.0f, 300.0f, val -> val >= 60.0f
                        ? String.format(Locale.ROOT, "%dm %02ds", (int) (val / 60), (int) (val % 60))
                        : String.format(Locale.ROOT, "%ds", val.intValue()),
                "slayer session timer pause afk idle stand still seconds duration threshold"));


        OPTIONS.add(new ConfigOption("slayer_hud_pause_outside_area", "Pause Session Outside Slayer Area",
                "Pauses the session timer once you leave the slayer's area.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerHudPauseOutsideArea,
                v -> { AlpakaConfig.instance.slayerHudPauseOutsideArea = v; AlpakaConfig.save(); },
                "slayer session pause outside area zone leave region timer stop"));

        OPTIONS.add(new ConfigOption("World Age", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("world_age_join_message_enabled", "Server Age Join Message",
                "Chat notice with the server age and recent visit status on join.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.worldAgeJoinMessageEnabled,
                v -> { AlpakaConfig.instance.worldAgeJoinMessageEnabled = v; AlpakaConfig.save(); },
                "world age server join chat message notification alert day time"));


        OPTIONS.add(new ConfigOption("world_age_recent_threshold", "Recent Visit Window",
                "How recently you must have visited to be told about it.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.worldAgeRecentThresholdSec,
                v -> { AlpakaConfig.instance.worldAgeRecentThresholdSec = Math.round(v); AlpakaConfig.save(); },
                10.0f, 300.0f, val -> {
                    int sec = Math.round(val);
                    if (sec >= 60) {
                        int m = sec / 60;
                        int s = sec % 60;
                        return s > 0 ? String.format(Locale.ROOT, "%dm %ds", m, s) : String.format(Locale.ROOT, "%dm", m);
                    }
                    return String.format(Locale.ROOT, "%ds", sec);
                },
                "world age recent visit threshold window seconds time minutes slider"));

        OPTIONS.add(new ConfigOption("Party", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("party_invite_prompt", "Party Invite Prompt",
                "Shows a Y/N prompt on screen when someone invites you to a party.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.partyInvitePromptEnabled,
                v -> { AlpakaConfig.instance.partyInvitePromptEnabled = v; AlpakaConfig.save(); },
                "party invite prompt accept join decline y n key popup hypixel"));

        OPTIONS.add(new ConfigOption("Resource Pack", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("cache_resource_pack", "Cache Resource Pack",
                "Keeps Hypixel's resource pack loaded, so joining SkyBlock skips the loading screen.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.cacheResourcePackEnabled,
                v -> { AlpakaConfig.instance.cacheResourcePackEnabled = v; AlpakaConfig.save(); },
                "cache resource pack server pack texture loading screen reload join hypixel skyblock fast"));

        OPTIONS.add(new ConfigOption("Guild Chat", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("guild_prefix", "Custom Guild Tag",
                "Your own tag instead of \"Guild >\". Works with Chat Tabs.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.guildPrefixEnabled,
                v -> { AlpakaConfig.instance.guildPrefixEnabled = v; AlpakaConfig.save(); },
                "guild chat prefix tag name replace custom color"));

        OPTIONS.add(new ConfigOption("guild_prefix_text", "Guild Tag",
                "Shown instead of \"Guild >\". Use & for colors, e.g. &eLEMAN >",
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
                "In-game name of the relay account.",
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

        OPTIONS.add(new ConfigOption("Damage Display", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("only_crit_damage", "Only Show Crit Damage",
                "Hides non-crit, fire, poison and ability damage tags in Skyblock.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.onlyCritDamageEnabled,
                v -> { AlpakaConfig.instance.onlyCritDamageEnabled = v; AlpakaConfig.save(); },
                "damage indicator tags numbers hide non-crit fire poison abilities crit skyblock"));


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
                0.2f, 3.0f, val -> String.format(Locale.ROOT, "%.1fx", val),
                "name tag animation speed fast slow"));

        OPTIONS.add(new ConfigOption("name_tag_scale", "Name Tag Size",
                "Size of the tag relative to vanilla.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagScale,
                v -> { AlpakaConfig.instance.nameTagScale = v; AlpakaConfig.save(); },
                0.5f, 2.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "name tag size scale big small"));

        OPTIONS.add(new ConfigOption("name_tag_height", "Name Tag Height",
                "Raises or lowers the tag from where vanilla would put it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagHeightOffset,
                v -> { AlpakaConfig.instance.nameTagHeightOffset = v; AlpakaConfig.save(); },
                -0.5f, 0.5f, val -> String.format(Locale.ROOT, "%+.2f blocks", val),
                "name tag height offset raise lower position up down"));

        OPTIONS.add(new ConfigOption("name_tag_color_whole", "Color The Rank Too",
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
                "A thin rainbow frame around the tag's backdrop.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagChromaBorderEnabled,
                v -> { AlpakaConfig.instance.nameTagChromaBorderEnabled = v; AlpakaConfig.save(); },
                "name tag frame border chroma rainbow backdrop"));

        OPTIONS.add(new ConfigOption("name_tag_background", "Backdrop",
                "The dark box behind the letters. Off leaves the text floating on its own.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagBackgroundEnabled,
                v -> { AlpakaConfig.instance.nameTagBackgroundEnabled = v; AlpakaConfig.save(); },
                "name tag background backdrop box remove hide off"));

        OPTIONS.add(new ConfigOption("name_tag_bg_opacity", "Backdrop Opacity",
                "Darkening behind the letters when the backdrop is on. Vanilla is 25%.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.nameTagBackgroundOpacity,
                v -> { AlpakaConfig.instance.nameTagBackgroundOpacity = v; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Off (0%)" : String.format(Locale.ROOT, "%.0f%%", val),
                "name tag background backdrop opacity dark box"));

        OPTIONS.add(ConfigOption.color("name_tag_gradient_start_color", "Gradient Start Color",
                "First color of the Gradient effect.",
                ConfigCategory.COSMETICS,
                c -> c.nameTagGradientStart,
                v -> { AlpakaConfig.instance.nameTagGradientStart = v; AlpakaConfig.save(); },
                "name tag gradient color start first picker"));

        OPTIONS.add(ConfigOption.color("name_tag_gradient_end_color", "Gradient End Color",
                "Second color of the Gradient effect.",
                ConfigCategory.COSMETICS,
                c -> c.nameTagGradientEnd,
                v -> { AlpakaConfig.instance.nameTagGradientEnd = v; AlpakaConfig.save(); },
                "name tag gradient color end second picker"));

        OPTIONS.add(new ConfigOption("Sensei Hat", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("chroma_hat", "Sensei Hat",
                "A translucent straw sensei hat on your own head. Only you can see it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatEnabled,
                v -> { AlpakaConfig.instance.chromaHatEnabled = v; AlpakaConfig.save(); },
                "chroma hat sensei samurai kasa jingasa cosmetic head straw"));

        OPTIONS.add(new ConfigOption("chroma_hat_rainbow", "Chroma Colors",
                "Rainbow colors sweeping around the hat, glowing in the dark. Off keeps the plain hat color.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatRainbow,
                v -> { AlpakaConfig.instance.chromaHatRainbow = v; AlpakaConfig.save(); },
                "chroma hat rainbow colors glow emissive"));

        OPTIONS.add(ConfigOption.color("chroma_hat_color", "Hat Color",
                "Color of the plain hat while Chroma Colors is off.",
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
                "Brim width and height of the hat.",
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

        OPTIONS.add(new ConfigOption("chroma_hat_speed", "Hat Color Speed",
                "How fast the rainbow spins around the hat. Chroma Colors only.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.chromaHatSpeed,
                v -> { AlpakaConfig.instance.chromaHatSpeed = v; AlpakaConfig.save(); },
                0.2f, 3.0f, val -> String.format(Locale.ROOT, "%.1fx", val),
                "chroma hat speed rainbow spin fast slow"));

        OPTIONS.add(new ConfigOption("Player Scale", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("player_scale", "Player Scale",
                "Stretches or shrinks your model in third person. Purely visual, only you can see it.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleEnabled,
                v -> { AlpakaConfig.instance.playerScaleEnabled = v; AlpakaConfig.save(); },
                "player scale size model body big small tall wide giant tiny stretch shrink cosmetic third person f5"));

        OPTIONS.add(new ConfigOption("player_scale_x", "Width (X)",
                "Side to side, across the shoulders. 1.00x is vanilla.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleX,
                v -> { AlpakaConfig.instance.playerScaleX = PlayerScaleFeature.snap(v); AlpakaConfig.save(); },
                PlayerScaleFeature.MIN_SCALE, PlayerScaleFeature.MAX_SCALE, val -> String.format(Locale.ROOT, "%.2fx", val),
                "player scale width x axis wide thin narrow broad"));

        OPTIONS.add(new ConfigOption("player_scale_y", "Height (Y)",
                "Feet to head. The feet stay on the ground, so the model grows upwards.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleY,
                v -> { AlpakaConfig.instance.playerScaleY = PlayerScaleFeature.snap(v); AlpakaConfig.save(); },
                PlayerScaleFeature.MIN_SCALE, PlayerScaleFeature.MAX_SCALE, val -> String.format(Locale.ROOT, "%.2fx", val),
                "player scale height y axis tall short giant tiny"));

        OPTIONS.add(new ConfigOption("player_scale_z", "Depth (Z)",
                "Front to back. Turns with the body, whichever way you face.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleZ,
                v -> { AlpakaConfig.instance.playerScaleZ = PlayerScaleFeature.snap(v); AlpakaConfig.save(); },
                PlayerScaleFeature.MIN_SCALE, PlayerScaleFeature.MAX_SCALE, val -> String.format(Locale.ROOT, "%.2fx", val),
                "player scale depth z axis thick flat paper thin"));

        OPTIONS.add(new ConfigOption("player_scale_others", "Scale Other Players Too",
                "Other real players on SkyBlock too, shrunk only, never enlarged. Hitboxes are unchanged.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.playerScaleOthers,
                v -> { AlpakaConfig.instance.playerScaleOthers = v; AlpakaConfig.save(); },
                "player scale others other players everyone all everybody"));

        OPTIONS.add(new ConfigOption("player_scale_reset", "Reset Scale",
                "Puts all three axes back to 1.00x. The toggle stays as it is.",
                ConfigCategory.COSMETICS,
                "Reset",
                parent -> {
                    AlpakaConfig.instance.playerScaleX = 1.0f;
                    AlpakaConfig.instance.playerScaleY = 1.0f;
                    AlpakaConfig.instance.playerScaleZ = 1.0f;
                    AlpakaConfig.save();
                },
                "player scale reset default vanilla normal 1.0").confirmFirst());

        OPTIONS.add(new ConfigOption("Damage Tags", ConfigCategory.COSMETICS));

        OPTIONS.add(new ConfigOption("custom_damage_tags", "Custom Damage Tags",
                "Draws damage numbers itself: they pop up, float and fade, in gradients. Visual only.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagsEnabled,
                v -> { AlpakaConfig.instance.customDamageTagsEnabled = v; AlpakaConfig.save(); },
                "damage tags numbers splash custom animated pop fade gradient crit replace hypixel"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_scale", "Damage Tag Size",
                "Size of the custom damage numbers. Crits are drawn a quarter larger.",
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
                400.0f, 3000.0f, val -> String.format(Locale.ROOT, "%.2fs", Math.round(val / 50f) * 50 / 1000f),
                "damage tag duration time lifetime fade long short"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_pop", "Pop Strength",
                "How far a number overshoots its size when it appears. 0% just grows in.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagPopStrength * 100f,
                v -> { AlpakaConfig.instance.customDamageTagPopStrength = v / 100f; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> String.format(Locale.ROOT, "%.0f%%", val),
                "damage tag pop bounce overshoot animation"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_rise", "Float Height",
                "How far a number drifts upwards while it fades.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagRise,
                v -> { AlpakaConfig.instance.customDamageTagRise = v; AlpakaConfig.save(); },
                0.0f, 2.0f, val -> String.format(Locale.ROOT, "%.2f blocks", val),
                "damage tag float rise up drift height"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_format", "Number Format",
                "As Hypixel writes it, shortened (1.2M), or in full with separators.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.customDamageTagFormat,
                v -> { AlpakaConfig.instance.customDamageTagFormat = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (CustomDamageTagFeature.FORMAT_NAMES.length - 1),
                val -> CustomDamageTagFeature.FORMAT_NAMES[Math.round(val)],
                "damage tag number format compact short million k m b separator").choices(CustomDamageTagFeature.FORMAT_NAMES));

        OPTIONS.add(new ConfigOption("custom_damage_tag_flow", "Flowing Gradient",
                "The gradient sways along the digits instead of standing still.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagFlowingGradient,
                v -> { AlpakaConfig.instance.customDamageTagFlowingGradient = v; AlpakaConfig.save(); },
                "damage tag gradient flow animated color shimmer"));


        OPTIONS.add(new ConfigOption("custom_damage_tag_shadow", "Damage Tag Shadow",
                "A drop shadow behind the digits, so they read on bright backgrounds.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagShadow,
                v -> { AlpakaConfig.instance.customDamageTagShadow = v; AlpakaConfig.save(); },
                "damage tag shadow drop text"));


        OPTIONS.add(new ConfigOption("custom_damage_tag_hide_slayer", "Hide Near Slayer Bosses",
                "No custom damage numbers on or right next to a slayer boss, yours or anyone's.",
                ConfigCategory.COSMETICS,
                () -> AlpakaConfig.instance.customDamageTagHideNearSlayerBoss,
                v -> { AlpakaConfig.instance.customDamageTagHideNearSlayerBoss = v; AlpakaConfig.save(); },
                "damage tag hide slayer boss near revenant tarantula sven voidgloom inferno demonlord vampire skyhanni"));

        OPTIONS.add(new ConfigOption("custom_damage_tag_dungeon_mode", "Damage Tags In Dungeons",
                "Hides the custom damage numbers in the dungeon boss room, or in the whole run.",
                ConfigCategory.COSMETICS,
                () -> (float) AlpakaConfig.instance.customDamageTagDungeonMode,
                v -> { AlpakaConfig.instance.customDamageTagDungeonMode = Math.round(v); AlpakaConfig.save(); },
                0.0f, (float) (CustomDamageTagFeature.DUNGEON_MODE_NAMES.length - 1),
                val -> CustomDamageTagFeature.DUNGEON_MODE_NAMES[Math.round(val)],
                "damage tag dungeon catacombs boss room floor hide master mode").choices(CustomDamageTagFeature.DUNGEON_MODE_NAMES));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_normal_start", "Normal Hit Start Color",
                "First color of a normal hit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagNormalStart,
                v -> { AlpakaConfig.instance.customDamageTagNormalStart = v; AlpakaConfig.save(); },
                "damage tag gradient color picker normal start"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_normal_end", "Normal Hit End Color",
                "Last color of a normal hit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagNormalEnd,
                v -> { AlpakaConfig.instance.customDamageTagNormalEnd = v; AlpakaConfig.save(); },
                "damage tag gradient color picker normal end"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_crit_start", "Crit Start Color",
                "First color of a crit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagCritStart,
                v -> { AlpakaConfig.instance.customDamageTagCritStart = v; AlpakaConfig.save(); },
                "damage tag gradient color picker crit start"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_crit_end", "Crit End Color",
                "Last color of a crit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagCritEnd,
                v -> { AlpakaConfig.instance.customDamageTagCritEnd = v; AlpakaConfig.save(); },
                "damage tag gradient color picker crit end"));

        // --- 7. SOUND & UTILITY ---

        // The quick command list is edited far more often than any sound is retuned, so it heads
        // the tab rather than sitting below every sound option.
        OPTIONS.add(new ConfigOption("Utility", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("command_wheel_custom_commands", "Quick Command Menu",
                "Add, edit or remove the quick command entries.",
                ConfigCategory.SOUND_MISC,
                "Edit Commands",
                parent -> Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.CommandWheelConfigScreen(parent)),
                "quick command wheel commands add remove edit custom list menu keybind"));

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

        OPTIONS.add(new ConfigOption("command_shortcuts_edit", "Edit Command Shortcuts",
                "Add, edit or remove aliases and key shortcuts.",
                ConfigCategory.SOUND_MISC,
                "Edit Shortcuts",
                parent -> Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.CommandShortcutsConfigScreen(parent)),
                "command shortcuts alias aliases edit add remove keybind key bind hotkey list menu"));

        OPTIONS.add(new ConfigOption("always_sprint", "Always Sprint",
                "Walking forward sprints on its own, no sprint key needed.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.alwaysSprintEnabled,
                v -> { AlpakaConfig.instance.alwaysSprintEnabled = v; AlpakaConfig.save(); },
                "always sprint auto run toggle movement key hold forward"));

        OPTIONS.add(new ConfigOption("keep_cursor_position", "Keep Cursor Position",
                "The cursor stays where it is when one menu follows another, such as backpack pages, instead of jumping back to the middle.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.keepCursorPositionEnabled,
                v -> { AlpakaConfig.instance.keepCursorPositionEnabled = v; AlpakaConfig.save(); },
                "keep cursor position mouse reset center centre middle inventory menu backpack storage page gui no cursor reset"));

        OPTIONS.add(new ConfigOption("Custom Sounds", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sounds", "Master Custom Sounds",
                "Master switch for custom sounds. Currently work in progress.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsEnabled,
                v -> { AlpakaConfig.instance.customSoundsEnabled = v; AlpakaConfig.save(); },
                "custom sounds audio effects click chime notification master"));

        OPTIONS.add(new ConfigOption("custom_sounds_volume", "Custom Sounds Volume",
                "Volume level for custom sound effects.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsVolume,
                v -> { AlpakaConfig.instance.customSoundsVolume = v; AlpakaConfig.save(); },
                0.0f, 1.0f, val -> String.format(Locale.ROOT, "%d%%", Math.round(val * 100)),
                "custom sounds volume loudness audio gain percent"));

        OPTIONS.add(new ConfigOption("Interface Sounds", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_button_click", "UI Button Click Sound",
                "Plays custom chime when clicking buttons in menus.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundButtonClick,
                v -> { AlpakaConfig.instance.customSoundButtonClick = v; AlpakaConfig.save(); },
                "button click sound ui audio feedback chime"));

        OPTIONS.add(soundVolume("custom_sound_button_click_volume", "Button Click Volume", "button click",
                () -> AlpakaConfig.instance.customSoundButtonClickVolume,
                v -> AlpakaConfig.instance.customSoundButtonClickVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_hotbar_scroll", "Hotbar Scroll Sound",
                "Plays custom sound effect when scrolling or switching hotbar slots.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundHotbarScroll,
                v -> { AlpakaConfig.instance.customSoundHotbarScroll = v; AlpakaConfig.save(); },
                "hotbar scroll slot equip switch audio sound"));

        OPTIONS.add(soundVolume("custom_sound_hotbar_scroll_volume", "Hotbar Scroll Volume", "hotbar scroll",
                () -> AlpakaConfig.instance.customSoundHotbarScrollVolume,
                v -> AlpakaConfig.instance.customSoundHotbarScrollVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_inventory_open_close", "Inventory Open/Close Sound",
                "Plays custom sound effect when opening or closing container GUIs.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundInventoryOpenClose,
                v -> { AlpakaConfig.instance.customSoundInventoryOpenClose = v; AlpakaConfig.save(); },
                "inventory open close gui container sound audio custom"));

        OPTIONS.add(soundVolume("custom_sound_inventory_open_close_volume", "Open/Close Volume", "open/close",
                () -> AlpakaConfig.instance.customSoundInventoryOpenCloseVolume,
                v -> AlpakaConfig.instance.customSoundInventoryOpenCloseVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_inventory_click", "Inventory Click Sound",
                "Plays custom sound when clicking items or picking them up.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundInventoryClick,
                v -> { AlpakaConfig.instance.customSoundInventoryClick = v; AlpakaConfig.save(); },
                "inventory click item pickup container sound audio custom"));

        OPTIONS.add(soundVolume("custom_sound_inventory_click_volume", "Inventory Click Volume", "inventory click",
                () -> AlpakaConfig.instance.customSoundInventoryClickVolume,
                v -> AlpakaConfig.instance.customSoundInventoryClickVolume = v));

        OPTIONS.add(new ConfigOption("Gameplay Sounds", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_rare_drop", "Slayer Rare Drop Sound",
                "Plays sound effect when getting a rare Slayer loot drop.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundRareDrop,
                v -> { AlpakaConfig.instance.customSoundRareDrop = v; AlpakaConfig.save(); },
                "rare drop sound slayer loot celebration audio"));

        OPTIONS.add(soundVolume("custom_sound_rare_drop_volume", "Rare Drop Volume", "rare drop",
                () -> AlpakaConfig.instance.customSoundRareDropVolume,
                v -> AlpakaConfig.instance.customSoundRareDropVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_notification", "Boss Spawn Sound",
                "Plays sound effect when a Slayer boss spawns.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundNotification,
                v -> { AlpakaConfig.instance.customSoundNotification = v; AlpakaConfig.save(); },
                "boss spawn sound notification alert audio"));

        OPTIONS.add(soundVolume("custom_sound_notification_volume", "Boss Spawn Volume", "boss spawn",
                () -> AlpakaConfig.instance.customSoundNotificationVolume,
                v -> AlpakaConfig.instance.customSoundNotificationVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_player_hurt", "Player Hurt Sound",
                "Plays custom sound effect when taking damage.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundPlayerHurt,
                v -> { AlpakaConfig.instance.customSoundPlayerHurt = v; AlpakaConfig.save(); },
                "player hurt sound damage hit audio custom"));

        OPTIONS.add(soundVolume("custom_sound_player_hurt_volume", "Player Hurt Volume", "player hurt",
                () -> AlpakaConfig.instance.customSoundPlayerHurtVolume,
                v -> AlpakaConfig.instance.customSoundPlayerHurtVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_blaze_death", "Blaze Death Sound",
                "Custom sound when a blaze you fought dies. Other kills are ignored.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundBlazeDeath,
                v -> { AlpakaConfig.instance.customSoundBlazeDeath = v; AlpakaConfig.save(); },
                "blaze death sound kill slayer audio custom own kills"));

        OPTIONS.add(soundVolume("custom_sound_blaze_death_volume", "Blaze Death Volume", "blaze death",
                () -> AlpakaConfig.instance.customSoundBlazeDeathVolume,
                v -> AlpakaConfig.instance.customSoundBlazeDeathVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_zombie_remedy", "Zombie Remedy Sound",
                "Plays custom sound for the zombie villager cure effect.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundZombieRemedy,
                v -> { AlpakaConfig.instance.customSoundZombieRemedy = v; AlpakaConfig.save(); },
                "zombie remedy cure villager sound audio custom"));

        OPTIONS.add(soundVolume("custom_sound_zombie_remedy_volume", "Zombie Remedy Volume", "zombie remedy",
                () -> AlpakaConfig.instance.customSoundZombieRemedyVolume,
                v -> AlpakaConfig.instance.customSoundZombieRemedyVolume = v));

        OPTIONS.add(new ConfigOption("custom_sound_successful_hit", "Successful Hit Sound",
                "Plays custom sound on a critical or successful hit.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundSuccessfulHit,
                v -> { AlpakaConfig.instance.customSoundSuccessfulHit = v; AlpakaConfig.save(); },
                "successful hit crit critical attack sound audio custom"));

        OPTIONS.add(soundVolume("custom_sound_successful_hit_volume", "Successful Hit Volume", "successful hit",
                () -> AlpakaConfig.instance.customSoundSuccessfulHitVolume,
                v -> AlpakaConfig.instance.customSoundSuccessfulHitVolume = v));

        OPTIONS.add(new ConfigOption("mute_vanilla_sounds_blaze_slayer", "Silence Blaze Slayer Noise",
                "Mutes blazes, fire, lava and ghasts during a Blaze quest. Menu and mod sounds play.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.muteVanillaSoundsInBlazeSlayer,
                v -> { AlpakaConfig.instance.muteVanillaSoundsInBlazeSlayer = v; AlpakaConfig.save(); },
                "mute silence vanilla game audio blaze slayer quiet boss spawn hear"));

        OPTIONS.add(new ConfigOption("blaze_mute_all_world_audio", "Silence: All World Audio",
                "Mutes every world sound during a Blaze quest, not just the fight's noise.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.blazeMuteAllWorldAudio,
                v -> { AlpakaConfig.instance.blazeMuteAllWorldAudio = v; AlpakaConfig.save(); },
                "mute silence all world audio blaze slayer quiet"));

        OPTIONS.add(new ConfigOption("Low HP Warning", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_low_hp_heartbeat", "Low HP Heartbeat Sound",
                "Heartbeat sound when your health drops below the threshold.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundLowHpHeartbeat,
                v -> { AlpakaConfig.instance.customSoundLowHpHeartbeat = v; AlpakaConfig.save(); },
                "low hp health heartbeat sound survival audio custom lowhp pulse alert"));

        OPTIONS.add(soundVolume("custom_sound_low_hp_heartbeat_volume", "Heartbeat Volume", "heartbeat",
                () -> AlpakaConfig.instance.customSoundLowHpHeartbeatVolume,
                v -> AlpakaConfig.instance.customSoundLowHpHeartbeatVolume = v));

        OPTIONS.add(new ConfigOption("low_hp_heartbeat_threshold", "Heartbeat HP Threshold",
                "Health percentage at which the heartbeat starts.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.lowHpHeartbeatThreshold * 100.0f,
                v -> { AlpakaConfig.instance.lowHpHeartbeatThreshold = v / 100.0f; AlpakaConfig.save(); },
                5.0f, 80.0f, val -> String.format(Locale.ROOT, "%.0f%%", val),
                "heartbeat low hp health percentage threshold trigger sound volume"));

        buildCategoryCache();
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
