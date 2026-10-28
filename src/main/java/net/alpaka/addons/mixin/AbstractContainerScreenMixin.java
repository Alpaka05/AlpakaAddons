package net.alpaka.addons.mixin;

import net.alpaka.addons.features.garden.VisitorBazaarFeature;
import net.alpaka.addons.features.itemvalue.ItemValuePopup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;

    /**
     * The item the Item Value popup is open on keeps its tooltip to itself: the popup opens beside the
     * mouse, which is still on the item, and the tooltip would cover it.
     */
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void alpaka$noTooltipUnderItemValue(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (hoveredSlot != null && ItemValuePopup.hidesTooltipOf((Screen) (Object) this, hoveredSlot.getItem())) ci.cancel();
    }

    /** Visitor Bazaar Shortcut: a refused Accept Offer click opens the bazaar instead. */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void alpaka$visitorBazaar(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (VisitorBazaarFeature.onSlotClicked(slot)) {
            ci.cancel();
        }
    }
}
