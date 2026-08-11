package net.alpaka.addons.client.gui;

import net.alpaka.addons.client.BlockOverlayConfigScreen;
import net.alpaka.addons.client.ColorPickerScreen;
import net.alpaka.addons.client.ItemSizeConfigScreen;
import net.alpaka.addons.client.ItemSwingConfigScreen;
import net.alpaka.addons.client.PlayerModelConfigScreen;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.playermodel.PlayerModelHudEditorScreen;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AlpakaConfigRegistry {
    private static final List<ConfigOption> OPTIONS = new ArrayList<>();

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

        OPTIONS.add(new ConfigOption("inventory_snow", "Inventory Snowflakes",
                "Renders cozy snowflake animations in inventory GUIs.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowEnabled,
                v -> { AlpakaConfig.instance.inventorySnowEnabled = v; AlpakaConfig.save(); },
                "snow winter effect gui inventory snowflakes"));

        OPTIONS.add(new ConfigOption("inventory_snow_speed", "Snow Animation Speed",
                "Speed of the falling snow effect in inventory.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.inventorySnowSpeed,
                v -> { AlpakaConfig.instance.inventorySnowSpeed = v; AlpakaConfig.save(); },
                0.1f, 5.0f, val -> String.format("%.1fx", val),
                "snow speed animation winter velocity"));

        OPTIONS.add(new ConfigOption("expand_chat_history", "Expand Chat History",
                "Increases chat history limit to store more past messages.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.expandChatHistory,
                v -> { AlpakaConfig.instance.expandChatHistory = v; AlpakaConfig.save(); },
                "chat history limit scroll log"));

        OPTIONS.add(new ConfigOption("name_highlighting", "Name Highlighting",
                "Highlights player names in chat and overhead tags.",
                ConfigCategory.VISUALS,
                () -> AlpakaConfig.instance.nameHighlightingEnabled,
                v -> { AlpakaConfig.instance.nameHighlightingEnabled = v; AlpakaConfig.save(); },
                "name tags chat highlight player color"));

        // --- 2. ITEM VIEWMODEL ---
        OPTIONS.add(new ConfigOption("item_size_feature", "Enable Viewmodel Modifiers",
                "Master toggle for custom hand and item adjustments.",
                ConfigCategory.VIEWMODEL,
                () -> AlpakaConfig.instance.itemSizeFeatureEnabled,
                v -> { AlpakaConfig.instance.itemSizeFeatureEnabled = v; AlpakaConfig.save(); },
                "viewmodel item size scale enable custom hand"));

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
        OPTIONS.add(new ConfigOption("Swing Customizations Header", ConfigCategory.VIEWMODEL));

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

        // --- 3. BLOCK OVERLAY ---
        OPTIONS.add(new ConfigOption("block_overlay_enabled", "Enable Block Overlay",
                "Enables custom targeted block highlight rendering.",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockOverlayEnabled,
                v -> { AlpakaConfig.instance.blockOverlayEnabled = v; AlpakaConfig.save(); },
                "block overlay outline highlight render custom enable"));

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
                0.1f, 5.0f, val -> String.format("%.1fx", val),
                "chroma speed rainbow velocity rgb cycle"));

        OPTIONS.add(new ConfigOption("block_ignore_depth", "Ignore Depth (X-Ray)",
                "Renders block overlay through walls (X-Ray mode).",
                ConfigCategory.BLOCK_OVERLAY,
                () -> AlpakaConfig.instance.blockIgnoreDepth,
                v -> { AlpakaConfig.instance.blockIgnoreDepth = v; AlpakaConfig.save(); },
                "ignore depth wall xray see through blocks outline"));

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

        // --- 5. CAMERA & MOTION ---
        OPTIONS.add(new ConfigOption("smooth_perspective", "Smooth Perspective",
                "Smooth transition animation when toggling perspective.",
                ConfigCategory.CAMERA,
                () -> AlpakaConfig.instance.smoothPerspectiveEnabled,
                v -> { AlpakaConfig.instance.smoothPerspectiveEnabled = v; AlpakaConfig.save(); },
                "smooth perspective camera transition f5 third person first person"));

        OPTIONS.add(new ConfigOption("smooth_perspective_duration", "Transition Duration",
                "Duration of perspective switch transition in ms.",
                ConfigCategory.CAMERA,
                () -> (float) AlpakaConfig.instance.smoothPerspectiveDurationMs,
                v -> { AlpakaConfig.instance.smoothPerspectiveDurationMs = Math.round(v); AlpakaConfig.save(); },
                100.0f, 1000.0f, val -> String.format("%d ms", Math.round(val)),
                "smooth perspective camera speed duration transition time ms"));

        // --- 6. SOUND & UTILITY ---
        OPTIONS.add(new ConfigOption("custom_sounds", "Custom Sounds",
                "Plays custom sound effects for button clicks and actions.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsEnabled,
                v -> { AlpakaConfig.instance.customSoundsEnabled = v; AlpakaConfig.save(); },
                "custom sounds audio effects click chime notification"));

        OPTIONS.add(new ConfigOption("custom_sounds_volume", "Custom Sounds Volume",
                "Volume level for custom sound effects.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customSoundsVolume,
                v -> { AlpakaConfig.instance.customSoundsVolume = v; AlpakaConfig.save(); },
                0.0f, 1.0f, val -> String.format("%d%%", Math.round(val * 100)),
                "custom sounds volume loudness audio gain percent"));

        OPTIONS.add(new ConfigOption("custom_escape_menu", "Custom Escape Menu",
                "Enables custom styled Alpaka pause screen (ESC).",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.customEscapeMenuEnabled,
                v -> { AlpakaConfig.instance.customEscapeMenuEnabled = v; AlpakaConfig.save(); },
                "custom escape menu pause screen esc button design"));

        OPTIONS.add(new ConfigOption("slayer_drop_tracker", "Slayer Drop Tracker",
                "Automatically tracks loot drops and kills for Hypixel Slayer bosses.",
                ConfigCategory.SOUND_MISC,
                () -> AlpakaConfig.instance.slayerDropTrackerEnabled,
                v -> { AlpakaConfig.instance.slayerDropTrackerEnabled = v; AlpakaConfig.save(); },
                "slayer drop tracker hypixel loot boss kill stats counter"));
    }

    public static List<ConfigOption> getOptions(ConfigCategory category, String searchQuery) {
        return OPTIONS.stream()
                .filter(option -> (category == ConfigCategory.ALL || option.getCategory() == category))
                .filter(option -> option.matches(searchQuery))
                .collect(Collectors.toList());
    }
}
