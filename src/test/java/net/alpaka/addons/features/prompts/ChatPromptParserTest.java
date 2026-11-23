package net.alpaka.addons.features.prompts;

import net.alpaka.addons.features.prompts.ChatPrompt.Choice;
import net.alpaka.addons.features.prompts.ChatPrompt.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Chat messages built the way Hypixel sends them. The two NPC dialogs follow messages recorded by
 * Prompt Overlay (dungeon-hub): one where the choices share a click action, one where each has its own.
 */
class ChatPromptParserTest {

    private static ClickEvent.Custom response(String npc, String key) {
        CompoundTag payload = new CompoundTag();
        payload.putString("npcId", npc);
        payload.putString("responseKey", key);
        return new ClickEvent.Custom(Identifier.fromNamespaceAndPath("skyblock", "dialogue_response"), Optional.of(payload));
    }

    private static MutableComponent clickable(String text, ClickEvent click) {
        return Component.literal(text).withStyle(style -> style.withClickEvent(click));
    }

    private static MutableComponent run(String text, String command) {
        return clickable(text, new ClickEvent.RunCommand(command));
    }

    private static ChatPrompt parse(Component message) {
        return ChatPromptParser.parse(message, null, null, null);
    }

    private static List<String> labels(ChatPrompt prompt) {
        return prompt.choices().stream().map(Choice::label).toList();
    }

