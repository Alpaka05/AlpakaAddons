package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.features.dungeon.terminal.TerminalClicks.Timing;
import net.alpaka.addons.features.dungeon.terminal.TerminalClicks.Verdict;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guards on terminal clicks (design.md 1.4, compliance.md T1-T4, R1-R2). */
class TerminalClicksTest {

    /** As shipped: first-click and rate guards on, wrong clicks let through. */
    private static final TerminalSettings DEFAULTS = settings(true, 500, true, false);
    private static final BooleanSupplier RIGHT = () -> false;
    private static final BooleanSupplier WRONG = () -> true;
    /** Opened at 10 000 ms; no click yet. */
    private static final long OPEN = 10_000L;
    private static final long NEVER = Long.MIN_VALUE / 2;

    private static TerminalSettings settings(boolean firstClick, int ms, boolean rate, boolean blockWrong) {
        return new TerminalSettings(firstClick, ms, rate, blockWrong, false, false, false, 1.5f);
    }

    private static Timing at(long sinceOpenMs, long ticks, boolean pingsLive, long lastClickMs) {
        return new Timing(OPEN + sinceOpenMs, OPEN, ticks, pingsLive, lastClickMs);
    }

    private static Verdict decide(TerminalSettings s, TerminalKind kind, Timing t, BooleanSupplier wrong) {
        return TerminalClicks.decide(s, kind, t, wrong);
    }

