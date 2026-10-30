package net.alpaka.addons.features.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Farming HUD's readers, on lines as Hypixel writes them (colors already stripped). */
class FarmingParseTest {

    private val tab = listOf(
        "Info",
        "Pests:",
        " Alive: 3",
        " Plots: 19",
        " Spray: None",
        " Repellent: None",
        " Bonus: INACTIVE",
        " Cooldown: 4m 32s",
        "",
        "Crop Milestones:",
        " Mushroom 25: 48.6%",
        "Jacob's Contest:",
        " Starts In: 29m",
        " ○ Cactus",
        "  Carrot",
        " ○ Moonflower",
        "Skills: Farming 60: MAX",
    )

    @Test
    fun readsTheActionBarXp() {
        val percent = FarmingParse.xpGain("1000/1000❤     +12.5 Farming (45.3%)     500/500✎ Mana")!!
        assertEquals(12.5, percent.gained)
        assertEquals("45.3%", percent.progress)
        assertNull(percent.current)

        val fraction = FarmingParse.xpGain("+1,204.8 Farming (123,456.5/200,000)")!!
        assertEquals(1204.8, fraction.gained)
        assertEquals(123456.5, fraction.current)
        assertEquals("200,000", fraction.needed)

        assertNull(FarmingParse.xpGain("+3 Mining (12%)"))
    }

    @Test
    fun readsTheGardenWidgets() {
        val pests = FarmingParse.widget(tab, "Pests:")!!
        assertEquals("4m 32s", FarmingParse.field(pests, "Cooldown"))
        assertEquals("None", FarmingParse.field(pests, "Repellent"))
        assertEquals(
            FarmingParse.Milestone("Mushroom", 25, 48.6),
            FarmingParse.milestone(FarmingParse.widget(tab, "Crop Milestones:")!!),
        )
        val contest = FarmingParse.widget(tab, "Jacob's Contest:")!!
        assertEquals("29m", FarmingParse.field(contest, "Starts In"))
        assertEquals(listOf("Cactus", "Carrot", "Moonflower"), FarmingParse.contestCrops(contest))
        assertNull(FarmingParse.widget(tab, "Visitors:"))
    }

    @Test
    fun aMaxedMilestoneHasNoPercentage() {
        val maxed = FarmingParse.milestone(listOf(" Potato 46: MAX"))!!
        assertTrue(maxed.maxed)
        assertEquals(46, maxed.tier)
    }

    @Test
    fun readsTheRepellentMessage() {
        assertEquals(
            FarmingParse.Repellent(4, 60),
            FarmingParse.repellent("YUM! Pests will now spawn 4x less while you break crops for the next 60m!"),
        )
        assertNull(FarmingParse.repellent("YUM! You ate a cookie."))
    }

    @Test
    fun readsTheCropMilestonesMenu() {
        val lore = listOf(
            "Harvest Mushroom on your Garden",
            "to increase your Mushroom tier!",
            "",
            "Total: 3,807,695",
            "",
            "Progress to Tier 26: 48.7%",
            "                    389,285/800k",
            "",
            "Progress to Tier 46: 18.8%",
            "                    3,807,695/20.2M",
        )
        assertEquals(
            FarmingParse.MenuMilestone("Mushroom", 25, 389_285L, 800_000L, 48.7),
            FarmingParse.menuMilestone("Mushroom 25", lore),
        )
        assertNull(FarmingParse.menuMilestone("Go Back", listOf("To Desk")))
        assertEquals(20_200_000L, FarmingParse.amount("20.2M"))
    }

    @Test
    fun recognisesSpawnedPests() {
        assertTrue(FarmingParse.pestSpawned("YUCK! 3 ൠ Pest have spawned in Plot - 19!"))
        assertTrue(FarmingParse.pestSpawned("GROSS! A ൠ Pest has spawned in The Barn!"))
        assertFalse(FarmingParse.pestSpawned("From [MVP+] Someone: YUCK! 6 ൠ Pest have spawned in Plot - 7!"))
    }

    @Test
    fun readsDurations() {
        assertEquals(4_320_000L, FarmingParse.durationMs("1h 12m"))
        assertEquals(118_000L, FarmingParse.durationMs("1m 58s"))
        assertNull(FarmingParse.durationMs("READY"))
    }
}
