package net.alpaka.addons.utils;

import net.minecraft.util.profiling.Profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Where the mod's own frame and tick time goes.
 *
 * Each hot hook is timed into a section while measuring is on: {@code /alpakadebug perf start}
 * starts from zero, {@code /alpakadebug perf} prints the totals since then, {@code stop} ends it.
 * While on, the sections also show in vanilla's profiler (F3 + L, or the debug pie) under their
 * {@code alpaka:} names. Off, which it is from launch, {@link #begin} and {@link #end} only read a
 * flag: two clock reads and two profiler calls in every entity hook of every frame, for nobody to
 * read, were a cost of their own.
 *
 * The per-entity hooks only {@link #count} their calls even while measuring, for the same reason;
 * the entity phase they add to is timed once per frame as a whole ({@link Section#ENTITY_PHASE}).
 * Next to the sections the report gives how fast the client thread allocates and how often the
 * garbage collector ran, which is where much of a mod's cost shows rather than in its own time.
 *
 * Averages hide a freeze: one frame of 300 ms in a minute of 500 fps barely moves them. So while
 * measuring, every frame is also split into the client loop's own phases ({@link Phase}), and a
 * frame that takes {@link #SPIKE_MS} or more is kept with what it spent its time on: the phases,
 * the client thread's CPU time (far below the frame's time means it waited, for the GPU, a lock or
 * the garbage collector, rather than computed), what it allocated, the collector's pauses and our
 * own sections. Each one also goes into latest.log, to line up with chat and warps there.
 *
 * A frame can also stand still without any of that showing: the thread waits somewhere no phase
 * covers, in another mod's hook or the window system. So a watchdog thread looks at the render
 * thread's stack once a frame has run {@link #SAMPLE_AFTER_MS}, and again later if it still runs;
 * a spike then says where the thread was. A stack is only taken from a frame already that slow.
 *
 * Only call from the render or client thread, which is where every timed hook runs.
 */
public final class AlpakaPerf {
    public enum Section {
        HUD_PLAYER_MODEL("alpaka:hud_player_model"),
        HUD_PERFORMANCE("alpaka:hud_performance"),
        HUD_SLAYER("alpaka:hud_slayer"),
        HUD_SLAYER_TIMER("alpaka:hud_slayer_timer"),
        HUD_DEPLOYABLE_TIMER("alpaka:hud_deployable_timer"),
        HUD_INVENTORY("alpaka:hud_inventory"),
        HUD_NOTIFICATIONS("alpaka:hud_notifications"),
        HUD_SCOREBOARD("alpaka:hud_scoreboard"),
        BLUR_CAPTURE("alpaka:blur_capture"),
        CHAT_EXTRACT("alpaka:chat_extract"),
        CHAT_MESSAGE("alpaka:chat_message"),
        CHAT_REPLAY("alpaka:chat_replay"),
        /** Vanilla's whole entity extraction, every rule hook of ours included; once per frame. */
        ENTITY_PHASE("alpaka:entity_phase"),
        /** The entity rule hooks, counted only. */
        ENTITY_RULES("alpaka:entity_rules", true),
        NAME_TAG("alpaka:name_tag"),
        CHROMA_HAT("alpaka:chroma_hat"),
        /** The first-person hands with everything the viewmodel features add to them. */
        HAND_ITEMS("alpaka:hand_items"),
        /** Block Overlay, the Etherwarp target and the fire pits. */
        LEVEL_OVERLAYS("alpaka:level_overlays"),
        DAMAGE_TAGS("alpaka:damage_tags"),
        DAMAGE_TAGS_TICK("alpaka:damage_tags_tick"),
        SLAYER_TICK("alpaka:slayer_tick");

        final String id;
        final boolean countOnly;

        Section(String id) {
            this(id, false);
        }

        Section(String id, boolean countOnly) {
            this.id = id;
            this.countOnly = countOnly;
        }
    }

    /** The steps of one pass of the client loop, timed around vanilla's calls. */
    public enum Phase {
        PACKETS("packets"),
        TASKS("tasks"),
        TICK("tick"),
        EXTRACT("extract"),
        GPU_TASKS("gpu tasks"),
        RENDER("render"),
        ACQUIRE("acquire"),
        SUBMIT("submit"),
        PRESENT("present"),
        /** The frame rate limit's sleep; left out of a frame's time, as it is waiting by choice. */
        LIMITER("limiter");

        final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    /** A frame whose time, the limiter's sleep left out, reaches this is a spike. */
    static final long SPIKE_MS = 50L;
    private static final long[] SPIKE_STEPS_MS = {50L, 100L, 250L, 1000L};
    private static final int WORST_KEPT = 5;
    /** Enough for a long session; past it the report still counts them. */
    private static final int MAX_LOGGED = 300;
    static final long SAMPLE_AFTER_MS = 40L;
    private static final long SECOND_SAMPLE_AFTER_MS = 150L;
    private static final int STACK_LOGGED = 30;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** What {@link #begin} hands out while measuring is off; {@link #end} then does nothing. */
    private static final long OFF = Long.MIN_VALUE;

    private static final Section[] SECTIONS = Section.values();
    private static final long[] NANOS = new long[SECTIONS.length];
    private static final long[] CALLS = new long[SECTIONS.length];
    private static final Phase[] PHASES = Phase.values();
    private static final long[] PHASE_NANOS = new long[PHASES.length];

    /** The running frame: its phases and sections, and where its counters stood at its start. */
    private static final long[] FRAME_PHASES = new long[PHASES.length];
    private static final long[] FRAME_SECTIONS = new long[SECTIONS.length];
    /** Read by the watchdog as well, hence volatile. */
    private static volatile long frameStartNanos = 0L;
    private static long frameCpuStart, frameAllocStart;
    private static final long[] frameGcStart = new long[4];

    private static long frames, workNanos, worstWorkNanos;
    private static final long[] SPIKES = new long[SPIKE_STEPS_MS.length];
    private static final List<Spike> WORST = new ArrayList<>();
    private static int spikesLogged;

    private static boolean enabled = false;
    private static long windowStartNanos = System.nanoTime();
    /** When measuring stopped, or 0 while it runs or never ran. */
    private static long windowEndNanos = 0L;

    /** The thread measured for allocations: the one that started measuring, the client thread. */
    private static long threadId = -1L;
    private static long allocatedAtStart, allocatedAtEnd;
    private static long gcRunsAtStart, gcRunsAtEnd, gcMillisAtStart, gcMillisAtEnd;
    private static final long[] gcAtStart = new long[4];
    private static final long[] gcAtEnd = new long[4];

    private AlpakaPerf() {}

    public static boolean isEnabled() {
        return enabled;
    }

    /** Starts measuring, from zero. */
    public static void start() {
        reset();
        enabled = true;
        startWatchdog(Thread.currentThread());
    }

    /** Stops measuring; what was measured stays for {@link #report()}. */
    public static void stop() {
        if (!enabled) return;
        enabled = false;
        windowEndNanos = System.nanoTime();
        allocatedAtEnd = allocatedBytes();
        gcRunsAtEnd = gcRuns();
        gcMillisAtEnd = gcMillis();
        gcSnapshot(gcAtEnd);
        frameStartNanos = 0L;
        stopWatchdog();
    }

    /** Starts timing a section; pass the result to {@link #end}, in a finally. */
    public static long begin(Section section) {
        if (!enabled) return OFF;
        Profiler.get().push(section.id);
        return System.nanoTime();
    }

    public static void end(Section section, long startNanos) {
        // Begun while off, so nothing was pushed - even if measuring started since.
        if (startNanos == OFF) return;
        long took = System.nanoTime() - startNanos;
        NANOS[section.ordinal()] += took;
        FRAME_SECTIONS[section.ordinal()] += took;
        CALLS[section.ordinal()]++;
        Profiler.get().pop();
    }

    /** One call of a section that is only counted. */
    public static void count(Section section) {
        if (enabled) CALLS[section.ordinal()]++;
    }

    /** Starts timing a phase of the client loop; pass the result to {@link #phaseEnd}. */
    public static long phaseBegin() {
        return enabled ? System.nanoTime() : OFF;
    }

    public static void phaseEnd(Phase phase, long startNanos) {
        if (startNanos == OFF) return;
        long took = System.nanoTime() - startNanos;
        PHASE_NANOS[phase.ordinal()] += took;
        FRAME_PHASES[phase.ordinal()] += took;
    }

    /** Called as each pass of the client loop begins: closes the frame before it. */
    public static void frameStart() {
        if (!enabled) {
            frameStartNanos = 0L;
            return;
        }
        long now = System.nanoTime();
        if (frameStartNanos != 0L) finishFrame(now - frameStartNanos);
        Arrays.fill(FRAME_PHASES, 0L);
        Arrays.fill(FRAME_SECTIONS, 0L);
        frameCpuStart = cpuNanos();
        frameAllocStart = allocatedBytes();
        gcSnapshot(frameGcStart);
        frameStartNanos = System.nanoTime();
    }

    /** Books the frame that just ended, {@code wallNanos} long; package-private for the tests. */
    static void finishFrame(long wallNanos) {
        long work = Math.max(0L, wallNanos - FRAME_PHASES[Phase.LIMITER.ordinal()]);
        frames++;
        workNanos += work;
        worstWorkNanos = Math.max(worstWorkNanos, work);
        long workMs = work / 1_000_000L;
        if (workMs < SPIKE_MS) return;
        for (int i = 0; i < SPIKE_STEPS_MS.length; i++) {
            if (workMs >= SPIKE_STEPS_MS[i]) SPIKES[i]++;
        }

        long cpuNow = cpuNanos();
        long allocNow = allocatedBytes();
        long[] gc = new long[4];
        gcSnapshot(gc);
        Spike spike = new Spike(LocalTime.now(), work,
                frameCpuStart < 0 || cpuNow < 0 ? -1L : cpuNow - frameCpuStart,
                frameAllocStart < 0 || allocNow < 0 ? -1L : allocNow - frameAllocStart,
                gc[0] - frameGcStart[0], gc[1] - frameGcStart[1], gc[2] - frameGcStart[2],
                FRAME_PHASES.clone(), FRAME_SECTIONS.clone(), samplesOf(frameStartNanos));
        if (spikesLogged < MAX_LOGGED) {
            spikesLogged++;
            StringBuilder log = new StringBuilder(spike.describe());
            for (int n = 0; n < spike.stacks().size(); n++) {
                StackTraceElement[] stack = spike.stacks().get(n);
                log.append("\n    render thread after ").append(n == 0 ? SAMPLE_AFTER_MS : SECOND_SAMPLE_AFTER_MS).append(" ms:");
                for (int i = 0; i < Math.min(STACK_LOGGED, stack.length); i++) log.append("\n      at ").append(stack[i]);
            }
            net.alpaka.addons.AlpakaAddons.LOGGER.info("[AlpakaPerf] spike {}", log);
        }
        WORST.add(spike);
        WORST.sort((a, b) -> Long.compare(b.workNanos, a.workNanos));
        if (WORST.size() > WORST_KEPT) WORST.remove(WORST.size() - 1);
    }

    /** One slow frame, with where its time went. */
    record Spike(LocalTime at, long workNanos, long cpuNanos, long allocatedBytes,
                 long gcPauses, long gcPauseMillis, long gcCycles, long[] phases, long[] sections,
                 List<StackTraceElement[]> stacks) {

        String describe() {
            StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%s  %d ms", CLOCK.format(at), workNanos / 1_000_000L));
            List<String> details = new ArrayList<>();
            if (cpuNanos >= 0) details.add(String.format(Locale.ROOT, "cpu %d ms", cpuNanos / 1_000_000L));
            if (allocatedBytes >= 0) details.add(String.format(Locale.ROOT, "%.1f MB", allocatedBytes / 1e6));
            if (!details.isEmpty()) out.append(" (").append(String.join(", ", details)).append(")");

            // Biggest phase first; what no phase covers is the rest of the loop.
            long covered = 0L;
            List<Integer> order = new ArrayList<>();
            for (Phase phase : PHASES) {
                if (phase == Phase.LIMITER) continue;
                covered += phases[phase.ordinal()];
                order.add(phase.ordinal());
            }
            order.sort((a, b) -> Long.compare(phases[b], phases[a]));
            for (int i : order) {
                if (phases[i] >= 1_000_000L) {
                    out.append(String.format(Locale.ROOT, " · %s %d", PHASES[i].label, phases[i] / 1_000_000L));
                }
            }
            long rest = workNanos - covered;
            if (rest >= 1_000_000L) out.append(String.format(Locale.ROOT, " · other %d", rest / 1_000_000L));

            if (gcPauses > 0) out.append(String.format(Locale.ROOT, " · GC pause %d× %d ms", gcPauses, gcPauseMillis));
            if (gcCycles > 0) out.append(String.format(Locale.ROOT, " · GC cycle %d", gcCycles));

            List<Integer> ours = new ArrayList<>();
            for (Section section : SECTIONS) {
                if (sections[section.ordinal()] >= 100_000L) ours.add(section.ordinal());
            }
            ours.sort((a, b) -> Long.compare(sections[b], sections[a]));
            if (!ours.isEmpty()) {
                List<String> named = new ArrayList<>();
                for (int i : ours.subList(0, Math.min(3, ours.size()))) {
                    named.add(String.format(Locale.ROOT, "%s %.1f", SECTIONS[i].id.substring("alpaka:".length()), sections[i] / 1e6));
                }
                out.append(" · alpaka: ").append(String.join(", ", named));
            }
            if (!stacks.isEmpty()) out.append(" · in ").append(where(stacks.get(stacks.size() - 1)));
            return out.toString();
        }
    }

    /**
     * Where a stack stood, short enough for chat: the method it was in, and the first two calls
     * from outside the JDK on the way there - a mod's handler shows as {@code handler$...$modid$...}.
     */
    static String where(StackTraceElement[] stack) {
        if (stack.length == 0) return "?";
        List<String> parts = new ArrayList<>();
        parts.add(shortName(stack[0]));
        for (int i = 1; i < stack.length && parts.size() < 3; i++) {
            String cls = stack[i].getClassName();
            if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.")) continue;
            parts.add(shortName(stack[i]));
        }
        return String.join(" ← ", parts);
    }

    private static String shortName(StackTraceElement frame) {
        String cls = frame.getClassName();
        return cls.substring(cls.lastIndexOf('.') + 1) + "." + frame.getMethodName();
    }

    /** The render thread's stacks taken during one frame, from the watchdog. */
    private record Samples(long frameStart, List<StackTraceElement[]> stacks) {}

    private static volatile Samples samples = new Samples(0L, List.of());
    private static Thread watchdog;

    private static List<StackTraceElement[]> samplesOf(long frameStart) {
        Samples taken = samples;
        return taken.frameStart() == frameStart ? taken.stacks() : List.of();
    }

    private static void startWatchdog(Thread renderThread) {
        stopWatchdog();
        samples = new Samples(0L, List.of());
        Thread thread = new Thread(() -> watch(renderThread), "Alpaka perf watchdog");
        thread.setDaemon(true);
        thread.start();
        watchdog = thread;
    }

    private static void stopWatchdog() {
        if (watchdog != null) watchdog.interrupt();
        watchdog = null;
    }

    private static void watch(Thread renderThread) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                return;
            }
            long frame = frameStartNanos;
            if (frame == 0L) continue;
            long ageMs = (System.nanoTime() - frame) / 1_000_000L;
            Samples taken = samples;
            int have = taken.frameStart() == frame ? taken.stacks().size() : 0;
            boolean due = have == 0 ? ageMs >= SAMPLE_AFTER_MS : have == 1 && ageMs >= SECOND_SAMPLE_AFTER_MS;
            if (!due) continue;
            StackTraceElement[] stack = renderThread.getStackTrace();
            // The frame may have ended while the stack was taken; then it belongs to no frame.
            if (frameStartNanos != frame) continue;
            List<StackTraceElement[]> stacks = new ArrayList<>(have == 0 ? List.of() : taken.stacks());
            stacks.add(stack);
            samples = new Samples(frame, List.copyOf(stacks));
        }
    }

    /** Back to zero; measuring stays on or off as it was. */
    public static void reset() {
        Arrays.fill(NANOS, 0L);
        Arrays.fill(CALLS, 0L);
        Arrays.fill(PHASE_NANOS, 0L);
        Arrays.fill(SPIKES, 0L);
        WORST.clear();
        frames = workNanos = worstWorkNanos = 0L;
        spikesLogged = 0;
        frameStartNanos = 0L;
        windowStartNanos = System.nanoTime();
        windowEndNanos = 0L;
        threadId = Thread.currentThread().threadId();
        allocatedAtStart = allocatedAtEnd = allocatedBytes();
        gcRunsAtStart = gcRunsAtEnd = gcRuns();
        gcMillisAtStart = gcMillisAtEnd = gcMillis();
        gcSnapshot(gcAtStart);
        System.arraycopy(gcAtStart, 0, gcAtEnd, 0, 4);
    }

    /**
     * One line per section that ran: milliseconds spent per second of play, calls per second and
     * microseconds per call; then the client thread's allocation rate and the garbage collector. At
     * 60 fps a frame is 16.7 ms, so 1 ms per second is about 0.1% of the frame budget.
     */
    public static List<String> report() {
        List<String> lines = new ArrayList<>();
        if (!enabled && windowEndNanos == 0L) {
            lines.add("Not measuring. /alpakadebug perf start begins, /alpakadebug perf stop ends.");
            return lines;
        }
        long end = enabled ? System.nanoTime() : windowEndNanos;
        double seconds = Math.max(1e-9, (end - windowStartNanos) / 1e9);
        lines.add(String.format(Locale.ROOT, "%s %.0f s (ms per second · calls per second · µs per call):",
                enabled ? "measuring for" : "measured over", seconds));
        for (Section section : SECTIONS) {
            long calls = CALLS[section.ordinal()];
            if (calls == 0) continue;
            String name = section.id.substring("alpaka:".length());
            if (section.countOnly) {
                lines.add(String.format(Locale.ROOT, "%s  counted only · %.0f", name, calls / seconds));
                continue;
            }
            double totalMs = NANOS[section.ordinal()] / 1e6;
            lines.add(String.format(Locale.ROOT, "%s  %.2f · %.0f · %.1f",
                    name, totalMs / seconds, calls / seconds, NANOS[section.ordinal()] / 1e3 / calls));
        }
        if (lines.size() == 1) lines.add("nothing measured yet");

        if (frames > 0) {
            lines.add(String.format(Locale.ROOT, "frames %d, %.2f ms on average, worst %d ms · %s",
                    frames, workNanos / 1e6 / frames, worstWorkNanos / 1_000_000L, spikeCounts()));
            List<String> phases = new ArrayList<>();
            for (Phase phase : PHASES) {
                if (PHASE_NANOS[phase.ordinal()] == 0L) continue;
                phases.add(String.format(Locale.ROOT, "%s %.1f", phase.label, PHASE_NANOS[phase.ordinal()] / 1e6 / seconds));
            }
            if (!phases.isEmpty()) lines.add("frame phases, ms per second: " + String.join(" · ", phases));
            if (!WORST.isEmpty()) {
                lines.add("worst frames (ms):");
                for (Spike spike : WORST) lines.add(spike.describe());
                lines.add("every frame of " + SPIKE_MS + " ms or more is in latest.log as \"[AlpakaPerf] spike\"");
            }
        }

        long allocated = (enabled ? allocatedBytes() : allocatedAtEnd) - allocatedAtStart;
        long runs = (enabled ? gcRuns() : gcRunsAtEnd) - gcRunsAtStart;
        long millis = (enabled ? gcMillis() : gcMillisAtEnd) - gcMillisAtStart;
        String allocation = allocatedAtStart < 0 ? "client thread allocations: not available on this Java"
                : String.format(Locale.ROOT, "client thread allocates %.1f MB/s", allocated / 1e6 / seconds);
        long[] gcNow = new long[4];
        if (enabled) gcSnapshot(gcNow); else System.arraycopy(gcAtEnd, 0, gcNow, 0, 4);
        lines.add(String.format(Locale.ROOT, "%s · GC: %d runs, %d ms (pauses %d, %d ms; concurrent cycles %d)",
                allocation, runs, millis, gcNow[0] - gcAtStart[0], gcNow[1] - gcAtStart[1], gcNow[2] - gcAtStart[2]));
        return lines;
    }

    private static String spikeCounts() {
        List<String> counts = new ArrayList<>();
        for (int i = 0; i < SPIKE_STEPS_MS.length; i++) {
            counts.add(String.format(Locale.ROOT, "%d ms+: %d", SPIKE_STEPS_MS[i], SPIKES[i]));
        }
        return String.join(", ", counts);
    }

    /** CPU time the calling thread has used so far, or -1 where the JVM cannot tell. */
    private static long cpuNanos() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1L;
    }

    private static List<GarbageCollectorMXBean> collectors;

    /**
     * Collector pauses (count, ms) and concurrent cycles (count) so far, into {@code into}. ZGC
     * and Shenandoah report their pauses and their cycles as separate beans; the cycles run beside
     * the game and only stop it when it allocates faster than they free.
     */
    private static void gcSnapshot(long[] into) {
        if (collectors == null) collectors = ManagementFactory.getGarbageCollectorMXBeans();
        Arrays.fill(into, 0L);
        for (GarbageCollectorMXBean gc : collectors) {
            long count = Math.max(0L, gc.getCollectionCount());
            if (isPause(gc.getName())) {
                into[0] += count;
                into[1] += Math.max(0L, gc.getCollectionTime());
            } else {
                into[2] += count;
            }
        }
    }

    static boolean isPause(String collector) {
        return collector.contains("Pauses") || !(collector.contains("Cycles") || collector.contains("Concurrent"));
    }

    /** Bytes the measured thread has allocated so far, or -1 where the JVM cannot tell. */
    private static long allocatedBytes() {
        if (threadId < 0) return -1L;
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean threads
                && threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled()) {
            return threads.getThreadAllocatedBytes(threadId);
        }
        return -1L;
    }

    private static long gcRuns() {
        long runs = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            runs += Math.max(0L, gc.getCollectionCount());
        }
        return runs;
    }

    private static long gcMillis() {
        long millis = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            millis += Math.max(0L, gc.getCollectionTime());
        }
        return millis;
    }
}
