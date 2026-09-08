package net.alpaka.addons.features.blaze;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.nametag.CustomNameTagFeature;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

/**
 * Draws blazes larger or smaller than vanilla, uniformly, in the world only.
 *
 * Purely visual, like the player scale: the hitbox and everything the server knows stay vanilla, so
 * a shrunken blaze still takes hits over its full real size and an enlarged one only where its body
 * actually is.
 *
 * Unlike the player scale this needs no hook of its own. {@code LivingEntityRenderState.scale} is
 * the factor vanilla applies for the size attribute, uniformly and about the feet, so multiplying it
 * at the tail of {@code LivingEntityRenderer.extractRenderState} - after vanilla has filled it from
 * the entity - is the whole feature. The name tag attachment is lifted along for a blaze that has a
 * name of its own; Hypixel's boss labels are separate armor stands and stay where they are.
 */
public final class BlazeScaleFeature {

    private BlazeScaleFeature() {
    }

    public static final float MIN_SCALE = 0.25f;
    public static final float MAX_SCALE = 3.0f;

    /** Sliders snap to this step so that exactly 1.00x is reachable by dragging. */
    private static final float STEP = 0.05f;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.blazeScaleEnabled;
    }

    /** Rounds a slider value to the nearest {@link #STEP}. */
    public static float snap(float value) {
        return Math.round(value / STEP) * STEP;
    }

    /** Tail of {@code LivingEntityRenderer.extractRenderState}. */
    public static void apply(LivingEntity entity, LivingEntityRenderState state) {
        if (!isEnabled() || !(entity instanceof Blaze)) return;
        if (!CustomNameTagFeature.getExtractingWorldEntity()) return;
        float factor = Mth.clamp(AlpakaConfig.instance.blazeScale, MIN_SCALE, MAX_SCALE);
        if (factor == 1.0f) return;
        state.scale *= factor;
        Vec3 attachment = state.nameTagAttachment;
        if (attachment != null) {
            state.nameTagAttachment = new Vec3(attachment.x, attachment.y * factor, attachment.z);
        }
    }
}
