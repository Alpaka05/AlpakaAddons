package net.alpaka.addons.features.dungeon.terminal;

import java.util.function.BooleanSupplier;

/**
 * Whether a terminal click the player just made goes out or is refused; refused means nothing is
 * sent for it at all, never a different click (design.md 1.4, compliance.md T1-T4, R1-R4).
 *
 * The checks, each with its own switch (see {@link TerminalSettings}):
 * - first click: until {@code firstClickMs} have passed since the session opened AND as many
 *   server ticks ({@code firstClickTicks()}) were counted - time alone only when the server sent
 *   no ping within {@link ServerTicks#STALE_MS} before the session opened and none since. Not
 *   armed again on a carried-on session. Applies to every terminal, Click the Button on Time
 *   included.
 * - rate: within {@link TerminalSettings#RATE_MS} of the previous accepted click.
 * - wrong: {@link TerminalSolution#wrongClick} with pending cells counted as done; off by default,
 *   never for Click the Button on Time.
 *
 * The same verdict serves the vanilla chest (TerminalOverlay, at slotClicked's head) and the custom
 * window (before Outbound.menuClick). Asking has no effect of its own: the caller records an
 * allowed click once it went out ({@link TerminalSession#onClickSent}) and may report a refused
 * one ({@link TerminalSession#onRefused}).
 */
public final class TerminalClicks {

    public enum Verdict {
        /** Send it. */
        ALLOW,
        /** Refused: the terminal opened too recently. */
        TOO_EARLY,
        /** Refused: too soon after the last click. */
        TOO_FAST,
        /** Refused: the click would be wrong and Block Wrong Clicks is on. */
        WRONG
    }

    /**
     * When a click comes, in relation to the terminal: all the guards look at.
     *
     * @param nowMs          the click, on the {@code Util.getMillis()} clock
     * @param openedAtMs     when the session opened
     * @param ticksSinceOpen server ticks counted since then
     * @param pingsLive      whether the server's ticks can be counted for this terminal: pings were
     *                       arriving when it opened, or one has arrived since ({@link #ticksCount})
     * @param lastClickMs    the last click that went out
     */
    public record Timing(long nowMs, long openedAtMs, long ticksSinceOpen, boolean pingsLive, long lastClickMs) {}

    private TerminalClicks() {}

    /** The verdict for a click on {@code slot} with {@code button} (0 left, 1 right) at {@code nowMs} ({@code Util.getMillis()}). */
    public static Verdict check(TerminalSession session, int slot, int button, long nowMs) {
        if (session == null) return Verdict.ALLOW;
        // Read the chest before judging, so a click right after the server's answer is judged by it.
        session.refresh(nowMs);
        return decide(TerminalSettings.current(), session.kind(), timing(session, nowMs),
                () -> session.refusalSolution().wrongClick(slot, button));
    }

    /**
     * The first-click and rate guards alone, for clicks that are not on a terminal cell: a number
     * key, the drop key, a click on the inventory below. They still reach the terminal's window on
     * the server, so they wait like any other.
     */
    public static Verdict checkGuards(TerminalSession session, long nowMs) {
        if (session == null) return Verdict.ALLOW;
        return decide(TerminalSettings.current(), session.kind(), timing(session, nowMs), () -> false);
    }

    private static Timing timing(TerminalSession session, long nowMs) {
        long ticks = ServerTicks.since(session.openedAtTick());
        return new Timing(nowMs, session.openedAtMs(), ticks, ticksCount(session.pingsLiveAtOpen(), ticks),
                session.lastClickMs());
    }

    /**
     * Whether the first-click guard waits for server ticks in a terminal: decided by whether pings
     * were arriving when it opened, not by whether they arrive now. A server that freezes stops
     * sending them, and its own first-click check is believed to pause with it; falling back to time
     * alone then would let a click through a few ticks after the window opened on the server's
     * clock. There is no time limit: the terminal waits until the server ticks again. A tick counted
     * since it opened turns the wait on too, for a terminal that opened in the middle of a freeze.
     */
    static boolean ticksCount(boolean pingsLiveAtOpen, long ticksSinceOpen) {
        return pingsLiveAtOpen || ticksSinceOpen > 0;
    }

    /**
     * The guards themselves, from plain values. {@code wrong} is asked only when the wrong-click
     * refusal applies, since it means solving the chest.
     */
    public static Verdict decide(TerminalSettings settings, TerminalKind kind, Timing timing, BooleanSupplier wrong) {
        if (settings.firstClickGuard() && tooEarly(settings, timing)) return Verdict.TOO_EARLY;
        if (settings.rateGuard() && timing.nowMs() - timing.lastClickMs() < TerminalSettings.RATE_MS) {
            return Verdict.TOO_FAST;
        }
        // Never Click the Button on Time: only letting a click through when the pane lines up
        // would turn clicking away into perfect timing.
        if (settings.blockWrongClicks() && !kind.isMelody() && wrong.getAsBoolean()) return Verdict.WRONG;
        return Verdict.ALLOW;
    }

    /**
     * Whether the terminal opened too recently. The server is believed to run its own check on its
     * main thread, whose clock stops when the server freezes; so the server's ticks have to have
     * passed as well as the time. When the server sent no pings at all when the terminal opened, and
     * none since, the ticks cannot be counted, and time alone decides rather than locking it.
     */
    static boolean tooEarly(TerminalSettings settings, Timing timing) {
        if (timing.nowMs() - timing.openedAtMs() < settings.firstClickMs()) return true;
        return timing.pingsLive() && timing.ticksSinceOpen() < settings.firstClickTicks();
    }
}
