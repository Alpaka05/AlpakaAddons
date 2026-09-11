package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Folds a message that repeats an earlier one into one line with a counter: "(x2)", "(x3)"...
 *
 * The same idea as CompactChat and Chatting: when a message arrives that is identical to one seen a
 * moment ago, that earlier copy is taken out of the chat and the new arrival goes in at the bottom
 * with the count appended, so the repeated line sits at the bottom with a single number instead of
 * piling up. Other messages may come in between - the earlier copy is simply lifted out from where
 * it sits - as long as it was added no longer ago than the Compact Chat Window allows. The window is
 * measured from the last identical message, so a line that keeps repeating keeps stacking for as
 * long as the gaps stay inside it. A window of zero folds back-to-back repeats only, which is how the
 * feature used to behave.
 *
 * ### What is never folded
 *
 * Hypixel frames level-ups, slayer rewards and other events with rows of dashes or "▬" and blank
 * lines. Those repeat by design - the box needs both its edges - so a line made of nothing but
 * separator characters or whitespace is always let through as it is.
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

    /** How many recent messages are remembered as candidates, whatever the window says. */
    private static final int MAX_REMEMBERED = 64;

    /**
     * A message that may still be folded into. {@code message} is the instance that sits in the chat's
     * lists, {@code original} its content before any counter, {@code addedAt} the wall-clock time the
     * newest copy went in.
     */
    private record Entry(GuiMessage message, Component original, String text, int count, long addedAt) {
    }

    /** Recent foldable messages, newest first. */
    private static final ArrayDeque<Entry> recent = new ArrayDeque<>();
    /** The entry for the message {@link #compact} just prepared, completed by {@link #onAdded}. */
    private static Entry pending = null;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.compactChatEnabled;
    }

    /** The window in milliseconds; zero means back-to-back repeats only. */
    private static long windowMillis() {
        return Math.max(0, AlpakaConfig.instance.compactChatWindowSeconds) * 1000L;
    }

    /**
     * The content to store for an arriving message: the same content, or - when it repeats a recent
     * message inside the window - the content with the counter, after that earlier copy has been
     * taken out of the given lists. {@link #onAdded} must be called with the message that is then
     * constructed.
     */
    public static Component compact(Component content, List<GuiMessage> allMessages, List<GuiMessage.Line> trimmedMessages) {
        pending = null;
        if (!isEnabled()) {
            forget();
            return content;
        }
        String text = content.getString();
        if (!isFoldable(text)) {
            return content;
        }
        long now = System.currentTimeMillis();
        long window = windowMillis();
        prune(now, window);

        int count = 1;
        Entry match = find(content, text, window, allMessages);
        if (match != null) {
            recent.remove(match);
            if (remove(match.message, allMessages, trimmedMessages)) {
                count = match.count + 1;
            }
        }
        pending = new Entry(null, content, text, count, now);
        return count > 1 ? withCounter(content, count) : content;
    }

    /** Remembers the message that was just constructed from {@link #compact}'s result. */
    public static void onAdded(GuiMessage message) {
        if (pending == null) return;
        recent.addFirst(new Entry(message, pending.original, pending.text, pending.count, pending.addedAt));
        while (recent.size() > MAX_REMEMBERED) {
            recent.removeLast();
        }
        pending = null;
    }

    /** Drops every remembered message, after the chat was cleared. */
    public static void forget() {
        recent.clear();
        pending = null;
    }

    private static boolean isFoldable(String text) {
        String plain = COLOR_CODES.matcher(text).replaceAll("");
        if (plain.isBlank()) return false;
        return !SEPARATOR.matcher(plain).matches();
    }

    /**
     * The remembered message this content repeats, if any. With a window it is the newest identical
     * message added inside the window; without one it has to be the newest message in the chat.
     */
    private static Entry find(Component content, String text, long window, List<GuiMessage> allMessages) {
        GuiMessage newest = allMessages.isEmpty() ? null : allMessages.get(0);
        for (Entry entry : recent) {
            if (!entry.text.equals(text) || !entry.original.equals(content)) continue;
            if (window == 0) {
                return entry.message == newest ? entry : null;
            }
            return entry;
        }
        return null;
    }

    /** Forgets entries that fell out of the window, so a stale line is never folded into. */
    private static void prune(long now, long window) {
        if (window == 0) return;
        Iterator<Entry> it = recent.iterator();
        while (it.hasNext()) {
            if (now - it.next().addedAt > window) it.remove();
        }
    }

    /**
     * Takes the given message out of both lists, wherever it sits: its lines each point back at it,
     * and there may be none if a chat tab was hiding it. Returns false when the message was already
     * gone, in which case there is nothing to fold into.
     */
    private static boolean remove(GuiMessage message, List<GuiMessage> allMessages, List<GuiMessage.Line> trimmedMessages) {
        boolean removed = false;
        Iterator<GuiMessage> it = allMessages.iterator();
        while (it.hasNext()) {
            if (it.next() == message) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (removed) {
            trimmedMessages.removeIf(line -> line.parent() == message);
        }
        return removed;
    }

    private static Component withCounter(Component content, int count) {
        MutableComponent line = Component.empty();
        line.append(content);
        line.append(Component.literal(" (x" + count + ")").withStyle(COUNTER_STYLE));
        return line;
    }
}
