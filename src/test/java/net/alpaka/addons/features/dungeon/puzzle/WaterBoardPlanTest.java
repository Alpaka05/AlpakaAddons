package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.puzzle.WaterBoardPlan.Kind;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoardPlan.Mark;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoardPlan.Phase;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoardPlan.Step;
import net.alpaka.addons.features.dungeon.puzzle.WaterBoardPlan.Tone;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.alpaka.addons.features.dungeon.puzzle.WaterBoardLever.COAL;
import static net.alpaka.addons.features.dungeon.puzzle.WaterBoardLever.DIAMOND;
import static net.alpaka.addons.features.dungeon.puzzle.WaterBoardLever.EMERALD;
import static net.alpaka.addons.features.dungeon.puzzle.WaterBoardLever.GOLD;
import static net.alpaka.addons.features.dungeon.puzzle.WaterBoardLever.QUARTZ;
import static net.alpaka.addons.features.dungeon.puzzle.WaterBoardLever.TERRACOTTA;
import static net.alpaka.addons.features.dungeon.puzzle.WaterBoardLever.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Playing out a solution: order, flips, confirmation, the clock and the labels (puzzles-water.md 7.4, 7.5, 8.3). */
class WaterBoardPlanTest {

    private static WaterBoardSolutions solutions;

    @BeforeAll
    static void load() throws IOException {
        solutions = WaterBoardSolutionsTest.shipped();
    }

    private static WaterBoardPlan standard(int variant, String doors, WaterBoardLever... flipped) {
        return plan(WaterBoardSolutions.Table.STANDARD, variant, doors, flipped);
    }

    private static WaterBoardPlan plan(WaterBoardSolutions.Table table, int variant, String doors, WaterBoardLever... flipped) {
        Map<WaterBoardLever, double[]> solution = solutions.get(table, variant, doors);
        Set<WaterBoardLever> set = EnumSet.noneOf(WaterBoardLever.class);
        set.addAll(List.of(flipped));
        return WaterBoardPlan.of(solution, set);
    }

    /** The order as "lever kind ticks" strings. */
    private static List<String> order(WaterBoardPlan plan) {
        return plan.order().stream().map(Step::toString).toList();
    }

    /** The label lines of every lever, in mark order, as "lever: line | line". */
    private static List<String> labels(WaterBoardPlan plan, long now) {
        return plan.marks(now).stream()
                .map(mark -> mark.lever().key() + ": " + String.join(" | ", mark.lines().stream().map(WaterBoardPlan.Line::text).toList()))
                .toList();
    }

    private static Mark mark(WaterBoardPlan plan, WaterBoardLever lever, long now) {
        return plan.marks(now).stream().filter(m -> m.lever() == lever).findFirst().orElseThrow();
    }

    // --- 8.3.1 ---

    @Test
    void flipsFirstThenTheWaterThenTheTimedFlips() {
        WaterBoardPlan plan = standard(1, "012");
        assertEquals(List.of("gold PRE @0", "quartz PRE @0", "diamond PRE @0", "terracotta PRE @0", "water WATER_ON @0",
                "emerald TIMED @158", "quartz TIMED @200"), order(plan));
        assertEquals(Phase.READY, plan.phase());
        assertEquals(List.of("gold: #1 Flip", "quartz: #2 Flip | #7 10.0s", "diamond: #3 Flip", "terracotta: #4 Flip",
                "water: #5 Wait", "emerald: #6 7.9s"), labels(plan, 0));

        // The four flips, by teammates: the water may go on now.
        for (WaterBoardLever lever : List.of(GOLD, QUARTZ, DIAMOND, TERRACOTTA)) plan.observedFlip(lever, 10, 0);
        assertEquals(List.of("water: #1 Water On", "emerald: #2 7.9s", "quartz: #3 10.0s"), labels(plan, 20));
        assertEquals(Tone.SOLUTION, mark(plan, WATER, 20).tone());
    }

    // --- 8.3.2 ---

    @Test
    void leversFlippedBeforeTheSolverSawTheBoard() {
        // Gold's flip was wanted and is done; emerald's was not and has to be undone first.
        WaterBoardPlan plan = standard(1, "012", GOLD, EMERALD);
        assertEquals(List.of("emerald FLIP_BACK @0", "quartz PRE @0", "diamond PRE @0", "terracotta PRE @0",
                "water WATER_ON @0", "emerald TIMED @158", "quartz TIMED @200"), order(plan));
        assertEquals(List.of(), plan.queue(GOLD));
        Mark emerald = mark(plan, EMERALD, 0);
        assertEquals(Tone.WRONG, emerald.tone());
        assertEquals("#1 Flip Back", emerald.lines().get(0).text());
        assertEquals("#6 7.9s", emerald.lines().get(1).text());
    }

