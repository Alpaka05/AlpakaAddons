package net.alpaka.addons.features.worldage;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.clock.ClockNetworkState;

/**
 * Works out the server's day from the time packets, including the islands where Hypixel drives the
 * clock by hand.
 *
 * The day of a server is its time-of-day clock over 24000 - the way Skytils, NoFrills and Scatha Pro
 * read it. The world age ({@code gameTime}) is no use: Hypixel carries it over from the map, so it
 * reads tens of thousands of days on every server. The clock is only honest while it runs on its
 * own, though, and Hypixel overrides it in two ways, both seen in the packets on 2026-10-18:
 * <ul>
 *   <li><b>Pinned</b> (Crimson Isle): the clock runs at rate 1, but once a second - after every
 *   20-tick time update - the server jumps it ahead to midnight of the next day, so the sky stays
 *   dark. The clock therefore gains a whole day per update, and its day count is the number of
 *   updates since the server started, i.e. its uptime in seconds of server ticks. Reading it as days
 *   was the "one day per second" counter; dividing out the 20 ticks per update gives the real age.</li>
 *   <li><b>Frozen</b> (Hub during events): rate 0 and a fixed value. Nothing about the server's age
 *   can be read from it.</li>
 * </ul>
 */
public final class WorldClockTracker {

    /** Ticks in a Minecraft day. */
    private static final long DAY_TICKS = 24000L;
    /** A pinned clock gains a day per time update, and the server sends one every 20 ticks. */
    private static final long TICKS_PER_TIME_UPDATE = 20L;
    /**
     * How far the clock may run ahead of the ticks that passed before it counts as pinned. Half a day:
     * far above any lag or rounding, far below the full day a pinning jump adds.
     */
    private static final long JUMP_THRESHOLD_TICKS = DAY_TICKS / 2;

    /** Day returned while the server's age cannot be read, shown as "?". */
    public static final long UNKNOWN_DAY = -1L;

    private enum Mode { WAITING, NORMAL, PINNED, FROZEN }

    private static ClientLevel trackedLevel = null;
    private static Mode mode = Mode.WAITING;
    private static long lastClockTicks = -1L;
    private static long lastGameTime = 0L;

    private WorldClockTracker() {}

    /** Called on the client thread once a time packet has been applied. */
    public static void onTimePacket(ClientLevel level, ClientboundSetTimePacket packet) {
        if (level != trackedLevel) {
            trackedLevel = level;
            mode = Mode.WAITING;
            lastClockTicks = -1L;
        }

        var clock = level.dimensionType().defaultClock();
        if (clock.isEmpty()) return;
        ClockNetworkState state = packet.clockUpdates().get(clock.get());
        if (state == null) return;

        long ticks = state.totalTicks();
        long gameTime = packet.gameTime();

        if (state.rate() == 0.0f) {
            mode = Mode.FROZEN;
        } else if (lastClockTicks >= 0L) {
            long elapsed = Math.max(0L, gameTime - lastGameTime);
            long expected = (long) Math.ceil(elapsed * (double) state.rate());
            if (ticks - lastClockTicks > expected + JUMP_THRESHOLD_TICKS) {
                // Sticky for this world: between the jumps the regular updates look normal.
                mode = Mode.PINNED;
            } else if (mode != Mode.PINNED) {
                mode = Mode.NORMAL;
            }
        }
        // The very first packet of a world cannot tell a pinned clock from a running one; the day
        // stays unknown until the second one, a second later.

        lastClockTicks = ticks;
        lastGameTime = gameTime;
    }

    /** The server's day, or {@link #UNKNOWN_DAY} when it cannot be read (yet). */
    public static long getDay(ClientLevel level) {
        if (level != trackedLevel) {
            // No time packet for this world yet: singleplayer before the first sync, or a fresh join.
            return UNKNOWN_DAY;
        }
        return switch (mode) {
            case NORMAL -> Math.max(0L, level.getOverworldClockTime() / DAY_TICKS);
            case PINNED -> Math.max(0L, lastClockTicks / DAY_TICKS * TICKS_PER_TIME_UPDATE / DAY_TICKS);
            case WAITING, FROZEN -> UNKNOWN_DAY;
        };
    }
}
