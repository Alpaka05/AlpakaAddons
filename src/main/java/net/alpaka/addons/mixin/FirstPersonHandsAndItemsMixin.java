package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.viewmodel.EquipAnimation;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The held items of the first-person hands and how high each hand is, which 26.3 keeps apart from
 * the renderer ({@link FirstPersonHandsAndItemsRendererMixin}): Disable Re-equip Animation and the
 * equip animation's slot switch.
 */
@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonHandsAndItemsMixin {
    @Unique
    private static final String ALPAKA$INSTANT_REPLACE =
            "Lnet/minecraft/client/player/FirstPersonHandsAndItems;shouldInstantlyReplaceVisibleItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/LocalPlayer;)Z";

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

    /** Notes the hotbar slot before vanilla compares the items, so a slot switch can be told from an item updated in place. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void alpaka$equipAnimationBeforeTick(LocalPlayer player, CallbackInfo ci) {
        EquipAnimation.beforeTick(player.getInventory().getSelectedSlot());
    }

    /** Swaps the main hand item at once (no re-equip), unless the equip animation plays a slot switch on it. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = ALPAKA$INSTANT_REPLACE, ordinal = 0))
    private boolean alpaka$instantSwapMain(FirstPersonHandsAndItems self, ItemStack from, ItemStack to, LocalPlayer player,
                                           Operation<Boolean> original) {
        return alpaka$skipReequip(InteractionHand.MAIN_HAND) || original.call(self, from, to, player);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = ALPAKA$INSTANT_REPLACE, ordinal = 1))
    private boolean alpaka$instantSwapOff(FirstPersonHandsAndItems self, ItemStack from, ItemStack to, LocalPlayer player,
                                          Operation<Boolean> original) {
        return alpaka$skipReequip(InteractionHand.OFF_HAND) || original.call(self, from, to, player);
    }

    /**
     * Disable Re-equip Animation, or the equip animation at 0% intensity. With both options on, a
     * slot switch still plays the equip animation; the option only skips the rest.
     */
    @Unique
    private static boolean alpaka$skipReequip(InteractionHand hand) {
        if (EquipAnimation.instant()) return true;
        AlpakaConfig c = AlpakaConfig.instance;
        return c.itemSizeFeatureEnabled && c.itemNoEquipEnabled && !EquipAnimation.ownsHand(hand);
    }

    /** Tells the equip animation where each hand is in a slot switch, and lets its test run lower the hand. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void alpaka$equipAnimationTick(LocalPlayer player, CallbackInfo ci) {
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
    private float alpaka$equipSpeedMain(float value, float min, float max, Operation<Float> original,
                                        @Local(argsOnly = true) LocalPlayer player) {
        float k = EquipAnimation.stepScale(InteractionHand.MAIN_HAND, this.mainHandItem != player.getMainHandItem());
        return original.call(value, min * k, max * k);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 3))
    private float alpaka$equipSpeedOff(float value, float min, float max, Operation<Float> original,
                                       @Local(argsOnly = true) LocalPlayer player) {
        float k = EquipAnimation.stepScale(InteractionHand.OFF_HAND, this.offHandItem != player.getOffhandItem());
        return original.call(value, min * k, max * k);
    }

    /** Keeps the hands up (no dip after an attack either), except a main hand the equip animation is playing on. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onUpdateHeldItems(LocalPlayer player, CallbackInfo ci) {
        if (alpaka$skipReequip(InteractionHand.MAIN_HAND)) {
            this.mainHandHeight = 1.0f;
            this.oMainHandHeight = 1.0f;
        }
        if (alpaka$skipReequip(InteractionHand.OFF_HAND)) {
            this.offHandHeight = 1.0f;
            this.oOffHandHeight = 1.0f;
        }
    }
}
