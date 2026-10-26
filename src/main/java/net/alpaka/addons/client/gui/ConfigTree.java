package net.alpaka.addons.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

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
        /** The sidebar entry it is listed under; set when the layout places it. */
        public Section section;

        Feature(String title, String description, ConfigOption master) {
            this.title = title;
            this.description = description;
            this.master = master;
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
            if (title.toLowerCase(Locale.ROOT).contains(normalizedQuery)) return true;
            if (master != null && master.matchesNormalized(normalizedQuery)) return true;
            for (ConfigOption option : settings) {
                if (option.matchesNormalized(normalizedQuery)) return true;
            }
            return false;
        }
    }

    /** A sidebar entry: a handful of related features. */
    public static final class Section {
        public final String title;
        public final List<Feature> features = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }
    }

    /** A sidebar group: a label over its sections. */
    public static final class Group {
        public final String title;
        public final List<Section> sections = new ArrayList<>();

        Group(String title) {
            this.title = title;
        }
    }

    /** The options that are features of their own. Everything else is a setting of the one before. */
    private static final Set<String> MASTERS = Set.of(
            // General
            "allow_api_calls", "update_notifications", "menu_font", "menu_theme", "menu_accent_color",
            "custom_escape_menu", "custom_main_menu", "disable_all_features", "reset_all_settings",
            // Visuals
            "fullbright", "clean_blaze", "blaze_fire_pits", "stop_blaze_spinning", "hide_hurt_overlay", "hide_mob_deaths",
            "hide_explosions",
            "render_hand_third_person", "smooth_perspective", "disable_front_perspective", "zoom",
            "sky_effects",
            "container_bg_opacity", "inventory_snow", "scrollable_tooltips", "persistent_item_name", "old_master_stars", "wide_health_bar",
            "better_chat", "chat_search", "chat_copy", "expand_chat_history", "compact_chat", "message_filter",
            "mention_notification", "chat_peek", "better_screenshot_message", "chat_tabs",
            "player_model_enabled", "performance_hud_enabled", "inventory_hud_enabled",
            // Item viewmodel: the adjustments are the modifiers' own settings
            "item_size_feature", "item_starlight", "item_motion_blur", "item_viewmodel_reset",
            // Block overlay: one feature, outline, fill, animation and filters its settings
            "block_overlay_enabled",
            // SkyBlock
            "etherwarp_overlay", "etherwarp_sound",
            "slayer_drop_tracker", "hide_hypixel_drop_message", "slayer_rng_drop_guild_chat",
            "hide_slayer_chat", "boss_spawn_alert", "boss_cocoon_alert", "slayer_timer", "slayer_hud_enabled",
            "world_age_join_message_enabled", "party_invite_prompt", "party_commands", "shorten_warp", "warp_cooldown_queue",
            "guild_prefix", "bridge_bot_formatter", "only_crit_damage", "ragnarock", "deployable_timer", "cache_resource_pack",
            "custom_auction_house", "custom_bazaar", "market_price_history", "custom_sign_input", "item_value", "chest_value",
            // Cosmetics
            "custom_name_tag", "chroma_hat", "aura", "player_scale", "custom_damage_tags",
            // Sound & utility
            "command_wheel_custom_commands", "always_sprint", "keep_cursor_position", "custom_sounds",
            "custom_sound_button_click", "custom_sound_hotbar_scroll", "custom_sound_inventory_open_close",
            "custom_sound_inventory_click", "custom_sound_rare_drop", "custom_sound_notification",
            "custom_sound_player_hurt", "custom_sound_blaze_death", "custom_sound_zombie_remedy",
            "custom_sound_successful_hit", "mute_vanilla_sounds_blaze_slayer", "custom_sound_low_hp_heartbeat"
    );

    /** Settings registered away from their feature. */
    private static final Map<String, String> PARENTS = Map.ofEntries(
            Map.entry("smooth_perspective_duration", "smooth_perspective"),
            Map.entry("custom_main_menu_whats_new", "custom_main_menu"),
            // The chat's background and font are parts of Better Chat.
            Map.entry("chat_blur", "better_chat"),
            Map.entry("chat_smooth_font", "better_chat"),
            // Each HUD's own style switches, registered at the end and placed in its feature.
            Map.entry("slayer_hud_blur", "slayer_hud_enabled"),
            Map.entry("slayer_hud_smooth_font", "slayer_hud_enabled"),
            Map.entry("slayer_timer_blur", "slayer_timer"),
            Map.entry("slayer_timer_smooth_font", "slayer_timer"),
            Map.entry("deployable_timer_blur", "deployable_timer"),
            Map.entry("deployable_timer_smooth_font", "deployable_timer"),
            Map.entry("performance_hud_blur", "performance_hud_enabled"),
            Map.entry("performance_hud_smooth_font", "performance_hud_enabled"),
            Map.entry("inventory_hud_blur", "inventory_hud_enabled"),
            Map.entry("notification_blur", "#Notification Customization"),
            Map.entry("notification_smooth_font", "#Notification Customization")
    );

    /**
     * Registry headers whose settings are a feature of their own rather than more settings of the
     * feature before them, with the name that feature goes by.
     */
    private static final Map<String, String> HEADER_FEATURES = Map.of(
            "Swing Customizations", "Swing",
            "Viewmodel Presets", "Presets",
            "HUD Style", "HUD Style",
            "Notification Customization", "Notifications",
            "Scoreboard", "Scoreboard Style",
            "Nametags", "Nametags",
            "Hide Fire Overlay", "Hide Fire Overlay"
    );

    /** What a feature built from a header is about; it has no master option to take it from. */
    private static final Map<String, String> HEADER_DESCRIPTIONS = Map.of(
            "Swing Customizations", "How the held item moves when you swing: drift, arc and whether the swing always plays out.",
            "Viewmodel Presets", "Save your viewmodel and swing settings to a preset and load them back later.",
            "HUD Style", "Main switches for the blurred panels and the smooth font of every HUD, and how strongly the world is blurred.",
            "Scoreboard", "The sidebar scoreboard's footer, background and font.",
            "Nametags", "How the nametags above players and mobs look, and whether you see your own.",
            "Hide Fire Overlay", "Hides the flames on burning mobs, and fades, lowers or stills the fire on your screen while you burn.",
            "Notification Customization", "How long notifications stay, where they appear and how they look. Send a test to see the result."
    );

    /**
     * The sidebar: groups, their sections, and which features each section lists, in order. A
     * feature is named by its master option's id, or by "#" and the header it was built from.
     * Anything left out ends up under "Other", so a newly registered option is never lost.
     */
    private static final String[][][] LAYOUT = {
            {{"General"},
                    {"Main", "allow_api_calls", "update_notifications", "#Notification Customization",
                            "command_wheel_custom_commands", "always_sprint", "keep_cursor_position", "disable_all_features", "reset_all_settings"},
                    {"Design", "menu_font", "menu_theme", "#HUD Style", "menu_accent_color", "custom_main_menu", "custom_escape_menu"},
                    {"Sounds", "custom_sounds", "custom_sound_button_click", "custom_sound_hotbar_scroll",
                            "custom_sound_inventory_open_close", "custom_sound_inventory_click", "custom_sound_rare_drop",
                            "custom_sound_notification", "custom_sound_player_hurt", "custom_sound_successful_hit",
                            "custom_sound_zombie_remedy", "custom_sound_blaze_death", "custom_sound_low_hp_heartbeat"}},
            {{"Visuals"},
                    {"World", "fullbright", "sky_effects", "block_overlay_enabled", "hide_hurt_overlay", "hide_mob_deaths", "hide_explosions",
                            "#Hide Fire Overlay", "#Nametags", "render_hand_third_person", "smooth_perspective", "disable_front_perspective", "zoom"},
                    {"Interface", "inventory_hud_enabled", "player_model_enabled", "performance_hud_enabled", "container_bg_opacity",
                            "scrollable_tooltips", "persistent_item_name", "old_master_stars", "wide_health_bar", "inventory_snow", "#Scoreboard"},
                    {"Chat", "better_chat", "chat_tabs", "chat_search", "chat_copy", "chat_peek", "compact_chat",
                            "message_filter", "expand_chat_history", "mention_notification", "better_screenshot_message"},
                    {"Viewmodel", "item_size_feature", "#Swing Customizations", "item_starlight", "item_motion_blur",
                            "#Viewmodel Presets", "item_viewmodel_reset"},
                    {"Cosmetics", "custom_name_tag", "custom_damage_tags", "chroma_hat", "aura", "player_scale"}},
            {{"SkyBlock"},
                    {"Slayer", "slayer_hud_enabled", "slayer_timer", "boss_spawn_alert", "boss_cocoon_alert", "slayer_drop_tracker",
                            "hide_hypixel_drop_message", "slayer_rng_drop_guild_chat",
                            "hide_slayer_chat", "clean_blaze", "blaze_fire_pits", "stop_blaze_spinning",
                            "mute_vanilla_sounds_blaze_slayer"},
                    {"Tools", "etherwarp_overlay", "etherwarp_sound", "only_crit_damage",
                            "world_age_join_message_enabled", "ragnarock", "deployable_timer", "cache_resource_pack"},
                    {"Markets", "custom_auction_house", "custom_bazaar", "market_price_history", "custom_sign_input", "item_value",
                            "chest_value"},
                    {"Social", "party_invite_prompt", "party_commands", "shorten_warp", "warp_cooldown_queue", "guild_prefix", "bridge_bot_formatter"}},
    };

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
        // Features first, in registry order: a master opens one, every other option joins the one
        // before it, and a header in HEADER_FEATURES opens one of its own.
        Map<String, Feature> byKey = new java.util.LinkedHashMap<>();
        for (ConfigCategory category : ConfigCategory.values()) {
            Feature feature = null;
            String pendingHeader = null;
            for (ConfigOption option : AlpakaConfigRegistry.getOptions(category, "")) {
                if (option.getType() == ConfigOption.Type.HEADER) {
                    pendingHeader = HEADER_FEATURES.containsKey(option.getTitle()) ? option.getTitle() : null;
                    continue;
                }
                String parentId = PARENTS.get(option.getId());
                if (parentId != null && byKey.containsKey(parentId)) {
                    byKey.get(parentId).settings.add(option);
                } else if (MASTERS.contains(option.getId())) {
                    feature = new Feature(featureTitle(option.getTitle()), option.getDescription(), option);
                    byKey.put(option.getId(), feature);
                } else {
                    if (pendingHeader != null || feature == null) {
                        String header = pendingHeader != null ? pendingHeader : category.getDisplayName();
                        feature = new Feature(HEADER_FEATURES.getOrDefault(header, header),
                                HEADER_DESCRIPTIONS.getOrDefault(header, ""), null);
                        byKey.put("#" + header, feature);
                    }
                    feature.settings.add(option);
                }
                pendingHeader = null;
            }
        }

        // A feature's key always comes first, right under its description, wherever it was
        // registered; the rest keep their registry order (the sort is stable).
        for (Feature feature : byKey.values()) {
            feature.settings.sort(java.util.Comparator.comparing(
                    (ConfigOption option) -> option.getType() != ConfigOption.Type.KEYBIND));
        }

        // Then the sidebar, as LAYOUT has it.
        List<Group> groups = new ArrayList<>();
        Set<String> placed = new java.util.HashSet<>();
        for (String[][] groupSpec : LAYOUT) {
            Group group = new Group(groupSpec[0][0]);
            for (int i = 1; i < groupSpec.length; i++) {
                Section section = new Section(groupSpec[i][0]);
                for (int j = 1; j < groupSpec[i].length; j++) {
                    Feature feature = byKey.get(groupSpec[i][j]);
                    if (feature == null || !placed.add(groupSpec[i][j])) continue;
                    feature.section = section;
                    section.features.add(feature);
                }
                if (!section.features.isEmpty()) group.sections.add(section);
            }
            if (!group.sections.isEmpty()) groups.add(group);
        }
        Section other = new Section("Other");
        for (Map.Entry<String, Feature> entry : byKey.entrySet()) {
            if (placed.contains(entry.getKey())) continue;
            entry.getValue().section = other;
            other.features.add(entry.getValue());
        }
        if (!other.features.isEmpty()) {
            Group last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last == null) groups.add(last = new Group("More"));
            last.sections.add(other);
        }
        return groups;
    }
}
