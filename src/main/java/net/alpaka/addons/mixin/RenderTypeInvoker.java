package net.alpaka.addons.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the package-private factory vanilla builds every {@link RenderType} with, so the block
 * overlay can wrap its own pipeline in a render type the submit-node renderer understands.
 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {

    @Invoker("create")
    static RenderType alpaka$create(String name, RenderSetup setup) {
        throw new AssertionError("replaced by mixin");
    }
}
