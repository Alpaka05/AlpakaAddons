package net.alpaka.addons.mixin;

import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
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
        ItemMotionBlurFeature.beginDrawing(submit);
    }

    // Plain quads go through getVertexBuilder, glinted ones through getFoilBuffer, which wraps
    // the same base type together with the glint layer. Both get the swapped type. The outline
    // pass is left alone: it draws the glow colour, not the item texture. The motion blur picks
    // first: a ghost gets its own translucent type, which the lighting swap then leaves alone; a
    // real item passes through to the lighting swap unchanged.
    @ModifyArg(
            method = "prepareMainSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
    )
    private RenderType alpaka$pickPlainBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(ItemMotionBlurFeature.pickRenderType(renderType));
    }

    @ModifyArg(
            method = "prepareFoilSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getFoilBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            index = 0
    )
    private RenderType alpaka$pickFoilBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(ItemMotionBlurFeature.pickRenderType(renderType));
    }

    // The quad colour is set once per quad from the tint layers; a motion blur ghost gets its
    // fade folded into the alpha here, which its translucent render type then blends with.
    @ModifyArg(
            method = "prepareMainSubmit",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V")
    )
    private int alpaka$ghostQuadColor(int color) {
        return ItemMotionBlurFeature.applyGhostAlpha(color);
    }
}
