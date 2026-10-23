package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;
import java.util.Arrays;

/**
 * All / Party / Guild / Co-op / PMs / NPCs tabs in the chat screen.
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
 *
 * The row is drawn in the mod's menu look: rounded tabs, the config's text greys, the accent for the
 * tab that is up and for unread counts, and the chat's own font - the menu font when the chat is
 * written in it. The labels cast vanilla's drop shadow in either font, as the chat's own lines do.
 *
 * Guild chat can also be kept off All, for a busy guild drowning out everything else; it then shows
 * only on the Guild tab, which counts it as unread as usual.
 */
public final class ChatTabsFeature {

    private ChatTabsFeature() {
    }

    private static ChatTab active = ChatTab.ALL;
    private static final int[] unread = new int[ChatTab.values().length];
    private static final ChatTab[] TABS = ChatTab.values();

    // The row and the chip are drawn every frame. A label only changes with its unread count, and a
    // reused component keeps the bidi-reordered text vanilla works out for it on first draw, so
    // neither the components nor that reorder are redone per frame. The widths are still measured
    // each frame, since a font change can alter them.
    /** Each tab's label as last built and the unread count it shows; reused until the count changes. */
    private static final Component[] LABELS = new Component[TABS.length];
    private static final int[] LABEL_COUNTS = new int[TABS.length];
    /** The filter chip's text for each tab, built on first draw. */
    private static final Component[] CHIPS = new Component[TABS.length];
    /** Whether the cached labels and chips are in the menu font; both are dropped when this changes. */
    private static boolean cachedInChatFont;

