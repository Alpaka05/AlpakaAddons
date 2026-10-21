package net.alpaka.addons.features.performance;

/**
 * The ping the HUD shows: the round trips of the last few seconds averaged, and the shown value
 * changed at most once a second.
 *
 * Answers can arrive several times a second - whenever F3's network chart or another mod measures -
 * and each round trip differs from the last by a millisecond or two. Shown one by one, the number
 * flickered; averaged and held for a second, it reads like vanilla's FPS counter.
 *
 * Pure arithmetic over timestamps passed in, so it can be tested without a game.
 */
public final class PingSmoother {
    /** Round trips older than this no longer count towards the average. */
    static final long WINDOW_MS = 10_000L;
    /** The shown value changes at most this often. */
    static final long REFRESH_MS = 1_000L;
    /** With no round trip for this long, there is no ping to show. */
    static final long STALE_MS = 20_000L;
    /** Enough for one answer a tick, as F3's network chart gets them, over the whole window. */
    static final int CAPACITY = 256;

    private final long[] times = new long[CAPACITY];
    private final int[] values = new int[CAPACITY];
    private int count = 0;
    private int head = 0;
    private int shown = -1;
    private long shownAtMs = Long.MIN_VALUE / 2;

    public synchronized void add(int rttMs, long nowMs) {
        times[head] = nowMs;
        values[head] = rttMs;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
        // The first answer shows at once rather than after a second of "--".
        if (shown < 0) refresh(nowMs);
    }

    /** The ping to show at {@code nowMs}, or -1 when there is none. */
    public synchronized int value(long nowMs) {
        if (count == 0 || nowMs - times[newest()] > STALE_MS) return -1;
        if (shown < 0 || nowMs - shownAtMs >= REFRESH_MS) refresh(nowMs);
        return shown;
    }

    public synchronized void reset() {
        count = 0;
        head = 0;
        shown = -1;
        shownAtMs = Long.MIN_VALUE / 2;
    }

    private void refresh(long nowMs) {
        long sum = 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            int index = (head - 1 - i + CAPACITY) % CAPACITY;
            if (nowMs - times[index] > WINDOW_MS) break;
            sum += values[index];
            n++;
        }
        // Nothing inside the window: the last round trip still stands until it goes stale.
        shown = n > 0 ? (int) Math.round((double) sum / n) : values[newest()];
        shownAtMs = nowMs;
    }

    private int newest() {
        return (head - 1 + CAPACITY) % CAPACITY;
    }
}
