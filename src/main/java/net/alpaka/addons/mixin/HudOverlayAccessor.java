package net.alpaka.addons.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether the action bar is up, for the inventory HUD to clear it while attached to the hotbar. */
@Mixin(Gui.class)
public interface HudOverlayAccessor {
    /** Ticks left on the action bar message; zero when none is showing. */
    @Accessor("overlayMessageTime")
    int alpaka$getOverlayMessageTime();
}
