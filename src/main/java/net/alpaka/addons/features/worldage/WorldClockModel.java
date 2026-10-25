package net.alpaka.addons.features.worldage;

/**
 * Reads a server's day from the overworld clock updates it sends, including the islands where
 * Hypixel drives that clock by hand.
 *
 * The day of a server is its overworld clock over 24000 - the way Skytils, NoFrills and Scatha Pro
 * read it. The world age ({@code gameTime}) is no use: Hypixel carries it over from the map, so it
 * reads tens of thousands of days on every server. The clock is only honest while it runs on its
 * own, though, and Hypixel overrides it in two ways (measured on 2026-10-18):
 * <ul>
 *   <li><b>Pinned</b> (Crimson Isle): the clock runs at rate 1, but after every 20-tick time update
 *   the server jumps it ahead to midnight of the next day, so the sky stays dark. The clock gains a
 *   whole day per update, so its day count is the number of updates since the server started. Read
 *   as days, that was a counter going up once a second; dividing out the 20 ticks per update gives
 *   the real age. Two Crimson Isle servers worked out this way agree with their world age to within
 *   24 ticks, which is what confirms the reading.</li>
 *   <li><b>Frozen</b> (lobbies, the Hub, the Private Island): rate 0 and a fixed value, so the clock
 *   says nothing about the server's age. The world age is honest only on worlds the server starts
 *   fresh: a Private Island begins at 0 when it is loaded, while the Hub and lobbies read thousands
 *   of days carried over from their map. A frozen clock therefore shows the world age when it is
 *   plausible as an uptime, and otherwise the frozen clock's own day - the number NoFrills and
 *   Skytils show there, since nothing better can be read.</li>
 * </ul>
 *
 * Kept free of Minecraft types so the tests can replay recorded packets through it.
 */
final class WorldClockModel {

    /** Day returned while the server's age cannot be read. */
    static final long UNKNOWN_DAY = -1L;

    /** Ticks in a Minecraft day. */
    private static final long DAY_TICKS = 24000L;
    /** A pinned clock gains a day per time update, and the server sends one every 20 ticks. */
    private static final long TICKS_PER_TIME_UPDATE = 20L;
    /**
     * How far the clock may run ahead of the ticks that passed before it counts as pinned. Half a day:
     * far above any lag or rounding, far below the full day a pinning jump adds.
     */
    private static final long JUMP_THRESHOLD_TICKS = DAY_TICKS / 2;
    /**
     * How long a clock stays pinned without another jump. A pinned clock jumps every 20 ticks, so five
     * seconds without one means it runs on its own again - or the jump was a one-off, like a
     * {@code /time set} in singleplayer.
     */
    private static final long PIN_TIMEOUT_TICKS = 100L;
    /**
     * The oldest world age taken as real under a frozen clock. Hypixel servers seen so far ran for about
     * a month at most, while a world age carried over from a map was 1450 days or more.
     */
    private static final long MAX_PLAUSIBLE_AGE_TICKS = 100L * DAY_TICKS;

    enum Mode {
        /**
         * Not enough updates yet to tell a pinned clock from a running one: until the first jump,
         * or two updates in a row without one.
         */
        WAITING,
        NORMAL,
        PINNED,
        FROZEN
    }

    private Mode mode = Mode.WAITING;
    private long lastClockTicks = -1L;
    private long lastGameTime = 0L;
    private long lastJumpGameTime = 0L;
    /** Updates in a row without a jump while {@link Mode#WAITING}. */
    private int steadyUpdates = 0;

    /** Forgets everything; called when the player changes world. */
    void reset() {
        mode = Mode.WAITING;
        lastClockTicks = -1L;
        lastGameTime = 0L;
        lastJumpGameTime = 0L;
        steadyUpdates = 0;
    }

    /** Takes one time update: the world's game time and the overworld clock's value and rate in it. */
    void onTimeUpdate(long gameTime, long clockTicks, float rate) {
        if (rate == 0.0f) {
            mode = Mode.FROZEN;
        } else if (lastClockTicks >= 0L) {
            long elapsed = Math.max(0L, gameTime - lastGameTime);
            long expected = (long) Math.ceil(elapsed * (double) rate);
            if (clockTicks - lastClockTicks > expected + JUMP_THRESHOLD_TICKS) {
                mode = Mode.PINNED;
                lastJumpGameTime = gameTime;
            } else if (mode == Mode.WAITING) {
                // The update after joining can come before a pinned clock's first jump, and between
                // its jumps a pinned clock runs like any other: taken as running, the Crimson Isle
                // showed the clock's raw count - "Day 28399" - for a second. Two steady updates in a
                // row first; a pinned clock jumps before the second.
                if (++steadyUpdates >= 2) mode = Mode.NORMAL;
            } else if (mode != Mode.PINNED || gameTime - lastJumpGameTime > PIN_TIMEOUT_TICKS) {
                // A pinned clock's regular updates between the jumps look normal, so it only counts
                // as running again once the jumps have stopped.
                mode = Mode.NORMAL;
            }
        }
        lastClockTicks = clockTicks;
        lastGameTime = gameTime;
    }

    /**
     * The server's day, or {@link #UNKNOWN_DAY}.
     *
     * @param liveClockTicks the overworld clock as the client runs it now, which moves on between
     *                       updates; only used while the clock runs normally
     */
    long day(long liveClockTicks) {
        return switch (mode) {
            case NORMAL -> Math.max(0L, liveClockTicks / DAY_TICKS);
            case PINNED -> Math.max(0L, lastClockTicks / DAY_TICKS * TICKS_PER_TIME_UPDATE / DAY_TICKS);
            case FROZEN -> (lastGameTime < MAX_PLAUSIBLE_AGE_TICKS ? lastGameTime : lastClockTicks) / DAY_TICKS;
            case WAITING -> UNKNOWN_DAY;
        };
    }

    Mode mode() {
        return mode;
    }
}
