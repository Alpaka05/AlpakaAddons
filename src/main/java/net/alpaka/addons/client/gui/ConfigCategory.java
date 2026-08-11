package net.alpaka.addons.client.gui;

public enum ConfigCategory {
    ALL("All Settings", "Overview of all available features and settings"),
    VISUALS("Visuals & Rendering", "Visual effects, fullbright, snowflakes & highlights"),
    VIEWMODEL("Item Viewmodel", "Hand positioning, scaling, rotations & swing animations"),
    BLOCK_OVERLAY("Block Overlay", "Custom block outline, colors, chroma & fill effects"),
    PLAYER_MODEL("Player Model HUD", "Configure 3D player character preview on HUD"),
    SKYBLOCK("Skyblock", "Hypixel Skyblock utilities & slayer drop trackers"),
    SOUND_MISC("Sound & Utility", "Custom sounds, volume & escape menu settings");

    private final String displayName;
    private final String description;

    ConfigCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getIcon() {
        return "";
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getFullLabel() {
        return displayName;
    }
}

