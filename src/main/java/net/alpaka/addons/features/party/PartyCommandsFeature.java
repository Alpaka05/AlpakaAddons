package net.alpaka.addons.features.party;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.performance.PerformanceStats;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Party commands: a party member writes "!warp", "!tps" or the like into party chat, and the mod
 * offers to answer it.
 *
 * By default a request raises a notification, as a party invite does: Y runs the answer, N or
 * waiting lets it go. The keys are only taken while the notice is up and no screen is open, and a
 * party invite's notice, if one is up as well, answers first.
 *
 * With Auto Answer on, the info and fun commands - the ones that only post a line
 * in party chat - are answered on their own, as Odin and SkyHanni answer them. That is the one
 * place the mod sends because of someone else's chat, so it is off by default, never covers the
 * leader's actions (warp, kick, transfer...), and answers at most once every {@link #AUTO_GAP_MS},
 * a short moment after the request, through {@link Outbound.Cause#PARTY_CHAT_REQUEST}.
 *
 * Which requests are offered at all is set per command in the config; the leader's chores that can
 * hurt a party - kick, transfer, promote - start switched off.
 */
public final class PartyCommandsFeature {

    private PartyCommandsFeature() {
    }

    /** How long a request can still be answered. */
    static final long REQUEST_TTL_MS = 30_000L;

    /** The same request from the same player again this soon is the same request, not a new one. */
    private static final long REPEAT_MS = 3_000L;

    /** As for party invites: long enough to read who asked for what. */
    private static final long MIN_HOLD_MS = 8_000L;

    private static PartyCommand pending = null;
    private static PartyChatCommand pendingRequest = null;
    private static long requestedAtMs = 0L;
    private static long noticeId = 0L;

    /** The least time between two answers sent without confirmation; requests in between are dropped. */
    static final long AUTO_GAP_MS = 2_000L;

    /** How long an answer without confirmation waits, so it does not land in the same tick as the request. */
    private static final long AUTO_DELAY_MS = 300L;

    private static PartyCommand autoCommand = null;
    private static PartyChatCommand autoRequest = null;
    private static long autoSendAtMs = 0L;
    private static long lastAutoAtMs = Long.MIN_VALUE / 2;

    private static String lastKey = null;
    private static long lastKeyAtMs = 0L;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.partyCommandsEnabled;
    }

    public static boolean isCommandEnabled(PartyCommand command) {
        return !AlpakaConfig.instance.partyCommandsDisabled.contains(command.name());
    }

    public static void setCommandEnabled(PartyCommand command, boolean enabled) {
        List<String> disabled = AlpakaConfig.instance.partyCommandsDisabled;
        disabled.remove(command.name());
        if (!enabled) disabled.add(command.name());
        AlpakaConfig.save();
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    /** Whether a command is answered without asking: only one that just posts in party chat. */
    static boolean answersDirectly(PartyCommand command) {
        return AlpakaConfig.instance.partyCommandsAutoAnswer && command.group() != PartyCommand.Group.LEADER;
    }

    static void onChat(Component message) {
        if (!isEnabled()) return;
        for (String line : SkyblockUtils.cleanColor(message.getString()).split("\n")) {
            PartyChatCommand request = PartyChatCommand.parse(line);
            if (request == null) continue;
            PartyCommand command = PartyCommand.byAlias(request.name());
            if (command == null || !isCommandEnabled(command)) continue;
            // Your own "!tps" is answered too when it needs no confirmation; a prompt for it would not be.
            if (answersDirectly(command)) {
                answerLater(command, request);
                return;
            }
            if (isSelf(request.sender())) continue;
            offer(command, request);
            return;
        }
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(name);
    }

    private static void offer(PartyCommand command, PartyChatCommand request) {
        long now = System.currentTimeMillis();
        String key = request.sender().toLowerCase(Locale.ROOT) + " " + command.name() + " " + request.args();
        if (key.equals(lastKey) && now - lastKeyAtMs < REPEAT_MS) return;
        lastKey = key;
        lastKeyAtMs = now;

        if (noticeId != 0L) AlpakaNotifications.dismiss(noticeId);
        pending = command;
        pendingRequest = request;
        requestedAtMs = now;
        String asked = "!" + request.name() + (request.args().isEmpty() ? "" : " " + request.args());
        List<Component> lines = List.of(
                Component.literal(request.sender() + ": ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(asked).withStyle(ChatFormatting.WHITE)),
                Component.literal("[Y]").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" Run    ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("[N]").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" Ignore").withStyle(ChatFormatting.WHITE)));
        long hold = Math.max(AlpakaNotifications.configuredHoldMs(), MIN_HOLD_MS);
        noticeId = AlpakaNotifications.sendLines("Party Command", lines, 0, hold);
    }

    private static void answerLater(PartyCommand command, PartyChatCommand request) {
        long now = System.currentTimeMillis();
        if (autoCommand != null || now - lastAutoAtMs < AUTO_GAP_MS) return;
        lastAutoAtMs = now;
        autoCommand = command;
        autoRequest = request;
        autoSendAtMs = now + AUTO_DELAY_MS;
    }

    private static void tick() {
        if (autoCommand == null || System.currentTimeMillis() < autoSendAtMs) return;
        PartyCommand command = autoCommand;
        PartyChatCommand request = autoRequest;
        autoCommand = null;
        autoRequest = null;
        if (!isEnabled() || !answersDirectly(command)) return;
        String answer = command.answer(request);
        if (answer != null) Outbound.command(answer, Outbound.Cause.PARTY_CHAT_REQUEST);
    }

    /** Whether a request is up and its keys should answer. Clears one that has run out. */
    public static boolean isShowing() {
        if (pending == null) return false;
        if (!isEnabled() || System.currentTimeMillis() - requestedAtMs > REQUEST_TTL_MS
                || !AlpakaNotifications.isShowing(noticeId)) {
            pending = null;
            pendingRequest = null;
            noticeId = 0L;
            return false;
        }
        return true;
    }

    /** A key press with no screen open. True when it was the request's Y or N, which then goes no further. */
    public static boolean onKey(int key, int scancode, boolean repeat) {
        if (!isShowing()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null) return false;
        char pressed = PartyInviteFeature.keyCharacter(key, scancode);
        if (pressed != 'y' && pressed != 'n') return false;
        if (!repeat) {
            if (pressed == 'y') {
                String answer = pending.answer(pendingRequest);
                if (answer != null) Outbound.command(answer, Outbound.Cause.KEY_PRESS);
            }
            dismiss();
        }
        return true;
    }

    public static void dismiss() {
        autoCommand = null;
        autoRequest = null;
        pending = null;
        pendingRequest = null;
        if (noticeId != 0L) {
            AlpakaNotifications.dismiss(noticeId);
            noticeId = 0L;
        }
    }

    /** "!help": the commands that are switched on. */
    static String enabledList() {
        List<String> names = new ArrayList<>();
        for (PartyCommand command : PartyCommand.values()) {
            if (isCommandEnabled(command)) names.add(command.helpName());
        }
        return String.join(" ", names);
    }

    static String currentTps() {
        float tps = PerformanceStats.tps();
        return Float.isNaN(tps) ? "unknown" : String.format(Locale.ROOT, "%.1f", Math.min(tps, 20.0f));
    }

    static String currentPing() {
        int ping = PerformanceStats.pingMs();
        if (ping < 0) {
            Minecraft mc = Minecraft.getInstance();
            var info = mc.getConnection() == null || mc.player == null
                    ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            ping = info == null ? -1 : info.getLatency();
        }
        return ping < 0 ? "unknown" : ping + "ms";
    }

    static String currentFps() {
        return String.valueOf(PerformanceStats.fps());
    }

    static String currentCoords() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "unknown";
        BlockPos pos = mc.player.blockPosition();
        return "x: " + pos.getX() + ", y: " + pos.getY() + ", z: " + pos.getZ();
    }

    /** The zone the sidebar names, as "⏣ Zone" reads there, else the tab list's area. */
    static String currentLocation() {
        for (String line : SkyblockUtils.getSidebarLines()) {
            int marker = line.indexOf('⏣');
            if (marker < 0) marker = line.indexOf('ф');
            if (marker >= 0) return line.substring(marker + 1).trim();
        }
        for (String line : SkyblockUtils.getTabListLines()) {
            if (line.startsWith("Area: ")) return line.substring("Area: ".length()).trim();
        }
        return "unknown";
    }

    static String currentHolding() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "nothing";
        ItemStack held = mc.player.getMainHandItem();
        return held.isEmpty() ? "nothing" : SkyblockUtils.cleanColor(held.getHoverName().getString());
    }

    static String currentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
    }
}
