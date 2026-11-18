package net.alpaka.addons.features.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AveragePriceTest {

    @Test
    void readsTheMeanOfSkyCoflsSummary() {
        String json = """
                {"min":230000000,"median":450000000,"mean":422557314.5585586,"mode":450000000,"volume":55.5,"max":525000000}""";
        assertEquals(422557314.5585586, AveragePrice.mean(json));
    }

    @Test
    void noSalesMeansNoAverage() {
        assertTrue(Double.isNaN(AveragePrice.mean("{\"min\":0,\"median\":0,\"mean\":0,\"volume\":0,\"max\":0}")));
        assertTrue(Double.isNaN(AveragePrice.mean("[]")));
    }

    @Test
    void asksForAPetByItsRarity() {
        assertEquals("https://sky.coflnet.com/api/item/price/PET_ENDER_DRAGON?Rarity=LEGENDARY",
                AveragePrice.url(new LowestBin.Item("PET_ENDER_DRAGON", "LEGENDARY", 1, "Ender Dragon")));
        assertEquals("https://sky.coflnet.com/api/item/price/HYPERION",
                AveragePrice.url(new LowestBin.Item("HYPERION", null, 1, "Hyperion")));
    }
}
