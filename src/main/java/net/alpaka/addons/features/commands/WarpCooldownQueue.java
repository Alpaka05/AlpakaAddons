package net.alpaka.addons.features.commands;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warp Cooldown: a warp typed right after joining a server waits for Hypixel's cooldown
 * instead of being refused.
 *
 * Hypixel turns a warp down for the first few seconds on a server ("You may only use this command
 * after 4s on the server!"), which after a server switch means typing it again. Like SkyHanni's
 * Fix Transfer Cooldown and Prevent Early Command Execution, this holds the command and sends it
 * once the wait is over:
 * <ul>
 *   <li>A warp typed within {@link #COOLDOWN_MS} of the world changing is held right away.</li>
 *   <li>Any other command Hypixel refuses with the message above is held for the time the message
 *       names, counted from the world change, and the refusal is hidden.</li>
 * </ul>
 * Only a command the player typed and sent with Enter is ever held, only the latest one, and only
 * once: a command that is refused again after waiting is left as refused. Joining another server or
 * leaving drops whatever is held, since that warp was meant for the server left behind.
 */
public final class WarpCooldownQueue {

    private WarpCooldownQueue() {
    }

    /** How long after a server join Hypixel refuses a warp, as SkyHanni times it. */
    static final long COOLDOWN_MS = 3_000L;

    /** A refusal more than this long after the command was typed is not about that command. */
    private static final long REFUSAL_WINDOW_MS = 3_000L;

    /** The commands that warp; "/warp isle" and the ones Shorten Warp makes as well. */
    private static final Set<String> WARP_COMMANDS = Set.of("warp", "is", "hub", "warpforge", "garden");

    static final Pattern REFUSAL = Pattern.compile(
            "^You may only use this command after (?<seconds>\\d+)s on the server!$");

    private static long worldChangedAtMs = 0L;
    private static String held = null;
    private static long sendAtMs = 0L;
    private static String lastTyped = null;
    private static long lastTypedAtMs = 0L;
    private static long onSkyblockAtMs = Long.MIN_VALUE / 2;

    /** How long after leaving SkyBlock's sidebar a server is still taken for SkyBlock. */
    private static final long SKYBLOCK_GRACE_MS = 30_000L;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.warpCooldownQueueEnabled;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !onRefusal(message));
    }

    /** From the session lifecycle: a new world is a new server, or at least a new wait. */
    public static void onWorldChange() {
        worldChangedAtMs = System.currentTimeMillis();
        held = null;
        lastTyped = null;
    }

    public static void onDisconnect() {
        held = null;
        lastTyped = null;
    }

    /**
     * A command the player just sent with Enter, without its slash. True when it was held, and must
     * not be sent now.
     */
    public static boolean onTyped(String command) {
        long now = System.currentTimeMillis();
        lastTyped = null;
        if (!isEnabled() || !onSkyblock()) return false;
        long readyAt = worldChangedAtMs + COOLDOWN_MS;
        if (isWarp(command) && now < readyAt) {
            hold(command, readyAt, now);
            return true;
        }
        lastTyped = command;
        lastTypedAtMs = now;
        return false;
    }

    static boolean isWarp(String command) {
        String trimmed = command.trim().toLowerCase(Locale.ROOT);
        int space = trimmed.indexOf(' ');
        return WARP_COMMANDS.contains(space < 0 ? trimmed : trimmed.substring(0, space));
    }

    /** Hypixel's refusal for the command typed last: holds it and says so. True hides the refusal. */
    private static boolean onRefusal(Component message) {
        if (lastTyped == null || !isEnabled()) return false;
        Matcher matcher = REFUSAL.matcher(SkyblockUtils.cleanColor(message.getString()).trim());
        if (!matcher.matches()) return false;
        long now = System.currentTimeMillis();
        if (now - lastTypedAtMs > REFUSAL_WINDOW_MS) return false;
        long seconds = Math.min(Long.parseLong(matcher.group("seconds")), 30L);
        // The wait counts from the join; never less than a moment, so the retry is not instant.
        long readyAt = Math.max(worldChangedAtMs + seconds * 1000L, now + 500L);
        String command = lastTyped;
        lastTyped = null;
        hold(command, readyAt, now);
        return true;
    }

    private static void hold(String command, long readyAt, long now) {
        held = command;
        sendAtMs = readyAt;
        SlayerDropTracker.sendModMessage(String.format(Locale.ROOT,
                "§7Hypixel's join cooldown: running §f/%s§7 in %.1fs.", command, (readyAt - now) / 1000.0));
    }

    /**
     * Whether the player is on SkyBlock, or was until a moment ago. Right after a server switch, in
     * just the seconds this is about, the new server has not sent its sidebar yet, and the sidebar
     * is what SkyblockUtils goes by.
     */
    static boolean onSkyblock() {
        return SkyblockUtils.isOnSkyblock()
                || System.currentTimeMillis() - onSkyblockAtMs < SKYBLOCK_GRACE_MS;
    }

    private static void tick() {
        if (SkyblockUtils.isOnSkyblock()) onSkyblockAtMs = System.currentTimeMillis();
        if (held == null || System.currentTimeMillis() < sendAtMs) return;
        String command = held;
        held = null;
        Outbound.command(command, Outbound.Cause.QUEUED_CHAT_INPUT);
    }
}
