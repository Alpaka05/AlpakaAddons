package net.alpaka.addons.features.performance;

/**
 * The server's ticks per second, worked out from the time updates it sends anyway.
 *
 * A server sends its game time once every 20 ticks (Hypixel included), so the ticks between two
 * updates over the real time between them is its tick rate - the same signal Odin and other
 * SkyBlock mods read, and it costs no packet of our own. The game time in the update is used when it
 * moves sensibly; where a server pins or jumps it, the update counts as the usual 20 ticks.
 *
 * The rate is taken over the last few updates rather than the last two, so one late packet does
 * not flash a dip. When updates stop coming, the silence counts against the rate, so a frozen
 * server reads as falling towards 0 instead of keeping its last value.
 *
 * Pure arithmetic over timestamps passed in, so it can be tested without a game.
 */
public final class TpsEstimator {
    /** Updates kept: five intervals, about five seconds at the usual one update a second. */
    static final int WINDOW = 6;
    /** The ticks between two updates when the server's game time cannot be trusted. */
    static final long TICKS_PER_UPDATE = 20L;
    /** A game time step beyond this is a jump (a new server, a time set), not ticks that ran. */
    static final long MAX_PLAUSIBLE_STEP = 1200L;
    /** A gap this long is a pause or a reconnect; the window starts over after it. */
    static final long RESET_GAP_MS = 30_000L;
    static final float MAX_TPS = 20.0f;

    private final long[] times = new long[WINDOW];
    private final long[] ticks = new long[WINDOW];
    private int count = 0;
    private int head = 0;
    private long lastGameTime = Long.MIN_VALUE;
    private long tickTotal = 0L;

    /** A time update arrived at {@code nowMs} carrying the server's {@code gameTime}. */
    public synchronized void onTimeUpdate(long gameTime, long nowMs) {
        if (count > 0 && nowMs - times[newest()] > RESET_GAP_MS) reset();
        if (count > 0) {
            long step = gameTime - lastGameTime;
            tickTotal += step >= 1 && step <= MAX_PLAUSIBLE_STEP ? step : TICKS_PER_UPDATE;
        }
        lastGameTime = gameTime;
        times[head] = nowMs;
        ticks[head] = tickTotal;
        head = (head + 1) % WINDOW;
        if (count < WINDOW) count++;
    }

    /** The tick rate at {@code nowMs}, or NaN until two updates have arrived. */
    public synchronized float tps(long nowMs) {
        if (count < 2) return Float.NaN;
        int newest = newest();
        int oldest = (head - count + WINDOW) % WINDOW;
        long span = times[newest] - times[oldest];
        long tickSpan = ticks[newest] - ticks[oldest];
        if (span <= 0) return MAX_TPS;
        // Silence past the usual interval is time in which no tick was reported.
        long usual = span / (count - 1);
        long overdue = Math.max(0L, nowMs - times[newest] - usual);
        float rate = tickSpan * 1000.0f / (span + overdue);
        return Math.max(0.0f, Math.min(MAX_TPS, rate));
    }

    public synchronized void reset() {
        count = 0;
        head = 0;
        tickTotal = 0L;
        lastGameTime = Long.MIN_VALUE;
    }

    private int newest() {
        return (head - 1 + WINDOW) % WINDOW;
    }
}
