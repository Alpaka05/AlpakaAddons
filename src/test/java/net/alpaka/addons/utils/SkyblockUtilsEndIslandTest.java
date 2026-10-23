package net.alpaka.addons.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyblockUtilsEndIslandTest {

    @Test
    void everyZoneOfTheEndIslandCounts() {
        assertTrue(SkyblockUtils.isEndIslandZoneLine("⏣ The End"));
        assertTrue(SkyblockUtils.isEndIslandZoneLine("⏣ Dragon's Nest"));
        assertTrue(SkyblockUtils.isEndIslandZoneLine("⏣ Void Sepulture"));
        assertTrue(SkyblockUtils.isEndIslandZoneLine("⏣ Zealot Bruiser Hideout"));
        assertTrue(SkyblockUtils.isEndIslandZoneLine(" ⏣The End"));
    }

    @Test
    void otherZonesAndLinesWithoutTheMarkerDoNot() {
        assertFalse(SkyblockUtils.isEndIslandZoneLine("⏣ Hub"));
        assertFalse(SkyblockUtils.isEndIslandZoneLine("⏣ The Wasteland"));
        assertFalse(SkyblockUtils.isEndIslandZoneLine("⏣ Smoldering Tomb"));
        assertFalse(SkyblockUtils.isEndIslandZoneLine("The End"));
        assertFalse(SkyblockUtils.isEndIslandZoneLine("Late Autumn 15th"));
    }
}
