package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

/**
 * All / Party / Guild / Co-op / PMs tabs in the chat screen.
 *
 * The tabs sit in the gap between the lowest chat line and the input box, where vanilla draws
 * nothing. Picking one narrows the chat to that channel: the filter runs where messages are laid
 * out into lines, so the stored history stays complete and switching back shows everything again,
 * including what arrived while another tab was up. A channel tab that received messages while it
 * was not the active one shows how many, until it is opened.
 *
 * Closing the chat goes back to All, unless "Keep Tab After Closing Chat" is on; then the HUD chat
 * stays filtered too, and a chip where the tab row sits says so. It used to stay filtered silently,
 * hiding slayer and drop lines with nothing on screen to explain where they went. The mod's own
 * lines and party invites show on every tab.
 *
 * Optionally a plain message typed on a channel tab is sent to that channel, with /pc, /gc or /r in
 * front. That is its own toggle and off by default, so a tab is only ever a view unless asked for.
 *
 * Each channel tab can be left out of the row, for a player with no use for, say, Co-op. A hidden
 * channel's lines are not lost: they sort to it as before and so still show on All.
 */
public final class ChatTabsFeature {

    private ChatTabsFeature() {
    }

    private static ChatTab active = ChatTab.ALL;
    private static final int[] unread = new int[ChatTab.values().length];