    @Test
    void firstClickNeedsTimeAndServerTicks() {
        // 500 ms and ceil(500 / 50) = 10 ticks.
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.PANES, at(0, 0, true, NEVER), RIGHT));
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.PANES, at(499, 20, true, NEVER), RIGHT));
        // The time has passed, but the lagging server counted only 9 ticks.
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.PANES, at(900, 9, true, NEVER), RIGHT));
        assertEquals(Verdict.ALLOW, decide(DEFAULTS, TerminalKind.PANES, at(500, 10, true, NEVER), RIGHT));
    }

    @Test
    void withoutPingsTimeAloneDecides() {
        assertEquals(Verdict.ALLOW, decide(DEFAULTS, TerminalKind.PANES, at(500, 0, false, NEVER), RIGHT));
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.PANES, at(499, 0, false, NEVER), RIGHT));
    }

    @Test
    void pingsThatStopAfterTheTerminalOpenedStillCount() {
        // Pings arrived when it opened; the server froze after 3 ticks and its last ping is 2.1 s old.
        // The server's own check paused with it: the tick half of the guard stays.
        assertTrue(TerminalClicks.ticksCount(true, 3));
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.PANES,
                at(2100, 3, TerminalClicks.ticksCount(true, 3), NEVER), RIGHT));
        assertEquals(Verdict.ALLOW, decide(DEFAULTS, TerminalKind.PANES,
                at(2600, 10, TerminalClicks.ticksCount(true, 10), NEVER), RIGHT));
        // No pings when it opened and none since: time alone.
        assertFalse(TerminalClicks.ticksCount(false, 0));
        assertEquals(Verdict.ALLOW, decide(DEFAULTS, TerminalKind.PANES,
                at(500, 0, TerminalClicks.ticksCount(false, 0), NEVER), RIGHT));
        // Opened during a freeze, and the server has ticked since: the ticks count from then on.
        assertTrue(TerminalClicks.ticksCount(false, 1));
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.PANES,
                at(600, 1, TerminalClicks.ticksCount(false, 1), NEVER), RIGHT));
    }

    @Test
    void firstClickDelayIsClampedAndSwitchable() {
        TerminalSettings tooShort = settings(true, 100, true, false);
        assertEquals(Verdict.TOO_EARLY, decide(tooShort, TerminalKind.PANES, at(349, 50, true, NEVER), RIGHT));
        assertEquals(Verdict.TOO_EARLY, decide(tooShort, TerminalKind.PANES, at(400, 6, true, NEVER), RIGHT));
        assertEquals(Verdict.ALLOW, decide(tooShort, TerminalKind.PANES, at(350, 7, true, NEVER), RIGHT));
        TerminalSettings tooLong = settings(true, 5000, true, false);
        assertEquals(Verdict.TOO_EARLY, decide(tooLong, TerminalKind.PANES, at(799, 50, true, NEVER), RIGHT));
        assertEquals(Verdict.ALLOW, decide(tooLong, TerminalKind.PANES, at(800, 16, true, NEVER), RIGHT));
        TerminalSettings off = settings(false, 500, true, false);
        assertEquals(Verdict.ALLOW, decide(off, TerminalKind.PANES, at(0, 0, true, NEVER), RIGHT));
    }

    @Test
    void firstClickAppliesToMelodyToo() {
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.MELODY, at(100, 2, true, NEVER), RIGHT));
    }

    @Test
    void rateGuardIsOneServerTick() {
        long now = OPEN + 2000;
        assertEquals(Verdict.TOO_FAST, decide(DEFAULTS, TerminalKind.RUBIX, at(2000, 40, true, now - 49), RIGHT));
        assertEquals(Verdict.TOO_FAST, decide(DEFAULTS, TerminalKind.RUBIX, at(2000, 40, true, now), RIGHT));
        assertEquals(Verdict.ALLOW, decide(DEFAULTS, TerminalKind.RUBIX, at(2000, 40, true, now - 50), RIGHT));
        TerminalSettings off = settings(true, 500, false, false);
        assertEquals(Verdict.ALLOW, decide(off, TerminalKind.RUBIX, at(2000, 40, true, now - 1), RIGHT));
        // The first-click guard comes first.
        assertEquals(Verdict.TOO_EARLY, decide(DEFAULTS, TerminalKind.RUBIX, at(100, 2, true, OPEN + 90), RIGHT));
    }

    @Test
    void wrongClicksAreLetThroughUnlessSwitchedOn() {
        Timing late = at(5000, 100, true, NEVER);
        AtomicInteger asked = new AtomicInteger();
        BooleanSupplier counting = () -> {
            asked.incrementAndGet();
            return true;
        };
        assertEquals(Verdict.ALLOW, decide(DEFAULTS, TerminalKind.NUMBERS, late, counting));
        assertEquals(0, asked.get(), "with the refusal off the chest is not even solved for it");
        TerminalSettings block = settings(true, 500, true, true);
        assertEquals(Verdict.WRONG, decide(block, TerminalKind.NUMBERS, late, WRONG));
        assertEquals(Verdict.ALLOW, decide(block, TerminalKind.NUMBERS, late, RIGHT));
    }

    @Test
    void neverAWrongClickInMelody() {
        TerminalSettings block = settings(true, 500, true, true);
        AtomicInteger asked = new AtomicInteger();
        assertEquals(Verdict.ALLOW, decide(block, TerminalKind.MELODY, at(5000, 100, true, NEVER), () -> {
            asked.incrementAndGet();
            return true;
        }));
        assertEquals(0, asked.get());
    }

    @Test
    void refusalCountsPendingClicksAsDone() {
        // Click in Order: 1 was just clicked and the server has not answered; 2 is the right next click.
        Chests chest = Chests.rows(4);
        int count = 1;
        for (int slot : TerminalKind.NUMBERS.playSlots()) chest.pane(slot, "red", count++);
        TerminalSession.State state = new TerminalSession.State().withPending(11, 100L);
        TerminalSolution refusal = TerminalSolver.solve(TerminalKind.NUMBERS, "Click in order!", chest.list(), state, true);
        TerminalSettings block = settings(true, 500, true, true);
        Timing late = at(5000, 100, true, NEVER);
        assertEquals(Verdict.ALLOW, decide(block, TerminalKind.NUMBERS, late, () -> refusal.wrongClick(12, 0)));
        assertEquals(Verdict.WRONG, decide(block, TerminalKind.NUMBERS, late, () -> refusal.wrongClick(11, 0)),
                "the same pane twice");
        assertEquals(Verdict.WRONG, decide(block, TerminalKind.NUMBERS, late, () -> refusal.wrongClick(13, 0)));
    }

    @Test
    void shippedDefaults() throws IOException {
        String source = Files.readString(Path.of("src/main/java/net/alpaka/addons/config/AlpakaConfig.java"));
        assertEquals("false", field(source, "terminalBlockWrongClicks"), "wrong clicks are let through by default");
        assertEquals("true", field(source, "terminalFirstClickGuard"));
        assertEquals("500", field(source, "terminalFirstClickMs"));
        assertEquals("true", field(source, "terminalRateGuard"));
        assertEquals("false", field(source, "terminalPredictClicks"), "only server-confirmed highlights by default");
        assertEquals("false", field(source, "terminalHideOtherItems"));
    }

    private static String field(String source, String name) {
        Matcher m = Pattern.compile("public (?:boolean|int) " + name + "\\s*=\\s*(\\w+);").matcher(source);
        assertTrue(m.find(), name + " not found");
        return m.group(1);
    }
}
