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

    @Test
    fun `other particle types scale from their own density`() {
        assertEquals(105, SnowOverlayRenderer.particleCount(1.0f, SnowOverlayRenderer.TYPE_SNOW))
        assertEquals(32, SnowOverlayRenderer.particleCount(1.0f, SnowOverlayRenderer.TYPE_FIREFLIES))
        assertEquals(84, SnowOverlayRenderer.particleCount(1.0f, SnowOverlayRenderer.TYPE_ASH))
        assertEquals(63, SnowOverlayRenderer.particleCount(1.0f, SnowOverlayRenderer.TYPE_CHERRY_PETALS))
        assertEquals(37, SnowOverlayRenderer.particleCount(1.0f, SnowOverlayRenderer.TYPE_LEAVES))
        assertEquals(32, SnowOverlayRenderer.particleCount(1.0f, SnowOverlayRenderer.TYPE_GLYPHS))
        assertEquals(126, SnowOverlayRenderer.particleCount(1.0f, SnowOverlayRenderer.TYPE_RAIN))
    }

    @Test
    fun `every type has a name and a density`() {
        assertEquals(7, SnowOverlayRenderer.TYPE_NAMES.size)
        for (type in SnowOverlayRenderer.TYPE_NAMES.indices) SnowOverlayRenderer.particleCount(1.0f, type)
    }

    @Test
    fun `an unknown type from a hand-edited config falls back to snow`() {
        assertEquals(SnowOverlayRenderer.TYPE_SNOW, SnowOverlayRenderer.clampType(-1))
        assertEquals(SnowOverlayRenderer.TYPE_SNOW, SnowOverlayRenderer.clampType(99))
        assertEquals(105, SnowOverlayRenderer.particleCount(1.0f, 99))
    }
}
