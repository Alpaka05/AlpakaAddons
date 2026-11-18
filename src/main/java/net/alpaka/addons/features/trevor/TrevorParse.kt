package net.alpaka.addons.features.trevor

import java.util.Locale

/**
 * Reads Trevor the Trapper's chat lines, color codes already stripped. Kept apart from the tracker
 * so the patterns can be tested without a game.
 *
 * The lines follow SkyHanni's TrevorFeatures and TrevorTracker:
 * - `[NPC] Trevor: You can find your TRACKABLE animal near the Desert Mountain.` when a task is taken,
 * - `Killing the animal rewarded you 3 pelts.` or `Your mob died randomly, you are rewarded 3 pelts.`
 *   when the animal dies,
 * - `You ran out of time and the animal disappeared!` when the ten minutes are over.
 *
 * Hypixel never names the animal in chat, so which kind it was comes from the mob the player saw.
 */
object TrevorParse {

    private val ACCEPT = Regex("""^\[NPC] Trevor: You can find your (?<rarity>[A-Za-z]+) animal near the (?<location>.+?)\.?$""")
    private val KILL = Regex("""^Killing the animal rewarded you (?<pelts>\d+) pelts?\.?$""")
    private val SELF_KILL = Regex("""^Your mob died randomly, you are rewarded (?<pelts>\d+) pelts?\.?$""")
    private const val OUT_OF_TIME = "You ran out of time and the animal disappeared!"
    private val SIDEBAR_PELTS = Regex("""^Pelts: (?<pelts>[\d,]+)""")

    sealed interface Event
    data class Accepted(val rarity: String, val location: String) : Event
    data class Killed(val pelts: Int, val selfKill: Boolean) : Event
    data object OutOfTime : Event

    fun parse(line: String): Event? {
        val text = line.trim()
        ACCEPT.matchEntire(text)?.let {
            return Accepted(rarityName(it.groups["rarity"]!!.value), it.groups["location"]!!.value.trim())
        }
        KILL.matchEntire(text)?.let { return Killed(it.groups["pelts"]!!.value.toInt(), false) }
        SELF_KILL.matchEntire(text)?.let { return Killed(it.groups["pelts"]!!.value.toInt(), true) }
        if (text == OUT_OF_TIME) return OutOfTime
        return null
    }

    /** "TRACKABLE" as "Trackable". */
    fun rarityName(raw: String): String =
        raw.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** The pelts the sidebar shows, when it has a "Pelts:" line. */
    fun sidebarPelts(lines: List<String>): Int? {
        for (line in lines) {
            val match = SIDEBAR_PELTS.find(line.trim()) ?: continue
            return match.groups["pelts"]!!.value.replace(",", "").toIntOrNull()
        }
        return null
    }

    /**
     * The maximum health values Hypixel gives the trapper's animals (SkyHanni's TrevorSolver); with
     * Derpy as mayor every mob has double. Normal farm animals have far less.
     */
    private val ANIMAL_HEALTHS = intArrayOf(100, 200, 500, 1000, 2000, 5000, 10000, 30000)

    fun isAnimalHealth(maxHealth: Float): Boolean {
        val health = Math.round(maxHealth)
        return ANIMAL_HEALTHS.any { it == health || it * 2 == health }
    }

    /** "1m 05s", or "42s" below a minute. */
    fun formatDuration(ms: Long): String {
        val total = ((ms + 999) / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return if (minutes > 0) String.format(Locale.ROOT, "%dm %02ds", minutes, seconds) else "${seconds}s"
    }
}