    // Layout, in GUI pixels. The input box starts at height - 14; the tabs end a pixel above it. The
    // lowest chat line ends at height - 40, so the row never touches the messages either.
    private static final int TAB_HEIGHT = 12;
    private static final int TAB_PAD = 5;
    private static final int TAB_GAP = 2;
    private static final int LEFT = 2;
    private static final int BOTTOM_GAP = 15;
    /** Corner radius of a tab and of the filter chip, a step below the HUD panels' for the smaller box. */
    private static final int TAB_RADIUS = 3;
    /**
     * Where a label starts below the box's top. Both fonts put their baseline 7 pixels below where
     * the text is drawn, so the capitals sit on the same rows in either: 2 to 8 of the 12, with two
     * rows above them and two below (the first holding their shadow) before the selected tab's
     * underline on row 11.
     */
    private static final int LABEL_TOP = 2;
    /** How far the selected tab's underline stays clear of the rounded corners on either side. */
    private static final int UNDERLINE_INSET = 2;
    /** The tint behind the selected tab and the chip: the accent, faint. */
    private static final int ACCENT_TINT_ALPHA = 0x30000000;
    private static final int HOVER_TINT = 0x20FFFFFF;

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
            case NPC -> cfg.chatTabsShowNpc;
        };
    }

    /** A tab was taken out of the row or put back: off a tab that is gone, back to All. */
    public static void onShownTabsChanged() {
        if (!isShown(active)) select(ChatTab.ALL);
        for (ChatTab tab : ChatTab.values()) {
            if (!isShown(tab)) unread[tab.ordinal()] = 0;
        }
        // Hiding the Guild tab brings guild lines kept off All back there, and showing it again
        // takes them away.
        if (AlpakaConfig.instance.chatTabsGuildOnlyInGuildTab) onFilterChanged();
    }

    /** A setting changed what a tab lets through: re-lays the history out under it. */
    public static void onFilterChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.hud.getChat().rescaleChat();
    }

    /**
     * Whether a message may be laid out into visible lines under the current tab.
     *
     * Asked with the message as Hypixel sent it, before the mod's own display formatting, which is
     * what keeps the guild tab working alongside the custom guild tag.
     */
    public static boolean accepts(GuiMessage message) {
        if (!isEnabled()) return true;
        if (active == ChatTab.ALL) {
            // Guild lines can be kept to the Guild tab, but only while that tab is in the row;
            // otherwise they would be nowhere at all.
            if (!AlpakaConfig.instance.chatTabsGuildOnlyInGuildTab || !isShown(ChatTab.GUILD)) return true;
            String text = ChatTab.strip(message.content().getString());
            return ChatTab.isUrgent(text) || ChatTab.classify(text) != ChatTab.GUILD;
        }
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
     * A key press in the chat screen. True when it was Tab for the tabs, which then goes no further.
     * Ctrl+Tab always cycles, Ctrl+Shift+Tab backwards. Plain Tab only does on an empty line: with
     * text typed it is vanilla's player-name completion, which it used to take over. A held Tab
     * repeats, and the chat screen hears every repeat as another press, so it raced through the
     * tabs; a repeat is swallowed without cycling, as on the peek.
     */
    public static boolean onChatScreenKey(int key, boolean shift, boolean control, boolean repeat,
                                          boolean suggestionsVisible, String input) {
        if (!tabKeyEnabled() || key != GLFW.GLFW_KEY_TAB) return false;
        if (!control && (suggestionsVisible || !input.isBlank())) return false;
        if (!repeat) cycle(shift);
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
        Component label = chipOf(active);
        int y1 = graphics.guiHeight() - BOTTOM_GAP;
        int y0 = y1 - TAB_HEIGHT;
        int width = font.width(label) + TAB_PAD * 2;
        drawTab(graphics, LEFT, y0, width, mc.options.getBackgroundColor(Integer.MIN_VALUE), true, false);
        graphics.text(font, label, LEFT + TAB_PAD, y0 + LABEL_TOP, AlpakaStyle.TEXT, true);
    }

    /**
     * One tab's box, or the chip's: rounded like the menus' rows, in vanilla's text background
     * color. With vanilla's default, Background for Chat Only, that is a fixed half-transparent
     * black whatever the chat's own background opacity is; only with that option off does it follow
     * the Text Background Opacity slider. The tab that is up carries a faint accent tint and an
     * accent underline that stops short of the rounded corners.
     */
    private static void drawTab(GuiGraphicsExtractor graphics, int x, int y0, int width, int background,
                                boolean selected, boolean hovered) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y0, width, TAB_HEIGHT, TAB_RADIUS, background);
        if (selected) {
            int accent = ModernGuiUtils.getAccentColor();
            ModernGuiUtils.drawRoundedRect(graphics, x, y0, width, TAB_HEIGHT, TAB_RADIUS, (accent & 0xFFFFFF) | ACCENT_TINT_ALPHA);
            ModernGuiUtils.drawRoundedRect(graphics, x + UNDERLINE_INSET, y0 + TAB_HEIGHT - 1,
                    width - UNDERLINE_INSET * 2, 1, 1, accent);
        } else if (hovered) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y0, width, TAB_HEIGHT, TAB_RADIUS, HOVER_TINT);
        }
    }

    /**
     * A label in the font the chat is written in: the menu font when the chat uses it, vanilla's
     * otherwise. Measured and drawn as the same component, so the click areas match the boxes.
     */
    private static Component inChatFont(Component label) {
        return GuiFont.inChat() ? GuiFont.restyle(label) : label;
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

    /** Drops the cached labels and chips once the chat has switched font. */
    private static void checkChatFont() {
        boolean inChat = GuiFont.inChat();
        if (inChat == cachedInChatFont) return;
        cachedInChatFont = inChat;
        Arrays.fill(LABELS, null);
        Arrays.fill(CHIPS, null);
    }

    /** The filter chip's text: the channel in full, the word after it quieter, so it reads as the channel's name. */
    private static Component chipOf(ChatTab tab) {
        checkChatFont();
        int i = tab.ordinal();
        if (CHIPS[i] == null) {
            CHIPS[i] = inChatFont(Component.literal(tab.label)
                    .append(Component.literal(" only").withStyle(Style.EMPTY.withColor(AlpakaStyle.TEXT_MUTED & 0xFFFFFF))));
        }
        return CHIPS[i];
    }

    /** A tab's name, with its unread count after it in the accent color while there is one. */
    private static Component labelOf(ChatTab tab) {
        checkChatFont();
        int i = tab.ordinal();
        int count = unread[i];
        Component cached = LABELS[i];
        if (cached != null && LABEL_COUNTS[i] == count) return cached;
        MutableComponent label = Component.literal(tab.label);
        if (count > 0) {
            label.append(Component.literal(" " + (count > 99 ? "99+" : String.valueOf(count)))
                    .withStyle(Style.EMPTY.withColor(ModernGuiUtils.getAccentColor() & 0xFFFFFF)));
        }
        Component styled = inChatFont(label);
        LABELS[i] = styled;
        LABEL_COUNTS[i] = count;
        return styled;
    }

    /** Draws the row. Called from the chat screen before the command suggestions, so those stay on top. */
    public static void render(GuiGraphicsExtractor graphics, int screenHeight, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int y1 = screenHeight - BOTTOM_GAP;
        int y0 = y1 - TAB_HEIGHT;
        int background = mc.options.getBackgroundColor(Integer.MIN_VALUE);
        // Vanilla's chat lines always cast their shadow, in the pixel font and in the menu font
        // alike, so the labels under them do too.
        boolean shadow = true;
        int x = LEFT;
        for (ChatTab tab : TABS) {
            if (!isShown(tab)) continue;
            Component label = labelOf(tab);
            int width = font.width(label) + TAB_PAD * 2;
            boolean selected = tab == active;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y0 && mouseY < y1;

            drawTab(graphics, x, y0, width, background, selected, hovered);
            // As the config's sidebar: the open one and the hovered one in full, the rest softer.
            int color = selected || hovered ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
            graphics.text(font, label, x + TAB_PAD, y0 + LABEL_TOP, color, shadow);
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
        for (ChatTab tab : TABS) {
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
