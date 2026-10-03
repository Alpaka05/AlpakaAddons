package net.alpaka.addons.features.perspective;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

/**
 * The glide between first person and the third-person views.
 *
 * The camera's pose is described relative to the player's live view: how far back it sits, and how
 * far its yaw and pitch are turned away from where the player looks. First person is (0, 0, 0), the
 * back view (vanilla's distance, 0, 0) and the front view (vanilla's distance, 180, minus twice the
 * pitch). A transition eases between two of those, and because they are relative the camera keeps
 * following the mouse while it moves. The old version lerped absolute positions and angles, so it
 * cut through the player's head and lagged behind the mouse until it finished.
 *
 * {@code CameraMixin} applies the result right after vanilla aligns the camera, so the FOV, the view
 * matrix and the cull frustum are all built from the smoothed pose rather than vanilla's.
 */
public final class SmoothPerspectiveFeature {
    /** Closer than this, the camera counts as in the head: the body is hidden, as in first person. */
    private static final float DETACHED_DISTANCE = 0.6f;

    /** The pose to show this frame, relative to the live view. */
    public record Pose(float distance, float yawOffset, float pitchOffset) {}

    private static CameraType lastType = null;
    private static boolean active = false;
    private static long startNanos = 0L;
    private static float startDistance, startYaw, startPitch;
    private static float shownDistance, shownYaw, shownPitch;

    private SmoothPerspectiveFeature() {}

    /**
     * Advances the transition for this frame, given the pose vanilla would show now. Returns the pose
     * to show instead, or null to leave vanilla's untouched.
     */
    public static Pose update(CameraType type, float targetDistance, float targetYaw, float targetPitch) {
        if (!AlpakaConfig.instance.smoothPerspectiveEnabled) {
            reset();
            return null;
        }
        long now = System.nanoTime();
        if (lastType == null) {
            lastType = type;
            show(targetDistance, targetYaw, targetPitch);
            return null;
        }
        if (type != lastType) {
            lastType = type;
            startDistance = shownDistance;
            startYaw = shownYaw;
            startPitch = shownPitch;
            startNanos = now;
            active = true;
        }
        if (!active) {
            show(targetDistance, targetYaw, targetPitch);
            return null;
        }

        double durationNanos = Math.max(50, AlpakaConfig.instance.smoothPerspectiveDurationMs) * 1_000_000.0;
        float progress = (float) ((now - startNanos) / durationNanos);
        if (progress >= 1.0f) {
            active = false;
            show(targetDistance, targetYaw, targetPitch);
            return null;
        }
        // Ease-out cubic: fast away from the old view, settling gently into the new one.
        float inverse = 1.0f - progress;
        float eased = 1.0f - inverse * inverse * inverse;

        show(Mth.lerp(eased, startDistance, targetDistance),
                startYaw + Mth.wrapDegrees(targetYaw - startYaw) * eased,
                Mth.lerp(eased, startPitch, targetPitch));
        return new Pose(shownDistance, shownYaw, shownPitch);
    }

    private static void show(float distance, float yaw, float pitch) {
        shownDistance = distance;
        shownYaw = yaw;
        shownPitch = pitch;
    }

    /** Forgets the transition, for a new camera entity or with the feature off. */
    public static void reset() {
        lastType = null;
        active = false;
    }

    /** Whether the camera is out of the player's head mid-transition, so the body should be drawn. */
    public static boolean isDetachedTransition() {
        return active && shownDistance > DETACHED_DISTANCE;
    }

    /**
     * Whether the first-person hand stays hidden this frame: while gliding back into first person the
     * camera is still behind the player, and the hand would float in front of the body.
     */
    public static boolean hidesHand() {
        return isDetachedTransition() && !AlpakaConfig.instance.renderHandInThirdPerson;
    }
}
