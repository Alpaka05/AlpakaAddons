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
 * arrival is still animating, the lines are drawn shifted down by the height the new ones have not
 * yet claimed - inside the chat's box, which stays put and clips them - so the older lines glide up
 * rather than jump, and the arrival's own lines rise in from the bottom edge as they fade in.
 *
 * A burst of messages arriving within {@link #MERGE_NANOS} of each other counts as one arrival, and
 * the slide never covers more than {@link #MAX_SLIDE_LINES}, so busy chat is not in constant motion.
 *
 * Arrivals are kept newest-first and finish oldest-first (same duration, later start), so the
 * finished ones are always at the tail and can be dropped without shifting the line indices the
 * remaining ones map to. A re-layout after a resize or a tab switch replays every stored message
 * through the same path; {@link #beginReplay()} / {@link #endReplay()} keep those out of here.
 */
public final class SmoothChatFeature {

    private record Arrival(long startNanos, int lines) {}

    /** Lines arriving this soon after the newest arrival join it rather than starting their own. */
    private static final long MERGE_NANOS = 50_000_000L;
    /** The slide covers at most this many lines; any more simply appear. */
    private static final int MAX_SLIDE_LINES = 3;

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
        long now = System.nanoTime();
        prune(now);
        Arrival newest = ARRIVALS.peekFirst();
        if (newest != null && now - newest.startNanos < MERGE_NANOS) {
            ARRIVALS.pollFirst();
            ARRIVALS.addFirst(new Arrival(newest.startNanos, newest.lines + count));
            return;
        }
        ARRIVALS.addFirst(new Arrival(now, count));
    }

    /**
     * Lines at this index of the newest-first list were taken out - a compacted repeat lifting its
     * earlier copy - so the arrivals covering them shrink, and the ones after keep pointing at the
     * same lines.
     */
    public static void onLinesRemoved(int index, int count) {
        if (count <= 0 || ARRIVALS.isEmpty()) return;
        int end = index + count;
        int covered = 0;
        ArrayDeque<Arrival> kept = new ArrayDeque<>();
        for (Arrival arrival : ARRIVALS) {
            int from = covered;
            int to = covered + arrival.lines;
            int overlap = Math.max(0, Math.min(to, end) - Math.max(from, index));
            if (arrival.lines - overlap > 0) kept.addLast(new Arrival(arrival.startNanos, arrival.lines - overlap));
            covered = to;
        }
        ARRIVALS.clear();
        ARRIVALS.addAll(kept);
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
        return Math.min(offset, MAX_SLIDE_LINES * lineHeight);
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
