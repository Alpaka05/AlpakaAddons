package net.alpaka.addons.features.viewmodel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SwingTrailTipTest {

    private static final float PIXEL = 1.0f / 16.0f;

    /** Corners of a model under construction, with whether each belongs to a rim face. */
    private static final class Model {
        final List<float[]> corners = new ArrayList<>();
        final List<Boolean> rim = new ArrayList<>();

        void corner(float x, float y, float z, boolean rimFace) {
            corners.add(new float[]{x, y, z});
            rim.add(rimFace);
        }

        /** The front and back faces every extruded sprite has, whatever is drawn on it. */
        Model spriteFaces() {
            for (float z : new float[]{7.5f * PIXEL, 8.5f * PIXEL}) {
                corner(0, 0, z, false);
                corner(1, 0, z, false);
                corner(1, 1, z, false);
                corner(0, 1, z, false);
            }
            return this;
        }

        /** The rim around one opaque pixel, counted from the bottom left of the texture. */
        Model pixel(int x, int y) {
            for (float z : new float[]{7.5f * PIXEL, 8.5f * PIXEL}) {
                corner(x * PIXEL, y * PIXEL, z, true);
                corner((x + 1) * PIXEL, y * PIXEL, z, true);
                corner((x + 1) * PIXEL, (y + 1) * PIXEL, z, true);
                corner(x * PIXEL, (y + 1) * PIXEL, z, true);
            }
            return this;
        }

        SwingTrailTip locate() {
            float[] flat = new float[corners.size() * 3];
            boolean[] rims = new boolean[corners.size()];
            for (int i = 0; i < corners.size(); i++) {
                System.arraycopy(corners.get(i), 0, flat, i * 3, 3);
                rims[i] = rim.get(i);
            }
            return SwingTrailTip.locate(flat, rims, corners.size());
        }
    }

    @Test
    void aSwordsTipIsItsOutermostPixelNotTheTextureCorner() {
        Model sword = new Model().spriteFaces();
        for (int i = 2; i <= 13; i++) {
            sword.pixel(i, i);
        }
        SwingTrailTip tip = sword.locate();
        assertNotNull(tip);
        assertEquals(14 * PIXEL, tip.tipX, 1.0e-4f);
        assertEquals(14 * PIXEL, tip.tipY, 1.0e-4f);
        assertEquals(0.5f, tip.tipZ, 1.0e-4f);
        assertEquals(2 * PIXEL, tip.gripX, 1.0e-4f);
        assertEquals(2 * PIXEL, tip.gripY, 1.0e-4f);
        assertEquals(0.5f, tip.gripZ, 1.0e-4f);
    }

    @Test
    void aTipTwoPixelsWideSitsBetweenThem() {
        // A handle up the diagonal, ending in two pixels side by side across it.
        Model tool = new Model().spriteFaces();
        for (int i = 2; i <= 11; i++) {
            tool.pixel(i, i);
        }
        tool.pixel(11, 12).pixel(12, 11);
        SwingTrailTip tip = tool.locate();
        assertNotNull(tip);
        assertEquals(12.5f * PIXEL, tip.tipX, 1.0e-4f);
        assertEquals(12.5f * PIXEL, tip.tipY, 1.0e-4f);
    }

    @Test
    void aModelOfBoxesUsesAllItsCorners() {
        // A block: no rim, corners well outside the sprite's slab.
        Model block = new Model();
        for (float z : new float[]{0.0f, 1.0f}) {
            block.corner(0, 0, z, false);
            block.corner(1, 0, z, false);
            block.corner(1, 1, z, false);
            block.corner(0, 1, z, false);
        }
        // Its side faces are "rim" by direction, which must not turn it into a sprite.
        block.corner(1, 0, 0, true);
        block.corner(1, 1, 1, true);
        SwingTrailTip tip = block.locate();
        assertNotNull(tip);
        assertEquals(1.0f, tip.tipX, 1.0e-4f);
        assertEquals(1.0f, tip.tipY, 1.0e-4f);
        assertEquals(0.5f, tip.tipZ, 0.2f);
        assertEquals(0.0f, tip.gripX, 1.0e-4f);
        assertEquals(0.0f, tip.gripY, 1.0e-4f);
    }

    @Test
    void aModelWithoutExtentHasNoTip() {
        assertNull(SwingTrailTip.locate(new float[0], new boolean[0], 0));
        Model dot = new Model();
        dot.corner(0.5f, 0.5f, 0.5f, false);
        dot.corner(0.5f, 0.5f, 0.5f, false);
        assertNull(dot.locate());
    }
}
