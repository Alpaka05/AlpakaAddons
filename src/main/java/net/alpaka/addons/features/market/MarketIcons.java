package net.alpaka.addons.features.market;

import net.alpaka.addons.features.wheel.WheelMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The market screens' own icons: the two markets and the small symbols beside prices and controls.
 * The categories show Hypixel's own items instead.
 *
 * Drawn as smooth shapes rather than textures, like the config menu's chevrons, so they stay sharp
 * at every GUI scale and take any color. Each icon is laid out on a 16 by 16 grid and scaled to the
 * size it is drawn at.
 */
public final class MarketIcons {
    private MarketIcons() {}

    public enum Glyph {
        // The markets
        AUCTION_HOUSE, BAZAAR,
        // Small symbols
        COIN, CLOCK, SEARCH, SORT, TIER, BIN, ORDERS, SELL, SACK, INVENTORY, CLASSIC, PERSON, BACK, CLOSE, BOX, PROMPT, PASTE
    }

    /** The icon of a market, for the header. */
    public static Glyph of(MarketKind kind) {
        return kind == MarketKind.BAZAAR ? Glyph.BAZAAR : Glyph.AUCTION_HOUSE;
    }

    /** Draws the glyph in a square of {@code size} GUI pixels whose top-left corner is at x, y. */
    public static void draw(GuiGraphicsExtractor graphics, Glyph glyph, float x, float y, float size, int color) {
        int scale = Minecraft.getInstance() == null ? 2 : (int) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        WheelMesh mesh = new WheelMesh(1.0f / scale);
        Pen pen = new Pen(mesh, x, y, size / 16f, 0f);
        switch (glyph) {
            case AUCTION_HOUSE -> gavel(pen, color);
            case BAZAAR -> stall(pen, color);
            case COIN -> coin(pen, color);
            case CLOCK -> clock(pen, color);
            case SEARCH -> magnifier(pen, color);
            case SORT -> sortBars(pen, color);
            case TIER -> gem(pen, color);
            case BIN -> bolt(pen, color);
            case ORDERS -> clipboard(pen, color);
            case SELL -> sellArrow(pen, color);
            case SACK -> sack(pen, color);
            case INVENTORY -> backpack(pen, color);
            case CLASSIC -> grid(pen, color);
            case PERSON -> person(pen, color);
            case BACK -> arrowLeft(pen, color);
            case CLOSE -> cross(pen, color);
            case BOX -> box(pen, color);
            case PROMPT -> chevron(pen, color);
            case PASTE -> clipboard(pen, color);
        }
        mesh.submit(graphics);
    }

    // ---- The markets ----------------------------------------------------------------------------

    private static void gavel(Pen p, int color) {
        // The head, tilted, with a wider band at each end.
        Pen tilted = p.rotated((float) Math.toRadians(-45), 9.6f, 6.4f);
        tilted.poly(color, 6.1f, 4.8f, 13.1f, 4.8f, 13.1f, 8.0f, 6.1f, 8.0f);
        tilted.poly(shade(color, 1.2f), 5.4f, 4.0f, 7.0f, 4.0f, 7.0f, 8.8f, 5.4f, 8.8f);
        tilted.poly(shade(color, 1.2f), 12.2f, 4.0f, 13.8f, 4.0f, 13.8f, 8.8f, 12.2f, 8.8f);
        p.line(8.2f, 7.8f, 2.6f, 13.4f, 1.5f, shade(color, 0.8f));
        p.poly(shade(color, 0.7f), 8.6f, 13.4f, 15f, 13.4f, 15f, 15f, 8.6f, 15f);
    }

    private static void stall(Pen p, int color) {
        p.poly(color, 1.6f, 2.6f, 14.4f, 2.6f, 14.4f, 5.8f, 1.6f, 5.8f);
        // The awning's scallops.
        for (int i = 0; i < 4; i++) p.disc(3.2f + i * 3.2f, 5.8f, 1.6f, i % 2 == 0 ? color : shade(color, 0.75f));
        p.poly(shade(color, 0.7f), 2.8f, 7f, 4f, 7f, 4f, 14.6f, 2.8f, 14.6f);
        p.poly(shade(color, 0.7f), 12f, 7f, 13.2f, 7f, 13.2f, 14.6f, 12f, 14.6f);
        p.poly(color, 1.6f, 10.6f, 14.4f, 10.6f, 14.4f, 12.2f, 1.6f, 12.2f);
    }

