package net.alpaka.addons.client.gui;

import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * What {@code GuiRenderState} gains through its mixin: a layer under every other GUI element of the
 * frame.
 *
 * Vanilla's render state only ever stacks a new element above what it overlaps, and each HUD part
 * opens a stratum of its own, so whatever is extracted later is drawn on top. The chat is extracted
 * after the action bar and the hotbar, yet its blurred panel has to lie beneath them, so they stay
 * readable through it. An element added here goes into a stratum of its own before the first one,
 * drawn before anything else of the frame.
 */
public interface AlpakaGuiUnderlay {

    /** Adds an element to the layer under everything else this frame. */
    void alpaka$addUnderlay(GuiElementRenderState state);
}
