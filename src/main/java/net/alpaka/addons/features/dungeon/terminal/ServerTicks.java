package net.alpaka.addons.features.dungeon.terminal;

import net.minecraft.util.Util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The server's ticks as the client sees them: Hypixel sends one ping each server tick
 * (terminals.md 1.4), counted here as they arrive.
 *
 * A clock that follows the server rather than the wall: when the server lags, it slows down with
 * it. The first-click guard waits for a number of these as well as for time, and the Water Board
 * times its steps by them. Counted on the network thread and read on the client thread, hence the
 * atomics. Counting is read-only - vanilla answers every ping as before - and costs an atomic
 * increment, so it runs whether or not a solver is on; that way a terminal opened right after
 * switching on already knows whether pings are arriving.
 */
public final class ServerTicks {

    /** Without a ping for this long, the server is taken to have stopped sending them. */
    public static final long STALE_MS = 2000L;

    private static final AtomicLong COUNT = new AtomicLong();
    private static final AtomicLong LAST_PING_MS = new AtomicLong(Long.MIN_VALUE / 2);

    private ServerTicks() {}

    /**
     * One ping from the server, already de-duplicated (the handler runs on the network thread and
     * again on the client thread). Pings with id 0 are not per-tick ones and do not count.
     */
    public static void onPing(int id) {
        if (id == 0) return;
        COUNT.incrementAndGet();
        LAST_PING_MS.set(Util.getMillis());
    }

    /** Ticks counted since the game started. Only differences mean anything. */
    public static long count() {
        return COUNT.get();
    }

    /** When the last ping arrived, on the {@code Util.getMillis()} clock. */
    public static long lastPingMs() {
        return LAST_PING_MS.get();
    }

    /** Whether pings are arriving: one came within {@link #STALE_MS} of {@code nowMs}. */
    public static boolean live(long nowMs) {
        return nowMs - LAST_PING_MS.get() <= STALE_MS;
    }

    /** Ticks since {@code sinceCount}, a value {@link #count()} returned earlier. */
    public static long since(long sinceCount) {
        return COUNT.get() - sinceCount;
    }
}
