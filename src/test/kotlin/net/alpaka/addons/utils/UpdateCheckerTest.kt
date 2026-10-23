package net.alpaka.addons.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Which release the update check offers, from GitHub's answer as it comes. */
class UpdateCheckerTest {

    private fun release(tag: String, vararg jars: String, draft: Boolean = false, prerelease: Boolean = false): String {
        val assets = jars.joinToString(",") {
            """{"name":"$it","browser_download_url":"https://example.invalid/$it","size":1}"""
        }
        return """{"tag_name":"$tag","html_url":"https://example.invalid/$tag","draft":$draft,""" +
            """"prerelease":$prerelease,"assets":[$assets]}"""
    }

    private fun newest(running: String, minecraft: String, vararg releases: String) =
        UpdateChecker.newest("[" + releases.joinToString(",") + "]", UpdateChecker.parse(running)!!,
            UpdateChecker.jarSuffixes(minecraft))

    @Test
    fun aHotfixGameLooksForItsOwnVersionThenTheMajorMinor() {
        assertEquals(listOf("-mc26.2.jar"), UpdateChecker.jarSuffixes("26.2"))
        assertEquals(listOf("-mc26.2.1.jar", "-mc26.2.jar"), UpdateChecker.jarSuffixes("26.2.1"))
        assertEquals(listOf("-mc26.2-rc1.jar", "-mc26.2.jar"), UpdateChecker.jarSuffixes("26.2-rc1"))
    }

    @Test
    fun theGameAndItsHotfixesBothFindTheReleaseJar() {
        val next = release("1.4.1", "alpaka-1.4.1-mc26.2.jar", "alpaka-1.4.1-mc26.2-sources.jar")
        assertEquals("alpaka-1.4.1-mc26.2.jar", newest("1.4.0", "26.2", next)?.jarName)
        assertEquals("alpaka-1.4.1-mc26.2.jar", newest("1.4.0", "26.2.1", next)?.jarName)
    }

    @Test
    fun aJarForTheExactVersionWins() {
        val next = release("1.4.1", "alpaka-1.4.1-mc26.2.jar", "alpaka-1.4.1-mc26.2.1.jar")
        assertEquals("alpaka-1.4.1-mc26.2.1.jar", newest("1.4.0", "26.2.1", next)?.jarName)
    }

    @Test
    fun aReleaseForAnotherMinecraftIsNotOffered() {
        assertNull(newest("1.4.0", "26.2", release("1.4.1", "alpaka-1.4.1-mc26.3.jar")))
        assertNull(newest("1.4.0", "26.2.1", release("1.4.1", "alpaka-1.4.1-mc26.1.2.jar")))
    }

    @Test
    fun versionsCompareByNumberNotByText() {
        assertEquals("1.10.0", newest("1.9.0", "26.2",
            release("1.9.5", "alpaka-1.9.5-mc26.2.jar"),
            release("1.10.0", "alpaka-1.10.0-mc26.2.jar"))?.version)
        assertNull(newest("1.10.0", "26.2", release("1.9.0", "alpaka-1.9.0-mc26.2.jar")))
    }

    @Test
    fun draftsPreReleasesAndOtherTagsAreSkipped() {
        assertNull(newest("1.4.0", "26.2",
            release("1.5.0", "alpaka-1.5.0-mc26.2.jar", draft = true),
            release("1.6.0", "alpaka-1.6.0-mc26.2.jar", prerelease = true),
            release("2.0.0-alpha", "alpaka-2.0.0-alpha-mc26.2.jar")))
    }
}
