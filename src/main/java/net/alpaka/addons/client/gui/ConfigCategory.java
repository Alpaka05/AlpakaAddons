package net.alpaka.addons.client.gui;

public enum ConfigCategory {
    GENERAL("General", "Use the tabs on the left for everything else.", "Thanks for installing Alpaka Addons"),
    VISUALS("Visuals & Rendering", "Visual effects, overlays, player model & inventory HUD"),
    VIEWMODEL("Item Viewmodel", "Hand positioning, scaling, rotations & swing animations"),
    BLOCK_OVERLAY("Block Overlay", "Custom block outline, colors, chroma & fill effects"),
    COSMETICS("Cosmetics", "Your own animated name tag & the chroma samurai hat"),
    SKYBLOCK("Skyblock", "Hypixel Skyblock utilities & slayer drop trackers"),
    SOUND_MISC("Sound & Utility", "Custom sounds, volume & escape menu settings");

    private final String displayName;
    private final String description;

    /**
     * The line written above a category's options, which is not always its name.
     *
     * General is the tab the config opens on, so it greets rather than labels; every other category
     * heads its page with the same name the sidebar lists it under.
     */
    private final String heading;

    ConfigCategory(String displayName, String description) {
        this(displayName, description, displayName);
    }

    ConfigCategory(String displayName, String description, String heading) {
        this.displayName = displayName;
        this.description = description;
        this.heading = heading;
    }

    public String getHeading() {
        return heading;
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

