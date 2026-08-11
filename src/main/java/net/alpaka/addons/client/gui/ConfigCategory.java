package net.alpaka.addons.client.gui;

public enum ConfigCategory {
    ALL("🔍", "Alle Settings", "Alle Features und Einstellungen im Überblick"),
    VISUALS("🎨", "Visuals & Rendering", "Optische Effekte, Brightness, Snowflakes & Highlights"),
    VIEWMODEL("⚔️", "Item Viewmodel", "Hand-Positionierung, Skalierung, Rotation & Swing-Animationen"),
    BLOCK_OVERLAY("🧱", "Block Overlay", "Custom Block-Outline, Colors, Chroma & Fill-Effekte"),
    PLAYER_MODEL("🧍", "Player Model HUD", "Spieler-Charakter Vorschau im HUD konfigurieren"),
    CAMERA("🎥", "Kamera & Motion", "Smooth Perspective Switch & Umschalt-Animationen"),
    SOUND_MISC("🔊", "Sound & Utility", "Custom Sounds, Lautstärke, Escape Menu & Slayer Tracker");

    private final String icon;
    private final String displayName;
    private final String description;

    ConfigCategory(String icon, String displayName, String description) {
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getFullLabel() {
        return icon + "  " + displayName;
    }
}
