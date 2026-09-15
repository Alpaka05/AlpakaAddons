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
 * @param color    the tint over the blurred frame; its alpha is how strongly it covers the blur
 * @param radiusPx corner radius in screen pixels
 */
public record BlurRectRenderState(
        Matrix3x2fc pose,
        int x0, int y0, int x1, int y1,
        int radiusPx,
        int color,
        float scaleToScreen,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    /**
     * @param scaleToScreen how many screen pixels one unit of the given coordinates covers under the
     *                      pose, so the shader's per-pixel edge falloff comes out one screen pixel wide
     */
    public BlurRectRenderState(Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                               int radiusPx, int color, float scaleToScreen, ScreenRectangle scissorArea) {
        this(pose, x0, y0, x1, y1, radiusPx, color, scaleToScreen, scissorArea,
                boundsOf(x0, y0, x1, y1, pose, scissorArea));
    }

    private static ScreenRectangle boundsOf(int x0, int y0, int x1, int y1, Matrix3x2fc pose, ScreenRectangle scissor) {
        ScreenRectangle rect = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(rect) : rect;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        float halfW = (x1 - x0) * scaleToScreen / 2.0f;
        float halfH = (y1 - y0) * scaleToScreen / 2.0f;
        int widthPx = Math.round((x1 - x0) * scaleToScreen);
        int heightPx = Math.round((y1 - y0) * scaleToScreen);

        vertex(consumer, x0, y0, -halfW, -halfH, widthPx, heightPx);
        vertex(consumer, x0, y1, -halfW, halfH, widthPx, heightPx);
        vertex(consumer, x1, y1, halfW, halfH, widthPx, heightPx);
        vertex(consumer, x1, y0, halfW, -halfH, widthPx, heightPx);
    }

    private void vertex(VertexConsumer consumer, int x, int y, float localX, float localY, int widthPx, int heightPx) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(color)
                .setUv(localX, localY)
                .setUv1(widthPx, heightPx)
                .setUv2(radiusPx, 0)
                .setNormal(0.0f, 0.0f, 1.0f);
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
