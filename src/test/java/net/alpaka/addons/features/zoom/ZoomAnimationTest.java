package net.alpaka.addons.features.zoom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The zoom animations, run frame by frame at 60 fps on the logarithm of the factor. */
class ZoomAnimationTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double TARGET = Math.log(4.0);

    /** The shown value after running the animation for the given time. */
    private static double run(int animation, double durationSec, double seconds, double[] peak) {
        double shown = 0.0;
        ZoomFeature.step(0.0, 0.0, 0.0, ZoomFeature.ANIMATION_INSTANT, durationSec); // clears the spring's speed
        for (double t = 0.0; t < seconds; t += FRAME) {
            shown = ZoomFeature.step(shown, TARGET, FRAME, animation, durationSec);
            if (peak != null) peak[0] = Math.max(peak[0], shown);
        }
        return shown;
    }

    @Test
    void instantJumpsStraightThere() {
        assertEquals(TARGET, ZoomFeature.step(0.0, TARGET, FRAME, ZoomFeature.ANIMATION_INSTANT, 0.25));
    }

    @Test
    void smoothIsPartWayAtFirstAndThereAfterItsDuration() {
        double early = run(ZoomFeature.ANIMATION_SMOOTH, 0.25, 0.05, null);
        assertTrue(early > 0.1 * TARGET && early < 0.9 * TARGET, "early: " + early);
        double done = run(ZoomFeature.ANIMATION_SMOOTH, 0.25, 0.25, null);
        assertTrue(Math.abs(TARGET - done) < 0.03 * TARGET, "after the duration: " + done);
    }

    @Test
    void bouncyOvershootsThenSettles() {
        double[] peak = {0.0};
        double settled = run(ZoomFeature.ANIMATION_BOUNCY, 0.25, 1.0, peak);
        assertTrue(peak[0] > TARGET * 1.02, "no overshoot: " + peak[0]);
        assertEquals(TARGET, settled, 0.01 * TARGET);
    }

    @Test
    void bouncyNeverZoomsOutPastTheNormalView() {
        double shown = TARGET;
        for (int i = 0; i < 120; i++) {
            shown = ZoomFeature.step(shown, 0.0, FRAME, ZoomFeature.ANIMATION_BOUNCY, 0.25);
            assertTrue(shown >= 0.0);
        }
        assertEquals(0.0, shown, 1.0e-3);
    }
}
