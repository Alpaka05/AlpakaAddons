package net.alpaka.addons.features.market;

import net.alpaka.addons.client.gui.AlpakaStyle;

/**
 * The market's own colors - coins, rarities, the price lines - are bright, made for the dark menu.
 * On a light Menu Theme they would fade into the panel, so there they are drawn darker.
 */
public final class MarketInk {
    private MarketInk() {}

    /** Whether the menus are light: their panel is brighter than mid-gray. */
    public static boolean light() {
        int c = AlpakaStyle.PANEL;
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        return r * 299 + g * 587 + b * 114 > 128 * 1000;
    }

    /** The color as it reads on the current panel: unchanged on a dark one, darker on a light one. */
    public static int ink(int argb) {
        if (!light()) return argb;
        int a = argb & 0xFF000000;
        int r = ((argb >> 16) & 0xFF) * 55 / 100;
        int g = ((argb >> 8) & 0xFF) * 55 / 100;
        int b = (argb & 0xFF) * 55 / 100;
        return a | r << 16 | g << 8 | b;
    }
}