    // --- 8.3.3 ---

    @Test
    void theWaterSwitchedOffPartWay() {
        WaterBoardPlan plan = standard(2, "013");
        assertEquals(List.of("quartz: #1 Flip | #4 3.6s", "emerald: #2 Flip | #5 4.7s | #7 12.1s",
                "water: #3 Wait | #6 Water Off 7.5s"), labels(plan, 0));
        plan.ownFlip(QUARTZ, 100, false);
        plan.ownFlip(EMERALD, 110, false);
        assertEquals("#1 Water On", mark(plan, WATER, 120).lines().get(0).text());
        plan.ownFlip(WATER, 1000, false);
        assertEquals(Phase.RUNNING, plan.phase());
        assertEquals(1000, plan.startTick());
        assertFalse(plan.estimated());
        assertEquals(List.of("quartz TIMED @72", "emerald TIMED @94", "water WATER_OFF @150", "emerald TIMED @242"), order(plan));
        assertEquals(List.of("quartz: #1 3.6s", "emerald: #2 4.7s | #4 12.1s", "water: #3 Water Off 7.5s"), labels(plan, 1000));
        assertEquals(242, plan.lastDueTicks());
    }

    // --- 8.3.4 ---

    @Test
    void countdownInTicks() {
        WaterBoardPlan plan = standard(1, "012");
        for (WaterBoardLever lever : List.of(GOLD, QUARTZ, DIAMOND, TERRACOTTA)) plan.ownFlip(lever, 900, false);
        plan.ownFlip(WATER, 1000, false);
        assertEquals("#1 2.9s", mark(plan, EMERALD, 1100).lines().get(0).text());
        assertEquals("#1 0.1s", mark(plan, EMERALD, 1157).lines().get(0).text());
        assertEquals("#1 Flip", mark(plan, EMERALD, 1158).lines().get(0).text());
        assertEquals("#1 Flip", mark(plan, EMERALD, 1167).lines().get(0).text());
        assertEquals("#1 Late", mark(plan, EMERALD, 1168).lines().get(0).text());
        // The colors never change with the time: nothing marks the moment a flip is due.
        for (long now = 1100; now <= 1200; now++) {
            assertEquals(Tone.SOLUTION, mark(plan, EMERALD, now).tone());
            assertEquals(Tone.SOLUTION, mark(plan, EMERALD, now).lines().get(0).tone());
            assertEquals(Tone.NEXT, mark(plan, QUARTZ, now).tone());
        }
    }

    @Test
    void secondsRoundUpToTheNextTenth() {
        assertEquals("2.9s", WaterBoardPlan.seconds(58));
        assertEquals("2.9s", WaterBoardPlan.seconds(57));
        assertEquals("0.1s", WaterBoardPlan.seconds(1));
        assertEquals("1.0s", WaterBoardPlan.seconds(20));
        assertEquals("12.1s", WaterBoardPlan.seconds(242));
        assertEquals("0.0s", WaterBoardPlan.seconds(0));
        assertEquals("0.0s", WaterBoardPlan.seconds(-5));
    }

    // --- 8.3.5 ---

    @Test
    void aWrongFlipBeforeTheWaterHasToBeFlippedBack() {
        WaterBoardPlan plan = standard(1, "124");
        assertEquals(List.of("water: #1 Water On", "coal: #2 2.9s", "gold: #3 6.7s", "terracotta: #4 10.2s"), labels(plan, 0));
        plan.ownFlip(GOLD, 10, false);
        assertEquals(List.of(Kind.FLIP_BACK, Kind.TIMED), plan.queue(GOLD).stream().map(Step::kind).toList());
        assertEquals(List.of("gold: #1 Flip Back | #4 6.7s", "water: #2 Wait", "coal: #3 2.9s", "terracotta: #5 10.2s"),
                labels(plan, 20));
        assertEquals(Tone.WRONG, mark(plan, WATER, 20).tone());
        plan.ownFlip(GOLD, 30, false);
        assertEquals(List.of(Kind.TIMED), plan.queue(GOLD).stream().map(Step::kind).toList());
        assertEquals("#1 Water On", mark(plan, WATER, 40).lines().get(0).text());
    }

