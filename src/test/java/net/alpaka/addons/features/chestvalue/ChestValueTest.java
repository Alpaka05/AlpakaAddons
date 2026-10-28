package net.alpaka.addons.features.chestvalue;

import net.alpaka.addons.features.chestvalue.ChestValue.Entry;
import net.alpaka.addons.features.chestvalue.ChestValue.Sort;
import net.alpaka.addons.features.chestvalue.ChestValue.Stack;
import net.alpaka.addons.features.chestvalue.ChestValue.Summary;
import net.alpaka.addons.features.market.MarketKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestValueTest {

    private static Stack stack(int slot, String id, String name, int count, double each) {
        return new Stack(slot, id, name, count, each, false, MarketKind.BAZAAR, null);
    }

    private static final List<Stack> CHEST = List.of(
            stack(0, "ENCHANTED_DIAMOND", "Enchanted Diamond", 64, 1_000),
            stack(1, "HYPERION", "Withered Hyperion", 1, 1_000_000),
            stack(2, "ENCHANTED_DIAMOND", "Enchanted Diamond", 64, 1_000),
            stack(3, "HYPERION", "Hyperion", 1, 600_000),
            stack(4, "DUNGEON_TRASH", "Trash", 3, Double.NaN),
            stack(5, "WHEAT", "Wheat", 200, 2));

    @Test
    void addsTheSameItemUpIntoOneLine() {
        Summary s = ChestValue.of(CHEST, Sort.VALUE);
        assertEquals(4, s.priced().size());
        Entry diamonds = s.priced().stream().filter(e -> e.id.equals("ENCHANTED_DIAMOND")).findFirst().orElseThrow();
        assertEquals(128, diamonds.count);
        assertEquals(List.of(0, 2), diamonds.slots);
        assertEquals(128_000, diamonds.total());
        // 1M + 600k + 128k + 400
        assertEquals(1_728_400, s.total());
        assertEquals(6, s.stacks());
        assertEquals(64 + 1 + 64 + 1 + 3 + 200, s.items());
    }

    @Test
    void theSameItemAtAnotherPriceIsItsOwnLine() {
        Summary s = ChestValue.of(CHEST, Sort.VALUE);
        assertEquals(2, s.priced().stream().filter(e -> e.id.equals("HYPERION")).count());
    }

    @Test
    void sortsByValuePriceEachOrAmount() {
        assertEquals(List.of("Withered Hyperion", "Hyperion", "Enchanted Diamond", "Wheat"),
                ChestValue.of(CHEST, Sort.VALUE).priced().stream().map(e -> e.name).toList());
        assertEquals(List.of("Withered Hyperion", "Hyperion", "Enchanted Diamond", "Wheat"),
                ChestValue.of(CHEST, Sort.EACH).priced().stream().map(e -> e.name).toList());
        assertEquals(List.of("Wheat", "Enchanted Diamond", "Withered Hyperion", "Hyperion"),
                ChestValue.of(CHEST, Sort.AMOUNT).priced().stream().map(e -> e.name).toList());
    }

    @Test
    void itemsWithoutAPriceAreListedApartAndMakeTheTotalALowerBound() {
        Summary s = ChestValue.of(CHEST, Sort.VALUE);
        assertEquals(1, s.unpriced().size());
        assertEquals("Trash", s.unpriced().get(0).name);
        assertTrue(s.incomplete());
        assertFalse(ChestValue.of(CHEST.subList(0, 4), Sort.VALUE).incomplete());
    }

    @Test
    void sharesAddUpToTheWhole() {
        Summary s = ChestValue.of(CHEST, Sort.VALUE);
        double sum = 0;
        for (Entry e : s.priced()) sum += s.share(e);
        assertEquals(1.0, sum, 1e-9);
        assertEquals(1_000_000 / 1_728_400.0, s.share(s.priced().get(0)), 1e-9);
    }

    @Test
    void findsTheLineOfASlot() {
        Summary s = ChestValue.of(CHEST, Sort.VALUE);
        assertSame(s.entryOf(0), s.entryOf(2));
        assertEquals("Trash", s.entryOf(4).name);
        assertNull(s.entryOf(42));
    }

    @Test
    void anEmptyMenuIsWorthNothing() {
        Summary s = ChestValue.of(List.of(), Sort.VALUE);
        assertTrue(s.isEmpty());
        assertEquals(0, s.total());
        assertFalse(s.incomplete());
    }

    @Test
    void sortCyclesThroughEveryOrder() {
        assertEquals(Sort.EACH, Sort.VALUE.next());
        assertEquals(Sort.VALUE, Sort.AMOUNT.next());
        assertEquals(Sort.VALUE, Sort.of(7 * 3));
    }

    @Test
    void writesTheListAsText() {
        String text = ChestValue.text("Ender Chest", ChestValue.of(CHEST, Sort.VALUE));
        assertEquals("""
                Ender Chest: at least 1,728,400 coins (1.73M)
                Withered Hyperion - 1M (58%)
                Hyperion - 600k (35%)
                128x Enchanted Diamond - 128k (7%)
                200x Wheat - 400 (<1%)
                3x Trash - no price""", text);
    }

    @Test
    void readsWhatASackHoldsFromTheLore() {
        assertEquals(12_345, ChestValue.sackCount(List.of("Slayer Sack", "", "Stored: 12,345/465.7k", "", "Click to pickup!")));
        assertEquals(0, ChestValue.sackCount(List.of("Slayer Sack", "Stored: 0/465.7k", "Empty sack!")));
        assertEquals(1_500, ChestValue.sackCount(List.of("Stored: 1.5k/60k")));
        assertEquals(-1, ChestValue.sackCount(List.of("Rarity: Legendary", "Stored in your Ender Chest")));
        assertEquals(-1, ChestValue.sackCount(List.of()));
    }
}
