package net.alpaka.addons.features.market;

/** Which of SkyBlock's two markets a menu belongs to. */
public enum MarketKind {
    AUCTION_HOUSE("Auction House"),
    BAZAAR("Bazaar");

    public final String displayName;

    MarketKind(String displayName) {
        this.displayName = displayName;
    }
}
