package net.alpaka.addons.utils;

import net.minecraft.util.profiling.Profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
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

    /** What {@link #begin} hands out while measuring is off; {@link #end} then does nothing. */
    private static final long OFF = Long.MIN_VALUE;

    private static final Section[] SECTIONS = Section.values();
    private static final long[] NANOS = new long[SECTIONS.length];
    private static final long[] CALLS = new long[SECTIONS.length];

    private static boolean enabled = false;
    private static long windowStartNanos = System.nanoTime();
    /** When measuring stopped, or 0 while it runs or never ran. */
    private static long windowEndNanos = 0L;

    /** The thread measured for allocations: the one that started measuring, the client thread. */
    private static long threadId = -1L;
    private static long allocatedAtStart, allocatedAtEnd;
    private static long gcRunsAtStart, gcRunsAtEnd, gcMillisAtStart, gcMillisAtEnd;

    private AlpakaPerf() {}

    public static boolean isEnabled() {
        return enabled;
    }

    /** Starts measuring, from zero. */
    public static void start() {
        reset();
        enabled = true;
    }

    /** Stops measuring; what was measured stays for {@link #report()}. */
    public static void stop() {
        if (!enabled) return;
        enabled = false;
        windowEndNanos = System.nanoTime();
        allocatedAtEnd = allocatedBytes();
        gcRunsAtEnd = gcRuns();
        gcMillisAtEnd = gcMillis();
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
        NANOS[section.ordinal()] += System.nanoTime() - startNanos;
        CALLS[section.ordinal()]++;
        Profiler.get().pop();
    }

    /** One call of a section that is only counted. */
    public static void count(Section section) {
        if (enabled) CALLS[section.ordinal()]++;
    }

    /** Back to zero; measuring stays on or off as it was. */
    public static void reset() {
        java.util.Arrays.fill(NANOS, 0L);
        java.util.Arrays.fill(CALLS, 0L);
        windowStartNanos = System.nanoTime();
        windowEndNanos = 0L;
        threadId = Thread.currentThread().threadId();
        allocatedAtStart = allocatedAtEnd = allocatedBytes();
        gcRunsAtStart = gcRunsAtEnd = gcRuns();
        gcMillisAtStart = gcMillisAtEnd = gcMillis();
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
                enabled ? "Measuring for" : "Measured over", seconds));
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
        if (lines.size() == 1) lines.add("Nothing measured yet.");

        long allocated = (enabled ? allocatedBytes() : allocatedAtEnd) - allocatedAtStart;
        long runs = (enabled ? gcRuns() : gcRunsAtEnd) - gcRunsAtStart;
        long millis = (enabled ? gcMillis() : gcMillisAtEnd) - gcMillisAtStart;
        String allocation = allocatedAtStart < 0 ? "Client thread allocations: not available on this Java"
                : String.format(Locale.ROOT, "Client thread allocates %.1f MB/s", allocated / 1e6 / seconds);
        lines.add(String.format(Locale.ROOT, "%s · GC: %d runs, %d ms", allocation, runs, millis));
        return lines;
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
