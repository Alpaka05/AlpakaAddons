package net.alpaka.addons.features.performance;

/**
 * A server's ticks per second, from packets that arrive at a known number of ticks apart.
 *
 * Fed with how many ticks each packet stands for and when it arrived, it reports the ticks over
 * the real time they took across the last {@link #WINDOW_MS} ms - the average SkyHanni shows, over
 * about 100 ticks. One late or bunched packet moves that by a fraction, so the reading stays put
 * while the server runs steadily.
 *
 * When packets stop for longer than a stall (a second, or a few of the source's usual gaps when
 * those are longer), the silence is added to the time, so a frozen server reads as falling instead
 * of keeping its last value. Short gaps between packets never count against it.
 *
 * Pure arithmetic over timestamps passed in, so it can be tested without a game.
 */
public final class TpsEstimator {
    /** The span the rate is averaged over: about 100 ticks at full speed. */
    static final long WINDOW_MS = 5_000L;
    /** Packets kept; enough for one a tick over the whole window. */
    static final int CAPACITY = 128;
    /** Silence shorter than this is a normal gap, never a stall. */
    static final long MIN_STALL_MS = 1_000L;
    /** For slow sources, a stall is this many of their usual gaps. */
    static final int STALL_GAPS = 3;
    /** A gap this long is a pause or a reconnect; the window starts over after it. */
    static final long RESET_GAP_MS = 30_000L;
    static final float MAX_TPS = 20.0f;

    private final long[] times = new long[CAPACITY];
    private final long[] ticks = new long[CAPACITY];
    private int count = 0;
    private int head = 0;
    private long tickTotal = 0L;

    /** A packet standing for {@code tickCount} ticks since the one before arrived at {@code nowMs}. */
    public synchronized void onTicks(long tickCount, long nowMs) {
        if (count > 0 && nowMs - times[newest()] > RESET_GAP_MS) reset();
        // The first packet only marks a start; the ticks it stands for ran before anything was timed.
        if (count > 0) tickTotal += tickCount;
        times[head] = nowMs;
        ticks[head] = tickTotal;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    /** The tick rate at {@code nowMs}, or NaN until two packets have arrived. */
    public synchronized float tps(long nowMs) {
        if (count < 2) return Float.NaN;
        int newest = newest();
        // The oldest packet still inside the window, but at least the one before the newest.
        int oldest = (newest - 1 + CAPACITY) % CAPACITY;
        int intervals = 1;
        for (int i = 2; i < count; i++) {
            int candidate = (newest - i + CAPACITY) % CAPACITY;
            if (times[newest] - times[candidate] > WINDOW_MS) break;
            oldest = candidate;
            intervals = i;
        }
        long span = times[newest] - times[oldest];
        long tickSpan = ticks[newest] - ticks[oldest];
        if (span <= 0) return MAX_TPS;

        long silence = nowMs - times[newest];
        long stall = Math.max(MIN_STALL_MS, STALL_GAPS * span / intervals);
        if (silence > stall) span += silence;
        float rate = tickSpan * 1000.0f / span;
        return Math.max(0.0f, Math.min(MAX_TPS, rate));
    }

    /** When the last packet arrived, or {@link Long#MIN_VALUE} before the first. */
    public synchronized long lastPacketMs() {
        return count == 0 ? Long.MIN_VALUE : times[newest()];
    }

    /** Packets counted since the last reset, up to {@link #CAPACITY}. */
    public synchronized int packets() {
        return count;
    }

    public synchronized void reset() {
        count = 0;
        head = 0;
        tickTotal = 0L;
    }

    private int newest() {
        return (head - 1 + CAPACITY) % CAPACITY;
    }
}
