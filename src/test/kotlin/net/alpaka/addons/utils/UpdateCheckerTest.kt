package net.alpaka.addons.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Which release the update check offers, from GitHub's answer as it comes. */
class UpdateCheckerTest {

    private fun release(tag: String, vararg jars: String, draft: Boolean = false, prerelease: Boolean = false): String {
        val assets = jars.joinToString(",") { """{"name":"$it"}""" }
        return """{"tag_name":"$tag","html_url":"https://example.invalid/$tag","draft":$draft,""" +
            """"prerelease":$prerelease,"assets":[$assets]}"""
    }

    private fun newest(running: String, minecraft: String, vararg releases: String) =
        UpdateChecker.newest("[" + releases.joinToString(",") + "]", ReleaseVersion.parse(running)!!,
            UpdateChecker.jarSuffixes(minecraft))?.version

    @Test
    fun aHotfixGameLooksForItsOwnVersionThenTheMajorMinor() {
        assertEquals(listOf("-mc26.2.jar"), UpdateChecker.jarSuffixes("26.2"))
        assertEquals(listOf("-mc26.2.1.jar", "-mc26.2.jar"), UpdateChecker.jarSuffixes("26.2.1"))
        assertEquals(listOf("-mc26.2-rc1.jar", "-mc26.2.jar"), UpdateChecker.jarSuffixes("26.2-rc1"))
    }

    @Test
    fun theGameAndItsHotfixesBothFindTheReleaseJar() {
        val next = release("2.0.1", "alpaka-2.0.1-mc26.2.jar")
        assertEquals("2.0.1", newest("2.0.0", "26.2", next))
        assertEquals("2.0.1", newest("2.0.0", "26.2.1", next))
    }

    @Test
    fun aReleaseForAnotherMinecraftIsNotOffered() {
        assertNull(newest("2.0.0", "26.2", release("2.0.1", "alpaka-2.0.1-mc26.3.jar")))
        assertNull(newest("2.0.0", "26.2.1", release("2.0.1", "alpaka-2.0.1-mc26.1.2.jar")))
    }

    @Test
    fun versionsCompareByNumberNotByText() {
        assertEquals("1.10.0", newest("1.9.0", "26.2",
            release("1.9.5", "alpaka-1.9.5-mc26.2.jar"),
            release("1.10.0", "alpaka-1.10.0-mc26.2.jar")))
    }

    @Test
    fun draftsAreSkippedAndPreReleasesReachOnlyTesters() {
        val beta = release("2.0.0-beta.1", "alpaka-2.0.0-beta.1-mc26.2.jar", prerelease = true)
        assertNull(newest("1.4.0", "26.2", release("1.5.0", "alpaka-1.5.0-mc26.2.jar", draft = true), beta))
        assertEquals("2.0.0-beta.1", newest("2.0.0-alpha+3.a1b2c3d", "26.2.1", beta))
    }
}
