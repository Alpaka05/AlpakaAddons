package net.alpaka.addons.features.dungeon.puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One Water Board solution being played out: the flips still to do, in order, and when.
 *
 * Each lever has a queue of steps taken from the solution table. Before the water runs, a board
 * lever's step is either "flip it now" (the table's 0.0) or a time to wait for; the water lever's
 * first step switches it on, which starts the clock. After that every step is due a fixed number of
 * server ticks after that moment. A flip of a lever, by whoever, takes the first step off its queue
 * - except a flip the plan did not ask for: before the water, flipping a lever whose next step is
 * not "flip it now" means it has to be flipped back, so a "flip back" step goes in front. During
 * the run the same goes for a lever flipped with nothing left to do, and for a flip that comes
 * well before the lever's next step: a flip counts as that step once it is nearer to it than to
 * the lever's step before, or at most {@link #EARLY_TICKS} before it ({@link #ESTIMATED_EARLY_TICKS}
 * while the clock is estimated), so a double flip or a teammate's stray flip does not use up a
 * step that is seconds away. The water lever cannot be flipped back
 * into the run: a change of the water the plan did not ask for leaves the run off its plan
 * ({@link #offPlan}), which only starting over can mend. Nothing is ever dropped silently, so a
 * wrong flip cannot leave the board in a state the solution does not expect.
 *
 * Flips come from two places. The player's own click counts at once, so the marks move with the
 * click, and waits for the board to confirm it: if the board's blocks have not moved within two
 * seconds the server ignored the click, and it is taken back. A flip seen on the board without a
 * click of our own is a teammate's and counts as it is seen. The water is seen by its lever's state
 * or by water falling in at the entrance, whichever comes first.
 *
 * Nothing here decides anything for the player or acts at a moment: it only lists what is left and
 * how long until each step, for steady labels. Pure; the caller passes the time in as server ticks.
 */
public final class WaterBoardPlan {

    /** What a step asks for. */
    public enum Kind {
        /** Flip the lever before switching the water on. */
        PRE,
        /** Flip the lever this long after the water came on. */
        TIMED,
        /** Switch the water on (at 0: start the run). */
        WATER_ON,
        /** Switch the water off again. */
        WATER_OFF,
        /** Undo a flip the solution did not ask for. */
        FLIP_BACK
    }

    /** One flip, due {@code ticks} after the water came on. Compared by identity. */
    public static final class Step {
        private final WaterBoardLever lever;
        private final int ticks;
        private final Kind kind;

        Step(WaterBoardLever lever, int ticks, Kind kind) {
            this.lever = lever;
            this.ticks = ticks;
            this.kind = kind;
        }

        public WaterBoardLever lever() {
            return lever;
        }

        public int ticks() {
            return ticks;
        }

        public Kind kind() {
            return kind;
        }

        /** Whether this step has to happen before the water is switched on. */
        public boolean beforeWater() {
            return kind == Kind.PRE || (kind == Kind.FLIP_BACK && ticks == 0);
        }

        @Override
        public String toString() {
            return lever.key() + " " + kind + " @" + ticks;
        }
    }

    public enum Phase {
        /** The flips before the water, then switching it on. */
        READY,
        /** The water was switched on; steps count down from that moment. */
        RUNNING
    }

    /** Which of the Solvers colors a mark takes. */
    public enum Tone {
        /** The step to do next. */
        SOLUTION,
        /** A step after it. */
        NEXT,
        /** Something to undo or hold back from. */
        WRONG
    }

    /** One label line above a lever. */
    public record Line(String text, Tone tone) {}

    /** What to draw at one lever: its box's tone and a line per step it has left, next step first. */
    public record Mark(WaterBoardLever lever, Tone tone, List<Line> lines) {}

    /** A flip that has not shown on the board for this long was ignored by the server. */
    public static final int CONFIRM_TICKS = 40;
    /** A step this many ticks overdue reads "Late". */
    public static final int LATE_TICKS = 10;
    /**
     * During the run, a flip this many ticks before a lever's next step still counts as that step,
     * however near the step before it was. Some Fast solutions flip one lever twice 10 ticks apart.
     */
    public static final int EARLY_TICKS = 10;
    /** The same while the start was worked out from a teammate's flip or the water, which can be this far off. */
    public static final int ESTIMATED_EARLY_TICKS = 30;
    /**
     * Water seen at the entrance takes about this long from the water lever. Only used when the
     * water was noticed by its flow rather than its lever, so the countdown is marked as estimated.
     */
    public static final int ENTRANCE_TRAVEL_TICKS = 10;
    /** The flow at the entrance may lag the water lever by this much before it counts as a change of its own. */
    public static final int ENTRANCE_GRACE_TICKS = 40;

    private enum Op { POP, PUSH, START, WATER }

    /**
     * What one flip did to the plan, so a click the server ignored can be taken back.
     * {@code lastTaken} is the lever's {@link #lastTaken} before a POP.
     */
    private record Applied(WaterBoardLever lever, long at, Op op, Step step, int lastTaken) {
        Applied(WaterBoardLever lever, long at, Op op, Step step) {
            this(lever, at, op, step, 0);
        }
    }

    private final Map<WaterBoardLever, ArrayDeque<Step>> queues = new EnumMap<>(WaterBoardLever.class);
    /** The player's own flips that the board has not shown yet, oldest first. */
    private final List<Applied> awaiting = new ArrayList<>();
    private Phase phase = Phase.READY;
    private long startTick = 0L;
    private boolean estimated = false;
    private boolean waterOn = false;
    /** Until then, a water lever change is the one whose flow was already seen; it is not counted again. */
    private long absorbWaterUntil = Long.MIN_VALUE;
    /** When the water last changed as far as the plan knows; long ago at first. */
    private long lastWaterChange = Long.MIN_VALUE / 2;
    /** The latest any step of the plan was due, done or not, in ticks after the start. */
    private int lastDue = 0;
    /** Per lever: when the step last taken off its queue was due (0, the start, before any). */
    private final int[] lastTaken = new int[WaterBoardLever.values().length];
    /** Water changes during the run that were not the plan's; any at all means the run is off its plan. */
    private int offPlanWater = 0;

    private WaterBoardPlan() {
        for (WaterBoardLever lever : WaterBoardLever.values()) queues.put(lever, new ArrayDeque<>());
    }

    /**
     * A plan for {@code solution} (seconds per lever, as in the tables) on a board where the levers
     * in {@code flippedAtStart} were already flipped an odd number of times. Those count as flips
     * made before the water, so one the solution wanted is done and any other has to be flipped back.
     */
    public static WaterBoardPlan of(Map<WaterBoardLever, double[]> solution, Set<WaterBoardLever> flippedAtStart) {
        WaterBoardPlan plan = new WaterBoardPlan();
        for (WaterBoardLever lever : WaterBoardLever.values()) {
            double[] seconds = solution.get(lever);
            if (seconds == null) continue;
            ArrayDeque<Step> queue = plan.queues.get(lever);
            for (int i = 0; i < seconds.length; i++) {
                int ticks = (int) Math.round(seconds[i] * 20.0);
                Kind kind;
                if (lever.isWater()) kind = i % 2 == 0 ? Kind.WATER_ON : Kind.WATER_OFF;
                else kind = ticks == 0 ? Kind.PRE : Kind.TIMED;
                queue.addLast(new Step(lever, ticks, kind));
                plan.lastDue = Math.max(plan.lastDue, ticks);
            }
        }
        for (WaterBoardLever lever : WaterBoardLever.values()) {
            if (!lever.isWater() && flippedAtStart.contains(lever)) plan.applyLever(lever, 0L);
        }
        return plan;
    }

    // --- flips ---

    /**
     * The player flipped {@code lever} at {@code now}. Counts at once; with {@code confirmable} it
     * waits for the board to show it (see {@link #expire}), without it - a lever whose blocks cannot
     * be found - the click is trusted as it is.
     */
    public void ownFlip(WaterBoardLever lever, long now, boolean confirmable) {
        Applied applied = lever.isWater() ? applyWater(now, now, false) : applyLever(lever, now);
        if (confirmable) awaiting.add(applied);
    }

    /**
     * The board shows {@code lever} flipped (its blocks moved, or the water lever changed state) at
     * {@code now}. Confirms the player's own oldest flip of it if one is waiting; otherwise it is a
     * teammate's and counts now. A teammate's water start is back-dated by the round trip
     * {@code rttTicks}: their click reached the server that long before ours would, so the steps fall
     * due as if the player had clicked it.
     */
    public void observedFlip(WaterBoardLever lever, long now, int rttTicks) {
        if (lever.isWater() && now <= absorbWaterUntil) {
            absorbWaterUntil = Long.MIN_VALUE;
            return;
        }
        Applied own = oldestAwaiting(lever);
        if (own != null) {
            awaiting.remove(own);
            return;
        }
        if (lever.isWater()) applyWater(now, now - Math.max(0, rttTicks), true);
        else applyLever(lever, now);
    }

    /**
     * Whether water is falling in at the entrance, seen at {@code now}; called every tick the board
     * can be seen. When it agrees with what the plan knows it only confirms the player's own water
     * click. The flow follows the lever by a few ticks, so a disagreement only counts once it has
     * outlasted {@link #ENTRANCE_GRACE_TICKS} since the water last changed and no own click is still
     * on its way: then the water changed with no lever change seen - a lever update that lags, or a
     * chunk that was not loaded - and it counts from here. The lever change that may still come is
     * then not counted a second time.
     */
    public void observedEntrance(boolean wet, long now, int rttTicks) {
        if (wet == waterOn) {
            Applied own = oldestAwaiting(WaterBoardLever.WATER);
            if (own != null) {
                awaiting.remove(own);
                absorbWaterUntil = now + CONFIRM_TICKS;
            }
            return;
        }
        if (oldestAwaiting(WaterBoardLever.WATER) != null || now - lastWaterChange < ENTRANCE_GRACE_TICKS) return;
        applyWater(now, now - Math.max(0, rttTicks) - ENTRANCE_TRAVEL_TICKS, true);
        absorbWaterUntil = now + CONFIRM_TICKS;
    }

    /** Takes back the player's own flips that the board has not shown within {@link #CONFIRM_TICKS}. */
    public void expire(long now) {
        for (int i = awaiting.size() - 1; i >= 0; i--) {
            Applied applied = awaiting.get(i);
            if (now - applied.at() < CONFIRM_TICKS) continue;
            awaiting.remove(i);
            undo(applied);
        }
    }

    /** A board lever's flip: the next step if it is due, otherwise a step to undo it. */
    private Applied applyLever(WaterBoardLever lever, long now) {
        ArrayDeque<Step> queue = queues.get(lever);
        Step head = queue.peekFirst();
        int elapsed = (int) Math.max(0L, now - startTick);
        boolean takesHead;
        if (head == null) takesHead = false;
        else if (phase == Phase.READY) takesHead = head.beforeWater();
        else takesHead = head.kind() == Kind.FLIP_BACK || head.beforeWater() || elapsed >= head.ticks() - earlySlack(lever, head);
        if (takesHead) {
            Step popped = queue.pollFirst();
            Applied applied = new Applied(lever, now, Op.POP, popped, lastTaken[lever.ordinal()]);
            if (popped.kind() != Kind.FLIP_BACK) lastTaken[lever.ordinal()] = popped.ticks();
            return applied;
        }
        int at = phase == Phase.READY ? 0 : elapsed;
        Step back = new Step(lever, at, Kind.FLIP_BACK);
        queue.addFirst(back);
        lastDue = Math.max(lastDue, at);
        return new Applied(lever, now, Op.PUSH, back);
    }

    /**
     * How early a flip during the run may come and still be {@code head}, the lever's next step:
     * up to halfway from the step taken before it, and at least {@link #EARLY_TICKS} (more while
     * the clock is estimated).
     */
    private int earlySlack(WaterBoardLever lever, Step head) {
        int least = estimated ? ESTIMATED_EARLY_TICKS : EARLY_TICKS;
        return Math.max(least, (head.ticks() - lastTaken[lever.ordinal()]) / 2);
    }

    /** The water lever's flip: before the run it starts the clock at {@code start}, during it switches the water. */
    private Applied applyWater(long now, long start, boolean estimate) {
        ArrayDeque<Step> queue = queues.get(WaterBoardLever.WATER);
        if (phase == Phase.READY) {
            Step head = queue.peekFirst();
            Step popped = head != null && head.kind() == Kind.WATER_ON ? queue.pollFirst() : null;
            phase = Phase.RUNNING;
            startTick = start;
            estimated = estimate;
            waterOn = true;
            lastWaterChange = now;
            return new Applied(WaterBoardLever.WATER, now, Op.START, popped);
        }
        waterOn = !waterOn;
        lastWaterChange = now;
        Step head = queue.peekFirst();
        Kind wanted = waterOn ? Kind.WATER_ON : Kind.WATER_OFF;
        Step popped = head != null && head.kind() == wanted ? queue.pollFirst() : null;
        // Not the plan's: the stream was cut or started again, and the countdowns no longer hold.
        if (popped == null) offPlanWater++;
        return new Applied(WaterBoardLever.WATER, now, Op.WATER, popped);
    }

    private void undo(Applied applied) {
        ArrayDeque<Step> queue = queues.get(applied.lever());
        switch (applied.op()) {
            case POP -> {
                queue.addFirst(applied.step());
                lastTaken[applied.lever().ordinal()] = applied.lastTaken();
            }
            case PUSH -> queue.removeFirstOccurrence(applied.step());
            case START -> {
                phase = Phase.READY;
                startTick = 0L;
                estimated = false;
                waterOn = false;
                lastWaterChange = applied.at();
                if (applied.step() != null) queue.addFirst(applied.step());
            }
            case WATER -> {
                waterOn = !waterOn;
                lastWaterChange = applied.at();
                if (applied.step() != null) queue.addFirst(applied.step());
                else offPlanWater--;
            }
        }
    }

    private Applied oldestAwaiting(WaterBoardLever lever) {
        for (Applied applied : awaiting) {
            if (applied.lever() == lever) return applied;
        }
        return null;
    }

    // --- state ---

    public Phase phase() {
        return phase;
    }

    /** The tick the water came on; only meaningful while running. */
    public long startTick() {
        return startTick;
    }

    /** Whether the start was worked out from a teammate's flip or the water rather than the player's click. */
    public boolean estimated() {
        return estimated;
    }

    /** Whether the water is on, as far as the plan knows. */
    public boolean waterOn() {
        return waterOn;
    }

    /**
     * Whether the water was switched on or off during the run when the plan did not ask for it: the
     * countdowns no longer match the flow, and the run can only be started over.
     */
    public boolean offPlan() {
        return offPlanWater > 0;
    }

    /** The steps of one lever, next first. */
    public List<Step> queue(WaterBoardLever lever) {
        return List.copyOf(queues.get(lever));
    }

    /** How many steps are left. */
    public int remaining() {
        int n = 0;
        for (ArrayDeque<Step> queue : queues.values()) n += queue.size();
        return n;
    }

    /**
     * How long after the start the plan's last step was due, whether it is done or not: after that
     * the doors should open within a few seconds.
     */
    public int lastDueTicks() {
        return lastDue;
    }

    /** Whether a click of the player's own is still waiting for the board. */
    public boolean awaitingConfirmation() {
        return !awaiting.isEmpty();
    }

    /**
     * Every step left, in the order to do them: by due time; at the same time, flipping back first
     * and the water after the board levers, then in lever order. Before the water that puts the
     * flips first and switching the water on after them.
     */
    public List<Step> order() {
        List<Step> steps = new ArrayList<>();
        for (WaterBoardLever lever : WaterBoardLever.values()) steps.addAll(queues.get(lever));
        steps.sort(Comparator.comparingInt(Step::ticks)
                .thenComparingInt(step -> step.kind() == Kind.FLIP_BACK ? 0 : 1)
                .thenComparingInt(step -> step.lever().isWater() ? 1 : 0)
                .thenComparingInt(step -> step.lever().ordinal()));
        return steps;
    }

    // --- what to show ---

    /**
     * The marks to draw at {@code now}: one per lever with steps left, in the order of their first
     * step. A lever's box takes the tone of its first step; its label lines read "#n" and then what
     * to do or how long until it, one per step in the lever's own order. The tones depend only on the
     * order, never on the time, so nothing changes color at the moment a flip is due.
     */
    public List<Mark> marks(long now) {
        List<Step> order = order();
        Map<Step, Integer> numbers = new IdentityHashMap<>();
        for (int i = 0; i < order.size(); i++) numbers.put(order.get(i), i + 1);
        boolean flipsBeforeWater = false;
        for (Step step : order) {
            if (!step.lever().isWater() && step.beforeWater()) flipsBeforeWater = true;
        }
        List<Mark> marks = new ArrayList<>();
        List<WaterBoardLever> levers = new ArrayList<>();
        for (Step step : order) {
            if (!levers.contains(step.lever())) levers.add(step.lever());
        }
        for (WaterBoardLever lever : levers) {
            List<Line> lines = new ArrayList<>();
            for (Step step : queues.get(lever)) {
                int number = numbers.get(step);
                lines.add(new Line(label(step, number, now, flipsBeforeWater), tone(step, number, flipsBeforeWater)));
            }
            marks.add(new Mark(lever, lines.getFirst().tone(), List.copyOf(lines)));
        }
        return marks;
    }

    private Tone tone(Step step, int number, boolean flipsBeforeWater) {
        if (step.kind() == Kind.FLIP_BACK) return Tone.WRONG;
        if (phase == Phase.READY && step.kind() == Kind.WATER_ON && flipsBeforeWater) return Tone.WRONG;
        return number == 1 ? Tone.SOLUTION : Tone.NEXT;
    }

    private String label(Step step, int number, long now, boolean flipsBeforeWater) {
        String prefix = "#" + number + " ";
        String action = switch (step.kind()) {
            case PRE, TIMED -> "Flip";
            case WATER_ON -> "Water On";
            case WATER_OFF -> "Water Off";
            case FLIP_BACK -> "Flip Back";
        };
        if (phase == Phase.READY) {
            return switch (step.kind()) {
                case PRE, FLIP_BACK -> prefix + action;
                case WATER_ON -> prefix + (step.ticks() == 0 && flipsBeforeWater ? "Wait" : action);
                case TIMED -> prefix + seconds(step.ticks());
                case WATER_OFF -> prefix + action + " " + seconds(step.ticks());
            };
        }
        long left = startTick + step.ticks() - now;
        if (step.kind() == Kind.FLIP_BACK) return prefix + action;
        if (left > 0) {
            String time = (estimated ? "~" : "") + seconds(left);
            return prefix + (step.kind() == Kind.WATER_OFF || step.kind() == Kind.WATER_ON ? action + " " + time : time);
        }
        return prefix + (left <= -LATE_TICKS ? "Late" : action);
    }

    /**
     * {@code ticks} as seconds with one decimal, rounded up to the next tenth so that a step never
     * reads "0.0s" while it is still ahead: 58 ticks read "2.9s", 1 tick "0.1s".
     */
    public static String seconds(long ticks) {
        long tenths = (Math.max(0L, ticks) + 1L) / 2L;
        return (tenths / 10L) + "." + (tenths % 10L) + "s";
    }
}
