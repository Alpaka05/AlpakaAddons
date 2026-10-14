package net.alpaka.addons.client.gui;

import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

/**
 * The look the mod's own HUDs share with the chat and the scoreboard: a rounded panel with a soft
 * shadow that shows the world blurred behind it, and text in the menu font, drawn flat on the panel
 * as the config menu draws its own ({@link #textShadow}).
 *
 * Each HUD decides for itself whether it uses either; this is only how they are drawn, so the
 * slayer HUD, the boss timer, the world age and the notices all come out alike. The panel samples
 * the blurred copy of the frame {@link ChatBlurFeature} keeps, at the shared Blur Strength.
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
    /** The tint over the blur: vanilla's scoreboard body box, so the panels match the sidebar. */
    public static final int TINT = 0x4C000000;

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
     * Submits a panel covering exactly this box under the current pose. Does nothing where the
     * extractor cannot take the mod's own elements; the HUD is then drawn without a background.
     *
     * @param tint    the colour over the blur; its alpha is how strongly it covers it
     * @param opacity the whole panel's strength, blur and shadow included, for fading it in and out
     */
    public static void submit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                              float scale, int tint, float opacity) {
        if (!(graphics instanceof AlpakaGuiElementSink sink) || opacity <= 0.0f) return;
        float toScreen = (float) Minecraft.getInstance().getWindow().getGuiScale() * scale;
        sink.alpaka$submitElement(new BlurRectRenderState(
                new Matrix3x2f(graphics.pose()), x0, y0, x1, y1,
                Math.round(RADIUS * toScreen), tint, toScreen, opacity,
                Math.round(SHADOW * toScreen), sink.alpaka$currentScissor()));
        ChatBlurFeature.request();
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

    /**
     * Whether a HUD's text casts vanilla's drop shadow, given whether the HUD asked for its panel.
     *
     * On the panel it does not: the config menu draws all of its text flat, and the tint under it
     * already gives the contrast the shadow is there for. Without the panel the text sits straight
     * on the world, where the shadow is what keeps it readable over snow or a bright sky - so the
     * shadow stays there, and also where the extractor cannot take the panel and it is not drawn
     * after all. Every HUD that can sit on a panel asks this rather than deciding for itself.
     */
    public static boolean textShadow(GuiGraphicsExtractor graphics, boolean blurred) {
        return !blurred || !(graphics instanceof AlpakaGuiElementSink);
    }

    /** The text in the menu font when the HUD asks for it, unchanged otherwise. */
    public static Component text(Component text, boolean smoothFont) {
        return smoothFont ? GuiFont.restyle(text) : text;
    }
}
