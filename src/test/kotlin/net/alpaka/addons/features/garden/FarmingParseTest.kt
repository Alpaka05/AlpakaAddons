package net.alpaka.addons.features.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Farming HUD's readers, on lines as Hypixel writes them (colors already stripped). */
class FarmingParseTest {

    private val tab = listOf(
        "Info",
        "Pests:",
        " Alive: 3",
        " Plots: 4, 12",
        " Cooldown: 1m 58s",
        "",
        "Crop Milestones:",
        " Cocoa Beans 31: 68.4%",
        "Jacob's Contest: 12m",
        " ○ Pumpkin",
        "  Mushroom",
        " ○ Wheat",
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
        assertEquals("1m 58s", FarmingParse.field(FarmingParse.widget(tab, "Pests:")!!, "Cooldown"))
        assertEquals(
            FarmingParse.Milestone("Cocoa Beans", 31, 68.4),
            FarmingParse.milestone(FarmingParse.widget(tab, "Crop Milestones:")!!),
        )
        assertEquals("12m", FarmingParse.widgetTitleValue(tab, "Jacob's Contest:"))
        assertEquals(listOf("Pumpkin", "Mushroom", "Wheat"), FarmingParse.contestCrops(FarmingParse.widget(tab, "Jacob's Contest:")!!))
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
    fun readsDurations() {
        assertEquals(4_320_000L, FarmingParse.durationMs("1h 12m"))
        assertEquals(118_000L, FarmingParse.durationMs("1m 58s"))
        assertNull(FarmingParse.durationMs("READY"))
    }
}