    // ---- Small symbols --------------------------------------------------------------------------

    private static void coin(Pen p, int color) {
        p.radial(8f, 8f, 6.6f, shade(color, 1.25f), shade(color, 0.8f));
        p.ring(8f, 8f, 4.0f, 4.9f, shade(color, 0.7f));
        p.disc(6.4f, 6.2f, 1.1f, 0x55FFFFFF);
    }

    private static void clock(Pen p, int color) {
        p.ring(8f, 8f, 5.4f, 6.8f, color);
        p.line(8f, 8f, 8f, 4.4f, 1.3f, color);
        p.line(8f, 8f, 10.6f, 9.6f, 1.3f, color);
    }

    private static void magnifier(Pen p, int color) {
        p.ring(6.8f, 6.8f, 3.6f, 5.0f, color);
        p.line(10.4f, 10.4f, 14.2f, 14.2f, 2.0f, color);
    }

    private static void sortBars(Pen p, int color) {
        p.poly(color, 2f, 3f, 14f, 3f, 14f, 4.8f, 2f, 4.8f);
        p.poly(color, 2f, 7.1f, 10.5f, 7.1f, 10.5f, 8.9f, 2f, 8.9f);
        p.poly(color, 2f, 11.2f, 7f, 11.2f, 7f, 13f, 2f, 13f);
    }

    private static void gem(Pen p, int color) {
        p.poly(shade(color, 1.2f), 4f, 2.5f, 12f, 2.5f, 14.5f, 6f, 1.5f, 6f);
        p.poly(color, 1.5f, 6f, 14.5f, 6f, 8f, 14.5f);
    }

    private static void bolt(Pen p, int color) {
        p.poly(color, 9.6f, 1.2f, 3.4f, 9.2f, 8f, 9.2f, 10.4f, 6.4f);
        p.poly(color, 8f, 6.8f, 12.6f, 6.8f, 6.4f, 14.8f, 7.6f, 9.0f);
    }

    private static void clipboard(Pen p, int color) {
        p.poly(shade(color, 0.6f), 3f, 2.6f, 13f, 2.6f, 13f, 15f, 3f, 15f);
        p.poly(color, 5.6f, 1.2f, 10.4f, 1.2f, 10.4f, 3.8f, 5.6f, 3.8f);
        for (int i = 0; i < 3; i++) {
            float y = 6.4f + i * 2.8f;
            p.poly(color, 5f, y, 11f, y, 11f, y + 1.2f, 5f, y + 1.2f);
        }
    }

    private static void sellArrow(Pen p, int color) {
        p.poly(shade(color, 0.7f), 2f, 9.5f, 14f, 9.5f, 14f, 15f, 2f, 15f);
        p.line(8f, 11.5f, 8f, 4.5f, 2.0f, color);
        p.poly(color, 8f, 1.2f, 12f, 5.6f, 4f, 5.6f);
    }

    private static void sack(Pen p, int color) {
        p.disc(8f, 10.4f, 5.0f, color);
        p.poly(shade(color, 0.8f), 6f, 3f, 10f, 3f, 9.2f, 6.2f, 6.8f, 6.2f);
        p.line(5.6f, 5.6f, 10.4f, 5.6f, 1.2f, shade(color, 0.6f));
    }

    private static void backpack(Pen p, int color) {
        p.poly(color, 3f, 5f, 13f, 5f, 13f, 14.6f, 3f, 14.6f);
        p.ring(8f, 5f, 1.6f, 2.8f, color);
        p.poly(shade(color, 0.7f), 4.8f, 8.6f, 11.2f, 8.6f, 11.2f, 12.6f, 4.8f, 12.6f);
    }

