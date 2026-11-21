package net.alpaka.addons.features.npc;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Answers an NPC's yes/no question with a click anywhere in the open chat.
 *
 * Hypixel asks things like "Accept the trapper's task to hunt the animal?" with a line
 * {@code Click an option: [YES] - [NO]}, where each word carries a command. Hitting the small
 * [YES] means aiming at a few pixels of text. With this on, the newest such question stays
 * pending for a short while, and a left click in the open chat that lands on nothing else - no
 * chat link, no tab, no HUD - runs the [YES] command, exactly as clicking the word would.
 *
 * Only a line that carries both a [YES] and a [NO] with a command counts: players cannot put click
 * events into chat, so such a line comes from the server. The question is forgotten once answered,
 * by this or by clicking either word, after {@link #TTL_MS}, and on a world change.
 */
public final class NpcQuickAnswer {

    private NpcQuickAnswer() {
    }

    /** How long a question waits for its click. NPC dialogues are answered within seconds. */
    public static final long TTL_MS = 30_000L;

    private static String yesCommand = null;
    private static String noCommand = null;
    private static long askedAtMs = 0L;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.npcQuickAnswerEnabled;
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message, System.currentTimeMillis());
        });
    }

    static void onChat(Component message, long now) {
        if (!isEnabled()) return;
        Options options = options(message);
        if (options == null) return;
        yesCommand = options.yes();
        noCommand = options.no();
        askedAtMs = now;
    }

    /** The commands behind a message's [YES] and [NO], or null when it does not offer both. */
    record Options(String yes, String no) {
    }

    static Options options(Component message) {
        String[] found = new String[2];
        message.visit((style, text) -> {
            String word = SkyblockUtils.cleanColor(text).trim();
            String command = command(style);
            if (command == null) return Optional.empty();
            if (word.equalsIgnoreCase("[YES]") && found[0] == null) found[0] = command;
            if (word.equalsIgnoreCase("[NO]") && found[1] == null) found[1] = command;
            return Optional.empty();
        }, Style.EMPTY);
        if (found[0] == null || found[1] == null || found[0].equals(found[1])) return null;
        return new Options(found[0], found[1]);
    }

    /** A style's run-command, without its slash, or null. */
    private static String command(Style style) {
        if (!(style.getClickEvent() instanceof ClickEvent.RunCommand run)) return null;
        String command = run.command().trim();
        if (command.startsWith("/")) command = command.substring(1);
        return command.isEmpty() ? null : command;
    }

    /** Whether a question is waiting. Forgets one that ran out. */
    public static boolean isPending() {
        return isPending(System.currentTimeMillis());
    }

    static boolean isPending(long now) {
        if (yesCommand == null) return false;
        if (!isEnabled() || now - askedAtMs > TTL_MS) {
            clear();
            return false;
        }
        return true;
    }

    /**
     * A click in the open chat that nothing else took. True when it answered the question and
     * should go no further.
     */
    public static boolean onChatClick(int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isPending()) return false;
        String command = yesCommand;
        clear();
        Outbound.command(command, Outbound.Cause.CHAT_CLICK);
        return true;
    }

    /** A chat link was clicked: the player answered the question themselves. */
    public static void onComponentClicked(Style style) {
        if (yesCommand == null) return;
        String command = command(style);
        if (command != null && (command.equals(yesCommand) || command.equals(noCommand))) clear();
    }

    /** The hint above the input bar's right end while a question waits. */
    public static void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        if (!isPending()) return;
        Font font = Minecraft.getInstance().font;
        Component hint = Component.literal("Click Anywhere: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[YES]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        int width = net.alpaka.addons.client.gui.TextWidths.of(font, hint);
        // Above the input bar, which is 14 pixels from the bottom edge to its top.
        graphics.text(font, hint, screenWidth - width - 4, screenHeight - 14 - font.lineHeight - 3, AlpakaStyle.TEXT, true);
    }

    public static void clear() {
        yesCommand = null;
        noCommand = null;
        askedAtMs = 0L;
    }
}
