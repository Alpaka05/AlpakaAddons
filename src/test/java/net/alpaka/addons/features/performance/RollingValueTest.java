package net.alpaka.addons.features.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollingValueTest {
    @Test
    void firstValueAppearsWithoutRolling() {
        RollingValue value = new RollingValue();
        value.set("60", 0xFFFFFFFF, 60, 1000L);
        assertFalse(value.animating(1000L));
    }

    @Test
    void changeRollsForTheDurationOnly() {
        RollingValue value = new RollingValue();
        value.set("60", 0xFFFFFFFF, 60, 1000L);
        value.set("61", 0xFFFFFFFF, 61, 2000L);
        assertTrue(value.animating(2000L));
        assertTrue(value.animating(2000L + RollingValue.DURATION_MS - 1));
        assertFalse(value.animating(2000L + RollingValue.DURATION_MS));
    }

    @Test
    void sameTextDoesNotRoll() {
        RollingValue value = new RollingValue();
        value.set("20.0", 0xFF55FF55, 200, 1000L);
        value.set("20.0", 0xFFFFAA00, 200, 2000L);
        assertFalse(value.animating(2000L));
    }
}
