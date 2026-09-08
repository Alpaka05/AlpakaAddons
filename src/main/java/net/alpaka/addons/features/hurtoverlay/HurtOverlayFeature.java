package net.alpaka.addons.features.hurtoverlay;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Hides the red flash that living entities show while their hurt timer runs.
 *
 * Purely cosmetic: the flag lives on the render state that the renderer rebuilds every frame from
 * {@code hurtTime}/{@code deathTime}, so clearing it there changes nothing about the entity, the
 * hit or the knockback - only how the model is tinted this frame.
 */
public final class HurtOverlayFeature {

    private HurtOverlayFeature() {
    }

    public static void apply(LivingEntityRenderState state) {
        if (AlpakaConfig.instance.hideHurtOverlayEnabled) {
            state.hasRedOverlay = false;
        }
    }
}
