package net.alpaka.addons.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * One soft shape of light in the GUI - a glow, a ring, a ray or a sparkle - drawn by
 * {@link AlpakaGuiPipelines#glow()} from a single quad of any shape: the four corners are given in
 * GUI coordinates, so a ray can point any way, and the fragment shader draws the shape from where
 * each corner lies in the shape's own coordinates. No texture, so any number batch into one draw.
 *
 * The corners go round in order: (x0, y0) to (x3, y3), with (u, v) for each.
 */
public record GlowRenderState(
        Matrix3x2fc pose,
        float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
        float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
        int shape, int param, int color,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    public static final int GLOW = 0;
    public static final int RING = 1;
    public static final int RAY = 2;
    public static final int SPARKLE = 3;

    /** A glow, ring or sparkle: a square around (cx, cy), reaching out by radius. */
    public static GlowRenderState disc(Matrix3x2fc pose, float cx, float cy, float radius, int shape, int param, int color) {
        float l = cx - radius, r = cx + radius, t = cy - radius, b = cy + radius;
        return new GlowRenderState(pose, l, t, l, b, r, b, r, t,
                -1, -1, -1, 1, 1, 1, 1, -1, shape, param, color, boundsOf(pose, l, t, r, b));
    }

    /**
     * A ray from (cx, cy) in the direction (dx, dy), a unit vector: from {@code from} to {@code to}
     * out, {@code width} wide at its root, narrowing to a third of that at its tip.
     */
    public static GlowRenderState ray(Matrix3x2fc pose, float cx, float cy, float dx, float dy,
                                      float from, float to, float width, int color) {
        float px = -dy, py = dx;
        float h0 = width / 2f, h1 = width / 6f;
        float ax = cx + dx * from, ay = cy + dy * from;
        float bx = cx + dx * to, by = cy + dy * to;
        float x0 = ax + px * h0, y0 = ay + py * h0;
        float x1 = ax - px * h0, y1 = ay - py * h0;
        float x2 = bx - px * h1, y2 = by - py * h1;
        float x3 = bx + px * h1, y3 = by + py * h1;
        float l = Math.min(Math.min(x0, x1), Math.min(x2, x3));
        float r = Math.max(Math.max(x0, x1), Math.max(x2, x3));
        float t = Math.min(Math.min(y0, y1), Math.min(y2, y3));
        float b = Math.max(Math.max(y0, y1), Math.max(y2, y3));
        return new GlowRenderState(pose, x0, y0, x1, y1, x2, y2, x3, y3,
                0, 1, 0, -1, 1, -1, 1, 1, RAY, 0, color, boundsOf(pose, l, t, r, b));
    }

    private static ScreenRectangle boundsOf(Matrix3x2fc pose, float l, float t, float r, float b) {
        int x = (int) Math.floor(l), y = (int) Math.floor(t);
        return new ScreenRectangle(x, y, (int) Math.ceil(r) - x + 1, (int) Math.ceil(b) - y + 1).transformMaxBounds(pose);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        vertex(consumer, x0, y0, u0, v0);
        vertex(consumer, x1, y1, u1, v1);
        vertex(consumer, x2, y2, u2, v2);
        vertex(consumer, x3, y3, u3, v3);
    }

    private void vertex(VertexConsumer consumer, float x, float y, float u, float v) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(color)
                .setUv(u, v)
                .setUv1(0, 0)
                .setUv2(shape, param)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    @Override
    public RenderPipeline pipeline() {
        return AlpakaGuiPipelines.glow();
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }
}
