package net.alpaka.addons.mixin;

import net.alpaka.addons.features.enchantments.AnvilCombineHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Slot backgrounds drawn before the slot's item, so the item stays on top of them. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotHighlightMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void alpaka$slotBackground(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AnvilCombineHelper.extractSlotBackground((AbstractContainerScreen<?>) (Object) this, graphics, slot);
    }
}
