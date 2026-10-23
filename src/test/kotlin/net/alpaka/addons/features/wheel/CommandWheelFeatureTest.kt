package net.alpaka.addons.features.wheel

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** When a tick opens the command wheel. */
class CommandWheelFeatureTest {

    @Test
    fun holdModeOpensOnAPressWhileTheKeyIsDown() {
        assertTrue(CommandWheelFeature.opens(pressed = true, toggleMode = false, keyDown = true))
    }

    @Test
    fun holdModeDoesNotReopenOnARestoredKeyState() {
        // The wheel was closed by a click with the key still held; the mouse grab set the key down
        // again without a press.
        assertFalse(CommandWheelFeature.opens(pressed = false, toggleMode = false, keyDown = true))
    }

    @Test
    fun holdModeIgnoresATapAlreadyLetGo() {
        assertFalse(CommandWheelFeature.opens(pressed = true, toggleMode = false, keyDown = false))
    }

    @Test
    fun toggleModeOpensOnAnyPress() {
        assertTrue(CommandWheelFeature.opens(pressed = true, toggleMode = true, keyDown = false))
        assertTrue(CommandWheelFeature.opens(pressed = true, toggleMode = true, keyDown = true))
        assertFalse(CommandWheelFeature.opens(pressed = false, toggleMode = true, keyDown = true))
    }
}
