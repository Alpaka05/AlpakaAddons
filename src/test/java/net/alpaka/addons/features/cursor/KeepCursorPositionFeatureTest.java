package net.alpaka.addons.features.cursor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeepCursorPositionFeatureTest {
    @BeforeEach
    void forgetEarlierPosition() {
        KeepCursorPositionFeature.restoreFor(false, 0L, 1.0, 1.0);
    }

    @Test
    void nextPageKeepsTheCursor() {
        KeepCursorPositionFeature.onGrab(312.5, 140.0, 1_000L);
        assertArrayEquals(new double[]{312.5, 140.0},
                KeepCursorPositionFeature.restoreFor(true, 1_050L, 854.0, 480.0));
    }

    @Test
    void aPositionIsUsedOnce() {
        KeepCursorPositionFeature.onGrab(312.5, 140.0, 1_000L);
        KeepCursorPositionFeature.restoreFor(true, 1_050L, 854.0, 480.0);
        assertNull(KeepCursorPositionFeature.restoreFor(true, 1_100L, 854.0, 480.0));
    }

    @Test
    void aMenuOpenedLaterStartsInTheMiddle() {
        KeepCursorPositionFeature.onGrab(312.5, 140.0, 1_000L);
        assertNull(KeepCursorPositionFeature.restoreFor(true,
                1_001L + KeepCursorPositionFeature.SWITCH_GRACE_MS, 854.0, 480.0));
    }

    @Test
    void screensOtherThanContainersStartInTheMiddle() {
        KeepCursorPositionFeature.onGrab(312.5, 140.0, 1_000L);
        assertNull(KeepCursorPositionFeature.restoreFor(false, 1_050L, 854.0, 480.0));
    }

    @Test
    void aShrunkWindowKeepsTheCursorInside() {
        KeepCursorPositionFeature.onGrab(900.0, -5.0, 1_000L);
        assertArrayEquals(new double[]{854.0, 0.0},
                KeepCursorPositionFeature.restoreFor(true, 1_050L, 854.0, 480.0));
    }
}
