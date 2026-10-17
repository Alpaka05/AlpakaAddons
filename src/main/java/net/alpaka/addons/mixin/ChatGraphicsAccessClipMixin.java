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
    // remap = false: Mixin refuses remappable members in a mixin with more than one target, and
    // dropped this whole mixin at startup, so the chat was never clipped. 26.x is unobfuscated, so
    // there is nothing to remap.
    @Shadow(remap = false) @Final private GuiGraphicsExtractor graphics;
    @Shadow(remap = false) private ActiveTextCollector.Parameters parameters;

    /** The text clip that was in place before ours, put back when ours is cleared. */
    @org.spongepowered.asm.mixin.Unique
    private ScreenRectangle alpaka$previousScissor;
    @org.spongepowered.asm.mixin.Unique
    private boolean alpaka$clipped;

    @Override
    public void alpaka$setTextClip(int x0, int x1, int y0, int y1) {
        if (!this.alpaka$clipped) {
            this.alpaka$previousScissor = this.parameters.scissor();
            this.alpaka$clipped = true;
        }
        this.parameters = this.parameters.withScissor(x0, x1, y0, y1);
    }

    /**
     * Puts the previous clip back, usually none, keeping the current pose. Built directly rather
     * than through withScissor(null), which dereferences its argument and crashed the game.
     */
    @Override
    public void alpaka$clearTextClip() {
        if (!this.alpaka$clipped) return;
        this.alpaka$clipped = false;
        this.parameters = new ActiveTextCollector.Parameters(this.parameters.pose(), this.parameters.opacity(), this.alpaka$previousScissor);
        this.alpaka$previousScissor = null;
    }

    @Override
    public GuiGraphicsExtractor alpaka$graphics() {
        return this.graphics;
    }
}
