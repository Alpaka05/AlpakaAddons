package net.alpaka.addons.features.market;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertEquals(MarketKind.BAZAAR, MarketTitles.classify("Are you sure?", MarketKind.BAZAAR));
        assertNull(MarketTitles.classify("Are you sure?", null));
        assertNull(MarketTitles.classify("Are you sure?", MarketKind.AUCTION_HOUSE));
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
    void aSearchWithOneResultIsStillAList() {
        List<MarketMenu.Slot> slots = browser();
        slots.set(12, slot(12, "black_stained_glass_pane", " "));
        MarketMenu menu = MarketMenu.parse(MarketKind.AUCTION_HOUSE, "Auctions: \"hyperion\"", 6, slots);
        assertEquals(List.of(11), menu.content);
        assertEquals(MarketMenu.Layout.GRID, menu.layout);
        assertNull(menu.hero);
    }

    @Test
    void cosmeticsIsACategory() {
        List<MarketMenu.Slot> slots = browser();
        slots.set(45, slot(45, "orange_dye", "Cosmetics", "Category", "", "Click to view items!"));
        MarketMenu menu = MarketMenu.parse(MarketKind.AUCTION_HOUSE, "Auctions Browser", 6, slots);
        assertEquals(MarketMenu.CategoryKind.COSMETICS, menu.categories.get(2).kind());
        assertFalse(menu.extras.contains(45));
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

    /** A Bazaar product's page as Hypixel sends it: the product itself names no price. */
    private static List<MarketMenu.Slot> productPage() {
        List<MarketMenu.Slot> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) slots.add(slot(i, "black_stained_glass_pane", " "));
        slots.set(10, slot(10, "golden_horse_armor", "Buy Instantly", "End Stone", "", "Price per unit: 3.2 coins",
                "Stack price: 204.8 coins", "", "Click to pick amount!"));
        slots.set(11, slot(11, "hopper", "Sell Instantly", "End Stone", "", "Price per unit: 0.1 coins", "", "Click to pick amount!"));
        slots.set(13, slot(13, "end_stone", "End Stone", "Collection Item", "", "COMMON BLOCK"));
        slots.set(15, slot(15, "filled_map", "Create Buy Order", "End Stone", "", "Only Order:", "- 3.1 coins each | 4,950x"));
        slots.set(16, slot(16, "map", "Create Sell Offer", "End Stone", "", "Top Offers:", "- 3.2 coins each | 62,208x"));
        slots.set(30, slot(30, "arrow", "Go Back", "To Mining"));
        slots.set(31, slot(31, "paper", "View Graphs", "Click to view!"));
        slots.set(32, slot(32, "book", "Manage Orders", "Click to manage!"));
        return slots;
    }

    @Test
    void aBazaarProductPageMakesTheProductItsHero() {
        MarketMenu menu = MarketMenu.parse(MarketKind.BAZAAR, "Mining ➜ End Stone", 4, productPage());
        assertEquals(MarketMenu.Layout.TILES, menu.layout);
        assertEquals(13, menu.hero);
        LoreInfo product = menu.info.get(13);
        assertEquals(LoreInfo.Type.PRODUCT, product.type);
        assertEquals(3.2, product.buyPrice, 1e-9);
        assertEquals(0.1, product.sellPrice, 1e-9);
    }

    @Test
    void theProductPageIsOnlyTheBazaars() {
        MarketMenu menu = MarketMenu.parse(MarketKind.AUCTION_HOUSE, "Mining ➜ End Stone", 4, productPage());
        assertNull(menu.hero);
        assertEquals(LoreInfo.Type.GENERIC, menu.info.get(13).type);
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
        assertEquals("Search the Bazaar", search.heading(MarketKind.BAZAAR, null));

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

    /** The Auction House's price sign, the same for a Buy It Now auction and one taking bids. */
    private static final String[] AUCTION_PRICE = {"", "^^^^^^^^^^^^^^^", "Your auction", "starting bid"};

    @Test
    void theAuctionPriceIsHeadedByTheMenuItCameFrom() {
        SignPrompt price = SignPrompt.of(AUCTION_PRICE);
        assertEquals(SignPrompt.Type.PRICE, price.type());
        assertEquals(1, price.inputLines());
        assertEquals("Buy It Now Price", price.heading(MarketKind.AUCTION_HOUSE, "Create BIN Auction"));
        assertEquals("Starting Bid", price.heading(MarketKind.AUCTION_HOUSE, "Create Auction"));
        assertEquals("Set Your Price", price.heading(null, null));
        assertEquals("Confirm", price.confirmLabel());
        assertEquals("Your auction starting bid", price.subtitle("Create Auction"));
        assertEquals("Buyers pay this at once", price.subtitle("Create BIN Auction"));

        SignPrompt bid = SignPrompt.of(new String[] {"", "^^^^^^^^^^^^^^^", "Your bid", ""});
        assertEquals("Place Your Bid", bid.heading(MarketKind.AUCTION_HOUSE, "BIN Auction View"));
        assertEquals("Bid", bid.confirmLabel());
    }

    @Test
    void numbersGoOnTheSignInPlainDigits() {
        SignPrompt price = SignPrompt.of(AUCTION_PRICE);
        assertEquals("4000000", price.answer("4m"));
        assertEquals("1500000", price.answer("1.5M"));
        assertEquals("1100000", price.answer("1.1m"));
        assertEquals("12000", price.answer("12,000"));
        assertEquals("3.25", price.answer("3.25"));
        assertEquals("2000000000", price.answer("2b"));
        // Not a number: as typed, for Hypixel to answer.
        assertEquals("abc", price.answer("abc"));

        SignPrompt amount = SignPrompt.of(new String[] {"", "^^^^^^^^^^^^^^^", "Enter amount", "to order"});
        assertEquals("1500", amount.answer("1.5k"));
        assertEquals("64", amount.answer("64"));

        SignPrompt search = SignPrompt.of(new String[] {"", "", "^^^^^^^^^^^^^^^", "Enter query"});
        assertEquals("4m", search.answer("4m"));
    }

    @Test
    void writesPlainDigits() {
        assertEquals("4000000", Coins.plain(4_000_000, true));
        assertEquals("0.1", Coins.plain(0.1, true));
        assertEquals("153420.2", Coins.plain(153_420.2, true));
        assertEquals("1500", Coins.plain(1499.6, false));
    }

    // ---- Price history --------------------------------------------------------------------------

    @Test
    void readsSkyCoflsBazaarHistoryOldestFirst() {
        List<PriceHistory.Point> points = PriceHistory.parseBazaar("""
                [{"buy":153227.1,"sell":149916.5,"timestamp":"2026-10-22T17:35:22.805"},
                 {"buy":0,"sell":149000.0,"timestamp":"2026-10-22T17:30:22.799"},
                 {"buy":0,"sell":0,"timestamp":"2026-10-22T17:25:22.799"}]""");
        assertEquals(2, points.size());
        assertTrue(points.get(0).timeMs() < points.get(1).timeMs());
        // No orders means no price, not a price of zero.
        assertTrue(Double.isNaN(points.get(0).first()));
        assertEquals(153227.1, points.get(1).first());
        assertEquals(1792690522805L, points.get(1).timeMs());
    }

    @Test
    void readsSkyCoflsAuctionHistory() {
        List<PriceHistory.Point> points = PriceHistory.parseAuction("""
                [{"min":750000000.0,"max":1.0222222E+09,"avg":920407407.3,"volume":3,"time":"2026-10-15T19:00:00"},
                 {"min":990000000.0,"max":1.5E+09,"avg":1085416666.3,"volume":6,"time":"2026-10-15T20:00:00"}]""");
        assertEquals(2, points.size());
        assertEquals(920407407.3, points.get(0).first());
        assertEquals(750000000.0, points.get(0).second());
    }

    @Test
    void asksSkyCoflForTheRightRange() {
        long now = 1792690522805L;
        assertEquals("https://sky.coflnet.com/api/bazaar/ENCHANTED_HAY_BALE/history/week",
                PriceHistory.url(MarketKind.BAZAAR, "ENCHANTED_HAY_BALE", PriceHistory.Range.WEEK, now));
        assertEquals("https://sky.coflnet.com/api/bazaar/COAL/history?start=2026-09-22T17:00:00&end=2026-10-22T17:00:00",
                PriceHistory.url(MarketKind.BAZAAR, "COAL", PriceHistory.Range.MONTH, now));
        assertEquals("https://sky.coflnet.com/api/item/price/HYPERION/history/full",
                PriceHistory.url(MarketKind.AUCTION_HOUSE, "HYPERION", PriceHistory.Range.ALL, now));
    }

    @Test
    void averagesALongHistoryDown() {
        List<PriceHistory.Point> points = new ArrayList<>();
        for (int i = 0; i < 1000; i++) points.add(new PriceHistory.Point(i, i, Double.NaN));
        List<PriceHistory.Point> small = PriceGraph.downsample(points, 100);
        assertEquals(100, small.size());
        assertEquals(4.5, small.get(0).first());
        assertTrue(Double.isNaN(small.get(0).second()));
    }

    @Test
    void findsSkyCoflsTagForAnItem() {
        assertEquals("PET_LION", ItemTags.petTag("{\"type\":\"LION\",\"tier\":\"LEGENDARY\"}"));
        assertEquals("HYPERION", ItemTags.expand("HYPERION", ""));
        assertNull(ItemTags.expand("RUNE", ""));
        ItemTags.index("""
                [{"name":"Enchanted Hay Bale","tag":"ENCHANTED_HAY_BALE","flags":"BAZAAR, GLOWING"},
                 {"name":"null","tag":"LOG_2:2","flags":"AUCTION"},
                 {"name":"Hyperion","tag":"HYPERION","flags":"AUCTION"}]""");
        assertEquals("ENCHANTED_HAY_BALE", ItemTags.lookup("Enchanted Hay Bale", MarketKind.BAZAAR));
        assertEquals("HYPERION", ItemTags.lookup("hyperion", MarketKind.AUCTION_HOUSE));
        assertNull(ItemTags.lookup("null", MarketKind.AUCTION_HOUSE));
    }

    @Test
    void theAuctionHousesSuggestionsLeaveOutPlainMinecraftItems() {
        ItemIcons.Catalog catalog = ItemIcons.index(com.google.gson.JsonParser.parseString("""
                {"items":[
                 {"id":"DIAMOND_SWORD","material":"DIAMOND_SWORD","name":"Diamond Sword"},
                 {"id":"WOOL:6","material":"WOOL","durability":6,"name":"Pink Wool"},
                 {"id":"HYPERION","material":"IRON_SWORD","name":"Hyperion"},
                 {"id":"DRAGON_EGG","material":"PAPER","name":"Dragon Egg"},
                 {"id":"NETHER_STAR","material":"NETHER_STAR","name":"Nether Star","museum":true},
                 {"id":"ENCHANTED_BOOK","material":"ENCHANTED_BOOK","name":"Enchanted Book"},
                 {"id":"CACTUS","material":"CACTUS","name":"Cactus"},
                 {"id":"BUILDER_CACTUS","material":"SKULL_ITEM","durability":3,"name":"Cactus"}]}"""));
        assertTrue(catalog.vanilla("DIAMOND_SWORD"));
        assertTrue(catalog.vanilla("WOOL:6"));
        // Not in Hypixel's list, but a damage value: a plain item.
        assertTrue(catalog.vanilla("WOOL:0"));
        assertFalse(catalog.vanilla("HYPERION"));
        // Named like a block, made of paper: SkyBlock's own.
        assertFalse(catalog.vanilla("DRAGON_EGG"));
        assertFalse(catalog.vanilla("NETHER_STAR"));
        assertFalse(catalog.vanilla("ENCHANTED_BOOK"));
        // SkyCofl's own tags, which Hypixel's list does not have.
        assertFalse(catalog.vanilla("PET_LION"));

        ItemTags.index("""
                [{"name":"Diamond Sword","tag":"DIAMOND_SWORD","flags":"AUCTION"},
                 {"name":"Wool","tag":"WOOL:0","flags":"AUCTION"},
                 {"name":"Hyperion","tag":"HYPERION","flags":"AUCTION"},
                 {"name":"Cactus","tag":"CACTUS","flags":"AUCTION"},
                 {"name":"Cactus","tag":"BUILDER_CACTUS","flags":"AUCTION"}]""");
        // The cactus head is SkyBlock's, so the name stays.
        assertEquals(List.of("Hyperion", "Cactus"), ItemTags.withoutVanilla(ItemTags.auctionTags(), catalog::vanilla));
    }

    @Test
    void suggestionIconsGoByTagNotByName() {
        ItemIcons.Catalog catalog = ItemIcons.index(com.google.gson.JsonParser.parseString("""
                {"items":[
                 {"id":"STARRED_BONZO_MASK","material":"SKULL_ITEM","durability":3,"name":"Bonzo's Mask","glowing":true},
                 {"id":"BONZO_MASK","material":"SKULL_ITEM","durability":3,"name":"Bonzo's Mask"},
                 {"id":"PET_SKIN_SHEEP_LIGHT_GREEN","material":"SKULL_ITEM","durability":3,"name":"Light Green Sheep Skin"},
                 {"id":"LUMBERJACK_RING","material":"PAPER","name":"Lumberjack Ring",
                  "item_model":"hypixel_skyblock:item/accessories/lumberjack/lumberjack_ring"}]}"""));
        assertEquals("hypixel_skyblock:item/accessories/lumberjack/lumberjack_ring", catalog.byId().get("LUMBERJACK_RING").model());
        assertNull(catalog.byId().get("BONZO_MASK").model());
        // Two items of one name: by name the first wins, by tag each is itself.
        assertTrue(catalog.byName().get("bonzo's mask").glowing());
        assertFalse(catalog.byId().get("BONZO_MASK").glowing());
        assertNotNull(catalog.byId().get("PET_SKIN_SHEEP_LIGHT_GREEN"));

        assertEquals("enchanted_book", ItemIcons.plain("ENCHANTMENT_SMOLDERING_5", "Smoldering V").material());
        assertEquals("splash_potion", ItemIcons.plain("POTION_damage", "Damage IV Splash Potion").material());
        assertEquals("potion", ItemIcons.plain("POTION_burning", "Burning I Potion").material());
        ItemIcons.Info wool = ItemIcons.plain("WOOL:6", "Pink Wool");
        assertEquals("WOOL", wool.material());
        assertEquals(6, wool.damage());
        assertNull(ItemIcons.plain("PET_GRIFFIN", "Griffin"));
    }

    @Test
    void findsPetsRunesAndShardsInTheNeuRepository() {
        List<String> pet = NeuItems.candidates("PET_GRIFFIN", null);
        assertEquals("GRIFFIN;4", pet.get(0));
        assertTrue(pet.contains("GRIFFIN;0"));
        assertEquals("PET_GRIFFIN", pet.get(pet.size() - 1));
        assertEquals(List.of("PET_SKIN_TIGER_TWILIGHT"), NeuItems.candidates("PET_SKIN_TIGER_TWILIGHT", null));
        assertEquals(List.of("PET_ITEM_BIG_TEETH_COMMON"), NeuItems.candidates("PET_ITEM_BIG_TEETH_COMMON", null));
        assertEquals(List.of("TIDAL_RUNE;1", "TIDAL_RUNE;2", "TIDAL_RUNE;3", "RUNE_TIDAL"), NeuItems.candidates("RUNE_TIDAL", null));
        assertEquals("SMITTEN_RUNE;3", NeuItems.candidates("UNIQUE_RUNE_SMITTEN", null).get(0));

        Map<String, String> shards = NeuItems.shardIds("""
                [{"stock":"SHARD_CRETAN_BULL","id":"ATTRIBUTE_SHARD_MYTHOLOGICAL_RESISTANCE;1"},
                 {"stock":"ENCHANTMENT_SHARPNESS_5","id":"SHARPNESS;5"}]""");
        assertEquals(Map.of("SHARD_CRETAN_BULL", "ATTRIBUTE_SHARD_MYTHOLOGICAL_RESISTANCE;1"), shards);
        assertEquals("ATTRIBUTE_SHARD_MYTHOLOGICAL_RESISTANCE;1", NeuItems.candidates("SHARD_CRETAN_BULL", shards).get(0));

        ItemIcons.Info head = NeuItems.parse("""
                {"itemid":"minecraft:skull","damage":3,
                 "nbttag":"{ExtraAttributes:{id:\\"REKINDLED_EMBER_BURNING\\"},ItemModel:\\"minecraft:player_head\\",SkullOwner:{Properties:{textures:[0:{Name:\\"textures\\",Value:\\"ewogIA==\\"}]}}}"}""");
        assertEquals("player_head", head.material());
        assertEquals("ewogIA==", head.skin());
        assertFalse(head.glowing());

        ItemIcons.Info boots = NeuItems.parse("""
                {"itemid":"minecraft:leather_boots","damage":0,"nbttag":"{display:{color:16711680},ench:[]}"}""");
        assertEquals("leather_boots", boots.material());
        assertEquals("255,0,0", boots.color());
        assertTrue(boots.glowing());

        // A model from Hypixel's resource pack stays apart from the paper underneath.
        ItemIcons.Info ring = NeuItems.parse("""
                {"itemid":"minecraft:paper","damage":0,
                 "nbttag":"{ExtraAttributes:{id:\\"LUMBERJACK_RING\\"},ItemModel:\\"hypixel_skyblock:item/accessories/lumberjack/lumberjack_ring\\"}"}""");
        assertEquals("paper", ring.material());
        assertEquals("hypixel_skyblock:item/accessories/lumberjack/lumberjack_ring", ring.model());

        // Without today's model the 1.8 skull stays a skull, turned into a player head by its damage.
        ItemIcons.Info old = NeuItems.parse("""
                {"itemid":"minecraft:skull","damage":3,"nbttag":"{}"}""");
        assertEquals("player_head", LegacyItems.modernId(old.material(), old.damage()));
    }

    @Test
    void oneWildSaleDoesNotSetTheScale() {
        List<PriceHistory.Point> points = new ArrayList<>();
        for (int i = 0; i < 100; i++) points.add(new PriceHistory.Point(i, 500 + i % 10, 450));
        points.set(50, new PriceHistory.Point(50, 2_000_000, 1));
        double[] scale = PriceGraph.scale(points);
        assertTrue(scale[1] < 1_000, "max " + scale[1]);
        assertTrue(scale[0] > 100, "min " + scale[0]);
    }

    @Test
    void theChangeComparesTheEndsOfTheRange() {
        List<PriceHistory.Point> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) points.add(new PriceHistory.Point(i, i < 10 ? 100 : 110, Double.NaN));
        double[] ends = PriceGraph.trend(points);
        assertEquals(100.0, ends[0]);
        assertEquals(110.0, ends[1]);
    }

    // ---- Search suggestions ---------------------------------------------------------------------

    private static final List<String> NAMES = List.of("Aspect of the Void", "Aspect of the Dragons", "Aspect of the End",
            "Dark Claymore", "Hyperion", "Enchanted Hay Bale", "Hay Bale", "Mithril Hay", "Giant's Sword");

    private static List<String> suggest(String query) {
        return SearchSuggestions.rank(query, NAMES, 5).stream().map(SearchSuggestions.Match::name).toList();
    }

    @Test
    void suggestsNamesStartingWithTheSearchFirst() {
        // Equally good matches: the shorter name first.
        assertEquals(List.of("Hay Bale", "Mithril Hay", "Enchanted Hay Bale"), suggest("hay"));
        assertEquals(List.of("Aspect of the End", "Aspect of the Void", "Aspect of the Dragons"), suggest("aspect"));
    }

    @Test
    void suggestsByWordStartsAndInitials() {
        assertEquals(List.of("Dark Claymore"), suggest("dark clay"));
        assertEquals(List.of("Enchanted Hay Bale"), suggest("ench hay"));
        assertEquals(List.of("Aspect of the Dragons"), suggest("aotd"));
        assertEquals(List.of("Giant's Sword"), suggest("giant"));
        assertTrue(suggest("zzz").isEmpty());
    }

    @Test
    void marksWhatMatched() {
        SearchSuggestions.Match match = SearchSuggestions.rank("ench hay", List.of("Enchanted Hay Bale"), 1).get(0);
        assertArrayEquals(new int[] {0, 4, 10, 13}, match.ranges());
        SearchSuggestions.Match initials = SearchSuggestions.rank("aotd", List.of("Aspect of the Dragons"), 1).get(0);
        assertArrayEquals(new int[] {0, 1, 7, 8, 10, 11, 14, 15}, initials.ranges());
    }

    // ---- Market stats ---------------------------------------------------------------------------

    @Test
    void readsTheOrderBookFromTheButtonsLore() {
        List<MarketStats.Level> orders = MarketStats.levels(List.of("Enchanted Hay Bale", "", "Top Orders:",
                "- 147,002.5 coins each | 1,640x from 3 orders", "- 147,000 coins each | 320x from 1 order",
                "- 5.3 coins each | 64x", "", "Click to setup Buy Order!"));
        assertEquals(3, orders.size());
        assertEquals(new MarketStats.Level(147_002.5, 1_640, 3), orders.get(0));
        assertEquals(new MarketStats.Level(147_000, 320, 1), orders.get(1));
        assertEquals(new MarketStats.Level(5.3, 64, 0), orders.get(2));
        List<MarketStats.Level> offers = MarketStats.levels(List.of("Top Offers:", "- 153,420.2 coins each | 960x in 2 offers"));
        assertEquals(new MarketStats.Level(153_420.2, 960, 2), offers.get(0));
        assertTrue(MarketStats.levels(List.of("Top Orders:", "No buy orders yet!")).isEmpty());
        assertEquals(List.of(new MarketStats.Level(3.1, 4_950, 0)), MarketStats.levels(List.of("Only Order:", "- 3.1 coins each | 4,950x")));
        assertEquals(10.0, MarketStats.spread(List.of(new MarketStats.Level(90, 1, 1)), List.of(new MarketStats.Level(100, 1, 1))), 1e-9);
        assertTrue(Double.isNaN(MarketStats.spread(List.of(), offers)));
    }

    @Test
    void readsSkyCoflsAuctionHistoryWithVolume() {
        List<PriceHistory.Point> points = PriceHistory.parseAuction("""
                [{"min":505911000.0,"max":998000000.0,"avg":879382200.0,"volume":5,"time":"2026-10-16T20:00:00"},
                 {"min":465000000.0,"max":1.45E+09,"avg":880412000.0,"volume":0,"time":"2026-10-16T21:00:00"}]""");
        assertEquals(2, points.size());
        assertEquals(998_000_000.0, points.get(0).high());
        assertEquals(5.0, points.get(0).volume());
        assertEquals(0.0, points.get(1).volume());
    }

    @Test
    void sumsUpARangeOfSales() {
        List<PriceHistory.Point> points = List.of(
                new PriceHistory.Point(0, 100, 90, 110, 3),
                new PriceHistory.Point(3_600_000, 200, 180, 220, 1));
        MarketStats.RangeStats stats = MarketStats.rangeStats(points, PriceHistory.Range.DAY);
        assertEquals(4.0, stats.salesPerDay());
        assertEquals(4.0, stats.sales());
        assertEquals(125.0, stats.average());
        assertEquals(4.0 / 7, MarketStats.rangeStats(points, PriceHistory.Range.WEEK).salesPerDay(), 1e-9);
        assertTrue(Double.isNaN(MarketStats.rangeStats(List.of(), PriceHistory.Range.DAY).salesPerDay()));
    }

    @Test
    void findsTheHourThatSellsMost() {
        List<PriceHistory.Point> week = new ArrayList<>();
        for (int i = 0; i < 72; i++) week.add(new PriceHistory.Point(i * 3_600_000L, 100, 90, 110, i % 24 == 18 ? 9 : 1));
        assertEquals(18, MarketStats.busiestHour(week, java.time.ZoneOffset.UTC));
        assertEquals(20, MarketStats.busiestHour(week, java.time.ZoneOffset.ofHours(2)));
        List<PriceHistory.Point> daily = new ArrayList<>();
        for (int i = 0; i < 72; i++) daily.add(new PriceHistory.Point(i * 86_400_000L, 100, 90, 110, 5));
        assertEquals(-1, MarketStats.busiestHour(daily, java.time.ZoneOffset.UTC));
        assertEquals(-1, MarketStats.busiestHour(week.subList(0, 10), java.time.ZoneOffset.UTC));
    }

    @Test
    void readsSkyCoflsSummaryAndJudgesAPrice() {
        PriceHistory.Summary summary = PriceHistory.summaryOf(PriceHistory.State.READY,
                com.google.gson.JsonParser.parseString("{\"min\":430000000,\"median\":938000000,\"mean\":809360992.45,\"mode\":535000000,\"volume\":92.5,\"max\":1515151515}").getAsJsonObject(),
                com.google.gson.JsonParser.parseString("{\"sell\":432180000.0,\"buy\":441000000.0,\"available\":1,\"isAh\":true}").getAsJsonObject());
        assertEquals(938_000_000.0, summary.median());
        assertEquals(535_000_000.0, summary.mode());
        assertEquals(92.5, summary.salesPerDay());
        assertEquals(441_000_000.0, summary.lowestBin());
        assertTrue(Double.isNaN(PriceHistory.summaryOf(PriceHistory.State.FAILED, null, null).lowestBin()));
        assertEquals("Below Market", MarketStats.verdict(80, 100));
        assertEquals("Fair Price", MarketStats.verdict(105, 100));
        assertEquals("Above Market", MarketStats.verdict(1_140, 938));
    }
}
