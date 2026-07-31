package net.alpaka.addons.mixin;

import net.alpaka.addons.features.zoom.ZoomFeature;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double xoffset, double yoffset, CallbackInfo info) {
        if (ZoomFeature.isZooming()) {
            if (yoffset != 0) {
                ZoomFeature.onMouseScroll(yoffset);
            }
            info.cancel();
        }
    }
}
