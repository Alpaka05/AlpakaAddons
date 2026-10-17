package net.alpaka.addons.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * One anti-aliased rounded rectangle in the GUI render state, drawn by
 * {@link AlpakaGuiPipelines#roundedRect()}.
 *
 * Positions are GUI coordinates under the given pose, like vanilla's own coloured rectangle. The
 * shape parameters travel in screen pixels, because the shader's one-pixel edge falloff has to be
 * one *screen* pixel wide whatever the GUI scale: each vertex carries its offset from the
 * rectangle's centre in UV0, the full size in UV1 and the corner radius and border thickness in
 * UV2, so the fragment shader can evaluate the distance to the edge without any per-element
 * uniforms.
 *
 * @param thicknessPx border thickness in screen pixels; 0 draws a filled shape
 */
public record RoundedRectRenderState(
        Matrix3x2fc pose,
        int x0, int y0, int x1, int y1,
        int radiusPx, int thicknessPx,
        int color,
        int guiScale,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    public RoundedRectRenderState(Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                                  int radiusPx, int thicknessPx, int color, int guiScale, ScreenRectangle scissorArea) {
        this(pose, x0, y0, x1, y1, radiusPx, thicknessPx, color, guiScale, scissorArea,
                boundsOf(x0, y0, x1, y1, pose, scissorArea));
    }

    private static ScreenRectangle boundsOf(int x0, int y0, int x1, int y1, Matrix3x2fc pose, ScreenRectangle scissor) {
        ScreenRectangle rect = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(rect) : rect;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        float halfW = (x1 - x0) * guiScale / 2.0f;
        float halfH = (y1 - y0) * guiScale / 2.0f;
        int widthPx = (x1 - x0) * guiScale;
        int heightPx = (y1 - y0) * guiScale;

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
                .setUv2(radiusPx, thicknessPx)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    @Override
    public RenderPipeline pipeline() {
        return AlpakaGuiPipelines.roundedRect();
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
