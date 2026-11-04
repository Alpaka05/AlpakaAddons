package net.alpaka.addons.features.garden

import net.alpaka.addons.features.profile.Levels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.math.floor

class FarmingTrackerXpTest {

    @BeforeEach
    fun reset() = FarmingTracker.resetXp()

    /** Feeds one action bar a second while farming [xpPerSecond], shown as Hypixel's two-decimal percentage. */
    private class PercentFarmer(var level: Int, var xp: Double, var tabLevel: Int? = level) {
        var now = 1_000_000L

        fun farm(seconds: Int, xpPerSecond: Double) {
            repeat(seconds) {
                now += 1_000
                xp += xpPerSecond
                val need = Levels.skillXp(level + 1, 0.0) - Levels.skillXp(level, 0.0)
                var into = xp - Levels.skillXp(level, 0.0)
                if (into >= need) {
                    level++
                    into -= need
                }
                val nextNeed = Levels.skillXp(level + 1, 0.0) - Levels.skillXp(level, 0.0)
                val percent = floor(into / nextNeed * 10_000) / 100
                val text = "1000/1000❤     +${xpPerSecond} Farming (${String.format(Locale.US, "%.2f", percent)}%)"
                FarmingTracker.onActionBar(text, now) { tabLevel }
            }
        }
    }

    @Test
    fun percentageAtHighLevelGivesTheRealRate() {
        // Level 50 needs 4.3M XP, so 0.01 % is 430 XP: most action bars show the same percentage.
        val farmer = PercentFarmer(level = 50, xp = Levels.skillXp(50, 12.0))
        farmer.farm(300, 100.0)
        val rate = FarmingTracker.xpPerHour(farmer.now)!!
        assertEquals(360_000.0, rate, 360_000.0 * 0.05)
    }

    @Test
    fun breaksAreLeftOut() {
        val farmer = PercentFarmer(level = 30, xp = Levels.skillXp(30, 40.0))
        farmer.farm(120, 50.0)
        farmer.now += 120_000 // two minutes away from the crops, no action bars
        farmer.farm(120, 50.0)
        assertEquals(180_000.0, FarmingTracker.xpPerHour(farmer.now)!!, 180_000.0 * 0.05)
    }

    @Test
    fun aLevelUpWhileTheTabStillShowsTheOldLevel() {
        val farmer = PercentFarmer(level = 40, xp = Levels.skillXp(41, 0.0) - 30_000.0, tabLevel = 40)
        farmer.farm(120, 500.0)
        assertEquals(41, farmer.level)
        // The tab list still says 40 all along.
        assertEquals(1_800_000.0, FarmingTracker.xpPerHour(farmer.now)!!, 1_800_000.0 * 0.05)
    }

    @Test
    fun withoutTheSkillsWidgetThereIsNoRate() {
        val farmer = PercentFarmer(level = 50, xp = Levels.skillXp(50, 12.0), tabLevel = null)
        farmer.farm(60, 100.0)
        assertTrue(FarmingTracker.xpNeedsSkillWidget)
        assertNull(FarmingTracker.xpPerHour(farmer.now))
    }

    @Test
    fun exactFiguresCountEveryGainAcrossALevelUp() {
        var now = 1_000_000L
        var current = 190_000.0
        var needed = 200_000.0
        repeat(60) {
            now += 1_000
            current += 1_000.0
            if (current >= needed) {
                current -= needed
                needed = 300_000.0
            }
            val shownNeeded = String.format(Locale.US, "%,.0f", needed)
            FarmingTracker.onActionBar("+1,000 Farming (${String.format(Locale.US, "%,.1f", current)}/$shownNeeded)", now) { null }
        }
        assertFalse(FarmingTracker.xpNeedsSkillWidget)
        // 59 gains measured over 59 seconds: the first only starts the count.
        assertEquals(3_600_000.0, FarmingTracker.xpPerHour(now)!!, 1.0)
    }

    @Test
    fun theRateIsGoneAfterFiveMinutesWithoutFarming() {
        val farmer = PercentFarmer(level = 30, xp = Levels.skillXp(30, 40.0))
        farmer.farm(60, 50.0)
        assertTrue(FarmingTracker.xpPerHour(farmer.now) != null)
        assertNull(FarmingTracker.xpPerHour(farmer.now + 5 * 60_000L + 1))
    }
}
