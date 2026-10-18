package net.alpaka.addons.features.scoreboard;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardFooterFeatureTest {

    @Test
    void recognisesHypixelAddress() {
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("www.hypixel.net"));
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("§ewww.hypixel.net§r "));
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("alpha.hypixel.net"));
        // Hypixel's invisible emoji that keep each line unique, also mid-word.
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("§ewww.hypixel.ne\uD83C\uDF82§et"));
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("\u26BD§ewww.hypixel.net"));
    }

    @Test
    void leavesOtherLinesAlone() {
        assertFalse(ScoreboardFooterFeature.isHypixelFooter("Purse: 1,234"));
        assertFalse(ScoreboardFooterFeature.isHypixelFooter("Visit www.hypixel.net/store"));
        assertFalse(ScoreboardFooterFeature.isHypixelFooter(""));
    }

    @Test
    void gradientKeepsTextAndRunsFromFirstToLastStop() {
        Component c = ScoreboardFooterFeature.gradient("AlpakaAddons", new int[]{0xFF0000, 0x00FF00, 0x0000FF});
        assertEquals("AlpakaAddons", c.getString());
        assertEquals(0xFF0000, c.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(0x0000FF, c.getSiblings().get(11).getStyle().getColor().getValue());
    }
}
