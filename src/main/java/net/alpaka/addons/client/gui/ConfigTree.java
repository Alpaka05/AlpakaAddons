package net.alpaka.addons.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The config options arranged the way the redesigned screen shows them: category, then section,
 * then feature, then the feature's own settings.
 *
 * The registry stays one flat list with headers between the sections. What it cannot say is which
 * options belong together - that "Chat Peek In Menus" is part of Chat Peek - and that is what this
 * adds. {@link #MASTERS} names the options that stand as features of their own; every other option
 * is a setting of the feature registered right before it in the same section, unless
 * {@link #PARENTS} says otherwise. A section that opens with a setting rather than a feature - a
 * page of sliders, say - becomes one feature named after the section.
 */
public final class ConfigTree {

    private ConfigTree() {
    }

    /** One feature: the option that switches it (or null for a section of plain settings) and its settings. */
    public static final class Feature {
        public final String title;
        public final String description;
        public final ConfigOption master;
        public final List<ConfigOption> settings = new ArrayList<>();
        public final Section section;

        Feature(String title, String description, ConfigOption master, Section section) {
            this.title = title;
            this.description = description;
            this.master = master;
            this.section = section;
        }

        /** Whether the feature has an on/off switch of its own, drawn in the feature list. */
        public boolean hasToggle() {
            return master != null && master.getType() == ConfigOption.Type.BOOLEAN;
        }

        /** Whether it is switched on; a feature without a switch always counts as on. */
        public boolean isOn() {
            return !hasToggle() || master.getBool();
        }

        /** What the details panel lists: the master too unless it is the switch shown in the list. */
        public List<ConfigOption> panelOptions() {
            if (master == null || hasToggle()) return settings;
            List<ConfigOption> all = new ArrayList<>(settings.size() + 1);
            all.add(master);
            all.addAll(settings);
            return all;
        }

        public boolean matches(String normalizedQuery) {
            if (normalizedQuery.isEmpty()) return true;
            if (title.toLowerCase().contains(normalizedQuery)) return true;
            if (master != null && master.matchesNormalized(normalizedQuery)) return true;
            for (ConfigOption option : settings) {
                if (option.matchesNormalized(normalizedQuery)) return true;
            }
            return false;
        }
    }

    /** A sidebar entry: one header's worth of features. */
    public static final class Section {
        public final String title;
        public final ConfigCategory category;
        public final List<Feature> features = new ArrayList<>();

        Section(String title, ConfigCategory category) {
            this.title = title;
            this.category = category;
        }
    }

    /** A sidebar group: one category's sections. */
    public static final class Group {
        public final ConfigCategory category;
        public final String title;
        public final List<Section> sections = new ArrayList<>();

        Group(ConfigCategory category, String title) {
            this.category = category;
            this.title = title;
        }
    }

    /** The options that are features of their own. Everything else is a setting of the one before. */
    private static final Set<String> MASTERS = Set.of(
            // General
            "allow_api_calls", "update_notifications", "notification_hold", "menu_font", "menu_accent_color",
            "custom_escape_menu", "custom_main_menu", "hud_editor", "disable_all_features",
            // Visuals
            "fullbright", "clean_blaze", "blaze_fire_pits", "stop_blaze_spinning", "hide_hurt_overlay", "hide_mob_deaths",
            "render_hand_third_person", "smooth_perspective", "disable_front_perspective",
            "container_bg_opacity", "inventory_snow", "scrollable_tooltips", "persistent_item_name", "wide_health_bar",
            "smooth_chat", "chat_smooth_scroll", "chat_blur", "chat_search", "expand_chat_history", "compact_chat",
            "mention_notification", "chat_peek", "better_screenshot_message", "chat_tabs",
            "player_model_enabled", "inventory_hud_enabled",
            // Item viewmodel
            "item_size_feature", "item_motion_blur", "item_viewmodel_reset",
            // Block overlay
            "block_overlay_enabled", "block_outline_enabled", "block_fill_enabled", "block_fade_in",
            "block_chroma_enabled", "block_ignore_depth", "block_ignore_plants", "block_hide_on_etherwarp",
            // SkyBlock
            "etherwarp_overlay", "etherwarp_sound",
            "slayer_drop_tracker", "hide_hypixel_drop_message", "slayer_rng_drop_guild_chat", "slayer_since_reply_offer",
            "hide_slayer_chat", "boss_spawn_alert", "slayer_timer", "slayer_hud_enabled",
            "world_age_hud_enabled", "world_age_join_message_enabled", "party_invite_prompt",
            "guild_prefix", "bridge_bot_formatter", "pangolin_highlight_enabled", "only_crit_damage",
            // Cosmetics
            "custom_name_tag", "chroma_hat", "player_scale", "custom_damage_tags",
            // Sound & utility
            "command_wheel_custom_commands", "always_sprint", "custom_sounds",
            "custom_sound_button_click", "custom_sound_hotbar_scroll", "custom_sound_inventory_open_close",
            "custom_sound_inventory_click", "custom_sound_rare_drop", "custom_sound_notification",
            "custom_sound_player_hurt", "custom_sound_blaze_death", "custom_sound_zombie_remedy",
            "custom_sound_successful_hit", "mute_vanilla_sounds_blaze_slayer", "custom_sound_low_hp_heartbeat"
    );

    /** Settings registered away from their feature. */
    private static final Map<String, String> PARENTS = Map.of(
            "smooth_perspective_duration", "smooth_perspective"
    );

    /** Short names for the sidebar's group labels. */
    private static String groupTitle(ConfigCategory category) {
        return switch (category) {
            case GENERAL -> "General";
            case VISUALS -> "Visuals";
            case VIEWMODEL -> "Viewmodel";
            case BLOCK_OVERLAY -> "Block Overlay";
            case COSMETICS -> "Cosmetics";
            case SKYBLOCK -> "SkyBlock";
            case SOUND_MISC -> "Sound & Utility";
        };
    }

    /**
     * Sidebar names for sections whose header repeats the group it sits in: under "Viewmodel",
     * "Viewmodel Adjustments" is just "Adjustments".
     */
    private static final Map<String, String> SECTION_TITLES = Map.of(
            "Viewmodel Adjustments", "Adjustments",
            "Swing Customizations", "Swing",
            "Swing Motion Blur", "Motion Blur",
            "Viewmodel Presets", "Presets",
            "Slayer Session HUD", "Session HUD",
            "Slayer Boss Timer", "Boss Timer"
    );

    /** "Enable Inventory HUD" reads as "Inventory HUD" in a list of features that each switch on. */
    private static String featureTitle(String title) {
        return title.startsWith("Enable ") ? title.substring("Enable ".length()) : title;
    }

    private static List<Group> cached = null;

    /** The whole tree, built once from the registry. */
    public static List<Group> groups() {
        if (cached == null) cached = Collections.unmodifiableList(build());
        return cached;
    }

    private static List<Group> build() {
        List<Group> groups = new ArrayList<>();
        Map<String, Feature> byMasterId = new java.util.HashMap<>();
        for (ConfigCategory category : ConfigCategory.values()) {
            Group group = new Group(category, groupTitle(category));
            Section section = null;
            Feature feature = null;
            for (ConfigOption option : AlpakaConfigRegistry.getOptions(category, "")) {
                if (option.getType() == ConfigOption.Type.HEADER) {
                    section = new Section(SECTION_TITLES.getOrDefault(option.getTitle(), option.getTitle()), category);
                    group.sections.add(section);
                    feature = null;
                    continue;
                }
                if (section == null) {
                    // Options before the first header: the category's own main settings.
                    section = new Section("Main", category);
                    group.sections.add(section);
                }
                String parentId = PARENTS.get(option.getId());
                if (parentId != null && byMasterId.containsKey(parentId)) {
                    byMasterId.get(parentId).settings.add(option);
                } else if (MASTERS.contains(option.getId())) {
                    feature = new Feature(featureTitle(option.getTitle()), option.getDescription(), option, section);
                    section.features.add(feature);
                    byMasterId.put(option.getId(), feature);
                } else {
                    if (feature == null) {
                        feature = new Feature(section.title, "", null, section);
                        section.features.add(feature);
                    }
                    feature.settings.add(option);
                }
            }
            group.sections.removeIf(s -> s.features.isEmpty());
            if (!group.sections.isEmpty()) groups.add(group);
        }
        return groups;
    }
}
