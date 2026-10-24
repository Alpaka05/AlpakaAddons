package net.alpaka.addons.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerDropTracker
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional

/**
 * Tells the player in chat, once per game session, when a newer release of the mod is on GitHub,
 * and offers to install it ([UpdateInstaller]).
 *
 * ### What counts as newer
 *
 * A release whose version is higher than the running one, and which carries a jar for the Minecraft
 * version the game is running. Drafts never count. Pre-releases (marked so on GitHub, or tagged with
 * a suffix such as `2.0.0-alpha.1`) count only on the "Pre-Releases" stream
 * ([AlpakaConfig.updateStream]); there they are ordered like semver, so `2.0.0-alpha.2` is above
 * `2.0.0-alpha.1` and `2.0.0` is above both. Releases are built per Minecraft version
 * (`alpaka-1.2.22-mc26.2.jar`), and a release with no jar for this game is one the player cannot
 * install, so it is not announced. A jar built for 26.2 is for every 26.2.x (see [jarSuffixes]).
 *
 * A build between releases (`1.2.22+25.d4e2c70`) counts as its release, so a test build of the
 * latest release is not told to update to it.
 *
 * ### What this sends
 *
 * One GET to GitHub's public releases list for this repository, once per start, with no key and
 * nothing about the player. Only while [AlpakaConfig.allowApiCalls] and
 * [AlpakaConfig.updateNotificationsEnabled] are both on; `/alpakaupdate check` asks again on
 * request, and needs only the first. The jar itself is downloaded only once the player has
 * confirmed the install.
 */
object UpdateChecker {

    private const val ENDPOINT = "https://api.github.com/repos/Alpaka05/AlpakaAddons/releases?per_page=20"

    /** A manual check this soon after the last one is turned down, so spamming it cannot use up GitHub's limit. */
    private const val MANUAL_COOLDOWN_MS = 60_000L

    private val SHA256 = Regex("[0-9a-f]{64}")

    /** The choices of "Update Stream", by [AlpakaConfig.updateStream]. */
    @JvmField
    val STREAM_NAMES = arrayOf("Releases", "Pre-Releases")

    /** [AlpakaConfig.updateStream], kept inside [STREAM_NAMES] so a hand-edited config cannot break the menu. */
    @JvmStatic
    fun stream(): Int = AlpakaConfig.instance.updateStream.coerceIn(0, STREAM_NAMES.size - 1)

    private fun includePreReleases() = stream() == 1

    /**
     * After "Update Stream" changes: forget what the old stream found, so the next tick in a world
     * asks again for the new one instead of the player having to run `/alpakaupdate check`.
     */
    @JvmStatic
    fun streamChanged() {
        latest = null
        found = null
        requested = false
    }

    /**
     * A release found to be newer, with the jar for this game. [sha256] is the jar's checksum as
     * GitHub lists it, or null when it lists none.
     */
    class Release(
        val version: String,
        val url: String,
        val jarName: String,
        val jarUrl: String,
        val jarSize: Long,
        val sha256: String?,
    )

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    /** Whether this session's one request has been made. */
    private var requested = false

    /** A release waiting for the player to be in a world to hear about it. */
    @Volatile
    private var found: Release? = null

    /** The newest release found this session, for the install button and `/alpakaupdate`. */
    @Volatile
    private var latest: Release? = null

    @Volatile
    private var checking = false

