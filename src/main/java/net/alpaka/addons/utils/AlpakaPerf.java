package net.alpaka.addons.utils;

import net.minecraft.util.profiling.Profiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the mod's own frame and tick time goes.
 *
 * Each hot hook is timed into a section. The section also shows in vanilla's profiler (F3 + L, or
 * the debug pie) under its {@code alpaka:} name, and {@code /alpakadebug perf} prints the totals
 * since the last reset. Measured before any performance work, so a change can show what it saved.
 *
 * Only call from the render or client thread, which is where every timed hook runs.
 */
public final class AlpakaPerf {
    public enum Section {
        HUD_PLAYER_MODEL("alpaka:hud_player_model"),
        HUD_WORLD_AGE("alpaka:hud_world_age"),
        HUD_SLAYER("alpaka:hud_slayer"),
        HUD_SLAYER_TIMER("alpaka:hud_slayer_timer"),
        HUD_INVENTORY("alpaka:hud_inventory"),
        HUD_NOTIFICATIONS("alpaka:hud_notifications"),
        BLUR_CAPTURE("alpaka:blur_capture"),
        CHAT_REPLAY("alpaka:chat_replay"),
        ENTITY_RULES("alpaka:entity_rules"),
        NAME_TAG("alpaka:name_tag"),
        SLAYER_TICK("alpaka:slayer_tick");

        final String id;

        Section(String id) {
            this.id = id;
        }
    }

    private static final Section[] SECTIONS = Section.values();
    private static final long[] NANOS = new long[SECTIONS.length];
    private static final long[] CALLS = new long[SECTIONS.length];
    private static long windowStartNanos = System.nanoTime();

    private AlpakaPerf() {}

    /** Starts a section; pass the result to {@link #end}. */
    public static long begin(Section section) {
        Profiler.get().push(section.id);
        return System.nanoTime();
    }

    public static void end(Section section, long startNanos) {
        NANOS[section.ordinal()] += System.nanoTime() - startNanos;
        CALLS[section.ordinal()]++;
        Profiler.get().pop();
    }

    public static void reset() {
        java.util.Arrays.fill(NANOS, 0L);
        java.util.Arrays.fill(CALLS, 0L);
        windowStartNanos = System.nanoTime();
    }

    /**
     * One line per section that ran: milliseconds spent per second of play, calls per second and
     * microseconds per call. At 60 fps a frame is 16.7 ms, so 1 ms per second is about 0.1% of the
     * frame budget.
     */
    public static List<String> report() {
        double seconds = Math.max(1e-9, (System.nanoTime() - windowStartNanos) / 1e9);
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "over %.0f s (ms per second · calls per second · µs per call):", seconds));
        for (Section section : SECTIONS) {
            long calls = CALLS[section.ordinal()];
            if (calls == 0) continue;
            double totalMs = NANOS[section.ordinal()] / 1e6;
            lines.add(String.format(Locale.ROOT, "%s  %.2f · %.0f · %.1f",
                    section.id.substring("alpaka:".length()), totalMs / seconds, calls / seconds,
                    NANOS[section.ordinal()] / 1e3 / calls));
        }
        if (lines.size() == 1) lines.add("nothing measured yet");
        return lines;
    }
}
