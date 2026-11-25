package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.viewmodel.EmptyHandViewmodel;
import net.alpaka.addons.features.viewmodel.EquipAnimation;
import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.alpaka.addons.features.viewmodel.HandItemStarlightFeature;
import net.alpaka.addons.features.viewmodel.InspectAnimation;
import net.alpaka.addons.features.viewmodel.ItemInspectFeature;
import net.alpaka.addons.features.viewmodel.SwingTrailFeature;
import net.alpaka.addons.utils.AlpakaPerf;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
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
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
    }

    /** No sway: the camera-lag rotations are left out. A condition rather than a redirect, so a viewmodel mod's hook on the same call still runs. */
    @WrapWithCondition(
            method = "submitHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V")
    )
    private boolean alpaka$keepSway(PoseStack poseStack, org.joml.Quaternionfc quaternion) {
        return !(AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemSwayDisabled);
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

            // Mirrored by the physical arm, like the rotation below, so a left-handed player's
            // items move outward too. This one translate covers the bare arm as well: both places
            // that draw it run inside this method after this point.
            matrices.translate(alpaka$sideOf(player, hand) == HumanoidArm.RIGHT ? xOffset : -xOffset, yOffset, zOffset);
        }
    }

    /**
     * Inspect Animation for the empty hand: turns the bare arm about the hand, before vanilla places
     * it. A held item is turned in {@link #alpaka$inspectItem} instead.
     */
    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void alpaka$inspect(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand,
            float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (hand != InteractionHand.MAIN_HAND || player != Minecraft.getInstance().player) return;
        InspectAnimation.Pose pose = ItemInspectFeature.currentPose();
        InspectAnimation.Kind kind = ItemInspectFeature.currentKind();
        if (pose == null || kind != InspectAnimation.Kind.HAND) return;
        float side = alpaka$sideOf(player, hand) == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float pivotX = side * (0.56f + kind.pivotX);
        float pivotY = -0.52f + kind.pivotY;
        float pivotZ = -0.72f + kind.pivotZ;
        matrices.translate(side * pose.x() + pivotX, pose.y() + pivotY, pose.z() + pivotZ);
        matrices.mulPose(Axis.YP.rotationDegrees(side * pose.yaw()));
        matrices.mulPose(Axis.XP.rotationDegrees(pose.pitch()));
        matrices.mulPose(Axis.ZP.rotationDegrees(side * pose.roll()));
        // Mirroring the arm flips the axis's x and the turn's direction.
        matrices.mulPose(new org.joml.Quaternionf().rotationAxis(
                side * pose.spin() * Mth.DEG_TO_RAD, side * kind.axisX, kind.axisY, kind.axisZ));
        matrices.translate(-pivotX, -pivotY, -pivotZ);
    }

    /** Which physical arm holds this hand's item, as vanilla works it out. */
    @org.spongepowered.asm.mixin.Unique
    private static HumanoidArm alpaka$sideOf(AbstractClientPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
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
            
            boolean isLeftHand = alpaka$sideOf(player, hand) == HumanoidArm.LEFT;
            matrices.mulPose(Axis.YP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationY : AlpakaConfig.instance.itemRotationY));
            matrices.mulPose(Axis.ZP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationZ : AlpakaConfig.instance.itemRotationZ));
            
            matrices.scale(scale, scale, scale);
        }
    }

    /**
     * Inspect Animation for a held item: turns it about its own middle as the inspect playing now
     * says. Hooked just before the item is drawn, after the viewmodel's scale and rotation (above),
     * so the middle and the item's length are found where the item really is: they are taken from
     * the item's own first-person transform (see InspectAnimation.Kind) through the pose as it
     * stands, and the turn is then put in front of everything, in the camera's frame. Found before
     * the viewmodel changes instead, a smaller or turned item spun about a point off its middle.
     */
    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
    )
    private void alpaka$inspectItem(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand,
            float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (hand != InteractionHand.MAIN_HAND || player != Minecraft.getInstance().player) return;
        InspectAnimation.Pose pose = ItemInspectFeature.currentPose();
        InspectAnimation.Kind kind = ItemInspectFeature.currentKind();
        if (pose == null || kind == null || kind == InspectAnimation.Kind.HAND) return;
        float side = alpaka$sideOf(player, hand) == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        PoseStack.Pose last = matrices.last();
        org.joml.Matrix4f matrix = last.pose();
        // The left hand's item transform is the right one's mirrored in x.
        org.joml.Vector3f middle = matrix.transformPosition(
                side * kind.pivotX, kind.pivotY, kind.pivotZ, new org.joml.Vector3f());
        org.joml.Vector3f length = matrix.transformDirection(
                side * kind.axisX, kind.axisY, kind.axisZ, new org.joml.Vector3f()).normalize();
        org.joml.Quaternionf turn = new org.joml.Quaternionf()
                .rotateY(side * pose.yaw() * Mth.DEG_TO_RAD)
                .rotateX(pose.pitch() * Mth.DEG_TO_RAD)
                .rotateZ(side * pose.roll() * Mth.DEG_TO_RAD)
                .mul(new org.joml.Quaternionf().rotationAxis(side * pose.spin() * Mth.DEG_TO_RAD, length));
        org.joml.Matrix4f around = new org.joml.Matrix4f()
                .translation(middle.x + side * pose.x(), middle.y + pose.y(), middle.z + pose.z())
                .rotate(turn)
                .translate(-middle.x, -middle.y, -middle.z);
        matrix.mulLocal(around);
        last.normal().set(new org.joml.Matrix3f().set(turn).mul(last.normal()));
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
            // About the fist, not the model's origin below the screen, so the hand stays in view.
            PoseStack.Pose last = matrices.last();
            EmptyHandViewmodel.apply(last.pose(), last.normal(), arm == HumanoidArm.LEFT ? -1.0f : 1.0f,
                    AlpakaConfig.instance.itemRotationX, AlpakaConfig.instance.itemRotationY,
                    AlpakaConfig.instance.itemRotationZ, AlpakaConfig.instance.itemScale);
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
            // Only the hand that is swinging, as vanilla does; the other gets its 0 unchanged.
            InteractionHand swinging = player.swingingArm != null ? player.swingingArm : InteractionHand.MAIN_HAND;
            if (hand != swinging) return swingProgress;
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
        if ((AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemNoEquipEnabled) || EquipAnimation.instant()) {
            cir.setReturnValue(true);
        }
    }

    /** Tells the equip animation where each hand is in a slot switch, and lets its test run lower the hand. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void alpaka$equipAnimationTick(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        this.mainHandHeight = EquipAnimation.afterTick(InteractionHand.MAIN_HAND,
                this.mainHandItem != player.getMainHandItem(), this.oMainHandHeight, this.mainHandHeight);
        this.offHandHeight = EquipAnimation.afterTick(InteractionHand.OFF_HAND,
                this.offHandItem != player.getOffhandItem(), this.oOffHandHeight, this.offHandHeight);
    }

    /**
     * Equip animation speed: scales how far each hand's height may move in one tick, for the main
     * hand (ordinal 2) and the off hand (ordinal 3). Ordinals 0 and 1 are the busy-hands branch,
     * which is left alone.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 2))
    private float alpaka$equipSpeedMain(float value, float min, float max, Operation<Float> original) {
        LocalPlayer player = Minecraft.getInstance().player;
        float k = player == null ? 1.0f : EquipAnimation.stepScale(InteractionHand.MAIN_HAND, this.mainHandItem != player.getMainHandItem());
        return original.call(value, min * k, max * k);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 3))
    private float alpaka$equipSpeedOff(float value, float min, float max, Operation<Float> original) {
        LocalPlayer player = Minecraft.getInstance().player;
        float k = player == null ? 1.0f : EquipAnimation.stepScale(InteractionHand.OFF_HAND, this.offHandItem != player.getOffhandItem());
        return original.call(value, min * k, max * k);
    }

    /** Draws a hand in a slot switch along the chosen equip animation's path instead of vanilla's straight drop. */
    @Inject(method = "applyItemArmTransform", at = @At("HEAD"), cancellable = true)
    private void alpaka$equipAnimation(PoseStack poseStack, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        if (EquipAnimation.apply(poseStack, arm, equipProgress)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onUpdateHeldItems(CallbackInfo ci) {
        if ((AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemNoEquipEnabled) || EquipAnimation.instant()) {
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
     * Gives the swing trail the pose the arm is placed in, before anything of the arm or the item
     * is added to it: the view with its bobbing and the sway of the hand.
     */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void alpaka$noteArmPose(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand,
            float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (player == Minecraft.getInstance().player) {
            SwingTrailFeature.noteArmPose(matrices.last().pose());
        }
    }

    /**
     * Tells the swing trail which hand is about to be drawn and how far into its swing it is.
     * Hooked at the renderItem call rather than at HEAD so the swing progress seen here is the one the
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
            SwingTrailFeature.noteArmItem(hand, swingProgress);
        }
    }

    /**
     * Times the first-person hands, with everything the viewmodel features add to them. Only a
     * measurement, so the game starts without it if it cannot apply.
     */
    @WrapMethod(method = "submitHandsWithItems", require = 0)
    private void alpaka$timeHands(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
                                  int packedLight, Operation<Void> original) {
        long t = AlpakaPerf.begin(AlpakaPerf.Section.HAND_ITEMS);
        try {
            original.call(frameInterp, poseStack, collector, player, packedLight);
        } finally {
            AlpakaPerf.end(AlpakaPerf.Section.HAND_ITEMS, t);
        }
    }

    // Everything the item state submits between these two points is one first-person hand item,
    // which is how the hand item lighting, starlight and swing trail features tell those submits
    // apart from every other item in the world (this method also serves third-person held items, filtered out
    // by context).
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void alpaka$forgetLastFrameHandItems(CallbackInfo ci) {
        // 26.2 builds the item groups per render phase, so there is no single "all items drawn"
        // point to clean up at; the start of the next hand render is the frame boundary instead.
        HandItemLightingFeature.endFrame();
        HandItemStarlightFeature.endFrame();
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT))
    private void alpaka$beforeHandItemSubmit(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                             PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HandItemLightingFeature.beginHandItem(entity, context);
        HandItemStarlightFeature.beginHandItem(entity, context);
        SwingTrailFeature.beginHandItem(entity, context);
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT, shift = At.Shift.AFTER))
    private void alpaka$afterHandItemSubmit(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                            PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HandItemStarlightFeature.endHandItem();
        SwingTrailFeature.endHandItem(collector);
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
