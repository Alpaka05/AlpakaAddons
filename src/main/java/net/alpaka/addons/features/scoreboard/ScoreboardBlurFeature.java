package net.alpaka.addons.features.scoreboard;

import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.client.gui.HudPanel;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The blurred scoreboard background: one rounded panel behind the sidebar that shows the world
 * blurred through it, with a soft shadow, in place of vanilla's two flat boxes (the darker title
 * row and the body under it).
 *
 * Vanilla draws the title box first and the body box second, both before any text. The title box
 * is only measured; when the body box comes, one panel spanning both is submitted in their place,
 * so the text still lands on top. The panel samples the same blurred copy of the frame as the chat
 * ({@link ChatBlurFeature}), at the chat's Blur Strength.
 *
 * Vanilla puts the sidebar a pixel from the right edge of the screen, which leaves the panel no
 * room for its padding and rounded corners there. While the panel is on, the whole sidebar is
 * moved left by {@link #SHIFT}.
 */
public final class ScoreboardBlurFeature {

    /** How far the sidebar moves left, in GUI pixels, so the panel clears the screen edge. */
    public static final int SHIFT = 3;
    /**
     * How far the panel grows past vanilla's boxes, in GUI pixels. Its corners and shadow are
     * {@link HudPanel}'s, which draws it.
     */
    private static final int PADDING = 2;

    // The sidebar being extracted: whether it is drawn as a panel, and the title box's top edge.
    private static boolean active;
    private static int titleTop;

    private ScoreboardBlurFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.blurOn(AlpakaConfig.instance.scoreboardBlurEnabled);
    }

    /** The sidebar is about to be extracted; moves it off the screen edge when it gets a panel. */
    public static void begin(GuiGraphicsExtractor graphics) {
        active = isEnabled() && graphics instanceof AlpakaGuiElementSink;
        titleTop = Integer.MIN_VALUE;
        if (active) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(-SHIFT, 0.0f);
        }
    }

    public static void end(GuiGraphicsExtractor graphics) {
        if (active) {
            graphics.pose().popMatrix();
            active = false;
        }
    }

    /** Vanilla's title box. Returns true when the panel takes it over and it must not be drawn. */
    public static boolean titleBox(int y0) {
        if (!active) return false;
        titleTop = y0;
        return true;
    }

    /** Vanilla's body box. Returns true when the panel takes it over and it must not be drawn. */
    public static boolean bodyBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (!active || !(graphics instanceof AlpakaGuiElementSink)) return false;
        int top = titleTop != Integer.MIN_VALUE ? Math.min(titleTop, y0) : y0;
        // The sidebar has no scale of its own: one panel pixel is one GUI pixel. Vanilla's body
        // colour is the tint, so the player's text background opacity still sets its strength.
        HudPanel.submit(graphics, x0 - PADDING, top - PADDING, x1 + PADDING, y1 + PADDING, 1.0f, color, 1.0f);
        return true;
    }
}
