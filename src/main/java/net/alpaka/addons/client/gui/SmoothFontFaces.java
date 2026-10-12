package net.alpaka.addons.client.gui;

import org.lwjgl.util.freetype.FT_Face;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The FreeType faces behind the mod's own TrueType fonts, so their glyphs can be loaded unhinted.
 *
 * Minecraft loads every TrueType glyph with FreeType's full hinting, which bends each outline onto
 * the pixel grid: stems snap to whole pixels and every advance is rounded. That suits a terminal
 * font at one size; at menu sizes it is what made Nunito's curves lumpy and its letter spacing
 * uneven. Unhinted, the outline keeps its true shape and the anti-aliasing carries the edges -
 * the way macOS draws text. Vanilla's fonts and resource packs' keep the hinting they had.
 */
public final class SmoothFontFaces {
    /** FreeType's FT_LOAD_NO_HINTING. */
    private static final int FT_LOAD_NO_HINTING = 1 << 1;

    private static final Set<Long> FACES = ConcurrentHashMap.newKeySet();
    /** Set while one of the mod's font definitions is opening its face. */
    private static final ThreadLocal<Boolean> LOADING_OURS = ThreadLocal.withInitial(() -> false);

    private SmoothFontFaces() {}

    public static void beginLoading(boolean ours) {
        LOADING_OURS.set(ours);
    }

    public static void endLoading() {
        LOADING_OURS.set(false);
    }

    /** Called as a provider takes its face; remembers it when one of ours is being loaded. */
    public static void onProviderCreated(FT_Face face) {
        if (LOADING_OURS.get() && face != null) FACES.add(face.address());
    }

    public static void onFaceClosed(FT_Face face) {
        if (face != null) FACES.remove(face.address());
    }

    /** The load flags to use for a glyph of this face. */
    public static int loadFlags(FT_Face face, int flags) {
        return face != null && FACES.contains(face.address()) ? flags | FT_LOAD_NO_HINTING : flags;
    }
}
