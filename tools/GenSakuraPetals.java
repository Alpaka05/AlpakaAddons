import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the cherry petals of the Inventory Particles: anime-style sakura petals, a rounded petal
 * that narrows to its base, with the little V notch in its tip that makes it read as cherry rather
 * than any other blossom. The base is a deeper pink that fades out to a pale tip, with a darker rim
 * so the petal keeps its outline over a bright background.
 *
 * Every pixel is sampled 8 x 8 times, so the edge gets soft alpha instead of jagged steps. Each
 * variant is a little wider, narrower, deeper notched or bent to one side, so a drift of petals
 * doesn't look stamped.
 *
 * Run it from the repository root, no build needed:
 *
 * <pre>
 *   java tools/GenSakuraPetals.java
 * </pre>
 */
public class GenSakuraPetals {

    static final int SIZE = 16;
    static final int SAMPLES = 8;
    static final String OUT = "src/main/resources/assets/alpaka/textures/particle/";

    /** Width (share of the texture), notch depth (share of the length), sideways bend. */
    static final double[][] VARIANTS = {
            {0.64, 0.12, 0.00},
            {0.56, 0.14, 0.12},
            {0.70, 0.10, -0.08},
            {0.52, 0.13, -0.16},
            {0.60, 0.15, 0.18},
            {0.66, 0.11, 0.06},
    };

    static final int[] BASE = {0xF0, 0x62, 0x9C};
    static final int[] BODY = {0xFF, 0xA6, 0xC9};
    static final int[] TIP = {0xFF, 0xD2, 0xE4};
    static final int[] RIM = {0xF2, 0x70, 0xA8};

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        for (int v = 0; v < VARIANTS.length; v++) {
            ImageIO.write(petal(VARIANTS[v]), "png", new File(OUT + "sakura_" + v + ".png"));
        }
        System.out.println("Wrote " + VARIANTS.length + " petals to " + OUT);
    }

    static BufferedImage petal(double[] variant) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double a = 0, r = 0, g = 0, b = 0;
                for (int sy = 0; sy < SAMPLES; sy++) {
                    for (int sx = 0; sx < SAMPLES; sx++) {
                        // x across the petal from -1 to 1, t along it from base (0) to tip (1).
                        double x = ((px + (sx + 0.5) / SAMPLES) / SIZE) * 2 - 1;
                        double t = 1 - (py + (sy + 0.5) / SAMPLES) / SIZE;
                        t = (t - 0.04) / 0.92;
                        double[] c = sample(x, t, variant);
                        if (c == null) continue;
                        a += 1;
                        r += c[0];
                        g += c[1];
                        b += c[2];
                    }
                }
                if (a == 0) continue;
                int alpha = (int) Math.round(255 * a / (SAMPLES * SAMPLES));
                int rgb = ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8) | (int) Math.round(b / a);
                image.setRGB(px, py, (alpha << 24) | rgb);
            }
        }
        return image;
    }

    /** The petal's color at a point, or null outside it. */
    static double[] sample(double x, double t, double[] variant) {
        if (t < 0 || t > 1) return null;
        double width = variant[0], notch = variant[1], bend = variant[2];
        // Bent: the middle line curves off to one side towards the tip.
        x -= bend * t * t;

        double half = halfWidth(t, width);
        if (Math.abs(x) > half) return null;
        // The notch: a V cut down into the middle of the rounded tip.
        double notchWidth = half * 0.3;
        if (t > 1 - notch && Math.abs(x) < notchWidth * (t - (1 - notch)) / notch) return null;

        // Deep pink at the base, through the body, to a pale tip.
        double[] color = t < 0.45 ? mix(BASE, BODY, t / 0.45) : mix(BODY, TIP, (t - 0.45) / 0.55);
        // A lighter vein up the middle of the lower half.
        if (Math.abs(x) < 0.07 && t > 0.1 && t < 0.6) color = mix(color, TIP, 0.35);
        // The rim, darker so the shape holds over a light backdrop.
        double edge = (half - Math.abs(x)) / Math.max(half, 1e-6);
        if (edge < 0.15) color = mix(color, RIM, 0.4);
        return color;
    }

    /** Half the petal's width at t: narrow at the base, widest past the middle, rounded at the tip. */
    static double halfWidth(double t, double width) {
        double widest = 0.68;
        if (t < widest) return width * Math.sin(Math.PI / 2 * Math.pow(t / widest, 0.85));
        double cap = (t - widest) / (1 - widest);
        return width * Math.sqrt(Math.max(0, 1 - cap * cap));
    }

    static double[] mix(int[] from, int[] to, double share) {
        return new double[] {
                from[0] + (to[0] - from[0]) * share,
                from[1] + (to[1] - from[1]) * share,
                from[2] + (to[2] - from[2]) * share,
        };
    }

    static double[] mix(double[] from, int[] to, double share) {
        return new double[] {
                from[0] + (to[0] - from[0]) * share,
                from[1] + (to[1] - from[1]) * share,
                from[2] + (to[2] - from[2]) * share,
        };
    }
}
