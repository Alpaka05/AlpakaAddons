package net.alpaka.addons.mixin;

import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The sign editor's lines and text field, for the market input bar, which draws them its own way
 * while vanilla keeps editing them and sends them when the editor closes.
 */
@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
    @Accessor("messages")
    String[] alpaka$messages();

    @Accessor("signField")
    TextFieldHelper alpaka$signField();

    /** Sets the line being edited, the first, as typing would. */
    @Invoker("setMessage")
    void alpaka$setMessage(String message);
}
