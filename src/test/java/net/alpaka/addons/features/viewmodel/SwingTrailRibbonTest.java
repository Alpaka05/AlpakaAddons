package net.alpaka.addons.features.viewmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingTrailRibbonTest {

    private static final long MS = 1_000_000L;
    private static final long LIFE = 200 * MS;
    private static final float TEXEL = 0.05f;
    private static final float EPS = 1.0e-4f;

    private final SwingTrailRibbon trail = new SwingTrailRibbon();
    private final float[] quads = new float[SwingTrailRibbon.MAX_FLOATS];
    private int floats;
    private long now = 1_000_000_000L;

    /** How far in front of the viewer the item is. */
    private static final float AWAY = -5.0f;

    /** One frame, 10 ms after the last: the tip at x, the grip end one unit below it. */
    private void frame(float progress, float tipX) {
        now += 10 * MS;
        trail.update(now, progress, new float[]{tipX, 0, AWAY, tipX, -1, AWAY}, TEXEL, LIFE);
    }

    private int build() {
        return build(1.0f);
    }

    private int build(float width) {
        floats = trail.build(now, TEXEL, LIFE, width, quads);
        return floats;
    }

    private float minX() {
        float min = Float.POSITIVE_INFINITY;
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            min = Math.min(min, quads[i]);
        }
        return min;
    }

    private float maxX() {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            max = Math.max(max, quads[i]);
        }
        return max;
    }

    /** The lowest any vertex at this x reaches: how far towards the grip end the sweep goes there. */
    private float depthAt(float x) {
        float depth = 0.0f;
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            if (Math.abs(quads[i] - x) < EPS) {
                depth = Math.min(depth, quads[i + 1]);
            }
        }
        return depth;
    }

    /** A strike in a few frames from 0 to the given x. */
    private void strikeTo(float x) {
        frame(0.0f, 0.0f);
        frame(0.1f, x / 2.0f);
        frame(0.2f, x);
    }

    @Test
    void aRestingItemDrawsNothing() {
        for (int i = 0; i < 20; i++) {
            frame(0.0f, 0.0f);
        }
        assertTrue(trail.isEmpty());
        assertEquals(0, build());
    }

    @Test
    void theSweepHangsOnTheTipAndIsACrescent() {
        strikeTo(1.0f);
        assertFalse(trail.isEmpty());
        assertTrue(build() > 0);
        assertEquals(0, floats % (4 * SwingTrailRibbon.VERTEX_FLOATS));
        // From where the item rested to where its tip is.
        assertEquals(0.0f, minX(), EPS);
        assertEquals(1.0f, maxX(), EPS);
        // One pixel thick at both ends, a fifth of the item's twenty pixels at the middle.
        assertEquals(-TEXEL, depthAt(1.0f), EPS);
        assertEquals(-TEXEL, depthAt(0.0f), EPS);
        assertEquals(-4 * TEXEL, depthAt(0.5f), EPS);
        // Its outer edge is the way the tip went.
        boolean onTheTip = false;
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            if (Math.abs(quads[i] - 1.0f) < EPS && Math.abs(quads[i + 1]) < EPS) {
                onTheTip = true;
            }
        }
        assertTrue(onTheTip, "no vertex at the tip");
    }

    @Test
    void theSweepIsMadeOfPixelsTheSizeOfTheItems() {
        strikeTo(1.0f);
        build();
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            float column = quads[i] / TEXEL;
            float row = -quads[i + 1] / TEXEL;
            assertEquals(Math.round(column), column, 1.0e-2f, "x=" + quads[i]);
            assertEquals(Math.round(row), row, 1.0e-2f, "y=" + quads[i + 1]);
            assertTrue(row > -1.0e-2f && row < 4.01f, "y=" + quads[i + 1]);
        }
    }

    @Test
    void theSweepFacesTheViewerOnTheSideTheItemIsOn() {
        // The grip end is straight below the tip, whichever way the strike goes.
        strikeTo(1.0f);
        build();
        assertEquals(-4 * TEXEL, depthAt(0.5f), EPS);
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            assertEquals(AWAY, quads[i + 2], EPS);
        }
        trail.clear();
        strikeTo(-1.0f);
        build();
        assertEquals(-4 * TEXEL, depthAt(-0.5f), EPS);
        // An item that leans along its way gets upright pixels all the same.
        trail.clear();
        now += 10 * MS;
        trail.update(now, 0.0f, new float[]{0, 0, AWAY, -0.8f, -0.6f, AWAY}, TEXEL, LIFE);
        now += 10 * MS;
        trail.update(now, 0.1f, new float[]{0.5f, 0, AWAY, -0.3f, -0.6f, AWAY}, TEXEL, LIFE);
        now += 10 * MS;
        trail.update(now, 0.2f, new float[]{1.0f, 0, AWAY, 0.2f, -0.6f, AWAY}, TEXEL, LIFE);
        build();
        assertEquals(0.0f, minX(), EPS);
        assertEquals(1.0f, maxX(), EPS);
        assertEquals(-4 * TEXEL, depthAt(0.5f), EPS);
    }

    @Test
    void theSweepIsSolidWithAShadedRowAlongItsInnerEdge() {
        strikeTo(1.0f);
        build();
        boolean shadedInnerEdge = false;
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            float shade = quads[i + 3];
            assertTrue(shade == 1.0f || shade == SwingTrailRibbon.EDGE_SHADE, "shade=" + shade);
            if (Math.abs(quads[i] - 0.5f) < EPS) {
                // At the middle: the innermost of the four rows is the shaded one, and only that.
                boolean innermost = quads[i + 1] < -3 * TEXEL - EPS;
                boolean outer = quads[i + 1] > -3 * TEXEL + EPS;
                if (innermost) {
                    assertEquals(SwingTrailRibbon.EDGE_SHADE, shade);
                    shadedInnerEdge = true;
                }
                if (outer) {
                    assertEquals(1.0f, shade);
                }
            }
        }
        assertTrue(shadedInnerEdge, "no shaded row at the middle");
    }

    @Test
    void theSweepIsTheSameAtAnyFrameRate() {
        frame(0.0f, 0.0f);
        for (int i = 1; i <= 10; i++) {
            frame(i * 0.02f, i * 0.1f);
        }
        assertTrue(build() > 0);
        assertEquals(0.0f, minX(), EPS);
        assertEquals(1.0f, maxX(), EPS);
        assertEquals(-TEXEL, depthAt(1.0f), EPS);
        assertEquals(-4 * TEXEL, depthAt(0.5f), EPS);
        assertEquals(-TEXEL, depthAt(0.0f), EPS);
    }

    @Test
    void theWidthSettingScalesHowThickItIs() {
        // Long enough that neither is held thin by its length.
        strikeTo(3.0f);
        build(1.0f);
        assertEquals(-4 * TEXEL, depthAt(1.5f), EPS);
        build(2.0f);
        assertEquals(-8 * TEXEL, depthAt(1.5f), EPS);
        build(0.25f);
        assertEquals(-TEXEL, depthAt(1.5f), EPS);
    }

    @Test
    void aShortSweepIsThin() {
        // Ten pixels long: two thick, not the four the item's length would give.
        strikeTo(0.5f);
        assertTrue(build() > 0);
        assertEquals(-2 * TEXEL, depthAt(0.25f), EPS);
    }

    @Test
    void theWayBackDrawsNothingAndTheSweepIsUsedUpFromWhereItBegan() {
        strikeTo(1.0f);
        frame(0.4f, 0.7f);
        frame(0.6f, 0.4f);
        assertTrue(build() > 0);
        // It stays where the strike ended.
        assertEquals(1.0f, maxX(), EPS);
        float begins = minX();
        assertTrue(begins < 0.1f, "begins=" + begins);
        for (int i = 0; i < 8; i++) {
            frame(0.0f, 0.0f);
        }
        assertTrue(build() > 0);
        assertEquals(1.0f, maxX(), EPS);
        assertTrue(minX() > begins + 0.1f, "not used up: " + minX());
        // And thinner with it.
        assertTrue(depthAt(1.0f - (1.0f - minX()) / 2.0f) >= -3 * TEXEL - EPS);
    }

    @Test
    void theSweepIsGoneAfterItsTime() {
        strikeTo(1.0f);
        frame(0.5f, 0.5f);
        for (int i = 0; i < 10; i++) {
            frame(0.0f, 0.0f);
        }
        assertTrue(build() > 0);
        for (int i = 0; i < 12; i++) {
            frame(0.0f, 0.0f);
        }
        assertEquals(0, build());
        assertTrue(trail.isEmpty());
    }

    @Test
    void aLongStrikeIsUsedUpFromItsTail() {
        frame(0.0f, 0.0f);
        for (int i = 1; i <= 30; i++) {
            frame(i * 0.01f, i * 0.1f);
        }
        assertTrue(build() > 0);
        // 200 ms at a tenth of a unit every 10 ms: the last two units of the way.
        assertEquals(1.0f, minX(), 0.02f);
        assertEquals(3.0f, maxX(), EPS);
    }

    @Test
    void aSwingChainedOntoAnotherDrawsItsOwnSweepWhileTheOldOneIsUsedUp() {
        frame(0.0f, 0.0f);
        frame(0.3f, 1.0f);
        frame(0.9f, 0.1f);
        int first = build();
        assertTrue(first > 0);
        // Progress jumps back: the next swing began without a frame at rest in between.
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        assertTrue(build() > first, "the second swing drew nothing of its own");
    }

    @Test
    void aSwingLostTrackOfDrawsNothingUntilTheNext() {
        strikeTo(1.0f);
        // Another item mid-swing: its tip is elsewhere and moves on with the rest of the swing.
        trail.interrupt(now);
        frame(0.4f, 3.0f);
        frame(0.6f, 4.0f);
        build();
        assertEquals(1.0f, maxX(), EPS);
        // The next swing draws again, from where it is first seen.
        frame(0.1f, 5.0f);
        frame(0.2f, 6.0f);
        build();
        assertEquals(6.0f, maxX(), EPS);
    }

    @Test
    void aSwingFirstSeenPartWayDrawsNothing() {
        frame(0.0f, 0.0f);
        // Not drawn for a second, and back in the middle of a swing.
        now += 1000 * MS;
        frame(0.5f, 1.0f);
        frame(0.6f, 2.0f);
        frame(0.7f, 3.0f);
        assertEquals(0, build());
    }

    @Test
    void anItemThatBarelyMovesDrawsNothing() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f * TEXEL);
        frame(0.2f, 1.0f * TEXEL);
        assertEquals(0, build());
    }

    @Test
    void aStrikeOfManyFramesStaysWithinBounds() {
        frame(0.0f, 0.0f);
        for (int i = 1; i <= 300; i++) {
            frame(Math.min(0.99f, i * 0.003f), i * 0.1f);
        }
        assertTrue(build() > 0);
        assertTrue(floats <= SwingTrailRibbon.MAX_FLOATS);
        assertEquals(30.0f, maxX(), EPS);
    }

    @Test
    void aSweepLongerThanItHasPixelsForIsDrawnInLongerOnes() {
        // 400 pixels in 200 ms, three times over.
        for (int swing = 0; swing < 3; swing++) {
            frame(0.0f, 0.0f);
            for (int i = 1; i <= 20; i++) {
                frame(i * 0.04f, i * 1.0f);
            }
        }
        assertTrue(build(2.0f) > 0);
        assertTrue(floats <= SwingTrailRibbon.MAX_FLOATS);
        assertEquals(20.0f, maxX(), 1.0e-3f);
    }
}
