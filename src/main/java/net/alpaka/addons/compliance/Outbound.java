package net.alpaka.addons.compliance;

import net.alpaka.addons.AlpakaAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;

/**
 * The one place the mod sends anything to the server.
 *
 * Every message or command the mod sends is the direct result of the player pressing a key,
 * clicking, or pressing Enter, and says which through its {@link Cause}. Nothing reacts to other
 * players' chat, timers or game events by sending; two features once did - a party-chat auto-reply
 * and a guild announcement fired from a timer - and were removed. A scan of the compiled classes in
 * the tests (OutboundBytecodeScanTest) fails the build if anything outside this class sends to the
 * server, apart from the resource pack answers of the pack cache.
 *
 * The one exception is {@link #latencyProbe()}: not a message or command but vanilla's own ping
 * request, which nobody else sees and which the server answers without acting on it. It goes out on
 * a timer because a ping has no other way to be measured, and at most once every
 * {@link #PROBE_FLOOR_MS} ms, enforced here so no caller can send more.
 */
public final class Outbound {
    /** What the player did that sends the command. */
    public enum Cause {
        /** Pressed Enter on a line typed into the chat box. */
        CHAT_INPUT,
        /** Pressed a key, such as Y on a party invite. */
        KEY_PRESS,
        /** Released or clicked a command wheel segment. */
        WHEEL,
        /** Clicked a slot in a menu, such as a garden visitor's Accept Offer. */
        CLICK
    }

    /** The same command again this soon is taken as a double input and dropped. */
    private static final long REPEAT_MS = 500L;

    private static String lastCommand = null;
    private static long lastSentAtMs = 0L;

    /** The least time between two latency probes, whoever asks. */
    public static final long PROBE_FLOOR_MS = 5_000L;
    private static long lastProbeAtMs = Long.MIN_VALUE / 2;

    private Outbound() {}

    /**
     * Sends a command, without its leading slash, for an action the player just took. Returns
     * whether it went out. Characters chat does not allow - formatting codes, control characters -
     * are removed first, so a hand-edited config cannot slip them in.
     */
    public static boolean command(String command, Cause cause) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null || command == null) return false;
        String cleaned = StringUtil.filterText(command).trim();
        if (cleaned.isEmpty()) return false;

        long now = System.currentTimeMillis();
        if (cleaned.equals(lastCommand) && now - lastSentAtMs < REPEAT_MS) return false;
        lastCommand = cleaned;
        lastSentAtMs = now;

        AlpakaAddons.LOGGER.debug("Sending /{} ({})", cleaned, cause);
        mc.player.connection.sendCommand(cleaned);
        return true;
    }

    /**
     * Sends one ping request, the packet vanilla's F3 network chart sends every tick, for the
     * Performance HUD's ping. Returns whether it went out; a call within {@link #PROBE_FLOOR_MS} of
     * the last one is dropped.
     */
    public static boolean latencyProbe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;
        long now = Util.getMillis();
        if (now - lastProbeAtMs < PROBE_FLOOR_MS) return false;
        lastProbeAtMs = now;
        mc.player.connection.send(new ServerboundPingRequestPacket(now));
        return true;
    }
}
