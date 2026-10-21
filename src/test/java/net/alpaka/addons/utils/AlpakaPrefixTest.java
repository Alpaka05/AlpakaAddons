package net.alpaka.addons.utils;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class AlpakaPrefixTest {

    @Test
    void readsAlpakaInBrackets() {
        assertEquals("[Alpaka] ", AlpakaPrefix.prefix().getString());
    }

    @Test
    void gradientRunsFromFirstToLastStop() {
        Component name = AlpakaPrefix.gradient("Alpaka", AlpakaPrefix.STOPS);
        var letters = name.getSiblings();
        assertEquals(6, letters.size());
        assertEquals(0xFFC24B, letters.get(0).getStyle().getColor().getValue());
        assertEquals(0x9B7BFF, letters.get(5).getStyle().getColor().getValue());
    }

    @Test
    void everyMessageGetsItsOwnPrefix() {
        assertNotSame(AlpakaPrefix.prefix(), AlpakaPrefix.prefix());
    }
}
