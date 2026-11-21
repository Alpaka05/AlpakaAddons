package net.alpaka.addons.features.viewmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingTrailPathTest {

    private static final long MS = 1_000_000L;
    private static final long LENGTH = 200 * MS;
    private static final float WIDTH = 0.5f;

    private final SwingTrailPath path = new SwingTrailPath();
    private float[] ribbon = new float[64];
    private final int[] floats = new int[1];
    private long now = 1_000_000_000L;

    /** One frame, 10 ms after the last: the tip at x, the grip end one unit below it. */
    private void frame(float progress, float tipX) {
        now += 10 * MS;
        path.update(now, progress, new float[]{tipX, 0, 0, tipX, -1, 0}, LENGTH);
    }

    private int build() {
        ribbon = path.build(now, LENGTH, WIDTH, ribbon, floats);
        return floats[0];
    }

    private float maxX() {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < floats[0]; i += SwingTrailPath.VERTEX_FLOATS) {
            max = Math.max(max, ribbon[i]);
        }
        return max;
    }

    @Test
    void aRestingItemLeavesNoTrail() {
        for (int i = 0; i < 20; i++) {
            frame(0.0f, 0.0f);
        }
        assertEquals(0, build());
    }

    @Test
    void theTrailLeadsWithTheItemWhileItStrikes() {
        frame(0.0f, 0.0f);
        frame(0.1f, 1.0f);
        frame(0.2f, 2.0f);
        assertTrue(build() > 0);
        // The leading edge lies on the item: at its tip, opaque, and WIDTH of the way to the grip end.
        boolean atTip = false;
        boolean inner = false;
        for (int i = 0; i < floats[0]; i += SwingTrailPath.VERTEX_FLOATS) {
            if (Math.abs(ribbon[i] - 2.0f) < 1.0e-4f) {
                if (Math.abs(ribbon[i + 1]) < 1.0e-4f && ribbon[i + 3] > 0.99f) atTip = true;
                if (Math.abs(ribbon[i + 1] + WIDTH) < 1.0e-4f && ribbon[i + 3] == 0.0f) inner = true;
            }
        }
        assertTrue(atTip, "no opaque vertex at the tip");
        assertTrue(inner, "no faded vertex down the item");
        // It starts as a point where the item rested.
        for (int i = 0; i < floats[0]; i += SwingTrailPath.VERTEX_FLOATS) {
            if (Math.abs(ribbon[i]) < 1.0e-4f) {
                assertEquals(0.0f, ribbon[i + 1], 1.0e-4f);
            }
        }
    }

    @Test
    void theWayBackLeavesNoTrail() {
        frame(0.0f, 0.0f);
        frame(0.1f, 1.0f);
        frame(0.2f, 2.0f);
        frame(0.3f, 3.0f);
        frame(0.5f, 2.0f);
        frame(0.7f, 1.0f);
        assertTrue(build() > 0);
        assertEquals(3.0f, maxX(), 0.05f);
        // The stroke was left behind at its furthest point and has closed to a point there.
        for (int i = 0; i < floats[0]; i += SwingTrailPath.VERTEX_FLOATS) {
            if (ribbon[i] > 2.999f) {
                assertEquals(0.0f, ribbon[i + 1], 1.0e-4f);
            }
        }
    }

    @Test
    void theTrailFadesOutAfterItsLength() {
        frame(0.0f, 0.0f);
        frame(0.1f, 1.0f);
        frame(0.2f, 2.0f);
        frame(0.5f, 1.0f);
        for (int i = 0; i < 25; i++) {
            frame(0.0f, 0.0f);
        }
        assertEquals(0, build());
        assertTrue(path.isEmpty());
    }

    @Test
    void aSwingChainedOntoAnotherStartsItsOwnStroke() {
        frame(0.0f, 0.0f);
        frame(0.3f, 3.0f);
        frame(0.9f, 0.2f);
        // Progress jumps back: the next swing began without a frame at rest in between.
        frame(0.1f, 1.0f);
        frame(0.2f, 2.0f);
        assertTrue(build() > 0);
        boolean leadsAgain = false;
        for (int i = 0; i < floats[0]; i += SwingTrailPath.VERTEX_FLOATS) {
            if (Math.abs(ribbon[i] - 2.0f) < 1.0e-4f && ribbon[i + 3] > 0.99f) leadsAgain = true;
        }
        assertTrue(leadsAgain, "the second swing has no trail on the item");
    }

    @Test
    void aLongGapStartsOver() {
        frame(0.0f, 0.0f);
        frame(0.1f, 1.0f);
        frame(0.2f, 2.0f);
        now += 400 * MS;
        frame(0.3f, 9.0f);
        // No ribbon across the gap, from where the item was to where it turned up.
        build();
        for (int i = 0; i < floats[0]; i += SwingTrailPath.VERTEX_FLOATS) {
            assertTrue(ribbon[i] > 8.99f, "ribbon reaches back across the gap: x=" + ribbon[i]);
        }
    }
}
