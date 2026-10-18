package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.Locale;

/**
 * Chat search: Ctrl+F in the chat screen - Cmd+F on macOS - turns the input line into a search
 * box. Whatever is typed there filters the chat down to the messages containing it, and the input
 * bar takes the accent color so it is obvious that typing will not send anything. The same
 * shortcut again, or closing the chat,
 * puts the message that was being typed back and shows the whole chat again.
 *
 * The filter rides on the same hook as the chat tabs: the chat lays its stored messages out into
 * visible lines through one method, and a message this filter rejects is simply not laid out. The
 * stored history is untouched, so leaving the search brings every message back.
 */
public final class ChatSearchFeature {
    private static final String HINT = "Search chat…";

    private static boolean searching;
    private static String query = "";
    /** {@link #query} in lower case, the form every message is compared in. */
    private static String needle = "";
    /**
     * Each message's text in lower case, while a search is open. Every key typed re-lays the whole
     * history out, and flattening and lower-casing thousands of messages each time showed as a hitch
     * per key. Weak, identity keys, and emptied when the search closes.
     */
    private static final java.util.Map<GuiMessage, String> LOWER_TEXT =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();
    /** The message that was being typed when the search started, restored on leaving it. */
    private static String draft = "";

    private ChatSearchFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatSearchEnabled;
    }

    public static boolean isSearching() {
        return searching;
    }

    /** Whether the chat should lay this message out; every message while not searching. */
    public static boolean accepts(GuiMessage message) {
        if (!searching || needle.isEmpty()) return true;
        return LOWER_TEXT.computeIfAbsent(message, m -> m.content().getString().toLowerCase(Locale.ROOT)).contains(needle);
    }

    public static void toggle(EditBox input) {
        if (searching) exit(input);
        else enter(input);
    }

    private static void enter(EditBox input) {
        searching = true;
        query = "";
        needle = "";
        if (input != null) {
            draft = input.getValue();
            input.setValue("");
            input.setSuggestion(HINT);
        }
        refresh();
    }

    /** Leaves the search; the input gets its earlier text back when it is still around. */
    public static void exit(EditBox input) {
        if (!searching) return;
        searching = false;
        query = "";
        needle = "";
        LOWER_TEXT.clear();
        if (input != null) {
            input.setSuggestion(null);
            input.setValue(draft);
        }
        draft = "";
        refresh();
    }

    /** The search text changed. */
    public static void onEdited(String text, EditBox input) {
        query = text == null ? "" : text;
        needle = query.toLowerCase(Locale.ROOT);
        if (input != null) input.setSuggestion(query.isEmpty() ? HINT : null);
        refresh();
    }

    /** The input bar's background while searching: the accent, translucent over the world. */
    public static int barColor() {
        return 0x8C000000 | (ModernGuiUtils.getAccentColor() & 0x00FFFFFF);
    }

    private static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.getChat().rescaleChat();
    }
}
