package net.alpaka.addons.features.garden

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class PestTrailGuessTest {

    @Test
    fun fitRecoversACubic() {
        val values = (0..9).map { t -> 2.0 - 1.5 * t + 0.25 * t * t - 0.01 * t * t * t }
        val c = PestTrailGuess.fit(values)
        assertNotNull(c)
        assertArrayEquals(doubleArrayOf(2.0, -1.5, 0.25, -0.01), c, 1e-7)
    }

    @Test
    fun needsFourPointsBeforeItGuesses() {
        val guess = PestTrailGuess()
        for (i in 0 until 3) guess.tryAdd(i * 0.5, 70.0, 0.0)
        assertNull(guess.solve())
        guess.tryAdd(1.5, 70.0, 0.0)
        assertNotNull(guess.solve())
    }

    @Test
    fun skipsRepeatsAndStrayParticles() {
        val guess = PestTrailGuess()
        assertTrue(guess.tryAdd(0.0, 70.0, 0.0))
        assertTrue(guess.tryAdd(0.5, 70.0, 0.0))
        // Hypixel sends a position again, sometimes after another one.
        assertFalse(guess.tryAdd(0.0, 70.0, 0.0))
        assertFalse(guess.tryAdd(0.5, 70.0, 0.0))
        // Too far from the last point to belong to the trail.
        assertFalse(guess.tryAdd(10.0, 70.0, 0.0))
        assertEquals(2, guess.size)
    }

    @Test
    fun aLevelTrailLeadsStraightOn() {
        // Leaving level, the hidden pitch has sin p = 0.75, so the control point sits sqrt(7) out.
        assertEquals(sqrt(7.0), PestTrailGuess.controlPointDistance(1.0, 0.0, 0.0), 1e-6)

        val guess = PestTrailGuess()
        for (i in 0 until 8) guess.tryAdd(100.0 + i * 0.5, 70.0, -20.0)
        val (x, y, z) = guess.solve()!!
        // t = 3 * sqrt(7) / 0.5 steps of half a block ahead of the first particle.
        assertEquals(100.0 + 3 * sqrt(7.0), x, 1e-6)
        assertEquals(70.0, y, 1e-6)
        assertEquals(-20.0, z, 1e-6)
    }
}
