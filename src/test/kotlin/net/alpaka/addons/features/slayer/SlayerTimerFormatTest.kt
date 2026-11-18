package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/** The timer's text without a Formatter must read exactly as the String.format it replaced. */
class SlayerTimerFormatTest {
    private fun reference(ms: Long): String {
        if (ms < 60_000L) return String.format(Locale.ROOT, "%.1fs", ms / 1000.0)
        val totalSeconds = ms / 1000L
        return String.format(Locale.ROOT, "%dm %02ds", totalSeconds / 60L, totalSeconds % 60L)
    }

    @Test
    fun matchesStringFormatForEveryMillisecondUpToTenMinutes() {
        for (ms in -2_000L..600_000L) assertEquals(reference(ms), SlayerTimer.format(ms), "ms=$ms")
    }

    @Test
    fun equalKeysMeanEqualText() {
        var lastKey = Long.MIN_VALUE
        var lastText = ""
        for (ms in -2_000L..600_000L) {
            val key = SlayerTimer.formatKey(ms)
            val text = SlayerTimer.format(ms)
            if (key == lastKey) assertEquals(lastText, text, "ms=$ms")
            lastKey = key
            lastText = text
        }
    }
}
