package net.alpaka.addons.features.dungeon.render;

import net.alpaka.addons.client.gui.Hsv;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creeper Beams' pair colors follow the Solvers colors the player set. */
class SolverColorsTest {

    /** The shipped Solution, Next and Wrong colors. */
    private static final int SOLUTION = 0xCC55FF55;
    private static final int NEXT = 0xCCFFD24D;
    private static final int WRONG = 0xCCFF5555;

    @Test
    void theFirstTwoPairsTakeTheSolutionAndNextColors() {
        assertEquals(SOLUTION, SolverColors.pairColor(0, SOLUTION, NEXT, WRONG));
        assertEquals(NEXT, SolverColors.pairColor(1, SOLUTION, NEXT, WRONG));
        // Further pairs repeat the four.
        for (int i = 0; i < 4; i++) {
            assertEquals(SolverColors.pairColor(i, SOLUTION, NEXT, WRONG), SolverColors.pairColor(i + 4, SOLUTION, NEXT, WRONG));
        }
    }

    @Test
    void fourDistinctColorsAwayFromTheWrongColor() {
        int[][] settings = {
                {SOLUTION, NEXT, WRONG},
                {0xFF3FA8FF, 0xFFFF9A3C, 0xFFFF5555},
                {0xFFFFFFFF, 0xFFFFD24D, 0xFFFF5555},  // a white Solution Color
                {0x80808080, 0xFF808080, 0xFF202020},  // nothing but greys
        };
        for (int[] s : settings) {
            Set<Integer> colors = new HashSet<>();
            for (int i = 0; i < 4; i++) colors.add(SolverColors.pairColor(i, s[0], s[1], s[2]));
            assertEquals(4, colors.size(), "four colors for " + Integer.toHexString(s[0]));
            for (int i = 2; i < 4; i++) {
                int color = SolverColors.pairColor(i, s[0], s[1], s[2]);
                assertNotEquals(s[2], color, "never the Wrong Color");
                assertEquals(s[0] >>> 24, color >>> 24, "the Solution Color's alpha");
                Hsv hsv = Hsv.of(color & 0xFFFFFF, new Hsv(0, 0, 0));
                assertTrue(hsv.sat() >= 0.55f && hsv.val() >= 0.8f, "a clear color");
            }
        }
        // The shipped colors: the turned hues keep well clear of the red that means "wrong".
        Hsv wrong = Hsv.of(WRONG & 0xFFFFFF, new Hsv(0, 0, 0));
        for (int i = 2; i < 4; i++) {
            Hsv hsv = Hsv.of(SolverColors.pairColor(i, SOLUTION, NEXT, WRONG) & 0xFFFFFF, new Hsv(0, 0, 0));
            float d = Math.abs(hsv.hue() - wrong.hue()) % 1.0f;
            assertTrue(Math.min(d, 1.0f - d) >= 0.15f, "pair " + i + " hue " + hsv.hue());
        }
    }

    @Test
    void theTurnedColorsFollowTheSolutionColor() {
        int green = SolverColors.pairColor(2, SOLUTION, NEXT, WRONG);
        int blue = SolverColors.pairColor(2, 0xCC3F6BFF, NEXT, WRONG);
        assertNotEquals(green, blue);
    }
}