    @Test
    void choicesUnderOneSharedClickAction() {
        ClickEvent.Custom shared = response("tyashoi", "x_6");
        MutableComponent group = clickable("", shared)
                .append(Component.literal("[Yes] ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("[Well] ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("[No] ").withStyle(ChatFormatting.RED));
        Component message = Component.literal("")
                // What a chat timestamp mod puts in front: clickable, but not an answer.
                .append(clickable("", new ClickEvent.SuggestCommand("10/30/2026")))
                .append(Component.literal("Select an option: ").withStyle(ChatFormatting.YELLOW).append(group))
                .append(Component.literal(""));

        ChatPrompt prompt = ChatPromptParser.parse(message, "Tyashoi", "Do you want to hear more?", null);

        assertNotNull(prompt);
        assertEquals(Kind.DIALOG, prompt.kind());
        assertEquals("Tyashoi", prompt.title());
        assertEquals("Do you want to hear more?", prompt.detail());
        assertEquals(List.of("Yes", "Well", "No"), labels(prompt));
        for (Choice choice : prompt.choices()) assertEquals(shared, choice.click());
    }

    @Test
    void choicesWithTheirOwnClickActionsAndArrows() {
        MutableComponent options = Component.literal("Select an option: ").withStyle(ChatFormatting.YELLOW);
        String[] texts = {"[You guessed it!] ", "[Could you tell me the rules again?] ", "[Nope.] "};
        for (int i = 0; i < texts.length; i++) {
            options.append(clickable("\n ", response("carnival_fisherman", "r_2_" + (i + 1)))
                    .append(Component.literal(" ➜ ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(texts[i]).withStyle(ChatFormatting.GREEN)));
        }

        ChatPrompt prompt = parse(Component.literal("").append(options));

        assertNotNull(prompt);
        assertEquals("NPC Prompt", prompt.title());
        assertEquals("", prompt.detail());
        // The middle one is too long for a line of the notice and is cut.
        assertEquals(List.of("You guessed it!", "Could you tell me the r…", "Nope."), labels(prompt));
        assertEquals(response("carnival_fisherman", "r_2_1"), prompt.choices().get(0).click());
        assertEquals(response("carnival_fisherman", "r_2_3"), prompt.choices().get(2).click());
    }

    @Test
    void yesIsFirstWhereverHypixelPutIt() {
        Component message = Component.literal("Select an option: ")
                .append(clickable("[No] ", response("npc", "no")))
                .append(clickable("[Yes] ", response("npc", "yes")));

        ChatPrompt prompt = parse(message);

        assertEquals(List.of("Yes", "No"), labels(prompt));
        assertEquals(response("npc", "yes"), prompt.choices().get(0).click());
    }

    @Test
    void choicesWithoutBracketsAreSplitByClickAction() {
        Component message = Component.literal("Select an option: ")
                .append(clickable(" ➜ ", response("npc", "a")))
                .append(clickable("Tell me more", response("npc", "a")))
                .append(Component.literal(" "))
                .append(clickable("Goodbye", response("npc", "b")));

        assertEquals(List.of("Tell me more", "Goodbye"), labels(parse(message)));
    }

    @Test
    void trapperAsksWithCommands() {
        Component message = Component.literal("\n")
                .append(Component.literal("Accept the trapper's task to hunt the animal?\n").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("Click an option: "))
                .append(run("§a§l[YES]", "/chatprompt 0b1c YES"))
                .append(Component.literal(" - "))
                .append(run("[NO]", "/chatprompt 0b1c NO"));

        ChatPrompt prompt = ChatPromptParser.parse(message, "Trevor", "You can find your animal near the Oasis.", null);

        assertEquals(Kind.DIALOG, prompt.kind());
        assertEquals("Trevor", prompt.title());
        assertEquals("Accept the trapper's task to hunt the animal?", prompt.detail());
        assertEquals(List.of("YES", "NO"), labels(prompt));
        assertEquals(new ClickEvent.RunCommand("/chatprompt 0b1c YES"), prompt.choices().get(0).click());
        assertEquals(new ClickEvent.RunCommand("/chatprompt 0b1c NO"), prompt.choices().get(1).click());
    }

    @Test
    void moreChoicesThanKeysAreLeftToTheChat() {
        MutableComponent message = Component.literal("Select an option: ");
        for (int i = 0; i < ChatPromptParser.MAX_CHOICES + 1; i++) {
            message.append(clickable("[" + i + "] ", response("npc", "k" + i)));
        }
        assertNull(parse(message));
    }

    @Test
    void aClickActionThatIsNotADialogAnswerIsNotSent() {
        ClickEvent.Custom other = new ClickEvent.Custom(Identifier.fromNamespaceAndPath("skyblock", "something_else"), Optional.empty());
        assertNull(parse(Component.literal("Select an option: ").append(clickable("[Yes] ", other))));
    }

    @Test
    void aLineAPlayerTypedRaisesNothing() {
        assertNull(parse(Component.literal("[MVP+] Someone: Select an option: [Yes] [No]")));
        assertNull(parse(Component.literal("Friend request from [MVP+] Someone\n[ACCEPT] - [DENY] - [BLOCK]")));
        assertNull(parse(Component.literal("Someone has sent you a trade request. Click here to accept!")));
    }

    @Test
    void aPlayerTypingTheMarkerCannotTurnTheClickOnTheirNameIntoAChoice() {
        // Hypixel puts a click on the sender's name; the words after it are the player's own.
        Component unbracketed = Component.literal("")
                .append(run("[MVP+] Someone", "/pv Someone"))
                .append(Component.literal(": Select an option: Yes"));
        assertNull(parse(unbracketed));

        Component bracketed = Component.literal("")
                .append(run("[MVP+]", "/pv Someone"))
                .append(Component.literal(" Someone: Select an option: [Yes] [No]"));
        assertNull(parse(bracketed));
    }

    @Test
    void onlyChoicesAfterTheMarkerCount() {
        Component message = Component.literal("")
                .append(run("[Someone]", "/pv Someone"))
                .append(Component.literal("\nSelect an option: "))
                .append(clickable("[Yes] ", response("npc", "yes")));

        ChatPrompt prompt = parse(message);

        assertEquals(List.of("Yes"), labels(prompt));
        assertEquals(response("npc", "yes"), prompt.choices().get(0).click());
    }

    @Test
    void choicesWithoutBracketsMustBeDialogAnswers() {
        Component message = Component.literal("Select an option: ")
                .append(run("Tell me more", "/pv Someone"));
        assertNull(parse(message));
    }

    @Test
    void friendRequest() {
        Component message = Component.literal("Friend request from [MVP+] Someone\n")
                .append(run("[ACCEPT]", "/f accept Someone"))
                .append(Component.literal(" - "))
                .append(run("[DENY]", "/f deny Someone"))
                .append(Component.literal(" - "))
                .append(run("[BLOCK]", "/block add Someone"));

        ChatPrompt prompt = parse(message);

        assertEquals(Kind.FRIEND, prompt.kind());
        assertEquals("From Someone", prompt.detail());
        assertEquals(List.of(new Choice("Accept", new ClickEvent.RunCommand("/f accept Someone"))), prompt.choices());
    }

    @Test
    void guildInvite() {
        Component message = Component.literal("[MVP+] Someone has invited you to join their guild, Alpaka Farm!\n")
                .append(run("You have 5 minutes to accept. Click here to accept or type /guild accept Someone!", "/guild accept Someone"));

        ChatPrompt prompt = parse(message);

        assertEquals(Kind.GUILD, prompt.kind());
        assertEquals("Someone invited you to Alpaka Farm", prompt.detail());
        assertEquals(new ClickEvent.RunCommand("/guild accept Someone"), prompt.choices().get(0).click());
    }

    @Test
    void duelInvite() {
        Component message = Component.literal("[VIP] Someone has invited you to Sumo Duels!\n")
                .append(run("Click here to accept!", "/duel accept Someone"));

        ChatPrompt prompt = parse(message);

        assertEquals(Kind.DUEL, prompt.kind());
        assertEquals("Someone invited you to Sumo Duels", prompt.detail());
    }

    @Test
    void tradeRequest() {
        Component message = run("Someone has sent you a trade request. Click here to accept!", "/tradeaccept 1f2e");

        ChatPrompt prompt = parse(message);

        assertEquals(Kind.TRADE, prompt.kind());
        assertEquals("From Someone", prompt.detail());
        assertEquals(new ClickEvent.RunCommand("/tradeaccept 1f2e"), prompt.choices().get(0).click());
    }

    @Test
    void requeueReadsTheFloorFromTheTooltip() {
        Component message = Component.literal("Click ")
                .append(Component.literal("HERE").withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/instancerequeue"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to re-queue into\nThe Catacombs\nFloor VII")))))
                .append(Component.literal(" to re-queue into The Catacombs!"));

        ChatPrompt prompt = parse(message);

        assertEquals(Kind.REQUEUE, prompt.kind());
        assertEquals("Into The Catacombs, Floor VII", prompt.detail());
        assertEquals(new ClickEvent.RunCommand("/instancerequeue"), prompt.choices().get(0).click());
    }

    @Test
    void abiphoneCall() {
        Component message = Component.literal("✆ RING... RING... RING... ")
                .append(run("[PICK UP]", "/cb 4d5e"));

        ChatPrompt prompt = ChatPromptParser.parse(message, null, null, "Fann");

        assertEquals(Kind.ABIPHONE, prompt.kind());
        assertEquals("Fann is calling", prompt.detail());
        assertEquals(List.of(new Choice("Pick Up", new ClickEvent.RunCommand("/cb 4d5e"))), prompt.choices());
        assertEquals("Incoming call", parse(message).detail());
    }

    @Test
    void sayGg() {
        Component message = Component.literal("")
                .append(run("CLICK HERE", "/ac gg").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" to say gg!"));

        ChatPrompt prompt = parse(message);

        assertEquals(Kind.GG, prompt.kind());
        assertInstanceOf(ClickEvent.RunCommand.class, prompt.choices().get(0).click());
    }

    @Test
    void aPartyInviteIsLeftToItsOwnPrompt() {
        Component message = Component.literal("[MVP+] Someone has invited you to join their party!\n")
                .append(run("Click here to join!", "/party accept Someone"));
        assertNull(parse(message));
    }
}
