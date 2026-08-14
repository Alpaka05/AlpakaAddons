package net.alpaka.addons.features.guifade;

public class GuiFadeTracker {
    private static long guiOpenTime = 0L;

    public static void onGuiOpened() {
        guiOpenTime = System.currentTimeMillis();
    }

    public static void onGuiClosed() {
        guiOpenTime = 0L;
    }

    public static long getGuiOpenTime() {
        if (guiOpenTime == 0L) {
            guiOpenTime = System.currentTimeMillis();
        }
        return guiOpenTime;
    }
}