    // --- 8.3.6 ---

    @Test
    void anOwnClickWaitsForTheBoard() {
        WaterBoardPlan plan = standard(1, "013"); // coal [0.0]
        plan.ownFlip(COAL, 500, true);
        assertEquals(List.of(), plan.queue(COAL));
        assertTrue(plan.awaitingConfirmation());
        // The board shows it: confirmed, not counted a second time.
        plan.observedFlip(COAL, 504, 0);
        assertFalse(plan.awaitingConfirmation());
        assertEquals(List.of(), plan.queue(COAL));
        plan.expire(600);
        assertEquals(List.of(), plan.queue(COAL));
    }

    @Test
    void anOwnClickTheServerIgnoredIsTakenBack() {
        WaterBoardPlan plan = standard(1, "013");
        plan.ownFlip(COAL, 500, true);
        plan.expire(539);
        assertEquals(List.of(), plan.queue(COAL));
        plan.expire(540);
        assertEquals(List.of("coal PRE @0"), plan.queue(COAL).stream().map(Step::toString).toList());
        assertFalse(plan.awaitingConfirmation());
    }

    @Test
    void aClickThatCannotBeConfirmedIsTrusted() {
        WaterBoardPlan plan = standard(1, "013");
        plan.ownFlip(COAL, 500, false);
        plan.expire(1000);
        assertEquals(List.of(), plan.queue(COAL));
    }

    // --- 8.3.7 ---

    @Test
    void aTeammatesWaterStartIsBackDatedByTheRoundTrip() {
        WaterBoardPlan plan = standard(1, "123"); // coal [0], gold [0, 8.7], terracotta [10.4]
        for (WaterBoardLever lever : List.of(COAL, GOLD)) plan.observedFlip(lever, 1500, 0);
        plan.observedFlip(WATER, 2000, Math.round(120 / 50.0f));
        assertEquals(Phase.RUNNING, plan.phase());
        assertEquals(1998, plan.startTick());
        assertTrue(plan.estimated());
        assertTrue(plan.waterOn());
        assertEquals("#1 ~8.6s", mark(plan, GOLD, 2000).lines().get(0).text());
    }

    @Test
    void waterSeenAtTheEntranceStartsTheRunWhenTheLeverWasMissed() {
        WaterBoardPlan plan = standard(1, "124");
        plan.observedEntrance(false, 2900, 2);
        assertEquals(Phase.READY, plan.phase());
        plan.observedEntrance(true, 3000, 2);
        assertEquals(Phase.RUNNING, plan.phase());
        assertEquals(3000 - 2 - WaterBoardPlan.ENTRANCE_TRAVEL_TICKS, plan.startTick());
        assertTrue(plan.estimated());
        // The lever's own update, late: the same start, not the water going off again.
        plan.observedFlip(WATER, 3010, 2);
        assertTrue(plan.waterOn());
    }

    @Test
    void theEntranceConfirmsAnOwnWaterClickWhoseLeverUpdateLags() {
        WaterBoardPlan plan = standard(1, "124");
        plan.ownFlip(WATER, 1000, true);
        assertEquals(Phase.RUNNING, plan.phase());
        // The water is still on its way down: no change of its own.
        for (long now = 1001; now < 1010; now++) plan.observedEntrance(false, now, 2);
        assertTrue(plan.waterOn());
        plan.observedEntrance(true, 1010, 2);
        assertFalse(plan.awaitingConfirmation());
        plan.observedFlip(WATER, 1030, 2);
        assertTrue(plan.waterOn());
        plan.expire(1100);
        assertEquals(Phase.RUNNING, plan.phase());
        assertEquals(1000, plan.startTick());
    }

    @Test
    void anIgnoredWaterClickReturnsToReady() {
        WaterBoardPlan plan = standard(1, "124");
        plan.ownFlip(WATER, 1000, true);
        for (long now = 1001; now < 1040; now++) {
            plan.observedEntrance(false, now, 2);
            plan.expire(now);
        }
        plan.expire(1040);
        assertEquals(Phase.READY, plan.phase());
        assertFalse(plan.waterOn());
        assertEquals("water WATER_ON @0", plan.order().getFirst().toString());
    }

