package net.alpaka.addons.mixin;

import net.alpaka.addons.features.itemsearch.ItemSearchHighlight;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Search's mark on the item it found and the Slot Highlight's marks.
 * The highlight is drawn before the slot's item, so the item stays on top of it.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotHighlightMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void alpaka$slotBackground(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemSearchHighlight.drawSlot(graphics, slot);
    }

    /** The Slot Highlight's marks, also behind the item, so it and the tooltip stay on top. */
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void alpaka$slotHighlight(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        net.alpaka.addons.features.slothighlight.SlotHighlight.drawSlot((AbstractContainerScreen<?>) (Object) this, graphics, slot);
    }
}
