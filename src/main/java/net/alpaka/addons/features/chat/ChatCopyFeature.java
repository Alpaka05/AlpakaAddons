package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

/**
 * Copy Chat: with the chat open, right-clicking a message puts its text on the clipboard - the
 * whole message, every line of it when it wraps, without formatting codes.
 *
 * The chat finds the message under the mouse while it draws its lines (ChatComponentMixin), where
 * the scroll, the chat's scale and Movable Chat are all already applied, so the click only has to
 * look it up. Only the clipboard is touched; nothing goes to the server.
 */
public final class ChatCopyFeature {
    /** The copy notice is a quick confirmation, shorter than the configured hold for news. */
    private static final long NOTICE_HOLD_MS = 2500L;

    /** The message under the mouse in the open chat as of the last frame, or null. */
    private static GuiMessage hovered;
    /** The last copy's notice, replaced by the next one so a few copies in a row don't stack up. */
    private static long noticeId;

    private ChatCopyFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatCopyEnabled;
    }

    public static void setHovered(GuiMessage message) {
        hovered = message;
    }

    /** A click in the chat screen; true when it copied a message and is used up. */
    public static boolean handleClick(int button) {
        if (button != 1 || !isEnabled() || hovered == null) return false;
        String text = plainText(hovered.content());
        if (text.isEmpty()) return false;
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        AlpakaNotifications.dismiss(noticeId);
        noticeId = AlpakaNotifications.send("Message Copied", text.replace('\n', ' '), 0, NOTICE_HOLD_MS);
        return true;
    }

    /**
     * The message as the player reads it: Compact Chat's "(x2)" counter is not part of it, and
     * neither are formatting codes some servers put into the text itself.
     */
    static String plainText(Component content) {
        return ChatTab.strip(CompactChatFeature.withoutCounter(content).getString());
    }
}
