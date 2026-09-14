package net.alpaka.addons.features.party;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Y/N notice for Hypixel party invites.
 *
 * Hypixel announces an invite in chat and offers a clickable line to accept it, which means
 * opening the chat, finding the line and clicking it before the sixty seconds run out. With this on,
 * the invite raises one of the mod's notifications instead: Y sends {@code /party accept <name>}
 * for the player who invited, N takes the notice away and nothing else happens. Both keys are only
 * taken while the notice is up and no screen is open, so they keep their normal meaning the rest of
 * the time - and once the notice has slid out, the chat line still works as it always did.
 *
 * The notice also goes away on its own when Hypixel says the invite expired or the player joined a
 * party by other means, so a stale one can never send an accept for an invite that is gone.
 *
 * The chat patterns follow SkyHanni's AcceptLastPartyInvite: with the colour codes stripped, the
 * invite line reads {@code [RANK] Name has invited you to join their party!} and the expiry
 * {@code The party invite from [RANK] Name has expired.}
 */
public final class PartyInviteFeature {

    private PartyInviteFeature() {
    }

    private static final Pattern INVITE = Pattern.compile(
            "(?:\\[[^\\]]*\\] )?(?<player>[A-Za-z0-9_]{1,16}) has invited you to join their party!");
    private static final Pattern EXPIRED = Pattern.compile(
            "The party invite from (?:\\[[^\\]]*\\] )?(?<player>[A-Za-z0-9_]{1,16}) has expired");
    private static final Pattern JOINED = Pattern.compile(
            "You have joined (?:\\[[^\\]]*\\] )?(?<player>[A-Za-z0-9_]{1,16})'s? party!");

    /** How long Hypixel keeps an invite open. */
    public static final long INVITE_TTL_MS = 60_000L;

    /**
     * The notice stays at least this long, whatever the notification duration is set to, so there
     * is time to read the name and reach for Y. A longer configured duration still applies.
     */
    private static final long MIN_HOLD_MS = 8_000L;

    private static String inviter = null;
    private static long invitedAtMs = 0L;
    private static long noticeId = 0L;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.partyInvitePromptEnabled;
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
    }

    static void onChat(Component message) {
        if (!isEnabled()) return;
        String text = SkyblockUtils.cleanColor(message.getString());

        Matcher invite = INVITE.matcher(text);
        if (invite.find()) {
            show(invite.group("player"));
            return;
        }
        if (inviter == null) return;

        Matcher expired = EXPIRED.matcher(text);
        if (expired.find() && expired.group("player").equalsIgnoreCase(inviter)) {
            dismiss();
            return;
        }
        if (JOINED.matcher(text).find()) {
            dismiss();
        }
    }

    private static void show(String name) {
        if (noticeId != 0L) {
            AlpakaNotifications.dismiss(noticeId);
        }
        inviter = name;
        invitedAtMs = System.currentTimeMillis();
        List<Component> lines = List.of(
                Component.literal(name + " invited you"),
                Component.literal("[Y]").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" Join    ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("[N]").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" Decline").withStyle(ChatFormatting.WHITE)));
        long hold = Math.max(AlpakaNotifications.configuredHoldMs(), MIN_HOLD_MS);
        noticeId = AlpakaNotifications.sendLines("Party Invite", lines, 0, hold);
    }

    /** Whether the notice is up and its keys should answer. Clears an invite that has run out. */
    public static boolean isShowing() {
        if (inviter == null) return false;
        if (!isEnabled() || System.currentTimeMillis() - invitedAtMs > INVITE_TTL_MS
                || !AlpakaNotifications.isShowing(noticeId)) {
            inviter = null;
            noticeId = 0L;
            return false;
        }
        return true;
    }

    /**
     * A key press with no screen open. True when the key was the notice's Y or N, which then goes
     * no further.
     *
     * The key is identified by the character it produces on the player's keyboard layout, not by
     * its GLFW code: GLFW codes name positions on a US keyboard, so on a German QWERTZ keyboard the
     * key labelled Y arrives as {@code GLFW_KEY_Z}. The notice says Y, so the key that says Y is the
     * one that joins.
     */
    public static boolean onKey(int key, int scancode, boolean repeat) {
        if (!isShowing()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return false;
        char pressed = keyCharacter(key, scancode);
        if (pressed != 'y' && pressed != 'n') return false;
        // A held key's repeats are taken as well, so nothing else sees them, but act only once.
        if (!repeat) {
            if (pressed == 'y') {
                mc.player.connection.sendCommand("party accept " + inviter);
            }
            dismiss();
        }
        return true;
    }

    /** The lower-case character a key produces on the current layout, or 0 when it has none. */
    private static char keyCharacter(int key, int scancode) {
        String name = GLFW.glfwGetKeyName(key, scancode);
        if (name != null && name.length() == 1) {
            return Character.toLowerCase(name.charAt(0));
        }
        // No layout name (some keyboards, some platforms): fall back to the US position.
        if (key == GLFW.GLFW_KEY_Y) return 'y';
        if (key == GLFW.GLFW_KEY_N) return 'n';
        return 0;
    }

    public static void dismiss() {
        inviter = null;
        if (noticeId != 0L) {
            AlpakaNotifications.dismiss(noticeId);
            noticeId = 0L;
        }
    }
}
