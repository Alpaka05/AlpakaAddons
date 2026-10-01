package net.alpaka.addons.client.hud;

/**
 * Where each HUD starts out, in one place: the config's initial values and the editor's Reset both
 * read these.
 *
 * Every default is anchored to the top-left corner for now, so each offset is simply the position
 * in GUI pixels; once positions are stored as anchor plus offset, these become those offsets. They
 * are laid out so no two of the mod's own HUDs overlap: the boss timer used to sit inside the
 * session HUD's box, and the player model in the middle of the text column.
 */
public final class HudDefaults {
    private HudDefaults() {}

    public static final int WORLD_AGE_X = 10;
    public static final int WORLD_AGE_Y = 10;

    public static final int SLAYER_SESSION_X = 10;
    public static final int SLAYER_SESSION_Y = 60;
    /** The session HUD's tallest layout, nine rows of ten pixels, at scale 1. */
    public static final int SLAYER_SESSION_MAX_HEIGHT = 89;

    public static final int SLAYER_TIMER_X = 10;
    /** Right under the session HUD's tallest layout, with a gap. */
    public static final int SLAYER_TIMER_Y = SLAYER_SESSION_Y + SLAYER_SESSION_MAX_HEIGHT + 6;

    /** To the right of the text column rather than inside it. */
    public static final int PLAYER_MODEL_X = 190;
    public static final int PLAYER_MODEL_Y = 85;

    /** Only used once the inventory HUD is detached from the hotbar. */
    public static final int INVENTORY_X = 10;
    public static final int INVENTORY_Y = 10;
}
