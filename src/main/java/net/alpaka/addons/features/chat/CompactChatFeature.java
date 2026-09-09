package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Folds a message that repeats right after itself into one line with a counter: "(x2)", "(x3)"...
 *
 * The same idea as CompactChat and Chatting: when a message arrives that is identical to the newest
 * one in the chat, the newest one is taken out and the new arrival goes in with the count appended,
 * so the repeated line sits at the bottom with a single number instead of stacking up. Only
 * back-to-back repeats are folded; a line that comes back after other messages is a new line.
 *
 * ### What is never folded
 *
 * Hypixel frames level-ups, slayer rewards and other events with rows of dashes or "▬" and blank
 * lines. Those repeat by design - the box needs both its edges - so a line made of nothing but
 * separator characters or whitespace is always let through as it is, and it also ends any run of
 * repeats, because it becomes the newest message.
 *
 * ### Identity
 *
 * Two messages are the same when their text is the same and the components are equal as a whole,
 * hover and click events included. Text alone would fold the mod's own screenshot notices, which
 * read identically but each open a different file; comparing the whole component keeps those apart
 * while Hypixel's repeated notices, which are identical down to the hover, still fold.
 *
 * ### Where this runs
 *
 * On the constructor call in ChatComponent.addMessage, after every filter that may cancel a message
 * has had its say, and before the message reaches the stored history or the display lines. The
 * stored message carries the counter, so a chat re-wrap or a tab switch shows it again unchanged.
 */
public final class CompactChatFeature {

    private CompactChatFeature() {
    }

    private static final Pattern COLOR_CODES = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    /** A line that is only separator glyphs: Hypixel's dashes, the thick "▬" rows and their kin. */
    private static final Pattern SEPARATOR = Pattern.compile("^[\\s\\-─═▬■=_*~•·.]+$");

    private static final Style COUNTER_STYLE = Style.EMPTY
            .withColor(ChatFormatting.GRAY)
            .withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);

    /** The message most recently added to the chat, as the instance that sits in its lists. */
    private static GuiMessage lastAdded = null;
    /** That message's content before any counter was appended. */
    private static Component lastOriginal = null;
    private static String lastText = null;
    private static int count = 0;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.compactChatEnabled;
    }

    /**
     * The content to store for an arriving message: the same content, or - when it repeats the
     * newest message - the content with the counter, after the newest message has been taken out of
     * the given lists. {@link #onAdded} must be called with the message that is then constructed.
     */
    public static Component compact(Component content, List<GuiMessage> allMessages, List<GuiMessage.Line> trimmedMessages) {
        if (!isEnabled()) {
            forget();
            return content;
        }
        String text = content.getString();
        if (!isFoldable(text)) {
            forget();
            return content;
        }
        boolean repeat = lastAdded != null
                && text.equals(lastText)
                && content.equals(lastOriginal)
                && !allMessages.isEmpty() && allMessages.get(0) == lastAdded;
        if (repeat) {
            count++;
            removeNewest(allMessages, trimmedMessages);
            return withCounter(content, count);
        }
        count = 1;
        lastOriginal = content;
        lastText = text;
        return content;
    }

    /** Remembers the message that was just constructed from {@link #compact}'s result. */
    public static void onAdded(GuiMessage message) {
        lastAdded = message;
    }

    /** Drops the memory of the newest message, after the chat was cleared. */
    public static void forget() {
        lastAdded = null;
        lastOriginal = null;
        lastText = null;
        count = 0;
    }

    private static boolean isFoldable(String text) {
        String plain = COLOR_CODES.matcher(text).replaceAll("");
        if (plain.isBlank()) return false;
        return !SEPARATOR.matcher(plain).matches();
    }

    /**
     * Takes the newest message out of both lists. Its lines sit at the front of the display list,
     * each pointing back at it, and there may be none if a chat tab was hiding it.
     */
    private static void removeNewest(List<GuiMessage> allMessages, List<GuiMessage.Line> trimmedMessages) {
        allMessages.remove(0);
        while (!trimmedMessages.isEmpty() && trimmedMessages.get(0).parent() == lastAdded) {
            trimmedMessages.remove(0);
        }
    }

    private static Component withCounter(Component content, int count) {
        MutableComponent line = Component.empty();
        line.append(content);
        line.append(Component.literal(" (x" + count + ")").withStyle(COUNTER_STYLE));
        return line;
    }
}
