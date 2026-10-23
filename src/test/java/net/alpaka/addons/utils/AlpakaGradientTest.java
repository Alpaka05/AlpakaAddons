package net.alpaka.addons.utils;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlpakaGradientTest {

    @Test
    void keepsTextAndRunsFromFirstToLastStop() {
        Component c = AlpakaGradient.of("AlpakaAddons", new int[]{0xFF0000, 0x00FF00, 0x0000FF});
        assertEquals("AlpakaAddons", c.getString());
        assertEquals(0xFF0000, c.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(0x0000FF, c.getSiblings().get(11).getStyle().getColor().getValue());
    }

    @Test
    void usesTheModColorsByDefault() {
        var letters = AlpakaGradient.of("Alpaka").getSiblings();
        assertEquals(6, letters.size());
        assertEquals(0xFFC24B, letters.get(0).getStyle().getColor().getValue());
        assertEquals(0x9B7BFF, letters.get(5).getStyle().getColor().getValue());
    }
}
