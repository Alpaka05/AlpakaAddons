package net.alpaka.addons.mixin;

import net.alpaka.addons.features.enchantments.ExperimentSolver;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Hands every slot the server sets in a menu to the Experiment Solver, which only reads them. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuSlotSetMixin {
    @Inject(method = "setItem", at = @At("RETURN"))
    private void alpaka$slotSet(int slot, int stateId, ItemStack stack, CallbackInfo ci) {
        ExperimentSolver.onSlotSet((AbstractContainerMenu) (Object) this, slot, stack);
    }

    @Inject(method = "initializeContents", at = @At("RETURN"))
    private void alpaka$contentsSet(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        for (int i = 0; i < items.size(); i++) ExperimentSolver.onSlotSet(menu, i, items.get(i));
    }
}
