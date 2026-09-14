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
 * Every file under {@code assets/alpaka/font/} is a static Regular cut. Variable-weight fonts
 * (Nunito[wght], Outfit[wght]) came up in their thinnest weight in the game: FreeType opens the
 * default instance and Minecraft never selects a named one, so the strokes were hairlines.
 */
public final class GuiFont {
    /** Display names, indexed by {@link AlpakaConfig#menuFont}. */
    public static final String[] NAMES = {"Minecraft", "Inter", "Poppins", "Varela Round", "Outfit", "Lato"};

    /** Font definition names under {@code assets/alpaka/font/}; null means vanilla's font. */
    private static final String[] FONT_IDS = {null, "inter", "poppins", "varela_round", "outfit", "lato"};

    private static final Style[] STYLES = new Style[NAMES.length];

    static {
        STYLES[0] = Style.EMPTY;
        for (int i = 1; i < NAMES.length; i++) {
            STYLES[i] = Style.EMPTY.withFont(
                    new FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", FONT_IDS[i])));
        }
    }

    private GuiFont() {}

    /** The configured font's index, clamped so a hand-edited config cannot take the menu down. */
    public static int selectedIndex() {
        int index = AlpakaConfig.instance.menuFont;
        return (index < 0 || index >= NAMES.length) ? 0 : index;
    }

    /** The style every string in the mod's screens should carry. */
    public static Style style() {
        return STYLES[selectedIndex()];
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
}
