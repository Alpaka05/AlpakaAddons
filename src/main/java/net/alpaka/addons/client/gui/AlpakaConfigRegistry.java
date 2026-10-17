package net.alpaka.addons.client.gui;

import java.util.Locale;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
import net.alpaka.addons.client.hud.HudEditorScreen;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.damagetags.CustomDamageTagFeature;
import net.alpaka.addons.features.etherwarp.EtherwarpOverlayFeature;
import net.alpaka.addons.features.nametag.CustomNameTagFeature;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.playerscale.PlayerScaleFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AlpakaConfigRegistry {
    private static final List<ConfigOption> OPTIONS = new ArrayList<>();
    private static final java.util.Map<ConfigCategory, List<ConfigOption>> CATEGORY_CACHE = new java.util.EnumMap<>(ConfigCategory.class);

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
                "Lets the mod go online for the mayor's slayer XP buff, the main menu's player count and the update check.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.allowApiCalls,
                v -> { AlpakaConfig.instance.allowApiCalls = v; AlpakaConfig.save(); },
                "api network internet hypixel request mayor election offline privacy allow network features"));

        OPTIONS.add(new ConfigOption("update_notifications", "Update Check",
                "Tells you in chat when a new version is out. Needs Network Features.",
                ConfigCategory.GENERAL,
                () -> AlpakaConfig.instance.updateNotificationsEnabled,
                v -> { AlpakaConfig.instance.updateNotificationsEnabled = v; AlpakaConfig.save(); },
                "update new version release github download notify check outdated update notifications"));

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
                "menu accent color colour theme custom picker border highlight gui teal gold cyan red green blue"));

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

        OPTIONS.add(new ConfigOption("hud_editor", "HUD Editor",
                "Moves and resizes every HUD: slayer session, boss timer, world age, inventory and player model. Also opens with /alpakahud.",
                ConfigCategory.GENERAL,
                "Open HUD Editor",
                parent -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(parent)),
                "hud editor position edit dragging screen drag move resize scale layout alpakahud avatar session"));

        OPTIONS.add(new ConfigOption("disable_all_features", "Disable All Features",
                "Turns off every feature of the mod, after asking first.",
                ConfigCategory.GENERAL,
                "Disable All Features",
                parent -> AlpakaConfig.instance.disableAllFeatures(),
                "disable all features turn off reset everywhere confirm prompt disables confirmation")
                .confirm("Disable All Features",
                        "Turns off every feature of Alpaka Addons, and some of their settings with them. "
                                + "Which ones were on is not remembered. This cannot be undone.",
                        "Disable All", true));

        OPTIONS.add(new ConfigOption("reset_all_settings", "Reset All Settings",
                "Puts every setting of the mod back to its default, after asking first.",
                ConfigCategory.GENERAL,
                "Reset All Settings",
                parent -> {
                    AlpakaConfig.resetToDefaults();
                    GuiFont.refreshChat();
                },
                "reset all settings default defaults factory restore clear confirm prompt confirmation")
                .confirm("Reset All Settings",
                        "Puts every setting of Alpaka Addons back to its default, HUD positions and Command Wheel commands included. "
                                + "Slayer stats and saved viewmodel presets are kept. This cannot be undone.",
                        "Reset All", true));

        // --- 1. VISUALS & RENDERING ---

        OPTIONS.add(new ConfigOption("World & Mobs", ConfigCategory.VISUALS));

        OPTIONS.add(new ConfigOption("fullbright", "Fullbright",
                "Lights up the whole world at full brightness, no torches needed.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.fullbrightEnabled,
                v -> { AlpakaConfig.instance.fullbrightEnabled = v; AlpakaConfig.save(); },
                "gamma brightness light fullbright vision enables maximum everywhere torches"));

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
                                v -> { AlpakaConfig.instance.cleanBlazeNameTags = v; AlpakaConfig.save(); })
                ),
                "blaze flame smoke angry villager lava drip particles potion effect swirl enchant enchanting glyph ender portal witch spell fire burning overlay fireball projectile lightning bolt flash thunder nametag name tag health smoldering miniboss demon clean hide"));

        OPTIONS.add(new ConfigOption("blaze_fire_pits", "Fire Pit Highlight",
                "Marks the fire pits on the floor with a glowing column during your Blaze slayer boss.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.blazeFirePitsEnabled,
                v -> { AlpakaConfig.instance.blazeFirePitsEnabled = v; AlpakaConfig.save(); },
                "blaze slayer fire pit pits clay terracotta floor flames aura highlight inferno demonlord boss warning"));

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
                "Makes mobs vanish the moment they die instead of tipping over first.",
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

        OPTIONS.add(new ConfigOption("inventory_snow", "Inventory Snowflakes",
                "Lets snowflakes fall over container screens, like chests and your inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowEnabled,
                v -> { AlpakaConfig.instance.inventorySnowEnabled = v; AlpakaConfig.save(); },
                "snow winter effect gui inventory snowflakes"));

        OPTIONS.add(new ConfigOption("inventory_snow_speed", "Snowfall Speed",
                "How fast the snowflakes fall.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowSpeed,
                v -> { AlpakaConfig.instance.inventorySnowSpeed = v; AlpakaConfig.save(); },
                0.1f, 5.0f, val -> String.format(Locale.ROOT, "%.2fx", val),
                "snow speed animation winter velocity snow fall speed"));

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

        OPTIONS.add(new ConfigOption("Chat", ConfigCategory.VISUALS));

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
                1.0f, 10.0f, v -> Math.round(v) + " / 10",
                "smooth chat strength speed duration animation slow fast slider visible"));

        OPTIONS.add(new ConfigOption("chat_smooth_scroll", "Smooth Chat Scrolling",
                "Makes scrolling the chat glide instead of jumping line by line.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatSmoothScrollEnabled,
                v -> { AlpakaConfig.instance.chatSmoothScrollEnabled = v; AlpakaConfig.save(); },
                "smooth chat scroll scrolling wheel glide peek animation"));

        OPTIONS.add(new ConfigOption("chat_search", "Chat Search",
                "Filters the chat to messages containing your text. Press Ctrl+F (Cmd+F on macOS) in the chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatSearchEnabled,
                v -> { AlpakaConfig.instance.chatSearchEnabled = v; AlpakaConfig.save(); },
                "chat search find filter ctrl f messages history lookup cmd mac macos"));

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
                500.0f, 20000.0f, val -> String.format(Locale.ROOT, "%,d lines", Math.round(val / 500.0f) * 500),
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
                "Shows the full chat, scrollable, while you hold the Chat Peek key. The key is unbound by default; set it in Controls.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.chatPeekEnabled,
                v -> { AlpakaConfig.instance.chatPeekEnabled = v; AlpakaConfig.save(); },
                "chat peek hold key read scroll history keybind open bigger larger peek chat unbound controls"));

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
                        new ConfigOption.ToggleEntry("Copy Button",
                                () -> AlpakaConfig.instance.screenshotShowCopy,
                                v -> { AlpakaConfig.instance.screenshotShowCopy = v; AlpakaConfig.save(); }),
                        new ConfigOption.ToggleEntry("Delete Button",
                                () -> AlpakaConfig.instance.screenshotShowDelete,
                                v -> { AlpakaConfig.instance.screenshotShowDelete = v; AlpakaConfig.save(); })
                ),
                "screenshot line parts file name open copy delete button show hide"));

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

        OPTIONS.add(new ConfigOption("inventory_hud_always", "Always Visible",
                "Keeps the inventory on screen. When off, only the Inventory HUD key shows it; the key is unbound by default, set it in Controls.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudAlwaysVisible,
                v -> { AlpakaConfig.instance.inventoryHudAlwaysVisible = v; AlpakaConfig.save(); },
                "inventory hud always visible permanent keybind toggle hidden off: only unbound controls"));

        OPTIONS.add(new ConfigOption("inventory_hud_on_change", "Show on Item Change",
                "Slides the inventory up briefly when an item is picked up or dropped.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventoryHudShowOnItemChange,
                v -> { AlpakaConfig.instance.inventoryHudShowOnItemChange = v; AlpakaConfig.save(); },
                "inventory hud show on pickup drop item change peek temporary"));

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
                "inventory hud frame border outline gradient colour color start first picker flat panel's"));

        OPTIONS.add(ConfigOption.color("inventory_hud_frame_end_color", "Frame End Color",
                "The frame gradient's end color.",
                ConfigCategory.VISUALS,
                c -> c.inventoryHudFrameEnd,
                v -> { AlpakaConfig.instance.inventoryHudFrameEnd = v; AlpakaConfig.save(); },
                "inventory hud frame border outline gradient colour color end second picker flat panel's"));

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
                "starlight galaxy color colour nebula tint purple"));

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

        OPTIONS.add(new ConfigOption("load_preset_1", "Load Preset 1",
                "Replaces your current viewmodel and swing settings with Preset 1.",
                ConfigCategory.VIEWMODEL,
                "Load Preset 1",
                parent -> AlpakaConfig.instance.loadPreset(0),
                "preset load 1 slot viewmodel hand size offset applies"));

        OPTIONS.add(new ConfigOption("save_preset_1", "Save to Preset 1",
                "Saves your current viewmodel and swing settings to Preset 1.",
                ConfigCategory.VIEWMODEL,
                "Save to Preset 1",
                parent -> AlpakaConfig.instance.savePreset(0),
                "preset save 1 slot viewmodel hand size offset")
                .confirm("Save to Preset 1",
                        "Saves your current viewmodel and swing settings to Preset 1, replacing what is saved there now.",
                        "Save", false));

        OPTIONS.add(new ConfigOption("load_preset_2", "Load Preset 2",
                "Replaces your current viewmodel and swing settings with Preset 2.",
                ConfigCategory.VIEWMODEL,
                "Load Preset 2",
                parent -> AlpakaConfig.instance.loadPreset(1),
                "preset load 2 slot viewmodel hand size offset applies"));

        OPTIONS.add(new ConfigOption("save_preset_2", "Save to Preset 2",
                "Saves your current viewmodel and swing settings to Preset 2.",
                ConfigCategory.VIEWMODEL,
                "Save to Preset 2",
                parent -> AlpakaConfig.instance.savePreset(1),
                "preset save 2 slot viewmodel hand size offset")
                .confirm("Save to Preset 2",
                        "Saves your current viewmodel and swing settings to Preset 2, replacing what is saved there now.",
                        "Save", false));

        OPTIONS.add(new ConfigOption("load_preset_3", "Load Preset 3",
                "Replaces your current viewmodel and swing settings with Preset 3.",
                ConfigCategory.VIEWMODEL,
                "Load Preset 3",
                parent -> AlpakaConfig.instance.loadPreset(2),
                "preset load 3 slot viewmodel hand size offset applies"));

        OPTIONS.add(new ConfigOption("save_preset_3", "Save to Preset 3",
                "Saves your current viewmodel and swing settings to Preset 3.",
                ConfigCategory.VIEWMODEL,
                "Save to Preset 3",
                parent -> AlpakaConfig.instance.savePreset(2),
                "preset save 3 slot viewmodel hand size offset")
                .confirm("Save to Preset 3",
                        "Saves your current viewmodel and swing settings to Preset 3, replacing what is saved there now.",
                        "Save", false));


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
                "block outline color colour picker rgb alpha cyan red blue"));

        OPTIONS.add(new ConfigOption("Fill", ConfigCategory.BLOCK_OVERLAY));

        OPTIONS.add(new ConfigOption("block_fill_enabled", "Fill",
                "Fills the faces of the block you aim at with a translucent color.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockFillEnabled,
                v -> { AlpakaConfig.instance.blockFillEnabled = v; AlpakaConfig.save(); },
                "block fill solid transparent face shading color colour block fill"));

        OPTIONS.add(ConfigOption.color("block_fill_color", "Fill Color",
                "The fill color, transparency included.",
                ConfigCategory.BLOCK_OVERLAY,
                c -> c.blockFillColor,
                v -> { AlpakaConfig.instance.blockFillColor = v; AlpakaConfig.save(); },
                "block fill color colour picker rgb alpha transparency tint"));

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
                "chroma rgb rainbow color colour cycle gradient block chroma rgb effect"));

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
                "etherwarp colour color target box gold"));

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
                "etherwarp fail colour color red blocked fail color"));

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


        OPTIONS.add(new ConfigOption("slayer_since_reply_offer", "Offer Replies to !since",
                "Shows your count when your party asks !since, with a Reply button.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerSinceReplyOffer,
                v -> { AlpakaConfig.instance.slayerSinceReplyOffer = v; AlpakaConfig.save(); },
                "since !since party reply answer bosses since last drop dry streak pc"));


        OPTIONS.add(new ConfigOption("hide_slayer_chat", "Hide Slayer Chat Spam",
                "Hides Hypixel's slayer quest, level-up and radio messages.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.hideSlayerChatMessages,
                v -> { AlpakaConfig.instance.hideSlayerChatMessages = v; AlpakaConfig.save(); },
                "hide slayer chat spam quest complete started radio level up messages"));

        OPTIONS.add(new ConfigOption("Slayer Boss Timer", ConfigCategory.SKYBLOCK));


        OPTIONS.add(new ConfigOption("boss_spawn_alert", "Boss Spawn Alert",
                "Slides in a notification the moment your slayer boss spawns.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.bossSpawnAlertEnabled,
                v -> { AlpakaConfig.instance.bossSpawnAlertEnabled = v; AlpakaConfig.save(); },
                "boss spawn alert notification slayer popup toast warning notice"));

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
                                v -> { AlpakaConfig.instance.slayerHudShowBear = v; AlpakaConfig.save(); })
                ),
                "slayer hud lines toggle show hide xp bosses per hour session time rng dry streak average bear gummy polar smoldering polarization effect slayer hud lines name total count session time"));

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


        OPTIONS.add(new ConfigOption("world_age_hud_enabled", "World Age HUD",
                "Shows the current server's world age in days.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.worldAgeHudEnabled,
                v -> { AlpakaConfig.instance.worldAgeHudEnabled = v; AlpakaConfig.save(); },
                "world age day hud server days time skyblock color red orange green world age (day) hud displays"));


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

        OPTIONS.add(new ConfigOption("Guild Chat", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("guild_prefix", "Custom Guild Tag",
                "Shows your own tag instead of \"Guild >\". Works with Chat Tabs.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.guildPrefixEnabled,
                v -> { AlpakaConfig.instance.guildPrefixEnabled = v; AlpakaConfig.save(); },
                "guild chat prefix tag name replace custom colour color"));

        OPTIONS.add(new ConfigOption("guild_prefix_text", "Guild Tag",
                "Shown instead of \"Guild >\". Write color codes with &, as in \"&eLEMAN >\".",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.guildPrefixText,
                v -> { AlpakaConfig.instance.guildPrefixText = v; },
                "&eLEMAN >",
                "guild tag text prefix colour color code ampersand"));


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

        OPTIONS.add(new ConfigOption("Mob Highlights", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("pangolin_highlight_enabled", "Highlight Pangolins",
                "Outlines Pangolins in Torrhus Canyon while they are clearly in view.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.pangolinHighlightEnabled,
                v -> { AlpakaConfig.instance.pangolinHighlightEnabled = v; AlpakaConfig.save(); },
                "pangolin highlight glow outline critter torrhus torhus canyon hideaway armadillo skyblock"));

        OPTIONS.add(ConfigOption.color("pangolin_highlight_color", "Outline Color",
                "The glowing outline's color.",
                ConfigCategory.SKYBLOCK,
                c -> c.pangolinHighlightColor,
                v -> { AlpakaConfig.instance.pangolinHighlightColor = v; AlpakaConfig.save(); },
                "pangolin outline color colour glow highlight picker critter torrhus canyon pangolin outline color highlighted"));

        OPTIONS.add(new ConfigOption("Damage Display", ConfigCategory.SKYBLOCK));

        OPTIONS.add(new ConfigOption("only_crit_damage", "Only Show Crit Damage",
                "Hides non-crit, fire, poison and ability damage tags on SkyBlock.",
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
                "name tag colour color rainbow chroma gradient pulse effect mode").choices(CustomNameTagFeature.COLOR_MODE_NAMES));

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
                "name tag rank prefix mvp vip color colour whole tag"));

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
                "name tag gradient colour color start first picker"));

        OPTIONS.add(ConfigOption.color("name_tag_gradient_end_color", "Gradient End Color",
                "The gradient's end color.",
                ConfigCategory.COSMETICS,
                c -> c.nameTagGradientEnd,
                v -> { AlpakaConfig.instance.nameTagGradientEnd = v; AlpakaConfig.save(); },
                "name tag gradient colour color end second picker"));

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
                "chroma hat rainbow colors colours glow emissive"));

        OPTIONS.add(ConfigOption.color("chroma_hat_color", "Hat Color",
                "The plain hat's color, used while Chroma Colors is off.",
                ConfigCategory.COSMETICS,
                c -> c.chromaHatColor,
                v -> { AlpakaConfig.instance.chromaHatColor = v; AlpakaConfig.save(); },
                "chroma hat colour color straw plain picker"));

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
                "chroma hat color colour speed rainbow spin fast slow hat color speed"));

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
                "damage tag gradient flow animated colour color shimmer"));


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
                "damage tag gradient colour color picker normal start first"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_normal_end", "Normal Hit End Color",
                "The end color of a normal hit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagNormalEnd,
                v -> { AlpakaConfig.instance.customDamageTagNormalEnd = v; AlpakaConfig.save(); },
                "damage tag gradient colour color picker normal end last"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_crit_start", "Crit Start Color",
                "The start color of a crit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagCritStart,
                v -> { AlpakaConfig.instance.customDamageTagCritStart = v; AlpakaConfig.save(); },
                "damage tag gradient colour color picker crit start first"));

        OPTIONS.add(ConfigOption.color("custom_damage_tag_crit_end", "Crit End Color",
                "The end color of a crit's gradient.",
                ConfigCategory.COSMETICS,
                c -> c.customDamageTagCritEnd,
                v -> { AlpakaConfig.instance.customDamageTagCritEnd = v; AlpakaConfig.save(); },
                "damage tag gradient colour color picker crit end last"));

        // --- 7. SOUND & UTILITY ---

        // The quick command list is edited far more often than any sound is retuned, so it heads
        // the tab rather than sitting below every sound option.
        OPTIONS.add(new ConfigOption("Utility", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("command_wheel_custom_commands", "Command Wheel",
                "A wheel of your own commands on a key. Bind the Command Wheel key in Controls; it is unbound by default.",
                ConfigCategory.SOUND_MISC,
                "Edit Commands",
                parent -> Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.CommandWheelConfigScreen(parent)),
                "quick command wheel commands add remove edit custom list menu keybind quick command menu editor entries unbound controls"));

        OPTIONS.add(new ConfigOption("command_wheel_toggle_mode", "Toggle Command Wheel",
                "Press the key once to open the wheel and click a command, instead of holding the key and releasing it.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.commandWheelToggleMode,
                v -> { AlpakaConfig.instance.commandWheelToggleMode = v; AlpakaConfig.save(); },
                "quick command wheel toggle hold press release click open mode keybind"));

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

        OPTIONS.add(new ConfigOption("custom_sound_hotbar_scroll", "Hotbar Scroll Sound",
                "Plays a custom sound when you scroll or switch hotbar slots.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundHotbarScroll,
                v -> { AlpakaConfig.instance.customSoundHotbarScroll = v; AlpakaConfig.save(); },
                "hotbar scroll slot equip switch audio sound"));

        OPTIONS.add(new ConfigOption("custom_sound_inventory_open_close", "Container Open & Close Sound",
                "Plays a custom sound when you open or close a container.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundInventoryOpenClose,
                v -> { AlpakaConfig.instance.customSoundInventoryOpenClose = v; AlpakaConfig.save(); },
                "inventory open close gui container sound audio custom inventory open/close sound"));

        OPTIONS.add(new ConfigOption("custom_sound_inventory_click", "Inventory Click Sound",
                "Plays a custom sound when you click an item or pick it up.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundInventoryClick,
                v -> { AlpakaConfig.instance.customSoundInventoryClick = v; AlpakaConfig.save(); },
                "inventory click item pickup container sound audio custom"));

        OPTIONS.add(new ConfigOption("Gameplay Sounds", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_rare_drop", "Rare Drop Sound",
                "Plays a sound when you get a rare slayer drop.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundRareDrop,
                v -> { AlpakaConfig.instance.customSoundRareDrop = v; AlpakaConfig.save(); },
                "rare drop sound slayer loot celebration audio slayer rare drop sound"));

        OPTIONS.add(new ConfigOption("custom_sound_notification", "Boss Spawn Sound",
                "Plays a sound when a slayer boss spawns.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundNotification,
                v -> { AlpakaConfig.instance.customSoundNotification = v; AlpakaConfig.save(); },
                "boss spawn sound notification alert audio"));

        OPTIONS.add(new ConfigOption("custom_sound_player_hurt", "Player Hurt Sound",
                "Plays a custom sound when you take damage.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundPlayerHurt,
                v -> { AlpakaConfig.instance.customSoundPlayerHurt = v; AlpakaConfig.save(); },
                "player hurt sound damage hit audio custom"));

        OPTIONS.add(new ConfigOption("custom_sound_blaze_death", "Blaze Death Sound",
                "Plays a custom sound when a blaze you fought dies. Other kills are ignored.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundBlazeDeath,
                v -> { AlpakaConfig.instance.customSoundBlazeDeath = v; AlpakaConfig.save(); },
                "blaze death sound kill slayer audio custom own kills"));

        OPTIONS.add(new ConfigOption("custom_sound_zombie_remedy", "Zombie Remedy Sound",
                "Plays a custom sound for the zombie villager cure effect.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundZombieRemedy,
                v -> { AlpakaConfig.instance.customSoundZombieRemedy = v; AlpakaConfig.save(); },
                "zombie remedy cure villager sound audio custom"));

        OPTIONS.add(new ConfigOption("custom_sound_successful_hit", "Successful Hit Sound",
                "Plays a custom sound on a critical or successful hit.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundSuccessfulHit,
                v -> { AlpakaConfig.instance.customSoundSuccessfulHit = v; AlpakaConfig.save(); },
                "successful hit crit critical attack sound audio custom"));

        OPTIONS.add(new ConfigOption("mute_vanilla_sounds_blaze_slayer", "Mute Blaze Noise",
                "Mutes blazes, fire, lava and ghasts during a Blaze quest. Menu and mod sounds still play.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.muteVanillaSoundsInBlazeSlayer,
                v -> { AlpakaConfig.instance.muteVanillaSoundsInBlazeSlayer = v; AlpakaConfig.save(); },
                "mute silence vanilla game audio blaze slayer quiet boss spawn hear silence blaze slayer noise"));

        OPTIONS.add(new ConfigOption("blaze_mute_all_world_audio", "Mute All World Sounds",
                "Mutes every world sound during a Blaze quest, not just the fight's noise.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.blazeMuteAllWorldAudio,
                v -> { AlpakaConfig.instance.blazeMuteAllWorldAudio = v; AlpakaConfig.save(); },
                "mute silence all world audio blaze slayer quiet silence: all world audio sounds"));

        OPTIONS.add(new ConfigOption("Low Health Warning", ConfigCategory.SOUND_MISC));

        OPTIONS.add(new ConfigOption("custom_sound_low_hp_heartbeat", "Low Health Heartbeat",
                "Plays a heartbeat when your health drops below the threshold.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundLowHpHeartbeat,
                v -> { AlpakaConfig.instance.customSoundLowHpHeartbeat = v; AlpakaConfig.save(); },
                "low hp health heartbeat sound survival audio custom lowhp pulse alert low hp heartbeat sound"));

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
        styleToggle("world_age_blur", "Blur", ConfigCategory.SKYBLOCK, true,
                () -> AlpakaConfig.instance.worldAgeHudBlur, v -> AlpakaConfig.instance.worldAgeHudBlur = v);
        styleToggle("world_age_smooth_font", "Smooth Font", ConfigCategory.SKYBLOCK, false,
                () -> AlpakaConfig.instance.worldAgeHudSmoothFont, v -> AlpakaConfig.instance.worldAgeHudSmoothFont = v);
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

        OPTIONS.add(new ConfigOption("Chat Style", ConfigCategory.VISUALS));
        styleToggle("chat_blur", "Blur", ConfigCategory.VISUALS, true,
                () -> AlpakaConfig.instance.chatBlurEnabled, v -> AlpakaConfig.instance.chatBlurEnabled = v);
        styleToggle("chat_smooth_font", "Smooth Font", ConfigCategory.VISUALS, false,
                () -> AlpakaConfig.instance.menuFontInChat, v -> { AlpakaConfig.instance.menuFontInChat = v; GuiFont.refreshChat(); });

        OPTIONS.add(new ConfigOption("Scoreboard", ConfigCategory.VISUALS));
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

    /** How many non-header options a category holds for the current search term. */
    public static int countOptions(ConfigCategory category, String searchQuery) {
        int count = 0;
        for (ConfigOption option : getOptions(category, searchQuery)) {
            if (option.getType() != ConfigOption.Type.HEADER) count++;
        }
        return count;
    }
}
