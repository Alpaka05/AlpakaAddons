package net.alpaka.addons.features.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The Farming HUD's blocks per second, from the crops the client breaks. */
class FarmingBpsTest {

    @BeforeEach
    fun reset() = FarmingTracker.resetBps()

    /** Breaks one crop every [stepMs] for [count] crops, starting at [start]; returns the last time. */
    private fun farm(start: Long, count: Int, stepMs: Long): Long {
        var now = start
        repeat(count) {
            FarmingTracker.onCropBroken(now)
            now += stepMs
        }
        return now - stepMs
    }

    @Test
    fun twentyCropsASecondIsTwentyBps() {
        val last = farm(1_000_000L, 401, 50L)
        assertEquals(20.0, FarmingTracker.blocksPerSecond(last)!!, 1e-9)
    }

    @Test
    fun tooLittleFarmingSaysNothingYet() {
        val last = farm(1_000_000L, 20, 50L)
        assertNull(FarmingTracker.blocksPerSecond(last))
    }

    @Test
    fun aPauseIsLeftOut() {
        var last = farm(1_000_000L, 201, 50L)
        // Thirty seconds at a pest, then on at the same speed.
        last = farm(last + 30_000L, 201, 50L)
        assertEquals(20.0, FarmingTracker.blocksPerSecond(last)!!, 0.1)
    }

    @Test
    fun aLongBreakStartsOver() {
        val last = farm(1_000_000L, 401, 50L)
        assertNull(FarmingTracker.blocksPerSecond(last + 6 * 60_000L))
        val later = farm(last + 6 * 60_000L, 201, 100L)
        assertEquals(10.0, FarmingTracker.blocksPerSecond(later)!!, 1e-9)
    }
}
