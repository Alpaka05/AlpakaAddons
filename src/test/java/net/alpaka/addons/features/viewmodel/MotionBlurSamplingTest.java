package net.alpaka.addons.features.viewmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionBlurSamplingTest {

    @Test
    void noBlurWhenTheItemBarelyMoves() {
        assertEquals(0, MotionBlurSampling.sampleCount(0.0f, 64));
        assertEquals(0, MotionBlurSampling.sampleCount(1.0f, 64));
        assertEquals(0, MotionBlurSampling.sampleCount(Float.NaN, 64));
    }

    @Test
    void sampleCountFollowsTheDistanceUpToTheCap() {
        assertEquals(MotionBlurSampling.MIN_SAMPLES, MotionBlurSampling.sampleCount(2.0f, 64));
        assertEquals(25, MotionBlurSampling.sampleCount(50.0f, 64));
        assertEquals(64, MotionBlurSampling.sampleCount(5000.0f, 64));
        assertEquals(2, MotionBlurSampling.sampleCount(5000.0f, 2));
    }

    /**
     * As many fresh samples as overlap on one spot must reach the configured opacity together, as
     * long as each of them stays under the cap for a single sample.
     */
    @Test
    void overlappingFreshSamplesReachTheConfiguredOpacity() {
        for (float overlap : new float[]{3.0f, 5.0f, 12.0f, 40.0f}) {
            for (float opacity : new float[]{0.1f, 0.35f, 0.6f}) {
                float alpha = MotionBlurSampling.alpha(0.0f, opacity, overlap);
                double reached = 1.0 - Math.pow(1.0 - alpha, overlap);
                assertEquals(opacity, reached, 1.0e-4, overlap + " overlapping at " + opacity);
            }
        }
    }

    /** Spread far apart, a sample must stay faint enough not to show as a copy of the item. */
    @Test
    void aSingleSampleNeverShowsAsACopy() {
        assertTrue(MotionBlurSampling.alpha(0.0f, 1.0f, 1.0f) <= MotionBlurSampling.MAX_SAMPLE_ALPHA);
        assertTrue(MotionBlurSampling.alpha(0.0f, 0.6f, 20.0f) < 0.06f);
        // Two samples would each need 37 % to reach 60 % together; they get the cap instead.
        assertEquals(MotionBlurSampling.MAX_SAMPLE_ALPHA, MotionBlurSampling.alpha(0.0f, 0.6f, 2.0f));
    }

    @Test
    void samplesFadeOutTowardsTheEndOfTheTrail() {
        float previous = Float.MAX_VALUE;
        for (int i = 0; i <= 10; i++) {
            float alpha = MotionBlurSampling.alpha(i / 10.0f, 0.6f, 6.0f);
            assertTrue(alpha <= previous, "not fading at " + i);
            previous = alpha;
        }
        assertEquals(0.0f, MotionBlurSampling.alpha(1.0f, 0.6f, 6.0f), 1.0e-6);
        assertEquals(0.0f, MotionBlurSampling.alpha(0.0f, 0.0f, 6.0f));
    }

    @Test
    void overlapFollowsItemSizeAndSpacing() {
        // A texture 320 px wide is 20 px per texture pixel; a 2.5-pixel blade 50 px wide.
        assertEquals(25.0f, MotionBlurSampling.overlap(320.0f, 2.0f), 1.0e-4);
        assertEquals(1.0f, MotionBlurSampling.overlap(16.0f, 100.0f));
        assertEquals(1.0f, MotionBlurSampling.overlap(320.0f, 0.0f));
    }
}
