package net.alpaka.addons.mixin;

import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {

    /**
     * Sees every ItemSubmit the moment it is stored. The object is passed through unchanged; the
     * hand item features only note its identity while the hand renderer is submitting.
     */
    @ModifyArg(
            method = "submitItem",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z")
    )
    private Object alpaka$rememberHandItemSubmit(Object itemSubmit) {
        HandItemLightingFeature.onItemSubmitted(itemSubmit);
        ItemMotionBlurFeature.onItemSubmitted(itemSubmit);
        return itemSubmit;
    }
}
