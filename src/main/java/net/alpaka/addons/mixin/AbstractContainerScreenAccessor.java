package net.alpaka.addons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel is, for the inventory buttons placed around it. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int alpaka$getLeftPos();

    @Accessor("topPos")
    int alpaka$getTopPos();

    @Accessor("imageWidth")
    int alpaka$getImageWidth();

    @Accessor("imageHeight")
    int alpaka$getImageHeight();
}
