package net.alpaka.addons.features.profile;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.market.MarketIcons;
import net.alpaka.addons.features.wheel.WheelMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The dashboard's graphs: one metric over the chosen range as a line with the area under it, its
 * scale on the right, the range's ends below and the exact value under the mouse. Drawn the way the
 * market's price history is, so the two read as the same kind of graph.
 */
public final class ProfileChart {
    private ProfileChart() {}

    /** How far back a graph goes. */
    public enum Range {
        WEEK("7D", 7), MONTH("30D", 30), QUARTER("90D", 90), YEAR("1Y", 365), ALL("All", 0);

        public final String chip;
        public final int days;

        Range(String chip, int days) {
            this.chip = chip;
            this.days = days;
        }

        public long fromMs(long now) {
            return days == 0 ? Long.MIN_VALUE : now - days * ProfileHistory.DAY;
        }
    }

    private static final int MAX_POINTS = 180;
    static final int UP = 0xFF5BE38A;
    static final int DOWN = 0xFFFF6B6B;

    /** The points inside the range, at most {@link #MAX_POINTS}, each the last of the ones it stands for. */
    static List<ProfileHistory.Point> visible(List<ProfileHistory.Point> points, Range range, long now) {
        long from = range.fromMs(now);
        List<ProfileHistory.Point> in = new ArrayList<>();
        for (ProfileHistory.Point p : points) if (p.timeMs() >= from) in.add(p);
        if (in.size() <= MAX_POINTS) return in;
        List<ProfileHistory.Point> out = new ArrayList<>(MAX_POINTS);
        double step = in.size() / (double) MAX_POINTS;
        for (int i = 0; i < MAX_POINTS; i++) {
            int last = Math.min(in.size() - 1, (int) Math.floor((i + 1) * step) - 1);
            out.add(in.get(Math.max(0, last)));
        }
        if (out.get(out.size() - 1) != in.get(in.size() - 1)) out.set(out.size() - 1, in.get(in.size() - 1));
        return out;
    }

    /** The change over the points: the last minus the first; NaN with fewer than two. */
    static double change(List<ProfileHistory.Point> points) {
        if (points.size() < 2) return Double.NaN;
        return points.get(points.size() - 1).value() - points.get(0).value();
    }

