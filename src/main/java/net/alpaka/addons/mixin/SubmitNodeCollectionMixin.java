package net.alpaka.addons.mixin;

import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {

    /*
     * Sees every item Submit the moment it is handed to a render phase. In 26.2 submitItem routes a
     * translucent item to the translucent phase and everything else to the solid phase (plus a
     * second Submit for the outline phase), so both call sites are covered. The object passes
     * through unchanged; the hand item feature only notes its identity while the hand renderer is
     * submitting.
     */
    @ModifyArg(
            method = "submitItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/TranslucentFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V")
    )
    private TranslucentSubmit alpaka$rememberTranslucentHandItem(TranslucentSubmit submit) {
        HandItemLightingFeature.onItemSubmitted(submit);
        return submit;
    }

    @ModifyArg(
            method = "submitItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V")
    )
    private SubmitNode alpaka$rememberSolidHandItem(SubmitNode submit) {
        HandItemLightingFeature.onItemSubmitted(submit);
        return submit;
    }
}
