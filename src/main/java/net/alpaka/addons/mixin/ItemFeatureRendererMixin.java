package net.alpaka.addons.mixin;

import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void alpaka$beginItem(MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineSource,
                                  SubmitNodeStorage.ItemSubmit submit, CallbackInfo ci) {
        HandItemLightingFeature.beginDrawing(submit);
    }

    // Plain quads go through BufferSource.getBuffer, glinted ones through getFoilBuffer, which
    // wraps the same base type together with the glint layer. Both get the swapped type.
    @ModifyArg(
            method = "renderItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
    )
    private RenderType alpaka$unlitPlainBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(renderType);
    }

    @ModifyArg(
            method = "renderItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            index = 1
    )
    private RenderType alpaka$unlitFoilBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(renderType);
    }

    @Inject(method = "renderTranslucent", at = @At("TAIL"))
    private void alpaka$endItemPasses(SubmitNodeCollection collection, MultiBufferSource.BufferSource bufferSource,
                                      OutlineBufferSource outlineSource, CallbackInfo ci) {
        // The solid pass runs before the translucent one, so by now every submit of this
        // collection has been drawn; drop whatever is left rather than let it accumulate.
        HandItemLightingFeature.endFrame();
    }
}
