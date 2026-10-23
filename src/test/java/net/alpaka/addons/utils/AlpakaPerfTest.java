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
        assertTrue(report.get(0).startsWith("measured over"), report.get(0));
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
}
