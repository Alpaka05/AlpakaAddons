package net.alpaka.addons.features.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RancherSpeedsTest {

    @Test
    void recognisesTheSpeedCapSign() {
        assertTrue(RancherSpeeds.isSpeedCap(SignPrompt.of(new String[] {"", "^^^^^^", "Set your", "speed cap!"})));
        assertTrue(RancherSpeeds.isSpeedCap(SignPrompt.of(new String[] {"", "^^^^^^", "Set your Garden's", "speed cap!"})));
        assertFalse(RancherSpeeds.isSpeedCap(SignPrompt.of(new String[] {"", "^^^^^^^^^^^^^^^", "Enter amount", "to order"})));
        assertFalse(RancherSpeeds.isSpeedCap(null));
    }
}
