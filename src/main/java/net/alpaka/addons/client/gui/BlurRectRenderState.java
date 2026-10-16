package net.alpaka.addons.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * A rounded rectangle that shows the blurred frame behind it, tinted - the chat's background panel.
 *
 * The vertex layout is the one {@link RoundedRectRenderState} uses, and the same vertex shader
 * reads it; only the fragment shader differs, sampling the blurred copy of the frame that
 * {@link ChatBlurFeature} keeps. Both the pipeline and the texture are looked up when the GUI
 * renderer prepares the frame, not when the element is extracted: by then the copy of this very
 * frame exists, and if it could not be made the element falls back to the plain rounded pipeline,
 * whose shader reads the same vertices, so the chat keeps a background either way.
 *
 * A panel can also cast a soft drop shadow: the quad is then grown by the shadow's reach on every
 * side, and the fragment shader shades the band outside the rounded edge with a falloff instead of
 * discarding it. The shadow's reach and strength ride in the normal's x and y, which the shape
 * shaders otherwise leave unused.
 *
 * @param color    the tint over the blurred frame; its alpha is how strongly it covers the blur
 * @param radiusPx corner radius in screen pixels
 * @param opacity  the whole panel's strength, 0..1, blur included; 1 unless it is fading
 * @param shadowPx how far the drop shadow reaches past the edge, in screen pixels; 0 for none
 */
public record BlurRectRenderState(
        Matrix3x2fc pose,
        int x0, int y0, int x1, int y1,
        int radiusPx,
        int color,
        float scaleToScreen,
        float opacity,
        int shadowPx,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    /** The largest shadow reach the normal can carry, in screen pixels. */
    public static final int MAX_SHADOW_PX = 64;
    /** How dark the shadow is right at the panel's edge. */
    private static final float SHADOW_ALPHA = 0.45f;

    /**
     * @param scaleToScreen how many screen pixels one unit of the given coordinates covers under the
     *                      pose, so the shader's per-pixel edge falloff comes out one screen pixel wide
     */
    public BlurRectRenderState(Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                               int radiusPx, int color, float scaleToScreen, ScreenRectangle scissorArea) {
        this(pose, x0, y0, x1, y1, radiusPx, color, scaleToScreen, 1.0f, scissorArea);
    }

    public BlurRectRenderState(Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                               int radiusPx, int color, float scaleToScreen, float opacity,
                               ScreenRectangle scissorArea) {
        this(pose, x0, y0, x1, y1, radiusPx, color, scaleToScreen, opacity, 0, scissorArea);
    }

    public BlurRectRenderState(Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                               int radiusPx, int color, float scaleToScreen, float opacity, int shadowPx,
                               ScreenRectangle scissorArea) {
        this(pose, x0, y0, x1, y1, radiusPx, color, scaleToScreen, opacity,
                Math.clamp(shadowPx, 0, MAX_SHADOW_PX), scissorArea,
                boundsOf(x0, y0, x1, y1, margin(Math.clamp(shadowPx, 0, MAX_SHADOW_PX), scaleToScreen), pose, scissorArea));
    }

    /** The shadow's reach in the panel's own coordinates, rounded up to whole units for the bounds. */
    private static int margin(int shadowPx, float scaleToScreen) {
        return shadowPx <= 0 ? 0 : (int) Math.ceil(shadowPx / Math.max(scaleToScreen, 0.01f));
    }

    private static ScreenRectangle boundsOf(int x0, int y0, int x1, int y1, int margin, Matrix3x2fc pose, ScreenRectangle scissor) {
        ScreenRectangle rect = new ScreenRectangle(x0 - margin, y0 - margin, x1 - x0 + 2 * margin, y1 - y0 + 2 * margin)
                .transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(rect) : rect;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        buildQuad(consumer, pose, x0, y0, x1, y1, radiusPx, color, scaleToScreen, opacity, shadowPx);
    }

    /**
     * The rounded rectangle's quad in the layout the shape shaders read, grown by the shadow's reach;
     * shared with {@link RoundedImageRenderState}.
     */
    static void buildQuad(VertexConsumer consumer, Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                          int radiusPx, int color, float scaleToScreen, float opacity, int shadowPx) {
        float halfW = (x1 - x0) * scaleToScreen / 2.0f;
        float halfH = (y1 - y0) * scaleToScreen / 2.0f;
        int widthPx = Math.round((x1 - x0) * scaleToScreen);
        int heightPx = Math.round((y1 - y0) * scaleToScreen);
        // With a shadow the quad reaches past the panel; UV1 stays the panel's own size.
        float grow = shadowPx / Math.max(scaleToScreen, 0.01f);
        float growPx = shadowPx;
        // The vertex shader reads the normal's z as the opacity, x and y as the shadow's reach (in
        // 64ths of MAX_SHADOW_PX) and strength.
        float reach = shadowPx / (float) MAX_SHADOW_PX;
        float strength = shadowPx > 0 ? SHADOW_ALPHA : 0.0f;

        float[][] corners = {
                {x0 - grow, y0 - grow, -halfW - growPx, -halfH - growPx},
                {x0 - grow, y1 + grow, -halfW - growPx, halfH + growPx},
                {x1 + grow, y1 + grow, halfW + growPx, halfH + growPx},
                {x1 + grow, y0 - grow, halfW + growPx, -halfH - growPx},
        };
        for (float[] c : corners) {
            consumer.addVertexWith2DPose(pose, c[0], c[1])
                    .setColor(color)
                    .setUv(c[2], c[3])
                    .setUv1(widthPx, heightPx)
                    .setUv2(radiusPx, 0)
                    .setNormal(reach, strength, opacity);
        }
    }

    /** The screen area an element in this layout covers, its shadow included. */
    static ScreenRectangle shadowedBounds(int x0, int y0, int x1, int y1, int shadowPx, float scaleToScreen,
                                          Matrix3x2fc pose, ScreenRectangle scissor) {
        return boundsOf(x0, y0, x1, y1, margin(shadowPx, scaleToScreen), pose, scissor);
    }

    @Override
    public RenderPipeline pipeline() {
        return ChatBlurFeature.isReady() ? AlpakaGuiPipelines.blurRect() : AlpakaGuiPipelines.roundedRect();
    }

    @Override
    public TextureSetup textureSetup() {
        return ChatBlurFeature.textureSetup();
    }
}
