package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.perspective.SmoothPerspectiveFeature;
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
        float originalFov = info.getReturnValue();
        if (ZoomFeature.isZooming()) {
            info.setReturnValue((float) (originalFov / ZoomFeature.getZoomFactor()));
        } else if (AlpakaConfig.instance.smoothPerspectiveEnabled) {
            float transitionScale = SmoothPerspectiveFeature.getTransitionScale();
            if (transitionScale < 1.0f) {
                info.setReturnValue(originalFov * transitionScale);
            } else {
                ZoomFeature.resetZoom();
            }
        } else {
            ZoomFeature.resetZoom();
        }
    }
}
