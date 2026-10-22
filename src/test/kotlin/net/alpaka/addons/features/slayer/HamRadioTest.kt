package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Reading the channel count from the Blazetekk Ham Radio's lore. */
class HamRadioTest {

    @Test
    fun readsTheCountFromTheNearbyLine() {
        val lore = listOf("Your channel: Infrared", "Nearby: 2 enjoyers", "", "LEGENDARY")
        assertEquals(2, HamRadio.parseNearby(lore))
    }

    @Test
    fun readsCrowdedChannels() {
        assertEquals(3, HamRadio.parseNearby(listOf("Nearby: 3 enjoyers")))
    }

    @Test
    fun nearbyLineWithoutANumberMeansAlone() {
        assertEquals(1, HamRadio.parseNearby(listOf("Nearby: Nobody")))
    }

    @Test
    fun noNearbyLineMeansNoCount() {
        assertNull(HamRadio.parseNearby(listOf("Your channel: Infrared", "LEGENDARY")))
    }
}
