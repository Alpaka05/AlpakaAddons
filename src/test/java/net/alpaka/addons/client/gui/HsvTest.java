package net.alpaka.addons.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HsvTest {
    private static final Hsv START = new Hsv(0.0f, 0.0f, 1.0f);

    @Test
    void everyColorComesBackOutExactly() {
        // A preset or a typed hex code must survive the trip through the wheel's coordinates, or the
        // picker would change a color just by opening it.
        for (int rgb = 0; rgb <= 0xFFFFFF; rgb += 0x010307) {
            assertEquals(0xFF000000 | rgb, Hsv.of(rgb, START).rgb(), String.format("#%06X", rgb));
        }
    }

    @Test
    void primariesSitWhereTheWheelDrawsThem() {
        assertEquals(0xFFFF0000, new Hsv(0.0f, 1.0f, 1.0f).rgb());
        assertEquals(0xFF00FF00, new Hsv(1.0f / 3.0f, 1.0f, 1.0f).rgb());
        assertEquals(0xFF0000FF, new Hsv(2.0f / 3.0f, 1.0f, 1.0f).rgb());
        assertEquals(0xFFFFFFFF, new Hsv(0.4f, 0.0f, 1.0f).rgb());
    }

    @Test
    void greyAndBlackKeepTheSpotOnTheWheel() {
        Hsv teal = Hsv.of(0x29B6B2, START);
        Hsv black = Hsv.of(0x000000, teal);
        assertEquals(teal.hue(), black.hue());
        assertEquals(teal.sat(), black.sat());
        Hsv grey = Hsv.of(0x808080, teal);
        assertEquals(teal.hue(), grey.hue());
        assertEquals(0.0f, grey.sat());
    }
}
