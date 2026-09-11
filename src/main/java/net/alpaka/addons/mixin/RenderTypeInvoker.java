package net.alpaka.addons.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens up {@code RenderType.create}, which vanilla keeps package-private because all of its own
 * render types are built inside that package. A mod that needs a render type vanilla does not
 * offer - here the item motion blur's translucent, non-depth-writing ghost type - has to go
 * through this.
 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {

    @Invoker("create")
    static RenderType alpaka$create(String name, RenderSetup setup) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
