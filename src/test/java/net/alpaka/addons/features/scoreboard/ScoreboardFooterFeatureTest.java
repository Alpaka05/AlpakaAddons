package net.alpaka.addons.features.scoreboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardFooterFeatureTest {

    @Test
    void recognisesHypixelAddress() {
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("www.hypixel.net"));
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("§ewww.hypixel.net§r "));
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("alpha.hypixel.net"));
        // As Hypixel sends it: a made-up code in the middle keeps the line unique.
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("§ewww.hypixel.ne§g§et"));
        // Hypixel's invisible emoji that keep each line unique, also mid-word.
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("§ewww.hypixel.ne\uD83C\uDF82§et"));
        assertTrue(ScoreboardFooterFeature.isHypixelFooter("\u26BD§ewww.hypixel.net"));
    }

    @Test
    void leavesOtherLinesAlone() {
        assertFalse(ScoreboardFooterFeature.isHypixelFooter("Purse: 1,234"));
        assertFalse(ScoreboardFooterFeature.isHypixelFooter("Visit www.hypixel.net/store"));
        assertFalse(ScoreboardFooterFeature.isHypixelFooter(""));
        // Hypixel's other address line, the one pointing at a store page.
        assertFalse(ScoreboardFooterFeature.isHypixelFooter("§f§ehypixel.net/§p§eptl"));
    }
}
