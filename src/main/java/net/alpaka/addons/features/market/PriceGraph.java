package net.alpaka.addons.features.market;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
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
 * The price history panel on a product's or an auction's page: two lines over the chosen range -
 * the Bazaar's buy and sell price, or the Auction House's average and lowest sale - with the latest
 * values and the change over the range above them, the range to pick in the corner and the exact
 * figures under the mouse.
 *
 * The data is SkyCofl's ({@link PriceHistory}); the panel names them as the source and links to the
 * item's page on their site, as their API asks.
 */
public final class PriceGraph {
    private PriceGraph() {}

    /** Where the panel's controls are, for the screen to click. */
    public interface Clicks {
        void add(int x, int y, int w, int h, Runnable action, String tip);
    }

    private static final int BUY = 0xFFFFB347;
    private static final int SELL = 0xFF5CE1F0;
    private static final int LOWEST = 0xFF5BE38A;
    private static final int UP = 0xFF5BE38A;
    private static final int DOWN = 0xFFFF6B6B;
    private static final int CHIP_H = 12;
    /**
     * At most this many points per line; longer histories are averaged down. More than the chart is
     * wide would only overdraw, and the Auction House's few sales an hour make a jagged line that
     * reads better a little smoothed.
     */
    private static final int MAX_POINTS = 160;
    private static final int MAX_AUCTION_POINTS = 48;

