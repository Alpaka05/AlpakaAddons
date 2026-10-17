package net.alpaka.addons.client.gui;

import net.minecraft.client.renderer.state.gui.GuiItemRenderState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lets GUI items be drawn translucent, which vanilla has no parameter for.
 *
 * An item on the GUI is not drawn where it is extracted. Its model is rendered into the item
 * atlas, and the renderer later blits its cell from there, always in plain white. So the strength
 * is noted against the item's render state when it is extracted, and the blit picks it up from
 * there (see GuiRendererMixin); the count and the durability and cooldown bars are drawn straight
 * away and take it on the spot (see GuiGraphicsExtractorMixin).
 *
 * Everything drawn between {@link #begin} and {@link #end} is faded; outside of that nothing is
 * touched. Items too large for the atlas take a separate path and stay opaque.
 */
public final class GuiItemFade {
    private static float current = 1.0f;

    /**
     * The faded items of the frame being built. Render states are made fresh every frame and have
     * no equals of their own, so they key by identity and drop out once the frame is done with them.
     */
    private static final Map<GuiItemRenderState, Float> FADED = new WeakHashMap<>();

    private GuiItemFade() {}

    /** Items drawn from here until {@link #end} come out at this strength, 0..1. */
    public static void begin(float alpha) {
        current = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    public static void end() {
        current = 1.0f;
    }

    /** Called as an item is extracted. */
    public static void onItemExtracted(GuiItemRenderState state) {
        if (current < 1.0f) FADED.put(state, current);
    }

    /**
     * The colour the atlas blit is drawn in. The atlas holds premultiplied colour, so every channel
     * is scaled, not just the alpha - scaling only the alpha would leave the item's colour behind
     * as a glow.
     */
    public static int atlasColor(GuiItemRenderState state, int color) {
        Float alpha = FADED.remove(state);
        if (alpha == null) return color;
        int a = Math.round(alpha * 255.0f);
        return (a << 24) | (a << 16) | (a << 8) | a;
    }

    /** A decoration colour (count, durability bar, cooldown) with its alpha scaled. */
    public static int decorationColor(int color) {
        if (current >= 1.0f) return color;
        int alpha = Math.round(((color >>> 24) & 0xFF) * current);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
