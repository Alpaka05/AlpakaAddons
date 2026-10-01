package net.alpaka.addons.compliance;

import net.alpaka.addons.AlpakaAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtil;

/**
 * The one place the mod sends anything to the server.
 *
 * Every message or command the mod sends is the direct result of the player pressing a key,
 * clicking, or pressing Enter, and says which through its {@link Cause}. Nothing reacts to other
 * players' chat, timers or game events by sending; two features once did - a party-chat auto-reply
 * and a guild announcement fired from a timer - and were removed. A source scan in the tests fails
 * the build if anything outside this class calls the network directly.
 */
public final class Outbound {
    /** What the player did that sends the command. */
    public enum Cause {
        /** Pressed Enter on a line typed into the chat box. */
        CHAT_INPUT,
        /** Pressed a key, such as Y on a party invite. */
        KEY_PRESS,
        /** Released or clicked a command wheel segment. */
        WHEEL
    }

    /** The same command again this soon is taken as a double input and dropped. */
    private static final long REPEAT_MS = 500L;

    private static String lastCommand = null;
    private static long lastSentAtMs = 0L;

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
}