    /**
     * Whether the panel is shown: switched on in the config. Without Network Features it stays, and
     * says how to turn them on rather than leaving a gap.
     */
    public static boolean enabled() {
        return AlpakaConfig.instance.marketPriceHistoryEnabled;
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, MarketKind kind, ItemTags.Tag tag,
                            int x, int y, int w, int h, int mx, int my, Clicks clicks) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS + 1, AlpakaStyle.ROW);
        ModernGuiUtils.drawRect(graphics, x + AlpakaStyle.CARD_RADIUS + 1, y, w - 2 * (AlpakaStyle.CARD_RADIUS + 1), 1, 0x14FFFFFF);

        // Header: the title, the source, and the range to choose.
        PriceHistory.Range range = PriceHistory.Range.of(AlpakaConfig.instance.marketPriceHistoryRange);
        graphics.text(font, GuiFont.text("Price History"), x + 8, y + 7, AlpakaStyle.TEXT, false);
        int chipsRight = x + w - 6;
        PriceHistory.Range[] ranges = PriceHistory.Range.values();
        for (int i = ranges.length - 1; i >= 0; i--) {
            PriceHistory.Range r = ranges[i];
            int cw = GuiFont.width(font, r.chip) + 10;
            int cx = chipsRight - cw;
            boolean selected = r == range;
            boolean hover = AlpakaStyle.inside(mx, my, cx, y + 5, cw, CHIP_H);
            int bg = selected ? MarketIcons.fade(AlpakaStyle.accent(), hover ? 0.35f : 0.24f) : hover ? AlpakaStyle.ROW_SELECTED : 0;
            if (bg != 0) ModernGuiUtils.drawRoundedRect(graphics, cx, y + 5, cw, CHIP_H, CHIP_H / 2, bg);
            int color = selected ? AlpakaStyle.accent() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED;
            graphics.text(font, GuiFont.text(r.chip), cx + 5, y + 7, color, false);
            clicks.add(cx, y + 5, cw, CHIP_H, () -> {
                AlpakaConfig.instance.marketPriceHistoryRange = r.ordinal();
                AlpakaConfig.save();
            }, r == PriceHistory.Range.ALL ? "Show the whole history" : "Show the last " + r.label.toLowerCase(Locale.ROOT));
            chipsRight = cx - 2;
        }

        int bodyY = y + 22;
        int bodyH = h - 22 - 6;
        if (!AlpakaConfig.instance.allowApiCalls) {
            message(graphics, font, "Turn on Network Features (General › Main) to see prices from SkyCofl.", x, bodyY, w, bodyH);
            return;
        }
        if (tag.tag() == null) {
            if (tag.lookup() == ItemTags.Lookup.LOADING) loading(graphics, x, bodyY, w, bodyH);
            else message(graphics, font, "SkyCofl has no price history for this item.", x, bodyY, w, bodyH);
            return;
        }

        PriceHistory.Result result = PriceHistory.get(kind, tag.tag(), range);
        if (result.points().isEmpty()) {
            switch (result.state()) {
                case LOADING -> loading(graphics, x, bodyY, w, bodyH);
                case EMPTY -> message(graphics, font, "No trades in this time.", x, bodyY, w, bodyH);
                default -> {
                    message(graphics, font, "Couldn't reach SkyCofl. Click to try again.", x, bodyY, w, bodyH);
                    clicks.add(x, bodyY, w, bodyH, () -> PriceHistory.retry(kind, tag.tag(), range), null);
                }
            }
            return;
        }
        int max = kind == MarketKind.BAZAAR ? MAX_POINTS : MAX_AUCTION_POINTS;
        chart(graphics, font, kind, tag.tag(), range, downsample(result.points(), max), x, bodyY, w, bodyH, mx, my, clicks);
    }

    /** Opens the item's page on SkyCofl's site, after asking, as every link out of the game does. */
    private static void openSource(String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new net.alpaka.addons.client.AlpakaConfirmScreen(mc.gui.screen(), "Open SkyCofl",
                "Opens this item's price page on SkyCofl in your browser: " + url, "Open", false,
                () -> net.minecraft.util.Util.getPlatform().openUri(url)));
    }

    private static void message(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int w, int h) {
        List<net.minecraft.network.chat.FormattedText> lines = GuiFont.splitLines(font, text, w - 24);
        int ly = y + (h - lines.size() * 10) / 2;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            int lw = font.width(line);
            graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(line), x + (w - lw) / 2, ly,
                    AlpakaStyle.TEXT_MUTED, false);
            ly += 10;
        }
    }

    /** A soft wave where the line will be, while SkyCofl answers. */
    private static void loading(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        WheelMesh mesh = new WheelMesh(1.0f / guiScale());
        int left = x + 10;
        int right = x + w - 10;
        float mid = y + h * 0.55f;
        float prevX = left;
        float prevY = mid;
        for (int i = 1; i <= 40; i++) {
            float px = left + (right - left) * i / 40f;
            float py = mid + (float) Math.sin(i * 0.45 - now / 260.0) * h * 0.12f;
            float alpha = 0.25f + 0.2f * (float) Math.sin(now / 300.0 - i * 0.2);
            line(mesh, prevX, prevY, px, py, 1.2f, MarketIcons.fade(AlpakaStyle.DIVIDER, Math.max(0.1f, alpha) * 2.5f));
            prevX = px;
            prevY = py;
        }
        mesh.submit(graphics);
    }

    private static void chart(GuiGraphicsExtractor graphics, Font font, MarketKind kind, String tag, PriceHistory.Range range,
                              List<PriceHistory.Point> points, int x, int y, int w, int h, int mx, int my, Clicks clicks) {
        boolean bazaar = kind == MarketKind.BAZAAR;
        int firstColor = BUY;
        int secondColor = bazaar ? SELL : LOWEST;
        String firstName = bazaar ? "Buy" : "Average";
        String secondName = bazaar ? "Sell" : "Lowest";

        // Legend: the latest of each line, and how the first moved over the range.
        double lastFirst = last(points, true);
        double lastSecond = last(points, false);
        int lx = x + 8;
        lx = legend(graphics, font, lx, y + 1, firstColor, firstName, lastFirst);
        lx = legend(graphics, font, lx + 8, y + 1, secondColor, secondName, lastSecond);
        double[] ends = trend(points);
        if (ends != null) {
            double change = (ends[1] - ends[0]) / ends[0] * 100.0;
            String text = String.format(Locale.ROOT, "%s%.1f%%", change >= 0 ? "+" : "", change);
            int tw = GuiFont.width(font, text);
            if (lx + 8 < x + w - 8 - tw) {
                graphics.text(font, GuiFont.text(text), x + w - 8 - tw, y + 1, change >= 0 ? UP : DOWN, false);
            }
        }

        // The plot, with its scale on the right and the range's ends below.
        double[] scale = scale(points);
        if (scale == null) return;
        double min = scale[0];
        double max = scale[1];
        if (max - min < max * 0.002) {
            double pad = Math.max(1, max * 0.01);
            min -= pad;
            max += pad;
        }
        double span = max - min;
        min -= span * 0.08;
        max += span * 0.08;
        if (min < 0) min = 0;

        String maxLabel = Coins.compact(max);
        String minLabel = Coins.compact(min);
        int labelW = Math.max(GuiFont.width(font, maxLabel), GuiFont.width(font, minLabel)) + 6;
        int px = x + 8;
        int pw = w - 16 - labelW;
        int py = y + 13;
        int ph = h - 13 - 11;
        if (pw < 20 || ph < 16) return;

        for (int i = 0; i <= 2; i++) {
            int gy = py + Math.round(ph * i / 2f);
            ModernGuiUtils.drawRect(graphics, px, gy, pw, 1, AlpakaStyle.LINE);
        }
        // Just inside the top and bottom lines, clear of the change above the plot.
        graphics.text(font, GuiFont.text(maxLabel), px + pw + 5, py + 2, AlpakaStyle.TEXT_MUTED, false);
        graphics.text(font, GuiFont.text(minLabel), px + pw + 5, py + ph - 10, AlpakaStyle.TEXT_MUTED, false);

        long t0 = points.get(0).timeMs();
        long t1 = points.get(points.size() - 1).timeMs();
        if (t1 <= t0) t1 = t0 + 1;
        DateTimeFormatter edge = edgeFormat(range);
        String from = edge.format(Instant.ofEpochMilli(t0).atZone(ZoneId.systemDefault()));
        String to = edge.format(Instant.ofEpochMilli(t1).atZone(ZoneId.systemDefault()));
        graphics.text(font, GuiFont.text(from), px, py + ph + 3, AlpakaStyle.TEXT_MUTED, false);
        graphics.text(font, GuiFont.text(to), px + pw - GuiFont.width(font, to), py + ph + 3, AlpakaStyle.TEXT_MUTED, false);

        // The source between the two dates, as SkyCofl asks, linking to the item's page there. Its
        // full wording where there is room for it, the name alone where there is not.
        int room = pw - GuiFont.width(font, from) - GuiFont.width(font, to) - 16;
        String source = GuiFont.width(font, "Prices provided by SkyCofl") <= room ? "Prices provided by SkyCofl" : "SkyCofl";
        int sw = GuiFont.width(font, source);
        int sx = px + (pw - sw) / 2;
        boolean hoverSource = AlpakaStyle.inside(mx, my, sx - 2, py + ph + 1, sw + 4, 11);
        graphics.text(font, GuiFont.text(source), sx, py + ph + 3, hoverSource ? AlpakaStyle.accent() : AlpakaStyle.DIVIDER, false);
        String page = PriceHistory.pageUrl(tag);
        clicks.add(sx - 2, py + ph + 1, sw + 4, 11, () -> openSource(page),
                "Prices provided by SkyCofl. Click to open " + page.replace("https://", "") + ".");

        WheelMesh mesh = new WheelMesh(1.0f / guiScale());
        // The fill first, under both lines; then the second line, then the first on top.
        series(mesh, points, true, firstColor, true, t0, t1, min, max, px, py, pw, ph);
        series(mesh, points, false, secondColor, false, t0, t1, min, max, px, py, pw, ph);
        series(mesh, points, true, firstColor, false, t0, t1, min, max, px, py, pw, ph);
        mesh.submit(graphics);

        // Under the mouse: the moment nearest it, its figures in a small box.
        if (AlpakaStyle.inside(mx, my, px, py - 2, pw, ph + 4)) {
            int index = nearest(points, t0 + (long) ((mx - px) / (double) pw * (t1 - t0)));
            PriceHistory.Point p = points.get(index);
            float hx = px + (float) ((p.timeMs() - t0) / (double) (t1 - t0) * pw);
            ModernGuiUtils.drawRect(graphics, Math.round(hx), py, 1, ph, AlpakaStyle.DIVIDER);
            WheelMesh dots = new WheelMesh(1.0f / guiScale());
            if (!Double.isNaN(p.second())) dots.disc(hx, yOf(p.second(), min, max, py, ph), 2.2f, secondColor);
            if (!Double.isNaN(p.first())) dots.disc(hx, yOf(p.first(), min, max, py, ph), 2.2f, firstColor);
            dots.submit(graphics);
            hoverBox(graphics, font, range, p, firstName, secondName, firstColor, secondColor, hx, px, py, pw);
        }
    }

    private static int legend(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, String name, double value) {
        WheelMesh dot = new WheelMesh(1.0f / guiScale());
        dot.disc(x + 2.5f, y + 4f, 2.5f, color);
        dot.submit(graphics);
        graphics.text(font, GuiFont.text(name), x + 8, y, AlpakaStyle.TEXT_MUTED, false);
        int vx = x + 8 + GuiFont.width(font, name) + 4;
        String v = Double.isNaN(value) ? "—" : Coins.compact(value);
        graphics.text(font, GuiFont.text(v), vx, y, color, false);
        return vx + GuiFont.width(font, v);
    }

    /**
     * One line, or with {@code area} the even fill under it, in its color down to the bottom of the
     * plot: one flat color reads as the area it is, where a fade looked like a shadow.
     */
    private static void series(WheelMesh mesh, List<PriceHistory.Point> points, boolean first, int color, boolean area,
                               long t0, long t1, double min, double max, int px, int py, int pw, int ph) {
        float bottom = py + ph;
        float prevX = Float.NaN;
        float prevY = Float.NaN;
        int fill = MarketIcons.fade(color, 0.18f);
        for (PriceHistory.Point p : points) {
            double v = first ? p.first() : p.second();
            if (Double.isNaN(v)) {
                prevX = Float.NaN;
                continue;
            }
            float x = px + (float) ((p.timeMs() - t0) / (double) (t1 - t0) * pw);
            float y = yOf(v, min, max, py, ph);
            if (!Float.isNaN(prevX)) {
                if (area) mesh.quad(prevX, prevY, fill, prevX, bottom, fill, x, bottom, fill, x, y, fill);
                else line(mesh, prevX, prevY, x, y, first ? 1.4f : 1.1f, color);
            }
            prevX = x;
            prevY = y;
        }
    }

    /** Where a price sits in the plot; one beyond the scale stays on its edge. */
    private static float yOf(double v, double min, double max, int py, int ph) {
        float y = py + (float) ((max - v) / (max - min) * ph);
        return Math.max(py, Math.min(py + ph, y));
    }

    /**
     * The prices the plot spans, padded a little. Set by where nearly all of the history lies rather
     * than by its extremes: one wild sale - a billion for an item that goes for half that, or a coin
     * for one listed by mistake - would otherwise squash everything else into a flat line. Such a
     * sale still shows, pinned to the edge.
     */
    static double[] scale(List<PriceHistory.Point> points) {
        List<Double> values = new ArrayList<>();
        for (PriceHistory.Point p : points) {
            if (!Double.isNaN(p.first())) values.add(p.first());
            if (!Double.isNaN(p.second())) values.add(p.second());
        }
        if (values.isEmpty()) return null;
        values.sort(Double::compare);
        int n = values.size();
        double min = n >= 20 ? values.get(n * 2 / 100) : values.get(0);
        double max = n >= 20 ? values.get(n - 1 - n * 2 / 100) : values.get(n - 1);
        if (max - min < max * 0.002) {
            double pad = Math.max(1, max * 0.01);
            min -= pad;
            max += pad;
        }
        double span = max - min;
        min = Math.max(0, min - span * 0.08);
        max += span * 0.08;
        return new double[] {min, max};
    }

    /**
     * Where the first line started and ended over the range, each the average of a stretch at its
     * end, so a single sale at either end does not decide the change shown. Null without enough.
     */
    static double[] trend(List<PriceHistory.Point> points) {
        List<Double> values = new ArrayList<>();
        for (PriceHistory.Point p : points) if (!Double.isNaN(p.first())) values.add(p.first());
        if (values.size() < 2) return null;
        int window = Math.max(1, values.size() * 15 / 100);
        double start = 0;
        double end = 0;
        for (int i = 0; i < window; i++) {
            start += values.get(i);
            end += values.get(values.size() - 1 - i);
        }
        start /= window;
        end /= window;
        return start > 0 ? new double[] {start, end} : null;
    }

    private static void line(WheelMesh mesh, float xa, float ya, float xb, float yb, float width, int color) {
        float dx = xb - xa;
        float dy = yb - ya;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        float nx = -dy / len * width / 2;
        float ny = dx / len * width / 2;
        mesh.convexPolygon(new float[] {xa + nx, ya + ny, xb + nx, yb + ny, xb - nx, yb - ny, xa - nx, ya - ny}, color);
    }

    private static void hoverBox(GuiGraphicsExtractor graphics, Font font, PriceHistory.Range range, PriceHistory.Point p,
                                 String firstName, String secondName, int firstColor, int secondColor,
                                 float hx, int px, int py, int pw) {
        String when = hoverFormat(range).format(Instant.ofEpochMilli(p.timeMs()).atZone(ZoneId.systemDefault()));
        String a = firstName + "  " + (Double.isNaN(p.first()) ? "—" : Coins.full(p.first()));
        String b = secondName + "  " + (Double.isNaN(p.second()) ? "—" : Coins.full(p.second()));
        int bw = Math.max(GuiFont.width(font, when), Math.max(GuiFont.width(font, a), GuiFont.width(font, b))) + 16;
        int bh = 36;
        int bx = Math.round(hx) + 6;
        if (bx + bw > px + pw) bx = Math.round(hx) - 6 - bw;
        bx = Math.max(px, bx);
        int by = py + 2;
        ModernGuiUtils.drawRoundedRect(graphics, bx, by, bw, bh, AlpakaStyle.ROW_RADIUS, 0xF0181818);
        ModernGuiUtils.drawRoundedOutline(graphics, bx, by, bw, bh, AlpakaStyle.ROW_RADIUS, AlpakaStyle.DIVIDER);
        graphics.text(font, GuiFont.text(when), bx + 6, by + 4, AlpakaStyle.TEXT_SOFT, false);
        WheelMesh dots = new WheelMesh(1.0f / guiScale());
        dots.disc(bx + 8, by + 18, 2.2f, firstColor);
        dots.disc(bx + 8, by + 28, 2.2f, secondColor);
        dots.submit(graphics);
        graphics.text(font, GuiFont.text(a), bx + 13, by + 14, AlpakaStyle.TEXT, false);
        graphics.text(font, GuiFont.text(b), bx + 13, by + 24, AlpakaStyle.TEXT, false);
    }

    private static DateTimeFormatter edgeFormat(PriceHistory.Range range) {
        return DateTimeFormatter.ofPattern(switch (range) {
            case DAY -> "HH:mm";
            case WEEK, MONTH -> "MMM d";
            case ALL -> "MMM yyyy";
        }, Locale.US);
    }

    private static DateTimeFormatter hoverFormat(PriceHistory.Range range) {
        return DateTimeFormatter.ofPattern(switch (range) {
            case DAY, WEEK -> "MMM d, HH:mm";
            case MONTH, ALL -> "MMM d, yyyy";
        }, Locale.US);
    }

    private static double last(List<PriceHistory.Point> points, boolean first) {
        for (int i = points.size() - 1; i >= 0; i--) {
            double v = first ? points.get(i).first() : points.get(i).second();
            if (!Double.isNaN(v)) return v;
        }
        return Double.NaN;
    }

    private static int nearest(List<PriceHistory.Point> points, long time) {
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

    /** At most {@code max} points, each an average of the ones it stands for. */
    static List<PriceHistory.Point> downsample(List<PriceHistory.Point> points, int max) {
        if (points.size() <= max) return points;
        List<PriceHistory.Point> out = new ArrayList<>(max);
        double step = points.size() / (double) max;
        for (int i = 0; i < max; i++) {
            int from = (int) Math.floor(i * step);
            int to = Math.min(points.size(), (int) Math.floor((i + 1) * step));
            double a = 0;
            double b = 0;
            int na = 0;
            int nb = 0;
            for (int j = from; j < to; j++) {
                PriceHistory.Point p = points.get(j);
                if (!Double.isNaN(p.first())) {
                    a += p.first();
                    na++;
                }
                if (!Double.isNaN(p.second())) {
                    b += p.second();
                    nb++;
                }
            }
            long time = points.get((from + to - 1) / 2).timeMs();
            out.add(new PriceHistory.Point(time, na == 0 ? Double.NaN : a / na, nb == 0 ? Double.NaN : b / nb));
        }
        return out;
    }

    private static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? 2 : (int) Math.max(1, mc.getWindow().getGuiScale());
    }
}
