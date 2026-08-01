package net.alpaka.addons.features.zoom;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ZoomFeature {
    public static KeyMapping ZOOM_KEY;
    private static double zoomFactor = 1.0;
    private static boolean isZooming = false;
    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 50.0;
    private static final double DEFAULT_ZOOM = 4.0;

    public static void register() {
        ZOOM_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.alpaka.zoom",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                AlpakaKeyCategory.CATEGORY
        ));
    }

    public static boolean isZooming() {
        if (ZOOM_KEY != null && ZOOM_KEY.isDown()) {
            if (!isZooming) {
                isZooming = true;
                if (zoomFactor == 1.0) {
                    zoomFactor = DEFAULT_ZOOM;
                }
            }
            return true;
        }
        isZooming = false;
        return false;
    }

    public static double getZoomFactor() {
        return zoomFactor;
    }

    public static void adjustZoom(double scrollDelta) {
        if (!isZooming()) return;

        if (scrollDelta > 0) {
            zoomFactor = Math.min(MAX_ZOOM, zoomFactor * 1.2);
        } else if (scrollDelta < 0) {
            zoomFactor = Math.max(MIN_ZOOM, zoomFactor / 1.2);
        }
    }

    public static void onMouseScroll(double scrollDelta) {
        adjustZoom(scrollDelta);
    }

    public static void resetZoom() {
        if (!isZooming) {
            zoomFactor = 1.0;
        }
    }
}
