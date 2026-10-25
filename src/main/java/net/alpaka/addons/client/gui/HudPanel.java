package net.alpaka.addons.client.gui;

import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

/**
 * The look the mod's own HUDs share with the chat and the scoreboard: a rounded panel with a soft
 * shadow that shows the world blurred behind it, and text in the menu font.
 *
 * Each HUD decides for itself whether it uses either; this is only how they are drawn, so the
 * slayer HUD, the boss timer, the world age and the notices all come out alike. The panel samples
 * the blurred copy of the frame {@link ChatBlurFeature} keeps, at the shared Blur Strength.
 *
 * Text on a HUD always casts vanilla's drop shadow ({@link #TEXT_SHADOW}), panel or not, as the
 * chat's and the sidebar's lines do. Only text on a dense panel - a notice's - is drawn flat
 * ({@link #DENSE_PANEL_TEXT_SHADOW}), as the config menu's is.
 */
public final class HudPanel {
    /** How far the panel reaches past the text it sits behind, in the HUD's own pixels. */
    public static final int PAD_X = 4;
    public static final int PAD_Y = 3;
    /**
     * Corner radius and shadow reach, in the HUD's own pixels; the scoreboard panel's. Public so a
     * HUD that submits a panel of its own shape - the inventory's, with its frame - keeps the same
     * corners and shadow as the rest.
     */
    public static final int RADIUS = 4;
    public static final int SHADOW = 4;
    /**
     * The tint over the blur for the mod's own HUDs: 45% black.
     *
     * Vanilla's scoreboard body box (30%) was the first choice, to match the sidebar, but over a
     * bright sky, sand or snow the blur behind the panel stays bright, and the HUDs' gray labels
     * came out at about 1.5:1 against it. At 45% they reach about 2:1 there, with their drop shadow
     * on top, and white values clear 4.5:1 over sky and sand. It stays lighter than vanilla's chat
     * background (50% at the default opacity), so the panel still reads as the blurred world and
     * not as a box. The sidebar itself keeps vanilla's color, which follows the player's text
     * background opacity; see {@code ScoreboardBlurFeature}.
     */
    public static final int TINT = 0x73000000;

    /** Whether text on a HUD casts vanilla's drop shadow: always, on a panel or without one. */
    public static final boolean TEXT_SHADOW = true;

    /**
     * Whether text on a dense panel casts the shadow: never. A notice's box covers what is behind
     * it, blurred or not, and gives the contrast a shadow would; its text is flat as the config
     * menu's.
     */
    public static final boolean DENSE_PANEL_TEXT_SHADOW = false;

    private HudPanel() {}

    /**
     * Submits a panel around a block of text that spans {@code 0..width} by {@code 0..height} under
     * the current pose, grown by the padding on every side.
     *
     * @param scale the HUD's own scale, which the pose already applies; with the GUI scale it says
     *              how many screen pixels one HUD pixel covers
     */
    public static void aroundText(GuiGraphicsExtractor graphics, int width, int height, float scale) {
        submit(graphics, -PAD_X, -PAD_Y, width + PAD_X, height + PAD_Y, scale, TINT, 1.0f);
    }

    /**
     * Submits a panel covering exactly this box under the current pose. Every extractor can take
     * it: a mixin makes {@code GuiGraphicsExtractor} an {@link AlpakaGuiElementSink}, and the check
     * here only guards against that mixin failing to apply.
     *
     * @param tint    the color over the blur; its alpha is how strongly it covers it
     * @param opacity the whole panel's strength, blur and shadow included, for fading it in and out
     */
    public static void submit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                              float scale, int tint, float opacity) {
        submit(graphics, x0, y0, x1, y1, scale, tint, opacity, Glass.HUD);
    }

    /** How a panel asks for the blur behind it. */
    public enum Glass {
        /** A panel of the HUD, under any open screen; see {@link ChatBlurFeature#requestForHud}. */
        HUD,
        /** A panel drawn on top of a screen - a notice over the config menu. */
        OVER_SCREEN,
        /** No blur: the panel is the plain tint. */
        PLAIN
    }

    /** As {@link #submit(GuiGraphicsExtractor, int, int, int, int, float, int, float)}, asking for the blur as {@code glass} says. */
    public static void submit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                              float scale, int tint, float opacity, Glass glass) {
        if (!(graphics instanceof AlpakaGuiElementSink sink) || opacity <= 0.0f) return;
        float toScreen = (float) Minecraft.getInstance().getWindow().getGuiScale() * scale;
        BlurRectRenderState panel = new BlurRectRenderState(
                new Matrix3x2f(graphics.pose()), x0, y0, x1, y1,
                Math.round(RADIUS * toScreen), tint, toScreen, opacity,
                Math.round(SHADOW * toScreen), sink.alpaka$currentScissor());
        // Before the submission, so a HUD cached by Gnetum hands this panel to the frame's own pass.
        switch (glass) {
            case HUD -> ChatBlurFeature.requestForHud(panel.bounds());
            case OVER_SCREEN -> ChatBlurFeature.request(panel.bounds());
            case PLAIN -> { }
        }
        sink.alpaka$submitElement(panel);
    }

    /**
     * How far a HUD's box reaches past its text, in GUI pixels: its own margin, or the panel's
     * padding when that is wider. The HUD editor draws and clamps that box, so a panel at the
     * screen's edge is kept on screen with the text inside it.
     */
    public static int margin(boolean blurred, float scale, int textMargin) {
        if (!blurred) return textMargin;
        return Math.max(textMargin, (int) Math.ceil(PAD_X * scale));
    }

    /** The text in the menu font when the HUD asks for it, unchanged otherwise. */
    public static Component text(Component text, boolean smoothFont) {
        return smoothFont ? GuiFont.restyle(text) : text;
    }
}
