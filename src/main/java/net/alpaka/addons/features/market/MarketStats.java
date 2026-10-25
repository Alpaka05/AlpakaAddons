package net.alpaka.addons.features.market;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The panel under the price history on a product's or an auction's page.
 *
 * For a Bazaar product, its order book: the best buy orders and sell offers, which Hypixel lists in
 * the lore of Create Buy Order and Create Sell Offer. For an auction, what SkyCofl knows about the
 * item on the Auction House: the lowest BIN, how often it sells, its median and average price, how
 * this auction's price compares, and the hour it sells most.
 *
 * Its colors go through {@link MarketInk}, so they stay readable on a light Menu Theme.
 */
public final class MarketStats {
    private MarketStats() {}

    private static final int COIN = 0xFFFFAA00;
    private static final int BUY = 0xFF5BE38A;
    private static final int SELL = 0xFFFFB347;
    private static final int RED = 0xFFFF6B6B;
    private static final int GAP = 4;

    private static final int HEADER_H = 22;
    private static final int BOOK_TOP = 34;
    private static final int BOOK_ROW_H = 11;
    private static final int BOOK_ROWS = 7;
    private static final int TILE_H = 34;

    /** The panel heights: with one row of orders or tiles at least, with all of them at most. */
    public static int bookMinHeight() {
        return BOOK_TOP + 2 * BOOK_ROW_H + 4;
    }

    public static int bookMaxHeight() {
        return BOOK_TOP + BOOK_ROWS * BOOK_ROW_H + 4;
    }

    public static int statsMinHeight() {
        return HEADER_H + TILE_H + 6;
    }

    public static int statsMaxHeight() {
        return HEADER_H + 2 * TILE_H + GAP + 6;
    }

    // ---- The Bazaar's order book ----------------------------------------------------------------

    /** One price in the order book: what is ordered or offered at it, by how many orders. */
    public record Level(double price, long amount, int orders) {}

    private static final Pattern LEVEL = Pattern.compile(
            "^-\\s*([\\d,]+(?:\\.\\d+)?) coins? each \\| ([\\d,]+)x(?: (?:from|in) ([\\d,]+) (?:orders?|offers?))?",
            Pattern.CASE_INSENSITIVE);

    /**
     * The levels under "Top Orders:" or "Top Offers:" ("Only Order:" for one) in a Create Buy Order or Create Sell Offer
     * button's lore, best first; empty when there are none.
     */
    public static List<Level> levels(List<String> lore) {
        List<Level> out = new ArrayList<>();
        boolean in = false;
        for (String raw : lore) {
            String line = raw.trim();
            // With a single order Hypixel writes "Only Order:" instead.
            if (line.equalsIgnoreCase("Top Orders:") || line.equalsIgnoreCase("Top Offers:")
                    || line.equalsIgnoreCase("Only Order:") || line.equalsIgnoreCase("Only Offer:")) {
                in = true;
                continue;
            }
            if (!in) continue;
            Matcher m = LEVEL.matcher(line);
            if (!m.find()) break;
            Double price = Coins.parseLore(m.group(1));
            if (price == null) continue;
            long amount = Long.parseLong(m.group(2).replace(",", ""));
            int orders = m.group(3) == null ? 0 : Integer.parseInt(m.group(3).replace(",", ""));
            out.add(new Level(price, amount, orders));
        }
        return out;
    }

    /** How far apart the best offer and the best order are, in percent of the offer. NaN without both. */
    static double spread(List<Level> orders, List<Level> offers) {
        if (orders.isEmpty() || offers.isEmpty() || offers.get(0).price() <= 0) return Double.NaN;
        return (offers.get(0).price() - orders.get(0).price()) / offers.get(0).price() * 100.0;
    }

    public static void drawOrderBook(GuiGraphicsExtractor graphics, Font font, List<Level> orders, List<Level> offers,
                                     int x, int y, int w, int h) {
        panel(graphics, x, y, w, h);
        graphics.text(font, GuiFont.text("Order Book"), x + 8, y + 7, AlpakaStyle.TEXT, false);
        double spread = spread(orders, offers);
        if (!Double.isNaN(spread)) {
            String text = String.format(Locale.ROOT, "Spread %.1f%%", spread);
            graphics.text(font, GuiFont.text(text), x + w - 8 - GuiFont.width(font, text), y + 7, AlpakaStyle.TEXT_MUTED, false);
        }

        int colW = (w - 16 - 8) / 2;
        int rows = Math.max(1, Math.min(BOOK_ROWS, (h - BOOK_TOP - 4) / BOOK_ROW_H));
        long most = 1;
        for (int i = 0; i < Math.min(rows, orders.size()); i++) most = Math.max(most, orders.get(i).amount());
        for (int i = 0; i < Math.min(rows, offers.size()); i++) most = Math.max(most, offers.get(i).amount());
        column(graphics, font, "Buy Orders", "No buy orders", MarketInk.ink(BUY), orders, x + 8, y, colW, rows, most);
        column(graphics, font, "Sell Offers", "No sell offers", MarketInk.ink(SELL), offers, x + 8 + colW + 8, y, colW, rows, most);
    }

