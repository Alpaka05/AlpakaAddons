package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CameraType.class)
public abstract class CameraTypeMixin {

    @Inject(method = "cycle", at = @At("RETURN"), cancellable = true)
    private void onCyclePerspective(CallbackInfoReturnable<CameraType> cir) {
        if (AlpakaConfig.instance.disableFrontPerspective && cir.getReturnValue() == CameraType.THIRD_PERSON_FRONT) {
            cir.setReturnValue(CameraType.FIRST_PERSON);
        }
    }
}
