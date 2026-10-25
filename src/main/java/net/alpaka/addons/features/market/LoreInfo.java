package net.alpaka.addons.features.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a market item's lore says, read once so a card can show the parts that matter at a glance:
 * an auction's price, time and seller, a product's buy and sell price, an order's progress. The
 * full lore is still there as the item's tooltip.
 *
 * Works on lines without formatting codes. Anything it does not recognise stays unset, and the
 * card falls back to showing the lore's first line.
 */
public final class LoreInfo {
    public enum Type { AUCTION, PRODUCT, ORDER, GENERIC }

    /** A "Label: 1,234 coins" line. */
    public record CoinLine(String label, double coins) {}

    public Type type = Type.GENERIC;

    // Auctions
    public String seller;
    public Double binPrice;
    public Double startingBid;
    public Double topBid;
    public Double yourBid;
    public Integer bids;
    public String bidder;
    public String endsIn;
    /** "Sold!", "Expired!", "Ended!" and the like: an auction that no longer runs. */
    public String status;

    // Bazaar products
    public Double buyPrice;
    public Double sellPrice;

    // Bazaar orders
    public Long filled;
    public Long amount;
    public Double unitPrice;

    public final List<CoinLine> coinLines = new ArrayList<>();
    /** The first line of the lore that says something, for cards with nothing else to show. */
    public String firstLine;
    /** A "Click to ..." hint, which says what the click does. */
    public String clickHint;

    private static final String NUM = "([\\d,]+(?:\\.\\d+)?)";
    private static final Pattern SELLER = Pattern.compile("^Seller: (.+)$");
    private static final Pattern BIN = Pattern.compile("^Buy it now: " + NUM + " coins?", Pattern.CASE_INSENSITIVE);
    private static final Pattern STARTING = Pattern.compile("^Starting bid: " + NUM + " coins?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_BID = Pattern.compile("^Top bid: " + NUM + " coins?", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUR_BID = Pattern.compile("^Your (?:previous )?bid: " + NUM + " coins?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIDS = Pattern.compile("^Bids: ([\\d,]+) bids?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIDDER = Pattern.compile("^Bidder: (.+)$");
    private static final Pattern ENDS = Pattern.compile("^Ends in: (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS = Pattern.compile("^Status: (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVER = Pattern.compile("^(Sold|Expired|Ended)!?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY_PRICE = Pattern.compile("^Buy price: " + NUM + " coins?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELL_PRICE = Pattern.compile("^Sell price: " + NUM + " coins?", Pattern.CASE_INSENSITIVE);
    /** "Filled: 26/50 (52%)", and for large orders "Filled: 1.2k/1.5k (80%)". */
    private static final Pattern FILLED = Pattern.compile("^Filled: ([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)");
    private static final Pattern AMOUNT = Pattern.compile("^(?:Order|Offer) amount: ([\\d,]+)x?", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT = Pattern.compile("^Price per unit: " + NUM + " coins?", Pattern.CASE_INSENSITIVE);
    private static final Pattern COIN_LINE = Pattern.compile("^([A-Za-z][A-Za-z '/-]{1,30}): " + NUM + " coins?$");
    private static final Pattern SEPARATOR = Pattern.compile("^[-▬=_ ]+$");

    public static LoreInfo parse(List<String> lore) {
        LoreInfo info = new LoreInfo();
        for (String raw : lore) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            try {
                read(info, line);
            } catch (NumberFormatException e) {
                // A number too large for its field: the line is skipped, the card shows the rest.
            }
        }

        if (info.seller != null || info.binPrice != null || info.startingBid != null || info.topBid != null) {
            info.type = Type.AUCTION;
        } else if (info.buyPrice != null || info.sellPrice != null) {
            info.type = Type.PRODUCT;
        } else if (info.filled != null) {
            info.type = Type.ORDER;
        }
        return info;
    }

    /** Reads one line into what the lore says so far. */
    private static void read(LoreInfo info, String line) {
        Matcher m;
        if ((m = SELLER.matcher(line)).find()) info.seller = m.group(1).trim();
        else if ((m = BIN.matcher(line)).find()) info.binPrice = Coins.parseLore(m.group(1));
        else if ((m = STARTING.matcher(line)).find()) info.startingBid = Coins.parseLore(m.group(1));
        else if ((m = TOP_BID.matcher(line)).find()) info.topBid = Coins.parseLore(m.group(1));
        else if ((m = YOUR_BID.matcher(line)).find()) info.yourBid = Coins.parseLore(m.group(1));
        else if ((m = BIDS.matcher(line)).find()) info.bids = Integer.parseInt(m.group(1).replace(",", ""));
        else if ((m = BIDDER.matcher(line)).find()) info.bidder = m.group(1).trim();
        else if ((m = ENDS.matcher(line)).find()) info.endsIn = m.group(1).trim();
        else if ((m = STATUS.matcher(line)).find()) info.status = m.group(1).trim();
        else if ((m = OVER.matcher(line)).find()) info.status = m.group(1) + "!";
        else if ((m = BUY_PRICE.matcher(line)).find()) info.buyPrice = Coins.parseLore(m.group(1));
        else if ((m = SELL_PRICE.matcher(line)).find()) info.sellPrice = Coins.parseLore(m.group(1));
        else if ((m = FILLED.matcher(line)).find()) {
            Double filled = Coins.parseInput(m.group(1));
            Double amount = Coins.parseInput(m.group(2));
            if (filled != null && amount != null) {
                info.filled = Math.round(filled);
                info.amount = Math.round(amount);
            }
        } else if ((m = AMOUNT.matcher(line)).find()) {
            if (info.amount == null) info.amount = Long.parseLong(m.group(1).replace(",", ""));
        } else if ((m = UNIT.matcher(line)).find()) {
            info.unitPrice = Coins.parseLore(m.group(1));
            if (info.unitPrice != null) info.coinLines.add(new CoinLine("Price per unit", info.unitPrice));
        } else if ((m = COIN_LINE.matcher(line)).find()) {
            Double coins = Coins.parseLore(m.group(2));
            if (coins != null) info.coinLines.add(new CoinLine(m.group(1).trim(), coins));
        }

        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.startsWith("click") || lower.startsWith("right-click") || lower.startsWith("left-click")) {
            if (info.clickHint == null) info.clickHint = line;
        } else if (info.firstLine == null && !SEPARATOR.matcher(line).matches()) {
            info.firstLine = line;
        }
    }

    /** Whether the card shows parsed data rather than just the lore's first line. */
    public boolean isRich() {
        return type != Type.GENERIC;
    }

    /** A Buy It Now auction, as opposed to one taking bids. */
    public boolean isBin() {
        return binPrice != null;
    }

    /** The price that matters now: the BIN, else the top bid, else the starting bid. */
    public Double auctionPrice() {
        if (binPrice != null) return binPrice;
        if (topBid != null) return topBid;
        return startingBid;
    }

    /** Whether the auction has stopped running: sold, expired or ended. */
    public boolean isOver() {
        if (status == null) return false;
        String s = status.toLowerCase(Locale.ROOT);
        return s.contains("sold") || s.contains("expired") || s.contains("ended");
    }

    /** A Bazaar order's share filled, 0..1, or -1 without one. */
    public float fillRatio() {
        if (filled == null || amount == null || amount <= 0) return -1;
        return Math.max(0f, Math.min(1f, filled / (float) amount));
    }
}
