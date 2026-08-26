import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the escape menu's button icons and writes them out as a sprite sheet.
 *
 * The icons are the source of truth here, not the PNG: each one is a plain text matrix below, so
 * changing a pixel means editing a character rather than opening an image editor. Rerun this after
 * any edit and commit the regenerated PNG alongside it.
 *
 * Run it from the repository root, no build needed:
 *
 * <pre>
 *   java tools/GenPauseIcons.java                        # rewrites the sprite sheet in place
 *   java tools/GenPauseIcons.java "" preview.png         # ...and a magnified preview to look at
 *
 * The first argument is the sheet's destination and the second an optional preview; an empty first
 * argument keeps the default location.
 * </pre>
 *
 * The sheet is one row of {@link #CELL}x{@link #CELL} cells. Cell order is what maps them onto
 * codepoints: the first becomes U+E000, the second U+E001 and so on, matching the {@code chars}
 * string in {@code assets/alpaka/font/pause_icons.json} and the ICON_* constants in
 * {@code CustomPauseScreen}. Adding an icon means appending a matrix here, extending that string,
 * and adding the constant - in that same order.
 *
 * Pixels are pure white on transparent on purpose. Minecraft tints font glyphs with the colour the
 * text is drawn in, so white is what lets the icons follow the label's hover and accent colours;
 * a coloured pixel would be multiplied by that colour and come out muddy.
 */
public class GenPauseIcons {

    /** Edge length of one glyph, in pixels. Must match "height" in pause_icons.json. */
    static final int CELL = 10;

    static final String DEFAULT_SHEET = "src/main/resources/assets/alpaka/textures/font/pause_icons.png";

    // '#' = opaque white, '.' = transparent.

    /** Resume Game. */
    static final String[] PLAY = {
        "..........",
        ".##.......",
        ".####.....",
        ".######...",
        ".########.",
        ".########.",
        ".######...",
        ".####.....",
        ".##.......",
        ".........."};

    /**
     * Alpaka Config. A cog: a toothed ring rather than a filled disc.
     *
     * Hollow on purpose. A solid cog at this size carries half again as many lit pixels as this one
     * and reads as a heavy blob next to the text; leaving the centre open, and a pixel of air around
     * the outside, is what lets the teeth register as teeth.
     */
    static final String[] GEAR = {
        "..........",
        "...#..#...",
        "..######..",
        ".###..###.",
        ".##....##.",
        ".##....##.",
        ".###..###.",
        "..######..",
        "...#..#...",
        ".........."};

    /** Mods. A crate with a lid seam - a strap through the middle read as a window instead. */
    static final String[] BOX = {
        "..........",
        "##########",
        "#........#",
        "##########",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "##########",
        ".........."};

    /** Minecraft Options. Sliders rather than a wrench, so it reads apart from the config cog. */
    static final String[] SLIDERS = {
        "......##..",
        "##########",
        "......##..",
        "..##......",
        "##########",
        "..##......",
        ".....##...",
        "##########",
        ".....##...",
        ".........."};

    /** Skyblock Wiki. A framed page with text lines. */
    static final String[] BOOK = {
        "..........",
        ".########.",
        ".#......#.",
        ".#.####.#.",
        ".#......#.",
        ".#.####.#.",
        ".#......#.",
        ".#.###..#.",
        ".########.",
        ".........."};

    /** Disconnect / Save & Quit. */
    static final String[] DOOR = {
        "..######..",
        "..#....#..",
        "..#....#..",
        "..#....#..",
        "..#..#.#..",
        "..#....#..",
        "..#....#..",
        "..#....#..",
        "..######..",
        ".........."};

    /** Order matters: this is what fixes each icon's codepoint. */
    static final String[][] ICONS = {PLAY, GEAR, BOX, SLIDERS, BOOK, DOOR};
    static final String[] NAMES = {"play", "gear", "box", "sliders", "book", "door"};

    public static void main(String[] args) throws Exception {
        String sheetPath = args.length > 0 && !args[0].isEmpty() ? args[0] : DEFAULT_SHEET;

        BufferedImage sheet = new BufferedImage(CELL * ICONS.length, CELL, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < ICONS.length; i++) {
            String[] art = ICONS[i];
            if (art.length != CELL) {
                throw new IllegalStateException(NAMES[i] + " has " + art.length + " rows, expected " + CELL);
            }
            for (int y = 0; y < CELL; y++) {
                if (art[y].length() != CELL) {
                    throw new IllegalStateException(NAMES[i] + " row " + y + " is " + art[y].length() + " wide, expected " + CELL);
                }
                for (int x = 0; x < CELL; x++) {
                    sheet.setRGB(i * CELL + x, y, art[y].charAt(x) == '#' ? 0xFFFFFFFF : 0x00000000);
                }
            }
        }

        File sheetFile = new File(sheetPath);
        if (sheetFile.getParentFile() != null) sheetFile.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", sheetFile);
        System.out.println("sprite sheet " + sheet.getWidth() + "x" + sheet.getHeight() + " -> " + sheetPath);
        System.out.println("codepoints U+E000.." + String.format("U+E%03X", ICONS.length - 1));

        if (args.length > 1) {
            writePreview(new File(args[1]));
            System.out.println("preview -> " + args[1]);
        }
    }

    /**
     * A magnified sheet on the menu's own background, with each icon also drawn at 1:1 and 3:1.
     *
     * Small pixel art is impossible to judge at actual size, and a stray pixel is invisible until
     * it is a block on screen - the cog this alpaca replaced took three passes to stop looking
     * like a blob.
     */
    private static void writePreview(File out) throws Exception {
        int zoom = 14, pad = 10, labelHeight = 16;
        int w = ICONS.length * (CELL * zoom + pad) + pad;
        int h = CELL * zoom + pad * 2 + labelHeight + CELL * 4 + pad;

        BufferedImage prev = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prev.createGraphics();
        g.setColor(new Color(0x1E1E1E));
        g.fillRect(0, 0, w, h);

        for (int i = 0; i < ICONS.length; i++) {
            int ox = pad + i * (CELL * zoom + pad);
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    boolean on = ICONS[i][y].charAt(x) == '#';
                    g.setColor(on ? new Color(0xE5B849) : new Color(0x2A2A2A));
                    g.fillRect(ox + x * zoom, pad + y * zoom, zoom - 1, zoom - 1);
                }
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(NAMES[i], ox, pad + CELL * zoom + 13);

            for (int scale : new int[]{1, 3}) {
                int oy = pad + CELL * zoom + labelHeight + (scale == 1 ? 0 : CELL + 6);
                for (int y = 0; y < CELL; y++) {
                    for (int x = 0; x < CELL; x++) {
                        if (ICONS[i][y].charAt(x) == '#') {
                            g.setColor(Color.WHITE);
                            g.fillRect(ox + x * scale, oy + y * scale, scale, scale);
                        }
                    }
                }
            }
        }
        g.dispose();
        ImageIO.write(prev, "png", out);
    }
}
