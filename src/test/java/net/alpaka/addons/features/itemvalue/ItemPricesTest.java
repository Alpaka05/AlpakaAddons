package net.alpaka.addons.features.itemvalue;

import net.alpaka.addons.features.market.MarketKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPricesTest {

    @Test
    void readsTheBazaarsQuickStatusAndSkipsTheOrderBooks() throws IOException {
        Map<String, ItemPrices.Product> bazaar = ItemPrices.parseBazaar(new StringReader("""
                {"success":true,"lastUpdated":1792714902786,"products":{
                  "HOT_POTATO_BOOK":{"product_id":"HOT_POTATO_BOOK",
                    "sell_summary":[{"amount":9,"pricePerUnit":78000.1,"orders":1}],
                    "buy_summary":[{"amount":1,"pricePerUnit":99999.7,"orders":1}],
                    "quick_status":{"productId":"HOT_POTATO_BOOK","sellPrice":78461.5,"sellVolume":31792,"buyPrice":99999.7,"buyOrders":72}},
                  "ENCHANTMENT_EFFICIENCY_6":{"product_id":"ENCHANTMENT_EFFICIENCY_6","sell_summary":[],"buy_summary":[],
                    "quick_status":{"sellPrice":0.0,"buyPrice":0.0}}
                }}"""));
        assertEquals(99999.7, bazaar.get("HOT_POTATO_BOOK").buy());
        assertEquals(78461.5, bazaar.get("HOT_POTATO_BOOK").sell());
        // Zero is how Hypixel writes a side nobody trades on.
        assertTrue(Double.isNaN(bazaar.get("ENCHANTMENT_EFFICIENCY_6").buy()));
    }

    @Test
    void readsTheLowestBins() throws IOException {
        Map<String, Double> bins = ItemPrices.parseLowestBins(new StringReader(
                "{\"HYPERION\":440000000,\"LION;4\":1.2E7,\"BROKEN\":\"x\",\"FREE\":0}"));
        assertEquals(440_000_000.0, bins.get("HYPERION"));
        assertEquals(12_000_000.0, bins.get("LION;4"));
        assertNull(bins.get("BROKEN"));
        assertNull(bins.get("FREE"));
    }

    @Test
    void quotesTheChosenBazaarSideThenTheOtherThenTheAuctionHouse() {
        Map<String, ItemPrices.Product> bazaar = Map.of(
                "BOOK", new ItemPrices.Product(100, 80),
                "NO_SELLERS", new ItemPrices.Product(Double.NaN, 50),
                "DEAD", new ItemPrices.Product(Double.NaN, Double.NaN));
        Map<String, Double> bins = Map.of("SWORD", 1_000.0, "DEAD", 7.0);

        assertEquals(100, ItemPrices.quote("BOOK", bazaar, bins, false).price());
        assertEquals(80, ItemPrices.quote("BOOK", bazaar, bins, true).price());
        assertEquals(50, ItemPrices.quote("NO_SELLERS", bazaar, bins, false).price());
        ItemPrices.Quote sword = ItemPrices.quote("SWORD", bazaar, bins, false);
        assertEquals(MarketKind.AUCTION_HOUSE, sword.market());
        // A product without orders on either side falls back to the Auction House.
        assertEquals(7, ItemPrices.quote("DEAD", bazaar, bins, false).price());
        assertNull(ItemPrices.quote("NOTHING", bazaar, bins, false));
        assertNull(ItemPrices.quote("BOOK", null, null, false));
    }
}
