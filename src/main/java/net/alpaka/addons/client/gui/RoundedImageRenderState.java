package net.alpaka.addons.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * A texture drawn over a whole rectangle with rounded corners, and a soft drop shadow if asked -
 * the screenshot preview. The vertices are those of {@link BlurRectRenderState}, so the same vertex
 * shader reads them; the fragment shader samples the texture instead of the blurred frame.
 *
 * @param radiusPx corner radius in screen pixels
 * @param shadowPx how far the drop shadow reaches past the edge, in screen pixels; 0 for none
 */
public record RoundedImageRenderState(
        Matrix3x2fc pose,
        int x0, int y0, int x1, int y1,
        int radiusPx,
        float scaleToScreen,
        int shadowPx,
        TextureSetup textureSetup,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    /**
     * @param scaleToScreen how many screen pixels one unit of the given coordinates covers under the
     *                      pose, so the edge falloff comes out one screen pixel wide
     */
    public RoundedImageRenderState(Matrix3x2fc pose, int x0, int y0, int x1, int y1, int radiusPx,
                                   float scaleToScreen, int shadowPx, TextureSetup textureSetup,
                                   ScreenRectangle scissorArea) {
        this(pose, x0, y0, x1, y1, radiusPx, scaleToScreen,
                Math.clamp(shadowPx, 0, BlurRectRenderState.MAX_SHADOW_PX), textureSetup, scissorArea,
                BlurRectRenderState.shadowedBounds(x0, y0, x1, y1,
                        Math.clamp(shadowPx, 0, BlurRectRenderState.MAX_SHADOW_PX), scaleToScreen, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        BlurRectRenderState.buildQuad(consumer, pose, x0, y0, x1, y1, radiusPx, 0xFFFFFFFF, scaleToScreen, 1.0f, shadowPx);
    }

    @Override
    public RenderPipeline pipeline() {
        return AlpakaGuiPipelines.roundedImage();
    }
}
