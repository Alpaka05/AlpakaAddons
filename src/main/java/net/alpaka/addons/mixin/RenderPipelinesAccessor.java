package net.alpaka.addons.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens up what a mod needs to declare a render pipeline of its own.
 *
 * Vanilla keeps both the snippet its debug quads are built from and the registration method
 * private; the block overlay needs them to derive a depth-test-free copy of that pipeline for its
 * "ignore depth" mode. See {@link net.alpaka.addons.features.blockoverlay.BlockOverlayRenderTypes}.
 */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

    @Accessor("DEBUG_FILLED_SNIPPET")
    static RenderPipeline.Snippet alpaka$debugFilledSnippet() {
        throw new AssertionError("replaced by mixin");
    }

    /** The snippet vanilla builds its GUI fills from: gui shaders, translucent blend, no texture. */
    @Accessor("GUI_SNIPPET")
    static RenderPipeline.Snippet alpaka$guiSnippet() {
        throw new AssertionError("replaced by mixin");
    }

    /** The snippet vanilla's textured GUI blits are built from: adds a sampler for texture 0. */
    @Accessor("GUI_TEXTURED_SNIPPET")
    static RenderPipeline.Snippet alpaka$guiTexturedSnippet() {
        throw new AssertionError("replaced by mixin");
    }

    /** Registers the pipeline so the shader manager precompiles it with vanilla's own. */
    @Invoker("register")
    static RenderPipeline alpaka$register(RenderPipeline pipeline) {
        throw new AssertionError("replaced by mixin");
    }
}
