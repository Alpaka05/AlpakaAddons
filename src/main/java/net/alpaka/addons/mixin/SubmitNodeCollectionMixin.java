package net.alpaka.addons.mixin;

import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.alpaka.addons.features.viewmodel.HandItemStarlightFeature;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {

    /*
     * Sees every item Submit the moment it is handed to a render phase. In 26.3 submitItem splits an
     * item into a Submit of its translucent quads for the translucent phase and one of its solid
     * quads for the solid phase (plus a third for the outline phase), so all call sites are covered. The object passes
     * through unchanged; the hand item features only note its identity while the hand renderer is
     * submitting.
     */
    @ModifyArg(
            method = "submitItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/FeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V")
    )
    private SubmitNode alpaka$rememberTranslucentHandItem(SubmitNode submit) {
        HandItemLightingFeature.onItemSubmitted(submit);
        HandItemStarlightFeature.onItemSubmitted(submit);
        ItemMotionBlurFeature.onItemSubmitted(submit);
        return submit;
    }

    @ModifyArg(
            method = "submitItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V")
    )
    private SubmitNode alpaka$rememberSolidHandItem(SubmitNode submit) {
        HandItemLightingFeature.onItemSubmitted(submit);
        HandItemStarlightFeature.onItemSubmitted(submit);
        ItemMotionBlurFeature.onItemSubmitted(submit);
        return submit;
    }
}
