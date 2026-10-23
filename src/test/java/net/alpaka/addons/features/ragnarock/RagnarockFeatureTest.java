package net.alpaka.addons.features.ragnarock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagnarockFeatureTest {

    private static String without(String text) {
        RagnarockFeature.Match m = RagnarockFeature.find(text);
        return text.substring(0, m.start()) + text.substring(m.end());
    }

    @Test
    void readsTheCountdown() {
        assertEquals(3, RagnarockFeature.find("9,011/9,248❤     226❈     CASTING IN 3s").seconds());
        assertEquals(1, RagnarockFeature.find("§c§lCASTING IN 1s").seconds());
        assertEquals(0, RagnarockFeature.find("100❤     CASTING").seconds());
    }

    @Test
    void ignoresActionBarsWithoutACast() {
        assertNull(RagnarockFeature.find("§c9,011/9,248❤     §a226❈ Defense     §b500/500✎ Mana"));
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
        List<RagnarockFeature.Piece> pieces = List.of(
                new RagnarockFeature.Piece(red, "9,011/9,248❤     "),
                new RagnarockFeature.Piece(green, "226❈     CASTING IN "),
                new RagnarockFeature.Piece(red, "3s"));
        String full = "9,011/9,248❤     226❈     CASTING IN 3s";
        RagnarockFeature.Match m = RagnarockFeature.find(full);
        Component out = RagnarockFeature.cut(pieces, m.start(), m.end());
        assertEquals("9,011/9,248❤     226❈", out.getString());
        assertEquals(green, out.getSiblings().get(1).getStyle());
    }

    private static final float CAST_PITCH = 1.4920635f;

    @Test
    void ownCastSoundCountsWhileTheCastRuns() {
        assertTrue(RagnarockFeature.isOwnCast(true, CAST_PITCH, "entity.wolf.death", 0.25));
        assertTrue(RagnarockFeature.isOwnCast(true, CAST_PITCH, "entity.wolf_angry.death", 9.0));
    }

    @Test
    void anotherPlayersCastDoesNotCount() {
        // No cast of our own running: a teammate's cast must not announce one.
        assertFalse(RagnarockFeature.isOwnCast(false, CAST_PITCH, "entity.wolf.death", 0.0));
        // Our cast running, but the sound comes from a teammate a few blocks away.
        assertFalse(RagnarockFeature.isOwnCast(true, CAST_PITCH, "entity.wolf.death", 7.0 * 7.0));
    }

    @Test
    void otherSoundsDoNotCount() {
        assertFalse(RagnarockFeature.isOwnCast(true, 1.0f, "entity.wolf.death", 0.0));
        assertFalse(RagnarockFeature.isOwnCast(true, CAST_PITCH, "entity.wolf.hurt", 0.0));
    }
}
