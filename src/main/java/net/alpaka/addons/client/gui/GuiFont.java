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
 * Poppins and Outfit ship only in SemiBold - their regular cuts looked frail at menu size.
 *
 * <h2>One definition per GUI scale</h2>
 *
 * A TrueType provider rasterises its glyphs {@code oversample} times finer than the GUI pixel
 * grid, and the glyph is then drawn scaled down by that factor. Text is only crisp when that
 * factor equals the window's GUI scale: then one glyph texel lands on exactly one screen pixel.
 * Any other ratio resamples the glyph - at GUI scale 3 with 4x oversampling every third pixel
 * blends two texels - which is what made the letters look uneven. So every face exists three
 * times, {@code <id>_s2.json}, {@code _s3} and {@code _s4}, identical but for the oversample,
 * and {@link #style()} picks the one matching the current GUI scale. They share the TTF files, as
 * do the larger {@code <id>_title_s<n>.json} behind {@link #title}.
 *
 * The glyph pages these fonts are baked into are sampled linearly rather than nearest-neighbour
 * (see {@code FontTextureMixin}); {@link #usesSmoothSampling} is how that mixin recognises them.
 */
public final class GuiFont {
    /** Display names, indexed by {@link AlpakaConfig#menuFont}. */
    public static final String[] NAMES = {"Minecraft", "Inter", "Poppins", "Varela Round", "Outfit"};

    /** Font definition names under {@code assets/alpaka/font/}; null means vanilla's font. */
    private static final String[] FONT_IDS = {null, "inter", "poppins_bold", "varela_round", "outfit_bold"};

    /** The oversampling variants, in the order {@link #scaleSlot()} numbers them. */
    private static final String[] SCALE_SUFFIXES = {"_s2", "_s3", "_s4"};

    /**
     * The styles handed out so far, by font index and scale slot, for text and for titles. Kept in
     * arrays rather than looked up by name: style() runs for every string the menus, the HUDs and the
     * scoreboard draw, every frame, and building the name to look it up allocated each time. A style
     * is immutable, so two threads filling the same slot at once only store equal values.
     */
    private static final Style[][] TEXT_STYLES = new Style[FONT_IDS.length][SCALE_SUFFIXES.length];
    private static final Style[][] TITLE_STYLES = new Style[FONT_IDS.length][SCALE_SUFFIXES.length];

    private GuiFont() {}

    /** The configured font's index, clamped so a hand-edited config cannot take the menu down. */
    public static int selectedIndex() {
        int index = AlpakaConfig.instance.menuFont;
        return (index < 0 || index >= NAMES.length) ? 0 : index;
    }

    /**
     * The oversampling variant to use, as a slot into {@link #SCALE_SUFFIXES}: 2 up to GUI scale 2,
     * 3 at scale 3, 4 from scale 4 on.
     */
    private static int scaleSlot() {
        int guiScale = 2;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null) {
            guiScale = minecraft.getWindow().getGuiScale();
        }
        if (guiScale <= 2) return 0;
        if (guiScale == 3) return 1;
        return 2;
    }

    private static Style fontStyle(String path) {
        return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", path)));
    }

    /** The style every string in the mod's screens should carry. */
    public static Style style() {
        int index = selectedIndex();
        String id = FONT_IDS[index];
        if (id == null) {
            return Style.EMPTY;
        }
        int slot = scaleSlot();
        Style style = TEXT_STYLES[index][slot];
        if (style == null) {
            style = fontStyle(id + SCALE_SUFFIXES[slot]);
            TEXT_STYLES[index][slot] = style;
        }
        return style;
    }

    /** {@code Component.literal}, in the configured font. Legacy § codes in the text still apply. */
    public static MutableComponent text(String text) {
        return Component.literal(text).withStyle(style());
    }

    /**
     * One and a half times the size of {@link #text}, for a screen's title, or null in vanilla's font.
     *
     * Its own font definitions ({@code <id>_title_s<n>.json}, the same faces 1.5 times larger) rather
     * than the normal text drawn under a scaled pose: a TrueType glyph is only crisp at the size it
     * was rasterised for, and scaling one up blurred the main menu's name. Vanilla's pixel font has
     * no such definition; callers draw the plain text when this returns null.
     */
    public static MutableComponent title(String text) {
        int index = selectedIndex();
        String id = FONT_IDS[index];
        if (id == null) return null;
        int slot = scaleSlot();
        Style style = TITLE_STYLES[index][slot];
        if (style == null) {
            style = fontStyle(id + "_title" + SCALE_SUFFIXES[slot]);
            TITLE_STYLES[index][slot] = style;
        }
        return Component.literal(text).withStyle(style);
    }

    /**
     * Width of the text in the configured font, for centring and caret placement.
     *
     * Measured as a bare run of text in the font's style rather than through {@link #text}: the
     * width comes out the same, since both visit one (style, text) pair, without building a
     * component that is thrown away straight after. This runs many times a frame, and once per
     * character in the loops that trim a label to fit.
     */
    public static int width(Font font, String text) {
        return font.width(FormattedText.of(text, style()));
    }

    /** Word-wraps the text in the configured font; the lines keep the font in their styles. */
    public static List<FormattedText> splitLines(Font font, String text, int maxWidth) {
        return font.getSplitter().splitLines(text, maxWidth, style());
    }

    /**
     * Whether a glyph page belongs to one of these TrueType fonts and should be sampled linearly.
     *
     * Pages are labelled {@code <font id>/<page number>} - {@code alpaka:inter_s2/0}. Every font in
     * this namespace is one of ours, the menus' line icons included.
     */
    public static boolean usesSmoothSampling(String textureLabel) {
        return textureLabel != null && textureLabel.startsWith("alpaka:");
    }
}
