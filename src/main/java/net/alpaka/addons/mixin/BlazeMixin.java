package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class BlazeMixin {
    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true)
    private void hideBlazeFire(CallbackInfoReturnable<Boolean> info) {
        if (AlpakaConfig.instance.cleanBlazeEnabled && ((Object) this instanceof Blaze)) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    private void hideBlazeFireAnimation(CallbackInfoReturnable<Boolean> info) {
        if (AlpakaConfig.instance.cleanBlazeEnabled && ((Object) this instanceof Blaze)) {
            info.setReturnValue(false);
        }
    }
}
