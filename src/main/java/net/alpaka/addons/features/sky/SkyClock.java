package net.alpaka.addons.features.sky;

/**
 * The animated skies' own clock: seconds of animation, run at the Animation Speed setting.
 *
 * Not the game's time, which stands still while the game is paused, jumps when the server sets
 * it and starts over every day; and the speed is applied step by step rather than to the total,
 * so moving the slider makes the sky run faster from where it is instead of leaping ahead. The
 * count starts over after four hours, while a float in the shader still resolves it finely; that
 * is the only jump.
 */
final class SkyClock {
    static final double WRAP_SECONDS = 4.0 * 60.0 * 60.0;

    /** The most one frame moves the sky on, so after a pause or a hitch it resumes rather than leaps. */
    static final double MAX_STEP_SECONDS = 0.25;

    private double seconds = 0.0;
    private long lastNanos = 0L;
    private boolean started = false;

    /** Moves the clock on to this moment at this speed and returns where it stands. */
    float advance(long nowNanos, float speed) {
        if (started) {
            double step = Math.min(Math.max(nowNanos - lastNanos, 0L) / 1.0e9, MAX_STEP_SECONDS);
            seconds = (seconds + step * Math.max(speed, 0.0f)) % WRAP_SECONDS;
        }
        started = true;
        lastNanos = nowNanos;
        return (float) seconds;
    }
}
