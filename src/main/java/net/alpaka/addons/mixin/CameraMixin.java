package net.alpaka.addons.mixin;

import net.alpaka.addons.features.perspective.SmoothPerspectiveFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the smooth perspective transition; see {@link SmoothPerspectiveFeature}.
 *
 * Right after vanilla has aligned the camera with the player, before it works out the FOV, the view
 * matrix and the cull frustum, so all three follow the smoothed pose. At the end of update, as this
 * used to run, the frustum had already been built from vanilla's pose: chunks and entities popped in
 * and out during the glide.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void move(float forwards, float up, float left);
    @Shadow private float getMaxZoom(float distance) { return distance; }
    @Shadow public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);
    @Shadow private Entity entity;
    @Shadow private Vec3 position;
    @Shadow private boolean detached;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER)
    )
    private void alpaka$smoothPerspective(DeltaTracker deltaTracker, CallbackInfo ci) {
        Entity camEntity = this.entity;
        // Riding: vanilla places the camera off the vehicle, which this does not model.
        if (camEntity == null || camEntity.isPassenger()) {
            SmoothPerspectiveFeature.reset();
            return;
        }

        CameraType type = Minecraft.getInstance().options.getCameraType();
        float partialTick = getCameraEntityPartialTicks(deltaTracker);

        // The eye, built the way vanilla builds it before moving a detached camera back. Read from
        // the entity rather than position(), which other mods wrap.
        Vec3 eye = new Vec3(
                Mth.lerp(partialTick, camEntity.xo, camEntity.getX()),
                Mth.lerp(partialTick, camEntity.yo, camEntity.getY()) + Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight),
                Mth.lerp(partialTick, camEntity.zo, camEntity.getZ()));
        float liveYaw = camEntity.getViewYRot(partialTick);
        float livePitch = camEntity.getViewXRot(partialTick);

        // What vanilla shows now, as distance and offsets from the live view. Its distance already
        // stops short of walls, so the glide never ends further out than vanilla would.
        float targetDistance = type.isFirstPerson() ? 0.0f : (float) this.position.distanceTo(eye);
        float targetYaw = type.isMirrored() ? 180.0f : 0.0f;
        float targetPitch = type.isMirrored() ? -2.0f * livePitch : 0.0f;

        SmoothPerspectiveFeature.Pose pose = SmoothPerspectiveFeature.update(type, targetDistance, targetYaw, targetPitch);
        if (pose == null) return;

        setPosition(eye);
        setRotation(liveYaw + pose.yawOffset(), livePitch + pose.pitchOffset());
        // Vanilla's own sweep, so the camera stops at a wall instead of passing into it.
        if (pose.distance() > 0.0f) move(-getMaxZoom(pose.distance()), 0.0f, 0.0f);
        this.detached = SmoothPerspectiveFeature.isDetachedTransition();
    }
}
