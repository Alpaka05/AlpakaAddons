package net.alpaka.addons.features.worldage;

import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

/**
 * Feeds the time packets of the current world into a {@link WorldClockModel}, which works out the
 * server's day from them.
 */
public final class WorldClockTracker {

    /** Day returned while the server's age cannot be read, shown as "?". */
    public static final long UNKNOWN_DAY = WorldClockModel.UNKNOWN_DAY;

    private static final WorldClockModel MODEL = new WorldClockModel();
    /**
     * The world the model reads, held weakly: a strong hold kept the last world - its chunks and
     * entities - alive in the menus after leaving it, until the next world sent its time.
     */
    private static java.lang.ref.WeakReference<ClientLevel> trackedLevel = new java.lang.ref.WeakReference<>(null);

    private WorldClockTracker() {}

    /** Called on the client thread once a time packet has been applied. */
    public static void onTimePacket(ClientLevel level, ClientboundSetTimePacket packet) {
        if (level != trackedLevel.get()) {
            trackedLevel = new java.lang.ref.WeakReference<>(level);
            MODEL.reset();
        }
        ClockNetworkState overworld = overworldClock(packet.clockUpdates());
        if (overworld != null) {
            MODEL.onTimeUpdate(packet.gameTime(), overworld.totalTicks(), overworld.rate());
        }
    }

    /**
     * The overworld clock's state, looked up by name rather than through the dimension's default
     * clock: the Crimson Isle is a Nether dimension, which has no default clock, yet the server still
     * sends - and pins - the overworld one there.
     */
    private static ClockNetworkState overworldClock(Map<Holder<WorldClock>, ClockNetworkState> updates) {
        for (Map.Entry<Holder<WorldClock>, ClockNetworkState> entry : updates.entrySet()) {
            if (entry.getKey().is(WorldClocks.OVERWORLD)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** The server's day, or {@link #UNKNOWN_DAY} when it cannot be read (yet). */
    public static long getDay(ClientLevel level) {
        if (level == null || level != trackedLevel.get()) {
            // No time packet for this world yet.
            return UNKNOWN_DAY;
        }
        return MODEL.day(level.getOverworldClockTime());
    }

    /** Forgets the world and what was read from it; called on disconnect. */
    public static void reset() {
        trackedLevel = new java.lang.ref.WeakReference<>(null);
        MODEL.reset();
    }
}
