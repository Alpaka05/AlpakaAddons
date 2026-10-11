package net.alpaka.addons.features.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The call sequences the two chat mixins drive, without the game: a packet's message, a mod's line
 * sent from a receive event while that message is being handled, and a mod's line on its own.
 */
class ChatOriginTest {

    /** What ChatListenerMixin does around one handleSystemMessage call that adds its message. */
    private static boolean listenerAdds(Runnable insideEvents) {
        ChatOrigin.enterListener();
        try {
            insideEvents.run(); // other mods' receive events run before the add
            boolean server = ChatOrigin.beginAdd();
            ChatOrigin.endAdd();
            return server;
        } finally {
            ChatOrigin.leaveListener();
        }
    }

    @Test
    void packetMessageIsServerAndAModLineSentFromItsEventIsNot() {
        boolean[] nested = new boolean[1];
        int previous = ChatOrigin.beginPacket();
        boolean outer;
        try {
            outer = listenerAdds(() -> nested[0] = listenerAdds(() -> { }));
        } finally {
            ChatOrigin.endPacket(previous);
        }
        assertTrue(outer, "the packet's own message");
        assertFalse(nested[0], "a mod's line sent while the packet's message was handled");
    }

    @Test
    void aModLineOutsideAnyPacketIsNotServer() {
        assertFalse(listenerAdds(() -> { }));
    }

    @Test
    void nothingIsLeftFlaggedAfterAPacket() {
        int previous = ChatOrigin.beginPacket();
        try {
            listenerAdds(() -> { });
        } finally {
            ChatOrigin.endPacket(previous);
        }
        assertFalse(listenerAdds(() -> { }), "a later mod line");
    }
}
