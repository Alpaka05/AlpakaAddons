package net.alpaka.addons.client.gui;

import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * A layer of {@code GuiRenderState}, which vanilla keeps package-private: implemented on it by its
 * mixin so {@link AlpakaGuiUnderlay} can add elements to a layer it created.
 */
public interface AlpakaGuiNode {

    void alpaka$addElement(GuiElementRenderState state);
}
