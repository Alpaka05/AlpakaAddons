package net.alpaka.addons.client.hud;

/**
 * Where each HUD starts out, in one place: the config's initial values and the editor's Reset both
 * read these.
 *
 * Every default is anchored to the top-left corner for now, so each offset is simply the position
 * in GUI pixels; once positions are stored as anchor plus offset, these become those offsets. They
 * are laid out so no two of the mod's own HUDs overlap: the boss timer used to sit inside the
 * session HUD's box (later under it), and the player model in the middle of the text column.
 */
public final class HudDefaults {
    private HudDefaults() {}

    /** The top row's line: the Performance HUD in the corner, then the world age, then the boss timer. */
    private static final int TOP_ROW_Y = 10;

    public static final int PERFORMANCE_X = 10;
    public static final int PERFORMANCE_Y = TOP_ROW_Y;
    /** The Performance HUD's line at its widest, "FPS 1000  Ping 999ms  TPS 20.0", in the pixel font. */
    private static final int PERFORMANCE_MAX_WIDTH = 158;

    /** Beside the Performance HUD, past its widest line with a gap. */
    public static final int WORLD_AGE_X = PERFORMANCE_X + PERFORMANCE_MAX_WIDTH + 10;
    public static final int WORLD_AGE_Y = TOP_ROW_Y;
    /** The world age's line at its widest, "Day: 35", in the pixel font. */
    private static final int WORLD_AGE_MAX_WIDTH = 38;

    /** Beside the world age, past its widest day with a gap; shown only while a boss is up. */
    public static final int SLAYER_TIMER_X = WORLD_AGE_X + WORLD_AGE_MAX_WIDTH + 10;
    public static final int SLAYER_TIMER_Y = TOP_ROW_Y;

    public static final int SLAYER_SESSION_X = 10;
    public static final int SLAYER_SESSION_Y = 60;

    /** To the right of the text column rather than inside it. */
    public static final int PLAYER_MODEL_X = 190;
    public static final int PLAYER_MODEL_Y = 85;

    /** Only used once the inventory HUD is detached from the hotbar. */
    public static final int INVENTORY_X = 10;
    public static final int INVENTORY_Y = 10;
}
