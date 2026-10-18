package net.alpaka.addons.features.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    /** Radius, then the halvings and sample spacing the blur uses for it. */
    @ParameterizedTest(name = "radius {0} -> {1} levels at {2}")
    @CsvSource({
            "1,  1, 0.5",
            "2,  1, 1.0",
            "3,  1, 1.5",
            "5,  2, 1.25",
            "10, 3, 1.25",
            "15, 3, 1.875",
            "20, 4, 1.25",
    })
    void planGrowsWithTheRadius(int radius, int levels, float offset) {
        DualKawaseBlur.Plan plan = DualKawaseBlur.plan(radius);
        assertEquals(levels, plan.levels());
        assertEquals(offset, plan.offset(), 1e-6);
    }

    @Test
    void noRadiusMeansNoBlur() {
        assertEquals(DualKawaseBlur.Plan.NONE, DualKawaseBlur.plan(0));
    }

    /** Every radius the strength slider can reach fits in the halvings the blur has targets for. */
    @Test
    void everyRadiusFitsTheTargets() {
        for (int radius = 1; radius <= ChatBlurFeature.MAX_BLUR_RADIUS; radius++) {
            DualKawaseBlur.Plan plan = DualKawaseBlur.plan(radius);
            assertTrue(plan.levels() >= 1 && plan.levels() <= DualKawaseBlur.MAX_LEVELS, "radius " + radius);
        }
    }

    /** Glass under a veil covers the world exactly as much as the chat's tint on its own. */
    @ParameterizedTest(name = "tint {0}")
    @CsvSource({"0.0", "0.25", "0.5", "0.9", "1.0"})
    void glassAndVeilAddUpToTheTint(float tint) {
        float veil = tint * ChatBlurFeature.VEIL_SHARE;
        float glass = ChatBlurFeature.glassTint(tint, veil);
        assertEquals(tint, 1.0f - (1.0f - glass) * (1.0f - veil), 1e-5);
    }
}
