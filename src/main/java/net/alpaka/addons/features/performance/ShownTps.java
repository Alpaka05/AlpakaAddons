package net.alpaka.addons.features.performance;

/**
 * Holds the TPS the HUD shows so it doesn't change with every small wobble of the measurement.
 *
 * A server that runs steadily still measures 19.3 one second and 19.6 the next; with Animate
 * Changes on, every one of those tenths rolls the number. Changes of less than
 * {@link #MIN_CHANGE_TENTHS} are never shown, and other small ones at most every
 * {@link #HOLD_MS} ms. A real drop or recovery of at least {@link #JUMP_TENTHS} shows at once, as
 * does the reading turning known or unknown.
 *
 * Pure arithmetic over values and timestamps passed in, so it can be tested without a game.
 */
final class ShownTps {
    /** Smaller changes are noise and never shown. */
    static final int MIN_CHANGE_TENTHS = 3;
    /** A change this big is a real drop or recovery and shows at once. */
    static final int JUMP_TENTHS = 10;
    /** The least time between two small changes. */
    static final long HOLD_MS = 3_000L;

    private int shown = Integer.MIN_VALUE;
    private long shownAt = 0L;

    /** The tenths to show for the measured {@code tenths} at {@code nowMs}; -1 stands for unknown. */
    int update(int tenths, long nowMs) {
        if (accept(tenths, nowMs)) {
            shown = tenths;
            shownAt = nowMs;
        }
        return shown;
    }

    private boolean accept(int tenths, long nowMs) {
        if (tenths == shown) return false;
        if (shown == Integer.MIN_VALUE || tenths < 0 || shown < 0) return true;
        int change = Math.abs(tenths - shown);
        if (change >= JUMP_TENTHS) return true;
        // Full speed is always shown as exactly 20.0, so getting back there is never noise.
        if (tenths == Math.round(TpsEstimator.MAX_TPS * 10.0f)) return nowMs - shownAt >= HOLD_MS;
        return change >= MIN_CHANGE_TENTHS && nowMs - shownAt >= HOLD_MS;
    }
}
