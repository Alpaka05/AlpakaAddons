package net.alpaka.addons.features.viewmodel;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingTrailParticlesTest {

    private static final long MS = 1_000_000L;
    private static final long LIFE = 400 * MS;
    private static final float TEXEL = 0.05f;

    private final SwingTrailParticles trail = new SwingTrailParticles(new Random(7));
    private final float[] squares = new float[SwingTrailParticles.MAX_PARTICLES * SwingTrailParticles.PARTICLE_FLOATS];
    private long now = 1_000_000_000L;

    /** One frame, 10 ms after the last: the tip at x, the grip end one unit below it. */
    private void frame(float progress, float tipX) {
        frame(progress, tipX, 1.0f);
    }

    private void frame(float progress, float tipX, float amount) {
        now += 10 * MS;
        trail.update(now, progress, new float[]{tipX, 0, 0, tipX, -1, 0}, TEXEL, LIFE, amount);
    }

    @Test
    void aRestingItemShedsNothing() {
        for (int i = 0; i < 20; i++) {
            frame(0.0f, 0.0f);
        }
        assertEquals(0, trail.count());
    }

    @Test
    void aStrikeShedsParticlesAlongTheWayOfTheTip() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        assertEquals(1.0f / TEXEL * SwingTrailParticles.PER_TEXEL, trail.count(), 1.0f);
        int floats = trail.build(now, 1.0f, squares);
        assertEquals(trail.count() * SwingTrailParticles.PARTICLE_FLOATS, floats);
        for (int i = 0; i < floats; i += SwingTrailParticles.VERTEX_FLOATS) {
            // Between where the item rested and where it is, on its outer part, give or take what
            // the particles have drifted and their own size.
            assertTrue(squares[i] > -0.2f && squares[i] < 1.2f, "x=" + squares[i]);
            assertTrue(squares[i + 1] > -0.6f && squares[i + 1] < 0.2f, "y=" + squares[i + 1]);
            assertTrue(squares[i + 3] > 0.0f && squares[i + 3] <= 1.0f);
        }
    }

    @Test
    void theNumberDoesNotDependOnTheFrameRate() {
        frame(0.0f, 0.0f);
        for (int i = 1; i <= 10; i++) {
            frame(i * 0.02f, i * 0.1f);
        }
        assertEquals(1.0f / TEXEL * SwingTrailParticles.PER_TEXEL, trail.count(), 1.0f);
    }

    @Test
    void theAmountScalesTheNumber() {
        frame(0.0f, 0.0f, 2.0f);
        frame(0.1f, 0.5f, 2.0f);
        frame(0.2f, 1.0f, 2.0f);
        assertEquals(1.0f / TEXEL * SwingTrailParticles.PER_TEXEL * 2.0f, trail.count(), 1.0f);
    }

    @Test
    void theWayBackShedsNothing() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        int shed = trail.count();
        frame(0.4f, 0.7f);
        frame(0.6f, 0.4f);
        frame(0.8f, 0.1f);
        assertEquals(shed, trail.count());
    }

    @Test
    void particlesAreGoneAfterTheirLife() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        frame(0.5f, 0.5f);
        // Each lives between half the length and all of it.
        for (int i = 0; i < 18; i++) {
            frame(0.0f, 0.0f);
        }
        assertTrue(trail.count() > 0);
        for (int i = 0; i < 25; i++) {
            frame(0.0f, 0.0f);
        }
        assertEquals(0, trail.count());
    }

    @Test
    void aSwingChainedOntoAnotherShedsAgain() {
        frame(0.0f, 0.0f);
        frame(0.3f, 1.0f);
        frame(0.9f, 0.1f);
        int first = trail.count();
        // Progress jumps back: the next swing began without a frame at rest in between.
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        assertTrue(trail.count() > first + 20, "the second swing shed " + (trail.count() - first));
    }

    @Test
    void aSwingLostTrackOfShedsNothingUntilTheNext() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        int shed = trail.count();
        // Another item mid-swing: its tip is elsewhere and moves on with the rest of the swing.
        trail.interrupt();
        frame(0.4f, 3.0f);
        frame(0.6f, 2.0f);
        frame(0.8f, 1.0f);
        assertEquals(shed, trail.count());
        // The next swing sheds again, chained on or after a rest.
        frame(0.1f, 0.5f);
        frame(0.2f, 1.5f);
        assertTrue(trail.count() > shed + 20, "the next swing shed " + (trail.count() - shed));
    }

    @Test
    void aSwingFirstSeenPartWayShedsNothing() {
        frame(0.5f, 2.0f);
        frame(0.7f, 1.0f);
        frame(0.9f, 0.2f);
        assertEquals(0, trail.count());
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        assertTrue(trail.count() > 20);
    }

    @Test
    void aLongStrikeStopsAtTheMostParticles() {
        frame(0.0f, 0.0f);
        for (int i = 1; i <= 40; i++) {
            frame(i * 0.01f, i * 2.0f);
        }
        assertEquals(SwingTrailParticles.MAX_PARTICLES, trail.count());
        assertEquals(SwingTrailParticles.MAX_PARTICLES * SwingTrailParticles.PARTICLE_FLOATS, trail.build(now, 1.0f, squares));
    }

    @Test
    void aLongGapStartsOver() {
        frame(0.0f, 0.0f);
        frame(0.1f, 0.5f);
        frame(0.2f, 1.0f);
        now += 400 * MS;
        frame(0.3f, 9.0f);
        // Nothing left from before the gap, and nothing shed across it.
        assertEquals(0, trail.count());
    }
}
