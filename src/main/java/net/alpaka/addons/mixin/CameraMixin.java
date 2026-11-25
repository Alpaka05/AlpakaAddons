package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.teleportwarp.SmoothTeleport;
import net.alpaka.addons.features.zoom.ZoomFeature;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
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

    /**
     * Smooth Teleport: the camera's eye position, drawn behind the player while it glides to where
     * a teleport put them. Before the third-person pull-back, which then starts from there.
     */
    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void alpaka$smoothTeleport(Camera camera, double x, double y, double z, Operation<Void> original) {
        if (SmoothTeleport.applies(camera.entity())) {
            Vec3 offset = SmoothTeleport.offset();
            original.call(camera, x + offset.x, y + offset.y, z + offset.z);
            return;
        }
        original.call(camera, x, y, z);
    }
}
