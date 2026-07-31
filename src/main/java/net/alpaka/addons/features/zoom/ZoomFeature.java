package net.alpaka.addons.features.zoom;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ZoomFeature {
    public static KeyMapping zoomKeyBinding;
    private static double zoomFactor = 4.0;
    private static final double DEFAULT_ZOOM = 4.0;
    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 50.0;

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.parse("alpaka:addons"));
        zoomKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.alpaka.zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            category
        ));
    }

    public static boolean isZooming() {
        if (zoomKeyBinding == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        return zoomKeyBinding.isDown();
    }

    public static double getZoomFactor() {
        return zoomFactor;
    }

    public static void onMouseScroll(double yoffset) {
        if (yoffset > 0) {
            zoomFactor = Math.min(MAX_ZOOM, zoomFactor * 1.2);
        } else if (yoffset < 0) {
            zoomFactor = Math.max(MIN_ZOOM, zoomFactor / 1.2);
        }
    }

    public static void resetZoom() {
        zoomFactor = DEFAULT_ZOOM;
    }
}
