package net.alpaka.addons.features.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells the Auction House's and the Bazaar's menus apart from every other chest by their titles.
 *
 * The title is all there is to go by when a menu opens: its items arrive a moment later. Titles
 * that only these menus use are recognised on their own. The generic ones - "Confirm Purchase", or
 * a "Farming ➜ Wheat & Seeds" page - count only when the player got there from a menu of the same
 * market, so the SkyBlock menu or a museum page never turns into a market by accident.
 */
public final class MarketTitles {
    private MarketTitles() {}

    /** The arrow Hypixel puts between the parts of a Bazaar title. */
    public static final String ARROW = "➜";

    private static final Set<String> AUCTION_TITLES = Set.of(
            "Auction House", "Auctions Browser", "Manage Auctions", "Your Bids", "Create Auction",
            "Create BIN Auction", "Auction View", "BIN Auction View", "Co-op Auction House");

    private static final Set<String> BAZAAR_TITLES = Set.of(
            "Bazaar", "Your Bazaar Orders", "Co-op Bazaar Orders", "How many do you want?",
            "How much do you want to pay?", "Confirm Buy Order", "Confirm Instant Buy",
            "At what price are you selling?", "Confirm Sell Offer", "Order options");

    /** {@code Auctions: "hyperion"} and {@code Bazaar ➜ "wheat"}, the titles of a search's results. */
    private static final Pattern QUERY = Pattern.compile("^(?:Auctions: |Bazaar " + ARROW + " )\"(.*)\"$");

    /**
     * The market a menu with this title belongs to, or null for any other menu. {@code previous}
     * is the market of the menu or input the player came from, if any.
     */
    public static MarketKind classify(String title, MarketKind previous) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isEmpty()) return null;

        if (AUCTION_TITLES.contains(t) || t.startsWith("Auctions: ") || t.startsWith("Auctions Browser")
                || t.startsWith("Auctions by ")) {
            return MarketKind.AUCTION_HOUSE;
        }
        if (BAZAAR_TITLES.contains(t) || t.startsWith("Bazaar " + ARROW)) return MarketKind.BAZAAR;

        // Pages that are part of a market only when reached from inside it.
        if (previous == MarketKind.AUCTION_HOUSE
                && (t.contains("Auction") || t.contains("Bid") || t.startsWith("Confirm"))) {
            return MarketKind.AUCTION_HOUSE;
        }
        if (previous == MarketKind.BAZAAR && t.contains(ARROW) && !t.contains("Museum")) {
            return MarketKind.BAZAAR;
        }
        return null;
    }

    /** What a search's results page was searched for, or null when the title is no search. */
    public static String query(String title) {
        if (title == null) return null;
        Matcher m = QUERY.matcher(title.trim());
        return m.matches() ? m.group(1) : null;
    }

    /**
     * The path the header shows under the market's name: the title split at its arrows, without
     * the market's own name, which the header already shows.
     */
    public static List<String> breadcrumb(String title, MarketKind kind) {
        List<String> parts = new ArrayList<>();
        if (title == null) return parts;
        for (String part : title.split(ARROW)) {
            String p = part.trim();
            if (p.isEmpty() || p.equals(kind.displayName) || p.equals("Bazaar")) continue;
            parts.add(p);
        }
        return parts;
    }
}
