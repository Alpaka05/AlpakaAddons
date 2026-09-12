package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
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
            method = "submitHandsWithItems",
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
            method = "submitArmWithItem",
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
            method = "submitArmWithItem",
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

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
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

    @Unique
    private static final String ALPAKA$ITEM_STATE_SUBMIT =
            "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    /**
     * Tells the motion blur which hand is about to be drawn and how far into its swing it is. Hooked
     * at the renderItem call rather than at HEAD so the swing progress seen here is the one the
     * ModifyVariable above has already adjusted.
     */
    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
    )
    private void alpaka$noteSwing(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand,
            float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (player == Minecraft.getInstance().player) {
            ItemMotionBlurFeature.noteArmItem(hand, swingProgress);
        }
    }

    // Everything the item state submits between these two points is one first-person hand item,
    // which is how the hand item lighting and motion blur features tell those submits apart from
    // every other item in the world (this method also serves third-person held items, filtered out
    // by context).
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void alpaka$forgetLastFrameHandItems(CallbackInfo ci) {
        // 26.2 builds the item groups per render phase, so there is no single "all items drawn"
        // point to clean up at; the start of the next hand render is the frame boundary instead.
        HandItemLightingFeature.endFrame();
        ItemMotionBlurFeature.endFrame();
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT))
    private void alpaka$beforeHandItemSubmit(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                             PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HandItemLightingFeature.beginHandItem(entity, context);
        ItemMotionBlurFeature.beginHandItem(entity, context);
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT, shift = At.Shift.AFTER))
    private void alpaka$afterHandItemSubmit(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                            PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        // The ghosts are submitted while the lighting feature is still capturing, so they lose their
        // shading along with the item they trail.
        ItemMotionBlurFeature.endHandItem(collector);
        HandItemLightingFeature.endHandItem();
    }

    /** Set when a vanilla swing began while a custom swing was still playing under "Always Finish Swing". */
    @Unique
    private static boolean pendingSwing = false;

    /**
     * The swing progress the hand is drawn at, on the mod's own clock rather than vanilla's tick
     * counter, so the swing speed setting can stretch or shorten it.
     *
     * A vanilla swing that begins while a custom swing is still playing is handled one of two ways.
     * Without "Always Finish Swing" it restarts the animation (after a short debounce), which is the
     * responsive look at the price of a visible jump back to rest. With it, the new swing is queued
     * and starts the instant the current one ends - but only while the attack or use button is
     * still held at that moment. Held attacks - mining, farming - fire a vanilla swing every few
     * ticks, so the queue turns them into one unbroken rhythm; without it the hand finished its
     * swing, then rested until the next vanilla swing happened to begin, up to 150 ms later, and
     * the motion read as swing, pause, swing. Once the button is released the queued swing is
     * dropped, so the hand comes to rest with the current stroke instead of throwing one more.
     */
    private float getCustomSwingProgress(AbstractClientPlayer player, float originalProgress) {
        int currentSwingTime = player.swingTime;
        boolean isSwingingNow = player.swinging;
        long now = System.currentTimeMillis();
        boolean alwaysFinish = AlpakaConfig.instance.itemSwingAlwaysFinishEnabled;

        if (isSwingingNow) {
            boolean freshStart = !lastSwinging;
            boolean newSwingReset = lastSwinging && (currentSwingTime < lastSwingTime);
            if (freshStart || newSwingReset) {
                if (!wasSwinging) {
                    swingStartTime = now;
                    wasSwinging = true;
                } else if (alwaysFinish) {
                    pendingSwing = true;
                } else if (now - swingStartTime > 100) {
                    swingStartTime = now;
                }
            }
        }
        lastSwingTime = currentSwingTime;
        lastSwinging = isSwingingNow;

        if (!wasSwinging) {
            pendingSwing = false;
            return 0.0f;
        }
        float duration = 250.0f / AlpakaConfig.instance.itemSwingSpeed;
        long elapsed = now - swingStartTime;
        if (elapsed >= duration) {
            if (!pendingSwing || !alpaka$attackHeld()) {
                wasSwinging = false;
                pendingSwing = false;
                return 0.0f;
            }
            // Chain the queued swing onto the end of this one, keeping the time already spent past
            // the boundary so the motion does not hitch by a frame. After a stall long enough to
            // miss a whole swing, resync to now instead of racing to catch up.
            pendingSwing = false;
            swingStartTime += (long) duration;
            elapsed -= (long) duration;
            if (elapsed >= duration) {
                swingStartTime = now;
                elapsed = 0L;
            }
        }
        return elapsed / duration;
    }

    /** Whether the player is still holding the button that produces swings. */
    @Unique
    private static boolean alpaka$attackHeld() {
        Options options = Minecraft.getInstance().options;
        return options.keyAttack.isDown() || options.keyUse.isDown();
    }
}
