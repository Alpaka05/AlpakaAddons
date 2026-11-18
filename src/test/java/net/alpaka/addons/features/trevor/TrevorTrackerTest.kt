package net.alpaka.addons.features.trevor

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrevorTrackerTest {

    @BeforeEach
    fun start() {
        TrevorTracker.persist = false
        TrevorTracker.clearForTest()
    }

    @AfterEach
    fun end() {
        TrevorTracker.clearForTest()
        TrevorTracker.persist = true
    }

    @Test
    fun readsTheAcceptLine() {
        assertEquals(
            TrevorParse.Accepted("Trackable", "Glowing Mushroom Cave"),
            TrevorParse.parse("[NPC] Trevor: You can find your TRACKABLE animal near the Glowing Mushroom Cave."),
        )
    }

    @Test
    fun readsBothRewardLines() {
        assertEquals(TrevorParse.Killed(3, false), TrevorParse.parse("Killing the animal rewarded you 3 pelts."))
        assertEquals(TrevorParse.Killed(5, true), TrevorParse.parse("Your mob died randomly, you are rewarded 5 pelts."))
        assertEquals(TrevorParse.OutOfTime, TrevorParse.parse("You ran out of time and the animal disappeared!"))
    }

    @Test
    fun playersTypingTheLineDoNotCount() {
        assertNull(TrevorParse.parse("[MVP+] Someone: Killing the animal rewarded you 3 pelts."))
        assertNull(TrevorParse.parse("Someone: [NPC] Trevor: You can find your TRACKABLE animal near the Oasis."))
    }

    @Test
    fun readsSidebarPelts() {
        assertEquals(1284, TrevorParse.sidebarPelts(listOf("Purse: 5", "Pelts: 1,284")))
        assertNull(TrevorParse.sidebarPelts(listOf("Purse: 5")))
    }

    @Test
    fun recognisesTrapperHealth() {
        assertTrue(TrevorParse.isAnimalHealth(500f))
        assertTrue(TrevorParse.isAnimalHealth(1000f)) // 500 with Derpy, or 1000 without
        assertFalse(TrevorParse.isAnimalHealth(10f))
    }

    @Test
    fun countsHuntsByKindWithPeltsAndTime() {
        TrevorTracker.onEvent(TrevorParse.Accepted("Trackable", "Oasis"), 1_000L)
        assertTrue(TrevorTracker.huntActive)
        assertEquals(20_000L, TrevorTracker.nextTaskInMs(1_000L))
        TrevorTracker.setTargetForTest(TrevorTracker.Animal.COW)
        TrevorTracker.onEvent(TrevorParse.Killed(4, false), 61_000L)

        assertFalse(TrevorTracker.huntActive)
        assertEquals(1, TrevorTracker.kills(TrevorTracker.Animal.COW))
        assertEquals(4L, TrevorTracker.stats.pelts)
        assertEquals(60_000L, TrevorTracker.averageHuntMs())
        assertEquals(0L, TrevorTracker.nextTaskInMs(61_000L))

        // An animal never seen still counts, as Unknown.
        TrevorTracker.onEvent(TrevorParse.Accepted("Elusive", "Desert Mountain"), 100_000L)
        TrevorTracker.onEvent(TrevorParse.Killed(2, true), 220_000L)
        assertEquals(1, TrevorTracker.kills(TrevorTracker.Animal.UNKNOWN))
        assertEquals(2, TrevorTracker.totalKills())
        assertEquals(90_000L, TrevorTracker.averageHuntMs())
    }

    @Test
    fun runningOutOfTimeCountsNothing() {
        TrevorTracker.onEvent(TrevorParse.Accepted("Trackable", "Oasis"), 0L)
        TrevorTracker.onEvent(TrevorParse.OutOfTime, 600_000L)
        assertFalse(TrevorTracker.huntActive)
        assertEquals(0, TrevorTracker.totalKills())
    }
}
