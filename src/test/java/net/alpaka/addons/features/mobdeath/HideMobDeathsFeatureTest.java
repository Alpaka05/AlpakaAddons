package net.alpaka.addons.features.mobdeath;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HideMobDeathsFeatureTest {

    @Test
    void zeroHealthNametagsAreDead() {
        assertTrue(HideMobDeathsFeature.readsZeroHealth("[Lv12] Zombie 0/100❤"));
        assertTrue(HideMobDeathsFeature.readsZeroHealth("[Lv390] Voidgloom Seraph 0/1.2M❤"));
        assertTrue(HideMobDeathsFeature.readsZeroHealth("﴾ Revenant Horror 0❤ ﴿"));
    }

    @Test
    void livingNametagsAreNot() {
        assertFalse(HideMobDeathsFeature.readsZeroHealth("[Lv12] Zombie 100/100❤"));
        assertFalse(HideMobDeathsFeature.readsZeroHealth("[Lv12] Zombie 10/100❤"));
        assertFalse(HideMobDeathsFeature.readsZeroHealth("Revenant Horror 0.5M❤"));
        assertFalse(HideMobDeathsFeature.readsZeroHealth("Revenant Horror 1,000❤"));
        assertFalse(HideMobDeathsFeature.readsZeroHealth("Zombie Villager"));
    }
}
