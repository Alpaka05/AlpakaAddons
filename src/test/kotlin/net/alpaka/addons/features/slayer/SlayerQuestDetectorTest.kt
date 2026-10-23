package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.AfterEach
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
    private val nextQuest = "0/3,000 Combat XP"

    /** The detector's clock, moved by hand instead of sleeping. Far from 0, as a real clock is. */
    private var now = 1_000_000L

    @BeforeEach
    fun reset() {
        d.clock = { now }
        d.resetForTest()
        d.seeForTest(type)
    }

    @AfterEach
    fun realClock() {
        d.clock = { System.currentTimeMillis() }
    }

    private fun advance(ms: Long) {
        now += ms
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
    fun slainIsAStatedKill() {
        sample(grinding)
        sample(fight)
        d.consumeSpawn()
        sample(slain)
        assertEquals(type, d.consumeKill())
        assertTrue(d.killStated)
    }

    @Test
    fun fightVanishingIsOnlyAnInferredKill() {
        // The quest lines going missing mid-fight looks exactly like this, so the kill is counted
        // but must not vouch for the fight's end.
        sample(grinding)
        sample(fight)
        d.consumeSpawn()
        sample("")
        advance(1_100)
        assertEquals(type, d.consumeKill())
        assertFalse(d.killStated)
    }

    @Test
    fun chatConfirmsAnInferredKill() {
        sample(grinding)
        sample(fight)
        d.consumeSpawn()
        sample(nextQuest)
        d.onChatKill(type)
        assertEquals(type, d.consumeKill())
        assertTrue(d.killStated)
    }

    @Test
    fun autoRestartedQuestCountsTheKill() {
        sample(grinding)
        sample(fight)
        sample(nextQuest)
        // Inferred, so held back for a veto first - but it is there, which it used not to be.
        assertNull(d.consumeKill())
        advance(1_100)
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
        advance(1_100)
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

    /** One whole boss: grinding, the fight, "Boss slain!", then the next quest a moment later. */
    private fun killABossNormally() {
        sample(grinding)
        sample(fight)
        d.consumeSpawn()
        sample(slain)
        assertEquals(type, d.consumeKill())
        advance(500)
        sample(nextQuest)
    }

    @Test
    fun aOneTickBossAfterANormalKillCounts() {
        killABossNormally()
        advance(20_000)
        sample(slain)
        assertEquals(type, d.consumeKill())
        assertFalse(d.killTimed)
    }

    @Test
    fun aOneTickBossReportedByChatAfterANormalKillCounts() {
        killABossNormally()
        advance(20_000)
        d.onChatKill(type)
        assertEquals(type, d.consumeKill())
    }

    @Test
    fun aOneTickBossReportedByChatFirstCountsOnce() {
        killABossNormally()
        advance(20_000)
        d.onChatKill(type)
        advance(200)
        sample(slain)
        assertEquals(type, d.consumeKill())
        assertNull(d.consumeKill())
    }

    @Test
    fun aOneTickBossReportedByTheSidebarFirstCountsOnce() {
        killABossNormally()
        advance(20_000)
        sample(slain)
        advance(200)
        d.onChatKill(type)
        d.onChatKill(type)
        assertEquals(type, d.consumeKill())
        assertNull(d.consumeKill())
    }

    @Test
    fun oneTickBossesInARowAllCount() {
        killABossNormally()
        repeat(3) {
            advance(20_000)
            sample(slain)
            assertEquals(type, d.consumeKill())
            advance(500)
            sample(nextQuest)
        }
    }

    @Test
    fun chatAfterAnAutoRestartedQuestIsTheSameBoss() {
        sample(grinding)
        sample(fight)
        d.consumeSpawn()
        sample(nextQuest)
        advance(1_100)
        assertEquals(type, d.consumeKill())
        advance(800)
        d.onChatKill(type)
        assertNull(d.consumeKill())
    }

    private val questLine = "Inferno Demonlord IV"
    private val sidebarWithoutQuest = listOf("SKYBLOCK", "Smoldering Tomb", "Purse: 1,000")

    @Test
    fun anEmptySidebarDoesNotFallBackToTheTabList() {
        // Between servers the sidebar is gone while the tab list may still be the old server's.
        var reads = 0
        val lines = d.linesToScan(10_000L, emptyList()) { reads++; listOf(questLine, fight) }
        assertTrue(lines.isEmpty())
        assertEquals(0, reads)
    }

    @Test
    fun aWorldChangeForgetsTheTabList() {
        d.linesToScan(10_000L, sidebarWithoutQuest) { listOf(questLine, fight) }
        d.resetForLevelChange()
        // Within the second the snapshot would otherwise be kept: the new world's tab list is read.
        val lines = d.linesToScan(10_100L, sidebarWithoutQuest) { listOf("Alpaka05") }
        assertEquals(sidebarWithoutQuest, lines)
    }

    @Test
    fun theTabListIsReadAfreshWhenTheQuestLeavesTheSidebar() {
        // No quest anywhere: the tab list is read, and then left alone for a second.
        var reads = 0
        d.linesToScan(10_000L, sidebarWithoutQuest) { reads++; emptyList() }
        d.linesToScan(10_250L, sidebarWithoutQuest) { reads++; emptyList() }
        assertEquals(1, reads)

        // The quest on the sidebar, then gone from it: the tab list is read right away.
        d.linesToScan(10_500L, listOf(questLine, grinding)) { reads++; emptyList() }
        val lines = d.linesToScan(10_750L, sidebarWithoutQuest) { reads++; listOf(questLine, fight) }
        assertEquals(2, reads)
        assertEquals(listOf(questLine, fight), lines)
    }
}
