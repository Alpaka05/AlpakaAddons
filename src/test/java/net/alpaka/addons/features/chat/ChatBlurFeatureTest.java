package net.alpaka.addons.features.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBlurFeatureTest {
    /** GUI scale, Blur Strength in percent, and the radius in screen pixels. */
    @ParameterizedTest(name = "scale {0} at {1}% -> radius {2}")
    @CsvSource({
            // Full strength: 5 per GUI scale step.
            "1, 100, 5",
            "2, 100, 10",
            "3, 100, 15",
            "4, 100, 20",
            "2, 50,  5",
            "4, 50,  10",
            "2, 0,   0",
            "4, 0,   0",
            // GUI scales past 4 (and "auto" on a huge window) blur like 4; 0 like 1.
            "6, 100, 20",
            "0, 100, 5",
            // Out-of-range strengths clamp.
            "2, 150, 10",
            "2, -10, 0",
    })
    void radiusScalesWithGuiScaleAndStrength(int guiScale, float strength, int radius) {
        assertEquals(radius, ChatBlurFeature.blurRadius(guiScale, strength));
    }

    /** A radius without its post effect would leave the panel unblurred, with only a log warning. */
    @Test
    void everyRadiusHasItsPostEffect() {
        for (int radius = 1; radius <= ChatBlurFeature.MAX_BLUR_RADIUS; radius++) {
            Path effect = Path.of("src/main/resources/assets/alpaka/post_effect/chat_blur_r" + radius + ".json");
            assertTrue(Files.exists(effect), effect + " is missing");
        }
    }
}
