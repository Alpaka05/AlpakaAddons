package net.alpaka.addons.mixin;

import net.alpaka.addons.features.garden.VisitorBazaarFeature;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenVisitorMixin {

    /** Visitor Bazaar Shortcut: a refused Accept Offer click opens the bazaar instead. */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void alpaka$visitorBazaar(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (VisitorBazaarFeature.onSlotClicked(slot)) {
            ci.cancel();
        }
    }
}
