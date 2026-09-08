package net.alpaka.addons.mixin;

import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {

    @Inject(method = "prepareSubmit", at = @At("HEAD"))
    private void alpaka$beginItem(ItemFeatureRenderer.Submit submit, boolean outline, CallbackInfo ci) {
        HandItemLightingFeature.beginDrawing(submit);
    }

    // Plain quads go through getVertexBuilder, glinted ones through getFoilBuffer, which wraps
    // the same base type together with the glint layer. Both get the swapped type. The outline
    // pass is left alone: it draws the glow colour, not the item texture.
    @ModifyArg(
            method = "prepareMainSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
    )
    private RenderType alpaka$unlitPlainBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(renderType);
    }

    @ModifyArg(
            method = "prepareFoilSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getFoilBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            index = 0
    )
    private RenderType alpaka$unlitFoilBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(renderType);
    }
}
