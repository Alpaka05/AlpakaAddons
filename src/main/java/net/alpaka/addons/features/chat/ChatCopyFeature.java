package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Copy Chat: with the chat open, right-clicking a message puts its text on the clipboard - the
 * whole message, every line of it when it wraps, without formatting codes. Shift+right-click copies
 * it with its colors and styles as § codes, the way Hypixel and the mod's own text write them.
 *
 * The chat finds the message under the mouse while it draws its lines (ChatComponentMixin), where
 * the scroll, the chat's scale and Movable Chat are all already applied, so the click only has to
 * look it up. Only the clipboard is touched; nothing goes to the server.
 */
public final class ChatCopyFeature {
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
    public static boolean handleClick(int button, boolean withCodes) {
        if (button != 1 || !isEnabled() || hovered == null) return false;
        String plain = plainText(hovered.content());
        if (plain.isEmpty()) return false;
        Minecraft.getInstance().keyboardHandler.setClipboard(withCodes ? codedText(hovered.content()) : plain);
        AlpakaNotifications.dismiss(noticeId);
        // The notice shows the plain text either way: codes would only be clutter in it.
        noticeId = AlpakaNotifications.send(withCodes ? "Message Copied With Colors" : "Message Copied",
                plain.replace('\n', ' '));
        return true;
    }

    /**
     * The message as the player reads it: Compact Chat's "(x2)" counter is not part of it, and
     * neither are formatting codes some servers put into the text itself.
     */
    static String plainText(Component content) {
        return ChatTab.strip(CompactChatFeature.withoutCounter(content).getString());
    }

    /**
     * The message with § codes: every run of text gets the codes for its style wherever the style
     * changes. A color code clears bold and the rest in the legacy format, so a run's color comes
     * first and its styles after; a run without a color starts with §r. Codes already sitting in
     * the text - Hypixel sends many that way - pass through as they are. A color outside the
     * sixteen legacy ones, like the mod's gradients, takes the nearest of them.
     */
    static String codedText(Component content) {
        StringBuilder out = new StringBuilder();
        Style[] previous = {Style.EMPTY};
        CompactChatFeature.withoutCounter(content).visit((style, text) -> {
            if (text.isEmpty()) return java.util.Optional.empty();
            if (!sameLook(style, previous[0])) {
                out.append(codes(style));
                previous[0] = style;
            }
            out.append(text);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out.toString().trim();
    }

    private static boolean sameLook(Style a, Style b) {
        return java.util.Objects.equals(a.getColor(), b.getColor())
                && a.isBold() == b.isBold() && a.isItalic() == b.isItalic()
                && a.isUnderlined() == b.isUnderlined() && a.isStrikethrough() == b.isStrikethrough()
                && a.isObfuscated() == b.isObfuscated();
    }

    private static String codes(Style style) {
        StringBuilder codes = new StringBuilder();
        TextColor color = style.getColor();
        codes.append(color == null ? ChatFormatting.RESET : nearestLegacy(color));
        if (style.isObfuscated()) codes.append(ChatFormatting.OBFUSCATED);
        if (style.isBold()) codes.append(ChatFormatting.BOLD);
        if (style.isStrikethrough()) codes.append(ChatFormatting.STRIKETHROUGH);
        if (style.isUnderlined()) codes.append(ChatFormatting.UNDERLINE);
        if (style.isItalic()) codes.append(ChatFormatting.ITALIC);
        return codes.toString();
    }

    private static ChatFormatting nearestLegacy(TextColor color) {
        int rgb = color.getValue();
        ChatFormatting best = ChatFormatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (ChatFormatting format : ChatFormatting.values()) {
            // The sixteen colors come first in the enum, white last; after them the styles.
            if (format.ordinal() > ChatFormatting.WHITE.ordinal()) continue;
            int other = TextColor.fromLegacyFormat(format).getValue();
            if (other == rgb) return format;
            long dr = ((rgb >> 16) & 0xFF) - ((other >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((other >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (other & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = format;
            }
        }
        return best;
    }
}