    private var lastManualCheckMs = 0L

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        UpdateInstaller.cleanUp()
    }

    /** The newest release found this session, or null if there is none or no check has answered yet. */
    @JvmStatic
    fun latestRelease(): Release? = latest

    private fun tick(mc: Minecraft) {
        if (mc.player == null) return
        val cfg = AlpakaConfig.instance
        if (!cfg.allowApiCalls || !cfg.updateNotificationsEnabled) return

        if (!requested) {
            requested = true
            fetch { release, _ -> found = release }
            return
        }
        val release = found ?: return
        found = null
        announce(release)
    }

    /** `/alpakaupdate`: the release found this session, or that there is none. */
    @JvmStatic
    fun status() {
        val release = latest
        if (release != null) {
            announce(release)
        } else {
            SlayerDropTracker.sendModMessage("§7You have §f${ModVersion.mod()}§7 and no newer release was found this session. "
                + "§f/alpakaupdate check§7 looks again.")
        }
    }

    /** `/alpakaupdate check`: asks GitHub now and answers in chat either way. */
    @JvmStatic
    fun checkNow() {
        if (!AlpakaConfig.instance.allowApiCalls) {
            SlayerDropTracker.sendModMessage("§7Network features are off in §f/aa§7, so the mod can't check for updates.")
            return
        }
        val now = System.currentTimeMillis()
        if (checking || now - lastManualCheckMs < MANUAL_COOLDOWN_MS) {
            SlayerDropTracker.sendModMessage("§7Checked just now - try again in a minute.")
            return
        }
        lastManualCheckMs = now
        requested = true // This check stands in for the automatic one if that has not run yet.
        SlayerDropTracker.sendModMessage("§7Checking for a newer release...")
        fetch { release, problem ->
            Minecraft.getInstance().execute {
                when {
                    problem != null -> SlayerDropTracker.sendModMessage("§cCouldn't check for a newer release ($problem).")
                    release == null -> SlayerDropTracker.sendModMessage("§7You have the newest release (§f${ModVersion.mod()}§7).")
                    else -> announce(release)
                }
            }
        }
    }

    /**
     * Asks GitHub for the newest release. [done] gets that release, or null when there is none newer,
     * and a short reason when the check failed; it runs on the HTTP client's thread.
     */
    private fun fetch(done: (Release?, String?) -> Unit) {
        val current = ModVersion.mod().substringBefore('+')
        val currentParts = parse(current, allowPreRelease = true)
        if (currentParts == null) { // A build without a version has nothing to compare.
            done(null, "this build has no version to compare")
            return
        }
        val jarSuffixes = jarSuffixes(ModVersion.minecraft())
        val preReleases = includePreReleases()

        val request = HttpRequest.newBuilder(URI.create(ENDPOINT))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "AlpakaAddons")
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build()

        checking = true
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, error ->
                checking = false
                try {
                    val status = response?.statusCode()
                    val problem = when {
                        error != null || response == null -> error?.toString() ?: "no answer"
                        // Unauthenticated requests get 60 an hour per address, shared with anything
                        // else on that address; the limit resets within the hour.
                        status == 403 || status == 429 -> "HTTP $status, most likely GitHub's hourly limit - try again later"
                        status != 200 -> "HTTP $status"
                        else -> null
                    }
                    if (problem != null) {
                        AlpakaAddons.LOGGER.warn("Could not check for a newer release ({})", problem)
                        done(null, problem)
                        return@whenComplete
                    }
                    val release = newest(response!!.body(), currentParts, jarSuffixes, preReleases)
                    if (release != null) latest = release
                    done(release, null)
                } catch (t: Throwable) {
                    AlpakaAddons.LOGGER.warn("Could not check for a newer release", t)
                    done(null, "GitHub's answer could not be read")
                }
            }
    }

    /**
     * The endings a release jar for this game can have, the best match first: the exact Minecraft
     * version, then its major.minor.
     *
     * Release jars are named after the major.minor they were built against (`-mc26.2.jar`, see
     * archiveVersion in build.gradle), while fabric.mod.json accepts every 26.2.x. Looking for the
     * exact version only, a game on a hotfix such as 26.2.1 found no jar in any release, was told it
     * had the newest one, and never heard of an update again.
     */
    internal fun jarSuffixes(minecraft: String): List<String> {
        val majorMinor = minecraft.substringBefore('-').split('.').take(2).joinToString(".")
        return listOf("-mc$minecraft.jar", "-mc$majorMinor.jar").distinct()
    }

    /**
     * The highest release above [current] with a jar ending in one of [jarSuffixes], or null if there
     * is none. Where a release has several, the first ending in [jarSuffixes] wins. Pre-releases are
     * only considered with [preReleases].
     */
    internal fun newest(body: String, current: Version, jarSuffixes: List<String>, preReleases: Boolean = false): Release? {
        var best: Release? = null
        var bestParts = current
        for (element in JsonParser.parseString(body).asJsonArray) {
            val release = element.asJsonObject
            if (release.get("draft")?.asBoolean == true) continue
            if (!preReleases && release.get("prerelease")?.asBoolean == true) continue
            val version = release.get("tag_name")?.asString?.removePrefix("v") ?: continue
            val parts = parse(version, allowPreRelease = preReleases) ?: continue
            if (compare(parts, bestParts) <= 0) continue
            val assets = release.getAsJsonArray("assets")?.map { it.asJsonObject } ?: continue
            val jar = jarSuffixes.firstNotNullOfOrNull { suffix ->
                assets.firstOrNull { it.get("name")?.asString?.endsWith(suffix) == true }
            } ?: continue
            best = Release(
                version,
                release.get("html_url")?.asString ?: continue,
                jar.get("name").asString,
                jar.get("browser_download_url")?.asString ?: continue,
                jar.get("size")?.asLong ?: 0L,
                sha256(jar.get("digest")),
            )
            bestParts = parts
        }
        return best
    }

    /** The hex of a `sha256:<hex>` digest, as GitHub lists it for each asset; null for anything else. */
    private fun sha256(digest: JsonElement?): String? {
        if (digest == null || !digest.isJsonPrimitive) return null
        val text = digest.asString.lowercase()
        if (!text.startsWith("sha256:")) return null
        return text.removePrefix("sha256:").takeIf { SHA256.matches(it) }
    }

    private fun announce(release: Release) {
        if (UpdateInstaller.isStaged(release.version)) {
            SlayerDropTracker.sendModMessage("§7Version §f${release.version}§7 is downloaded and installs when you close the game.")
            return
        }
        val line = Component.literal("§7Version §f${release.version}§7 is out (you have §f${ModVersion.mod().substringBefore('+')}§7). ")
            .append(Component.literal("[Release Page]").withStyle { style ->
                style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(ClickEvent.OpenUrl(URI.create(release.url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(release.url)))
            })
        // Only offered where installing can work; otherwise a hint says why, so the button's absence is not a puzzle.
        val blocker = UpdateInstaller.blocker(release)
        if (blocker == null) {
            line.append(" ").append(Component.literal("[Install]").withStyle { style ->
                style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(ClickEvent.Custom(UpdateInstaller.INSTALL, Optional.empty()))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Asks first, then installs it when you close the game")))
            })
        }
        SlayerDropTracker.sendModMessage(line)
        if (blocker != null) SlayerDropTracker.sendModMessage("§8  $blocker.")
    }

    /** A version's numbers ("1.2.22" as [1, 2, 22]) and its pre-release suffix ("alpha.1" as ["alpha", "1"]), empty for none. */
    internal data class Version(val numbers: List<Int>, val preRelease: List<String> = emptyList())

    /**
     * "1.2.22" or, with [allowPreRelease], "2.0.0-alpha.1"; null for anything else. Without it a
     * suffixed tag is not a release and is skipped.
     */
    internal fun parse(version: String, allowPreRelease: Boolean = false): Version? {
        val core = version.substringBefore('-')
        val suffix = version.substringAfter('-', "")
        if (suffix.isNotEmpty() && !allowPreRelease) return null
        if (version.contains('-') && suffix.isEmpty()) return null
        val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
        if (numbers.isEmpty()) return null
        return Version(numbers, if (suffix.isEmpty()) emptyList() else suffix.split('.'))
    }

    /** Semver order: the numbers first, then a pre-release below its release, then the suffix part by part. */
    private fun compare(a: Version, b: Version): Int {
        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val diff = a.numbers.getOrElse(i) { 0 } - b.numbers.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        if (a.preRelease.isEmpty() || b.preRelease.isEmpty()) {
            return b.preRelease.size.coerceAtMost(1) - a.preRelease.size.coerceAtMost(1)
        }
        for (i in 0 until minOf(a.preRelease.size, b.preRelease.size)) {
            val x = a.preRelease[i]
            val y = b.preRelease[i]
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val diff = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1 // Numbers sort below words, as in semver.
                yn != null -> 1
                else -> x.compareTo(y)
            }
            if (diff != 0) return diff
        }
        return a.preRelease.size - b.preRelease.size
    }
}
