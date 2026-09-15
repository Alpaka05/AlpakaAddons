package net.alpaka.addons.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.minecraft.resources.Identifier;

/**
 * Render pipelines for the mod's own GUI drawing.
 *
 * {@link #roundedRect()} draws an anti-aliased rounded rectangle, filled or as a border, from a
 * single quad: the fragment shader evaluates a signed distance to the shape's edge and fades the
 * coverage over one screen pixel. That is what gives the config menu smooth curves at any GUI
 * scale, where fills could only stack one-pixel strips.
 *
 * The shape is carried in the vertex data rather than in uniforms, so any number of rectangles
 * batch into one draw like vanilla's own GUI fills. The ENTITY vertex format is borrowed for its
 * spare attributes: UV0 holds the fragment's position relative to the centre, UV1 the size and
 * UV2 the radius and border thickness - see {@link RoundedRectRenderState}.
 *
 * Registered through vanilla's own registry at client start, before the shader manager compiles,
 * so the shaders are built alongside vanilla's rather than on first use.
 */
public final class AlpakaGuiPipelines {
    private static RenderPipeline roundedRect;
    private static RenderPipeline blurRect;

    private AlpakaGuiPipelines() {}

    public static void init() {
        roundedRect();
        blurRect();
    }

    /**
     * The rounded rectangle again, filled with the blurred frame behind it: the same vertices and
     * vertex shader as {@link #roundedRect()}, on top of vanilla's textured GUI snippet so the
     * fragment shader gets a sampler for the blurred copy. See {@link BlurRectRenderState}.
     */
    public static RenderPipeline blurRect() {
        if (blurRect == null) {
            blurRect = RenderPipelinesAccessor.alpaka$register(
                    RenderPipeline.builder(RenderPipelinesAccessor.alpaka$guiTexturedSnippet())
                            .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/gui_blur_rect"))
                            .withVertexShader(Identifier.fromNamespaceAndPath("alpaka", "core/rounded_rect"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath("alpaka", "core/blur_rect"))
                            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                            .build());
        }
        return blurRect;
    }

    public static RenderPipeline roundedRect() {
        if (roundedRect == null) {
            roundedRect = RenderPipelinesAccessor.alpaka$register(
                    RenderPipeline.builder(RenderPipelinesAccessor.alpaka$guiSnippet())
                            .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/gui_rounded_rect"))
                            .withVertexShader(Identifier.fromNamespaceAndPath("alpaka", "core/rounded_rect"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath("alpaka", "core/rounded_rect"))
                            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                            .build());
        }
        return roundedRect;
    }
}
