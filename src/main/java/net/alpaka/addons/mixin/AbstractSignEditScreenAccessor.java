package net.alpaka.addons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The sign editor's four lines, for the market input bar: it writes the answer into them, and
 * vanilla sends them when the editor closes, as it does for a sign typed on by hand.
 */
@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
    @Accessor("messages")
    String[] alpaka$messages();
}
