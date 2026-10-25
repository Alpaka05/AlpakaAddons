package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.config.AlpakaConfig;

/**
 * The terminal options as the terminal code uses them, with every ranged value clamped: the
 * sliders keep them in range, a hand-edited config does not. Built from the config with
 * {@link #current()}, or by hand in tests, where the config cannot be loaded.
 *
 * @param firstClickGuard  refuse clicks right after the terminal opened
 * @param firstClickMs     how long, 350..800 ms; also {@link #firstClickTicks()} server ticks
 * @param rateGuard        refuse a click within {@link #RATE_MS} of the last accepted one
 * @param blockWrongClicks refuse wrong clicks (never for Click the Button on Time)
 * @param predictClicks    let the highlights move on before the server confirms a click
 * @param hideOtherItems   vanilla chest: do not draw items outside the solution
 * @param customGui        open terminals in the custom window
 * @param guiScale         the custom window's cell scale, 1..3
 */
public record TerminalSettings(boolean firstClickGuard, int firstClickMs, boolean rateGuard, boolean blockWrongClicks,
                               boolean predictClicks, boolean hideOtherItems, boolean customGui, float guiScale) {

    public static final int MIN_FIRST_CLICK_MS = 350;
    public static final int MAX_FIRST_CLICK_MS = 800;
    /** One server tick. */
    public static final long RATE_MS = 50L;
    public static final float MIN_GUI_SCALE = 1.0f;
    public static final float MAX_GUI_SCALE = 3.0f;

    public TerminalSettings {
        firstClickMs = clampFirstClickMs(firstClickMs);
        guiScale = clampGuiScale(guiScale);
    }

    /** The options as they are set now. */
    public static TerminalSettings current() {
        AlpakaConfig c = AlpakaConfig.instance;
        return new TerminalSettings(c.terminalFirstClickGuard, c.terminalFirstClickMs, c.terminalRateGuard,
                c.terminalBlockWrongClicks, c.terminalPredictClicks, c.terminalHideOtherItems,
                c.terminalSolversEnabled && c.terminalCustomGui, c.terminalGuiScale);
    }

    /** Server ticks the first-click guard also waits for: one per 50 ms, rounded up (7..16). */
    public int firstClickTicks() {
        return (firstClickMs + 49) / 50;
    }

    public static int clampFirstClickMs(int ms) {
        return Math.max(MIN_FIRST_CLICK_MS, Math.min(MAX_FIRST_CLICK_MS, ms));
    }

    public static float clampGuiScale(float scale) {
        if (!Float.isFinite(scale)) return 1.5f;
        return Math.max(MIN_GUI_SCALE, Math.min(MAX_GUI_SCALE, scale));
    }
}
