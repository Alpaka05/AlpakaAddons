package net.alpaka.addons.features.viewmodel;

/**
 * The numbers behind the swing motion blur: how many samples to take along the item's path and how
 * opaque each one is. Kept apart from the rendering so it can be tested on its own.
 *
 * The goal is a smear, not a row of copies. Samples are placed close enough that their edges are
 * only a few pixels apart, and each one is faint, so no single copy stands out. How faint follows
 * from how many samples overlap on any one spot: a sword blade is only two or three texture pixels
 * wide, so as it sweeps past, a spot on screen is covered by the few samples whose blade lies over
 * it, not by all of them. The alphas are chosen so that those few add up to the configured opacity
 * right behind the item; a big block covers a spot with more of them and simply gets more solid.
 * The samples are spread evenly along the distance the item travelled rather than over time, so
 * they are as close together in the fastest part of the swing as in the slow ones. Along the
 * trail the samples fade out with a smooth curve, so the smear is densest right behind
 * the item and dissolves towards its end instead of stopping at an edge.
 */
public final class MotionBlurSampling {

    /** Neighbouring samples are kept about this many pixels apart on screen. */
    static final float TARGET_SPACING_PIXELS = 2.0f;

    /** Below this much on-screen travel there is nothing to blur. */
    static final float MIN_PATH_PIXELS = 1.5f;

    /** At least this many samples once there is a blur at all, so it never shows as one copy. */
    static final int MIN_SAMPLES = 4;

    /**
     * Width of the thin parts of an item - a blade, a handle - in texture pixels, out of the 16 a
     * texture is wide. It decides how many samples overlap on one spot as such a part sweeps past.
     */
    static final float THIN_PART_TEXELS = 2.5f;

    /**
     * No sample is drawn stronger than this. When the samples have to sit far apart (a very fast
     * swing at a low quality), a strong one would show its edges as a copy of the item; capped,
     * the smear just gets fainter.
     */
    static final float MAX_SAMPLE_ALPHA = 0.3f;

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
     * How many neighbouring samples cover one spot as a thin part of the item sweeps past it, for
     * an item whose texture spans {@code unitPixels} on screen and samples {@code spacingPixels}
     * apart. Never less than one.
     */
    public static float overlap(float unitPixels, float spacingPixels) {
        if (!(spacingPixels > 0.0f) || !(unitPixels > 0.0f)) {
            return 1.0f;
        }
        return Math.max(1.0f, THIN_PART_TEXELS * unitPixels / 16.0f / spacingPixels);
    }

    /**
     * The alpha of a sample whose pose is {@code age} of the trail length old (0 at the item, 1 at
     * the end of the trail). It is set so that {@code overlap} fresh samples laid over each other
     * reach {@code opacity} - their transmittances multiplied together are {@code 1 - opacity} -
     * and fades out with {@link #weight} towards the end of the trail.
     */
    public static float alpha(float age, float opacity, float overlap) {
        if (!(opacity > 0.0f)) {
            return 0.0f;
        }
        double logTransmittance = Math.log(1.0 - Math.min(opacity, MAX_OPACITY));
        double alpha = 1.0 - Math.exp(logTransmittance / Math.max(1.0f, overlap) * weight(age));
        return (float) Math.min(MAX_SAMPLE_ALPHA, alpha);
    }

    /**
     * How much a sample {@code age} of the trail length old counts: 1 at the item, easing down to
     * 0 at the end of the trail along a smoothstep, so the smear fades without a visible end.
     */
    static double weight(float age) {
        double a = Math.clamp(age, 0.0, 1.0);
        return 1.0 - a * a * (3.0 - 2.0 * a);
    }
}
