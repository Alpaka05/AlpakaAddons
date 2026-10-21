package net.alpaka.addons.client.gui;

/**
 * Hue, saturation and value, the color picker's wheel coordinates: the hue is the angle round the
 * wheel, the saturation the distance from its middle, the value the brightness slider. All three run
 * from 0 to 1. Written out here instead of borrowing java.awt.Color, which the client never loads.
 */
public record Hsv(float hue, float sat, float val) {

    /** The opaque RGB color (0xFFRRGGBB) for these coordinates. */
    public int rgb() {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) h % 6;
        float f = h - (float) Math.floor(h);
        float p = val * (1.0f - sat);
        float q = val * (1.0f - sat * f);
        float t = val * (1.0f - sat * (1.0f - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = val; g = t; b = p; }
            case 1 -> { r = q; g = val; b = p; }
            case 2 -> { r = p; g = val; b = t; }
            case 3 -> { r = p; g = q; b = val; }
            case 4 -> { r = t; g = p; b = val; }
            default -> { r = val; g = p; b = q; }
        }
        return 0xFF000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float c) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, c)) * 255.0f);
    }

    /**
     * The coordinates of an RGB color. A grey has no hue and black no saturation either; for those
     * the ones of {@code previous} are kept, so dragging the brightness down to black and back up
     * again returns to the same spot on the wheel.
     */
    public static Hsv of(int rgb, Hsv previous) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (max <= 0.0f) return new Hsv(previous.hue, previous.sat, 0.0f);
        if (delta <= 0.0f) return new Hsv(previous.hue, 0.0f, max);
        float hue;
        if (max == r) hue = ((g - b) / delta) / 6.0f;
        else if (max == g) hue = ((b - r) / delta + 2.0f) / 6.0f;
        else hue = ((r - g) / delta + 4.0f) / 6.0f;
        if (hue < 0.0f) hue += 1.0f;
        return new Hsv(hue, delta / max, max);
    }
}
