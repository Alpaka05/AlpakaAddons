package net.alpaka.addons.features.blockoverlay;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.alpaka.addons.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The render types the block overlay is submitted with.
 *
 * Up to 26.1 the overlay was drawn immediately, and "ignore depth" worked by flipping a static flag
 * that a mixin on {@code DepthStencilState.depthTest()} read while the quads went to the GPU. Since
 * 26.2 the outline is only <em>submitted</em> during the level render and drawn later by the
 * feature renderer, batched with everything else of the same render type, so no flag set at
 * submission time can reach the draw. What decides the depth test now is the render type's own
 * pipeline - hence a second render type, identical to vanilla's debug quads except that its depth
 * test always passes.
 */
public final class BlockOverlayRenderTypes {

    private static RenderType noDepthQuads;

    private BlockOverlayRenderTypes() {}

    /**
     * Builds the pipeline up front so it is precompiled together with vanilla's on resource reload,
     * rather than compiled the first frame the overlay is drawn through walls.
     */
    public static void init() {
        noDepthQuads();
    }

    /** Position-colour quads with the depth test switched off - the overlay seen through walls. */
    public static RenderType noDepthQuads() {
        if (noDepthQuads == null) {
            RenderPipeline pipeline = RenderPipelinesAccessor.alpaka$register(
                RenderPipeline.builder(RenderPipelinesAccessor.alpaka$debugFilledSnippet())
                    .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/block_overlay_no_depth"))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build());
            noDepthQuads = RenderTypeInvoker.alpaka$create(
                "alpaka_block_overlay_no_depth",
                RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup());
        }
        return noDepthQuads;
    }

    /** The render type for the current settings: vanilla's debug quads, or the see-through copy. */
    public static RenderType current(boolean ignoreDepth) {
        return ignoreDepth ? noDepthQuads() : RenderTypes.debugQuads();
    }
}
