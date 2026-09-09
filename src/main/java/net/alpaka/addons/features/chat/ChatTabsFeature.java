package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * All / Party / Guild / PMs tabs in the chat screen.
 *
 * The tabs sit in the gap between the lowest chat line and the input box, where vanilla draws
 * nothing. Picking one narrows the chat to that channel: the filter runs where messages are laid
 * out into lines, so the stored history stays complete and switching back shows everything again,
 * including what arrived while another tab was up. A channel tab that received messages while it
 * was not the active one shows how many, until it is opened.
 *
 * The choice persists while the game runs - closing the chat does not reset it, so the HUD too shows
 * only the chosen channel until All is picked again - and starts on All every launch.
 *
 * Optionally a plain message typed on a channel tab is sent to that channel, with /pc, /gc or /r in
 * front. That is its own toggle and off by default, so a tab is only ever a view unless asked for.
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

    /**
     * Whether a message may be laid out into visible lines under the current tab.
     *
     * Asked with the message as Hypixel sent it, before the mod's own display formatting, which is
     * what keeps the guild tab working alongside the custom guild tag.
     */
    public static boolean accepts(GuiMessage message) {
        if (!isEnabled() || active == ChatTab.ALL) return true;
        return ChatTab.classify(message.content()) == active;
    }

    /** Counts a newly arrived message for the channel tab it belongs to, if that tab is not up. */
    public static void onMessage(Component content) {
        if (!isEnabled()) return;
        ChatTab tab = ChatTab.classify(content);
        if (tab != ChatTab.ALL && tab != active) {
            unread[tab.ordinal()]++;
        }
    }

    public static void select(ChatTab tab) {
        unread[tab.ordinal()] = 0;
        if (tab == active) return;
        active = tab;
        // Re-lays the history out under the new filter and jumps back to the newest line.
        Minecraft.getInstance().gui.getChat().rescaleChat();
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
