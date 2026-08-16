package net.alpaka.addons.client.gui;

import net.alpaka.addons.client.BlockOverlayConfigScreen;
import net.alpaka.addons.client.ColorPickerScreen;
import net.alpaka.addons.client.ItemSizeConfigScreen;
import net.alpaka.addons.client.ItemSwingConfigScreen;
import net.alpaka.addons.client.PlayerModelConfigScreen;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.playermodel.PlayerModelHudEditorScreen;
import net.alpaka.addons.features.sound.CustomSoundFeature;
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

    private static void registerAllOptions() {
        OPTIONS.clear();

        // --- 1. VISUALS & RENDERING ---
        OPTIONS.add(new ConfigOption("render_hand_third_person", "Show Hand in 3rd Person",
                "Renders held items in 3rd person perspective.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.renderHandInThirdPerson,
                v -> { AlpakaConfig.instance.renderHandInThirdPerson = v; AlpakaConfig.save(); },
                "hand view third person render"));

        OPTIONS.add(new ConfigOption("fullbright", "Fullbright",
                "Enables maximum brightness everywhere in the world without torches.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.fullbrightEnabled,
                v -> { AlpakaConfig.instance.fullbrightEnabled = v; AlpakaConfig.save(); },
                "gamma brightness light fullbright vision"));

        OPTIONS.add(new ConfigOption("clean_blaze", "Clean Blaze",
                "Removes smoke particles from blazes for a clearer view.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.cleanBlazeEnabled,
                v -> { AlpakaConfig.instance.cleanBlazeEnabled = v; AlpakaConfig.save(); },
                "blaze smoke particles clear view"));

        OPTIONS.add(new ConfigOption("stop_blaze_spinning", "Stop Blaze Spinning",
                "Stops blaze rods from spinning around blaze mobs.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.stopBlazeSpinning,
                v -> { AlpakaConfig.instance.stopBlazeSpinning = v; AlpakaConfig.save(); },
                "blaze rods spin spinning rotation animation stop mob"));

        OPTIONS.add(new ConfigOption("inventory_snow", "Inventory Snowflakes",
                "Renders cozy snowflake animations in inventory GUIs.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowEnabled,
                v -> { AlpakaConfig.instance.inventorySnowEnabled = v; AlpakaConfig.save(); },
                "snow winter effect gui inventory snowflakes"));

        OPTIONS.add(new ConfigOption("inventory_snow_speed", "Snow Fall Speed",
                "Speed of falling snow particles in inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowSpeed,
                v -> { AlpakaConfig.instance.inventorySnowSpeed = v; AlpakaConfig.save(); },
                0.1f, 5.0f, val -> String.format("%.1fx", val),
                "snow speed animation winter velocity"));

        OPTIONS.add(new ConfigOption("container_bg_opacity", "Container Background Opacity",
                "Opacity of container background darkening (Default Minecraft: 75%, 100%: Fully Black, 0%: Disabled).",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.containerBgOpacity * 100.0f,
                v -> { AlpakaConfig.instance.containerBgOpacity = v / 100.0f; AlpakaConfig.save(); },
                0.0f, 100.0f, val -> val == 0.0f ? "Disabled (0%)" : String.format("%.0f%%", val),
                "container background darkening opacity overlay tint inventory chest GUI"));

        OPTIONS.add(new ConfigOption("container_bg_fade_in", "Container Background Fade In",
                "Smoothly fades in the dark container background when opening GUIs.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.containerBgFadeInEnabled,
                v -> { AlpakaConfig.instance.containerBgFadeInEnabled = v; AlpakaConfig.save(); },
                "container background fade in smooth transition gui"));

        OPTIONS.add(new ConfigOption("container_bg_fade_duration", "Container Fade Speed",
                "Duration of container background fade-in animation in ms.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.containerBgFadeInDurationMs,
                v -> { AlpakaConfig.instance.containerBgFadeInDurationMs = Math.round(v); AlpakaConfig.save(); },
                50.0f, 1000.0f, val -> String.format("%d ms", Math.round(val)),
                "container background fade speed duration time ms transition"));

        OPTIONS.add(new ConfigOption("expand_chat_history", "Expand Chat History",
                "Increases chat history limit to store more past messages.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.expandChatHistory,
                v -> { AlpakaConfig.instance.expandChatHistory = v; AlpakaConfig.save(); },
                "chat history limit scroll log"));

        OPTIONS.add(new ConfigOption("name_highlighting", "Name Highlighting",
                "Highlights important player and boss names in chat.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.nameHighlightingEnabled,
                v -> { AlpakaConfig.instance.nameHighlightingEnabled = v; AlpakaConfig.save(); },
                "name highlight chat player color tag"));

        OPTIONS.add(new ConfigOption("custom_escape_menu", "Custom Escape Menu",
                "Enables custom styled Alpaka pause screen (ESC).",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customEscapeMenuEnabled,
                v -> { AlpakaConfig.instance.customEscapeMenuEnabled = v; AlpakaConfig.save(); },
                "custom escape menu pause screen esc button design visuals"));

        OPTIONS.add(new ConfigOption("custom_main_menu", "Custom Main Menu",
                "Enables Hypixel-themed custom title screen on launch.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.customMainMenuEnabled,
                v -> { AlpakaConfig.instance.customMainMenuEnabled = v; AlpakaConfig.save(); },
                "custom main menu title screen hypixel start launch background GUI"));

        OPTIONS.add(new ConfigOption("menu_accent_color", "Menu Accent Color",
                "Select custom accent color for all mod GUI borders, headers, and highlights.",
                ConfigCategory.VISUALS,
                "Choose Color",
                parent -> Minecraft.getInstance().setScreen(new ColorPickerScreen(parent, "Menu Accent Color", AlpakaConfig.instance.menuAccentColor, color -> {
                    AlpakaConfig.instance.menuAccentColor = color;
                    AlpakaConfig.save();
                })),
                "menu accent color theme custom picker border highlight gui gold cyan red green blue"));

        OPTIONS.add(new ConfigOption("smooth_perspective", "Smooth Perspective",
                "Smooth transition animation when toggling perspective.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.smoothPerspectiveEnabled,
                v -> { AlpakaConfig.instance.smoothPerspectiveEnabled = v; AlpakaConfig.save(); },
                "smooth perspective camera transition f5 third person first person"));

        OPTIONS.add(new ConfigOption("disable_front_perspective", "Disable Front Perspective",
                "Skips front 3rd-person view when pressing F5 so it only toggles between 1st person and 3rd person back.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.disableFrontPerspective,
                v -> { AlpakaConfig.instance.disableFrontPerspective = v; AlpakaConfig.save(); },
                "disable front perspective skip view f5 third person first person camera"));

        OPTIONS.add(new ConfigOption("smooth_perspective_duration", "Transition Duration",
                "Duration of perspective switch transition in ms.",
                ConfigCategory.VISUALS,
                () -> (float) AlpakaConfig.instance.smoothPerspectiveDurationMs,
                v -> { AlpakaConfig.instance.smoothPerspectiveDurationMs = Math.round(v); AlpakaConfig.save(); },
                100.0f, 1000.0f, val -> String.format("%d ms", Math.round(val)),
                "smooth perspective camera speed duration transition time ms"));

        // --- 2. ITEM VIEWMODEL ---
        OPTIONS.add(new ConfigOption("item_size_feature", "Enable Viewmodel Modifiers",
                "Master toggle for custom hand and item adjustments.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSizeFeatureEnabled,
                v -> { AlpakaConfig.instance.itemSizeFeatureEnabled = v; AlpakaConfig.save(); },
                "viewmodel item size scale enable custom hand"));

        // Adjustments Header
        OPTIONS.add(new ConfigOption("Viewmodel Adjustments", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("item_scale", "Item Scale (Size)",
                "Scale multiplier for held items in hand.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemScale,
                v -> { AlpakaConfig.instance.itemScale = v; AlpakaConfig.save(); },
                0.2f, 2.5f, val -> String.format("%.2fx", val),
                "scale size item hand viewmodel big small"));

        OPTIONS.add(new ConfigOption("item_x_offset", "X Offset (Left/Right)",
                "Horizontal offset of the hand to the left or right.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemXOffset,
                v -> { AlpakaConfig.instance.itemXOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format("%.2f", val),
                "position x offset left right hand"));

        OPTIONS.add(new ConfigOption("item_y_offset", "Y Offset (Up/Down)",
                "Vertical offset of the hand up or down.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemYOffset,
                v -> { AlpakaConfig.instance.itemYOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format("%.2f", val),
                "position y offset up down height hand"));

        OPTIONS.add(new ConfigOption("item_z_offset", "Z Offset (Forward/Back)",
                "Depth offset of the hand forward or backward.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemZOffset,
                v -> { AlpakaConfig.instance.itemZOffset = v; AlpakaConfig.save(); },
                -1.5f, 1.5f, val -> String.format("%.2f", val),
                "position z offset forward back depth hand"));

        OPTIONS.add(new ConfigOption("item_rotation_x", "Rotation Pitch (X)",
                "Pitch tilt of the hand forward or backward.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationX,
                v -> { AlpakaConfig.instance.itemRotationX = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format("%.0f°", val),
                "rotation pitch x angle tilt"));

        OPTIONS.add(new ConfigOption("item_rotation_y", "Rotation Yaw (Y)",
                "Yaw turn of the hand side to side.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationY,
                v -> { AlpakaConfig.instance.itemRotationY = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format("%.0f°", val),
                "rotation yaw y angle turn"));

        OPTIONS.add(new ConfigOption("item_rotation_z", "Rotation Roll (Z)",
                "Roll tilt of the hand side to side.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemRotationZ,
                v -> { AlpakaConfig.instance.itemRotationZ = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format("%.0f°", val),
                "rotation roll z angle side tilt"));

        OPTIONS.add(new ConfigOption("item_swing_speed", "Swing Speed",
                "Speed multiplier for swing animations.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingSpeed,
                v -> { AlpakaConfig.instance.itemSwingSpeed = v; AlpakaConfig.save(); },
                0.1f, 1.5f, val -> String.format("%.2fx", val),
                "swing speed attack animation fast slow"));

        OPTIONS.add(new ConfigOption("item_sway_disabled", "Disable Hand Sway",
                "Disables hand sway when turning the camera.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwayDisabled,
                v -> { AlpakaConfig.instance.itemSwayDisabled = v; AlpakaConfig.save(); },
                "sway hand motion movement camera steady"));

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

        // Swing Customizations Section Header
        OPTIONS.add(new ConfigOption("Swing Customizations", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("swing_drift_x", "Swing Drift X",
                "Lateral swing drift displacement.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftX,
                v -> { AlpakaConfig.instance.swingDriftX = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format("%.1f", val),
                "swing drift x lateral motion"));

        OPTIONS.add(new ConfigOption("swing_drift_y", "Swing Drift Y",
                "Vertical swing drift displacement.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftY,
                v -> { AlpakaConfig.instance.swingDriftY = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format("%.1f", val),
                "swing drift y vertical motion"));

        OPTIONS.add(new ConfigOption("swing_drift_z", "Swing Drift Z",
                "Depth swing drift displacement.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingDriftZ,
                v -> { AlpakaConfig.instance.swingDriftZ = v; AlpakaConfig.save(); },
                -100.0f, 100.0f, val -> String.format("%.1f", val),
                "swing drift z depth motion"));

        OPTIONS.add(new ConfigOption("swing_arc_x", "Swing Arc Pitch",
                "Additional swing rotation angle around X axis.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcX,
                v -> { AlpakaConfig.instance.swingArcX = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format("%.0f°", val),
                "swing arc pitch angle rot"));

        OPTIONS.add(new ConfigOption("swing_arc_y", "Swing Arc Yaw",
                "Additional swing rotation angle around Y axis.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcY,
                v -> { AlpakaConfig.instance.swingArcY = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format("%.0f°", val),
                "swing arc yaw angle rot"));

        OPTIONS.add(new ConfigOption("swing_arc_z", "Swing Arc Roll",
                "Additional swing rotation angle around Z axis.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.swingArcZ,
                v -> { AlpakaConfig.instance.swingArcZ = v; AlpakaConfig.save(); },
                -180.0f, 180.0f, val -> String.format("%.0f°", val),
                "swing arc roll angle rot"));

        OPTIONS.add(new ConfigOption("item_swing_trans_disable", "Disable Swing Translation",
                "Disables position translation during swings.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingTranslationDisabled,
                v -> { AlpakaConfig.instance.itemSwingTranslationDisabled = v; AlpakaConfig.save(); },
                "disable swing translation movement offset shift"));

        OPTIONS.add(new ConfigOption("item_swing_always_finish", "Always Finish Swing",
                "Forces swing animations to always complete fully.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSwingAlwaysFinishEnabled,
                v -> { AlpakaConfig.instance.itemSwingAlwaysFinishEnabled = v; AlpakaConfig.save(); },
                "always finish swing complete attack animation"));

        // Presets Header
        OPTIONS.add(new ConfigOption("Viewmodel Presets", ConfigCategory.VIEWMODEL));

        OPTIONS.add(new ConfigOption("load_preset_1", "Load Preset 1",
                "Applies viewmodel and swing settings from Preset 1.",
                ConfigCategory.VIEWMODEL,
                "Load P1",
                parent -> {
                    AlpakaConfig.instance.loadPreset(0);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset load 1 slot viewmodel hand size offset"));

        OPTIONS.add(new ConfigOption("save_preset_1", "Save to Preset 1",
                "Saves your current viewmodel and swing settings to Preset 1.",
                ConfigCategory.VIEWMODEL,
                "Save P1",
                parent -> {
                    AlpakaConfig.instance.savePreset(0);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset save 1 slot viewmodel hand size offset"));

        OPTIONS.add(new ConfigOption("load_preset_2", "Load Preset 2",
                "Applies viewmodel and swing settings from Preset 2.",
                ConfigCategory.VIEWMODEL,
                "Load P2",
                parent -> {
                    AlpakaConfig.instance.loadPreset(1);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset load 2 slot viewmodel hand size offset"));

        OPTIONS.add(new ConfigOption("save_preset_2", "Save to Preset 2",
                "Saves your current viewmodel and swing settings to Preset 2.",
                ConfigCategory.VIEWMODEL,
                "Save P2",
                parent -> {
                    AlpakaConfig.instance.savePreset(1);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset save 2 slot viewmodel hand size offset"));

        OPTIONS.add(new ConfigOption("load_preset_3", "Load Preset 3",
                "Applies viewmodel and swing settings from Preset 3.",
                ConfigCategory.VIEWMODEL,
                "Load P3",
                parent -> {
                    AlpakaConfig.instance.loadPreset(2);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset load 3 slot viewmodel hand size offset"));

        OPTIONS.add(new ConfigOption("save_preset_3", "Save to Preset 3",
                "Saves your current viewmodel and swing settings to Preset 3.",
                ConfigCategory.VIEWMODEL,
                "Save P3",
                parent -> {
                    AlpakaConfig.instance.savePreset(2);
                    try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                },
                "preset save 3 slot viewmodel hand size offset"));

        // --- 3. BLOCK OVERLAY ---
        OPTIONS.add(new ConfigOption("block_overlay_enabled", "Enable Block Overlay",
                "Enables custom targeted block highlight rendering.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOverlayEnabled,
                v -> { AlpakaConfig.instance.blockOverlayEnabled = v; AlpakaConfig.save(); },
                "block overlay outline highlight render custom enable"));

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
                50.0f, 1000.0f, val -> String.format("%d ms", Math.round(val)),
                "block fade in duration speed time ms transition overlay"));

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
                0.5f, 10.0f, val -> String.format("%.1fpx", val),
                "outline thickness line width border size"));

        OPTIONS.add(new ConfigOption("block_outline_color", "Outline Color",
                "Select color for the block outline.",
                ConfigCategory.BLOCK_OVERLAY,
                "Choose Color",
                parent -> Minecraft.getInstance().setScreen(new ColorPickerScreen(parent, "Block Outline Color", AlpakaConfig.instance.blockOutlineColor, color -> {
                    AlpakaConfig.instance.blockOutlineColor = color;
                    AlpakaConfig.save();
                })),
                "block outline color picker rgb alpha cyan red blue"));

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
                0.1f, 2.0f, val -> String.format("%.1fx", val),
                "chroma speed rainbow velocity rgb cycle"));

        OPTIONS.add(new ConfigOption("block_ignore_depth", "Ignore Depth (X-Ray)",
                "Renders block overlay through walls (X-Ray mode).",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockIgnoreDepth,
                v -> { AlpakaConfig.instance.blockIgnoreDepth = v; AlpakaConfig.save(); },
                "ignore depth wall xray see through blocks outline"));

        OPTIONS.add(new ConfigOption("block_ignore_plants", "Ignore Plants",
                "Disables block highlighting when targeting foliage like grass, flowers, or crops.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockIgnorePlants,
                v -> { AlpakaConfig.instance.blockIgnorePlants = v; AlpakaConfig.save(); },
                "ignore plants foliage grass flowers crops outline disable overlay"));

        OPTIONS.add(new ConfigOption("block_fill_enabled", "Block Fill",
                "Fills target block faces with transparent color.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockFillEnabled,
                v -> { AlpakaConfig.instance.blockFillEnabled = v; AlpakaConfig.save(); },
                "block fill solid transparent face shading color"));

        OPTIONS.add(new ConfigOption("block_fill_color", "Fill Color",
                "Select fill color and transparency for targeted blocks.",
                ConfigCategory.BLOCK_OVERLAY,
                "Choose Color",
                parent -> Minecraft.getInstance().setScreen(new ColorPickerScreen(parent, "Block Fill Color", AlpakaConfig.instance.blockFillColor, color -> {
                    AlpakaConfig.instance.blockFillColor = color;
                    AlpakaConfig.save();
                })),
                "block fill color picker rgb alpha transparency tint"));

        // --- 4. PLAYER MODEL HUD ---
        OPTIONS.add(new ConfigOption("player_model_enabled", "Enable Player Model HUD",
                "Displays a miniature 3D player avatar on the HUD.",
                ConfigCategory.PLAYER_MODEL,
                () -> AlpakaConfig.instance.playerModelEnabled,
                v -> { AlpakaConfig.instance.playerModelEnabled = v; AlpakaConfig.save(); },
                "player model hud avatar skin 3d display preview"));

        OPTIONS.add(new ConfigOption("player_model_only_actions", "Only Show On Action",
                "Only displays avatar while sprinting, flying, or swinging.",
                ConfigCategory.PLAYER_MODEL,
                () -> AlpakaConfig.instance.playerModelOnlyActions,
                v -> { AlpakaConfig.instance.playerModelOnlyActions = v; AlpakaConfig.save(); },
                "only action sprint fly swing dynamic hide avatar"));

        OPTIONS.add(new ConfigOption("player_model_disable_movement", "Disable Movement Sway",
                "Fixes avatar position without walking sway motion.",
                ConfigCategory.PLAYER_MODEL,
                () -> AlpakaConfig.instance.playerModelDisableMovement,
                v -> { AlpakaConfig.instance.playerModelDisableMovement = v; AlpakaConfig.save(); },
                "disable movement sway steady static hud player model"));

        OPTIONS.add(new ConfigOption("player_model_hide_armor", "Hide Armor on Model",
                "Hides armor pieces from rendering on the player model HUD.",
                ConfigCategory.PLAYER_MODEL,
                () -> AlpakaConfig.instance.playerModelHideArmor,
                v -> { AlpakaConfig.instance.playerModelHideArmor = v; AlpakaConfig.save(); },
                "player model hide armor helmet chestplate leggings boots overlay"));

        OPTIONS.add(new ConfigOption("player_model_show_in_guis", "Show in GUIs / Menus",
                "Renders player model HUD even when container or menu GUIs are open.",
                ConfigCategory.PLAYER_MODEL,
                () -> AlpakaConfig.instance.playerModelShowInGuis,
                v -> { AlpakaConfig.instance.playerModelShowInGuis = v; AlpakaConfig.save(); },
                "player model show in guis menus screen open container inventory HUD"));

        OPTIONS.add(new ConfigOption("player_model_scale", "Model Scale",
                "Size scale of the HUD player avatar.",
                ConfigCategory.PLAYER_MODEL,
                () -> (float) AlpakaConfig.instance.playerModelScale,
                v -> { AlpakaConfig.instance.playerModelScale = Math.round(v); AlpakaConfig.save(); },
                10.0f, 100.0f, val -> String.format("%d%%", Math.round(val)),
                "player model scale size zoom width height"));

        OPTIONS.add(new ConfigOption("player_model_hud_editor", "Configure HUD Position",
                "Opens visual editor to drag and position HUD avatar.",
                ConfigCategory.PLAYER_MODEL,
                "Adjust Position",
                parent -> Minecraft.getInstance().setScreen(new PlayerModelHudEditorScreen(parent)),
                "player model position edit dragging hud screen drag move"));

        // --- 5. SKYBLOCK ---
        OPTIONS.add(new ConfigOption("slayer_drop_tracker", "Slayer Drop Tracker",
                "Automatically tracks loot drops and kills for Hypixel Slayer bosses.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.slayerDropTrackerEnabled,
                v -> { AlpakaConfig.instance.slayerDropTrackerEnabled = v; AlpakaConfig.save(); },
                "slayer drop tracker hypixel loot boss kill stats counter skyblock"));

        OPTIONS.add(new ConfigOption("world_age_hud_enabled", "Enable World Age (Day) HUD",
                "Displays the current server's world age in days.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.worldAgeHudEnabled,
                v -> { AlpakaConfig.instance.worldAgeHudEnabled = v; AlpakaConfig.save(); },
                "world age day hud server days time skyblock color red orange green"));

        OPTIONS.add(new ConfigOption("world_age_hud_editor", "Configure World Age Position",
                "Opens visual editor to drag, position, and resize the World Age HUD text.",
                ConfigCategory.SKYBLOCK,
                "Adjust Position",
                parent -> Minecraft.getInstance().setScreen(new net.alpaka.addons.features.worldage.WorldAgeHudEditorScreen(parent)),
                "world age position edit dragging hud screen drag move resize scale"));

        OPTIONS.add(new ConfigOption("world_age_join_message_enabled", "Server Age Join Message",
                "Shows a chat notification with the server age and recent visit status when joining a server.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.worldAgeJoinMessageEnabled,
                v -> { AlpakaConfig.instance.worldAgeJoinMessageEnabled = v; AlpakaConfig.save(); },
                "world age server join chat message notification alert day time"));

        OPTIONS.add(new ConfigOption("world_age_recent_threshold", "Recent Visit Window",
                "Time window threshold in seconds to report if you recently visited this server.",
                ConfigCategory.SKYBLOCK,
                () -> (float) AlpakaConfig.instance.worldAgeRecentThresholdSec,
                v -> { AlpakaConfig.instance.worldAgeRecentThresholdSec = Math.round(v); AlpakaConfig.save(); },
                10.0f, 300.0f, val -> {
                    int sec = Math.round(val);
                    if (sec >= 60) {
                        int m = sec / 60;
                        int s = sec % 60;
                        return s > 0 ? String.format("%dm %ds", m, s) : String.format("%dm", m);
                    }
                    return String.format("%ds", sec);
                },
                "world age recent visit threshold window seconds time minutes slider"));

        OPTIONS.add(new ConfigOption("only_crit_damage", "Only Show Crit Damage",
                "Hides non-crit, fire, poison, and secondary ability damage nametags in Skyblock, keeping only critical hits.",
                ConfigCategory.SKYBLOCK,
                () -> AlpakaConfig.instance.onlyCritDamageEnabled,
                v -> { AlpakaConfig.instance.onlyCritDamageEnabled = v; AlpakaConfig.save(); },
                "damage indicator tags numbers hide non-crit fire poison abilities crit skyblock"));

        // --- 6. SOUND & UTILITY ---
        OPTIONS.add(new ConfigOption("custom_sounds", "Master Custom Sounds",
                "Master switch for custom sound effects throughout the mod.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsEnabled,
                v -> { AlpakaConfig.instance.customSoundsEnabled = v; AlpakaConfig.save(); },
                "custom sounds audio effects click chime notification master"));

        OPTIONS.add(new ConfigOption("custom_sounds_volume", "Custom Sounds Volume",
                "Volume level for custom sound effects.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsVolume,
                v -> { AlpakaConfig.instance.customSoundsVolume = v; AlpakaConfig.save(); },
                0.0f, 1.0f, val -> String.format("%d%%", Math.round(val * 100)),
                "custom sounds volume loudness audio gain percent"));

        OPTIONS.add(new ConfigOption("custom_sound_button_click", "UI Button Click Sound",
                "Plays custom chime when clicking buttons in menus.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundButtonClick,
                v -> { AlpakaConfig.instance.customSoundButtonClick = v; AlpakaConfig.save(); },
                "button click sound ui audio feedback chime"));

        OPTIONS.add(new ConfigOption("custom_sound_hotbar_scroll", "Hotbar Scroll Sound",
                "Plays custom sound effect when scrolling or switching hotbar slots.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundHotbarScroll,
                v -> { AlpakaConfig.instance.customSoundHotbarScroll = v; AlpakaConfig.save(); },
                "hotbar scroll slot equip switch audio sound"));

        OPTIONS.add(new ConfigOption("custom_sound_rare_drop", "Slayer Rare Drop Sound",
                "Plays sound effect when getting a rare Slayer loot drop.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundRareDrop,
                v -> { AlpakaConfig.instance.customSoundRareDrop = v; AlpakaConfig.save(); },
                "rare drop sound slayer loot celebration audio"));

        OPTIONS.add(new ConfigOption("custom_sound_notification", "Boss Spawn Sound",
                "Plays sound effect when a Slayer boss spawns.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundNotification,
                v -> { AlpakaConfig.instance.customSoundNotification = v; AlpakaConfig.save(); },
                "boss spawn sound notification alert audio"));

        OPTIONS.add(new ConfigOption("custom_sound_player_hurt", "Player Hurt Sound",
                "Plays custom sound effect when taking damage.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundPlayerHurt,
                v -> { AlpakaConfig.instance.customSoundPlayerHurt = v; AlpakaConfig.save(); },
                "player hurt sound damage hit audio custom"));

        OPTIONS.add(new ConfigOption("custom_sound_inventory_open_close", "Inventory Open/Close Sound",
                "Plays custom sound effect when opening or closing container GUIs.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundInventoryOpenClose,
                v -> { AlpakaConfig.instance.customSoundInventoryOpenClose = v; AlpakaConfig.save(); },
                "inventory open close gui container sound audio custom"));

        OPTIONS.add(new ConfigOption("custom_sound_low_hp_heartbeat", "Low HP Heartbeat Sound",
                "Plays low health heartbeat sound when below configured HP percentage in survival mode.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundLowHpHeartbeat,
                v -> { AlpakaConfig.instance.customSoundLowHpHeartbeat = v; AlpakaConfig.save(); },
                "low hp health heartbeat sound survival audio custom lowhp pulse alert"));

        OPTIONS.add(new ConfigOption("low_hp_heartbeat_threshold", "Heartbeat HP Threshold",
                "Percentage of max health at which low HP heartbeat sound starts playing.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.lowHpHeartbeatThreshold * 100.0f,
                v -> { AlpakaConfig.instance.lowHpHeartbeatThreshold = v / 100.0f; AlpakaConfig.save(); },
                5.0f, 80.0f, val -> String.format("%.0f%%", val),
                "heartbeat low hp health percentage threshold trigger sound volume"));

        OPTIONS.add(new ConfigOption("command_wheel_custom_commands", "Quick Command Menu",
                "Add, edit, or remove custom commands for the quick command overlay.",
                ConfigCategory.SOUND_MISC,
                "Edit Commands",
                parent -> Minecraft.getInstance().setScreen(new net.alpaka.addons.client.CommandWheelConfigScreen(parent)),
                "quick command wheel commands add remove edit custom list menu keybind"));

        OPTIONS.add(new ConfigOption("disable_all_features", "Disable All Features",
                "Disables every feature in the mod after confirmation.",
                ConfigCategory.SOUND_MISC,
                "Disable All",
                parent -> Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                AlpakaConfig.instance.disableAllFeatures();
                            }
                            Minecraft.getInstance().setScreen(parent);
                        },
                        Component.literal("§c§lDisable All Features"),
                        Component.literal("Are you sure you want to disable all features of Alpaka Addons?"),
                        Component.literal("§cDisable All"),
                        Component.literal("Cancel")
                )),
                "disable all features turn off reset everywhere confirm prompt"));

        buildCategoryCache();
    }

    private static void buildCategoryCache() {
        CATEGORY_CACHE.clear();
        for (ConfigCategory cat : ConfigCategory.values()) {
            List<ConfigOption> list = OPTIONS.stream()
                    .filter(opt -> cat == ConfigCategory.ALL || opt.getCategory() == cat)
                    .collect(Collectors.toList());
            CATEGORY_CACHE.put(cat, java.util.Collections.unmodifiableList(list));
        }
    }

    public static List<ConfigOption> getOptions(ConfigCategory category, String searchQuery) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return CATEGORY_CACHE.getOrDefault(category, java.util.Collections.emptyList());
        }
        return CATEGORY_CACHE.getOrDefault(category, java.util.Collections.emptyList()).stream()
                .filter(option -> option.matches(searchQuery))
                .collect(Collectors.toList());
    }
}