    @Test
    void theFlowLaggingTheLeverIsNotAChange() {
        WaterBoardPlan plan = standard(2, "013");
        plan.observedFlip(QUARTZ, 0, 0);
        plan.observedFlip(EMERALD, 0, 0);
        plan.observedFlip(WATER, 1000, 0);
        for (long now = 1000; now < 1000 + WaterBoardPlan.ENTRANCE_GRACE_TICKS; now++) plan.observedEntrance(false, now, 0);
        assertTrue(plan.waterOn());
        // Planned water off, seen by its lever; the stream drains a little later.
        plan.observedFlip(WATER, 1150, 0);
        assertFalse(plan.waterOn());
        assertEquals(List.of("emerald TIMED @94", "emerald TIMED @242", "quartz TIMED @72").stream().sorted().toList(),
                order(plan).stream().sorted().toList());
        for (long now = 1150; now < 1160; now++) plan.observedEntrance(true, now, 0);
        assertFalse(plan.waterOn());
    }

    // --- 8.3.8 ---

    @Test
    void theFastSetHasNoFlipsBeforeTheWater() {
        WaterBoardPlan plan = plan(WaterBoardSolutions.Table.FAST, 4, "024");
        assertEquals("water WATER_ON @0", plan.order().getFirst().toString());
        assertEquals("#1 Water On", mark(plan, WATER, 0).lines().get(0).text());
        plan.ownFlip(WATER, 100, false);
        assertEquals(List.of("coal TIMED @12", "gold TIMED @24", "coal TIMED @36", "emerald TIMED @52",
                "terracotta TIMED @66", "coal TIMED @100", "coal TIMED @130", "terracotta TIMED @158",
                "terracotta TIMED @180"), order(plan));
    }

    // --- during the run ---

    @Test
    void aFlipWithNothingLeftDuringTheRunIsUndone() {
        WaterBoardPlan plan = standard(1, "124");
        plan.ownFlip(WATER, 1000, false);
        plan.ownFlip(COAL, 1058, false); // its 2.9 s flip
        assertEquals(List.of(), plan.queue(COAL));
        plan.ownFlip(COAL, 1070, false); // one too many
        assertEquals(List.of("coal FLIP_BACK @70"), plan.queue(COAL).stream().map(Step::toString).toList());
        Mark coal = mark(plan, COAL, 1071);
        assertEquals(Tone.WRONG, coal.tone());
        assertEquals("#1 Flip Back", coal.lines().get(0).text());
    }

    @Test
    void aSecondFlipLongBeforeTheNextStepIsUndoneNotCounted() {
        // Emerald at 3.3 s and 7.8 s; a held click flips it again 4 ticks after the first.
        WaterBoardPlan plan = standard(2, "014");
        plan.ownFlip(WATER, 1000, false);
        plan.ownFlip(EMERALD, 1066, false);
        assertEquals(List.of("emerald TIMED @156"), plan.queue(EMERALD).stream().map(Step::toString).toList());
        plan.ownFlip(EMERALD, 1070, false);
        assertEquals(List.of("emerald FLIP_BACK @70", "emerald TIMED @156"),
                plan.queue(EMERALD).stream().map(Step::toString).toList());
        Mark emerald = mark(plan, EMERALD, 1071);
        assertEquals(Tone.WRONG, emerald.tone());
        assertEquals("#1 Flip Back", emerald.lines().get(0).text());
        // Flipped back: the 7.8 s step is still to do.
        plan.ownFlip(EMERALD, 1080, false);
        assertEquals(List.of("emerald TIMED @156"), plan.queue(EMERALD).stream().map(Step::toString).toList());
    }

    @Test
    void aFlipNearItsStepCountsForIt() {
        WaterBoardPlan plan = standard(1, "124"); // coal 2.9 s, gold 6.7 s, terracotta 10.2 s
        plan.ownFlip(WATER, 1000, false);
        plan.ownFlip(COAL, 1050, false); // 8 ticks early: on time as far as anyone can tell
        assertEquals(List.of(), plan.queue(COAL));
        // A misclick on terracotta, seconds before its step: it has to be flipped back.
        plan.ownFlip(TERRACOTTA, 1060, false);
        assertEquals(List.of("terracotta FLIP_BACK @60", "terracotta TIMED @204"),
                plan.queue(TERRACOTTA).stream().map(Step::toString).toList());

        // Nearer to the next step than to the one before: that step, done a little early.
        WaterBoardPlan emerald = standard(2, "014");
        emerald.ownFlip(WATER, 1000, false);
        emerald.ownFlip(EMERALD, 1066, false);
        emerald.ownFlip(EMERALD, 1140, false);
        assertEquals(List.of(), emerald.queue(EMERALD));
    }

