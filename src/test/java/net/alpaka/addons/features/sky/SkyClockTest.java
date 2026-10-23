package net.alpaka.addons.features.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyClockTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void startsAtZeroAndCountsSecondsAtTheGivenSpeed() {
        SkyClock clock = new SkyClock();
        assertEquals(0.0f, clock.advance(5 * SECOND, 1.0f));
        assertEquals(0.1f, clock.advance(5 * SECOND + SECOND / 10, 1.0f), 1e-6f);
        assertEquals(0.3f, clock.advance(5 * SECOND + 2 * SECOND / 10, 2.0f), 1e-6f);
    }

    @Test
    void changingTheSpeedDoesNotJump() {
        SkyClock clock = new SkyClock();
        long now = 0L;
        clock.advance(now, 1.0f);
        for (int i = 0; i < 100; i++) clock.advance(now += SECOND / 10, 1.0f);
        float before = clock.advance(now, 1.0f);
        // The same moment at three times the speed: nothing has passed, so nothing moves.
        assertEquals(before, clock.advance(now, 3.0f));
        assertEquals(before + 0.3f, clock.advance(now + SECOND / 10, 3.0f), 1e-4f);
    }

    @Test
    void aLongGapMovesTheSkyOnByOneShortStepOnly() {
        SkyClock clock = new SkyClock();
        clock.advance(0L, 1.0f);
        assertEquals((float) SkyClock.MAX_STEP_SECONDS, clock.advance(600 * SECOND, 1.0f), 1e-6f);
    }

    @Test
    void aClockGoingBackwardsMovesNothing() {
        SkyClock clock = new SkyClock();
        clock.advance(10 * SECOND, 1.0f);
        assertEquals(0.0f, clock.advance(9 * SECOND, 1.0f));
    }

    @Test
    void wrapsAfterFourHours() {
        SkyClock clock = new SkyClock();
        long now = 0L;
        clock.advance(now, 1.0f);
        float last = 0.0f;
        boolean wrapped = false;
        // Four hours and a bit, in steps the clock takes whole.
        for (int i = 0; i < (int) (SkyClock.WRAP_SECONDS / 0.2) + 10; i++) {
            float time = clock.advance(now += SECOND / 5, 1.0f);
            if (time < last) wrapped = true;
            last = time;
        }
        assertTrue(wrapped);
        assertEquals(2.0f, last, 0.01f);
    }
}
