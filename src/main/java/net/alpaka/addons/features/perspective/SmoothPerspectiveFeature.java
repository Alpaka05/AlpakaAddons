package net.alpaka.addons.features.perspective;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

public class SmoothPerspectiveFeature {
    private static CameraType lastCameraType = null;
    private static long transitionStartTime = 0;
    private static final float START_SCALE = 0.18f;

    public static float getTransitionScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return 1.0f;

        CameraType currentType = mc.options.getCameraType();
        if (lastCameraType == null) {
            lastCameraType = currentType;
            return 1.0f;
        }

        if (currentType != lastCameraType) {
            lastCameraType = currentType;
            transitionStartTime = System.currentTimeMillis();
        }

        if (transitionStartTime == 0) return 1.0f;

        long duration = Math.max(50, AlpakaConfig.instance.smoothPerspectiveDurationMs);
        long elapsed = System.currentTimeMillis() - transitionStartTime;
        if (elapsed >= duration) {
            transitionStartTime = 0;
            return 1.0f;
        }

        float progress = (float) elapsed / duration;
        float easeOut = 1.0f - (float) Math.pow(1.0f - progress, 3);

        return START_SCALE + (1.0f - START_SCALE) * easeOut;
    }
}
