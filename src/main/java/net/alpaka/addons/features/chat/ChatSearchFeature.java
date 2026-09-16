package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.Locale;

/**
 * Chat search: Ctrl+F in the chat screen turns the input line into a search box. Whatever is typed
 * there filters the chat down to the messages containing it, and the input bar takes the accent
 * colour so it is obvious that typing will not send anything. Ctrl+F again, or closing the chat,
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
        return message.content().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    public static void toggle(EditBox input) {
        if (searching) exit(input);
        else enter(input);
    }

    private static void enter(EditBox input) {
        searching = true;
        query = "";
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
