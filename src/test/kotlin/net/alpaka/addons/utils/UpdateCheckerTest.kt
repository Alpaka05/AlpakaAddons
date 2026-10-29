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

    private fun newestRelease(running: String, minecraft: String, vararg releases: String, preReleases: Boolean = false) =
        UpdateChecker.newest("[" + releases.joinToString(",") + "]", ReleaseVersion.parse(running)!!,
            UpdateChecker.jarSuffixes(minecraft), preReleases)

    private fun newest(running: String, minecraft: String, vararg releases: String, preReleases: Boolean = false) =
        newestRelease(running, minecraft, *releases, preReleases = preReleases)?.version

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
    fun theInstallerGetsTheGameJarNotTheSources() {
        val next = release("2.0.1", "alpaka-2.0.1-mc26.2-sources.jar", "alpaka-2.0.1-mc26.2.jar")
        assertEquals("alpaka-2.0.1-mc26.2.jar", newestRelease("2.0.0", "26.2", next)?.jarName)
    }

    @Test
    fun aJarForTheExactVersionWins() {
        val next = release("2.0.1", "alpaka-2.0.1-mc26.2.jar", "alpaka-2.0.1-mc26.2.1.jar")
        assertEquals("alpaka-2.0.1-mc26.2.1.jar", newestRelease("2.0.0", "26.2.1", next)?.jarName)
    }

    @Test
    fun theChecksumIsTakenOnlyFromAValidSha256Digest() {
        val hex = "ab".repeat(32)
        fun digest(value: String) = """{"tag_name":"2.0.1","html_url":"https://example.invalid/2.0.1","assets":[""" +
            """{"name":"alpaka-2.0.1-mc26.2.jar","browser_download_url":"https://example.invalid/a.jar","size":1,"digest":"$value"}]}"""
        assertEquals(hex, newestRelease("2.0.0", "26.2", digest("sha256:${hex.uppercase()}"))?.sha256)
        assertNull(newestRelease("2.0.0", "26.2", digest("sha1:$hex"))?.sha256)
        assertNull(newestRelease("2.0.0", "26.2", digest("sha256:abc"))?.sha256)
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
        // An older release is never offered.
        assertNull(newest("1.10.0", "26.2", release("1.9.0", "alpaka-1.9.0-mc26.2.jar")))
    }

    @Test
    fun draftsAreSkippedAndPreReleasesOnlyOnThePreReleaseStream() {
        val beta = release("2.0.0-beta.1", "alpaka-2.0.0-beta.1-mc26.2.jar", prerelease = true)
        val draft = release("1.5.0", "alpaka-1.5.0-mc26.2.jar", draft = true)
        assertNull(newest("1.4.0", "26.2", draft, beta))
        assertNull(newest("1.4.0", "26.2", draft, preReleases = true))
        assertEquals("2.0.0-beta.1", newest("1.4.0", "26.2", beta, preReleases = true))
        assertEquals("2.0.0-beta.1", newest("2.0.0-alpha+3.a1b2c3d", "26.2.1", beta, preReleases = true))
        // A suffixed tag GitHub doesn't mark as a pre-release is one all the same.
        assertNull(newest("1.4.0", "26.2", release("2.0.0-rc.1", "alpaka-2.0.0-rc.1-mc26.2.jar")))
        // A plain tag GitHub marks as a pre-release is one too: only the pre-release stream offers it.
        val marked = release("1.6.0", "alpaka-1.6.0-mc26.2.jar", prerelease = true)
        assertNull(newest("1.5.0", "26.2", marked))
        assertEquals("1.6.0", newest("1.5.0", "26.2", marked, preReleases = true))
        // A running 2.0.0 is past its own release candidate.
        assertNull(newest("2.0.0", "26.2", release("2.0.0-rc.1", "alpaka-2.0.0-rc.1-mc26.2.jar", prerelease = true),
            preReleases = true))
    }

    @Test
    fun aTesterOnTheReleaseStreamHearsOnlyOfTheRelease() {
        val beta = release("2.0.0-beta.1", "alpaka-2.0.0-beta.1-mc26.2.jar", prerelease = true)
        assertNull(newest("2.0.0-alpha+3.a1b2c3d", "26.2", beta))
        assertEquals("2.0.0", newest("2.0.0-alpha+3.a1b2c3d", "26.2", beta, release("2.0.0", "alpaka-2.0.0-mc26.2.jar")))
    }
}
