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

    /** One frame, 10 ms after the last: the tip at x, the grip end one unit below it. */
    private void frame(float progress, float tipX) {
        now += 10 * MS;
        trail.update(now, progress, new float[]{tipX, 0, 0, tipX, -1, 0}, TEXEL, LIFE);
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

    /** The lowest any vertex at this x reaches: how far down the item the slash goes there. */
    private float depthAt(float x) {
        float depth = 0.0f;
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            if (Math.abs(quads[i] - x) < EPS) {
                depth = Math.min(depth, quads[i + 1]);
            }
        }
        return depth;
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
    void theSlashHangsOnTheItemAndRunsOutToAPoint() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        assertFalse(trail.isEmpty());
        assertTrue(build() > 0);
        assertEquals(0, floats % (4 * SwingTrailRibbon.VERTEX_FLOATS));
        // From where the item rested to where it is.
        assertEquals(0.0f, minX(), EPS);
        assertEquals(1.0f, maxX(), EPS);
        // At the item: solid at the tip, faded out a good way down the blade.
        boolean solidTip = false;
        boolean fadedInner = false;
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            if (Math.abs(quads[i] - 1.0f) < EPS) {
                if (Math.abs(quads[i + 1]) < EPS && quads[i + 3] > 0.99f) solidTip = true;
                if (Math.abs(quads[i + 1] + SwingTrailRibbon.BLADE_SHARE) < EPS && quads[i + 3] == 0.0f) fadedInner = true;
            }
        }
        assertTrue(solidTip, "no solid vertex at the tip");
        assertTrue(fadedInner, "no faded vertex down the item");
        // A point where the strike began, and never wider than at the item.
        assertEquals(0.0f, depthAt(0.0f), EPS);
        for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
            assertTrue(quads[i + 1] <= EPS && quads[i + 1] >= -SwingTrailRibbon.BLADE_SHARE - EPS, "y=" + quads[i + 1]);
            assertTrue(quads[i + 3] >= 0.0f && quads[i + 3] <= 1.0f);
        }
    }

    @Test
    void theSlashIsTheSameAtAnyFrameRate() {
        frame(0.0f, 0.0f);
        for (int i = 1; i <= 10; i++) {
            frame(i * 0.02f, i * 0.1f);
        }
        assertTrue(build() > 0);
        assertEquals(0.0f, minX(), EPS);
        assertEquals(1.0f, maxX(), EPS);
        assertEquals(-SwingTrailRibbon.BLADE_SHARE, depthAt(1.0f), EPS);
        assertEquals(0.0f, depthAt(0.0f), EPS);
    }

    @Test
    void theWidthSettingScalesHowFarDownTheItemItReaches() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        build(2.0f);
        assertEquals(-2.0f * SwingTrailRibbon.BLADE_SHARE, depthAt(1.0f), EPS);
    }

    @Test
    void theWayBackDrawsNothingAndTheSlashClosesAtTheFront() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        frame(0.4f, 0.7f);
        frame(0.6f, 0.4f);
        assertTrue(build() > 0);
        // It stays where the strike ended, and no longer has the item's width there.
        assertEquals(1.0f, maxX(), EPS);
        assertEquals(0.0f, depthAt(1.0f), EPS);
        assertTrue(depthAt(0.5f) < -0.05f, "the middle is still wide");
    }

    @Test
    void theSlashIsGoneAfterItsTime() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
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
    void aSwingChainedOntoAnotherDrawsItsOwnSlashWhileTheOldOneFades() {
        frame(0.0f, 0.0f);
        frame(0.3f, 1.0f);
        frame(0.9f, 0.1f);
        int first = build();
        assertTrue(first > 0);
        // Progress jumps back: the next swing began without a frame at rest in between.
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        assertTrue(build() > first, "the second swing drew nothing of its own");
        // The new one hangs on the item at full width.
        assertEquals(-SwingTrailRibbon.BLADE_SHARE, depthAt(1.0f), EPS);
    }

    @Test
    void aSwingLostTrackOfDrawsNothingUntilTheNext() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
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
}
