package net.alpaka.addons.config;

/** A gradient the player saved in the color picker: its start and end color, ARGB. */
public final class GradientPreset {
    public int start;
    public int end;

    public GradientPreset() {
    }

    public GradientPreset(int start, int end) {
        this.start = start;
        this.end = end;
    }
}
