package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.alpaka.addons.features.hurtoverlay.HurtOverlayFeature;
import net.alpaka.addons.features.slayer.BossHighlightRenderState;
import net.alpaka.addons.features.slayer.SlayerBossHighlight;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    // The descriptor is spelled out because the class also carries the erased bridge
    // extractRenderState(Entity, EntityRenderState, float); the name alone would be ambiguous.
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void alpaka$hideHurtOverlay(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        // TAIL: vanilla has just derived hasRedOverlay from hurtTime/deathTime, and nothing after
        // this point reads it before the layers are drawn.
        HurtOverlayFeature.apply(state);
        SlayerBossHighlight.applyOverlay(entity, (BossHighlightRenderState) state);
    }

    /**
     * Tints a highlighted slayer boss's model. Taken at the call rather than on getModelTint
     * itself, which some renderers (the wolf's, for the Sven Packmaster) override.
     */
    @ModifyExpressionValue(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I")
    )
    private int alpaka$tintBossHighlight(int tint, LivingEntityRenderState state) {
        int highlight = ((BossHighlightRenderState) state).alpaka$getHighlightTint();
        return highlight == 0 ? tint : ARGB.multiply(tint, highlight);
    }
}
