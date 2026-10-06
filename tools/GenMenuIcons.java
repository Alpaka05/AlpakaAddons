import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws the main menu's button icons and writes them out as a sprite sheet for a bitmap font.
 *
 * These are line icons drawn as vectors - the same strokes as the menu design - and rendered
 * anti-aliased at four times the glyph's size. The font is sampled linearly
 * (GuiFont.usesSmoothSampling), so a ring such as the power symbol stays round at every GUI scale
 * instead of breaking into steps.
 *
 * Run it from the repository root, no build needed:
 *
 * <pre>
 *   java tools/GenMenuIcons.java                       # rewrites the sprite sheet in place
 *   java tools/GenMenuIcons.java "" preview.png        # ...and an enlarged preview to look at
 * </pre>
 *
 * Each icon is drawn in a 24-unit box, as an SVG icon would be, and scaled into its cell. Cell order
 * fixes the codepoints: the first is U+E000, matching the {@code chars} string in
 * {@code assets/alpaka/font/menu_icons.json} and the ICON_* constants in MenuStyle.
 *
 * Pixels are white on transparent: Minecraft tints glyphs with the text colour, so white is what
 * lets the icons take the label's colour.
 */
public class GenMenuIcons {

    /** Edge length of one cell in texels: four per GUI pixel of a ten-pixel glyph. */
    static final int CELL = 40;

    /** The icons are drawn in this many units, like a 24-pixel SVG icon. */
    static final double UNITS = 24.0;

    /** Line width in units, as in the design. */
    static final float STROKE = 2.0f;

    static final String DEFAULT_SHEET = "src/main/resources/assets/alpaka/textures/font/menu_icons.png";
    static final String FONT_JSON = "src/main/resources/assets/alpaka/font/menu_icons.json";

    record Icon(String name, List<Shape> strokes, List<Shape> fills) {}

    /** Join Hypixel. Two stacked server units, each with its status light. */
    static Icon server() {
        return new Icon("server",
                List.of(new RoundRectangle2D.Double(3, 4, 18, 7, 4, 4), new RoundRectangle2D.Double(3, 13, 18, 7, 4, 4)),
                List.of(dot(7, 7.5, 1.2), dot(7, 16.5, 1.2)));
    }

    /** Singleplayer. One person: head and shoulders. */
    static Icon person() {
        Path2D shoulders = new Path2D.Double();
        shoulders.moveTo(4, 21);
        shoulders.curveTo(4, 17, 8, 15, 12, 15);
        shoulders.curveTo(16, 15, 20, 17, 20, 21);
        return new Icon("person", List.of(new Ellipse2D.Double(8, 4, 8, 8), shoulders), List.of());
    }

    /** Multiplayer. A person, and a second one half behind them. */
    static Icon people() {
        Path2D front = new Path2D.Double();
        front.moveTo(2.5, 20);
        front.curveTo(2.5, 16.5, 5.5, 14.5, 9, 14.5);
        front.curveTo(12.5, 14.5, 15.5, 16.5, 15.5, 20);
        Path2D back = new Path2D.Double();
        back.moveTo(17, 14.5);
        back.curveTo(19.5, 14.5, 21.5, 16, 21.5, 19);
        return new Icon("people",
                List.of(new Ellipse2D.Double(5.5, 4.5, 7, 7), front, new Ellipse2D.Double(14.5, 6.5, 5, 5), back),
                List.of());
    }

    /** Join Alpha. A flask, for the test server's experiments. */
    static Icon flask() {
        Path2D body = new Path2D.Double();
        body.moveTo(10, 3);
        body.lineTo(10, 9);
        body.lineTo(5, 18);
        body.quadTo(4.2, 21, 6.7, 21);
        body.lineTo(17.3, 21);
        body.quadTo(19.8, 21, 19, 18);
        body.lineTo(14, 9);
        body.lineTo(14, 3);
        return new Icon("flask", List.of(new Line2D.Double(9, 3, 15, 3), body), List.of());
    }

    /** Mods. A puzzle piece: a square with a knob on top and on the right, a notch underneath. */
    static Icon puzzle() {
        Area piece = new Area(new Rectangle2D.Double(5, 7, 12, 12));
        piece.add(new Area(new Ellipse2D.Double(11 - 2.3, 7 - 3.6, 4.6, 4.6)));
        piece.add(new Area(new Ellipse2D.Double(17 - 1.0, 13 - 2.3, 4.6, 4.6)));
        piece.subtract(new Area(new Ellipse2D.Double(11 - 2.3, 19 - 2.3, 4.6, 4.6)));
        return new Icon("puzzle", List.of(piece), List.of());
    }

    /** Options. Two sliders. */
    static Icon sliders() {
        return new Icon("sliders",
                List.of(new Line2D.Double(4, 7, 14, 7), new Line2D.Double(18, 7, 20, 7),
                        new Line2D.Double(4, 17, 8, 17), new Line2D.Double(12, 17, 20, 17),
                        new Ellipse2D.Double(14, 5, 4, 4), new Ellipse2D.Double(8, 15, 4, 4)),
                List.of());
    }

    /** Quit. The power symbol: a ring open at the top with a bar through the gap. */
    static Icon power() {
        return new Icon("power",
                List.of(new Line2D.Double(12, 3, 12, 12), new Arc2D.Double(4, 4.9, 16, 16, 135.5, 269, Arc2D.OPEN)),
                List.of());
    }

