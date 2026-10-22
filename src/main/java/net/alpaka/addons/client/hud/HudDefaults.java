package net.alpaka.addons.client.hud;

import net.alpaka.addons.client.gui.HudPanel;

/**
 * Where each HUD starts out, in one place: the config's initial values and the editor's Reset both
 * read these. Only a fresh config and Reset use them; a position the player has saved stays put.
 *
 * Every default is anchored to the top-left corner for now, so each offset is simply the position
 * in GUI pixels; once positions are stored as anchor plus offset, these become those offsets.
 *
 * The layout keeps the mod's own text HUDs short and at the top of the left edge, since the chat
 * fills the left edge from the bottom up (its lowest line ends 40 pixels above the screen's bottom,
 * and ten lines reach 90 pixels above that):
 *
 * <ul>
 *   <li>A top row: the boss timer in the corner, one line, and the world age beside it.</li>
 *   <li>Under that row, the slayer session HUD, which at its tallest - ten rows - still ends above
 *       a full ten-line chat on a screen 270 GUI pixels tall (1080p at GUI scale 4).</li>
 *   <li>The player model right of the session HUD's rows, and low enough that the HUD editor's
 *       header and status line, centred along the top, never cover it.</li>
 * </ul>
 *
 * The panels keep a few pixels between them, so none of these HUDs overlap or touch. The boss
 * timer used to sit under the session HUD, where a session with every row on reached into it.
 */
public final class HudDefaults {
    private HudDefaults() {}

    /** The gap kept between two HUDs' panels, in GUI pixels. */
    private static final int PANEL_GAP = 7;
    /** One line of text: the font's line height. */
    private static final int LINE = 9;

    /** The top-left corner: one line, and shown only while a boss is up. */
    public static final int SLAYER_TIMER_X = 10;
    public static final int SLAYER_TIMER_Y = 10;

    /** Beside the boss timer, past its widest time ("Boss 12m 34s") with a gap. */
    public static final int WORLD_AGE_X = 96;
    public static final int WORLD_AGE_Y = SLAYER_TIMER_Y;

    /**
     * Past the world age at the end of the top row. The icon is taller than a line, so it starts
     * a little higher and its text lines up with the row.
     */
    public static final int DEPLOYABLE_TIMER_X = 150;
    public static final int DEPLOYABLE_TIMER_Y = SLAYER_TIMER_Y - 4;

    public static final int SLAYER_SESSION_X = 10;
    /** Under the top row: that row's panel bottom, the gap, then this panel's top padding. */
    public static final int SLAYER_SESSION_Y = SLAYER_TIMER_Y + LINE + HudPanel.PAD_Y + PANEL_GAP + HudPanel.PAD_Y;

    /**
     * The avatar's feet; at the default size it stands 72 pixels tall and 48 wide. Right of the
     * session HUD's widest rows, and with its top at 60, below the HUD editor's header (to 59).
     */
    public static final int PLAYER_MODEL_X = 160;
    public static final int PLAYER_MODEL_Y = 132;

    /**
     * Only used once the inventory HUD is detached from the hotbar. It then starts over the top
     * row; it is the one HUD without a place of its own, since it is rarely detached.
     */
    public static final int INVENTORY_X = 10;
    public static final int INVENTORY_Y = 10;
}
