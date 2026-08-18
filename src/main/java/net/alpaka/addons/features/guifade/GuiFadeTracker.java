package net.alpaka.addons.features.guifade;

/**
 * Tracks when the current GUI "session" began, so the container background can fade in once when
 * a GUI is first opened rather than on every screen that follows it.
 */
public final class GuiFadeTracker {

    /**
     * How long after a GUI closes a newly opened one still counts as the same session.
     *
     * Navigating Hypixel Skyblock menus does not swap one screen for another: the server closes the
     * window and opens the replacement a tick or two later, so the client briefly has no screen at
     * all. Without this grace period every backpack page and every menu click would look like a
     * fresh open and replay the fade.
     */
    private static final long SWITCH_GRACE_MS = 400L;

    private static long guiOpenTime = 0L;
    private static long guiCloseTime = 0L;

    private GuiFadeTracker() {}

    public static void onGuiOpened() {
        long now = System.currentTimeMillis();

        // Reopening within the grace window is a menu switch, not a new session: keep the original
        // start time so the backdrop stays at full opacity instead of fading in again.
        if (guiCloseTime != 0L && now - guiCloseTime <= SWITCH_GRACE_MS) {
            return;
        }
        guiOpenTime = now;
    }

    public static void onGuiClosed() {
        guiCloseTime = System.currentTimeMillis();
    }

    public static long getGuiOpenTime() {
        if (guiOpenTime == 0L) {
            guiOpenTime = System.currentTimeMillis();
        }
        return guiOpenTime;
    }
}
