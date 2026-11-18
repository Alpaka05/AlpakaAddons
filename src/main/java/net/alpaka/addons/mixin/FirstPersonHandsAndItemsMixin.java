package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Equip Animation: the held item is swapped at once and the hands never dip. */
@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonHandsAndItemsMixin {
    @Shadow
    private float mainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void onShouldSkipAnimation(ItemStack from, ItemStack to, LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemNoEquipEnabled) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onUpdateHeldItems(LocalPlayer player, CallbackInfo ci) {
        if (AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemNoEquipEnabled) {
            this.mainHandHeight = 1.0f;
            this.offHandHeight = 1.0f;
            this.oMainHandHeight = 1.0f;
            this.oOffHandHeight = 1.0f;
        }
    }
}
