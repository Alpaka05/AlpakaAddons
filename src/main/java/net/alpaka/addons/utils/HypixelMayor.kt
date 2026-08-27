package net.alpaka.addons.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Whether the perk that raises slayer XP is currently in force, read from Hypixel's public election
 * endpoint.
 *
 * The only piece of Skyblock state this mod cannot get from the client. Hypixel does not put the
 * mayor on the scoreboard, in the tab list or in chat - SkyHanni and Skyblocker both call this same
 * endpoint for it, which settled the question of whether a client-side source exists.
 *
 * ### Why this asks for the perk, not for Aatrox
 *
 * A mayor does not take office with their whole perk list. The set is drawn per election, so Aatrox
 * can hold office without "Slayer XP Buff" among his perks at all - checking the mayor's name would
 * then add 25% that the player is not getting. The perk is also not exclusive to the mayor: the
 * minister contributes one of their own, which can be this one.
 *
 * So the question asked here is only ever "is this perk active", whoever happens to provide it.
 *
 * ### What this sends
 *
 * A single GET to a public resources endpoint that takes no API key, names no player, and carries no
 * identifying information. It describes the server's current election, the same for everyone. It is
 * not a player lookup, and it only happens while [AlpakaConfig.allowApiCalls] is on.
 */
object HypixelMayor {

    private const val ENDPOINT = "https://api.hypixel.net/v2/resources/skyblock/election"

    /** Hypixel's own name for the perk, as it appears in the response and in SkyHanni's perk table. */
    private const val SLAYER_XP_PERK = "slayer xp buff"

    /** "Earn 25% more Slayer XP", per the wiki and the perk's own description text. */
    private const val SLAYER_XP_MULTIPLIER = 1.25

    /**
     * How long a successful answer is trusted.
     *
     * A term lasts about five days, so this could be far longer; a few hours simply bounds how long
     * a re-election goes unnoticed while costing one request a session.
     */
    private const val CACHE_MS = 3 * 60 * 60 * 1000L

    /** How long to wait after a failure. Hypixel being unreachable is not worth retrying hard. */
    private const val RETRY_AFTER_FAILURE_MS = 10 * 60 * 1000L

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    /** Null while the answer is unknown, which is not the same as knowing the perk is inactive. */
    private var slayerXpPerkActive: Boolean? = null
    private var validUntilMs = 0L

    /** Set while a request is in flight, so a busy tick cannot start a second one. */
    @Volatile
    private var fetching = false

    /** Whose term it is, for the diagnostics command. Not what the multiplier is decided on. */
    private var mayorName: String? = null

    /**
     * What to multiply a boss's flat slayer XP by.
     *
     * Falls back to 1.0 whenever the answer is unknown - never fetched, request failed, endpoint
     * unreachable. Reporting the base figure is the safe side of that: too low is a number the
     * player can recognise, too high is one they cannot.
     */
    fun slayerXpMultiplier(): Double {
        // Gated on the setting as well as on the answer, not just on the request. Switching API
        // access off and still quietly applying a figure fetched earlier would make the setting
        // mean "stop asking" when it reads as "do not use this".
        if (!AlpakaConfig.instance.allowApiCalls) return 1.0
        return if (slayerXpPerkActive == true) SLAYER_XP_MULTIPLIER else 1.0
    }

    /** The mayor's name as Hypixel spells it, or null when it is not known. */
    fun currentMayor(): String? = mayorName

    /**
     * Refreshes the answer if it has gone stale. Called once per client tick; nearly every call is
     * a timestamp comparison and returns immediately.
     */
    fun tick() {
        if (!AlpakaConfig.instance.allowApiCalls) return
        if (fetching) return
        if (System.currentTimeMillis() < validUntilMs) return
        if (!SkyblockUtils.isOnSkyblock()) return

        fetch()
    }

    private fun fetch() {
        fetching = true
        // Held off for the failure interval up front, so a request that never comes back cannot
        // leave the door open for another one on the very next tick.
        validUntilMs = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS

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
                            "Could not read the current election; slayer XP will be reported unbuffed ({})",
                            error?.toString() ?: "HTTP ${response?.statusCode()}",
                        )
                        return@whenComplete
                    }
                    parse(response.body())
                } catch (t: Throwable) {
                    AlpakaAddons.LOGGER.warn("Could not read the current election", t)
                } finally {
                    fetching = false
                }
            }
    }

    /**
     * Reads the perks actually in force out of the response.
     *
     * Deliberately confined to the `mayor` object. The response also carries `current`, the election
     * being voted on right now, with every candidate and their full perk list - Aatrox is in there
     * whether or not he is in office, so a search across the whole document would report the buff as
     * active during any election he stands in.
     */
    private fun parse(body: String) {
        val root = JsonParser.parseString(body).asJsonObject
        if (!root.get("success").asBoolean) {
            AlpakaAddons.LOGGER.warn("Election endpoint reported failure; slayer XP stays unbuffed")
            return
        }

        val mayor = root.getAsJsonObject("mayor") ?: return
        mayorName = mayor.get("name")?.asString

        val active = activePerkNames(mayor)
        val wasActive = slayerXpPerkActive
        slayerXpPerkActive = active.any { it == SLAYER_XP_PERK }
        validUntilMs = System.currentTimeMillis() + CACHE_MS

        if (wasActive != slayerXpPerkActive) {
            AlpakaAddons.LOGGER.info(
                "Mayor {} is in office; the slayer XP buff is {}",
                mayorName ?: "unknown",
                if (slayerXpPerkActive == true) "active" else "not among the perks in force",
            )
        }
    }

    /** Every perk in force: the mayor's drawn set, plus the single one the minister contributes. */
    private fun activePerkNames(mayor: JsonObject): List<String> {
        val names = mutableListOf<String>()

        mayor.getAsJsonArray("perks")?.forEach { entry ->
            entry.asJsonObject.get("name")?.asString?.lowercase()?.let { names.add(it) }
        }

        mayor.getAsJsonObject("minister")
            ?.getAsJsonObject("perk")
            ?.get("name")?.asString?.lowercase()
            ?.let { names.add(it) }

        return names
    }
}
