package net.alpaka.addons.client.gui;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The typeface the mod's own screens are written in.
 *
 * Minecraft draws every string in whatever font its style names, and a style with no font means the
 * pixel font. So a screen does not switch fonts by picking a different {@link Font}; it hands every
 * string it draws a style that names one of the TrueType fonts under {@code assets/alpaka/font/}.
 * This class is the one place that style comes from, so the config screen and the widgets it is
 * built from all agree, and so the choice can be changed from the config at runtime.
 *
 * Widths have to go through here too: {@code Font.width(String)} measures in the pixel font, and a
 * caret or centred label positioned with that would be off by a few pixels per word in any other.
 *
 * Index 0 is vanilla's own font, kept as a choice so the pixel look is one click away.
 *
 * Every file under {@code assets/alpaka/font/} is a static cut. Variable-weight fonts
 * (Nunito[wght], Outfit[wght]) came up in their thinnest weight in the game: FreeType opens the
 * default instance and Minecraft never selects a named one, so the strokes were hairlines.
 *
 * <h2>One definition per GUI scale</h2>
 *
 * A TrueType provider rasterises its glyphs {@code oversample} times finer than the GUI pixel
 * grid, and the glyph is then drawn scaled down by that factor. Text is only crisp when that
 * factor equals the window's GUI scale: then one glyph texel lands on exactly one screen pixel.
 * Any other ratio resamples the glyph - at GUI scale 3 with 4x oversampling every third pixel
 * blends two texels - which is what made the letters look uneven. So every face exists three
 * times, {@code <id>_s2.json}, {@code _s3} and {@code _s4}, identical but for the oversample,
 * and {@link #style()} picks the one matching the current GUI scale. They share the TTF files.
 *
 * The glyph pages these fonts are baked into are sampled linearly rather than nearest-neighbour
 * (see {@code FontTextureMixin}); {@link #usesSmoothSampling} is how that mixin recognises them.
 */
public final class GuiFont {
    /** Display names, indexed by {@link AlpakaConfig#menuFont}. */
    public static final String[] NAMES = {"Minecraft", "Inter", "Poppins", "Varela Round", "Outfit", "Lato"};

    /** Font definition names under {@code assets/alpaka/font/}; null means vanilla's font. */
    private static final String[] REGULAR_IDS = {null, "inter", "poppins", "varela_round", "outfit", "lato"};

    /**
     * The heavier cut of each face, for {@link AlpakaConfig#menuFontBold}. SemiBold where the family
     * has one, Bold for Lato. Varela Round comes in a single weight and keeps its regular cut.
     */
    private static final String[] BOLD_IDS = {null, "inter_bold", "poppins_bold", null, "outfit_bold", "lato_bold"};

    /** Vanilla's font has no heavier file, but it does have its own bold rendering. */
    private static final Style VANILLA_BOLD = Style.EMPTY.withBold(true);

    private static final Map<String, Style> STYLE_CACHE = new HashMap<>();

    private GuiFont() {}

    /** The configured font's index, clamped so a hand-edited config cannot take the menu down. */
    public static int selectedIndex() {
        int index = AlpakaConfig.instance.menuFont;
        return (index < 0 || index >= NAMES.length) ? 0 : index;
    }

    /** The oversampling variant to use: 2 up to GUI scale 2, 3 at scale 3, 4 from scale 4 on. */
    private static String scaleSuffix() {
        int guiScale = 2;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null) {
            guiScale = minecraft.getWindow().getGuiScale();
        }
        if (guiScale <= 2) return "_s2";
        if (guiScale == 3) return "_s3";
        return "_s4";
    }

    /** The style every string in the mod's screens should carry. */
    public static Style style() {
        int index = selectedIndex();
        boolean bold = AlpakaConfig.instance.menuFontBold;
        String id = bold && BOLD_IDS[index] != null ? BOLD_IDS[index] : REGULAR_IDS[index];
        if (id == null) {
            return bold ? VANILLA_BOLD : Style.EMPTY;
        }
        String fullId = id + scaleSuffix();
        return STYLE_CACHE.computeIfAbsent(fullId, key ->
                Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", key))));
    }

    /** {@code Component.literal}, in the configured font. Legacy § codes in the text still apply. */
    public static MutableComponent text(String text) {
        return Component.literal(text).withStyle(style());
    }

    /** Width of the text in the configured font, for centring and caret placement. */
    public static int width(Font font, String text) {
        return font.width(text(text));
    }

    /** Word-wraps the text in the configured font; the lines keep the font in their styles. */
    public static List<FormattedText> splitLines(Font font, String text, int maxWidth) {
        return font.getSplitter().splitLines(text, maxWidth, style());
    }

    /**
     * Whether a glyph page belongs to one of these TrueType fonts and should be sampled linearly.
     *
     * Pages are labelled {@code <font id>/<page number>} - {@code alpaka:inter_s2/0}. Every font in
     * this namespace is one of ours except the pause menu's icon font, which is a pixel sprite
     * sheet and stays crisp.
     */
    public static boolean usesSmoothSampling(String textureLabel) {
        return textureLabel != null
                && textureLabel.startsWith("alpaka:")
                && !textureLabel.startsWith("alpaka:pause_icons");
    }
}
