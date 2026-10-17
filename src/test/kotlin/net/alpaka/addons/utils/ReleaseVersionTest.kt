package net.alpaka.addons.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The order the update check relies on, from the versions builds and tags actually carry. */
class ReleaseVersionTest {
    private fun v(s: String) = ReleaseVersion.parse(s)!!

    @Test
    fun releasesAndPreReleasesSortInOrder() {
        val ordered = listOf(
            "1.3.0", "1.3.1", "1.10.0", "2.0.0-alpha", "2.0.0-beta.1", "2.0.0-beta.2", "2.0.0-beta.10",
            "2.0.0-rc.1", "2.0.0", "2.0.1",
        ).map(::v)
        for (i in 1 until ordered.size) assertTrue(ordered[i - 1] < ordered[i], "${ordered[i - 1]} < ${ordered[i]} at $i")
    }

    @Test
    fun theBuildPartIsIgnored() {
        assertEquals(0, v("1.3.0+134.0de331d").compareTo(v("1.3.0")))
        assertEquals(0, v("2.0.0-alpha+3.a1b2c3d.dirty").compareTo(v("2.0.0-alpha")))
        assertEquals(0, v("v1.3.0").compareTo(v("1.3.0")))
    }

    @Test
    fun knowsAPreRelease() {
        assertTrue(v("2.0.0-alpha+3.a1b2c3d").isPreRelease)
        assertTrue(v("2.0.0-beta.1").isPreRelease)
        assertFalse(v("1.3.0+134.0de331d").isPreRelease)
    }

    @Test
    fun rejectsWhatIsNoVersion() {
        assertNull(ReleaseVersion.parse("unknown"))
        assertNull(ReleaseVersion.parse("1.x"))
        assertNull(ReleaseVersion.parse("2.0.0-"))
        assertNull(ReleaseVersion.parse("2.0.0-beta..1"))
    }
}
