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
 * until an oversized tooltip is actually on screen - unless Always Scrollable lets the wheel move
 * every tooltip, which then takes the wheel from the screen while one is up.
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

    /** A tooltip not drawn for this long is gone: the offset resets and the wheel is handed back. */
    private static final long GONE_MS = 150L;

    /** How quickly a smooth scroll catches up with the wheel, as a time constant in milliseconds. */
    private static final float SMOOTH_TAU_MS = 45.0f;

    /** Pixels the tooltip is to be shifted up from where it starts; negative moves it down. */
    private static int scroll = 0;
    /** Where it is drawn, easing toward {@link #scroll} while scrolling is smooth. */
    private static float shown = 0.0f;

    /** How far it can be shifted either way; both 0 while the wheel has nothing to do. */
    private static int minScroll = 0;
    private static int maxScroll = 0;

    /** Size of the tooltip last drawn, which is how one item is told from the next. */
    private static int fingerprint = 0;
    private static long lastDrawnMs = 0L;

    /**
     * A screen whose tooltips are short and whose wheel always belongs to the screen, such as the
     * inventory buttons' icon grid: with Always Scrollable on, every item name there would
     * otherwise take the wheel from the grid.
     */
    public interface KeepsWheel {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.scrollableTooltipsEnabled;
    }

    /**
     * Called with the position the vanilla positioner chose. Returns that position for a tooltip
     * the wheel has not moved, and the scrolled one otherwise.
     *
     * A tooltip taller than the screen starts at the top edge and scrolls until its bottom shows.
     * With Always Scrollable, one that fits can be moved too, from where vanilla put it as far as
     * either screen edge.
     *
     * @param guiHeight the screen height in GUI pixels
     * @param width     the tooltip's content width, as handed to the positioner
     * @param height    the tooltip's content height, as handed to the positioner
     */
    public static Vector2ic position(Vector2ic vanilla, int guiHeight, int width, int height) {
        if (!isEnabled()) {
            minScroll = maxScroll = 0;
            return vanilla;
        }
        AlpakaConfig cfg = AlpakaConfig.instance;
        long now = System.currentTimeMillis();
        long sinceLast = now - lastDrawnMs;
        int print = width * 31 + height;
        if (print != fingerprint || sinceLast > GONE_MS) {
            fingerprint = print;
            scroll = 0;
            shown = 0.0f;
        }
        lastDrawnMs = now;

        // The box adds its padding around the content; the margins keep it off both edges.
        int overflow = height + 2 * EDGE - guiHeight;
        int startY;
        if (overflow > 0) {
            startY = EDGE;
            minScroll = 0;
            maxScroll = overflow;
        } else if (cfg.scrollableTooltipsAlways) {
            startY = vanilla.y();
            minScroll = Math.min(0, vanilla.y() - (guiHeight - height - EDGE));
            maxScroll = Math.max(0, vanilla.y() - EDGE);
        } else {
            minScroll = maxScroll = 0;
            return vanilla;
        }
        scroll = Mth.clamp(scroll, minScroll, maxScroll);
        if (cfg.scrollableTooltipsSmooth && sinceLast <= GONE_MS) {
            shown += (scroll - shown) * (1.0f - (float) Math.exp(-sinceLast / SMOOTH_TAU_MS));
            if (Math.abs(scroll - shown) < 0.5f) shown = scroll;
        } else {
            shown = scroll;
        }
        return new Vector2i(vanilla.x(), startY - Math.round(shown));
    }

    /**
     * The scroll wheel while a screen is open. True when the event was taken for the tooltip, which
     * also keeps it away from whatever the screen would otherwise scroll.
     */
    public static boolean onScroll(double yOffset) {
        if (!isEnabled() || minScroll == maxScroll) return false;
        if (System.currentTimeMillis() - lastDrawnMs > GONE_MS) return false;
        if (yOffset != 0) {
            // Wheel down reads further down the tooltip, which means moving the box up; inverted,
            // the box follows the wheel. A fast wheel reports several notches at once and moves that
            // much further; a trackpad's fraction of a notch still moves at least one step, so a
            // nudge always does something.
            int step = Math.max(1, Math.round(AlpakaConfig.instance.scrollableTooltipsSpeed));
            int delta = Math.max(step, (int) Math.round(Math.abs(yOffset) * step));
            int direction = (int) Math.signum(yOffset) * (AlpakaConfig.instance.scrollableTooltipsInvert ? -1 : 1);
            scroll = Mth.clamp(scroll - direction * delta, minScroll, maxScroll);
        }
        return true;
    }
}
