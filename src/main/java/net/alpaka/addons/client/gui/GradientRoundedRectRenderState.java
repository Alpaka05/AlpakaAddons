package net.alpaka.addons.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * {@link RoundedRectRenderState} with a colour per corner: the shader interpolates the vertex
 * colour across the quad, so four corner colours give a smooth gradient over the fill or along a
 * border for free. Equal colours on both ends of one diagonal make a straight two-colour ramp along
 * the other, with no seam where the quad's two triangles meet.
 *
 * Unlike the plain state, the scale from the given coordinates to screen pixels is a float, so it
 * also serves under a pose that scales the GUI further - a HUD drawn at the player's chosen size.
 *
 * @param scaleToScreen how many screen pixels one unit of the given coordinates covers
 * @param thicknessPx   border thickness in screen pixels; 0 draws a filled shape
 */
public record GradientRoundedRectRenderState(
        Matrix3x2fc pose,
        int x0, int y0, int x1, int y1,
        int radiusPx, int thicknessPx,
        int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft,
        float scaleToScreen,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    public GradientRoundedRectRenderState(Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                                          int radiusPx, int thicknessPx,
                                          int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft,
                                          float scaleToScreen, ScreenRectangle scissorArea) {
        this(pose, x0, y0, x1, y1, radiusPx, thicknessPx,
                colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft,
                scaleToScreen, scissorArea, boundsOf(x0, y0, x1, y1, pose, scissorArea));
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

        vertex(consumer, x0, y0, -halfW, -halfH, widthPx, heightPx, colorTopLeft);
        vertex(consumer, x0, y1, -halfW, halfH, widthPx, heightPx, colorBottomLeft);
        vertex(consumer, x1, y1, halfW, halfH, widthPx, heightPx, colorBottomRight);
        vertex(consumer, x1, y0, halfW, -halfH, widthPx, heightPx, colorTopRight);
    }

    private void vertex(VertexConsumer consumer, int x, int y, float localX, float localY,
                        int widthPx, int heightPx, int color) {
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
