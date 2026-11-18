package net.alpaka.addons.features.profittracker

import net.alpaka.addons.features.itemsearch.ItemSearchMenus
import net.alpaka.addons.features.slayer.SlayerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/** The Profit Tracker's parsing, figures and CSV, without a game around it. */
class ProfitTrackerTest {

    @Test
    fun readsThePurse() {
        assertEquals(12_345_678.0, ProfitParse.purse("Purse: 12,345,678"))
        assertEquals(1_000.5, ProfitParse.purse("Purse: 1,000.5 (+50)"))
        assertEquals(42.0, ProfitParse.purse("Piggy: 42"))
        assertNull(ProfitParse.purse("Bits: 1,234"))
    }

    @Test
    fun readsChatLines() {
        assertEquals("Enchanted Rotten Flesh",
            ProfitParse.movedFromSacks("Moved 64 Enchanted Rotten Flesh from your Sacks to your inventory."))
        assertEquals("Enchanted Diamond", ProfitParse.supercrafted("You Supercrafted Enchanted Diamond x4!"))
        assertEquals("Enchanted Diamond", ProfitParse.supercrafted("You Supercrafted Enchanted Diamond!"))
        assertTrue(ProfitParse.slayerStarted("  SLAYER QUEST STARTED!"))
        assertTrue(ProfitParse.slayerComplete("  SLAYER QUEST COMPLETE!"))
        assertTrue(ProfitParse.slayerComplete("  NICE! SLAYER BOSS SLAIN!"))
        assertFalse(ProfitParse.slayerComplete("  SLAYER QUEST FAILED!"))
        assertEquals(100_000L, ProfitParse.autoSlayerCost("Took 100,000 coins from your bank for auto-slayer..."))
        assertEquals(2_500L, ProfitParse.autoSlayerCost("Took 2.5k coins from your bank for auto-slayer..."))
        assertTrue(ProfitParse.burrow("You dug out a Griffin Burrow! (2/4)"))
        assertTrue(ProfitParse.burrow("You finished the Griffin burrow chain! (4/4)"))
        assertEquals("Mite", ProfitParse.pestKill("You received 3x Compost for killing a Mite!"))
        assertEquals("Earthworm", ProfitParse.pestKill("You received 1x Enchanted Melon for killing an Earthworm!"))
        assertEquals(1_234_567.0, ProfitParse.dragonDamage("           Your Damage: 1,234,567 (Position #3)"))
        assertTrue(ProfitParse.eyePlaced("☬ You placed a Summoning Eye! (3/8)"))
        assertTrue(ProfitParse.eyeRecovered("You recovered a Summoning Eye!"))
    }

    @Test
    fun readsSackLines() {
        val change = ItemSearchMenus.parseSackChange("  +12 Revenant Flesh (Combat Sack)")!!
        assertEquals(12L, change.delta())
        assertEquals("Revenant Flesh", change.item())
    }

    @Test
    fun shortNumbers() {
        assertEquals(1_500L, ProfitParse.shortNumber("1,500"))
        assertEquals(2_500_000L, ProfitParse.shortNumber("2.5M"))
        assertNull(ProfitParse.shortNumber("abc"))
    }

    @Test
    fun petKeys() {
        assertEquals("ENDER_DRAGON;4", ProfitItems.petKey("""{"type":"ENDER_DRAGON","tier":"LEGENDARY","exp":0}"""))
        assertEquals("BAT;5", ProfitItems.petKey("""{"type":"BAT","tier":"MYTHIC"}"""))
        assertNull(ProfitItems.petKey("not json"))
        assertNull(ProfitItems.petKey(""))
    }

    @Test
    fun activityIdsRoundTrip() {
        val zombie = ProfitActivity.slayer(SlayerType.ZOMBIE)
        assertEquals("Revenant Horror", ProfitActivity.of(zombie.id).title)
        assertEquals(ProfitActivity.FISHING, ProfitActivity.of("fishing"))
        val isle = ProfitActivity.of("island:Crimson Isle")
        assertTrue(isle.isIsland)
        assertEquals("Crimson Isle", isle.title)
    }

    @Test
    fun bucketAddsAndRemoves() {
        val bucket = ProfitBucket()
        bucket.addItem("REVENANT_FLESH", "Revenant Flesh", 10, 1L)
        bucket.addItem("REVENANT_FLESH", "Revenant Flesh", -4, 2L)
        assertEquals(6L, bucket.items["REVENANT_FLESH"]!!.amount)
        bucket.addItem("REVENANT_FLESH", "Revenant Flesh", -6, 3L)
        assertTrue(bucket.items.isEmpty())
        assertEquals(1L, bucket.startedAt)
        bucket.addCost(100, 4L)
        assertFalse(bucket.isEmpty())
    }

    @Test
    fun csvQuotesAndNumbers() {
        assertEquals("plain", ProfitCsv.cell("plain"))
        assertEquals("\"a, b\"", ProfitCsv.cell("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", ProfitCsv.cell("say \"hi\""))
        assertEquals("1500", ProfitCsv.number(1500.0))
        assertEquals("12.35", ProfitCsv.number(12.345))
        assertEquals("", ProfitCsv.number(Double.NaN))

        val row = ProfitCsv.logRow(0L, ZoneOffset.UTC, "kiwi", "Revenant Horror", "REVENANT_FLESH", "Revenant Flesh", 3, 2.5)
        assertEquals("1970-01-01 00:00:00,kiwi,Revenant Horror,REVENANT_FLESH,Revenant Flesh,3,2.50,7.50", row)
    }

    @Test
    fun summaryAddsItemsCoinsAndCosts() {
        val bucket = ProfitBucket()
        bucket.addItem("A", "Item A", 2, 1L)
        bucket.addItem("B", "Item B", 1, 1L)
        bucket.items["B"]!!.hidden = true
        bucket.addItem("C", "No Price", 5, 1L)
        bucket.addCoins(1_000, 1L)
        bucket.addCost(500, 1L)
        bucket.activeMs = 1_800_000L
        val prices = mapOf("A" to 100.0, "B" to 1_000_000.0)
        val rows = ProfitCsv.summaryRows("kiwi", "Fishing", "session", bucket) { prices[it] ?: Double.NaN }
        // 2 x 100 + 1,000 coins - 500 costs; the hidden and the unpriced item don't count.
        assertEquals("kiwi,Fishing,session,TOTAL,Total Profit,,,700,0,0.50,1400", rows.last())
        assertEquals("kiwi,Fishing,session,C,No Price,5,,,,,", rows[2])
    }
}
