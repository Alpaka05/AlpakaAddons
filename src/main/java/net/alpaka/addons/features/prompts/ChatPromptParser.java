package net.alpaka.addons.features.prompts;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.features.prompts.ChatPrompt.Choice;
import net.alpaka.addons.features.prompts.ChatPrompt.Kind;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a chat message for something to answer: an NPC's dialog choices, a call, a request.
 *
 * A prompt only ever comes from a message with a click event on it. Players cannot put click events
 * into chat, so a line a player typed to look like a request raises nothing, and the answer a key
 * sends is the event Hypixel attached - never a command put together from the message's words.
 *
 * The message formats follow Prompt Overlay (dungeon-hub), which reads the same lines. An NPC
 * dialog arrives as "Select an option: " followed by its choices, each in brackets and under a
 * {@code skyblock:dialogue_response} click action; the choices may each carry their own action or
 * share one that sits on the piece around them.
 */
public final class ChatPromptParser {

    private ChatPromptParser() {
    }

    /** At most this many choices get a key; a message with more is left to the chat. */
    public static final int MAX_CHOICES = 5;

    private static final int MAX_DETAIL = 84;
    private static final int MAX_LABEL = 24;

    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern OPTIONS_MARKER = Pattern.compile("(?:Select|Click) an option:");
    private static final Pattern BRACKETED = Pattern.compile("^\\[(.+)]$");

    private static final Pattern FRIEND_ACCEPT = Pattern.compile("^/(?:f|friend) accept (?<player>[A-Za-z0-9_]{1,16})$");
    private static final Pattern GUILD_ACCEPT = Pattern.compile("^/(?:g|guild) accept (?<player>[A-Za-z0-9_]{1,16})$");
    private static final Pattern DUEL_ACCEPT = Pattern.compile("^/duel accept (?<player>[A-Za-z0-9_]{1,16})$");
    private static final Pattern TRADE_ACCEPT = Pattern.compile("^/trade(?:accept)? \\S+$");
    private static final String REQUEUE_COMMAND = "/instancerequeue";

    private static final Pattern GUILD_NAME = Pattern.compile("has invited you to join their guild, ([^!\\n]+)!");
    private static final Pattern DUEL_KIND = Pattern.compile("has invited you to ([^!\\n]+)!");
    private static final Pattern TRADE_FROM = Pattern.compile("([A-Za-z0-9_]{1,16}) has sent you a trade request");
    private static final Pattern REQUEUE_INTO = Pattern.compile("re-queue into ([^!\\n]+?)\\s*[!.]?\\s*(?:\\n|$)");
    private static final Pattern FLOOR = Pattern.compile("Floor (?:I{1,3}|IV|VI{0,2})\\b");

    private static final Set<String> AFFIRMATIVE = Set.of("yes", "accept", "confirm", "ok", "okay", "sure");
    private static final Set<String> NEGATIVE = Set.of("no", "decline", "deny", "cancel", "nope");

    /** One run of text in the message with the click event that applies to it, inherited ones included. */
    private record Piece(String text, ClickEvent click, HoverEvent hover) {
    }

    /**
     * The prompt in a chat message, or null.
     *
     * @param npcName the NPC that spoke last, if one did a moment ago, else null
     * @param npcLine what it said
     * @param caller  who the Abiphone last announced, if it did a moment ago, else null
     */
    public static ChatPrompt parse(Component message, String npcName, String npcLine, String caller) {
        List<Piece> pieces = new ArrayList<>();
        message.visit((style, text) -> {
            pieces.add(new Piece(strip(text), style.getClickEvent(), style.getHoverEvent()));
            return Optional.empty();
        }, Style.EMPTY);

        boolean clickable = false;
        StringBuilder joined = new StringBuilder();
        for (Piece piece : pieces) {
            joined.append(piece.text());
            clickable |= answers(piece.click());
        }
        if (!clickable) return null;
        String plain = joined.toString();

        ChatPrompt request = request(pieces, plain);
        if (request != null) return request;

        String trimmed = plain.trim();
        if (trimmed.startsWith("✆") && trimmed.contains("RING...")) {
            for (Piece piece : pieces) {
                if (piece.click() instanceof ClickEvent.RunCommand && piece.text().contains("PICK UP")) {
                    String detail = caller == null ? "Incoming call" : caller + " is calling";
                    return single(Kind.ABIPHONE, "Abiphone", detail, "Pick Up", piece.click());
                }
            }
        }

        if (plain.toLowerCase(Locale.ROOT).contains("to say gg")) {
            for (Piece piece : pieces) {
                if (piece.click() instanceof ClickEvent.RunCommand && piece.text().toUpperCase(Locale.ROOT).contains("CLICK HERE")) {
                    return single(Kind.GG, "Rare Drop", "Congratulate with gg?", "Say GG", piece.click());
                }
            }
        }

        return dialog(pieces, plain, npcName, npcLine);
    }

