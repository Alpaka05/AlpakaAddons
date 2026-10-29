package net.alpaka.addons.features.dungeon.puzzle;

/**
 * The Water Board's clock, in server ticks.
 *
 * The water moves one block every five server ticks, so the flips have to follow the server, not
 * the wall: when Hypixel lags, the water slows down and so must the countdowns. Hypixel sends one
 * ping per server tick (ServerTicks counts them), and this clock advances by the pings counted
 * since it was last read. Should the pings stop for longer than a lag spike lasts - a server that
 * does not send them - it goes by the wall clock instead, 50 ms to the tick, rather than standing
 * still.
 *
 * Only differences between two readings mean anything. Pure; the caller passes the counts in.
 */
public final class WaterBoardClock {

    private static final long MS_PER_TICK = 50L;

    private long ticks = 0L;
    private long lastServerCount = 0L;
    private long lastMs = 0L;
    private long carryMs = 0L;
    private boolean started = false;

    /**
     * The time now, in ticks.
     *
     * @param serverCount ServerTicks.count()
     * @param pingsLive   whether pings are still arriving (ServerTicks.live)
     * @param nowMs       a monotonic millisecond clock
     */
    public long read(long serverCount, boolean pingsLive, long nowMs) {
        if (!started) {
            started = true;
        } else if (pingsLive) {
            ticks += Math.max(0L, serverCount - lastServerCount);
            carryMs = 0L;
        } else {
            long ms = carryMs + Math.max(0L, nowMs - lastMs);
            ticks += ms / MS_PER_TICK;
            carryMs = ms % MS_PER_TICK;
        }
        lastServerCount = serverCount;
        lastMs = nowMs;
        return ticks;
    }
}