    /**
     * The graph of one metric. {@code maxLevel} is for the hover's level note of a skill; 0 for
     * anything else. The box with the hovered value goes to {@code overlay}, to be drawn last.
     * Returns false when there was too little history to draw, after saying so.
     */
    public static boolean draw(GuiGraphicsExtractor graphics, Font font, List<ProfileHistory.Point> all, String metric,
                               int maxLevel, Range range, int color, int x, int y, int w, int h, int mx, int my,
                               java.util.function.Consumer<Runnable> overlay, boolean own) {
        long now = System.currentTimeMillis();
        List<ProfileHistory.Point> points = visible(all, range, now);
        if (points.size() < 2) {
            String text = all.size() >= 2 ? "Nothing recorded in this range. Pick a longer one."
                    : own ? "No history yet. Alpaka records your profile every hour while you play SkyBlock, so the graph fills in over the next days."
                    : "No history yet. Alpaka records this profile each time you look at it.";
            message(graphics, font, text, x, y, w, h);
            return false;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ProfileHistory.Point p : points) {
            min = Math.min(min, p.value());
            max = Math.max(max, p.value());
        }
        if (max - min < Math.max(1e-6, Math.abs(max) * 0.002)) {
            double pad = Math.max(1, Math.abs(max) * 0.01);
            min -= pad;
            max += pad;
        }
        double span = max - min;
        min -= span * 0.08;
        max += span * 0.08;
        if (min < 0 && points.get(0).value() >= 0) min = Math.max(min, 0);

        String maxLabel = Metrics.format(metric, max);
        String minLabel = Metrics.format(metric, min);
        int labelW = Math.max(GuiFont.width(font, maxLabel), GuiFont.width(font, minLabel)) + 6;
        int px = x + 4;
        int pw = w - 8 - labelW;
        int py = y + 4;
        int ph = h - 4 - 12;
        if (pw < 30 || ph < 16) return true;

        for (int i = 0; i <= 3; i++) {
            ModernGuiUtils.drawRect(graphics, px, py + Math.round(ph * i / 3f), pw, 1, AlpakaStyle.LINE);
        }
        graphics.text(font, GuiFont.text(maxLabel), px + pw + 5, py + 2, AlpakaStyle.TEXT_MUTED, false);
        graphics.text(font, GuiFont.text(minLabel), px + pw + 5, py + ph - 10, AlpakaStyle.TEXT_MUTED, false);

        long t0 = range == Range.ALL ? points.get(0).timeMs() : range.fromMs(now);
        long t1 = now;
        if (points.get(0).timeMs() < t0) t0 = points.get(0).timeMs();
        if (t1 <= t0) t1 = t0 + 1;
        DateTimeFormatter edge = DateTimeFormatter.ofPattern(t1 - t0 > 200 * ProfileHistory.DAY ? "MMM yyyy" : "MMM d", Locale.US);
        String from = edge.format(Instant.ofEpochMilli(t0).atZone(ZoneId.systemDefault()));
        String to = "Now";
        graphics.text(font, GuiFont.text(from), px, py + ph + 3, AlpakaStyle.TEXT_MUTED, false);
        graphics.text(font, GuiFont.text(to), px + pw - GuiFont.width(font, to), py + ph + 3, AlpakaStyle.TEXT_MUTED, false);

        WheelMesh mesh = new WheelMesh(1.0f / guiScale());
        int fill = MarketIcons.fade(color, 0.16f);
        float bottom = py + ph;
        float prevX = Float.NaN;
        float prevY = Float.NaN;
        for (ProfileHistory.Point p : points) {
            float cx = xOf(p.timeMs(), t0, t1, px, pw);
            float cy = yOf(p.value(), min, max, py, ph);
            if (!Float.isNaN(prevX)) mesh.quad(prevX, prevY, fill, prevX, bottom, fill, cx, bottom, fill, cx, cy, fill);
            prevX = cx;
            prevY = cy;
        }
        prevX = Float.NaN;
        for (ProfileHistory.Point p : points) {
            float cx = xOf(p.timeMs(), t0, t1, px, pw);
            float cy = yOf(p.value(), min, max, py, ph);
            if (!Float.isNaN(prevX)) line(mesh, prevX, prevY, cx, cy, 1.4f, color);
            prevX = cx;
            prevY = cy;
        }
        // Where the history stops short of now, a dotted run to the right edge: nothing was
        // recorded since, which is not the same as nothing having changed.
        if (prevX < px + pw - 4) {
            for (float dx = prevX + 3; dx < px + pw; dx += 4) mesh.disc(dx, prevY, 0.6f, MarketIcons.fade(color, 0.6f));
        }
        mesh.submit(graphics);

        if (AlpakaStyle.inside(mx, my, px, py - 2, pw, ph + 4)) {
            long at = t0 + (long) ((mx - px) / (double) pw * (t1 - t0));
            int index = nearest(points, at);
            ProfileHistory.Point p = points.get(index);
            float hx = xOf(p.timeMs(), t0, t1, px, pw);
            ModernGuiUtils.drawRect(graphics, Math.round(hx), py, 1, ph, AlpakaStyle.DIVIDER);
            WheelMesh dot = new WheelMesh(1.0f / guiScale());
            dot.disc(hx, yOf(p.value(), min, max, py, ph), 2.4f, color);
            dot.submit(graphics);
            // Drawn after everything else on the page, so a card beside the graph doesn't cover it.
            ProfileHistory.Point first = points.get(0);
            overlay.accept(() -> hoverBox(graphics, font, metric, maxLevel, p, first, color, hx, px, py, pw));
        }
        return true;
    }

    /** A small line without labels, for a card. */
    public static void sparkline(GuiGraphicsExtractor graphics, List<ProfileHistory.Point> all, Range range, int color,
                                 int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        List<ProfileHistory.Point> points = visible(all, range, now);
        if (points.size() < 2) {
            ModernGuiUtils.drawRect(graphics, x, y + h - 1, w, 1, AlpakaStyle.LINE);
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ProfileHistory.Point p : points) {
            min = Math.min(min, p.value());
            max = Math.max(max, p.value());
        }
        if (max - min < Math.max(1e-9, Math.abs(max) * 1e-4)) {
            min -= 1;
            max += 1;
        }
        long t0 = points.get(0).timeMs();
        long t1 = Math.max(t0 + 1, points.get(points.size() - 1).timeMs());
        WheelMesh mesh = new WheelMesh(1.0f / guiScale());
        int fill = MarketIcons.fade(color, 0.14f);
        float prevX = Float.NaN;
        float prevY = Float.NaN;
        for (ProfileHistory.Point p : points) {
            float cx = xOf(p.timeMs(), t0, t1, x, w);
            float cy = yOf(p.value(), min, max, y, h);
            if (!Float.isNaN(prevX)) {
                mesh.quad(prevX, prevY, fill, prevX, y + h, fill, cx, y + h, fill, cx, cy, fill);
                line(mesh, prevX, prevY, cx, cy, 1.1f, color);
            }
            prevX = cx;
            prevY = cy;
        }
        mesh.submit(graphics);
    }

