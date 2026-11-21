package net.alpaka.addons.features.npc;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcQuickAnswerTest {

    private boolean wasEnabled;

    @BeforeEach
    void enable() {
        wasEnabled = AlpakaConfig.instance.npcQuickAnswerEnabled;
        AlpakaConfig.instance.npcQuickAnswerEnabled = true;
        NpcQuickAnswer.clear();
    }

    @AfterEach
    void restore() {
        AlpakaConfig.instance.npcQuickAnswerEnabled = wasEnabled;
        NpcQuickAnswer.clear();
    }

    private static MutableComponent option(String text, String command, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    /** The line as Hypixel sends it under Trevor's question. */
    private static Component prompt() {
        return Component.literal("Click an option: ").withStyle(ChatFormatting.YELLOW)
                .append(option("[YES]", "/selectnpcoption trapper r_2_1", ChatFormatting.GREEN))
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                .append(option("[NO]", "/selectnpcoption trapper r_2_2", ChatFormatting.RED));
    }

    @Test
    void readsBothCommands() {
        NpcQuickAnswer.Options options = NpcQuickAnswer.options(prompt());
        assertEquals("selectnpcoption trapper r_2_1", options.yes());
        assertEquals("selectnpcoption trapper r_2_2", options.no());
    }

    @Test
    void typedTextIsNoQuestion() {
        // A player can type the words, but not attach commands to them.
        assertNull(NpcQuickAnswer.options(Component.literal("Click an option: [YES] - [NO]")));
    }

    @Test
    void needsBothWords() {
        Component onlyYes = Component.literal("Click: ").append(option("[YES]", "/selectnpcoption a b", ChatFormatting.GREEN));
        assertNull(NpcQuickAnswer.options(onlyYes));
    }

    @Test
    void pendingUntilTimeout() {
        NpcQuickAnswer.onChat(prompt(), 1_000L);
        assertTrue(NpcQuickAnswer.isPending(1_000L + NpcQuickAnswer.TTL_MS));
        assertFalse(NpcQuickAnswer.isPending(1_001L + NpcQuickAnswer.TTL_MS));
        assertFalse(NpcQuickAnswer.isPending(1_000L));
    }

    @Test
    void clickingTheWordItselfAnswers() {
        NpcQuickAnswer.onChat(prompt(), 1_000L);
        NpcQuickAnswer.onComponentClicked(net.minecraft.network.chat.Style.EMPTY
                .withClickEvent(new ClickEvent.RunCommand("/selectnpcoption trapper r_2_2")));
        assertFalse(NpcQuickAnswer.isPending(1_000L));
    }

    @Test
    void offIgnoresQuestions() {
        AlpakaConfig.instance.npcQuickAnswerEnabled = false;
        NpcQuickAnswer.onChat(prompt(), 1_000L);
        AlpakaConfig.instance.npcQuickAnswerEnabled = true;
        assertFalse(NpcQuickAnswer.isPending(1_000L));
    }
}
