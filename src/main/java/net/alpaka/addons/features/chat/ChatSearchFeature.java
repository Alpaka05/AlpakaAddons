package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.locale.Language;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Chat search: Ctrl+F in the chat screen - Cmd+F on macOS - turns the input line into a search
 * box. Whatever is typed there filters the chat down to the messages containing it, and the input
 * bar takes the accent colour so it is obvious that typing will not send anything. The same
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
    private static String queryLower = "";
    /** Each message's text, lower-cased, for the current search only (see accepts). */
    private static final Map<GuiMessage, String> LOWERED = new IdentityHashMap<>();
    /** The language LOWERED was filled in; null until the search's first lookup. */
    private static Language loweredIn;
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
        if (!searching || query.isEmpty()) return true;
        // Every keystroke replays the whole history through here, and a stored message's text does
        // not change, so it is flattened and lower-cased once per search instead of once per
        // keystroke. Identity keys skip the record's deep equals. The memo is emptied when the search
        // ends, and when a resource reload swaps the language, since translated parts then read
        // differently. Messages arriving during a long search pass through here too, so the memo is
        // also emptied once it outgrows twice the chat's history length; otherwise it would keep
        // messages the chat has already trimmed away alive until the search ends. Emptying it only
        // means each message is flattened again on the next keystroke.
        Language language = Language.getInstance();
        if (language != loweredIn) {
            LOWERED.clear();
            loweredIn = language;
        }
        if (LOWERED.size() >= 2 * AlpakaConfig.instance.chatHistoryLength()) LOWERED.clear();
        String text = LOWERED.computeIfAbsent(message, m -> m.content().getString().toLowerCase(Locale.ROOT));
        return text.contains(queryLower);
    }

    public static void toggle(EditBox input) {
        if (searching) exit(input);
        else enter(input);
    }

    private static void enter(EditBox input) {
        searching = true;
        query = "";
        queryLower = "";
        LOWERED.clear();
        loweredIn = null;
        if (input != null) {
            draft = input.getValue();
            // setValue fires the input's responder, ChatScreen.onEdited, which ChatScreenMixin hands
            // to onEdited("") now that searching is on - and that already re-lays the chat out.
            // Refreshing again here would replay the whole history a second time for nothing.
            input.setValue("");
            input.setSuggestion(HINT);
        } else {
            refresh();
        }
    }

    /** Leaves the search; the input gets its earlier text back when it is still around. */
    public static void exit(EditBox input) {
        if (!searching) return;
        searching = false;
        query = "";
        queryLower = "";
        LOWERED.clear();
        loweredIn = null;
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
        queryLower = query.toLowerCase(Locale.ROOT);
        if (input != null) input.setSuggestion(query.isEmpty() ? HINT : null);
        refresh();
    }

    /** The input bar's background while searching: the accent, translucent over the world. */
    public static int barColor() {
        return 0x8C000000 | (ModernGuiUtils.getAccentColor() & 0x00FFFFFF);
    }

    private static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.hud.getChat().rescaleChat();
    }
}
