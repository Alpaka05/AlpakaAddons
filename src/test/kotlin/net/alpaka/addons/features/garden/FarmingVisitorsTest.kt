package net.alpaka.addons.features.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Farming HUD's Visitors line: the tab list's visitor count and "Next Visitor:" timer. */
class FarmingVisitorsTest {

    @Test
    fun readsTheVisitorCountAndTimer() {
        val tab = listOf(
            "Visitors: (3)",
            " Lazy Miner",
            " Jacob",
            " Andrew",
            "Info",
            " Next Visitor: 4m 12s",
        )
        assertEquals(3, FarmingParse.visitorCount(tab))
        assertEquals("4m 12s", FarmingParse.nextVisitor(tab))
        // A queue grown by Max Occupancy, should Hypixel ever show its size.
        assertEquals(10, FarmingParse.visitorCount(listOf("Visitors: (10/10)")))
        assertNull(FarmingParse.visitorCount(listOf("Pests:", " Alive: 3")))
        assertNull(FarmingParse.nextVisitor(listOf("Pests:", " Alive: 3")))
        assertEquals("Queue Full!", FarmingParse.nextVisitor(listOf(" Next Visitor: Queue Full!")))
    }

    @Test
    fun countsDownATimeInSecondsAndFollowsTheTabWhenItRunsAhead() {
        FarmingTracker.onNextVisitor("4m 12s", 0L)
        assertEquals(252_000L, FarmingTracker.nextVisitorLeftMs(0L))
        assertEquals(250_000L, FarmingTracker.nextVisitorLeftMs(2_000L))
        // Rounding of the shown seconds does not move the countdown.
        FarmingTracker.onNextVisitor("4m 10s", 2_500L)
        assertEquals(250_000L, FarmingTracker.nextVisitorLeftMs(2_000L))
        // Breaking crops took 20 seconds off: the countdown follows at once.
        FarmingTracker.onNextVisitor("3m 50s", 3_000L)
        assertEquals(230_000L, FarmingTracker.nextVisitorLeftMs(3_000L))
        assertNull(FarmingTracker.visitorTimeText)
    }

    @Test
    fun aTimeInWholeMinutesIsShownAsTheTabSaysIt() {
        FarmingTracker.onNextVisitor("11m", 0L)
        assertNull(FarmingTracker.nextVisitorLeftMs(0L))
        assertEquals("11m", FarmingTracker.visitorTimeText)
    }

    @Test
    fun aFullQueueOrLockedVisitorsHaveNoTimer() {
        FarmingTracker.onNextVisitor("Queue Full!", 0L)
        assertTrue(FarmingTracker.visitorQueueFull)
        assertNull(FarmingTracker.nextVisitorLeftMs(0L))
        assertNull(FarmingTracker.visitorTimeText)

        FarmingTracker.onNextVisitor("Not Unlocked!", 0L)
        assertTrue(FarmingTracker.visitorsLocked)
        assertFalse(FarmingTracker.visitorQueueFull)

        FarmingTracker.onNextVisitor(null, 0L)
        assertFalse(FarmingTracker.visitorsLocked)
        assertNull(FarmingTracker.nextVisitorLeftMs(0L))
    }
}
