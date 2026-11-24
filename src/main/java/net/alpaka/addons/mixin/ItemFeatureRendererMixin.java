package net.alpaka.addons.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.alpaka.addons.features.viewmodel.HandItemStarlightFeature;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> {

    @Shadow @Final private QuadInstance quadInstance;

    @Inject(method = "prepareSubmit", at = @At("HEAD"))
    private void alpaka$beginItem(ItemFeatureRenderer.Submit submit, boolean outline, CallbackInfo ci) {
        HandItemLightingFeature.beginDrawing(submit);
    }

    // Plain quads go through getVertexBuilder, glinted ones through getFoilBuffer, which wraps
    // the same base type together with the glint layer. Both get the swapped type. The outline
    // pass is left alone: it draws the glow color, not the item texture.
    @ModifyArg(
            method = "prepareMainSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
    )
    private RenderType alpaka$pickPlainBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(renderType);
    }

    @ModifyArg(
            method = "prepareFoilSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getFoilBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            index = 0
    )
    private RenderType alpaka$pickFoilBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(renderType);
    }

    // With "Replace Enchant Glint" the starlight stands in for vanilla's glint on the hand item.
    @Inject(method = "prepareFoilSubmit", at = @At("HEAD"), cancellable = true)
    private void alpaka$starlightReplacesGlint(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        if (HandItemStarlightFeature.hidesGlint(submit)) ci.cancel();
    }

    // The second pass over the submits draws the glint layers; the hand item's starlight goes in
    // with them, over every item submit of the hand, glinted or not. See HandItemStarlightFeature.
    @Inject(method = "prepareSubmit", at = @At("TAIL"))
    private void alpaka$starlight(ItemFeatureRenderer.Submit submit, boolean foilPass, CallbackInfo ci) {
        boolean glinted = submit.foilType() != ItemStackRenderState.FoilType.NONE;
        if (!foilPass || !HandItemStarlightFeature.drawsOver(submit, glinted)) return;
        VertexConsumer buffer = HandItemStarlightFeature.starlightBuffer(
                this.getVertexBuilder(HandItemStarlightFeature.renderType()));
        for (BakedQuad quad : submit.quads()) {
            buffer.putBakedQuad(submit.pose(), quad, this.quadInstance);
        }
    }
}
