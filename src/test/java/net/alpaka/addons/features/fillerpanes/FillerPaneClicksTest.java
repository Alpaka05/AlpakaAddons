package net.alpaka.addons.features.fillerpanes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillerPaneClicksTest {
    @Test
    void blankNamesAreFillers() {
        assertTrue(FillerPaneClicks.isBlankName(" "));
        assertTrue(FillerPaneClicks.isBlankName(""));
        assertTrue(FillerPaneClicks.isBlankName("§7 "));
        assertTrue(FillerPaneClicks.isBlankName("§r"));
    }

    @Test
    void namedPanesAreNot() {
        assertFalse(FillerPaneClicks.isBlankName("Black Stained Glass Pane"));
        assertFalse(FillerPaneClicks.isBlankName("§aClose"));
        assertFalse(FillerPaneClicks.isBlankName("§cLocked"));
    }
}
