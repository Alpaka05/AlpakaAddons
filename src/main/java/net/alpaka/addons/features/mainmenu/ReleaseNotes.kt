package net.alpaka.addons.features.mainmenu

import com.google.gson.JsonParser
import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The notes of the mod's newest release, for the main menu's What's New card.
 *
 * One GET to GitHub's public "latest release" of this repository - drafts and pre-releases are
 * never the latest - the first time the menu opens in a game session, with no key and nothing
 * about the player; a failed request is tried again on a later open, but not more than once every
 * few minutes. Nothing is asked while "Allow Network Features" or the card itself is off, and the
 * card stays away.
 *
 * The notes are written as the release notes always are: a bold line per section (**New**,
 * **Changed**, **Fixed**) and a bullet per item ([parse]).
 */
object ReleaseNotes {

    private const val ENDPOINT = "https://api.github.com/repos/Alpaka05/AlpakaAddons/releases/latest"
    private const val RETRY_MS = 5 * 60_000L
    private val DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    class Section(val title: String, val items: List<String>)

    class Notes(val version: String, val date: String, val url: String, val sections: List<Section>) {
        val itemCount: Int get() = sections.sumOf { it.items.size }
    }

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    /** The notes, once they have arrived; set on the render thread. */
    var notes: Notes? = null
        private set

    private var inFlight = false
    private var nextAttemptMs = 0L

    /** Asks GitHub for the notes unless they are here, on their way, or the last try was too recent. */
    fun request() {
        val cfg = AlpakaConfig.instance
        if (!cfg.allowApiCalls || !cfg.customMainMenuWhatsNew || notes != null || inFlight) return
        val now = System.currentTimeMillis()
        if (now < nextAttemptMs) return
        inFlight = true
        nextAttemptMs = now + RETRY_MS

        val request = HttpRequest.newBuilder(URI.create(ENDPOINT))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "AlpakaAddons")
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build()
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            val read = try {
                if (error == null && response?.statusCode() == 200) read(response.body()) else {
                    AlpakaAddons.LOGGER.warn("Could not load the release notes ({})",
                        error?.toString() ?: "HTTP ${response?.statusCode()}")
                    null
                }
            } catch (t: Throwable) {
                AlpakaAddons.LOGGER.warn("Could not read the release notes", t)
                null
            }
            Minecraft.getInstance().execute {
                inFlight = false
                if (read != null) notes = read
            }
        }
    }

    /** The notes to show: none while network features or the card are off, even if they arrived before. */
    fun shown(): Notes? {
        val cfg = AlpakaConfig.instance
        return if (cfg.allowApiCalls && cfg.customMainMenuWhatsNew) notes else null
    }

    /** Whether the player has not opened these notes yet. */
    fun unread(notes: Notes): Boolean = AlpakaConfig.instance.releaseNotesSeen != notes.version

    fun markRead(notes: Notes) {
        if (!unread(notes)) return
        AlpakaConfig.instance.releaseNotesSeen = notes.version
        AlpakaConfig.save()
    }

    private fun read(body: String): Notes? {
        val release = JsonParser.parseString(body).asJsonObject
        val version = release.get("tag_name")?.asString?.removePrefix("v") ?: return null
        val url = release.get("html_url")?.asString ?: return null
        val date = release.get("published_at")?.takeIf { it.isJsonPrimitive }?.asString
            ?.let { runCatching { OffsetDateTime.parse(it).format(DATE) }.getOrNull() } ?: ""
        val sections = parse(release.get("body")?.takeIf { it.isJsonPrimitive }?.asString ?: "")
        if (sections.isEmpty()) return null
        return Notes(version, date, url, sections)
    }

    /**
     * The sections of a release's notes: a line that is only bold text or a heading starts one, a
     * bullet adds an item to it. Bullets before the first heading go under "Changes"; markdown
     * emphasis and code marks are dropped, as the menu draws plain text.
     */
    @JvmStatic
    fun parse(markdown: String): List<Section> {
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        for (raw in markdown.lineSequence()) {
            val line = raw.trim()
            val heading = Regex("^(?:#{1,6}\\s+(.+)|\\*\\*(.+)\\*\\*:?)$").find(line)
            when {
                heading != null -> {
                    val title = clean(heading.groupValues[1].ifEmpty { heading.groupValues[2] })
                    sections += title to mutableListOf()
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val item = clean(line.substring(2))
                    if (item.isEmpty()) continue
                    if (sections.isEmpty()) sections += "Changes" to mutableListOf()
                    sections.last().second += item
                }
            }
        }
        return sections.filter { it.second.isNotEmpty() }.map { Section(it.first, it.second) }
    }

    private fun clean(text: String): String = text.replace("**", "").replace("`", "").trim()
}
