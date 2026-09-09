package net.alpaka.addons.mixin;

import net.alpaka.addons.features.hurtoverlay.HurtOverlayFeature;
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
    }
}
