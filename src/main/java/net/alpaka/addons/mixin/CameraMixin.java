package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract Vec3 position();
    @Shadow public abstract float yRot();
    @Shadow public abstract float xRot();
    @Shadow private Entity entity;

    @Unique private CameraType alpaka$lastCameraType = null;
    @Unique private long alpaka$transitionStartTime = 0;
    @Unique private Vec3 alpaka$startOffset = Vec3.ZERO;
    @Unique private float alpaka$startYaw = 0.0f;
    @Unique private float alpaka$startPitch = 0.0f;
    @Unique private Vec3 alpaka$lastOffset = Vec3.ZERO;
    @Unique private float alpaka$lastYaw = 0.0f;
    @Unique private float alpaka$lastPitch = 0.0f;

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!AlpakaConfig.instance.smoothPerspectiveEnabled) {
            alpaka$lastCameraType = null;
            alpaka$transitionStartTime = 0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || this.entity == null) return;

        CameraType currentType = mc.options.getCameraType();
        Vec3 vanillaPos = this.position();
        float vanillaYaw = this.yRot();
        float vanillaPitch = this.xRot();

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 eyePos = this.entity.getEyePosition(tickDelta);
        Vec3 targetOffset = vanillaPos.subtract(eyePos);

        if (alpaka$lastCameraType == null) {
            alpaka$lastCameraType = currentType;
            alpaka$lastOffset = targetOffset;
            alpaka$lastYaw = vanillaYaw;
            alpaka$lastPitch = vanillaPitch;
            return;
        }

        if (currentType != alpaka$lastCameraType) {
            alpaka$startOffset = alpaka$lastOffset;
            alpaka$startYaw = alpaka$lastYaw;
            alpaka$startPitch = alpaka$lastPitch;
            alpaka$transitionStartTime = System.currentTimeMillis();
            alpaka$lastCameraType = currentType;
        }

        if (alpaka$transitionStartTime == 0) {
            alpaka$lastOffset = targetOffset;
            alpaka$lastYaw = vanillaYaw;
            alpaka$lastPitch = vanillaPitch;
            return;
        }

        long duration = Math.max(50, AlpakaConfig.instance.smoothPerspectiveDurationMs);
        long elapsed = System.currentTimeMillis() - alpaka$transitionStartTime;

        if (elapsed >= duration) {
            alpaka$transitionStartTime = 0;
            alpaka$lastOffset = targetOffset;
            alpaka$lastYaw = vanillaYaw;
            alpaka$lastPitch = vanillaPitch;
            return;
        }

        float progress = Math.min(1.0f, (float) elapsed / duration);
        // Smooth step / cubic easing
        float factor = progress * progress * (3.0f - 2.0f * progress);

        Vec3 currentOffset = alpaka$lerpVec3(alpaka$startOffset, targetOffset, factor);
        float currentYaw = alpaka$lerpAngle(alpaka$startYaw, vanillaYaw, factor);
        float currentPitch = alpaka$lerpAngle(alpaka$startPitch, vanillaPitch, factor);

        this.setPosition(eyePos.add(currentOffset));
        this.setRotation(currentYaw, currentPitch);

        alpaka$lastOffset = currentOffset;
        alpaka$lastYaw = currentYaw;
        alpaka$lastPitch = currentPitch;
    }

    @Unique
    private Vec3 alpaka$lerpVec3(Vec3 start, Vec3 end, double t) {
        return new Vec3(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        );
    }

    @Unique
    private float alpaka$lerpAngle(float start, float end, float t) {
        float diff = (end - start) % 360.0f;
        if (diff < -180.0f) {
            diff += 360.0f;
        }
        if (diff > 180.0f) {
            diff -= 360.0f;
        }
        return start + diff * t;
    }
}
