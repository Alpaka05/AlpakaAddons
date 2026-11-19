package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.viewmodel.HandItemLightingFeature;
import net.alpaka.addons.features.viewmodel.HandItemStarlightFeature;
import net.alpaka.addons.features.viewmodel.ItemMotionBlurFeature;
import net.alpaka.addons.utils.AlpakaPerf;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
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
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first-person hands: item size, position and rotation, the swing settings and the hooks the
 * hand item features need. 26.3 draws them from a render state of the local player only, so there
 * is no other player to filter out here; the held items' state lives in
 * {@link FirstPersonHandsAndItemsMixin}.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin {
    @Unique
    private static long swingStartTime = 0;
    @Unique
    private static boolean wasSwinging = false;
    /**
     * The player's swing last seen. Vanilla makes a new one for every swing it starts, even one that
     * restarts an identical swing, so a different object is exactly a new swing.
     */
    @Unique
    private static LivingEntity.SwingDescription lastSeenSwing = null;
    /** The swing the custom animation is playing: its hand and kind (whack, stab). */
    @Unique
    private static LivingEntity.SwingDescription activeSwing = null;

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
    }

    /** No sway: the camera-lag rotations are left out. A condition rather than a redirect, so a viewmodel mod's hook on the same call still runs. */
    @WrapWithCondition(
            method = "submitHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V")
    )
    private boolean alpaka$keepSway(PoseStack poseStack, Axis axis, float degrees) {
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
        poseStack.rotate(Axis.YP.rotationDegrees((float)armSideSign * (preY + lateSwingCurve * AlpakaConfig.instance.swingArcY)));
        poseStack.rotate(Axis.ZP.rotationDegrees((float)armSideSign * midSwingCurve * AlpakaConfig.instance.swingArcZ));
        poseStack.rotate(Axis.XP.rotationDegrees(midSwingCurve * AlpakaConfig.instance.swingArcX));
        poseStack.rotate(Axis.YP.rotationDegrees((float)armSideSign * -preY));
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void onBeforeRenderItem(
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float tickProgress, float pitch,
            InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled) {
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

    /** Which physical arm holds this hand's item, as vanilla works it out. */
    @org.spongepowered.asm.mixin.Unique
    private static HumanoidArm alpaka$sideOf(PlayerRenderState player, InteractionHand hand) {
        HumanoidArm main = player.avatarRenderState.mainArm;
        return hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT)
    )
    private void onRenderItem(
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float tickProgress, float pitch,
            InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled && item.isEmpty()) {
                return;
            }
            float scale = AlpakaConfig.instance.itemScale;
            matrices.rotate(Axis.XP.rotationDegrees(AlpakaConfig.instance.itemRotationX));
            
            boolean isLeftHand = alpaka$sideOf(player, hand) == HumanoidArm.LEFT;
            matrices.rotate(Axis.YP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationY : AlpakaConfig.instance.itemRotationY));
            matrices.rotate(Axis.ZP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationZ : AlpakaConfig.instance.itemRotationZ));
            
            matrices.scale(scale, scale, scale);
        }
    }

    @Inject(
            method = "renderPlayerArm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V")
    )
    private void onRenderHand(
            PoseStack matrices, SubmitNodeCollector queue, int light, float equipProgress,
            float swingProgress, HumanoidArm arm, PlayerRenderState player, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled) {
                return;
            }
            float scale = AlpakaConfig.instance.itemScale;
            matrices.rotate(Axis.XP.rotationDegrees(AlpakaConfig.instance.itemRotationX));
            boolean isLeftHand = (arm == HumanoidArm.LEFT);
            matrices.rotate(Axis.YP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationY : AlpakaConfig.instance.itemRotationY));
            matrices.rotate(Axis.ZP.rotationDegrees(isLeftHand ? -AlpakaConfig.instance.itemRotationZ : AlpakaConfig.instance.itemRotationZ));
            matrices.scale(scale, scale, scale);
        }
    }

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float modifySwingProgress(
            float swingProgress, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState hands,
            float tickProgress, float pitch, InteractionHand hand, float swingProgressArg, ItemStack item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && player != null) {
            if (AlpakaConfig.instance.itemIgnoreEmptyHandEnabled && item.isEmpty()) {
                return swingProgress;
            }
            float progress = getCustomSwingProgress(player);
            // Only the hand that is swinging, as vanilla does; the other gets its 0 unchanged. The
            // custom swing can outlast vanilla's, so its hand is the one it remembered, not
            // vanilla's attack hand, which is gone by then.
            InteractionHand swinging = activeSwing != null ? activeSwing.hand()
                    : hands.attackHand != null ? hands.attackHand : InteractionHand.MAIN_HAND;
            if (hand != swinging) return swingProgress;
            return progress;
        }
        return swingProgress;
    }

    /**
     * Since 26.3 vanilla only animates a hand while the player's own swing is running, and stops the
     * moment it ends. A slower swing speed, or "Always Finish Swing", plays on past that point, so
     * without this the item snapped back to rest mid-swing. While the custom swing is playing on
     * this hand, the renderer sees the swing it belongs to. The bare arm needs nothing: vanilla
     * draws its swing from the progress alone. The swing progress here is the one adjusted above,
     * since the argument is read after the ModifyVariable at HEAD has run.
     */
    @ModifyExpressionValue(
            method = "submitArmWithItem",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;currentSwing:Lnet/minecraft/world/entity/LivingEntity$SwingDescription;", opcode = Opcodes.GETFIELD)
    )
    private LivingEntity.SwingDescription alpaka$keepCustomSwing(
            LivingEntity.SwingDescription original,
            @Local(argsOnly = true) InteractionHand hand,
            @Local(argsOnly = true, ordinal = 2) float swingProgress) {
        if (!AlpakaConfig.instance.itemSizeFeatureEnabled) return original;
        LivingEntity.SwingDescription active = activeSwing;
        if (active == null || active.hand() != hand || swingProgress <= 0.0f) return original;
        return active;
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
            at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT)
    )
    private void alpaka$noteSwing(
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float tickProgress, float pitch,
            InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector queue, int light, CallbackInfo ci) {
        ItemMotionBlurFeature.noteArmItem(hand, swingProgress);
    }

    /**
     * Times the first-person hands, with everything the viewmodel features add to them. Only a
     * measurement, so the game starts without it if it cannot apply.
     */
    @WrapMethod(method = "submitHandsWithItems", require = 0)
    private void alpaka$timeHands(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
                                  FirstPersonHandsAndItemsRenderState hands, Operation<Void> original) {
        long t = AlpakaPerf.begin(AlpakaPerf.Section.HAND_ITEMS);
        try {
            original.call(frameInterp, poseStack, collector, player, hands);
        } finally {
            AlpakaPerf.end(AlpakaPerf.Section.HAND_ITEMS, t);
        }
    }

    // Everything the item state submits between these two points is one first-person hand item,
    // which is how the hand item lighting and motion blur features tell those submits apart from
    // every other item in the world.
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void alpaka$forgetLastFrameHandItems(CallbackInfo ci) {
        // 26.2 builds the item groups per render phase, so there is no single "all items drawn"
        // point to clean up at; the start of the next hand render is the frame boundary instead.
        HandItemLightingFeature.endFrame();
        HandItemStarlightFeature.endFrame();
        ItemMotionBlurFeature.endFrame();
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT))
    private void alpaka$beforeHandItemSubmit(
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float tickProgress, float pitch,
            InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector collector, int light, CallbackInfo ci) {
        LivingEntity entity = Minecraft.getInstance().player;
        ItemDisplayContext context = alpaka$sideOf(player, hand) == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        HandItemLightingFeature.beginHandItem(entity, context);
        HandItemStarlightFeature.beginHandItem(entity, context);
        ItemMotionBlurFeature.beginHandItem(entity, context);
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = ALPAKA$ITEM_STATE_SUBMIT, shift = At.Shift.AFTER))
    private void alpaka$afterHandItemSubmit(
            PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float tickProgress, float pitch,
            InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
            SubmitNodeCollector collector, int light, CallbackInfo ci) {
        // The ghosts are submitted while the lighting feature is still capturing, so they lose their
        // shading along with the item they trail; starlight stops first, so they trail plain.
        HandItemStarlightFeature.endHandItem();
        ItemMotionBlurFeature.endHandItem(collector);
        HandItemLightingFeature.endHandItem();
    }

    /** A vanilla swing that began while a custom swing was still playing under "Always Finish Swing". */
    @Unique
    private static LivingEntity.SwingDescription pendingSwing = null;

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
    private float getCustomSwingProgress(LocalPlayer player) {
        // Seen at once, unlike a drop in the swing's progress, which vanilla only shows a tick or
        // two after the restart. Called for both hands each frame; the second call sees no new
        // swing, so it changes nothing.
        LivingEntity.SwingDescription current = player.getCurrentSwing();
        boolean newSwing = current != null && current != lastSeenSwing;
        lastSeenSwing = current;
        long now = System.currentTimeMillis();
        boolean alwaysFinish = AlpakaConfig.instance.itemSwingAlwaysFinishEnabled;

        if (newSwing) {
            if (!wasSwinging) {
                swingStartTime = now;
                wasSwinging = true;
                activeSwing = current;
            } else if (alwaysFinish) {
                pendingSwing = current;
            } else if (now - swingStartTime > 100) {
                swingStartTime = now;
                activeSwing = current;
            }
        }

        if (!wasSwinging) {
            pendingSwing = null;
            activeSwing = null;
            return 0.0f;
        }
        float duration = 250.0f / AlpakaConfig.instance.itemSwingSpeed;
        long elapsed = now - swingStartTime;
        if (elapsed >= duration) {
            if (pendingSwing == null || !alpaka$attackHeld()) {
                wasSwinging = false;
                pendingSwing = null;
                activeSwing = null;
                return 0.0f;
            }
            // Chain the queued swing onto the end of this one, keeping the time already spent past
            // the boundary so the motion does not hitch by a frame. After a stall long enough to
            // miss a whole swing, resync to now instead of racing to catch up.
            activeSwing = pendingSwing;
            pendingSwing = null;
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
