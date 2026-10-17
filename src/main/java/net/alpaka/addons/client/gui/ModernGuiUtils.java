package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The drawing primitives every window and HUD of the mod builds on: the user's accent, rectangles,
 * anti-aliased rounded shapes, soft shadows and blended colors. What the controls look like is
 * {@link AlpakaStyle}'s business.
 */
public final class ModernGuiUtils {
    private ModernGuiUtils() {}

    public static int getAccentColor() {
        return net.alpaka.addons.config.AlpakaConfig.instance.menuAccentColor;
    }

    /** The accent at 65 % of its brightness; see {@link AlpakaStyle#accentDim()}. */
    public static int getAccentDimColor() {
        int color = net.alpaka.addons.config.AlpakaConfig.instance.menuAccentColor;
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * 0.65f);
        int g = (int) (((color >> 8) & 0xFF) * 0.65f);
        int b = (int) ((color & 0xFF) * 0.65f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    /** A square one-pixel outline, for where rounded shapes cannot be drawn. */
    public static void drawOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // ---- Rounded shapes -------------------------------------------------------------------------
    //
    // Every rounded shape is one quad drawn by the mod's own pipeline (AlpakaGuiPipelines), whose
    // fragment shader measures the distance to the shape's edge and fades coverage over one screen
    // pixel. That gives properly anti-aliased curves and hairline borders at any GUI scale; the
    // earlier strip-built shapes were resolved to pixels and showed their steps at every corner.
    // The shape parameters are handed over in screen pixels so the falloff is one *screen* pixel
    // wide however the GUI is scaled.

    /** The window's GUI scale: how many screen pixels one GUI pixel spans. */
    private static int guiScale() {
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return window == null ? 1 : Math.max(1, window.getGuiScale());
    }

    /** Border thickness in screen pixels: a hairline at GUI scale 2 and 3, two pixels from 4 up. */
    private static int hairline(int scale) {
        return Math.max(1, Math.round(scale * 0.5f));
    }

    private static void submitRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                      int radiusPx, int thicknessPx, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        if (!(graphics instanceof AlpakaGuiElementSink sink)) {
            // Without the mixin there is no way to submit a custom element; a square fill is the
            // honest fallback rather than nothing.
            drawRect(graphics, x, y, width, height, color);
            return;
        }
        int scale = guiScale();
        sink.alpaka$submitElement(new RoundedRectRenderState(
                new org.joml.Matrix3x2f(graphics.pose()),
                x, y, x + width, y + height,
                radiusPx, thicknessPx, color, scale,
                sink.alpaka$currentScissor()));
    }

    /** A filled rectangle with rounded corners of the given radius, in GUI coordinates. */
    public static void drawRoundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        submitRounded(graphics, x, y, width, height, Math.max(0, radius) * guiScale(), 0, color);
    }

    /** A hairline outline with rounded corners of the given radius, in GUI coordinates. */
    public static void drawRoundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        int scale = guiScale();
        submitRounded(graphics, x, y, width, height, Math.max(0, radius) * scale, hairline(scale), color);
    }

    /**
     * A panel's soft drop shadow: a few translucent, progressively larger and fainter copies of its
     * rounded outline behind it.
     */
    public static void drawPanelShadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, float strength) {
        for (int i = 1; i <= 6; i++) {
            int alpha = (int) ((0x22 * strength) * (1.0f - (float) i / 6.0f));
            if (alpha <= 0) continue;
            drawRoundedRect(graphics, x - i, y - i, width + i * 2, height + i * 2, radius + i, alpha << 24);
        }
    }

    /** Centred text in the configured menu font, without vanilla's drop shadow. */
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, net.minecraft.network.chat.Component text, int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /**
     * Linearly blends two ARGB colors; {@code t} 0 gives {@code from}, 1 gives {@code to}.
     * For controls whose color follows an animation instead of flipping with the state.
     */
    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
