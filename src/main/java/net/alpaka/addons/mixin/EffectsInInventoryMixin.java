package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Effects: skips the potion effect list next to container screens, tooltips on it included. */
@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideEffects(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (AlpakaConfig.instance.hideInventoryEffects) {
            ci.cancel();
        }
    }
}
