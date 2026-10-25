package net.alpaka.addons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The slot under the mouse in a menu, for the Item Value key: which item it is asked about. And
 * where the menu sits, so the Chest Value panel goes beside it rather than over its slots.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot alpaka$hoveredSlot();

    @Accessor("leftPos")
    int alpaka$leftPos();

    @Accessor("topPos")
    int alpaka$topPos();

    @Accessor("imageWidth")
    int alpaka$imageWidth();

    @Accessor("imageHeight")
    int alpaka$imageHeight();
}
