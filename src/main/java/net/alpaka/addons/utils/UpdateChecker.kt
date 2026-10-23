package net.alpaka.addons.utils

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

/**
 * Tells the player in chat, once per game session, when a newer release of the mod is on GitHub.
 *
 * ### What counts as newer
 *
 * A release whose version is higher than the running one, and which carries a jar for the
 * Minecraft version the game is running. Drafts are skipped. Pre-releases (`2.0.0-beta.1`) are
 * offered only to a player already running one, so a tester of the next version hears of the next
 * beta and of the release itself, while everyone else hears of full releases only. Releases are built per Minecraft version
 * (`alpaka-1.2.22-mc26.2.jar`), and a release with no jar for this game is one the player cannot
 * install, so it is not announced. A jar built for 26.2 is for every 26.2.x (see [jarSuffixes]).
 *
 * A build between releases (`1.2.22+25.d4e2c70`) counts as its release, so a test build of the
 * latest release is not told to update to it. A build ahead of the next one (`2.0.0-alpha+3.a1b2c3d`)
 * sorts below that version's betas and the release, see [ReleaseVersion].
 *
 * ### What this sends
 *
 * One GET to GitHub's public releases list for this repository, once per start, with no key and
 * nothing about the player. Only while [AlpakaConfig.allowApiCalls] and
 * [AlpakaConfig.updateNotificationsEnabled] are both on.
 */
object UpdateChecker {

    private const val ENDPOINT = "https://api.github.com/repos/Alpaka05/AlpakaAddons/releases?per_page=20"

    /** A release found to be newer, waiting for the player to be in a world to hear about it. */
    internal class Release(val version: String, val url: String)

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    /** Whether this session's one request has been made. */
    private var requested = false

    @Volatile
    private var found: Release? = null

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    private fun tick(mc: Minecraft) {
        if (mc.player == null) return
        val cfg = AlpakaConfig.instance
        if (!cfg.allowApiCalls || !cfg.updateNotificationsEnabled) return

        if (!requested) {
            requested = true
            fetch()
            return
        }
        val release = found ?: return
        found = null
        announce(release)
    }

    private fun fetch() {
        val current = ReleaseVersion.parse(ModVersion.mod()) ?: return // A build without a version has nothing to compare.
        val jarSuffixes = jarSuffixes(ModVersion.minecraft())

        val request = HttpRequest.newBuilder(URI.create(ENDPOINT))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "AlpakaAddons")
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build()

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, error ->
                try {
                    if (error != null || response == null || response.statusCode() != 200) {
                        AlpakaAddons.LOGGER.warn(
                            "Could not check for a newer release ({})",
                            error?.toString() ?: "HTTP ${response?.statusCode()}",
                        )
                        return@whenComplete
                    }
                    found = newest(response.body(), current, jarSuffixes)
                } catch (t: Throwable) {
                    AlpakaAddons.LOGGER.warn("Could not check for a newer release", t)
                }
            }
    }

    /**
     * The endings a release jar for this game can have, the best match first: the exact Minecraft
     * version, then its major.minor.
     *
     * Release jars are named after the major.minor they were built against (`-mc26.2.jar`, see
     * archiveVersion in build.gradle), while fabric.mod.json accepts every 26.2.x. Looking for the
     * exact version only, a game on a hotfix such as 26.2.1 found no jar in any release and never
     * heard of an update again.
     */
    internal fun jarSuffixes(minecraft: String): List<String> {
        val majorMinor = minecraft.substringBefore('-').split('.').take(2).joinToString(".")
        return listOf("-mc$minecraft.jar", "-mc$majorMinor.jar").distinct()
    }

    /** The highest release above [current] with a jar ending in one of [jarSuffixes], or null if there is none. */
    internal fun newest(body: String, current: ReleaseVersion, jarSuffixes: List<String>): Release? {
        var best: Release? = null
        var bestVersion = current
        for (element in JsonParser.parseString(body).asJsonArray) {
            val release = element.asJsonObject
            if (release.get("draft")?.asBoolean == true) continue
            val version = release.get("tag_name")?.asString?.removePrefix("v") ?: continue
            val parsed = ReleaseVersion.parse(version) ?: continue
            val preRelease = release.get("prerelease")?.asBoolean == true || parsed.isPreRelease
            if (preRelease && !current.isPreRelease) continue
            if (parsed <= bestVersion) continue
            val hasJar = release.getAsJsonArray("assets")?.any { asset ->
                val name = asset.asJsonObject.get("name")?.asString ?: return@any false
                jarSuffixes.any { name.endsWith(it) }
            } == true
            if (!hasJar) continue
            best = Release(version, release.get("html_url")?.asString ?: continue)
            bestVersion = parsed
        }
        return best
    }

    private fun announce(release: Release) {
        SlayerDropTracker.sendModMessage(Component.literal("§7Version §f${release.version}§7 is out (you have §f${ModVersion.mod().substringBefore('+')}§7). ")
            .append(Component.literal("[Download]").withStyle { style ->
                style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(ClickEvent.OpenUrl(URI.create(release.url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(release.url)))
            }))
    }
}
