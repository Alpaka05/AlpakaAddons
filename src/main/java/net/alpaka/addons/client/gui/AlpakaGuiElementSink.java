package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * What {@code GuiGraphicsExtractor} gains through its mixin: a way to hand it a custom element.
 *
 * Vanilla's extractor only submits its own element types, and keeps both the render state and the
 * scissor stack private. The mixin implements this on the extractor, so a screen can cast its
 * graphics to it and submit a {@link RoundedRectRenderState} clipped to the current scissor.
 */
public interface AlpakaGuiElementSink {

    /** Adds an element to the current layer, exactly as a vanilla fill would be added. */
    void alpaka$submitElement(GuiElementRenderState state);

    /** The scissor rectangle in effect, in screen-space GUI coordinates, or null when none is. */
    ScreenRectangle alpaka$currentScissor();
}
