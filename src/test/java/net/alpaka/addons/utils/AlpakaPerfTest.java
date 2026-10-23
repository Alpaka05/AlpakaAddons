package net.alpaka.addons.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The switch on the mod's own profiler: nothing while off, totals while and after it is on. */
class AlpakaPerfTest {

    @AfterEach
    void off() {
        AlpakaPerf.stop();
        AlpakaPerf.reset();
    }

    private static String line(List<String> report, String section) {
        return report.stream().filter(line -> line.startsWith(section + "  ")).findFirst().orElse(null);
    }

    @Test
    void offFromLaunchAndNothingIsRecorded() {
        AlpakaPerf.reset();
        long t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_SLAYER);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_SLAYER, t);
        AlpakaPerf.count(AlpakaPerf.Section.ENTITY_RULES);
        List<String> report = AlpakaPerf.report();
        assertEquals(1, report.size());
        assertTrue(report.get(0).startsWith("Not measuring."), report.get(0));
    }

    @Test
    void measuresBetweenStartAndStop() {
        AlpakaPerf.start();
        long t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_SLAYER);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_SLAYER, t);
        AlpakaPerf.count(AlpakaPerf.Section.ENTITY_RULES);
        AlpakaPerf.count(AlpakaPerf.Section.ENTITY_RULES);
        AlpakaPerf.stop();
        // Recorded after the stop: left out.
        AlpakaPerf.count(AlpakaPerf.Section.ENTITY_RULES);

        List<String> report = AlpakaPerf.report();
        assertTrue(report.get(0).startsWith("Measured over"), report.get(0));
        assertTrue(line(report, "hud_slayer") != null, String.join("\n", report));
        assertTrue(line(report, "entity_rules").contains("counted only"), String.join("\n", report));
        assertTrue(report.get(report.size() - 1).contains("GC:"), String.join("\n", report));
    }

    @Test
    void aSectionBegunWhileOffEndsQuietlyAfterAStart() {
        long t = AlpakaPerf.begin(AlpakaPerf.Section.CHAT_EXTRACT);
        AlpakaPerf.start();
        AlpakaPerf.end(AlpakaPerf.Section.CHAT_EXTRACT, t);
        assertEquals(null, line(AlpakaPerf.report(), "chat_extract"));
    }

    @Test
    void aSlowFrameIsKeptWithItsPhases() {
        AlpakaPerf.start();
        AlpakaPerf.finishFrame(2_000_000L);
        AlpakaPerf.phaseEnd(AlpakaPerf.Phase.PRESENT, System.nanoTime() - 150_000_000L);
        AlpakaPerf.finishFrame(180_000_000L);

        List<String> report = AlpakaPerf.report();
        String all = String.join("\n", report);
        assertTrue(all.contains("Frames 2,"), all);
        assertTrue(all.contains("50 ms+: 1, 100 ms+: 1, 250 ms+: 0"), all);
        String spike = report.get(report.indexOf("Worst frames (ms):") + 1);
        assertTrue(spike.matches("\\d\\d:\\d\\d:\\d\\d  180 ms.*· present 1\\d\\d · other \\d+.*"), spike);
        assertTrue(report.get(report.size() - 1).contains("GC:"), all);
    }

    @Test
    void theLimitersSleepIsNoSpike() {
        AlpakaPerf.start();
        AlpakaPerf.phaseEnd(AlpakaPerf.Phase.LIMITER, System.nanoTime() - 95_000_000L);
        AlpakaPerf.finishFrame(100_000_000L);
        String all = String.join("\n", AlpakaPerf.report());
        assertTrue(all.contains("50 ms+: 0"), all);
        assertTrue(!all.contains("Worst frames"), all);
    }

    @Test
    void zgcPausesAreToldFromItsCycles() {
        assertTrue(AlpakaPerf.isPause("ZGC Minor Pauses"));
        assertTrue(!AlpakaPerf.isPause("ZGC Major Cycles"));
        assertTrue(AlpakaPerf.isPause("G1 Young Generation"));
        assertTrue(!AlpakaPerf.isPause("G1 Concurrent GC"));
    }
}