    /** A request that is known by the command on its accept link: friend, guild, duel, trade, re-queue. */
    private static ChatPrompt request(List<Piece> pieces, String plain) {
        for (Piece piece : pieces) {
            if (!(piece.click() instanceof ClickEvent.RunCommand run)) continue;
            String command = run.command();

            Matcher friend = FRIEND_ACCEPT.matcher(command);
            if (friend.matches()) {
                return single(Kind.FRIEND, "Friend Request", "From " + friend.group("player"), "Accept", run);
            }
            Matcher guild = GUILD_ACCEPT.matcher(command);
            if (guild.matches()) {
                Matcher name = GUILD_NAME.matcher(plain);
                String detail = guild.group("player") + " invited you"
                        + (name.find() ? " to " + name.group(1).trim() : "");
                return single(Kind.GUILD, "Guild Invite", detail, "Join", run);
            }
            Matcher duel = DUEL_ACCEPT.matcher(command);
            if (duel.matches()) {
                Matcher kind = DUEL_KIND.matcher(plain);
                String detail = duel.group("player") + " invited you"
                        + (kind.find() ? " to " + kind.group(1).trim() : "");
                return single(Kind.DUEL, "Duel Invite", detail, "Accept", run);
            }
            if (TRADE_ACCEPT.matcher(command).matches()) {
                Matcher from = TRADE_FROM.matcher(plain);
                if (from.find()) {
                    return single(Kind.TRADE, "Trade Request", "From " + from.group(1), "Accept", run);
                }
            }
            if (command.equals(REQUEUE_COMMAND)) {
                Matcher into = REQUEUE_INTO.matcher(plain);
                String place = into.find() ? into.group(1).trim() : "the same floor";
                // The floor is only in the tooltip of the link.
                if (piece.hover() instanceof HoverEvent.ShowText tooltip) {
                    Matcher floor = FLOOR.matcher(strip(tooltip.value().getString()));
                    if (floor.find() && !place.contains(floor.group())) place += ", " + floor.group();
                }
                return single(Kind.REQUEUE, "Re-Queue", "Into " + place, "Re-Queue", run);
            }
        }
        return null;
    }

    private static ChatPrompt dialog(List<Piece> pieces, String plain, String npcName, String npcLine) {
        Matcher marker = OPTIONS_MARKER.matcher(plain);
        if (!marker.find()) return null;

        // Each choice stands in its own brackets, whether it has a click action of its own or
        // shares one with its neighbors.
        List<Choice> choices = new ArrayList<>();
        for (Piece piece : pieces) {
            if (!answers(piece.click())) continue;
            Matcher bracketed = BRACKETED.matcher(piece.text().trim());
            if (bracketed.matches()) choices.add(new Choice(label(bracketed.group(1)), piece.click()));
        }
        if (choices.isEmpty()) choices = unbracketed(pieces);
        if (choices.isEmpty() || choices.size() > MAX_CHOICES) return null;

        // Y is the yes, wherever Hypixel put it.
        if (choices.size() == 2 && NEGATIVE.contains(word(choices.get(0).label()))
                && AFFIRMATIVE.contains(word(choices.get(1).label()))) {
            choices = List.of(choices.get(1), choices.get(0));
        }

        // The question comes with the choices ("Accept the trapper's task ...?"), or is what the
        // NPC said just before them.
        String question = WHITESPACE.matcher(plain.substring(0, marker.start())).replaceAll(" ").trim();
        if (question.isEmpty() && npcLine != null) question = npcLine;
        String title = npcName != null ? npcName : "NPC Prompt";
        return new ChatPrompt(Kind.DIALOG, title, shorten(question, MAX_DETAIL), List.copyOf(choices));
    }

    /** Choices written without brackets: every run of text under one click action is one choice. */
    private static List<Choice> unbracketed(List<Piece> pieces) {
        List<Choice> choices = new ArrayList<>();
        ClickEvent current = null;
        StringBuilder text = new StringBuilder();
        for (Piece piece : pieces) {
            ClickEvent click = answers(piece.click()) ? piece.click() : null;
            if (current != null && !current.equals(click)) {
                String label = label(text.toString());
                if (!label.isEmpty()) choices.add(new Choice(label, current));
                text.setLength(0);
            }
            current = click;
            if (click != null) text.append(piece.text());
        }
        if (current != null) {
            String label = label(text.toString());
            if (!label.isEmpty()) choices.add(new Choice(label, current));
        }
        return choices;
    }

    /** Whether a click event is one a key may send: a command, or an NPC dialog's click action. */
    static boolean answers(ClickEvent click) {
        if (click instanceof ClickEvent.RunCommand run) {
            String command = run.command();
            return command.length() > 1 && command.startsWith("/") && command.indexOf('\n') < 0;
        }
        return click instanceof ClickEvent.Custom custom && Outbound.DIALOGUE_RESPONSE.equals(custom.id());
    }

    private static ChatPrompt single(Kind kind, String title, String detail, String label, ClickEvent click) {
        return new ChatPrompt(kind, title, shorten(detail, MAX_DETAIL), List.of(new Choice(label, click)));
    }

    private static String label(String raw) {
        String text = raw.replace("➜", "").replace("[", "").replace("]", "");
        return shorten(WHITESPACE.matcher(text).replaceAll(" ").trim(), MAX_LABEL);
    }

    private static String word(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static String strip(String text) {
        return text.indexOf('§') < 0 ? text : FORMATTING.matcher(text).replaceAll("");
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1).trim() + "…";
    }
}
