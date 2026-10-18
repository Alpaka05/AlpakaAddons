package net.alpaka.addons.features.performance;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * The numbers the Performance HUD shows, collected as cheaply as the game allows.
 *
 * <ul>
 *   <li>FPS is vanilla's own counter ({@link Minecraft#getFps()}), which the game updates once a
 *       second anyway; reading it costs nothing per frame.</li>
 *   <li>TPS is counted as SkyHanni counts it: Hypixel sends a ping packet every server tick, so
 *       the ticks over the time they took, averaged over about 100 of them, is the tick rate.
 *       Where a server does not ping every tick (vanilla servers, singleplayer), the time updates
 *       every server sends once a second, 20 ticks apart, stand in. No packet of our own.</li>
 *   <li>Ping is the round trip of a ping request, the one vanilla's F3 network chart sends every
 *       tick. Any answer counts, so while F3's chart or another mod already measures, nothing more
 *       goes out. Otherwise one request leaves every {@link #PROBE_INTERVAL_MS} ms, and only while
 *       the HUD is on screen with Ping shown - a hundredth of what the F3 chart sends. What shows
 *       is the average of the last few seconds, changed once a second; see {@link PingSmoother}.</li>
 * </ul>
 */
public final class PerformanceStats {
    /** How often the HUD measures the ping itself when nothing else does. */
    static final long PROBE_INTERVAL_MS = Outbound.PROBE_FLOOR_MS;
    /** Answers outside this are from a request timed on another clock, not a real round trip. */
    private static final long MAX_PLAUSIBLE_PING_MS = 30_000L;
    /** The HUD counts as on screen this long after it was last drawn. */
    private static final long SHOWN_GRACE_MS = 1_500L;

    /** Below this many pings a second the server is not pinging every tick, or barely ticking. */
    private static final float MIN_TICK_PINGS_PER_SECOND = 4.0f;
    /** A time update's game time step beyond this is a jump (a new server, a time set), not ticks that ran. */
    private static final long MAX_PLAUSIBLE_STEP = 1_200L;
    private static final long TICKS_PER_TIME_UPDATE = 20L;

    /** One entry per server tick, from the pings Hypixel sends each tick. */
    private static final TpsEstimator TICK_PINGS = new TpsEstimator();
    /** One entry per time update, for servers without a ping every tick. */
    private static final TpsEstimator TIME_UPDATES = new TpsEstimator();
    private static long lastGameTime = Long.MIN_VALUE;

    /** Written on the network thread, where answers are handled; read on the render thread. */
    private static final PingSmoother PING = new PingSmoother();
    private static volatile long lastAnswerMs = Long.MIN_VALUE / 2;
    private static long lastProbeMs = Long.MIN_VALUE / 2;
    private static long lastShownMs = Long.MIN_VALUE / 2;
    /**
     * The packets last counted. Each is handled twice - first on the network thread, then again on
     * the client thread it is handed to - and must only count once.
     */
    private static volatile Object lastTimePacket = null;
    private static volatile Object lastServerPing = null;

    private PerformanceStats() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        TICK_PINGS.reset();
        TIME_UPDATES.reset();
        lastGameTime = Long.MIN_VALUE;
        PING.reset();
        lastAnswerMs = Long.MIN_VALUE / 2;
        lastTimePacket = null;
        lastServerPing = null;
    }

    /** From the packet listener, for every time update the server sends. */
    public static void onTimeUpdate(Object packet, long gameTime) {
        if (packet == lastTimePacket) return;
        lastTimePacket = packet;
        long step = gameTime - lastGameTime;
        lastGameTime = gameTime;
        // Where a server pins or jumps its game time, the update counts as the usual 20 ticks.
        TIME_UPDATES.onTicks(step >= 1 && step <= MAX_PLAUSIBLE_STEP ? step : TICKS_PER_TIME_UPDATE, Util.getMillis());
    }

    /** From the packet listener, for every ping the server sends; Hypixel sends one each tick. */
    public static void onServerPing(Object packet) {
        if (packet == lastServerPing) return;
        lastServerPing = packet;
        TICK_PINGS.onTicks(1L, Util.getMillis());
    }

    /** From the packet listener, for every answer to a ping request, ours or not. */
    public static void onPong(long requestTimeMs) {
        long now = Util.getMillis();
        long rtt = now - requestTimeMs;
        if (rtt < 0 || rtt > MAX_PLAUSIBLE_PING_MS) return;
        PING.add((int) rtt, now);
        lastAnswerMs = now;
    }

    /** The HUD was drawn this frame; only then is the ping worth measuring. */
    static void markShown() {
        lastShownMs = Util.getMillis();
    }

    private static void tick() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (!cfg.performanceHudEnabled || !cfg.performanceHudShowPing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        long now = Util.getMillis();
        if (now - lastShownMs > SHOWN_GRACE_MS) return;
        // An answer to anyone's request - F3's chart, another mod's - is as good as our own.
        if (now - lastAnswerMs < PROBE_INTERVAL_MS || now - lastProbeMs < PROBE_INTERVAL_MS) return;
        lastProbeMs = now;
        Outbound.latencyProbe();
    }

    public static int fps() {
        return Minecraft.getInstance().getFps();
    }

    /** The recent round trips averaged, in milliseconds, or -1 when there is no recent one. */
    public static int pingMs() {
        return PING.value(Util.getMillis());
    }

    /** The server's tick rate, or NaN until it is known. */
    public static float tps() {
        long now = Util.getMillis();
        float fromPings = TICK_PINGS.tps(now);
        // Pings a few times a second at most are a server that only pings now and then, not every
        // tick; then, and while a server barely ticks at all, the time updates tell the rate.
        if (!Float.isNaN(fromPings) && fromPings >= MIN_TICK_PINGS_PER_SECOND) return fromPings;
        float fromTime = TIME_UPDATES.tps(now);
        return Float.isNaN(fromTime) ? fromPings : fromTime;
    }
}
