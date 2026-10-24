package net.alpaka.addons.features.zoom;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyBinds;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Zoom: narrows the field of view while the key is held, or from one press to the next in toggle
 * mode.
 *
 * The zoom is applied to the camera's finished FOV (CameraMixin) rather than through the player's
 * FOV modifier as it used to be: vanilla clamps that modifier to 10x and eases it at a fixed half per
 * tick, which left no room for a zoom amount above 10x or an animation of the player's choosing. The
 * animation runs here instead, per frame, on the logarithm of the factor, so going from 2x to 4x
 * takes as long as from 8x to 16x.
 */
public class ZoomFeature {
    public static KeyMapping ZOOM_KEY;

    /** Animation choices, in the order the settings list them. */
    public static final String[] ANIMATIONS = {"Instant", "Smooth", "Bouncy"};
    public static final int ANIMATION_INSTANT = 0;
    public static final int ANIMATION_SMOOTH = 1;
    public static final int ANIMATION_BOUNCY = 2;

    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 50.0;
    private static final double SCROLL_STEP = 1.2;

    /** In toggle mode, whether the last press switched the zoom on. */
    private static boolean toggled = false;
    /** The zoom being aimed at while zooming; starts at the configured amount, scrolling moves it. */
    private static double zoomFactor = 1.0;
    private static boolean wasZooming = false;

    /** The animated factor, as its logarithm, and its speed for the bouncy animation. */
    private static double shownLog = 0.0;
    private static double velocity = 0.0;
    private static long lastFrameNanos = 0L;

    public static void register() {
        ZOOM_KEY = AlpakaKeyBinds.register(new KeyMapping(
                "key.alpaka.zoom",
                InputConstants.Type.KEYSYM,
                // C, as players know it from OptiFine. Vanilla also uses C, but only for saving a
                // hotbar in creative mode.
                GLFW.GLFW_KEY_C,
                net.alpaka.addons.client.AlpakaKeyCategory.CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drained in hold mode too, so a press from then cannot flip the zoom after a switch.
            boolean pressed = false;
            while (ZOOM_KEY.consumeClick()) pressed = true;
            AlpakaConfig config = AlpakaConfig.instance;
            if (!config.zoomEnabled || !config.zoomToggleMode || client.player == null) {
                toggled = false;
            } else if (pressed && client.gui.screen() == null) {
                toggled = !toggled;
            }
        });
    }

    public static boolean isZooming() {
        AlpakaConfig config = AlpakaConfig.instance;
        if (ZOOM_KEY == null || !config.zoomEnabled) return false;
        boolean zooming = config.zoomToggleMode ? toggled : ZOOM_KEY.isDown();
        if (zooming && !wasZooming) zoomFactor = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, config.zoomAmount));
        wasZooming = zooming;
        return zooming;
    }

    /** Whether a scroll goes to the zoom rather than to the hotbar. */
    public static boolean handlesScroll() {
        return AlpakaConfig.instance.zoomScrollEnabled && isZooming();
    }

    public static void onMouseScroll(double scrollDelta) {
        if (scrollDelta > 0) {
            zoomFactor = Math.min(MAX_ZOOM, zoomFactor * SCROLL_STEP);
        } else if (scrollDelta < 0) {
            zoomFactor = Math.max(MIN_ZOOM, zoomFactor / SCROLL_STEP);
        }
    }

    /**
     * What the camera's FOV is divided by this frame: 1 when not zooming, else the zoom as far as its
     * animation has got. Called once per frame by the camera.
     */
    public static double fovDivisor() {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0 : Math.min(0.1, (now - lastFrameNanos) / 1.0e9);
        lastFrameNanos = now;
        double target = Math.log(isZooming() ? zoomFactor : 1.0);
        AlpakaConfig config = AlpakaConfig.instance;
        shownLog = step(shownLog, target, dt, config.zoomAnimation, Math.max(0.01, config.zoomAnimationMs / 1000.0));
        return Math.exp(shownLog);
    }

    /** The zoom currently on screen, for scaling the mouse down with it. */
    public static double shownFactor() {
        return Math.exp(shownLog);
    }

    /** Whether the camera is zoomed in at all right now, the way back out included. */
    public static boolean isZoomedIn() {
        return shownLog > 1.0e-3 || isZooming();
    }

    /** Moves the shown zoom toward the target by one frame of the chosen animation. */
    static double step(double shown, double target, double dt, int animation, double durationSec) {
        switch (animation) {
            case ANIMATION_SMOOTH -> {
                velocity = 0.0;
                // Eases out: 98% of the way there after the chosen duration.
                double next = shown + (target - shown) * (1.0 - Math.exp(-dt * 4.0 / durationSec));
                return Math.abs(target - next) < 1.0e-4 ? target : next;
            }
            case ANIMATION_BOUNCY -> {
                // An underdamped spring that settles in about the chosen duration, overshooting a
                // little first. Small fixed steps keep it stable at a low frame rate.
                double omega = 7.0 / durationSec;
                double damping = 0.45;
                double x = shown;
                double remaining = dt;
                while (remaining > 0.0) {
                    double h = Math.min(remaining, 1.0 / 240.0);
                    double accel = -2.0 * damping * omega * velocity - omega * omega * (x - target);
                    velocity += accel * h;
                    x += velocity * h;
                    remaining -= h;
                }
                // Never wider than the normal view: the bounce on the way out ends at it.
                if (x < 0.0) {
                    x = 0.0;
                    velocity = 0.0;
                }
                if (Math.abs(target - x) < 1.0e-4 && Math.abs(velocity) < 1.0e-3) {
                    velocity = 0.0;
                    return target;
                }
                return x;
            }
            default -> {
                velocity = 0.0;
                return target;
            }
        }
    }
}
