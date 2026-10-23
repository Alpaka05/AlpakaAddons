package net.alpaka.addons.client.hud;

/**
 * Where each HUD starts out, in one place: the config's initial values and the editor's Reset both
 * read these.
 *
 * Every default is anchored to the top-left corner for now, so each offset is simply the position
 * in GUI pixels; once positions are stored as anchor plus offset, these become those offsets. They
 * are laid out so no two of the mod's own HUDs overlap: the boss timer used to sit inside the
 * session HUD's box (later under it), and the player model in the middle of the text column. The
 * boss timer is the one exception to the corner: it starts centered, see {@link #CENTERED_X}.
 */
public final class HudDefaults {
    private HudDefaults() {}

    /** The top-left corner: the Performance HUD's line, with the day first. */
    public static final int PERFORMANCE_X = 10;
    public static final int PERFORMANCE_Y = 10;

    /**
     * An x that stands for "centered on the screen" rather than a pixel. Positions are absolute, so a
     * centre depends on the screen's width; the HUD works it out each frame while its x is this.
     * Dragging it in the editor stores a real x again.
     */
    public static final int CENTERED_X = Integer.MIN_VALUE;

    /**
     * Centred along the top, where a running boss is easy to keep an eye on; shown only while a boss
     * is up. Below the line of one boss bar (its name and bar end at 17), which Hypixel shows now
     * and then.
     */
    public static final int SLAYER_TIMER_X = CENTERED_X;
    public static final int SLAYER_TIMER_Y = 22;

    /** Under the Performance HUD's line, and 16 pixels tall, so it ends well above the slayer session HUD. */
    public static final int DEPLOYABLE_TIMER_X = PERFORMANCE_X;
    public static final int DEPLOYABLE_TIMER_Y = PERFORMANCE_Y + 16;

    public static final int SLAYER_SESSION_X = 10;
    public static final int SLAYER_SESSION_Y = 60;

    /**
     * The avatar's feet; at the default size it stands 78 pixels tall and 48 wide. To the right of
     * the text column rather than inside it, and with its head below the top row and the boss timer.
     */
    public static final int PLAYER_MODEL_X = 190;
    public static final int PLAYER_MODEL_Y = 110;

    /** Only used once the inventory HUD is detached from the hotbar. */
    public static final int INVENTORY_X = 10;
    public static final int INVENTORY_Y = 10;
}
