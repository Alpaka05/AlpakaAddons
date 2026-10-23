package net.alpaka.addons.features.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingSmootherTest {

    @Test
    void nothingToShowAtFirst() {
        assertEquals(-1, new PingSmoother().value(0));
    }

    @Test
    void firstAnswerShowsAtOnce() {
        PingSmoother ping = new PingSmoother();
        ping.add(87, 0);
        assertEquals(87, ping.value(10));
    }

    @Test
    void manyAnswersASecondChangeTheValueAtMostOnceASecond() {
        // Answers every 50 ms, alternating by two milliseconds, as F3's chart or another mod
        // delivers them; read every frame for ten seconds.
        PingSmoother ping = new PingSmoother();
        int changes = 0;
        int last = -1;
        for (long t = 0; t <= 10_000; t += 16) {
            if (t % 50 < 16) ping.add((t / 50) % 2 == 0 ? 40 : 42, t);
            int value = ping.value(t);
            if (value != last) changes++;
            last = value;
            assertTrue(value >= 40 && value <= 42, "outside the round trips: " + value);
        }
        assertTrue(changes <= 11, "changed " + changes + " times in ten seconds");
    }

    @Test
    void averagesTheRecentRoundTrips() {
        PingSmoother ping = new PingSmoother();
        ping.add(100, 0);
        ping.add(120, 5_000);
        assertEquals(110, ping.value(6_000));
    }

    @Test
    void oldRoundTripsDropOut() {
        PingSmoother ping = new PingSmoother();
        ping.add(300, 0);
        ping.add(100, 9_000);
        assertEquals(100, ping.value(12_000));
    }

    @Test
    void goesBlankWhenNoAnswerComes() {
        PingSmoother ping = new PingSmoother();
        ping.add(80, 0);
        assertEquals(80, ping.value(15_000));
        assertEquals(-1, ping.value(25_000));
    }
}
