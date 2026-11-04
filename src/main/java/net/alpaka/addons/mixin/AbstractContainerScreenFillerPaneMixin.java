package net.alpaka.addons.mixin;

import net.alpaka.addons.features.fillerpanes.FillerPaneClicks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenFillerPaneMixin {

    /** Ignore Filler Panes: a click on an empty SkyBlock menu cell is dropped before vanilla handles it. */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void alpaka$ignoreFillerPane(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (FillerPaneClicks.ignores(slot)) ci.cancel();
    }
}
