package net.alpaka.addons.client.gui;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.List;

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

    private static final Style[] REGULAR = new Style[NAMES.length];
    private static final Style[] BOLD = new Style[NAMES.length];

    static {
        for (int i = 0; i < NAMES.length; i++) {
            REGULAR[i] = REGULAR_IDS[i] == null ? Style.EMPTY : fontStyle(REGULAR_IDS[i]);
        }
        // Vanilla's font has no heavier file, but it does have its own bold rendering.
        BOLD[0] = Style.EMPTY.withBold(true);
        for (int i = 1; i < NAMES.length; i++) {
            BOLD[i] = BOLD_IDS[i] == null ? REGULAR[i] : fontStyle(BOLD_IDS[i]);
        }
    }

    private GuiFont() {}

    private static Style fontStyle(String id) {
        return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", id)));
    }

    /** The configured font's index, clamped so a hand-edited config cannot take the menu down. */
    public static int selectedIndex() {
        int index = AlpakaConfig.instance.menuFont;
        return (index < 0 || index >= NAMES.length) ? 0 : index;
    }

    /** The style every string in the mod's screens should carry. */
    public static Style style() {
        int index = selectedIndex();
        return AlpakaConfig.instance.menuFontBold ? BOLD[index] : REGULAR[index];
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
     * Pages are labelled {@code <font id>/<page number>} - {@code alpaka:inter/0} - so the label is
     * matched against every id here. The pause menu's icon font is also under {@code alpaka:} and is
     * deliberately not listed: it is a pixel sprite sheet and stays crisp.
     */
    public static boolean usesSmoothSampling(String textureLabel) {
        if (textureLabel == null) return false;
        for (String[] ids : new String[][] {REGULAR_IDS, BOLD_IDS}) {
            for (String id : ids) {
                if (id != null && textureLabel.startsWith("alpaka:" + id + "/")) return true;
            }
        }
        return false;
    }
}
