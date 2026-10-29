package net.alpaka.addons.config

import net.alpaka.addons.features.wheel.CommandWheelPages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * A profile's own wheel next to the shared one: where it starts, and that the two stay apart.
 *
 * In the config package because loading a settings file into a temporary folder is only open to it.
 */
class CommandWheelPagesTest {
    @TempDir
    lateinit var dir: Path

    private val kiwi = CommandWheelPages.profileKey("acc", "kiwi")

    @BeforeEach
    fun freshConfig() {
        AlpakaConfig.load(AlpakaConfig.ConfigStore(dir.resolve("alpaka.json").toFile(), dir.resolve("alpaka.json.bak").toFile()))
    }

    @Test
    fun aProfileWithoutItsOwnWheelReadsTheSharedOne() {
        CommandWheelPages.add(null, "/warp crimson", null)
        assertFalse(CommandWheelPages.hasOwnWheel(kiwi))
        assertEquals(CommandWheelPages.pages(null), CommandWheelPages.pages(kiwi))
    }

    @Test
    fun aNewProfileWheelStartsAsACopyAndThenGoesItsOwnWay() {
        CommandWheelPages.add(null, "/warp crimson", null)
        CommandWheelPages.createProfileWheel(kiwi)
        assertTrue(CommandWheelPages.hasOwnWheel(kiwi))
        assertTrue(CommandWheelPages.contains(kiwi, "/warp crimson"))

        CommandWheelPages.remove(kiwi, 0, CommandWheelPages.pages(kiwi)[0].indexOf("/warp crimson"))
        CommandWheelPages.add(kiwi, "/warp hub", null)

        assertFalse(CommandWheelPages.contains(kiwi, "/warp crimson"))
        assertTrue(CommandWheelPages.contains(null, "/warp crimson"))
        assertFalse(CommandWheelPages.contains(null, "/warp hub"))
    }

    @Test
    fun resetAndRemoveOnlyTouchTheProfilesWheel() {
        CommandWheelPages.add(null, "/warp crimson", null)
        CommandWheelPages.createProfileWheel(kiwi)
        CommandWheelPages.reset(kiwi)
        assertEquals(listOf(CommandWheelPages.DEFAULTS), CommandWheelPages.pages(kiwi))
        assertTrue(CommandWheelPages.contains(null, "/warp crimson"))

        CommandWheelPages.removeProfileWheel(kiwi)
        assertFalse(CommandWheelPages.hasOwnWheel(kiwi))
        assertTrue(CommandWheelPages.contains(kiwi, "/warp crimson"))
    }

    @Test
    fun profileWheelsSurviveASaveAndLoad() {
        CommandWheelPages.createProfileWheel(kiwi)
        CommandWheelPages.add(kiwi, "/warp hub", null)
        freshConfig()
        assertTrue(CommandWheelPages.contains(kiwi, "/warp hub"))
    }

    @Test
    fun theNameIsShownCapitalized() {
        assertEquals("Kiwi", CommandWheelPages.profileName(kiwi))
    }
}
