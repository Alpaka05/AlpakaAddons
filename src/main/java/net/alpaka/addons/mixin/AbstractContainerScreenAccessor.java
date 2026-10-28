package net.alpaka.addons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The slot under the mouse in a menu, for the Item Search key: which item to search for. And where
 * the menu sits, so Item Search can outline the slots holding the item it was asked about.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot alpaka$hoveredSlot();

    @Accessor("leftPos")
    int alpaka$leftPos();

    @Accessor("topPos")
    int alpaka$topPos();
}
