package net.alpaka.addons.mixin;

import net.alpaka.addons.client.gui.AlpakaGuiNode;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets {@link GuiRenderStateMixin} add elements to a layer, whose class vanilla keeps private. */
@Mixin(targets = "net.minecraft.client.renderer.state.gui.GuiRenderState$Node")
public abstract class GuiRenderStateNodeMixin implements AlpakaGuiNode {

    @Shadow public abstract void addGuiElement(GuiElementRenderState state);

    @Override
    public void alpaka$addElement(GuiElementRenderState state) {
        addGuiElement(state);
    }
}
