package net.alpaka.addons.features.snow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SnowOverlayRendererTest {

    @Test
    fun `amount 1 keeps the original 105 flakes`() {
        assertEquals(105, SnowOverlayRenderer.particleCount(1.0f))
    }

    @Test
    fun `amount scales the count`() {
        assertEquals(53, SnowOverlayRenderer.particleCount(0.5f))
        assertEquals(210, SnowOverlayRenderer.particleCount(2.0f))
        assertEquals(525, SnowOverlayRenderer.particleCount(5.0f))
    }

    @Test
    fun `out of range values from a hand-edited config are clamped`() {
        assertEquals(11, SnowOverlayRenderer.particleCount(0.0f))
        assertEquals(525, SnowOverlayRenderer.particleCount(100.0f))
        assertEquals(105, SnowOverlayRenderer.particleCount(Float.NaN))
    }
}
