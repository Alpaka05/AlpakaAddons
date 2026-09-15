package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Smooth chat: a new message slides up into place and fades in, instead of the chat jumping by a
 * line the moment it arrives.
 *
 * The chat keeps its lines newest-first, and a new message is laid out by adding its lines at the
 * front. Each such arrival is remembered here with the number of lines it added and when. While an
 * arrival is still animating, the whole chat is drawn shifted down by the height its lines have not
 * yet claimed, so the older lines glide up rather than jump, and the arrival's own lines are drawn
 * with the animation's progress as their alpha, so they fade in where they land.
 *
 * Arrivals are kept newest-first and finish oldest-first (same duration, later start), so the
 * finished ones are always at the tail and can be dropped without shifting the line indices the
 * remaining ones map to. A re-layout after a resize or a tab switch replays every stored message
 * through the same path; {@link #beginReplay()} / {@link #endReplay()} keep those out of here.
 */
public final class SmoothChatFeature {

    private record Arrival(long startNanos, int lines) {}

    /** Newest first. */
    private static final ArrayDeque<Arrival> ARRIVALS = new ArrayDeque<>();
    private static boolean replaying;

    private SmoothChatFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.smoothChatEnabled;
    }

    /**
     * How long one arrival takes. The strength setting runs 1..10; each step is 60 ms, so the
     * default of 5 is a 300 ms slide, and 10 is a slow, very visible 600 ms.
     */
    private static long durationNanos() {
        int strength = Math.max(1, Math.min(10, AlpakaConfig.instance.smoothChatStrength));
        return strength * 60_000_000L;
    }

    public static void beginReplay() {
        replaying = true;
    }

    public static void endReplay() {
        replaying = false;
    }

    /** A live message just added this many lines at the front of the chat. */
    public static void onLinesAdded(int count) {
        if (count <= 0 || replaying || !isEnabled()) return;
        prune(System.nanoTime());
        ARRIVALS.addFirst(new Arrival(System.nanoTime(), count));
    }

    /** The chat was cleared; nothing left to animate. */
    public static void clear() {
        ARRIVALS.clear();
    }

    /** 0 → 1 over the duration, easing out so the movement is quick to start and settles gently. */
    private static float progress(Arrival arrival, long now) {
        float t = (now - arrival.startNanos) / (float) durationNanos();
        if (t >= 1.0f) return 1.0f;
        if (t <= 0.0f) return 0.0f;
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static void prune(long now) {
        while (!ARRIVALS.isEmpty() && progress(ARRIVALS.peekLast(), now) >= 1.0f) {
            ARRIVALS.pollLast();
        }
    }

    /**
     * How far down the whole chat is drawn right now, in chat-scaled pixels: the height of every
     * animating arrival's lines that they have not yet claimed. 0 while nothing is animating.
     */
    public static float slideOffset(int lineHeight) {
        if (ARRIVALS.isEmpty()) return 0.0f;
        long now = System.nanoTime();
        prune(now);
        float offset = 0.0f;
        for (Arrival arrival : ARRIVALS) {
            offset += (1.0f - progress(arrival, now)) * arrival.lines * lineHeight;
        }
        return offset;
    }

    /**
     * The alpha multiplier for the line at this index of the chat's newest-first line list: the
     * progress of the arrival that brought it, or 1 for a line that is not animating.
     */
    public static float lineAlpha(int trimmedIndex) {
        if (ARRIVALS.isEmpty()) return 1.0f;
        long now = System.nanoTime();
        int covered = 0;
        Iterator<Arrival> it = ARRIVALS.iterator();
        while (it.hasNext()) {
            Arrival arrival = it.next();
            if (trimmedIndex < covered + arrival.lines) {
                return progress(arrival, now);
            }
            covered += arrival.lines;
        }
        return 1.0f;
    }
}
