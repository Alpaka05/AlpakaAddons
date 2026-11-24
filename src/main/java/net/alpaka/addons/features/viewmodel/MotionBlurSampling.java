package net.alpaka.addons.features.viewmodel;

/**
 * The numbers behind the swing motion blur: how many samples to take along the item's path and how
 * opaque each one is. Kept apart from the rendering so it can be tested on its own.
 *
 * The goal is a smear, not a row of copies. Samples are placed close enough that their edges are
 * only a few pixels apart, and their alphas are chosen so that a spot covered by every sample of
 * the trail ends up exactly at the configured opacity. A spot the item only passed through gets
 * proportionally less, which is what a camera with a slow shutter would show. Newer samples weigh
 * more than older ones, so the smear is densest right behind the item and fades out at its end.
 */
public final class MotionBlurSampling {

    /** Neighbouring samples are kept about this many pixels apart on screen. */
    static final float TARGET_SPACING_PIXELS = 2.5f;

    /** Below this much on-screen travel there is nothing to blur. */
    static final float MIN_PATH_PIXELS = 1.5f;

    /** At least this many samples once there is a blur at all, so it never shows as one copy. */
    static final int MIN_SAMPLES = 4;

    /** A sample fainter than this rounds to zero in an 8-bit framebuffer and is skipped. */
    public static final float MIN_VISIBLE_ALPHA = 0.5f / 255.0f;

    /** The most opacity the smear can reach, so the alpha formula never takes the log of zero. */
    private static final float MAX_OPACITY = 0.995f;

    private MotionBlurSampling() {
    }

    /**
     * How many samples to take for a trail whose item travelled {@code pathPixels} on screen,
     * capped at {@code maxSamples}; 0 when it barely moved.
     */
    public static int sampleCount(float pathPixels, int maxSamples) {
        if (!(pathPixels >= MIN_PATH_PIXELS) || maxSamples <= 0) {
            return 0;
        }
        int wanted = (int) Math.ceil(pathPixels / TARGET_SPACING_PIXELS);
        return Math.min(maxSamples, Math.max(Math.min(MIN_SAMPLES, maxSamples), wanted));
    }

    /**
     * The alpha of each of {@code samples} samples, newest first. Sample i (1-based, counted back
     * from the item) weighs {@code 1 - (i - 0.5) / samples}, and its alpha is set so that the
     * transmittance of all of them multiplied together is {@code 1 - opacity}: with ordinary alpha
     * blending, a pixel every sample covers ends up at exactly that opacity, however many samples
     * there are.
     */
    public static float[] sampleAlphas(int samples, float opacity) {
        float[] alphas = new float[Math.max(samples, 0)];
        if (samples <= 0 || opacity <= 0.0f) {
            return alphas;
        }
        double logTransmittance = Math.log(1.0 - Math.min(opacity, MAX_OPACITY));
        double weightSum = 0.0;
        for (int i = 1; i <= samples; i++) {
            weightSum += weight(i, samples);
        }
        for (int i = 1; i <= samples; i++) {
            alphas[i - 1] = (float) (1.0 - Math.exp(logTransmittance * weight(i, samples) / weightSum));
        }
        return alphas;
    }

    private static double weight(int i, int samples) {
        return 1.0 - (i - 0.5) / samples;
    }
}
