package net.alpaka.addons.features.dungeon.render;

import net.alpaka.addons.client.gui.Hsv;
import net.alpaka.addons.config.AlpakaConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The Solvers colors and drawing style, read from the config with every ranged value clamped.
 *
 * The settings' sliders keep them in range, but a hand-edited file does not, and a box style of 7
 * or a line 40 pixels wide should not reach the renderer. The device solvers use the same colors.
 */
public final class SolverColors {

    /** The Box Style choices, by config value. */
    public static final String[] BOX_STYLES = {"Outline", "Filled", "Outline & Fill"};
    public static final int OUTLINE = 0;
    public static final int FILLED = 1;
    public static final int BOTH = 2;

    public static final float MIN_LINE_WIDTH = 1.0f;
    public static final float MAX_LINE_WIDTH = 6.0f;

    /** A fill uses the color at this fraction of its alpha, so the block under it stays readable. */
    public static final float FILL_ALPHA = 0.35f;

    private SolverColors() {}

    /** The thing to do now. */
    public static int solution() {
        return AlpakaConfig.instance.dungeonSolverColor;
    }

    /** What comes after it. */
    public static int next() {
        return AlpakaConfig.instance.dungeonSolverNextColor;
    }

    /** Used pads, liars, wrong answers. */
    public static int wrong() {
        return AlpakaConfig.instance.dungeonSolverWrongColor;
    }

    /** {@link #OUTLINE}, {@link #FILLED} or {@link #BOTH}. */
    public static int boxStyle() {
        return clampBoxStyle(AlpakaConfig.instance.dungeonSolverBoxStyle);
    }

    /** Outline and line thickness in screen pixels. */
    public static float lineWidth() {
        return clampLineWidth(AlpakaConfig.instance.dungeonSolverLineWidth);
    }

    /** Whether order numbers, click counts and countdowns are drawn. */
    public static boolean labels() {
        return AlpakaConfig.instance.dungeonSolverLabels;
    }

    public static int clampBoxStyle(int style) {
        return style < OUTLINE || style > BOTH ? BOTH : style;
    }

    /** The Box Style slider's text for a value, whatever the value; it reads the config as it is. */
    public static String boxStyleName(int style) {
        return BOX_STYLES[clampBoxStyle(style)];
    }

    public static float clampLineWidth(float width) {
        if (!Float.isFinite(width)) return 2.5f;
        return Math.max(MIN_LINE_WIDTH, Math.min(MAX_LINE_WIDTH, width));
    }

    /** {@code argb} with its alpha scaled down for a fill. */
    public static int fill(int argb) {
        return scaleAlpha(argb, FILL_ALPHA);
    }

    /** {@code argb} with its alpha multiplied by {@code factor} (0..1). */
    public static int scaleAlpha(int argb, float factor) {
        int alpha = Math.round((argb >>> 24) * Math.max(0.0f, Math.min(1.0f, factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** {@code argb} with alpha {@code alpha} (0..255). */
    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    /** Creeper Beams' color for the pair numbered {@code index} (from 0), from the colors set now. */
    public static int pairColor(int index) {
        return pairColor(index, solution(), next(), wrong());
    }

    /**
     * A color per Creeper Beams pair, from the configured colors: the first pair takes the Solution
     * Color, the second the Next Color, the third and fourth two hues of the Solution Color turned
     * as far as they go from the Solution, Next and Wrong hues (red would read as "wrong") and from
     * each other. Further pairs repeat the four; their numbers tell them apart. The turned hues keep
     * the Solution Color's alpha and are at least fairly saturated and bright, so a white or grey
     * Solution Color still gives two clear colors. Pure.
     */
    public static int pairColor(int index, int solution, int next, int wrong) {
        int slot = Math.floorMod(index, 4);
        if (slot == 0) return solution;
        if (slot == 1) return next;
        Hsv base = hsv(solution);
        List<Float> avoid = new ArrayList<>();
        for (int argb : new int[]{solution, next, wrong}) {
            Hsv c = hsv(argb);
            // A grey has no hue worth keeping away from.
            if (c.sat() >= MIN_HUE_SATURATION) avoid.add(c.hue());
        }
        float third = farthestHue(base.hue(), avoid);
        float hue = third;
        if (slot == 3) {
            avoid.add(third);
            hue = farthestHue(base.hue(), avoid);
        }
        int rgb = new Hsv(hue, Math.max(base.sat(), PAIR_SATURATION), Math.max(base.val(), PAIR_BRIGHTNESS)).rgb();
        return (solution & 0xFF000000) | (rgb & 0x00FFFFFF);
    }

    private static final float MIN_HUE_SATURATION = 0.15f;
    private static final float PAIR_SATURATION = 0.6f;
    private static final float PAIR_BRIGHTNESS = 0.85f;
    /** The hues tried for the turned pair colors: the Solution hue turned by twelfths. */
    private static final int HUE_STEPS = 12;

    /** Of {@code from} turned by twelfths, the hue farthest from every hue in {@code avoid}; the first on a tie. */
    private static float farthestHue(float from, List<Float> avoid) {
        float best = from;
        float bestDistance = -1.0f;
        for (int k = 1; k < HUE_STEPS; k++) {
            float hue = (from + (float) k / HUE_STEPS) % 1.0f;
            float distance = 1.0f;
            for (float other : avoid) distance = Math.min(distance, hueDistance(hue, other));
            if (distance > bestDistance + 1.0e-4f) {
                bestDistance = distance;
                best = hue;
            }
        }
        return best;
    }

    /** How far apart two hues (0..1) are around the circle, 0..0.5. */
    private static float hueDistance(float a, float b) {
        float d = Math.abs(a - b) % 1.0f;
        return Math.min(d, 1.0f - d);
    }

    private static Hsv hsv(int argb) {
        return Hsv.of(argb & 0x00FFFFFF, new Hsv(0.0f, 0.0f, 0.0f));
    }
}