    // Layout, in GUI pixels. The input box starts at height - 14; the tabs end a pixel above it. The
    // lowest chat line ends at height - 40, so the row never touches the messages either.
    private static final int TAB_HEIGHT = 12;
    private static final int TAB_PAD = 5;
    private static final int TAB_GAP = 2;
    private static final int LEFT = 2;
    private static final int BOTTOM_GAP = 15;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatTabsEnabled;
    }

    /** Whether a tab is in the row. All always is, so there is always somewhere to go back to. */
    public static boolean isShown(ChatTab tab) {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return switch (tab) {
            case ALL -> true;
            case PARTY -> cfg.chatTabsShowParty;
            case GUILD -> cfg.chatTabsShowGuild;
            case COOP -> cfg.chatTabsShowCoop;
            case PRIVATE -> cfg.chatTabsShowPrivate;
        };
    }

    /** A tab was taken out of the row or put back: off a tab that is gone, back to All. */
    public static void onShownTabsChanged() {
        if (!isShown(active)) select(ChatTab.ALL);
        for (ChatTab tab : ChatTab.values()) {
            if (!isShown(tab)) unread[tab.ordinal()] = 0;
        }
    }

    /**
     * Whether a message may be laid out into visible lines under the current tab.
     *
     * Asked with the message as Hypixel sent it, before the mod's own display formatting, which is
     * what keeps the guild tab working alongside the custom guild tag.
     */
    public static boolean accepts(GuiMessage message) {
        if (!isEnabled() || active == ChatTab.ALL) return true;
        // The mod's own lines, and every other client-side one, are not exempt: they belong to no
        // channel, so they sort to All like any other unclaimed line rather than following the
        // player onto every tab.
        String text = ChatTab.strip(message.content().getString());
        if (ChatTab.isUrgent(text)) return true;
        return ChatTab.classify(text) == active;
    }

    /** The chat screen closed: back to All, unless the player keeps the tab. */
    public static void onChatClosed() {
        if (!isEnabled() || AlpakaConfig.instance.chatTabsKeepAfterClose) return;
        if (active != ChatTab.ALL) select(ChatTab.ALL);
    }

    /** Counts a newly arrived message for the channel tab it belongs to, if that tab is not up. */
    public static void onMessage(Component content) {
        if (!isEnabled()) return;
        ChatTab tab = ChatTab.classify(content);
        if (tab != ChatTab.ALL && tab != active && isShown(tab)) {
            unread[tab.ordinal()]++;
        }
    }

    public static void select(ChatTab tab) {
        unread[tab.ordinal()] = 0;
        if (tab == active) return;
        active = tab;
        // Re-lays the history out under the new filter and jumps back to the newest line.
        Minecraft.getInstance().gui.hud.getChat().rescaleChat();
    }

    /** Moves to the next shown tab, or the previous one, wrapping around at either end. */
    public static void cycle(boolean backwards) {
        ChatTab[] tabs = ChatTab.values();
        int step = backwards ? tabs.length - 1 : 1;
        ChatTab next = active;
        // Ends at All at the latest, which is always shown.
        do {
            next = tabs[(next.ordinal() + step) % tabs.length];
        } while (!isShown(next));
        select(next);
    }

    /** Whether the tab row shows while peeking and Tab cycles the tabs. */
    public static boolean tabKeyEnabled() {
        return isEnabled() && AlpakaConfig.instance.chatTabsTabKey;
    }

    /**
     * A key press in the chat screen. True when it cycled the tabs, which then goes no further.
     * Ctrl+Tab always cycles, Ctrl+Shift+Tab backwards. Plain Tab only does on an empty line: with
     * text typed it is vanilla's player-name completion, which it used to take over.
     */
    public static boolean onChatScreenKey(int key, boolean shift, boolean control, boolean suggestionsVisible, String input) {
        if (!tabKeyEnabled() || key != GLFW.GLFW_KEY_TAB) return false;
        if (!control && (suggestionsVisible || !input.isBlank())) return false;
        cycle(shift);
        return true;
    }

    /**
     * A key press outside the chat screen. True when it was Tab while peeking, which cycles the
     * tabs on a press and is merely swallowed on a repeat, so a held Tab does not race through them.
     */
    public static boolean onPeekKey(int key, boolean shift, boolean repeat) {
        if (!tabKeyEnabled() || key != GLFW.GLFW_KEY_TAB || !ChatPeekFeature.isPeeking()) return false;
        if (!repeat) cycle(shift);
        releasePlayerListKey();
        return true;
    }

    /**
     * Whether the player list should stay hidden because Tab means "next chat tab" right now: in
     * the chat screen, and while the chat is held open with Peek Chat.
     */
    public static boolean suppressesTabList() {
        if (!tabKeyEnabled()) return false;
        return Minecraft.getInstance().gui.screen() instanceof ChatScreen || ChatPeekFeature.isPeeking();
    }

    /**
     * Marks the player list key as not held. Vanilla's own list is cancelled at its hook, but
     * other mods draw a tab HUD of their own off this very mapping, so it is kept released for as
     * long as Tab means "next chat tab".
     */
    public static void releasePlayerListKey() {
        Minecraft.getInstance().options.keyPlayerList.setDown(false);
    }

    /**
     * With the tab kept after closing the chat, a chip where the tab row sits says which channel the
     * HUD chat is showing, so a filtered chat never looks like an empty one.
     */
    public static void renderFilterChip(GuiGraphicsExtractor graphics) {
        if (!isEnabled() || active == ChatTab.ALL || ChatPeekFeature.isPeeking()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ChatScreen) return;
        Font font = mc.font;
        Component label = Component.literal(active.label + " only");
        int y1 = graphics.guiHeight() - BOTTOM_GAP;
        int y0 = y1 - TAB_HEIGHT;
        int width = font.width(label) + TAB_PAD * 2;
        int accent = ModernGuiUtils.getAccentColor();
        graphics.fill(LEFT, y0, LEFT + width, y1, mc.options.getBackgroundColor(Integer.MIN_VALUE));
        graphics.fill(LEFT, y0, LEFT + width, y1, (accent & 0xFFFFFF) | 0x30000000);
        graphics.fill(LEFT, y1 - 1, LEFT + width, y1, accent);
        graphics.text(font, label, LEFT + TAB_PAD, y0 + 2, 0xFFFFFFFF);
    }

    /** The tab row under the held-open chat, without hover since there is no cursor on it. */
    public static void renderWhilePeeking(GuiGraphicsExtractor graphics) {
        if (!tabKeyEnabled() || !ChatPeekFeature.isPeeking()) return;
        releasePlayerListKey();
        render(graphics, graphics.guiHeight(), -1, -1);
    }

    /**
     * The command a plain message should go out as on the current tab, or null to send it as typed.
     * Only with the send-to-channel toggle on; commands and empty lines are always left alone.
     */
    public static String channelCommand(String normalizedMessage) {
        if (!isEnabled() || !AlpakaConfig.instance.chatTabsSendToChannel) return null;
        if (normalizedMessage.isEmpty() || normalizedMessage.startsWith("/")) return null;
        return switch (active) {
            case PARTY -> "pc " + normalizedMessage;
            case GUILD -> "gc " + normalizedMessage;
            case COOP -> "cc " + normalizedMessage;
            case PRIVATE -> "r " + normalizedMessage;
            default -> null;
        };
    }

    private static Component labelOf(ChatTab tab) {
        MutableComponent label = Component.literal(tab.label);
        int count = unread[tab.ordinal()];
        if (count > 0) {
            label.append(Component.literal(" " + (count > 99 ? "99+" : String.valueOf(count))).withStyle(ChatFormatting.YELLOW));
        }
        return label;
    }

    /** Draws the row. Called from the chat screen before the command suggestions, so those stay on top. */
    public static void render(GuiGraphicsExtractor graphics, int screenHeight, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int y1 = screenHeight - BOTTOM_GAP;
        int y0 = y1 - TAB_HEIGHT;
        int background = mc.options.getBackgroundColor(Integer.MIN_VALUE);
        int accent = ModernGuiUtils.getAccentColor();
        int x = LEFT;
        for (ChatTab tab : ChatTab.values()) {
            if (!isShown(tab)) continue;
            Component label = labelOf(tab);
            int width = font.width(label) + TAB_PAD * 2;
            boolean selected = tab == active;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y0 && mouseY < y1;

            graphics.fill(x, y0, x + width, y1, background);
            if (selected) {
                graphics.fill(x, y0, x + width, y1, (accent & 0xFFFFFF) | 0x30000000);
                graphics.fill(x, y1 - 1, x + width, y1, accent);
            } else if (hovered) {
                graphics.fill(x, y0, x + width, y1, 0x20FFFFFF);
            }
            int color = selected ? 0xFFFFFFFF : hovered ? 0xFFDDDDDD : 0xFFA0A0A0;
            graphics.text(font, label, x + TAB_PAD, y0 + 2, color);
            x += width + TAB_GAP;
        }
    }

    /** A left click in the chat screen. True when it landed on a tab, which is then selected. */
    public static boolean handleClick(double mouseX, double mouseY, int button, int screenHeight) {
        if (!isEnabled() || button != 0) return false;
        Font font = Minecraft.getInstance().font;
        int y1 = screenHeight - BOTTOM_GAP;
        int y0 = y1 - TAB_HEIGHT;
        if (mouseY < y0 || mouseY >= y1) return false;
        int x = LEFT;
        for (ChatTab tab : ChatTab.values()) {
            if (!isShown(tab)) continue;
            int width = font.width(labelOf(tab)) + TAB_PAD * 2;
            if (mouseX >= x && mouseX < x + width) {
                select(tab);
                return true;
            }
            x += width + TAB_GAP;
        }
        return false;
    }
}
