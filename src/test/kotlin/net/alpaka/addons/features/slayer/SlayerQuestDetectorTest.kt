package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The quest detector's state machine, fed sidebar samples directly. */
class SlayerQuestDetectorTest {
    private val d = SlayerQuestDetector
    private val type = SlayerType.BLAZE

    private val grinding = "1,200/3,000 Combat XP"
    private val fight = "Slay the boss!"
    private val slain = "Boss slain!"

    @BeforeEach
    fun reset() {
        d.resetForTest()
        d.seeForTest(type)
    }

    private fun sample(progress: String) = d.sampleForTest(progress, if (progress.isEmpty()) null else type)

    @Test
    fun grindToFightIsAVerifiedSpawn() {
        sample(grinding)
        sample(fight)
        assertEquals(type, d.consumeSpawn())
        assertTrue(d.spawnVerified)
    }

    @Test
    fun fightToSlainIsAStatedTimedKill() {
        sample(grinding)
        sample(fight)
        d.consumeSpawn()
        sample(slain)
        assertEquals(type, d.consumeKill())
        assertTrue(d.killTimed)
    }

    @Test
    fun autoRestartedQuestCountsTheKill() {
        sample(grinding)
        sample(fight)
        sample("0/3,000 Combat XP")
        // Inferred, so held back for a veto first - but it is there, which it used not to be.
        assertNull(d.consumeKill())
        Thread.sleep(1_100)
        assertEquals(type, d.consumeKill())
    }

    @Test
    fun bossBetweenTwoSamplesCountsButIsNotTimed() {
        sample(grinding)
        sample(slain)
        assertEquals(type, d.consumeKill())
        assertFalse(d.killTimed)
    }

    @Test
    fun chatAndSidebarReportTheBossOnce() {
        sample(grinding)
        sample(fight)
        d.onChatKill(type)
        sample(slain)
        assertEquals(type, d.consumeKill())
        assertNull(d.consumeKill())
    }

    @Test
    fun sidebarFlickerDuringTheFightIsNotAKill() {
        sample(grinding)
        sample(fight)
        assertEquals(type, d.consumeSpawn())
        sample("")
        sample(fight)
        Thread.sleep(1_100)
        assertNull(d.consumeKill())
        assertNull(d.consumeSpawn())
    }

    @Test
    fun fightFirstSeenFromNothingIsAnUnverifiedSpawn() {
        sample("")
        sample(fight)
        assertEquals(type, d.consumeSpawn())
        assertFalse(d.spawnVerified)
    }
}
