package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.zoom.ZoomFeature;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** How the mouse turns the camera while zoomed: slower with the zoom, and cinematic if chosen. */
@Mixin(MouseHandler.class)
public class MouseHandlerZoomMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    /** The movement since the last turn, scaled down by the zoom so aiming feels the same. */
    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void alpaka$zoomSensitivity(double movementTime, CallbackInfo ci) {
        if (!AlpakaConfig.instance.zoomReduceSensitivity || !ZoomFeature.isZoomedIn()) return;
        double factor = ZoomFeature.shownFactor();
        if (factor <= 1.0) return;
        this.accumulatedDX /= factor;
        this.accumulatedDY /= factor;
    }

    /** Vanilla's cinematic camera while zoomed, without touching the player's own F8 switch. */
    @ModifyExpressionValue(method = "turnPlayer",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean alpaka$zoomCinematic(boolean smoothCamera) {
        return smoothCamera || (AlpakaConfig.instance.zoomCinematicCamera && ZoomFeature.isZooming());
    }
}
