package net.alpaka.addons.features.fire;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Hides or softens the fire Minecraft draws for burning things.
 *
 * Two separate effects: the flames wrapped around a burning entity in the world (mobs, other
 * players, and yourself in third person), and the two fire sprites covering the bottom of your
 * screen while you burn in first person. Both are purely cosmetic - the burning, its damage and the
 * fire sound are untouched.
 *
 * The entity flames are dropped on the render state, the same flag vanilla rebuilds every frame
 * from {@code isOnFire()}, so nothing about the entity itself changes.
 */
public final class FireOverlayFeature {

    private FireOverlayFeature() {
    }

    public static void applyEntity(EntityRenderState state) {
        if (AlpakaConfig.instance.hideEntityFire) {
            state.displayFireAnimation = false;
        }
    }

    /** Whether the first-person fire is drawn at all: at zero opacity it is skipped outright. */
    public static boolean showFirstPersonFire() {
        return AlpakaConfig.instance.firstPersonFireOpacity > 0.0f;
    }

    /** Scales the alpha of vanilla's first-person fire color by the opacity setting. */
    public static int firstPersonFireColor(int argb) {
        float opacity = Math.clamp(AlpakaConfig.instance.firstPersonFireOpacity, 0.0f, 1.0f);
        if (opacity >= 1.0f) return argb;
        int alpha = Math.round((argb >>> 24) * opacity);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
