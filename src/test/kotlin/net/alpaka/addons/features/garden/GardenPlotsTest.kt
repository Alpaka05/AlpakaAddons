package net.alpaka.addons.features.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GardenPlotsTest {

    @Test
    fun squaresFollowTheGrid() {
        // The Barn sits on the origin, plot centers are 96 blocks apart.
        assertEquals(GardenPlots.BARN, GardenPlots.index(0.0, 0.0))
        assertEquals(0, GardenPlots.index(-192.0, -192.0))
        assertEquals(24, GardenPlots.index(192.0, 192.0))
        // One plot east of the Barn, and one north.
        assertEquals(13, GardenPlots.index(96.0, 0.0))
        assertEquals(7, GardenPlots.index(0.0, -96.0))
        // The outer edge still belongs to the last plot; beyond it is off the grid.
        assertEquals(24, GardenPlots.index(240.0, 240.0))
        assertNull(GardenPlots.index(241.0, 0.0))
    }
}
