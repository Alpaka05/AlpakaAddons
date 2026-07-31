package net.alpaka.addons.mixin;

import net.alpaka.addons.features.zoom.ZoomFeature;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void modifyFov(boolean firstPerson, float partialTick, CallbackInfoReturnable<Float> info) {
        if (ZoomFeature.isZooming()) {
            info.setReturnValue((float) (info.getReturnValue() / ZoomFeature.getZoomFactor()));
        } else {
            ZoomFeature.resetZoom();
        }
    }
}
