package net.alpaka.addons.features.dungeon.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The display-only count-down of the player's own Arrow Align clicks. */
class ArrowAlignPredictionTest {

    @Test
    void withoutAClickTheServersRotationCounts() {
        ArrowAlignPrediction prediction = new ArrowAlignPrediction();
        assertEquals(5, prediction.rotation(3, 5, 0));
        assertFalse(prediction.pending(3, 0));
    }

    @Test
    void aClickCountsUntilTheServerShowsIt() {
        ArrowAlignPrediction prediction = new ArrowAlignPrediction();
        prediction.onOwnClick(3, 5, 1000);
        assertEquals(6, prediction.rotation(3, 5, 1050));
        assertTrue(prediction.pending(3, 1050));
        // Other frames are not touched.
        assertEquals(2, prediction.rotation(4, 2, 1050));

        // The server's update arrives: from now on its rotation counts again.
        assertEquals(6, prediction.rotation(3, 6, 1100));
        assertFalse(prediction.pending(3, 1100));
        assertEquals(6, prediction.rotation(3, 6, 1150));
    }

    @Test
    void quickClicksAddUpAndWaitForTheLastUpdate() {
        ArrowAlignPrediction prediction = new ArrowAlignPrediction();
        prediction.onOwnClick(0, 1, 0);
        prediction.onOwnClick(0, 1, 40);
        prediction.onOwnClick(0, 1, 80);
        assertEquals(4, prediction.rotation(0, 1, 100));
        // The server shows the first two turns; the third is still on its way.
        assertEquals(4, prediction.rotation(0, 2, 150));
        assertEquals(4, prediction.rotation(0, 3, 200));
        assertEquals(4, prediction.rotation(0, 4, 250));
        assertEquals(0, prediction.pendingCount(250));
    }

    @Test
    void aClickTheServerNeverShowsStopsCounting() {
        ArrowAlignPrediction prediction = new ArrowAlignPrediction();
        prediction.onOwnClick(7, 2, 0);
        assertEquals(3, prediction.rotation(7, 2, ArrowAlignPrediction.HOLD_MS - 1));
        assertEquals(2, prediction.rotation(7, 2, ArrowAlignPrediction.HOLD_MS));
        // A new click after that starts from the server's rotation again, not the dropped one.
        prediction.onOwnClick(7, 2, ArrowAlignPrediction.HOLD_MS + 10);
        assertEquals(3, prediction.rotation(7, 2, ArrowAlignPrediction.HOLD_MS + 20));
    }

    @Test
    void turnsWrapAfterTheEighthStep() {
        ArrowAlignPrediction prediction = new ArrowAlignPrediction();
        prediction.onOwnClick(12, 7, 0);
        assertEquals(0, prediction.rotation(12, 7, 10));
    }

    @Test
    void clearForgetsEveryClick() {
        ArrowAlignPrediction prediction = new ArrowAlignPrediction();
        prediction.onOwnClick(1, 1, 0);
        prediction.onOwnClick(2, 1, 0);
        assertEquals(2, prediction.pendingCount(10));
        prediction.clear();
        assertEquals(0, prediction.pendingCount(10));
        assertEquals(1, prediction.rotation(1, 1, 10));
    }

    @Test
    void cellsOutsideTheWallAreIgnored() {
        ArrowAlignPrediction prediction = new ArrowAlignPrediction();
        prediction.onOwnClick(-1, 1, 0);
        prediction.onOwnClick(25, 1, 0);
        assertEquals(1, prediction.rotation(-1, 1, 10));
        assertEquals(1, prediction.rotation(25, 1, 10));
        assertEquals(0, prediction.pendingCount(10));
    }
}
