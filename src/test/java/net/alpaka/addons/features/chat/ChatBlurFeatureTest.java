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
            // Full strength is the blur as it was before the slider: 3 per GUI scale step.
            "1, 100, 3",
            "2, 100, 6",
            "3, 100, 9",
            "4, 100, 12",
            "2, 50,  3",
            "4, 50,  6",
            "2, 0,   0",
            "4, 0,   0",
            // GUI scales past 4 (and "auto" on a huge window) blur like 4; 0 like 1.
            "6, 100, 12",
            "0, 100, 3",
            // Out-of-range strengths clamp.
            "2, 150, 6",
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
