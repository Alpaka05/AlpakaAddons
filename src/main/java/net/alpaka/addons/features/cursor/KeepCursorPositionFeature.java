package net.alpaka.addons.features.cursor;

/**
 * Keeps the cursor where it was when Hypixel swaps one menu for the next, such as backpack or
 * storage pages, instead of letting it jump back to the middle of the screen.
 *
 * Hypixel does not swap the screen in place: it closes the window and opens the next one a tick or
 * two later. Closing grabs the mouse, which centres it, and opening releases it at the centre. If a
 * container opens shortly after a close, the release puts the cursor back where the close found it.
 * Only the local cursor moves; nothing is sent and nothing is clicked.
 */
public final class KeepCursorPositionFeature {

    /** How long after a close a newly opened container still counts as the next page of the same menu. */
    static final long SWITCH_GRACE_MS = 400L;

    private static boolean saved = false;
    private static double savedX;
    private static double savedY;
    private static long savedAt;

    private KeepCursorPositionFeature() {}

    /** The mouse is about to be grabbed: remember where the cursor was in the screen being left. */
    public static void onGrab(double x, double y, long now) {
        saved = true;
        savedX = x;
        savedY = y;
        savedAt = now;
    }

    /**
     * Where the cursor should go when a screen releases the mouse, or null for vanilla's centre. Only
     * containers keep it, so chat or the pause menu still open with the cursor in the middle. A saved
     * position is used once at most.
     */
    public static double[] restoreFor(boolean container, long now, double maxX, double maxY) {
        if (!saved) return null;
        saved = false;
        if (!container) return null;
        if (now - savedAt > SWITCH_GRACE_MS) return null;
        // The window may have been resized in between; never put the cursor outside it.
        return new double[]{clamp(savedX, maxX), clamp(savedY, maxY)};
    }

    private static double clamp(double value, double max) {
        return Math.max(0.0, Math.min(value, max));
    }
}
