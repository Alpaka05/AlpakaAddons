package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public class LightmapMixin {
    @Unique
    private boolean alpaka$lastFullbright = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(LightmapRenderState state, CallbackInfo ci) {
        if (AlpakaConfig.instance.fullbrightEnabled) {
            state.nightVisionEffectIntensity = 1.0f;
            state.nightVisionColor = new org.joml.Vector3f(1.0f, 1.0f, 1.0f);
            if (!alpaka$lastFullbright) {
                state.needsUpdate = true;
                alpaka$lastFullbright = true;
            }
        } else {
            if (alpaka$lastFullbright) {
                state.needsUpdate = true;
                alpaka$lastFullbright = false;
            }
        }
    }
}
