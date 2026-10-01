package net.alpaka.addons.client.gui;

public final class PloppAnimation {
    private PloppAnimation() {}

    /**
     * Elastic ease-out pop curve for menu opening.
     * Starts at 0.90, overshoots slightly, settles smoothly at 1.00.
     */
    public static float getOpenScale(long openTimeMs) {
        long elapsed = System.currentTimeMillis() - openTimeMs;
        float t = Math.min(1.0f, elapsed / 220.0f);
        if (t >= 1.0f) return 1.0f;
        
        // Elastic ease-out spring
        double c1 = 1.6;
        double c3 = c1 + 1.0;
        double progress = 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
        return (float) (0.90 + 0.10 * progress);
    }

    /**
     * Smooth linear/cubic interpolation for hover scale.
     */
    public static float interpolate(float current, float target, float deltaSec, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(1.0f, deltaSec * speed);
    }
}
