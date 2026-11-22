package net.alpaka.addons.features.prompts;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.party.PartyInviteFeature;
import net.alpaka.addons.features.prompts.ChatPrompt.Choice;
import net.alpaka.addons.features.resourcepack.ServerPackCache;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Key prompts for what Hypixel asks in chat: an NPC's dialog choices, an Abiphone call, a trade,
 * friend, guild or duel request, the re-queue after a dungeon run and the gg under a rare drop.
 *
 * Hypixel writes these as chat lines with clickable words, which means opening the chat and
 * hitting a word before the offer runs out. With this on, such a line raises one of the mod's
 * notifications with the same answers on keys: Y and N for one or two answers, 1 to 5 for more.
 * One key press sends what one click on that word would send - the command on it, or for an NPC
 * dialog its click action, through {@link Outbound} - and takes the notice away. N on a request
 * with a single answer only takes the notice away. The chat line keeps working as it always did.
 *
 * The keys are only taken while the notice is up and no screen is open. A party invite's or a
 * party command's notice, if one is up as well, answers first. A dialog's notice also goes when
 * the NPC speaks again, since the choices belonged to what it said before, and on a world change
 * every notice goes but a friend, guild or duel request's.
 *
 * The idea and the message formats are Prompt Overlay's (dungeon-hub); see {@link ChatPromptParser}.
 */
public final class NpcPromptsFeature {

    private NpcPromptsFeature() {
    }

    /** {@code [NPC] Trevor: You can find your animal near the Desert Settlement.} */
    private static final Pattern NPC_LINE = Pattern.compile("^\\[NPC] (?<name>[^:]{1,40}): (?<text>.+)$");
    /** The Abiphone naming who calls: {@code ✆ Fann ✆}. */
    private static final Pattern CALLER_LINE = Pattern.compile("✆ (?<name>[^✆\\n]{1,40}?) ✆");
    private static final Pattern FORMATTING = Pattern.compile("§.");

    /** How long an NPC's line or a caller's name is taken to belong to the prompt that follows. */
    private static final long CONTEXT_MS = 10_000L;

    /** As for party invites: long enough to read the question and reach for the key. */
    private static final long MIN_HOLD_MS = 8_000L;

    /** Two short answers share a line; longer ones get a line each. */
    private static final int SHARED_LINE_CHARS = 18;

    /** A question of up to three lines, and five answers. */
    private static final int MAX_LINES = 10;

    /** What {@link #pick} gives for a key that is not an answer. */
    static final int NONE = -1;
    /** What {@link #pick} gives for N on a prompt with one answer: take the notice away, send nothing. */
    static final int IGNORE = -2;

    private static ChatPrompt current = null;
    private static long noticeId = 0L;

