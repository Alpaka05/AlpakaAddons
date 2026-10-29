package net.alpaka.addons.features.dungeon.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The F7/M7 boss phases by height and Goldor's four sections by position (terminals.md 1.1, 1.3). */
class GoldorPhaseTest {

    @Test
    void phasesByHeight() {
        assertEquals(1, GoldorPhase.phaseAt(230));
        assertEquals(2, GoldorPhase.phaseAt(170));
        assertEquals(3, GoldorPhase.phaseAt(155));
        assertEquals(3, GoldorPhase.phaseAt(115));
        assertEquals(4, GoldorPhase.phaseAt(100));
        assertEquals(4, GoldorPhase.phaseAt(60));
        assertEquals(5, GoldorPhase.phaseAt(20));
    }

    /** Every device, terminal and lever of the table lands in its own section. */
    @Test
    void everyKnownPositionFallsInItsSection() {
        int[][][] sections = {
                {{110, 91}, {111, 73}, {111, 79}, {89, 92}, {89, 101}, {106, 113}, {94, 113}},
                {{60, 143}, {68, 121}, {59, 122}, {47, 121}, {40, 122}, {39, 143}, {27, 127}, {23, 138}},
                {{0, 77}, {-3, 112}, {-3, 93}, {19, 93}, {-3, 77}, {14, 55}, {2, 55}},
                {{63, 35}, {41, 29}, {44, 29}, {67, 29}, {72, 48}, {86, 46}, {84, 34}},
        };
        for (int s = 0; s < sections.length; s++) {
            for (int[] pos : sections[s]) {
                assertEquals(s + 1, GoldorPhase.sectionAt(pos[0] + 0.5, pos[1] + 0.5), "(" + pos[0] + ", " + pos[1] + ")");
            }
        }
    }
}
