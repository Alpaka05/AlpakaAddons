package net.alpaka.addons.features.teleportwarp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The camera's glide: the whole way ahead at the start, none at the end, only ever closing in. */
class SmoothTeleportGlideTest {

    @Test
    void startsAtTheOldPlaceAndEndsAtTheNewOne() {
        assertEquals(1.0f, SmoothTeleport.remaining(0.0f), 1e-6f);
        assertEquals(0.0f, SmoothTeleport.remaining(1.0f), 1e-6f);
        assertEquals(0.0f, SmoothTeleport.remaining(1.5f), 1e-6f);
    }

    @Test
    void onlyEverClosesIn() {
        float previous = 1.0f;
        for (float p = 0.05f; p <= 1.0f; p += 0.05f) {
            float now = SmoothTeleport.remaining(p);
            assertTrue(now <= previous, "moved back at " + p);
            previous = now;
        }
    }
}