    private static String npcName = null;
    private static String npcLine = null;
    private static long npcAtMs = 0L;
    private static String caller = null;
    private static long callerAtMs = 0L;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.npcPromptsEnabled;
    }

    private static boolean isKindEnabled(ChatPrompt.Kind kind) {
        AlpakaConfig config = AlpakaConfig.instance;
        return switch (kind) {
            case DIALOG -> config.npcPromptsDialogs;
            case ABIPHONE -> config.npcPromptsAbiphone;
            case TRADE -> config.npcPromptsTrade;
            case FRIEND -> config.npcPromptsFriend;
            case GUILD -> config.npcPromptsGuild;
            case DUEL -> config.npcPromptsDuel;
            case REQUEUE -> config.npcPromptsRequeue;
            case GG -> config.npcPromptsGg;
        };
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
        // A prompt another mod's chat filter hid can still be answered.
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
    }

    static void onChat(Component message) {
        if (!isEnabled()) return;
        // The commands and click actions a key sends are Hypixel's; nothing is read off other servers.
        if (!ServerPackCache.isHypixel(Minecraft.getInstance().getCurrentServer())) return;

        long now = System.currentTimeMillis();
        noteContext(message.getString(), now);

        ChatPrompt prompt = ChatPromptParser.parse(message,
                now - npcAtMs <= CONTEXT_MS ? npcName : null,
                now - npcAtMs <= CONTEXT_MS ? npcLine : null,
                now - callerAtMs <= CONTEXT_MS ? caller : null);
        if (prompt != null && isKindEnabled(prompt.kind())) show(prompt);
    }

    /** Remembers who spoke or called, for the prompt that follows, and lets go of a dialog that went on. */
    private static void noteContext(String text, long now) {
        String plain = text.indexOf('§') < 0 ? text : FORMATTING.matcher(text).replaceAll("");
        for (String raw : plain.split("\n")) {
            String line = raw.trim();
            Matcher npc = NPC_LINE.matcher(line);
            if (npc.matches()) {
                npcName = npc.group("name").trim();
                npcLine = npc.group("text").trim();
                npcAtMs = now;
                if (current != null && current.kind() == ChatPrompt.Kind.DIALOG) dismiss();
                continue;
            }
            Matcher call = CALLER_LINE.matcher(line);
            if (call.find() && !call.group("name").contains("RING")) {
                caller = call.group("name").trim();
                callerAtMs = now;
            }
        }
    }

    private static void show(ChatPrompt prompt) {
        // The Abiphone rings more than once; the same offer again keeps the notice it has.
        if (isShowing() && current.choices().equals(prompt.choices())) return;
        if (noticeId != 0L) AlpakaNotifications.dismiss(noticeId);

        current = prompt;
        long hold = Math.max(AlpakaNotifications.configuredHoldMs(), MIN_HOLD_MS);
        noticeId = AlpakaNotifications.sendLines(prompt.title(), lines(prompt), 0, hold, MAX_LINES);
    }

    /** The notice's body: the question, then the answers with their keys. */
    static List<Component> lines(ChatPrompt prompt) {
        List<Component> lines = new ArrayList<>();
        if (!prompt.detail().isEmpty()) lines.add(Component.literal(prompt.detail()));

        List<Choice> choices = prompt.choices();
        if (choices.size() == 1) {
            lines.add(keyed("[Y]", ChatFormatting.GREEN, choices.get(0).label())
                    .append("    ").append(keyed("[N]", ChatFormatting.RED, "Ignore")));
        } else if (choices.size() == 2) {
            MutableComponent yes = keyed("[Y]", ChatFormatting.GREEN, choices.get(0).label());
            MutableComponent no = keyed("[N]", ChatFormatting.RED, choices.get(1).label());
            if (choices.get(0).label().length() + choices.get(1).label().length() <= SHARED_LINE_CHARS) {
                lines.add(yes.append("    ").append(no));
            } else {
                lines.add(yes);
                lines.add(no);
            }
        } else {
            for (int i = 0; i < choices.size(); i++) {
                lines.add(keyed("[" + (i + 1) + "]", ChatFormatting.YELLOW, choices.get(i).label()));
            }
        }
        return lines;
    }

    private static MutableComponent keyed(String key, ChatFormatting color, String label) {
        return Component.literal(key).withStyle(color)
                .append(Component.literal(" " + label).withStyle(ChatFormatting.WHITE));
    }

    /** Whether a prompt is up and its keys should answer. Clears one whose notice has gone. */
    public static boolean isShowing() {
        if (current == null) return false;
        if (!isEnabled() || !AlpakaNotifications.isShowing(noticeId)) {
            current = null;
            noticeId = 0L;
            return false;
        }
        return true;
    }

    /**
     * A key press with no screen open. True when the key was one of the prompt's, which then goes
     * no further - so a number answers the dialog instead of changing the hotbar slot, for as long
     * as the notice is up.
     */
    public static boolean onKey(int key, int scancode, boolean repeat) {
        if (!isShowing()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null) return false;

        int picked = pick(PartyInviteFeature.keyCharacter(key, scancode), digit(key), current.choices().size());
        if (picked == NONE) return false;
        // A held key's repeats are taken as well, so nothing else sees them, but act only once.
        if (!repeat) {
            if (picked >= 0) send(current.choices().get(picked).click());
            dismiss();
        }
        return true;
    }

    /**
     * Which answer a key stands for: its index, {@link #IGNORE}, or {@link #NONE}.
     *
     * One or two answers are Y and N, as on a party invite. From three on they are numbered, and
     * the letters mean nothing, so no answer is given by a key the notice does not show.
     *
     * @param letter the character the key produces on the player's layout, lower case, or 0
     * @param digit  the number on the key, or 0
     * @param count  how many answers the prompt has
     */
    static int pick(char letter, int digit, int count) {
        if (count <= 2) {
            if (letter == 'y') return 0;
            if (letter == 'n') return count == 2 ? 1 : IGNORE;
            return NONE;
        }
        return digit >= 1 && digit <= count ? digit - 1 : NONE;
    }

    /**
     * The number on a key of the top row or the number pad, or 0. By position, as the hotbar keys
     * are: on a French keyboard the top row types "&é" without Shift but is still slots 1 and 2.
     */
    static int digit(int key) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return key - GLFW.GLFW_KEY_0;
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) return key - GLFW.GLFW_KEY_KP_0;
        return 0;
    }

    private static void send(ClickEvent click) {
        if (click instanceof ClickEvent.RunCommand run) {
            Outbound.command(run.command().substring(1), Outbound.Cause.KEY_PRESS);
        } else if (click instanceof ClickEvent.Custom custom) {
            Outbound.dialogueResponse(custom, Outbound.Cause.KEY_PRESS);
        }
    }

    public static void dismiss() {
        current = null;
        if (noticeId != 0L) {
            AlpakaNotifications.dismiss(noticeId);
            noticeId = 0L;
        }
    }

    /**
     * A new world: what an NPC asked, a call, a trade or a re-queue belonged to the place left
     * behind. A friend, guild or duel request is still open after a warp and stays.
     */
    public static void onWorldChange() {
        ChatPrompt.Kind kind = current == null ? null : current.kind();
        boolean stillOpen = kind == ChatPrompt.Kind.FRIEND || kind == ChatPrompt.Kind.GUILD || kind == ChatPrompt.Kind.DUEL;
        if (!stillOpen) dismiss();
        npcName = null;
        npcLine = null;
        npcAtMs = 0L;
        caller = null;
        callerAtMs = 0L;
    }

    /** A disconnect: nothing asked on the server left can be answered any more. */
    public static void reset() {
        dismiss();
        npcName = null;
        npcLine = null;
        npcAtMs = 0L;
        caller = null;
        callerAtMs = 0L;
    }
}
