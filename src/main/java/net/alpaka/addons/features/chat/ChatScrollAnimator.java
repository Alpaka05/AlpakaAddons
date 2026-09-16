package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;

/**
 * Smooth chat scrolling: the chat's scroll position is a whole number of lines that jumps with
 * every wheel notch, so the lines used to snap. This keeps a fractional "visual" position that
 * chases the real one, and the chat is drawn shifted by the difference, so each notch glides the
 * lines into place instead - in the HUD, in the chat screen and while peeking, which all draw
 * through the same path.
 *
 * A new message arriving while scrolled up makes vanilla bump the position by a line to keep the
 * view still; {@link #snap(int)} moves the visual position along with it so that bump does not
 * read as a scroll.
 */
public final class ChatScrollAnimator {
    private static float visual;
    private static long lastNanos;

    /**
     * How fast the visual position closes in, per second. A wheel notch moves the chat seven lines,
     * so the glide has to be slow enough to be seen over that distance: 10 per second settles in
     * about 300 ms. Overridable with -Dalpaka.scrollRate for tuning.
     */
    private static final float RATE = Float.parseFloat(System.getProperty("alpaka.scrollRate", "10"));

    private ChatScrollAnimator() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatSmoothScrollEnabled;
    }

    /**
     * Advances the visual position towards the real one and returns their difference in lines:
     * negative right after scrolling up (the lines are still drawn a little further up and settle
     * down), 0 at rest.
     */
    public static float offsetLines(int target) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000.0f);
        lastNanos = now;
        if (!isEnabled()) {
            visual = target;
            return 0.0f;
        }
        visual += (target - visual) * (1.0f - (float) Math.exp(-dt * RATE));
        if (Math.abs(target - visual) < 0.002f) visual = target;
        return visual - target;
    }

    /** The real position moved by this much without the user scrolling; follow it at once. */
    public static void snap(int delta) {
        visual += delta;
    }

    public static void reset() {
        visual = 0.0f;
    }
}
