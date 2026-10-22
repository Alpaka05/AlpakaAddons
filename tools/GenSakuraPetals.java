import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Writes the cherry petals of the Inventory Particles: pixel art in Minecraft's own style, hard
 * edges and no anti-aliasing, in the pinks of vanilla's cherry leaf particles. Vanilla's petals are
 * three pixels wide, too small to show a shape; these are up to eight tall, enough for the sakura
 * petal: an oval body narrowing to a long base, with the little notch in its tip. Light comes from
 * the top left, as on Minecraft's block and item textures.
 *
 * Each map below is one petal, tip at the top. Run it from the repository root, no build needed:
 *
 * <pre>
 *   java tools/GenSakuraPetals.java
 * </pre>
 */
public class GenSakuraPetals {

    static final String OUT = "src/main/resources/assets/alpaka/textures/particle/";

    /** Vanilla's cherry particle pinks, plus a highlight and a deep base. */
    static final Map<Character, Integer> PALETTE = Map.of(
            'H', 0xFDDDEE,
            'L', 0xF7B9DC,
            'M', 0xEFA7CD,
            'N', 0xE899C3,
            'D', 0xE88BBC,
            'E', 0xDA6BA6,
            'B', 0xC9558F);

    /** Tip at the top; each is centered on an 8 x 8 texture. */
    static final String[][] PETALS = {
            {
                    "..E.E..",
                    ".EHLNE.",
                    "EHLLLME",
                    "EHLLMMD",
                    "ELLLMMD",
                    ".ELMMD.",
                    "..EDB..",
                    "...B...",
            },
            {
                    "..E.E..",
                    ".EHNLE.",
                    ".EHLMD.",
                    "EHLLMD.",
                    "ELLMMD.",
                    ".ELMD..",
                    "..EB...",
                    "..B....",
            },
            {
                    ".E.E.",
                    "EHLNE",
                    "ELLMD",
                    "ELMMD",
                    ".EMD.",
                    "..B..",
            },
            {
                    "..E.E..",
                    ".ENLHE.",
                    "EMLLLHE",
                    "DMMLLHE",
                    "DMMLLLE",
                    ".DMMLE.",
                    "..BDE..",
                    "...B...",
            },
            {
                    "...E.E..",
                    "..EHLNE.",
                    ".EHLLMD.",
                    ".ELLMMD.",
                    ".ELMMD..",
                    "..EMD...",
                    "..DB....",
                    ".B......",
            },
    };

    static final int SIZE = 8;

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        for (int p = 0; p < PETALS.length; p++) {
            String[] rows = PETALS[p];
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            int left = (SIZE - rows[0].length()) / 2;
            int top = (SIZE - rows.length) / 2;
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    Integer rgb = PALETTE.get(rows[y].charAt(x));
                    if (rgb != null) image.setRGB(left + x, top + y, 0xFF000000 | rgb);
                }
            }
            ImageIO.write(image, "png", new File(OUT + "sakura_" + p + ".png"));
        }
        System.out.println("Wrote " + PETALS.length + " petals to " + OUT);
    }
}
