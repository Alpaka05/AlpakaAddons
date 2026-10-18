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
 *   <li>TPS comes from the time updates every server sends once a second; see
 *       {@link TpsEstimator}. No packet of our own.</li>
 *   <li>Ping is the round trip of a ping request, the one vanilla's F3 network chart sends every
 *       tick. Any answer counts, so while F3's chart or another mod already measures, nothing more
 *       goes out. Otherwise one request leaves every {@link #PROBE_INTERVAL_MS} ms, and only while
 *       the HUD is on screen with Ping shown - a hundredth of what the F3 chart sends.</li>
 * </ul>
 */
public final class PerformanceStats {
    /** How often the HUD measures the ping itself when nothing else does. */
    static final long PROBE_INTERVAL_MS = Outbound.PROBE_FLOOR_MS;
    /** A ping older than this is too old to show. */
    private static final long PING_STALE_MS = 20_000L;
    /** Answers outside this are from a request timed on another clock, not a real round trip. */
    private static final long MAX_PLAUSIBLE_PING_MS = 30_000L;
    /** The HUD counts as on screen this long after it was last drawn. */
    private static final long SHOWN_GRACE_MS = 1_500L;

    private static final TpsEstimator TPS = new TpsEstimator();

    /** Written on the network thread, where answers are handled; read on the render thread. */
    private static volatile int pingMs = -1;
    private static volatile long lastAnswerMs = Long.MIN_VALUE / 2;
    private static long lastProbeMs = Long.MIN_VALUE / 2;
    private static long lastShownMs = Long.MIN_VALUE / 2;
    /**
     * The time update last counted. The packet is handled twice - first on the network thread, then
     * again on the client thread it is handed to - and must only count once.
     */
    private static volatile Object lastTimePacket = null;

    private PerformanceStats() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        TPS.reset();
        pingMs = -1;
        lastAnswerMs = Long.MIN_VALUE / 2;
        lastTimePacket = null;
    }

    /** From the packet listener, for every time update the server sends. */
    public static void onTimeUpdate(Object packet, long gameTime) {
        if (packet == lastTimePacket) return;
        lastTimePacket = packet;
        TPS.onTimeUpdate(gameTime, Util.getMillis());
    }

    /** From the packet listener, for every answer to a ping request, ours or not. */
    public static void onPong(long requestTimeMs) {
        long now = Util.getMillis();
        long rtt = now - requestTimeMs;
        if (rtt < 0 || rtt > MAX_PLAUSIBLE_PING_MS) return;
        pingMs = (int) rtt;
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

    /** The last round trip in milliseconds, or -1 when there is no recent one. */
    public static int pingMs() {
        return Util.getMillis() - lastAnswerMs > PING_STALE_MS ? -1 : pingMs;
    }

    /** The server's tick rate, or NaN until it is known. */
    public static float tps() {
        return TPS.tps(Util.getMillis());
    }
}
