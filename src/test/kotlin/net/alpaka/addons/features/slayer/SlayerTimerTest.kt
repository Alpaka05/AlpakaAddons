package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The boss timer's clock against the boss death the entity tracker saw. */
class SlayerTimerTest {
    private val type = SlayerType.BLAZE

    @BeforeEach
    fun reset() {
        SlayerBossEntityTracker.takeDeath()
        // The boss arrived 0.4s after the sidebar's "Slay the boss!", rising out of grinding.
        SlayerTimer.startForTest(type, tier = 4, sidebarStartMs = 10_000L, entityStartMs = 10_400L, verified = true)
    }

    private fun stop(killedAtMs: Long, stated: Boolean = true) =
        SlayerTimer.stopClock(type, killedAtMs, SlayerBossEntityTracker.takeDeath(), stated)

    @Test
    fun theBossDyingEndsTheTime() {
        SlayerBossEntityTracker.dieForTest(atMs = 40_000L, byHealth = true)
        val fight = stop(killedAtMs = 41_200L)
        assertEquals(29_600L, fight.reportedMs)
        assertEquals(40_000L, fight.entityDeathMs)
        assertNull(fight.notCounted)
    }

    @Test
    fun aHealthDeathVouchesForAKillTheSidebarOnlyInferred() {
        SlayerBossEntityTracker.dieForTest(atMs = 40_000L, byHealth = true)
        assertNull(stop(killedAtMs = 41_200L, stated = false).notCounted)
    }

    @Test
    fun aBossThatOnlyWentMissingEndsTheTimeButCannotVouchForIt() {
        SlayerBossEntityTracker.dieForTest(atMs = 40_000L, byHealth = false)
        val fight = stop(killedAtMs = 41_200L, stated = false)
        assertEquals(29_600L, fight.reportedMs)
        assertEquals("end not confirmed", fight.notCounted)
    }

    @Test
    fun withoutADeathTheKillEndsTheTime() {
        val fight = stop(killedAtMs = 41_200L)
        assertEquals(30_800L, fight.reportedMs)
        assertNull(fight.entityDeathMs)
        assertNull(fight.notCounted)
    }

    @Test
    fun aDeathLongBeforeTheKillIsNotThisFightsEnd() {
        SlayerBossEntityTracker.dieForTest(atMs = 30_000L, byHealth = true)
        val fight = stop(killedAtMs = 41_200L)
        assertNull(fight.entityDeathMs)
        assertEquals(30_800L, fight.reportedMs)
    }

    @Test
    fun takingTheDeathForgetsIt() {
        SlayerBossEntityTracker.dieForTest(atMs = 40_000L, byHealth = true)
        val death = SlayerBossEntityTracker.takeDeath()
        assertEquals(40_000L, death?.atMs)
        assertTrue(death!!.byHealth)
        assertNull(SlayerBossEntityTracker.takeDeath())
    }
}
