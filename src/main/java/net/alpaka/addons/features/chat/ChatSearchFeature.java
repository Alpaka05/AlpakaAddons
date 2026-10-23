package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.locale.Language;

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
 *
 * Laying the chat out replays the whole history, which takes a while with an expanded one, so the
 * search lays it out a moment after the last key rather than on every key, and only when the query
 * actually changed.
 */
public final class ChatSearchFeature {
    private static final String HINT = "Search chat…";

    /** How long after the last key the chat is laid out for the new query. */
    private static final long LAYOUT_DELAY_MS = 120L;

    private static boolean searching;
    private static String query = "";
    /** The query the chat is laid out for, which trails {@link #query} while typing. */
    private static String laidOutQuery = "";
    /** {@link #laidOutQuery} in lower case, the form every message is compared in. */
    private static String needle = "";
    /** When the chat is due to be laid out for {@link #query}, or 0 when nothing is pending. */
    private static long layoutDueAtMs = 0L;
    /**
     * Each message's text without formatting codes and in lower case, while a search is open. Every
     * search re-lays the whole history out, and flattening and lower-casing thousands of messages each
     * time showed as a hitch. Weak, identity keys; emptied when the search closes, and when a resource
     * reload changes the language, since translated parts then read differently.
     */
    private static final java.util.Map<GuiMessage, String> LOWER_TEXT =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();
    /** The language LOWER_TEXT was filled in. */
    private static Language lowerTextLanguage;
    /** The message that was being typed when the search started, restored on leaving it. */
    private static String draft = "";

    private ChatSearchFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatSearchEnabled;
    }

    public static boolean isSearching() {
        return searching;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    /**
     * Whether the chat should lay this message out; every message while not searching. Compared
     * without formatting codes, which some messages carry in their text: searching for "a" used to
     * find every line with a green "§a" in it.
     */
    public static boolean accepts(GuiMessage message) {
        if (!searching || needle.isEmpty()) return true;
        Language language = Language.getInstance();
        if (language != lowerTextLanguage) {
            LOWER_TEXT.clear();
            lowerTextLanguage = language;
        }
        return LOWER_TEXT.computeIfAbsent(message,
                m -> ChatTab.strip(m.content().getString()).toLowerCase(Locale.ROOT)).contains(needle);
    }

    public static void toggle(EditBox input) {
        if (searching) exit(input);
        else enter(input);
    }

    private static void enter(EditBox input) {
        searching = true;
        query = "";
        laidOutQuery = "";
        needle = "";
        layoutDueAtMs = 0L;
        // No layout: an empty search shows every message, which is what the chat shows already. It
        // used to lay the whole history out twice here, once for clearing the input and once more.
        if (input != null) {
            draft = input.getValue();
            input.setValue("");
            input.setSuggestion(HINT);
        }
    }

    /** Leaves the search; the input gets its earlier text back when it is still around. */
    public static void exit(EditBox input) {
        if (!searching) return;
        searching = false;
        boolean filtered = !laidOutQuery.isEmpty();
        query = "";
        laidOutQuery = "";
        needle = "";
        layoutDueAtMs = 0L;
        LOWER_TEXT.clear();
        if (input != null) {
            input.setSuggestion(null);
            input.setValue(draft);
        }
        draft = "";
        // Only a chat that was filtered needs laying out again to show everything.
        if (filtered) refresh();
    }

    /** The search text changed; the chat follows once typing pauses (see {@link #tick()}). */
    public static void onEdited(String text, EditBox input) {
        query = text == null ? "" : text;
        if (input != null) input.setSuggestion(query.isEmpty() ? HINT : null);
        layoutDueAtMs = System.currentTimeMillis() + LAYOUT_DELAY_MS;
    }

    /** Lays the chat out for the query once no key has come for {@link #LAYOUT_DELAY_MS}. */
    private static void tick() {
        if (layoutDueAtMs == 0L || System.currentTimeMillis() < layoutDueAtMs) return;
        layoutDueAtMs = 0L;
        if (!searching || query.equals(laidOutQuery)) return;
        laidOutQuery = query;
        needle = query.toLowerCase(Locale.ROOT);
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
