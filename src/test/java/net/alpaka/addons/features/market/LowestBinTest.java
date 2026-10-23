package net.alpaka.addons.features.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowestBinTest {

    @Test
    void takesTheCheapestPerItemAndSkipsOwnAuctions() {
        String json = """
                [{"count":1,"startingBid":444000000,"auctioneerId":"aaa","bin":true},
                 {"count":1,"startingBid":400000000,"auctioneerId":"ME","bin":true},
                 {"count":4,"startingBid":1600000000,"auctioneerId":"bbb","bin":true},
                 {"count":1,"startingBid":100,"auctioneerId":"ccc","bin":false}]""";
        assertEquals(400_000_000.0, LowestBin.cheapestEach(json, "me"));
        assertTrue(Double.isNaN(LowestBin.cheapestEach("[]", "me")));
    }

    @Test
    void offersTheLowestAndALittleUnder() {
        List<LowestBin.Suggestion> s = LowestBin.suggestions(444_000_000);
        assertEquals(3, s.size());
        assertEquals(new LowestBin.Suggestion("444M", 444_000_000L), s.get(0));
        assertEquals(new LowestBin.Suggestion("-1% 439M", 439_000_000L), s.get(1));
        assertEquals(new LowestBin.Suggestion("-5% 421M", 421_000_000L), s.get(2));
    }

    @Test
    void cheapItemsStillGoUnder() {
        List<LowestBin.Suggestion> s = LowestBin.suggestions(10);
        assertEquals(10L, s.get(0).coins());
        assertEquals(9L, s.get(1).coins());
        // 5% under 10 is 9.5, which rounds down to the 9 already offered.
        assertEquals(2, s.size());
    }

    @Test
    void roundsDownToThreeDigits() {
        assertEquals(178_000L, LowestBin.roundDown(178_199));
        assertEquals(28_700_000L, LowestBin.roundDown(28_710_000));
        assertEquals(995L, LowestBin.roundDown(995.5));
    }

    @Test
    void asksForAPetOfItsRarity() {
        assertEquals("LEGENDARY", LowestBin.petRarity("{\"type\":\"LION\",\"tier\":\"LEGENDARY\",\"exp\":0}"));
        assertNull(LowestBin.petRarity("not json"));
        assertEquals("https://sky.coflnet.com/api/auctions/tag/PET_LION/active/bin?Rarity=LEGENDARY",
                LowestBin.url(new LowestBin.Item("PET_LION", "LEGENDARY", 1, "Lion")));
        assertEquals("https://sky.coflnet.com/api/auctions/tag/HYPERION/active/bin",
                LowestBin.url(new LowestBin.Item("HYPERION", null, 1, "Hyperion")));
    }
}
