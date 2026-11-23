package net.alpaka.addons.features.teleportwarp;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Smooth Teleport, part of Teleport Warp: after an item teleport the camera glides from where the
 * player stood to where they landed instead of jumping there, like Skyblocker's responsive Smooth
 * AOTE.
 *
 * Only the picture moves. The server's teleport is applied by vanilla the moment it arrives, so
 * the player's real position, hitbox, aim and movement are already at the destination; the camera
 * is just drawn a shrinking distance behind it for the length of the glide (CameraMixin), and in
 * third person the player's model with it (EntityRendererMixin). It waits for the server instead
 * of predicting the destination, so it never shows a wrong place and needs no extra packets.
 */
public final class SmoothTeleport {
    /** A move farther than this is not glided; no teleport item goes that far. */
    private static final double MAX_DISTANCE = 100.0;

    /** Where the camera is drawn relative to the player when the glide starts. */
    private static Vec3 startOffset = Vec3.ZERO;
    /** When the glide started, or -1 while none runs. */
    private static long startNanos = -1L;
    private static ClientLevel level;

    private SmoothTeleport() {}

    /** The server moved the player by an item teleport from {@code from} to {@code to}. */
    static void onTeleport(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (!AlpakaConfig.instance.teleportWarpSmooth || from.distanceToSqr(to) > MAX_DISTANCE * MAX_DISTANCE) return;
        // Chained teleports start from where the camera is drawn now, so it never jumps.
        startOffset = offset().add(from.subtract(to));
        startNanos = System.nanoTime();
        level = mc.level;
    }

    /** How far behind the player the camera is drawn right now; zero outside a glide. */
    public static Vec3 offset() {
        if (startNanos < 0) return Vec3.ZERO;
        Minecraft mc = Minecraft.getInstance();
        float glide = Mth.clamp(AlpakaConfig.instance.teleportWarpSmoothTime, 50.0f, 500.0f) / 1000.0f;
        float elapsed = (System.nanoTime() - startNanos) / 1.0e9f;
        if (elapsed >= glide || mc.level != level || !AlpakaConfig.instance.teleportWarpEnabled
                || !AlpakaConfig.instance.teleportWarpSmooth) {
            startNanos = -1L;
            return Vec3.ZERO;
        }
        return startOffset.scale(remaining(elapsed / glide));
    }

    /** How much of the way is still ahead at {@code progress}: an ease-out, fast first and settling. */
    static float remaining(float progress) {
        float x = 1.0f - Mth.clamp(progress, 0.0f, 1.0f);
        return x * x * x;
    }

    /** Whether the camera following this entity is drawn behind it right now. */
    public static boolean applies(Entity entity) {
        return startNanos >= 0 && entity != null && entity == Minecraft.getInstance().player;
    }
}
