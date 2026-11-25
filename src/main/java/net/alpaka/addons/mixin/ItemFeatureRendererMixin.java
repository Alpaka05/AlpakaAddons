package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
    private void alpaka$beginItem(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        HandItemLightingFeature.beginDrawing(submit);
    }

    // Every quad goes through getVertexBuilder, a glinted one with a render type that draws the
    // glint along with the item. All of them get the swapped type. The outline pass is left alone:
    // it draws the glow color, not the item texture.
    @ModifyArg(
            method = "prepareMainSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
    )
    private RenderType alpaka$pickPlainBuffer(RenderType renderType) {
        return HandItemLightingFeature.pickRenderType(renderType);
    }

    // With "Replace Enchant Glint" the starlight stands in for vanilla's glint on the hand item: the
    // item is drawn as if it had none.
    @ModifyExpressionValue(
            method = "prepareMainSubmit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer$Submit;foilType()Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;")
    )
    private ItemStackRenderState.FoilType alpaka$starlightReplacesGlint(ItemStackRenderState.FoilType foilType,
                                                                       @Local(argsOnly = true) ItemFeatureRenderer.Submit submit) {
        return foilType != ItemStackRenderState.FoilType.NONE && HandItemStarlightFeature.hidesGlint(submit)
                ? ItemStackRenderState.FoilType.NONE : foilType;
    }

    // The hand item's starlight goes in right after the item, over every item submit of the hand,
    // glinted or not. See HandItemStarlightFeature.
    @Inject(method = "prepareMainSubmit", at = @At("TAIL"))
    private void alpaka$starlight(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        boolean glinted = submit.foilType() != ItemStackRenderState.FoilType.NONE;
        if (!HandItemStarlightFeature.drawsOver(submit, glinted)) return;
        VertexConsumer buffer = HandItemStarlightFeature.starlightBuffer(
                this.getVertexBuilder(HandItemStarlightFeature.renderType()));
        for (BakedQuad quad : submit.quads()) {
            buffer.putBakedQuad(submit.pose(), quad, this.quadInstance);
        }
    }
}
