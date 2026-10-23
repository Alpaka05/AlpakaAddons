package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
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
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(1500L);

    /** The message under the mouse in the open chat as of the last frame, or null. */
    private static GuiMessage hovered;

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
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(text);
        SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST,
                Component.literal("Message Copied"), Component.literal(shorten(text)));
        return true;
    }

    /**
     * The message as the player reads it: Compact Chat's "(x2)" counter is not part of it, and
     * neither are formatting codes some servers put into the text itself.
     */
    static String plainText(Component content) {
        return ChatTab.strip(CompactChatFeature.withoutCounter(content).getString());
    }

    private static String shorten(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() > 40 ? oneLine.substring(0, 39) + "…" : oneLine;
    }
}
