package net.alpaka.addons.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlpakaPrefixTest {

    @Test
    void readsAlpakaInBrackets() {
        assertEquals("[Alpaka] ", AlpakaPrefix.prefix().getString());
    }

    @Test
    void appendingAMessageLeavesTheNextPrefixAlone() {
        AlpakaPrefix.prefix().append("Hello");
        assertEquals("[Alpaka] ", AlpakaPrefix.prefix().getString());
    }
}
