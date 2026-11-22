package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.alpaka.addons.features.zoom.ZoomFeature;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {

    /** The zoom, on the finished FOV; see {@link ZoomFeature} for why not the player's FOV modifier. */
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float alpaka$zoom(float fov) {
        double divisor = ZoomFeature.fovDivisor();
        return divisor == 1.0 ? fov : (float) (fov / divisor);
    }
}