    /** The pointer at the end of a menu row. */
    static Icon chevron() {
        Path2D chevron = new Path2D.Double();
        chevron.moveTo(9, 6);
        chevron.lineTo(15, 12);
        chevron.lineTo(9, 18);
        return new Icon("chevron", List.of(chevron), List.of());
    }

    /** Resume Game, in the pause menu. A play triangle with softened corners. */
    static Icon play() {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(7, 4);
        triangle.lineTo(19, 12);
        triangle.lineTo(7, 20);
        triangle.closePath();
        return new Icon("play", List.of(triangle), List.of());
    }

    /** Wiki, in the pause menu. An open book: two pages meeting at the spine. */
    static Icon book() {
        Path2D left = new Path2D.Double();
        left.moveTo(12, 7);
        left.curveTo(10, 5, 6, 4.5, 3, 5);
        left.lineTo(3, 18.5);
        left.curveTo(6, 18, 10, 18.5, 12, 20.5);
        Path2D right = new Path2D.Double();
        right.moveTo(12, 7);
        right.curveTo(14, 5, 18, 4.5, 21, 5);
        right.lineTo(21, 18.5);
        right.curveTo(18, 18, 14, 18.5, 12, 20.5);
        return new Icon("book", List.of(left, right, new Line2D.Double(12, 7, 12, 20.5)), List.of());
    }

    /** Disconnect, in the pause menu. A door frame open on the right, an arrow leaving through it. */
    static Icon door() {
        Path2D frame = new Path2D.Double();
        frame.moveTo(10, 3.5);
        frame.lineTo(6, 3.5);
        frame.quadTo(4, 3.5, 4, 5.5);
        frame.lineTo(4, 18.5);
        frame.quadTo(4, 20.5, 6, 20.5);
        frame.lineTo(10, 20.5);
        Path2D head = new Path2D.Double();
        head.moveTo(16, 7.5);
        head.lineTo(20.5, 12);
        head.lineTo(16, 16.5);
        return new Icon("door", List.of(frame, head, new Line2D.Double(9.5, 12, 20.5, 12)), List.of());
    }

    /** Order matters: this is what fixes each icon's codepoint. */
    static final List<Icon> ICONS = List.of(server(), person(), people(), flask(), puzzle(), sliders(), power(),
            chevron(), play(), book(), door());

    static Shape dot(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, r * 2, r * 2);
    }

    public static void main(String[] args) throws Exception {
        String sheetPath = args.length > 0 && !args[0].isEmpty() ? args[0] : DEFAULT_SHEET;

        // One codepoint per cell, or every glyph after a mismatch lands on the wrong character.
        java.nio.file.Path json = java.nio.file.Path.of(FONT_JSON);
        if (java.nio.file.Files.exists(json)) {
            int listed = java.nio.file.Files.readString(json).split("\\\\u[eE]0", -1).length - 1;
            if (listed != ICONS.size()) {
                throw new IllegalStateException(FONT_JSON + " lists " + listed + " codepoints, but there are "
                        + ICONS.size() + " icons here");
            }
        }

        BufferedImage sheet = new BufferedImage(CELL * ICONS.size(), CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        for (int i = 0; i < ICONS.size(); i++) {
            Icon icon = ICONS.get(i);
            AffineTransform cell = new AffineTransform();
            cell.translate(i * CELL, 0);
            cell.scale(CELL / UNITS, CELL / UNITS);
            AffineTransform saved = g.getTransform();
            g.transform(cell);
            g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Shape stroke : icon.strokes()) g.draw(stroke);
            for (Shape fill : icon.fills()) g.fill(fill);
            g.setTransform(saved);
        }
        g.dispose();

        File sheetFile = new File(sheetPath);
        if (sheetFile.getParentFile() != null) sheetFile.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", sheetFile);
        System.out.println("sprite sheet " + sheet.getWidth() + "x" + sheet.getHeight() + " -> " + sheetPath);
        System.out.println("codepoints U+E000.." + String.format("U+E%03X", ICONS.size() - 1));

        if (args.length > 1) {
            writePreview(sheet, new File(args[1]));
            System.out.println("preview -> " + args[1]);
        }
    }

    /** The sheet enlarged on a dark background, each icon named underneath. */
    private static void writePreview(BufferedImage sheet, File out) throws Exception {
        int zoom = 3, pad = 12, label = 16;
        BufferedImage prev = new BufferedImage(sheet.getWidth() * zoom + pad * (ICONS.size() + 1),
                CELL * zoom + pad * 2 + label, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prev.createGraphics();
        g.setColor(new Color(0x1E1E1E));
        g.fillRect(0, 0, prev.getWidth(), prev.getHeight());
        for (int i = 0; i < ICONS.size(); i++) {
            int ox = pad + i * (CELL * zoom + pad);
            g.drawImage(sheet.getSubimage(i * CELL, 0, CELL, CELL), ox, pad, CELL * zoom, CELL * zoom, null);
            g.setColor(Color.WHITE);
            g.drawString(ICONS.get(i).name(), ox, pad + CELL * zoom + 13);
        }
        g.dispose();
        ImageIO.write(prev, "png", out);
    }
}
