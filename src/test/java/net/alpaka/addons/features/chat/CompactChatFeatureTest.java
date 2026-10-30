package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactChatFeatureTest {
    private static final String SEP = "§b-----------------------------------------------------";
    private static final String SOLD = "§eYou collected §63,623,400 coins §efrom selling §5Archfiend Dice";

    /** Newest first, like ChatComponent's lists. */
    private final List<GuiMessage> messages = new ArrayList<>();
    private final List<GuiMessage.Line> lines = new ArrayList<>();
    private int window;

    @BeforeEach
    void setUp() {
        window = AlpakaConfig.instance.compactChatWindowSeconds;
        AlpakaConfig.instance.compactChatEnabled = true;
        CompactChatFeature.forget();
    }

    @AfterEach
    void tearDown() {
        AlpakaConfig.instance.compactChatWindowSeconds = window;
        CompactChatFeature.forget();
    }

    private void add(String text) {
        Component stored = CompactChatFeature.compact(Component.literal(text), messages, lines);
        GuiMessage message = new GuiMessage(0, stored, null, GuiMessageSource.SYSTEM_SERVER, null);
        CompactChatFeature.onAdded(message);
        messages.addFirst(message);
        lines.addFirst(new GuiMessage.Line(message, null, true));
    }

    private List<String> chat() {
        return messages.reversed().stream().map(m -> m.content().getString()).toList();
    }

    private void addBoxed(String text) {
        add(SEP);
        add(text);
        add(SEP);
    }

    @Test
    void foldedBoxTakesItsEdgesAlong() {
        AlpakaConfig.instance.compactChatWindowSeconds = 30;
        addBoxed(SOLD);
        addBoxed(SOLD);
        addBoxed(SOLD);
        assertEquals(List.of(SEP, SOLD + " (x3)", SEP), chat());
        assertEquals(3, lines.size());
    }

    @Test
    void boxEdgesAreFoundPastOtherMessages() {
        AlpakaConfig.instance.compactChatWindowSeconds = 30;
        addBoxed(SOLD);
        add("other");
        addBoxed(SOLD);
        assertEquals(List.of("other", SEP, SOLD + " (x2)", SEP), chat());
    }

    @Test
    void blankLinesInsideTheBoxGoToo() {
        AlpakaConfig.instance.compactChatWindowSeconds = 30;
        for (int i = 0; i < 2; i++) {
            add(SEP);
            add(" ");
            add(SOLD);
            add(" ");
            add(SEP);
        }
        assertEquals(List.of(SEP, " ", SOLD + " (x2)", " ", SEP), chat());
    }

    @Test
    void unboxedMessageLeavesNeighbouringEdgesAlone() {
        AlpakaConfig.instance.compactChatWindowSeconds = 30;
        add(SEP);
        add("box");
        add(SEP);
        add(SOLD);
        add(SOLD);
        assertEquals(List.of(SEP, "box", SEP, SOLD + " (x2)"), chat());
    }

    @Test
    void backToBackBoxesFoldWithoutAWindow() {
        AlpakaConfig.instance.compactChatWindowSeconds = 0;
        addBoxed(SOLD);
        addBoxed(SOLD);
        assertEquals(List.of(SEP, SOLD + " (x2)", SEP), chat());
    }
}
