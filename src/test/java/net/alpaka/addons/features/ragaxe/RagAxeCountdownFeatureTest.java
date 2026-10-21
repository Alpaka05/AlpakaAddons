package net.alpaka.addons.features.ragaxe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RagAxeCountdownFeatureTest {

    private static String without(String text) {
        RagAxeCountdownFeature.Match m = RagAxeCountdownFeature.find(text);
        return text.substring(0, m.start()) + text.substring(m.end());
    }

    @Test
    void readsTheCountdown() {
        assertEquals(3, RagAxeCountdownFeature.find("9,011/9,248❤     226❈     CASTING IN 3s").seconds());
        assertEquals(1, RagAxeCountdownFeature.find("§c§lCASTING IN 1s").seconds());
        assertEquals(0, RagAxeCountdownFeature.find("100❤     CASTING").seconds());
    }

    @Test
    void ignoresActionBarsWithoutACast() {
        assertNull(RagAxeCountdownFeature.find("§c9,011/9,248❤     §a226❈ Defense     §b500/500✎ Mana"));
    }

    @Test
    void cutsTheCastAndItsGapOut() {
        assertEquals("§c9,011/9,248❤     §a226❈",
                without("§c9,011/9,248❤     §a226❈     §c§lCASTING IN 3s"));
        assertEquals("§c9,011/9,248❤     §a226❈",
                without("§c9,011/9,248❤     §c§lCASTING IN 2s§r     §a226❈"));
        assertEquals("§a226❈", without("§c§lCASTING IN 2s     §a226❈"));
        assertEquals("", without("CASTING IN 3s"));
    }

    @Test
    void keepsTheStylesOfTheRest() {
        Style red = Style.EMPTY.withColor(ChatFormatting.RED);
        Style green = Style.EMPTY.withColor(ChatFormatting.GREEN);
        List<RagAxeCountdownFeature.Piece> pieces = List.of(
                new RagAxeCountdownFeature.Piece(red, "9,011/9,248❤     "),
                new RagAxeCountdownFeature.Piece(green, "226❈     CASTING IN "),
                new RagAxeCountdownFeature.Piece(red, "3s"));
        String full = "9,011/9,248❤     226❈     CASTING IN 3s";
        RagAxeCountdownFeature.Match m = RagAxeCountdownFeature.find(full);
        Component out = RagAxeCountdownFeature.cut(pieces, m.start(), m.end());
        assertEquals("9,011/9,248❤     226❈", out.getString());
        assertEquals(green, out.getSiblings().get(1).getStyle());
    }
}
