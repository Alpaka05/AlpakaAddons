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
 * A full release - GitHub leaves drafts and pre-releases out of the list on its own account, and
 * those are skipped here as well - whose version is higher than the running one, and which carries
 * a jar for the Minecraft version the game is running. Releases are built per Minecraft version
 * (`alpaka-1.2.22-mc26.2.jar`), and a release with no jar for this game is one the player cannot
 * install, so it is not announced.
 *
 * A build between releases (`1.2.22+25.d4e2c70`) counts as its release, so a test build of the
 * latest release is not told to update to it.
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
    private class Release(val version: String, val url: String)

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
        val current = ModVersion.mod().substringBefore('+')
        val currentParts = parse(current) ?: return // A build without a version has nothing to compare.
        val jarSuffix = "-mc${ModVersion.minecraft()}.jar"

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
                    found = newest(response.body(), currentParts, jarSuffix)
                } catch (t: Throwable) {
                    AlpakaAddons.LOGGER.warn("Could not check for a newer release", t)
                }
            }
    }

    /** The highest release above [current] with a jar ending in [jarSuffix], or null if there is none. */
    private fun newest(body: String, current: List<Int>, jarSuffix: String): Release? {
        var best: Release? = null
        var bestParts = current
        for (element in JsonParser.parseString(body).asJsonArray) {
            val release = element.asJsonObject
            if (release.get("draft")?.asBoolean == true || release.get("prerelease")?.asBoolean == true) continue
            val version = release.get("tag_name")?.asString?.removePrefix("v") ?: continue
            val parts = parse(version) ?: continue
            if (compare(parts, bestParts) <= 0) continue
            val hasJar = release.getAsJsonArray("assets")?.any {
                it.asJsonObject.get("name")?.asString?.endsWith(jarSuffix) == true
            } == true
            if (!hasJar) continue
            best = Release(version, release.get("html_url")?.asString ?: continue)
            bestParts = parts
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

    /** "1.2.22" as [1, 2, 22]; null for anything that is not dot-separated numbers. */
    private fun parse(version: String): List<Int>? {
        val parts = version.split('.').map { it.toIntOrNull() ?: return null }
        return parts.ifEmpty { null }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
