package net.alpaka.addons.features.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpsEstimatorTest {

    @Test
    void unknownUntilTwoUpdates() {
        TpsEstimator tps = new TpsEstimator();
        assertTrue(Float.isNaN(tps.tps(0)));
        tps.onTimeUpdate(1000, 0);
        assertTrue(Float.isNaN(tps.tps(0)));
    }

    @Test
    void fullSpeedServerReadsTwenty() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i < 10; i++) tps.onTimeUpdate(1000 + 20L * i, 1000L * i);
        assertEquals(20.0f, tps.tps(9000), 0.01f);
    }

    @Test
    void slowServerReadsItsRate() {
        // 20 ticks every 2 seconds: half speed.
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i < 10; i++) tps.onTimeUpdate(20L * i, 2000L * i);
        assertEquals(10.0f, tps.tps(18000), 0.01f);
    }

    @Test
    void pinnedGameTimeCountsTwentyTicksPerUpdate() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i < 6; i++) tps.onTimeUpdate(5000, 1000L * i);
        assertEquals(20.0f, tps.tps(5000), 0.01f);
    }

    @Test
    void silenceDragsTheRateDown() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i < 6; i++) tps.onTimeUpdate(20L * i, 1000L * i);
        float later = tps.tps(5000 + 5000);
        assertTrue(later < 12.0f, "a server silent for 5 s should read well below 20, was " + later);
    }

    @Test
    void longGapStartsOver() {
        TpsEstimator tps = new TpsEstimator();
        for (int i = 0; i < 6; i++) tps.onTimeUpdate(20L * i, 1000L * i);
        tps.onTimeUpdate(999_999, 100_000);
        assertTrue(Float.isNaN(tps.tps(100_000)));
    }
}
