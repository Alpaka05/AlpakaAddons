package net.alpaka.addons.client.gui;

import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A colour and a sub-pixel offset for a skin preview on the GUI, which vanilla has no parameters for.
 *
 * A skin preview is not drawn where it is extracted: the model is rendered into a texture of its
 * own, and that texture is blitted later, always in plain white and at the whole GUI pixel its box
 * starts on. The main menu's avatar needs both to be otherwise - tinted to match the scene it stands
 * in, and placed between pixels, since at a large GUI scale a whole-pixel step is a visible jump
 * while it follows the mouse. So the style is noted against the preview's render state when it is
 * extracted, and the blit picks it up from there (see PictureInPictureRendererMixin).
 *
 * Only previews drawn between {@link #begin} and {@link #end} are styled.
 */
public final class GuiSkinStyle {
    private static boolean active = false;
    private static int color = -1;
    private static float offsetX = 0.0f;
    private static float offsetY = 0.0f;

    private record Style(int color, float offsetX, float offsetY) {}

    /** The previews of the frame being built; render states are fresh every frame, keyed by identity. */
    private static final Map<PictureInPictureRenderState, Style> STYLED = new WeakHashMap<>();

    private GuiSkinStyle() {}

    /** Previews from here until {@link #end} are blitted in {@code tint} (ARGB), moved by the offset. */
    public static void begin(int tint, float dx, float dy) {
        active = true;
        color = tint;
        offsetX = dx;
        offsetY = dy;
    }

    public static void end() {
        active = false;
    }

    /** Called as a picture-in-picture state is extracted. */
    public static void onPictureExtracted(PictureInPictureRenderState state) {
        if (active) STYLED.put(state, new Style(color, offsetX, offsetY));
    }

    /** The colour the preview is blitted in. The texture is premultiplied, which a tint of full alpha suits. */
    public static int color(PictureInPictureRenderState state, int vanilla) {
        Style style = STYLED.get(state);
        return style == null ? vanilla : style.color();
    }

    /** The pose the preview is blitted with: vanilla's, moved by the fraction of a pixel. */
    public static Matrix3x2fc pose(PictureInPictureRenderState state, Matrix3x2fc vanilla) {
        Style style = STYLED.get(state);
        if (style == null || (style.offsetX() == 0.0f && style.offsetY() == 0.0f)) return vanilla;
        return new Matrix3x2f(vanilla).translate(style.offsetX(), style.offsetY());
    }
}
