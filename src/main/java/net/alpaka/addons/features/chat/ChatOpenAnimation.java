package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;

/**
 * Opening Animation: opening the chat unrolls it upwards from the input line instead of showing the
 * whole history at once, and the input line slides up from the bottom edge.
 *
 * The chat starts at the height the HUD chat had a moment ago - the lines still showing there stay
 * where they are - and grows to the open chat's height. The chat's box, its panel and its lines all
 * follow that one height; see ChatComponentMixin and ChatScreenMixin.
 */
public final class ChatOpenAnimation {

    /** How long the chat takes to unroll. */
    private static final long DURATION_NANOS = 250_000_000L;

    /** When the chat was opened; 0 while nothing is animating. */
    private static long startNanos;
    /** How many lines the HUD chat showed when the chat was opened; -1 until the first layout measures it. */
    private static int startLines = -1;

    private ChatOpenAnimation() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.betterChatOn(AlpakaConfig.instance.chatOpenAnimationEnabled);
    }

    /** The chat screen just opened. */
    public static void onOpen() {
        startNanos = isEnabled() ? System.nanoTime() : 0L;
        startLines = -1;
    }

    /** 0 → 1 over the duration, easing out so the chat opens quickly and settles gently. */
    public static float progress() {
        if (startNanos == 0L) return 1.0f;
        float t = (System.nanoTime() - startNanos) / (float) DURATION_NANOS;
        if (t >= 1.0f || !isEnabled()) {
            startNanos = 0L;
            return 1.0f;
        }
        if (t <= 0.0f) return 0.0f;
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    /** Whether the starting height still has to be measured, on the open chat's first layout. */
    public static boolean needsStartLines() {
        return startLines < 0;
    }

    public static void setStartLines(int lines) {
        startLines = Math.max(0, lines);
    }

    /** How many lines of the open chat's {@code shownLines} are unrolled at this progress, fractions included. */
    public static float revealedLines(int shownLines, float progress) {
        float start = Math.min(Math.max(startLines, 0), shownLines);
        return start + (shownLines - start) * progress;
    }
}