    private static void hoverBox(GuiGraphicsExtractor graphics, Font font, String metric, int maxLevel, ProfileHistory.Point p,
                                 ProfileHistory.Point first, int color, float hx, int px, int py, int pw) {
        String when = DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm", Locale.US)
                .format(Instant.ofEpochMilli(p.timeMs()).atZone(ZoneId.systemDefault()));
        List<String> lines = new ArrayList<>();
        lines.add(Metrics.kind(metric) == Metrics.Kind.DECIMAL
                ? String.format(Locale.US, "%.2f", p.value()) : Coins.full(Math.round(p.value())));
        String level = Metrics.levelNote(metric, p.value(), maxLevel);
        if (level != null) lines.add(level);
        double change = p.value() - first.value();
        boolean showChange = p != first && change != 0;
        int bw = GuiFont.width(font, when);
        for (String line : lines) bw = Math.max(bw, GuiFont.width(font, line) + 8);
        String changeText = showChange ? Metrics.delta(metric, change) + " in range" : null;
        if (changeText != null) bw = Math.max(bw, GuiFont.width(font, changeText));
        bw += 12;
        int bh = 6 + 10 * (1 + lines.size() + (changeText != null ? 1 : 0));
        int bx = Math.round(hx) + 6;
        if (bx + bw > px + pw) bx = Math.round(hx) - 6 - bw;
        bx = Math.max(px, bx);
        int by = py + 2;
        graphics.nextStratum();
        ModernGuiUtils.drawRoundedRect(graphics, bx, by, bw, bh, AlpakaStyle.ROW_RADIUS, (AlpakaStyle.PANEL & 0x00FFFFFF) | 0xF0000000);
        ModernGuiUtils.drawRoundedOutline(graphics, bx, by, bw, bh, AlpakaStyle.ROW_RADIUS, AlpakaStyle.DIVIDER);
        graphics.text(font, GuiFont.text(when), bx + 6, by + 4, AlpakaStyle.TEXT_MUTED, false);
        int ly = by + 14;
        WheelMesh dot = new WheelMesh(1.0f / guiScale());
        dot.disc(bx + 8, ly + 4, 2.2f, color);
        dot.submit(graphics);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, GuiFont.text(lines.get(i)), bx + 14, ly, i == 0 ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
            ly += 10;
        }
        if (changeText != null) {
            graphics.text(font, GuiFont.text(changeText), bx + 6, ly, change > 0 ? ink(UP) : ink(DOWN), false);
        }
    }

    static void message(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int w, int h) {
        List<net.minecraft.network.chat.FormattedText> lines = GuiFont.splitLines(font, text, Math.max(40, w - 30));
        int ly = y + (h - lines.size() * 10) / 2;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            int lw = font.width(line);
            graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(line), x + (w - lw) / 2, ly,
                    AlpakaStyle.TEXT_MUTED, false);
            ly += 10;
        }
    }

    static int ink(int color) {
        return net.alpaka.addons.features.market.MarketInk.ink(color);
    }

    private static float xOf(long t, long t0, long t1, int px, int pw) {
        return px + (float) ((t - t0) / (double) (t1 - t0) * pw);
    }

    private static float yOf(double v, double min, double max, int py, int ph) {
        float y = py + (float) ((max - v) / (max - min) * ph);
        return Math.max(py, Math.min(py + ph, y));
    }

    static void line(WheelMesh mesh, float xa, float ya, float xb, float yb, float width, int color) {
        float dx = xb - xa;
        float dy = yb - ya;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        float nx = -dy / len * width / 2;
        float ny = dx / len * width / 2;
        mesh.convexPolygon(new float[] {xa + nx, ya + ny, xb + nx, yb + ny, xb - nx, yb - ny, xa - nx, ya - ny}, color);
    }

    private static int nearest(List<ProfileHistory.Point> points, long time) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            long d = Math.abs(points.get(i).timeMs() - time);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? 2 : (int) Math.max(1, mc.getWindow().getGuiScale());
    }
}
