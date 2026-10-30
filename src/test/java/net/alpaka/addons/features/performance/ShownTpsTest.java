package net.alpaka.addons.features.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShownTpsTest {

    @Test
    void firstReadingShowsAtOnce() {
        assertEquals(193, new ShownTps().update(193, 0));
    }

    @Test
    void tinyWobbleIsNeverShown() {
        ShownTps shown = new ShownTps();
        shown.update(190, 0);
        int[] wobble = {191, 189, 192, 188, 190, 191};
        long now = 0;
        for (int tenths : wobble) {
            now += 5_000;
            assertEquals(190, shown.update(tenths, now));
        }
    }

    @Test
    void smallChangeWaitsForTheHold() {
        ShownTps shown = new ShownTps();
        shown.update(190, 0);
        assertEquals(190, shown.update(185, 1_000));
        assertEquals(190, shown.update(185, 2_999));
        assertEquals(185, shown.update(185, 3_000));
        // And the next one waits again.
        assertEquals(185, shown.update(180, 4_000));
        assertEquals(180, shown.update(180, 6_000));
    }

    @Test
    void realDropShowsAtOnce() {
        ShownTps shown = new ShownTps();
        shown.update(200, 0);
        assertEquals(150, shown.update(150, 100));
        assertEquals(200, shown.update(200, 200));
    }

    @Test
    void backToFullSpeedShowsAfterTheHold() {
        ShownTps shown = new ShownTps();
        shown.update(194, 0);
        assertEquals(194, shown.update(200, 1_000));
        assertEquals(200, shown.update(200, 3_000));
    }

    @Test
    void unknownAndKnownSwitchAtOnce() {
        ShownTps shown = new ShownTps();
        shown.update(200, 0);
        assertEquals(-1, shown.update(-1, 10));
        assertEquals(198, shown.update(198, 20));
    }
}
