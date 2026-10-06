import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Turns a screenshot into the main menu's background image.
 *
 * Three steps: the top rows are cut off, where the HUD's FPS and ping line sat; the picture is
 * scaled to [WIDTH] wide, which keeps the jar small and is still sharp on a 1080p screen; and it is
 * blurred a little, as a camera's depth of field would - the avatar stands in front of it in full
 * focus, and the soft background is most of what makes it look placed in the scene rather than
 * pasted on.
 *
 * Run it from the repository root, no build needed:
 *
 * <pre>
 *   java tools/PrepMenuBackground.java ~/Downloads/screenshot.png [rows to cut from the top]
 * </pre>
 */
public class PrepMenuBackground {

    static final String OUT = "src/main/resources/assets/alpaka/textures/gui/menu_background.png";
    static final int WIDTH = 1920;
    static final int DEFAULT_CROP_TOP = 48;
    /** The depth of field, as a Gaussian's sigma in output pixels. */
    static final float BLUR_SIGMA = 1.6f;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("usage: PrepMenuBackground <screenshot.png> [cropTop]");
        int cropTop = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_CROP_TOP;

        BufferedImage source = ImageIO.read(new File(args[0]));
        BufferedImage cropped = source.getSubimage(0, cropTop, source.getWidth(), source.getHeight() - cropTop);

        int height = Math.round(cropped.getHeight() * (WIDTH / (float) cropped.getWidth()));
        BufferedImage scaled = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cropped, 0, 0, WIDTH, height, null);
        g.dispose();

        BufferedImage blurred = blur(blur(scaled, true), false);
        File out = new File(OUT);
        out.getParentFile().mkdirs();
        ImageIO.write(blurred, "png", out);
        System.out.println(WIDTH + "x" + height + " -> " + OUT + " (" + out.length() / 1024 + " KiB)");
    }

    /** One pass of a separable Gaussian, horizontal or vertical, with the edges clamped. */
    static BufferedImage blur(BufferedImage image, boolean horizontal) {
        int radius = (int) Math.ceil(BLUR_SIGMA * 3);
        float[] weights = new float[radius * 2 + 1];
        float sum = 0;
        for (int i = -radius; i <= radius; i++) {
            weights[i + radius] = (float) Math.exp(-(i * i) / (2 * BLUR_SIGMA * BLUR_SIGMA));
            sum += weights[i + radius];
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;

        // ConvolveOp leaves the borders unprocessed, so the image is padded by clamping first.
        int w = image.getWidth(), h = image.getHeight();
        int padX = horizontal ? radius : 0, padY = horizontal ? 0 : radius;
        BufferedImage padded = new BufferedImage(w + padX * 2, h + padY * 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < padded.getHeight(); y++) {
            for (int x = 0; x < padded.getWidth(); x++) {
                int sx = Math.min(w - 1, Math.max(0, x - padX));
                int sy = Math.min(h - 1, Math.max(0, y - padY));
                padded.setRGB(x, y, image.getRGB(sx, sy));
            }
        }
        Kernel kernel = horizontal ? new Kernel(weights.length, 1, weights) : new Kernel(1, weights.length, weights);
        BufferedImage result = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(padded, null);
        return result.getSubimage(padX, padY, w, h);
    }
}
