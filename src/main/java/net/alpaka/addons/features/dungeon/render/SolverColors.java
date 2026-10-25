package net.alpaka.addons.features.dungeon.render;

import net.alpaka.addons.config.AlpakaConfig;

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
}