    private static void column(GuiGraphicsExtractor graphics, Font font, String title, String none, int color, List<Level> levels,
                               int x, int y, int w, int rows, long most) {
        graphics.text(font, GuiFont.text(title), x, y + 22, color, false);
        if (levels.isEmpty()) {
            graphics.text(font, GuiFont.text(none), x, y + BOOK_TOP + 1, AlpakaStyle.TEXT_MUTED, false);
            return;
        }
        for (int i = 0; i < Math.min(rows, levels.size()); i++) {
            Level level = levels.get(i);
            int ry = y + BOOK_TOP + i * BOOK_ROW_H;
            // A bar as long as the amount, against the largest in the book, so depth reads at a glance.
            int bar = Math.max(2, Math.round(w * (level.amount() / (float) most)));
            ModernGuiUtils.drawRoundedRect(graphics, x, ry - 1, bar, BOOK_ROW_H - 1, 2, MarketIcons.fade(color, 0.14f));
            String amount = Coins.full(level.amount()) + "x";
            if (level.orders() > 0) {
                String withOrders = amount + " · " + level.orders();
                if (GuiFont.width(font, withOrders) + GuiFont.width(font, Coins.full(level.price())) + 10 <= w) amount = withOrders;
            }
            int amountW = GuiFont.width(font, amount);
            String price = Coins.full(level.price());
            if (GuiFont.width(font, price) + amountW + 8 > w) price = Coins.compact(level.price());
            graphics.text(font, GuiFont.text(price), x + 2, ry + 1, MarketInk.ink(COIN), false);
            graphics.text(font, GuiFont.text(amount), x + w - 2 - amountW, ry + 1, AlpakaStyle.TEXT_SOFT, false);
        }
    }

    // ---- The Auction House's statistics ---------------------------------------------------------

    /** What the history says over the chosen range: the sales, a day and in all, and their average price. */
    record RangeStats(double salesPerDay, double sales, double average) {}

    static RangeStats rangeStats(List<PriceHistory.Point> points, PriceHistory.Range range) {
        if (points.isEmpty()) return new RangeStats(Double.NaN, Double.NaN, Double.NaN);
        double sales = 0;
        double weighted = 0;
        double plain = 0;
        int counted = 0;
        for (PriceHistory.Point p : points) {
            if (!Double.isNaN(p.first())) {
                plain += p.first();
                counted++;
            }
            if (!Double.isNaN(p.volume()) && !Double.isNaN(p.first())) {
                sales += p.volume();
                weighted += p.first() * p.volume();
            }
        }
        double days = switch (range) {
            case DAY -> 1;
            case WEEK -> 7;
            case MONTH -> 30;
            case ALL -> Math.max(1, (points.get(points.size() - 1).timeMs() - points.get(0).timeMs()) / 86_400_000.0);
        };
        double average = sales > 0 ? weighted / sales : counted > 0 ? plain / counted : Double.NaN;
        return new RangeStats(sales > 0 ? sales / days : Double.NaN, sales > 0 ? sales : Double.NaN, average);
    }

    /**
     * The hour of the day, on the player's clock, in which the most sold over an hourly history;
     * -1 if the history is not hourly or too short to tell.
     */
    static int busiestHour(List<PriceHistory.Point> hourly, ZoneId zone) {
        if (hourly.size() < 48) return -1;
        double[] byHour = new double[24];
        int hourlyGaps = 0;
        for (int i = 0; i < hourly.size(); i++) {
            PriceHistory.Point p = hourly.get(i);
            if (i > 0 && p.timeMs() - hourly.get(i - 1).timeMs() <= 2 * 3_600_000L) hourlyGaps++;
            if (Double.isNaN(p.volume())) continue;
            byHour[Instant.ofEpochMilli(p.timeMs()).atZone(zone).getHour()] += p.volume();
        }
        if (hourlyGaps < hourly.size() / 2) return -1;
        int best = -1;
        for (int h = 0; h < 24; h++) if (byHour[h] > 0 && (best < 0 || byHour[h] > byHour[best])) best = h;
        return best;
    }

    /** "Below Market", "Fair Price" or "Above Market": this price against the median sale. */
    static String verdict(double price, double median) {
        double change = (price - median) / median * 100.0;
        if (change <= -10) return "Below Market";
        if (change >= 10) return "Above Market";
        return "Fair Price";
    }

    private record Tile(String label, String value, int valueColor, String sub, int subColor) {}

