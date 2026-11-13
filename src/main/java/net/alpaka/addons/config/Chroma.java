package net.alpaka.addons.config;

import net.minecraft.util.Mth;

/**
 * A rainbow that a color option can use instead of its fixed color, set in the color picker of the
 * options that offer it ({@code ConfigOption.withChroma}). Only where a moving rainbow makes sense
 * gets one, so most color options have none.
 *
 * The hue moves along a position (a letter's index in a line, say) and over time: one full rainbow
 * spans {@link #size} positions and passes by {@link #speed} times every two seconds.
 */
public final class Chroma {
    public static final float MIN_SPEED = 0.1f, MAX_SPEED = 3.0f;
    public static final float MIN_SIZE = 5.0f, MAX_SIZE = 100.0f;

    /** One full rainbow every two seconds at 1x, like the block overlay's chroma. */
    private static final double CYCLES_PER_SECOND = 0.5;

    public boolean enabled = false;
    public float speed = 1.0f;
    /** Positions one full rainbow spans; smaller packs more colors into a word. */
    public float size = 30.0f;
    /** How strong the colors are, in percent; low values fade toward white. */
    public float saturation = 80.0f;

    public Chroma() {
    }

    /** A chroma with its own defaults, for an option whose rainbow should start out different. */
    public Chroma(boolean enabled, float speed, float size, float saturation) {
        this.enabled = enabled;
        this.speed = speed;
        this.size = size;
        this.saturation = saturation;
    }

    public Chroma copy() {
        Chroma c = new Chroma();
        c.set(this);
        return c;
    }

    public void set(Chroma other) {
        enabled = other.enabled;
        speed = other.speed;
        size = other.size;
        saturation = other.saturation;
    }

    public boolean sameAs(Chroma other) {
        return enabled == other.enabled && speed == other.speed && size == other.size
                && saturation == other.saturation;
    }

    // Clamped where read, so a hand-edited config cannot push them out of the sliders' range.
    public float speed() { return Mth.clamp(speed, MIN_SPEED, MAX_SPEED); }
    public float size() { return Mth.clamp(size, MIN_SIZE, MAX_SIZE); }
    public float saturation01() { return Mth.clamp(saturation, 0.0f, 100.0f) / 100.0f; }

    /** How far the rainbow has moved at {@code timeMs}, in rainbows. */
    public double shift(long timeMs) {
        return timeMs / 1000.0 * CYCLES_PER_SECOND * speed();
    }

    /** The hue (0..1) at {@code position} for a {@link #shift}; moves toward higher positions over time. */
    public float hue(double shift, double position) {
        double hue = position / size() - shift;
        return (float) (hue - Math.floor(hue));
    }

    /** The color (0xRRGGBB, no alpha) at {@code position} for a {@link #shift}. */
    public int rgb(double shift, double position) {
        return Mth.hsvToRgb(hue(shift, position), saturation01(), 1.0f) & 0xFFFFFF;
    }

    /** The color at {@code position} now. */
    public int rgbNow(double position) {
        return rgb(shift(System.currentTimeMillis()), position);
    }
}