    @Test
    void stepsCloseTogetherAndAnEstimatedClock() {
        // Fast 4 "024": coal at 0.6 s and 1.8 s. A flip 4 ticks after the first is not the second.
        WaterBoardPlan own = plan(WaterBoardSolutions.Table.FAST, 4, "024");
        own.ownFlip(WATER, 1000, false);
        own.ownFlip(COAL, 1012, false);
        own.ownFlip(COAL, 1016, false);
        assertEquals(Kind.FLIP_BACK, own.queue(COAL).getFirst().kind());
        // With the start seen from a teammate's flip the clock can be off by a second or so, and
        // the same flip counts for the step.
        WaterBoardPlan seen = plan(WaterBoardSolutions.Table.FAST, 4, "024");
        seen.observedFlip(WATER, 1000, 0);
        assertTrue(seen.estimated());
        seen.observedFlip(COAL, 1012, 0);
        seen.observedFlip(COAL, 1016, 0);
        assertEquals(List.of("coal TIMED @100", "coal TIMED @130"), seen.queue(COAL).stream().map(Step::toString).toList());
    }

    @Test
    void anUnplannedWaterChangeLeavesTheRunOffItsPlan() {
        WaterBoardPlan plan = standard(1, "124"); // water [0.0]
        plan.ownFlip(WATER, 1000, false);
        int left = plan.remaining();
        assertFalse(plan.offPlan());
        // A teammate switches it off at 3 s.
        plan.observedFlip(WATER, 1060, 0);
        assertFalse(plan.waterOn());
        assertTrue(plan.offPlan());
        assertEquals(left, plan.remaining());
    }

    @Test
    void anIgnoredOwnWaterClickDoesNotLeaveTheRunOffItsPlan() {
        WaterBoardPlan plan = standard(1, "124");
        plan.ownFlip(WATER, 1000, false);
        plan.ownFlip(WATER, 1060, true);
        assertTrue(plan.offPlan());
        plan.expire(1100);
        assertFalse(plan.offPlan());
        assertTrue(plan.waterOn());
    }

    @Test
    void theWaterOnAgainAfterAPlannedStopIsOffPlan() {
        WaterBoardPlan plan = standard(2, "013"); // water [0.0, 7.5]
        plan.ownFlip(QUARTZ, 0, false);
        plan.ownFlip(EMERALD, 0, false);
        plan.ownFlip(WATER, 1000, false);
        plan.ownFlip(WATER, 1150, false);
        assertFalse(plan.waterOn());
        assertFalse(plan.offPlan());
        plan.observedFlip(WATER, 1200, 0);
        assertTrue(plan.waterOn());
        assertTrue(plan.offPlan());
    }

    @Test
    void stepsLeftOverFromBeforeTheWaterAreDueAtOnce() {
        WaterBoardPlan plan = standard(1, "012");
        plan.ownFlip(WATER, 1000, false); // four flips still to do
        assertEquals("#1 Flip", mark(plan, GOLD, 1005).lines().get(0).text());
        assertEquals("#1 Late", mark(plan, GOLD, 1010).lines().get(0).text());
        plan.ownFlip(GOLD, 1012, false);
        assertEquals(List.of(), plan.queue(GOLD));
    }

    @Test
    void theLastDueTimeStaysWhenTheStepsAreDone() {
        WaterBoardPlan plan = standard(2, "013");
        plan.ownFlip(QUARTZ, 0, false);
        plan.ownFlip(EMERALD, 0, false);
        plan.ownFlip(WATER, 1000, false);
        plan.ownFlip(QUARTZ, 1072, false);
        plan.ownFlip(EMERALD, 1094, false);
        plan.ownFlip(WATER, 1150, false);
        assertFalse(plan.waterOn());
        plan.ownFlip(EMERALD, 1242, false);
        assertEquals(0, plan.remaining());
        assertEquals(242, plan.lastDueTicks());
        assertEquals(List.of(), plan.marks(1300));
    }

    @Test
    void theNextLeverIsTheSolutionColorAndTheRestTheNextColor() {
        WaterBoardPlan plan = standard(1, "012");
        List<Mark> marks = plan.marks(0);
        assertEquals(GOLD, marks.get(0).lever());
        assertEquals(Tone.SOLUTION, marks.get(0).tone());
        for (Mark mark : marks.subList(1, marks.size())) {
            assertEquals(mark.lever() == WATER ? Tone.WRONG : Tone.NEXT, mark.tone(), mark.lever().key());
        }
        assertEquals(7, plan.remaining());
    }
}