    private static void grid(Pen p, int color) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float x = 2f + c * 4.4f;
                float y = 2f + r * 4.4f;
                p.poly(color, x, y, x + 3.4f, y, x + 3.4f, y + 3.4f, x, y + 3.4f);
            }
        }
    }

    private static void person(Pen p, int color) {
        p.disc(8f, 5.2f, 3.0f, color);
        p.sector(8f, 15.6f, 0f, 6.4f, (float) -Math.PI, 0f, color);
    }

    private static void arrowLeft(Pen p, int color) {
        p.line(13.5f, 8f, 4.5f, 8f, 1.8f, color);
        p.poly(color, 1.8f, 8f, 7.2f, 2.8f, 7.2f, 13.2f);
    }

    private static void cross(Pen p, int color) {
        p.line(3.5f, 3.5f, 12.5f, 12.5f, 1.8f, color);
        p.line(12.5f, 3.5f, 3.5f, 12.5f, 1.8f, color);
    }

    private static void chevron(Pen p, int color) {
        p.line(5f, 3.5f, 11f, 8f, 1.8f, color);
        p.line(11f, 8f, 5f, 12.5f, 1.8f, color);
    }

    private static void box(Pen p, int color) {
        p.poly(shade(color, 1.1f), 2f, 4f, 14f, 4f, 14f, 7f, 2f, 7f);
        p.poly(shade(color, 0.75f), 3f, 7f, 13f, 7f, 13f, 14.5f, 3f, 14.5f);
        p.poly(color, 6.5f, 8.5f, 9.5f, 8.5f, 9.5f, 10f, 6.5f, 10f);
    }

    // ---- Helpers --------------------------------------------------------------------------------

    /** The color with its red, green and blue scaled; above 1 it brightens. */
    public static int shade(int color, float factor) {
        int a = color >>> 24;
        int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** The color at a share of its alpha. */
    public static int fade(int color, float alpha) {
        int a = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Draws on the 16 by 16 grid into the mesh, scaled, moved and optionally turned about a point. */
    private static final class Pen {
        final WheelMesh mesh;
        final float x0;
        final float y0;
        final float s;
        final float cos;
        final float sin;
        final float pivotX;
        final float pivotY;

        Pen(WheelMesh mesh, float x0, float y0, float s, float angle) {
            this(mesh, x0, y0, s, angle, 8f, 8f);
        }

        Pen(WheelMesh mesh, float x0, float y0, float s, float angle, float pivotX, float pivotY) {
            this.mesh = mesh;
            this.x0 = x0;
            this.y0 = y0;
            this.s = s;
            this.cos = (float) Math.cos(angle);
            this.sin = (float) Math.sin(angle);
            this.pivotX = pivotX;
            this.pivotY = pivotY;
        }

        Pen rotated(float angle, float px, float py) {
            return new Pen(mesh, x0, y0, s, angle, px, py);
        }

        float x(float gx, float gy) {
            float dx = gx - pivotX;
            float dy = gy - pivotY;
            return x0 + (pivotX + dx * cos - dy * sin) * s;
        }

        float y(float gx, float gy) {
            float dx = gx - pivotX;
            float dy = gy - pivotY;
            return y0 + (pivotY + dx * sin + dy * cos) * s;
        }

        void poly(int color, float... points) {
            float[] out = new float[points.length];
            for (int i = 0; i < points.length; i += 2) {
                out[i] = x(points[i], points[i + 1]);
                out[i + 1] = y(points[i], points[i + 1]);
            }
            mesh.convexPolygon(out, color);
        }

        /** A straight stroke of the given width, as a feathered quad. */
        void line(float xa, float ya, float xb, float yb, float width, int color) {
            float dx = xb - xa;
            float dy = yb - ya;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len == 0) return;
            float nx = -dy / len * width / 2;
            float ny = dx / len * width / 2;
            poly(color, xa + nx, ya + ny, xb + nx, yb + ny, xb - nx, yb - ny, xa - nx, ya - ny);
        }

        void disc(float cx, float cy, float r, int color) {
            mesh.disc(x(cx, cy), y(cx, cy), r * s, color);
        }

        void radial(float cx, float cy, float r, int inner, int outer) {
            mesh.radialGradient(x(cx, cy), y(cx, cy), r * s, inner, outer);
        }

        void ring(float cx, float cy, float rIn, float rOut, int color) {
            if (rOut <= 0) return;
            mesh.ring(x(cx, cy), y(cx, cy), rIn * s, rOut * s, color);
        }

        void sector(float cx, float cy, float rIn, float rOut, float from, float to, int color) {
            float turn = (float) Math.atan2(sin, cos);
            mesh.ringSector(x(cx, cy), y(cx, cy), rIn * s, rOut * s, from + turn, to + turn, from + turn, to + turn,
                    color, color, false);
        }
    }
}
