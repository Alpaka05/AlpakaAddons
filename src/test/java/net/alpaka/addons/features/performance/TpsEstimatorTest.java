package net.alpaka.addons.features.performance;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpsEstimatorTest {

    @Test
    void unknownUntilTwoPackets() {
        TpsEstimator tps = new TpsEstimator();
        assertTrue(Float.isNaN(tps.tps(0)));
        tps.onTicks(1, 0);
        assertTrue(Float.isNaN(tps.tps(0)));
    }

    @Test
    void onePingEveryTickReadsTwenty() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i <= 200; i++) tps.onTicks(1, 50L * i);
        assertEquals(20.0f, tps.tps(10_000), 0.01f);
    }

    @Test
    void bunchedPingsStaySteady() {
        // Pings arriving in bursts - three at once, then a gap - as a network delivers them, read
        // at every moment in between: the reading must not swing.
        TpsEstimator tps = new TpsEstimator();
        Random random = new Random(1);
        long now = 0;
        float min = Float.MAX_VALUE, max = 0;
        for (int i = 0; i < 600; i++) {
            if (i % 3 == 0) now += 150 + random.nextInt(41) - 20;
            tps.onTicks(1, now);
            if (i > 200) {
                for (long t = now; t < now + 150; t += 16) {
                    float value = tps.tps(t);
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }
        assertTrue(min > 19.0f, "dipped to " + min);
        assertTrue(max - min < 1.0f, "swung between " + min + " and " + max);
    }

    @Test
    void slowServerReadsItsRate() {
        // Ten ticks a second.
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i <= 100; i++) tps.onTicks(1, 100L * i);
        assertEquals(10.0f, tps.tps(10_000), 0.01f);
    }

    @Test
    void timeUpdatesOnceASecondReadTwentyBetweenThem() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i <= 10; i++) tps.onTicks(20, 1000L * i);
        // Right before the next update is due, and a little after it was due.
        assertEquals(20.0f, tps.tps(10_990), 0.01f);
        assertEquals(20.0f, tps.tps(11_500), 0.01f);
    }

    @Test
    void stalledServerFalls() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i <= 100; i++) tps.onTicks(1, 50L * i);
        assertEquals(20.0f, tps.tps(5_000 + 900), 0.01f);
        float later = tps.tps(5_000 + 3_000);
        assertTrue(later < 13.0f, "a server silent for 3 s should read well below 20, was " + later);
    }

    @Test
    void longGapStartsOver() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i <= 10; i++) tps.onTicks(1, 50L * i);
        tps.onTicks(1, 100_000);
        assertTrue(Float.isNaN(tps.tps(100_000)));
    }
}
