package net.alpaka.addons.features.viewmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionBlurSamplingTest {

    @Test
    void noBlurWhenTheItemBarelyMoves() {
        assertEquals(0, MotionBlurSampling.sampleCount(0.0f, 40));
        assertEquals(0, MotionBlurSampling.sampleCount(1.0f, 40));
        assertEquals(0, MotionBlurSampling.sampleCount(Float.NaN, 40));
    }

    @Test
    void sampleCountFollowsTheDistanceUpToTheCap() {
        assertEquals(MotionBlurSampling.MIN_SAMPLES, MotionBlurSampling.sampleCount(2.0f, 40));
        assertEquals(20, MotionBlurSampling.sampleCount(50.0f, 40));
        assertEquals(40, MotionBlurSampling.sampleCount(5000.0f, 40));
        assertEquals(2, MotionBlurSampling.sampleCount(5000.0f, 2));
    }

    /** A pixel every sample covers must end up at the configured opacity, whatever the count. */
    @Test
    void fullCoverageReachesTheConfiguredOpacity() {
        for (int samples : new int[]{1, 4, 12, 40, 64}) {
            for (float opacity : new float[]{0.1f, 0.35f, 0.6f, 0.9f}) {
                float[] alphas = MotionBlurSampling.sampleAlphas(samples, opacity);
                double transmittance = 1.0;
                for (float alpha : alphas) {
                    transmittance *= 1.0 - alpha;
                }
                assertEquals(opacity, 1.0 - transmittance, 1.0e-4, samples + " samples at " + opacity);
            }
        }
    }

    /** The point of the blur: no single sample stands out as a visible copy of the item. */
    @Test
    void eachSampleIsFaintAndOlderOnesFadeOut() {
        float[] alphas = MotionBlurSampling.sampleAlphas(40, 0.6f);
        assertTrue(alphas[0] < 0.06f, "newest sample too strong: " + alphas[0]);
        for (int i = 1; i < alphas.length; i++) {
            assertTrue(alphas[i] < alphas[i - 1], "sample " + i + " not fainter than the one before");
        }
    }

    @Test
    void fullOpacityStaysFinite() {
        for (float alpha : MotionBlurSampling.sampleAlphas(12, 1.0f)) {
            assertTrue(alpha > 0.0f && alpha < 1.0f);
        }
        assertEquals(0.0f, MotionBlurSampling.sampleAlphas(12, 0.0f)[0]);
    }
}
