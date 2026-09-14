package net.alpaka.addons.features.tooltip;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Lets the mouse wheel move a tooltip that is taller than the screen.
 *
 * Vanilla positions such a tooltip so that its top is cut off and nothing can be done about it;
 * a long SkyBlock item lore simply ends where the screen does. With this on, a tooltip that does not
 * fit starts at the top edge instead and the wheel moves it up, revealing the rest, for as long as
 * the pointer stays on the same item. Tooltips that fit are left exactly where vanilla puts them
 * and the wheel keeps its normal meaning, so a creative inventory or a scrolling list is unaffected
 * until an oversized tooltip is actually on screen.
 *
 * The hook sits where every tooltip is positioned ({@code GuiGraphicsExtractor.tooltip}), so item
 * tooltips, mod tooltips and text tooltips all behave the same. The scroll offset belongs to the
 * tooltip currently shown: a change of size (which is what a different item looks like from here)
 * or a frame without a tooltip resets it, so the next item starts from the top again.
 */
public final class ScrollableTooltipsFeature {

    private ScrollableTooltipsFeature() {
    }

    /** Gap kept between the tooltip box and the screen edge, like the vanilla positioner's. */
    private static final int EDGE = 4;

    /** How far one wheel notch moves the tooltip. */
    private static final int STEP = 12;

    /** A tooltip not drawn for this long is gone: the offset resets and the wheel is handed back. */
    private static final long GONE_MS = 150L;

    /** Pixels the tooltip is shifted up, 0 = top edge visible. */
    private static int scroll = 0;

    /** How far it can be shifted before the bottom edge shows; 0 while the tooltip fits. */
    private static int maxScroll = 0;

    /** Size of the tooltip last drawn, which is how one item is told from the next. */
    private static int fingerprint = 0;
    private static long lastDrawnMs = 0L;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.scrollableTooltipsEnabled;
    }

    /**
     * Called with the position the vanilla positioner chose. Returns that position for a tooltip
     * that fits, and the scrolled one for a tooltip that does not.
     *
     * @param guiHeight the screen height in GUI pixels
     * @param width     the tooltip's content width, as handed to the positioner
     * @param height    the tooltip's content height, as handed to the positioner
     */
    public static Vector2ic position(Vector2ic vanilla, int guiHeight, int width, int height) {
        if (!isEnabled()) {
            maxScroll = 0;
            return vanilla;
        }
        long now = System.currentTimeMillis();
        int print = width * 31 + height;
        if (print != fingerprint || now - lastDrawnMs > GONE_MS) {
            fingerprint = print;
            scroll = 0;
        }
        lastDrawnMs = now;

        // The box adds its padding around the content; the margins keep it off both edges.
        int overflow = height + 2 * EDGE - guiHeight;
        if (overflow <= 0) {
            maxScroll = 0;
            return vanilla;
        }
        maxScroll = overflow;
        scroll = Mth.clamp(scroll, 0, maxScroll);
        return new Vector2i(vanilla.x(), EDGE - scroll);
    }

    /**
     * The scroll wheel while a screen is open. True when the event was taken for the tooltip, which
     * also keeps it away from whatever the screen would otherwise scroll.
     */
    public static boolean onScroll(double yOffset) {
        if (!isEnabled() || maxScroll <= 0) return false;
        if (System.currentTimeMillis() - lastDrawnMs > GONE_MS) return false;
        if (yOffset != 0) {
            // Wheel down reads further down the tooltip, which means moving the box up. A fast wheel
            // reports several notches at once and moves that much further; a trackpad's fraction of
            // a notch still moves at least one step, so a nudge always does something.
            int delta = (int) Math.round(Math.abs(yOffset) * STEP);
            if (delta < STEP) delta = STEP;
            scroll = Mth.clamp(scroll - (int) Math.signum(yOffset) * delta, 0, maxScroll);
        }
        return true;
    }
}
