package net.alpaka.addons.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.features.sky.SkyEffectsFeature;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sky Effects inside vanilla's sky pass: the Overworld's over the sun, moon and stars, the End's in
 * place of its textured sky. The Nether has no sky pass; see {@link LevelRendererMixin}.
 */
@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    @Shadow
    @Final
    private RenderTarget renderTarget;

    /**
     * Right after the sun, moon and stars, with the angles they were drawn at. Below the horizon
     * vanilla's dark disc, drawn after this, still covers the effects.
     */
    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void alpaka$overworldSky(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
                                     MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyEffectsFeature.renderOverworld(renderTarget, sunAngle, moonAngle, starAngle, rainBrightness);
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void alpaka$endSky(CallbackInfo ci) {
        if (SkyEffectsFeature.renderEnd(renderTarget)) ci.cancel();
    }
}
