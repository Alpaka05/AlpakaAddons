package net.alpaka.addons.features.playerscale;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.nametag.CustomNameTagFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

/**
 * Draws player models stretched or shrunk along each axis, in the world only.
 *
 * Purely visual. The hitbox, eye height, camera, reach and everything the server knows stay
 * vanilla, and nothing is sent, so nobody else sees it. The player's own model is scaled whenever
 * the feature is on - visible in third person and the F5 front view - and every other player as an
 * option on top.
 *
 * ### Where it hooks in
 *
 * The scale is applied at the tail of {@code AvatarRenderer.scale}, which vanilla calls after
 * {@code setupRotations} and the y-flip. That makes the axes the body's own - X is width across the
 * shoulders, Y height, Z front to back - and they turn with the player rather than staying aligned to
 * the world. The model's origin at that point is the feet, and the translation that lifts the model
 * onto them comes afterwards and is scaled along, so a taller player still stands on the ground rather
 * than sinking into it or floating.
 *
 * Whether a state gets scaled is decided at the head of {@code AvatarRenderer.extractRenderState},
 * where the entity is known, and stored on the state through {@link PlayerScaleRenderState}; the
 * scale method only reads it back. The head rather than the tail because vanilla extracts the shadow
 * inside that method and asks {@code getShadowRadius} for the footprint, which
 * {@link #shadowFactor} widens along with the body.
 *
 * GUI previews - the inventory screen's avatar and this mod's player model HUD - go through the same
 * renderer but not through {@code EntityRenderDispatcher.extractEntity}, so the world-extraction flag
 * the name tag feature maintains tells them apart, and they keep vanilla's size.
 */
public final class PlayerScaleFeature {

    private PlayerScaleFeature() {
    }

    public static final float MIN_SCALE = 0.1f;
    public static final float MAX_SCALE = 3.0f;

    /** Sliders snap to this step so that exactly 1.00x is reachable by dragging. */
    private static final float STEP = 0.05f;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.playerScaleEnabled;
    }

    /** Rounds a slider value to the nearest {@link #STEP}. */
    public static float snap(float value) {
        return Math.round(value / STEP) * STEP;
    }

    /**
     * Whether this avatar is drawn scaled: the feature is on, the world (not a GUI) is extracting it,
     * and it is either the local player or, with the option on, anybody else.
     *
     * "Anybody else" is every avatar the player renderer draws, which on a server is the other
     * players and the player-shaped NPCs, and in singleplayer includes mannequins.
     */
    private static boolean appliesTo(Avatar entity) {
        if (!isEnabled()) return false;
        if (!CustomNameTagFeature.getExtractingWorldEntity()) return false;
        if (entity == Minecraft.getInstance().player) return true;
        return AlpakaConfig.instance.playerScaleOthers;
    }

    /** Head of {@code AvatarRenderer.extractRenderState}: decide, and remember on the state. */
    public static void extract(Avatar entity, AvatarRenderState state) {
        PlayerScaleRenderState scaled = (PlayerScaleRenderState) state;
        if (appliesTo(entity)) {
            AlpakaConfig cfg = AlpakaConfig.instance;
            scaled.alpaka$setScale(clamp(cfg.playerScaleX), clamp(cfg.playerScaleY), clamp(cfg.playerScaleZ));
        } else {
            // Always written, never assumed: a state that is not scaled this time must not keep the
            // answer from an earlier extraction.
            scaled.alpaka$setScale(1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * Tail of {@code AvatarRenderer.extractRenderState}: lifts the name tag with the head.
     *
     * The tag is drawn from the state's attachment point after the model's pose stack has been popped,
     * so it does not scale with the model on its own. The attachment is relative to the feet, which
     * is also what the model scales about, so scaling its height is exactly right. Both vanilla's tag
     * and the mod's own animated one read this field.
     */
    public static void adjustNameTag(AvatarRenderState state) {
        PlayerScaleRenderState scaled = (PlayerScaleRenderState) state;
        float y = scaled.alpaka$getScaleY();
        Vec3 attachment = state.nameTagAttachment;
        if (attachment == null || y == 1.0f) return;
        state.nameTagAttachment = new Vec3(attachment.x, attachment.y * y, attachment.z);
    }

    /** Tail of {@code AvatarRenderer.scale}: the scale itself. */
    public static void applyScale(AvatarRenderState state, PoseStack poseStack) {
        PlayerScaleRenderState scaled = (PlayerScaleRenderState) state;
        float x = scaled.alpaka$getScaleX();
        float y = scaled.alpaka$getScaleY();
        float z = scaled.alpaka$getScaleZ();
        if (x == 1.0f && y == 1.0f && z == 1.0f) return;
        poseStack.scale(x, y, z);
    }

    /**
     * Factor for the shadow radius of a scaled player: the mean of the two horizontal axes, since
     * the shadow is a disc and cannot be oval.
     */
    public static float shadowFactor(EntityRenderState state) {
        if (!(state instanceof PlayerScaleRenderState scaled)) return 1.0f;
        return (scaled.alpaka$getScaleX() + scaled.alpaka$getScaleZ()) / 2.0f;
    }

    private static float clamp(float value) {
        return Mth.clamp(value, MIN_SCALE, MAX_SCALE);
    }
}
