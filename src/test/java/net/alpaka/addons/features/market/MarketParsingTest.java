package net.alpaka.addons.features.market;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketParsingTest {

    // ---- Titles ---------------------------------------------------------------------------------

    @Test
    void recognisesTheMarketsByTitle() {
        assertEquals(MarketKind.AUCTION_HOUSE, MarketTitles.classify("Auctions Browser", null));
        assertEquals(MarketKind.AUCTION_HOUSE, MarketTitles.classify("Auctions: \"hyperion\"", null));
        assertEquals(MarketKind.AUCTION_HOUSE, MarketTitles.classify("BIN Auction View", null));
        assertEquals(MarketKind.BAZAAR, MarketTitles.classify("Bazaar ➜ Farming", null));
        assertEquals(MarketKind.BAZAAR, MarketTitles.classify("How many do you want?", null));
    }

    @Test
    void leavesOtherMenusAlone() {
        assertNull(MarketTitles.classify("SkyBlock Menu", null));
        assertNull(MarketTitles.classify("Confirm Purchase", null));
        assertNull(MarketTitles.classify("Farming ➜ Wheat & Seeds", null));
        // Museum pages use the same arrow, even when reached from the Bazaar.
        assertNull(MarketTitles.classify("Museum ➜ Weapons", MarketKind.BAZAAR));
        assertNull(MarketTitles.classify("SkyBlock Menu", MarketKind.AUCTION_HOUSE));
    }

    @Test
    void genericTitlesCountOnlyFromInsideTheMarket() {
        assertEquals(MarketKind.AUCTION_HOUSE, MarketTitles.classify("Confirm Purchase", MarketKind.AUCTION_HOUSE));
        assertEquals(MarketKind.BAZAAR, MarketTitles.classify("Farming ➜ Wheat & Seeds", MarketKind.BAZAAR));
        assertNull(MarketTitles.classify("Confirm Purchase", MarketKind.BAZAAR));
    }

    @Test
    void readsTheSearchAndTheBreadcrumb() {
        assertEquals("hyperion", MarketTitles.query("Auctions: \"hyperion\""));
        assertEquals("wheat", MarketTitles.query("Bazaar ➜ \"wheat\""));
        assertNull(MarketTitles.query("Auctions Browser"));
        assertEquals(List.of("Farming"), MarketTitles.breadcrumb("Bazaar ➜ Farming", MarketKind.BAZAAR));
        assertEquals(List.of("Farming", "Wheat & Seeds"),
                MarketTitles.breadcrumb("Farming ➜ Wheat & Seeds", MarketKind.BAZAAR));
    }

    // ---- Coins ----------------------------------------------------------------------------------

    @Test
    void readsCoinsAsPlayersTypeThem() {
        assertEquals(1_500_000.0, Coins.parseInput("1.5m"));
        assertEquals(250_000.0, Coins.parseInput("250k"));
        assertEquals(2_000_000_000.0, Coins.parseInput("2B"));
        assertEquals(12_000.0, Coins.parseInput("12,000"));
        assertEquals(64.0, Coins.parseInput(" 64 "));
        assertNull(Coins.parseInput("abc"));
        assertNull(Coins.parseInput("k"));
        assertNull(Coins.parseInput(""));
    }

    @Test
    void writesCoinsShort() {
        assertEquals("950", Coins.compact(950));
        assertEquals("5.3", Coins.compact(5.3));
        assertEquals("12.5k", Coins.compact(12_500));
        assertEquals("1.25M", Coins.compact(1_250_000));
        assertEquals("1M", Coins.compact(999_700));
        assertEquals("3.1B", Coins.compact(3_100_000_000.0));
        assertEquals("1,500,000", Coins.full(1_500_000));
    }

    @Test
    void readsHypixelTimes() {
        assertEquals(5 * 3600 + 3 * 60 + 12, Coins.seconds("5h 3m 12s"));
        assertEquals(86_400 + 2 * 3600, Coins.seconds("1d 2h"));
        assertEquals(-1, Coins.seconds("Soon!"));
    }

    // ---- Lore -----------------------------------------------------------------------------------

    @Test
    void readsABinAuction() {
        LoreInfo info = LoreInfo.parse(List.of(
                "Gear Score: 620", "", "-----------------", "Seller: [MVP+] Alpaka05",
                "Buy it now: 1,234,567 coins", "", "Ends in: 5h 3m 12s", "", "Click to inspect!"));
        assertEquals(LoreInfo.Type.AUCTION, info.type);
        assertTrue(info.isBin());
        assertEquals(1_234_567.0, info.auctionPrice());
        assertEquals("[MVP+] Alpaka05", info.seller);
        assertEquals("5h 3m 12s", info.endsIn);
        assertEquals("Click to inspect!", info.clickHint);
        assertEquals("Gear Score: 620", info.firstLine);
    }

    @Test
    void anAuctionWithBidsShowsTheTopBid() {
        LoreInfo info = LoreInfo.parse(List.of(
                "Seller: [VIP] Someone", "Bids: 3 bids", "Top bid: 150,000 coins", "Bidder: [MVP] Other",
                "Ends in: 59s"));
        assertFalse(info.isBin());
        assertEquals(150_000.0, info.auctionPrice());
        assertEquals(3, info.bids);
    }

    @Test
    void anEndedAuctionIsOver() {
        LoreInfo info = LoreInfo.parse(List.of("Seller: You", "Buy it now: 5 coins", "Status: Sold!"));
        assertTrue(info.isOver());
    }

    @Test
    void readsABazaarProductAndOrder() {
        LoreInfo product = LoreInfo.parse(List.of("Farming Commodity", "Buy price: 5.3 coins", "Sell price: 4.9 coins"));
        assertEquals(LoreInfo.Type.PRODUCT, product.type);
        assertEquals(5.3, product.buyPrice);
        assertEquals(4.9, product.sellPrice);

        LoreInfo order = LoreInfo.parse(List.of("Order amount: 640x", "Filled: 320/640 50%!", "Price per unit: 5.1 coins"));
        assertEquals(LoreInfo.Type.ORDER, order.type);
        assertEquals(0.5f, order.fillRatio());
        assertEquals(5.1, order.unitPrice);

        // Large orders are written short.
        LoreInfo large = LoreInfo.parse(List.of("Order amount: 1,500x", "Filled: 1.2k/1.5k (80%)"));
        assertEquals(1200L, large.filled);
        assertEquals(0.8f, large.fillRatio(), 0.001f);
    }

    @Test
    void absurdNumbersDoNotBreakTheCard() {
        LoreInfo info = LoreInfo.parse(List.of("Bids: 99,999,999,999 bids", "Seller: Someone"));
        assertEquals(LoreInfo.Type.AUCTION, info.type);
        assertNull(info.bids);
    }

    @Test
    void readsTheAuctionHousePageArrows() {
        List<MarketMenu.Slot> slots = new ArrayList<>();
        slots.add(slot(53, "arrow", "Next Page", "(2/14)", "", "Click to turn the page!"));
        MarketMenu menu = MarketMenu.parse(MarketKind.AUCTION_HOUSE, "Auctions Browser", 6, slots);
        assertEquals(2, menu.page);
        assertEquals(14, menu.pages);
    }

    // ---- Menus ----------------------------------------------------------------------------------

    private static MarketMenu.Slot slot(int index, String id, String name, String... lore) {
        return new MarketMenu.Slot(index, id, name, List.of(lore), false);
    }

    private static List<MarketMenu.Slot> browser() {
        List<MarketMenu.Slot> slots = new ArrayList<>();
        for (int i = 0; i < 54; i++) slots.add(slot(i, "black_stained_glass_pane", " "));
        slots.set(0, new MarketMenu.Slot(0, "golden_sword", "Weapons", List.of("Currently browsing!"), true));
        slots.set(9, slot(9, "diamond_chestplate", "Armor"));
        slots.set(11, slot(11, "diamond_sword", "Hyperion", "Seller: A", "Buy it now: 1,000,000,000 coins"));
        slots.set(12, slot(12, "bow", "Terminator", "Seller: B", "Starting bid: 900,000,000 coins"));
        slots.set(47, slot(47, "oak_sign", "Search", "Click to search!"));
        slots.set(49, slot(49, "arrow", "Go Back", "To Auction House"));
        slots.set(50, slot(50, "hopper", "Sort", "  Highest Bid", "▶ Lowest Price", "  Ending soon"));
        slots.set(53, slot(53, "arrow", "Next Page", "Page 3"));
        slots.set(46, slot(46, "arrow", "Previous Page", "Page 1"));
        slots.set(45, slot(45, "cauldron", "Auction House Settings", "Click to open!"));
        return slots;
    }

    @Test
    void takesTheBrowserApart() {
        MarketMenu menu = MarketMenu.parse(MarketKind.AUCTION_HOUSE, "Auctions Browser", 6, browser());
        assertEquals(2, menu.categories.size());
        assertTrue(menu.categories.get(0).selected());
        assertFalse(menu.categories.get(1).selected());
        assertEquals(List.of(11, 12), menu.content);
        assertEquals(List.of(45), menu.extras);
        assertEquals(47, menu.controls.get(MarketMenu.Control.SEARCH));
        assertEquals("Lowest Price", menu.controlValues.get(MarketMenu.Control.SORT));
        assertEquals("Auction House", menu.backTarget);
        assertEquals(2, menu.page);
        assertEquals(MarketMenu.Layout.GRID, menu.layout);
        assertFalse(menu.empty);
    }

    @Test
    void aFewActionsBecomeTilesWithTheAuctionAsHero() {
        List<MarketMenu.Slot> slots = new ArrayList<>();
        for (int i = 0; i < 54; i++) slots.add(slot(i, "black_stained_glass_pane", " "));
        slots.set(13, slot(13, "diamond_sword", "Hyperion", "Seller: A", "Buy it now: 1,000 coins"));
        slots.set(31, slot(31, "gold_nugget", "Buy Item Right Now", "Price: 1,000 coins"));
        slots.set(49, slot(49, "arrow", "Go Back"));
        MarketMenu menu = MarketMenu.parse(MarketKind.AUCTION_HOUSE, "BIN Auction View", 6, slots);
        assertEquals(MarketMenu.Layout.TILES, menu.layout);
        assertEquals(13, menu.hero);
        assertEquals(List.of(13, 31), menu.content);
    }

    @Test
    void anEmptyChestIsStillLoading() {
        List<MarketMenu.Slot> slots = new ArrayList<>();
        for (int i = 0; i < 54; i++) slots.add(slot(i, "air", ""));
        assertTrue(MarketMenu.parse(MarketKind.BAZAAR, "Bazaar ➜ Farming", 6, slots).empty);
    }

    @Test
    void theTitleSelectsTheBazaarCategory() {
        List<MarketMenu.Slot> slots = new ArrayList<>();
        slots.add(slot(0, "golden_hoe", "Farming"));
        slots.add(slot(9, "diamond_pickaxe", "Mining"));
        MarketMenu menu = MarketMenu.parse(MarketKind.BAZAAR, "Bazaar ➜ Mining", 6, slots);
        assertFalse(menu.categories.get(0).selected());
        assertTrue(menu.categories.get(1).selected());
    }

    // ---- Sign prompts ---------------------------------------------------------------------------

    @Test
    void recognisesHypixelsSignPrompts() {
        // The Auction House's and the Bazaar's search: two lines for the answer.
        SignPrompt search = SignPrompt.of(new String[] {"", "", "^^^^^^^^^^^^^^^", "Enter query"});
        assertEquals(SignPrompt.Type.SEARCH, search.type());
        assertEquals(2, search.inputLines());
        assertEquals("Enter query", search.question());
        assertEquals("Search the Bazaar", search.heading(MarketKind.BAZAAR));

        SignPrompt oneLine = SignPrompt.of(new String[] {"", "^^^^^^^^^^^^^^^", "Enter query", ""});
        assertEquals(SignPrompt.Type.SEARCH, oneLine.type());
        assertEquals(1, oneLine.inputLines());

        assertEquals(SignPrompt.Type.PRICE, SignPrompt.of(new String[] {"", "^^^^^^^^^^^^^^^", "Your bid", ""}).type());
        assertEquals(SignPrompt.Type.AMOUNT, SignPrompt.of(new String[] {"", "^^^^^^^^^^^^^^^", "Enter amount", "to order"}).type());
        assertEquals(SignPrompt.Type.PRICE, SignPrompt.of(new String[] {"", "^^Flipping^^", "Enter price", "per unit"}).type());
        assertNull(SignPrompt.of(new String[] {"Welcome", "to my", "island", ""}));
    }

    /** Six pixels a character and fifteen to a line, roughly the vanilla font on a sign. */
    private static int width(String text) {
        return text.length() * 6;
    }

    @Test
    void splitsALongSearchAtASpace() {
        SignPrompt search = SignPrompt.of(new String[] {"", "", "^^^^^^^^^^^^^^^", "Enter query"});
        assertArrayEquals(new String[] {"hyperion", ""}, search.split("hyperion", MarketParsingTest::width, 90));
        assertArrayEquals(new String[] {"withered dark", "claymore"},
                search.split("withered dark claymore", MarketParsingTest::width, 90));
        // A word longer than a line would come back as two words, so it does not fit.
        assertNull(search.split("abcdefghijklmnopq", MarketParsingTest::width, 90));
        assertNull(search.split("one two three four five six seven", MarketParsingTest::width, 90));
    }

    @Test
    void aPriceHasOneLine() {
        SignPrompt price = SignPrompt.of(new String[] {"", "^^^^^^^^^^^^^^^", "Your bid", ""});
        assertArrayEquals(new String[] {"1.5m"}, price.split("1.5m", MarketParsingTest::width, 90));
        assertNull(price.split("1000000000000000", MarketParsingTest::width, 90));
    }
}
