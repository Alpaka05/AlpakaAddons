package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.alpaka.addons.features.enchantments.AnvilCombineHelper;
import net.alpaka.addons.features.enchantments.ExperimentSolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slot highlights and remembered items for the Anvil Combine Helper and the Experiment Solver.
 * The highlight is drawn before the slot's item, so the item stays on top of it.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotHighlightMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void alpaka$slotBackground(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AnvilCombineHelper.extractSlotBackground(screen, graphics, slot);
        if (screen instanceof ContainerScreen chest) ExperimentSolver.extractSlotBackground(chest, graphics, slot);
    }

    @ModifyExpressionValue(method = "extractSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack alpaka$rememberedItem(ItemStack actual, GuiGraphicsExtractor graphics, Slot slot) {
        if (!((Object) this instanceof ContainerScreen chest)) return actual;
        return ExperimentSolver.displayed(chest, slot, actual);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void alpaka$slotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (slot != null) ExperimentSolver.onSlotClicked(((AbstractContainerScreen<?>) (Object) this).getMenu(), slotId);
    }
}