    public static void drawAuctionStats(GuiGraphicsExtractor graphics, Font font, String tag, Double price,
                                        PriceHistory.Range range, int x, int y, int w, int h) {
        panel(graphics, x, y, w, h);
        graphics.text(font, GuiFont.text("Market Stats"), x + 8, y + 7, AlpakaStyle.TEXT, false);
        String rangeName = range == PriceHistory.Range.ALL ? "All Time" : "Past " + range.label;
        graphics.text(font, GuiFont.text(rangeName), x + w - 8 - GuiFont.width(font, rangeName), y + 7, AlpakaStyle.TEXT_MUTED, false);

        PriceHistory.Summary summary = PriceHistory.summary(tag);
        PriceHistory.Result history = PriceHistory.get(MarketKind.AUCTION_HOUSE, tag, range);
        PriceHistory.Result week = PriceHistory.get(MarketKind.AUCTION_HOUSE, tag, PriceHistory.Range.WEEK);
        boolean summaryLoading = summary.state() == PriceHistory.State.LOADING;
        boolean historyLoading = history.points().isEmpty() && history.state() == PriceHistory.State.LOADING;
        RangeStats stats = rangeStats(history.points(), range);
        int hour = busiestHour(week.points(), ZoneId.systemDefault());

        List<Tile> tiles = new ArrayList<>();
        // The lowest BIN, and what this auction asks over it.
        String vsLowest = "";
        int vsColor = AlpakaStyle.TEXT_MUTED;
        if (price != null && !Double.isNaN(summary.lowestBin()) && summary.lowestBin() > 0) {
            double over = (price - summary.lowestBin()) / summary.lowestBin() * 100.0;
            if (over <= 0.05) {
                vsLowest = "This is it";
                vsColor = MarketInk.ink(BUY);
            } else {
                vsLowest = String.format(Locale.ROOT, "This: +%.0f%%", over);
            }
        }
        tiles.add(new Tile("Lowest BIN", coins(summary.lowestBin(), summaryLoading), MarketInk.ink(COIN), vsLowest, vsColor));
        tiles.add(new Tile("Sales / Day", count(stats.salesPerDay(), historyLoading), AlpakaStyle.TEXT,
                Double.isNaN(stats.sales()) ? "" : Coins.full(Math.round(stats.sales())) + " Sold", AlpakaStyle.TEXT_MUTED));
        tiles.add(new Tile("Median", coins(summary.median(), summaryLoading), MarketInk.ink(COIN),
                Double.isNaN(summary.mode()) ? "" : "Mode " + Coins.compact(summary.mode()), AlpakaStyle.TEXT_MUTED));
        if (price != null && !Double.isNaN(summary.median()) && summary.median() > 0) {
            String verdict = verdict(price, summary.median());
            int color = verdict.startsWith("Below") ? MarketInk.ink(BUY) : verdict.startsWith("Above") ? MarketInk.ink(RED) : AlpakaStyle.TEXT;
            double change = (price - summary.median()) / summary.median() * 100.0;
            tiles.add(new Tile("Price Check", verdict, color, String.format(Locale.ROOT, "%+.0f%% vs median", change), AlpakaStyle.TEXT_MUTED));
        } else {
            tiles.add(new Tile("Price Check", summaryLoading ? "…" : "—", AlpakaStyle.TEXT, "", AlpakaStyle.TEXT_MUTED));
        }
        tiles.add(new Tile("Average", coins(stats.average(), historyLoading), MarketInk.ink(COIN), "Per Sale", AlpakaStyle.TEXT_MUTED));
        tiles.add(new Tile("Busiest Hour", hour < 0 ? (week.points().isEmpty() && week.state() == PriceHistory.State.LOADING ? "…" : "—")
                : String.format(Locale.ROOT, "%02d:00", hour), AlpakaStyle.TEXT, "Most Sales, Past Week", AlpakaStyle.TEXT_MUTED));

        int cols = w >= 250 ? 3 : 2;
        int rows = Math.max(1, Math.min(2, (h - HEADER_H - 6 + GAP) / (TILE_H + GAP)));
        int tileW = (w - 12 - (cols - 1) * GAP) / cols;
        for (int i = 0; i < Math.min(tiles.size(), cols * rows); i++) {
            int tx = x + 6 + (i % cols) * (tileW + GAP);
            int ty = y + HEADER_H + (i / cols) * (TILE_H + GAP);
            tile(graphics, font, tiles.get(i), tx, ty, tileW);
        }
    }

    private static void tile(GuiGraphicsExtractor graphics, Font font, Tile tile, int x, int y, int w) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, TILE_H, 3, MarketIcons.fade(AlpakaStyle.PANEL, 0.55f));
        int tw = w - 10;
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, tile.label(), tw)), x + 5, y + 4, AlpakaStyle.TEXT_MUTED, false);
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, tile.value(), tw)), x + 5, y + 14, tile.valueColor(), false);
        if (!tile.sub().isEmpty()) {
            graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, tile.sub(), tw)), x + 5, y + 24, tile.subColor(), false);
        }
    }

    private static String coins(double value, boolean loading) {
        if (Double.isNaN(value)) return loading ? "…" : "—";
        return Coins.compact(value);
    }

    private static String count(double value, boolean loading) {
        if (Double.isNaN(value)) return loading ? "…" : "—";
        return value < 10 ? String.format(Locale.ROOT, "%.1f", value) : Coins.full(Math.round(value));
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS + 1, AlpakaStyle.ROW);
        ModernGuiUtils.drawRect(graphics, x + AlpakaStyle.CARD_RADIUS + 1, y, w - 2 * (AlpakaStyle.CARD_RADIUS + 1), 1, 0x14FFFFFF);
    }
}
