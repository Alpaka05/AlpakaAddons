package net.alpaka.addons.features.party;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Y/N prompt for Hypixel party invites.
 *
 * Hypixel announces an invite in chat and offers a clickable line to accept it, which means
 * opening the chat, finding the line and clicking it before the sixty seconds run out. With this on,
 * the invite puts a prompt on the HUD instead: Y sends {@code /party accept <name>} for the player
 * who invited, N takes the prompt away and nothing else happens. Both keys are only taken while the
 * prompt is up and no screen is open, so they keep their normal meaning the rest of the time.
 *
 * The prompt also goes away on its own when Hypixel says the invite expired, when the player joined
 * a party by other means (clicking the line, or typing the command), or after the sixty seconds an
 * invite lives for, so a stale prompt can never send an accept for an invite that is gone.
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

    private static String inviter = null;
    private static long invitedAtMs = 0L;

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
            inviter = invite.group("player");
            invitedAtMs = System.currentTimeMillis();
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

    /** Whether a prompt should be on screen right now. Clears an invite that has run out. */
    public static boolean isShowing() {
        if (inviter == null) return false;
        if (!isEnabled() || System.currentTimeMillis() - invitedAtMs > INVITE_TTL_MS) {
            inviter = null;
            return false;
        }
        return true;
    }

    /** The name of the player who invited, while a prompt is showing. */
    public static String inviterName() {
        return inviter;
    }

    /** How much of the invite's lifetime is left, 1 fresh to 0 gone. */
    public static float remainingFraction() {
        if (inviter == null) return 0.0f;
        long left = INVITE_TTL_MS - (System.currentTimeMillis() - invitedAtMs);
        return Math.max(0.0f, Math.min(1.0f, left / (float) INVITE_TTL_MS));
    }

    /**
     * A key press with no screen open. True when the key was the prompt's Y or N, which then goes
     * no further.
     *
     * The key is identified by the character it produces on the player's keyboard layout, not by
     * its GLFW code: GLFW codes name positions on a US keyboard, so on a German QWERTZ keyboard the
     * key labelled Y arrives as {@code GLFW_KEY_Z}. The prompt says Y, so the key that says Y is the
     * one that joins.
     */
    public static boolean onKey(int key, int scancode) {
        if (!isShowing()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return false;
        char pressed = keyCharacter(key, scancode);
        if (pressed == 'y') {
            mc.player.connection.sendCommand("party accept " + inviter);
            dismiss();
            return true;
        }
        if (pressed == 'n') {
            dismiss();
            return true;
        }
        return false;
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
    }
}
