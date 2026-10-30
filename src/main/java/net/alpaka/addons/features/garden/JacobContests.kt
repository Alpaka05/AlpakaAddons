package net.alpaka.addons.features.garden

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig

/**
 * The Jacob's Contests of the current SkyBlock year, for the Farming HUD's "Contest" line.
 *
 * Read from Elite (api.elitebot.dev), which collects the calendar from players' mods and hands it
 * out without a key: every contest's start and its three crops. Only while API calls are allowed;
 * without them the HUD falls back to the tab list's Jacob's Contest widget.
 */
object JacobContests {

    private const val ENDPOINT = "https://api.elitebot.dev/contests/at/now"

    /** A contest runs for 20 minutes from its start. */
    const val LENGTH_MS = 20 * 60_000L

    /** A year whose calendar is complete only changes when the year does. */
    private const val COMPLETE_REFRESH_MS = 6 * 3_600_000L
    /** Players keep adding contests to an incomplete year. */
    private const val INCOMPLETE_REFRESH_MS = 3_600_000L
    private const val RETRY_AFTER_FAILURE_MS = 10 * 60_000L

    data class Contest(val startMs: Long, val crops: List<String>)

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    @Volatile
    private var contests: List<Contest> = emptyList()
    @Volatile
    private var fetching = false
    private var validUntilMs = 0L
    private var lastAttemptMs = 0L

    /** The running contest, or the next one; null when the calendar is unknown. */
    fun current(now: Long = System.currentTimeMillis()): Contest? {
        if (!AlpakaConfig.instance.allowApiCalls) return null
        return contests.firstOrNull { it.startMs + LENGTH_MS > now }
    }

    /** Fetches the calendar when it is missing or stale. Cheap to call every second. */
    fun tick() {
        if (!AlpakaConfig.instance.allowApiCalls || fetching) return
        val now = System.currentTimeMillis()
        // A calendar that has run out (the year turned over) is refetched, but not more often than
        // a failed request would be retried.
        val stale = now >= validUntilMs || current(now) == null
        if (!stale || now - lastAttemptMs < RETRY_AFTER_FAILURE_MS) return
        fetch(now)
    }

    private fun fetch(now: Long) {
        fetching = true
        lastAttemptMs = now
        val request = HttpRequest.newBuilder(URI.create(ENDPOINT))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "AlpakaAddons")
            .GET()
            .build()
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, error ->
                try {
                    if (error != null || response == null || response.statusCode() != 200) {
                        AlpakaAddons.LOGGER.warn(
                            "Could not read the Jacob's Contest calendar ({})",
                            error?.toString() ?: "HTTP ${response?.statusCode()}",
                        )
                        return@whenComplete
                    }
                    parse(response.body())
                } catch (t: Throwable) {
                    AlpakaAddons.LOGGER.warn("Could not read the Jacob's Contest calendar", t)
                } finally {
                    fetching = false
                }
            }
    }

    private fun parse(body: String) {
        val root = JsonParser.parseString(body).asJsonObject
        val list = ArrayList<Contest>()
        root.getAsJsonObject("contests")?.entrySet()?.forEach { (start, crops) ->
            val seconds = start.toLongOrNull() ?: return@forEach
            list.add(Contest(seconds * 1000L, crops.asJsonArray.map { it.asString }))
        }
        list.sortBy { it.startMs }
        contests = list
        val complete = root.get("complete")?.asBoolean ?: false
        validUntilMs = System.currentTimeMillis() + if (complete) COMPLETE_REFRESH_MS else INCOMPLETE_REFRESH_MS
    }
}
