package net.alpaka.addons.features.dungeon.puzzle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Water Board's clock follows the server's ticks, and the wall only without pings (puzzles-water.md 5). */
class WaterBoardClockTest {

    @Test
    void countsServerTicksWhilePingsArrive() {
        WaterBoardClock clock = new WaterBoardClock();
        assertEquals(0, clock.read(1000, true, 0));
        assertEquals(5, clock.read(1005, true, 250));
        // A lag spike: no pings, no time, however long the wall says it was.
        assertEquals(5, clock.read(1005, true, 1500));
        assertEquals(8, clock.read(1008, true, 1550));
    }

    @Test
    void goesByTheWallWithoutPings() {
        WaterBoardClock clock = new WaterBoardClock();
        clock.read(0, false, 1000);
        assertEquals(1, clock.read(0, false, 1070));
        // The 20 ms left over carry into the next reading.
        assertEquals(2, clock.read(0, false, 1100));
        assertEquals(2, clock.read(0, false, 1149));
        assertEquals(3, clock.read(0, false, 1150));
        // Pings again: the ticks they counted since the last reading, not the wall.
        assertEquals(43, clock.read(40, true, 5000));
        assertEquals(45, clock.read(42, true, 9000));
    }
}
