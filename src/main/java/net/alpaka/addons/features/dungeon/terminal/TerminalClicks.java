package net.alpaka.addons.features.dungeon.terminal;

/**
 * Whether a terminal click the player just made goes out or is refused; refused means nothing is
 * sent for it at all, never a different click (design.md 1.4, compliance.md T1-T4, R1-R4).
 *
 * The checks, each with its own switch (see {@link TerminalSettings}):
 * - first click: until {@code firstClickMs} have passed since the session opened AND as many
 *   server ticks ({@code firstClickTicks()}) were counted - time alone when no ping arrived for
 *   {@link ServerTicks#STALE_MS}. Not armed again on a carried-on session. Applies to every
 *   terminal, Click the Button on Time included.
 * - rate: within {@link TerminalSettings#RATE_MS} of the previous accepted click.
 * - wrong: {@link TerminalSolution#wrongClick} with pending cells counted as done; off by default,
 *   never for Click the Button on Time.
 *
 * The same verdict serves the vanilla chest (TerminalOverlay, at slotClicked's head) and the custom
 * window (before Outbound.menuClick).
 *
 * Not built yet: lets every click through.
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

    private TerminalClicks() {}

    /** The verdict for a click on {@code slot} with {@code button} (0 left, 1 right) at {@code nowMs} ({@code Util.getMillis()}). */
    public static Verdict check(TerminalSession session, int slot, int button, long nowMs) {
        return Verdict.ALLOW;
    }
}
