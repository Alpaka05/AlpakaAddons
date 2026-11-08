package net.alpaka.addons.mixin;

import net.alpaka.addons.features.sky.SkyEffectsFeature;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sky Effects after the opaque world: the main pass calls executeSolid right after the solid and
 * cutout terrain, to draw the solid entities and block entities. From its end on, every pixel the
 * opaque world covers has depth, so the skies drawn here are only worked out where they show.
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public class PreparedFrameSkyMixin {

    @Inject(method = "executeSolid", at = @At("TAIL"))
    private void alpaka$skyEffects(CallbackInfo ci) {
        SkyEffectsFeature.afterOpaqueWorld();
    }
}
