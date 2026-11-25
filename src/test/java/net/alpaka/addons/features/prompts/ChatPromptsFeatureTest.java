package net.alpaka.addons.features.prompts;

import net.alpaka.addons.features.prompts.ChatPrompt.Choice;
import net.alpaka.addons.features.prompts.ChatPrompt.Kind;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatPromptsFeatureTest {

    private static ChatPrompt prompt(String detail, String... labels) {
        List<Choice> choices = new ArrayList<>();
        for (String label : labels) choices.add(new Choice(label, new ClickEvent.RunCommand("/chatprompt x " + label)));
        return new ChatPrompt(Kind.DIALOG, "Trevor", detail, choices);
    }

    private static List<String> lines(ChatPrompt prompt) {
        return ChatPromptsFeature.lines(prompt).stream().map(Component::getString).toList();
    }

    @Test
    void oneAnswerIsYAndNOnlyTakesTheNoticeAway() {
        assertEquals(0, ChatPromptsFeature.pick('y', 0, 1));
        assertEquals(ChatPromptsFeature.IGNORE, ChatPromptsFeature.pick('n', 0, 1));
        assertEquals(ChatPromptsFeature.NONE, ChatPromptsFeature.pick((char) 0, 1, 1));
    }

    @Test
    void twoAnswersAreYAndN() {
        assertEquals(0, ChatPromptsFeature.pick('y', 0, 2));
        assertEquals(1, ChatPromptsFeature.pick('n', 0, 2));
        // The hotbar keeps its numbers while the notice only shows letters.
        assertEquals(ChatPromptsFeature.NONE, ChatPromptsFeature.pick((char) 0, 1, 2));
        assertEquals(ChatPromptsFeature.NONE, ChatPromptsFeature.pick('w', 0, 2));
    }

    @Test
    void moreAnswersAreNumbered() {
        assertEquals(0, ChatPromptsFeature.pick((char) 0, 1, 3));
        assertEquals(2, ChatPromptsFeature.pick((char) 0, 3, 3));
        assertEquals(ChatPromptsFeature.NONE, ChatPromptsFeature.pick((char) 0, 4, 3));
        assertEquals(ChatPromptsFeature.NONE, ChatPromptsFeature.pick('y', 0, 3));
        assertEquals(ChatPromptsFeature.NONE, ChatPromptsFeature.pick('n', 0, 3));
    }

    @Test
    void numbersComeFromTheTopRowAndTheNumberPad() {
        assertEquals(1, ChatPromptsFeature.digit(InputConstants.KEY_1));
        assertEquals(5, ChatPromptsFeature.digit(InputConstants.KEY_5));
        assertEquals(9, ChatPromptsFeature.digit(InputConstants.KEY_9));
        assertEquals(3, ChatPromptsFeature.digit(InputConstants.KEY_NUMPAD3));
        assertEquals(9, ChatPromptsFeature.digit(InputConstants.KEY_NUMPAD9));
        // The 0 keys follow the 9s in 26.3's key numbers, and are no answer.
        assertEquals(0, ChatPromptsFeature.digit(InputConstants.KEY_0));
        assertEquals(0, ChatPromptsFeature.digit(InputConstants.KEY_NUMPAD0));
        assertEquals(0, ChatPromptsFeature.digit(InputConstants.KEY_Y));
    }

    @Test
    void theNoticeShowsTheKeysItTakes() {
        assertEquals(List.of("Incoming call", "[Y] Pick Up    [N] Ignore"), lines(prompt("Incoming call", "Pick Up")));
        assertEquals(List.of("Hunt the animal?", "[Y] YES    [N] NO"), lines(prompt("Hunt the animal?", "YES", "NO")));
        assertEquals(List.of("[Y] Tell me more about it", "[N] Goodbye"), lines(prompt("", "Tell me more about it", "Goodbye")));
        assertEquals(List.of("[1] Yes", "[2] Well", "[3] No"), lines(prompt("", "Yes", "Well", "No")));
    }
}
