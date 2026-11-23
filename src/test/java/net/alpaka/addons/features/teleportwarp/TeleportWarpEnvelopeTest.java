package net.alpaka.addons.features.teleportwarp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The warp's strength over time: up fast, then fading to nothing exactly at its duration. */
class TeleportWarpEnvelopeTest {

    @Test
    void startsAndEndsAtNothing() {
        assertEquals(0.0f, TeleportWarpFeature.envelope(0.0f, 0.45f), 1e-6f);
        assertEquals(0.0f, TeleportWarpFeature.envelope(0.45f, 0.45f), 1e-6f);
        assertEquals(0.0f, TeleportWarpFeature.envelope(2.0f, 0.45f), 1e-6f);
        assertEquals(0.0f, TeleportWarpFeature.envelope(-0.1f, 0.45f), 1e-6f);
    }

    @Test
    void peaksAfterTheRiseThenOnlyFades() {
        assertEquals(1.0f, TeleportWarpFeature.envelope(0.07f, 0.45f), 1e-4f);
        float previous = 1.0f;
        for (float t = 0.08f; t < 0.45f; t += 0.01f) {
            float now = TeleportWarpFeature.envelope(t, 0.45f);
            assertTrue(now <= previous, "rose again at " + t);
            assertTrue(now >= 0.0f && now <= 1.0f);
            previous = now;
        }
    }
}
