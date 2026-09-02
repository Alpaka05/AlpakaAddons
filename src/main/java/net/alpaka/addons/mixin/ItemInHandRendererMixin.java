package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Unique
    private static long swingStartTime = 0;
    @Unique
    private static boolean wasSwinging = false;
    @Unique
    private static int lastSwingTime = 0;
    @Unique
    private static boolean lastSwinging = false;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
    }

    @Redirect(
            method = "renderHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V")
    )
    private void redirectMulPose(PoseStack poseStack, org.joml.Quaternionfc quaternion) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemSwayDisabled) {
            return;
        }
        poseStack.mulPose(quaternion);
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void overrideSwingDrift(float attackProgress, PoseStack poseStack, int handSide, HumanoidArm arm, CallbackInfo ci) {
        if (!AlpakaConfig.instance.itemSizeFeatureEnabled) return;
        
        boolean hasCustomDrift = (AlpakaConfig.instance.swingDriftX != 0 || AlpakaConfig.instance.swingDriftY != 0 || AlpakaConfig.instance.swingDriftZ != 0);
        boolean disableSwing = AlpakaConfig.instance.itemSwingTranslationDisabled;
        
        if (hasCustomDrift || disableSwing) {
            ci.cancel();
            
            float driftX = 0.0f;
            float driftY = 0.0f;
            float driftZ = 0.0f;
            
            if (hasCustomDrift && !disableSwing) {
                float sqrtAttack = Mth.sqrt(attackProgress);
                driftX = (AlpakaConfig.instance.swingDriftX / 100.0f) * Mth.sin(sqrtAttack * (float)Math.PI);
                driftY = (AlpakaConfig.instance.swingDriftY / 100.0f) * Mth.sin(sqrtAttack * ((float)Math.PI * 2));
                driftZ = (AlpakaConfig.instance.swingDriftZ / 100.0f) * Mth.sin(attackProgress * (float)Math.PI);
            }
            
            poseStack.translate(handSide * driftX, driftY, driftZ);
            this.applyItemArmAttackTransform(poseStack, arm, attackProgress);
        }
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void overrideSwingArc(PoseStack poseStack, HumanoidArm arm, float attackProgress, CallbackInfo ci) {
        if (!AlpakaConfig.instance.itemSizeFeatureEnabled) return;
        if (AlpakaConfig.instance.swingArcX == 0 && AlpakaConfig.instance.swingArcY == 0 && AlpakaConfig.instance.swingArcZ == 0) return;
        
        ci.cancel();
        int armSideSign = arm == HumanoidArm.RIGHT ? 1 : -1;
        float lateSwingCurve = Mth.sin(attackProgress * attackProgress * (float)Math.PI);
        float midSwingCurve = Mth.sin(Mth.sqrt(attackProgress) * (float)Math.PI);
        
        float preY = 45.0f; // Standard Minecraft pre-Y rotation
        poseStack.mulPose(Axis.YP.rotationDegrees((float)armSideSign * (preY + lateSwingCurve * AlpakaConfig.instance.swingArcY)));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float)armSideSign * midSwingCurve * AlpakaConfig.instance.swingArcZ));
        poseStack.mulPose(Axis.XP.rotationDegrees(midSwingCurve * AlpakaConfig.instance.swingArcX));
        poseStack.mulPose(Axis.YP.rotationDegrees((float)armSideSign * -preY));
    }

    @Inject(
            method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void onBeforeRenderItem(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand,
            float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && player == Minecraft.getInstance().player) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled && item.isEmpty()) {
                return;
            }
            float xOffset = AlpakaConfig.instance.itemXOffset;
            float yOffset = AlpakaConfig.instance.itemYOffset;
            float zOffset = AlpakaConfig.instance.itemZOffset;

            if (hand == InteractionHand.MAIN_HAND) {
                matrices.translate(xOffset, yOffset, zOffset);
            } else {
                matrices.translate(-xOffset, yOffset, zOffset);
            }
        }
    }

    @Inject(
            method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
    )
    private void onRenderItem(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand,
            float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && player == Minecraft.getInstance().player) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled && item.isEmpty()) {
                return;
            }
            float scale = AlpakaConfig.instance.itemScale;
            matrices.mulPose(Axis.XP.rotationDegrees(AlpakaConfig.instance.itemRotationX));
            
            boolean isLeftHand = (hand == InteractionHand.OFF_HAND && player.getMainArm() == HumanoidArm.RIGHT)
                    || (hand == InteractionHand.MAIN_HAND && player.getMainArm() == HumanoidArm.LEFT);
            matrices.mulPose(Axis.YP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationY : AlpakaConfig.instance.itemRotationY));
            matrices.mulPose(Axis.ZP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationZ : AlpakaConfig.instance.itemRotationZ));
            
            matrices.scale(scale, scale, scale);
        }
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void onBeforeRenderHand(
            PoseStack matrices, SubmitNodeCollector queue, int light, float equipProgress,
            float swingProgress, HumanoidArm arm, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled) {
                return;
            }
            float xOffset = AlpakaConfig.instance.itemXOffset;
            float yOffset = AlpakaConfig.instance.itemYOffset;
            float zOffset = AlpakaConfig.instance.itemZOffset;

            if (arm == HumanoidArm.RIGHT) {
                matrices.translate(xOffset, yOffset, zOffset);
            } else {
                matrices.translate(-xOffset, yOffset, zOffset);
            }
        }
    }

    @Inject(
            method = "renderPlayerArm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;")
    )
    private void onRenderHand(
            PoseStack matrices, SubmitNodeCollector queue, int light, float equipProgress,
            float swingProgress, HumanoidArm arm, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled) {
                return;
            }
            float scale = AlpakaConfig.instance.itemScale;
            matrices.mulPose(Axis.XP.rotationDegrees(AlpakaConfig.instance.itemRotationX));
            boolean isLeftHand = (arm == HumanoidArm.LEFT);
            matrices.mulPose(Axis.YP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationY : AlpakaConfig.instance.itemRotationY));
            matrices.mulPose(Axis.ZP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationZ : AlpakaConfig.instance.itemRotationZ));
            matrices.scale(scale, scale, scale);
        }
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float modifySwingProgress(
            float swingProgress, AbstractClientPlayer player, float tickProgress, float pitch,
            InteractionHand hand, float swingProgressArg, ItemStack item) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && player == Minecraft.getInstance().player) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled && item.isEmpty()) {
                return swingProgress;
            }
            return getCustomSwingProgress(player, swingProgress);
        }
        return swingProgress;
    }

    @ModifyVariable(method = "renderPlayerArm", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float modifySwingProgressHand(float swingProgress) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled) {
                return swingProgress;
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                return getCustomSwingProgress(player, swingProgress);
            }
        }
        return swingProgress;
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void onShouldSkipAnimation(ItemStack from, ItemStack to, CallbackInfoReturnable<Boolean> cir) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemNoEquipEnabled) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onUpdateHeldItems(CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemNoEquipEnabled) {
            this.mainHandHeight = 1.0f;
            this.offHandHeight = 1.0f;
            this.oMainHandHeight = 1.0f;
            this.oOffHandHeight = 1.0f;
        }
    }

    private float getCustomSwingProgress(AbstractClientPlayer player, float originalProgress) {
        int currentSwingTime = player.swingTime;
        boolean isSwingingNow = player.swinging;

        if (isSwingingNow) {
            boolean freshStart = !lastSwinging;
            boolean newSwingReset = lastSwinging && (currentSwingTime < lastSwingTime);

            if (freshStart || newSwingReset) {
                if (!AlpakaConfig.instance.itemSwingAlwaysFinishEnabled || !wasSwinging) {
                    long elapsed = System.currentTimeMillis() - swingStartTime;
                    if (!wasSwinging || elapsed > 100) {
                        swingStartTime = System.currentTimeMillis();
                        wasSwinging = true;
                    }
                }
            }
        }
        lastSwingTime = currentSwingTime;
        lastSwinging = isSwingingNow;

        if (wasSwinging) {
            long elapsed = System.currentTimeMillis() - swingStartTime;
            float duration = 250.0f; // Base swing duration in ms
            float speedMultiplier = AlpakaConfig.instance.itemSwingSpeed;
            float progress = (elapsed / (duration / speedMultiplier));
            if (progress >= 1.0f) {
                wasSwinging = false;
                return 0.0f;
            }
            return progress;
        }
        return 0.0f;
    }
}
