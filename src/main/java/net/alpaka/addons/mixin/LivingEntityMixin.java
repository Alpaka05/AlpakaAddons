package net.alpaka.addons.mixin;

import net.alpaka.addons.features.mobdeath.HideMobDeathsFeature;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The puff of smoke a mob leaves when its body is removed, sent as an entity event; see {@link HideMobDeathsFeature}. */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideDeathSmoke(CallbackInfo ci) {
        if (HideMobDeathsFeature.shouldHideSmoke((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
