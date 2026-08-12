package net.alpaka.addons.mixin;

import net.alpaka.addons.features.blaze.CleanBlazeFeature;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlazeModel.class)
public class BlazeModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("HEAD"))
    private void stopBlazeRodSpin(LivingEntityRenderState state, CallbackInfo ci) {
        if (CleanBlazeFeature.shouldStopBlazeRodSpin()) {
            state.ageInTicks = 0.0f;
        }
    }
}
