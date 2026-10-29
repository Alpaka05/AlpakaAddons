package net.alpaka.addons.features.dungeon.device;

import java.util.Arrays;

/**
 * The player's own Arrow Align clicks that the server has not shown yet, so the click counts can
 * count down at once instead of a round trip later. Only what is drawn changes: every click has
 * already gone to the server exactly as vanilla sent it, and none is held back or refused.
 *
 * A right-clicked frame is expected to end one step further on than the rotation shown then, or
 * than the rotation already expected from a click before it. The expectation holds until the
 * server's rotation reaches it, or until {@link #HOLD_MS} pass without another click on that frame;
 * a click the server dropped then stops counting and the frame shows what the server says again.
 */
public final class ArrowAlignPrediction {

    /** How long an unconfirmed click counts. About a second covers a slow round trip (Odin keeps one second too). */
    public static final long HOLD_MS = 1000;

    private static final int NONE = -1;

    private final int[] expected = new int[ArrowAlignPatterns.CELLS];
    private final long[] clickedAt = new long[ArrowAlignPatterns.CELLS];

    public ArrowAlignPrediction() {
        clear();
    }

    /** The player right-clicked the frame of {@code cell}, which the server showed at {@code serverRotation}. */
    public void onOwnClick(int cell, int serverRotation, long nowMs) {
        if (cell < 0 || cell >= expected.length || serverRotation < 0) return;
        int from = pending(cell, nowMs) ? expected[cell] : serverRotation;
        expected[cell] = Math.floorMod(from + 1, ArrowAlignPatterns.ROTATIONS);
        clickedAt[cell] = nowMs;
    }

    /**
     * The rotation to count from for {@code cell}: the expected one while a click is unconfirmed,
     * otherwise the server's. Drops the expectation once the server has caught up or it timed out.
     */
    public int rotation(int cell, int serverRotation, long nowMs) {
        if (cell < 0 || cell >= expected.length || expected[cell] == NONE) return serverRotation;
        if (serverRotation == expected[cell] || !pending(cell, nowMs)) {
            expected[cell] = NONE;
            return serverRotation;
        }
        return expected[cell];
    }

    /** Whether {@code cell} has a click the server has not shown yet. */
    public boolean pending(int cell, long nowMs) {
        return expected[cell] != NONE && nowMs - clickedAt[cell] < HOLD_MS;
    }

    /** How many cells have an unconfirmed click; for the debug line. */
    public int pendingCount(long nowMs) {
        int n = 0;
        for (int i = 0; i < expected.length; i++) {
            if (pending(i, nowMs)) n++;
        }
        return n;
    }

    public void clear() {
        Arrays.fill(expected, NONE);
        Arrays.fill(clickedAt, 0L);
    }
}
