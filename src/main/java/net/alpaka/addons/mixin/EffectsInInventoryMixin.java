package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.hideeffects.HideEffects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide Effects: skips the potion effect list next to container screens, tooltips on it included. */
@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideEffects(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (AlpakaConfig.instance.hideInventoryEffects) {
            ci.cancel();
        }
    }

    // Screen.showsActiveEffects() answers from here; item list mods read it to keep the strip
    // beside the inventory free, which left an empty gap once the list was hidden.
    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void alpaka$reportNoEffects(CallbackInfoReturnable<Boolean> cir) {
        if (HideEffects.hidesInventoryList()) {
            cir.setReturnValue(false);
        }
    }
}
