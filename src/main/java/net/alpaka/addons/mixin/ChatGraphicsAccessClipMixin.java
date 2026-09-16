package net.alpaka.addons.mixin;

import net.alpaka.addons.features.chat.AlpakaChatGraphicsClip;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Gives the chat's drawing accesses a text clip; see {@link AlpakaChatGraphicsClip}. The capturing
 * access used for hover detection has no text parameters and is left alone.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"
})
public class ChatGraphicsAccessClipMixin implements AlpakaChatGraphicsClip {
    @Shadow @Final private GuiGraphicsExtractor graphics;
    @Shadow private ActiveTextCollector.Parameters parameters;

    @Override
    public void alpaka$setTextClip(int x0, int x1, int y0, int y1) {
        this.parameters = this.parameters.withScissor(x0, x1, y0, y1);
    }

    @Override
    public void alpaka$clearTextClip() {
        this.parameters = this.parameters.withScissor((ScreenRectangle) null);
    }

    @Override
    public GuiGraphicsExtractor alpaka$graphics() {
        return this.graphics;
    }
}
