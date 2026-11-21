package net.alpaka.addons.features.profittracker

/**
 * The chat and sidebar lines the tracker reads, color codes already stripped. Each pattern is the
 * one SkyHanni matches for the same thing, checked against its captured messages.
 */
object ProfitParse {

    /** The sidebar's "Purse: 1,234,567" (or "Piggy:" before the Piggy Bank breaks), maybe with "(+50)" after it. */
    private val PURSE = Regex("""^(?:Purse|Piggy): ([\d,]+(?:\.\d+)?)""")

    /** "Moved 64 Enchanted Rotten Flesh from your Sacks to your inventory." - not a gain, a withdrawal. */
    private val MOVED_FROM_SACKS = Regex("""^Moved ([\d,]+) (.+) from your Sacks to your inventory\.$""")

    /** "You Supercrafted Enchanted Diamond x4!" - the result lands in the sacks and is not loot. */
    private val SUPERCRAFT = Regex("""^You Supercrafted (.+?)(?: x[\d,]+)?!$""")

    private val SLAYER_STARTED = Regex("""^\s*SLAYER QUEST STARTED!\s*$""")
    private val SLAYER_COMPLETE = Regex("""^\s*(?:SLAYER QUEST COMPLETE!|NICE! SLAYER BOSS SLAIN!)\s*$""")
    private val AUTO_SLAYER = Regex("""^Took ([\d,.]+[kKmM]?) coins from your bank for auto-slayer\.\.\.$""")

    private val BURROW = Regex("""^(?:You dug out a Griffin Burrow!|You finished the Griffin burrow chain!).*""")

    /** "You received 3x Compost for killing a Mite!" - one line per drop of a pest. */
    private val PEST_KILL = Regex("""^You received \d+x .+ for killing an? (.+)!$""")

    /** "+Your Damage: 1,234,567 (Position #3)" after a dragon fight. */
    private val DRAGON_DAMAGE = Regex("""^\s*\+?\s*Your Damage: ([\d,.]+) .*\(Position #\d+\)$""")
    private val EYE_PLACED = Regex("""^☬ You placed a Summoning Eye!.*""")
    private val EYE_RECOVERED = Regex("""^You recovered a Summoning Eye!.*""")

    @JvmStatic
    fun purse(line: String): Double? = PURSE.find(line.trim())?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    /** The item name a "Moved ... from your Sacks" line names, or null. */
    @JvmStatic
    fun movedFromSacks(line: String): String? = MOVED_FROM_SACKS.matchEntire(line.trim())?.groupValues?.get(2)

    @JvmStatic
    fun supercrafted(line: String): String? = SUPERCRAFT.matchEntire(line.trim())?.groupValues?.get(1)

    fun slayerStarted(line: String): Boolean = SLAYER_STARTED.matches(line)

    fun slayerComplete(line: String): Boolean = SLAYER_COMPLETE.matches(line)

    /** What auto-slayer took from the bank, in coins; null for any other line. */
    @JvmStatic
    fun autoSlayerCost(line: String): Long? = AUTO_SLAYER.matchEntire(line.trim())?.groupValues?.get(1)?.let(::shortNumber)

    fun burrow(line: String): Boolean = BURROW.matches(line.trim())

    /** The pest a drop line names ("Mite"), or null. */
    fun pestKill(line: String): String? = PEST_KILL.matchEntire(line.trim())?.groupValues?.get(1)

    /** The damage dealt in a dragon fight's summary line, or null. */
    fun dragonDamage(line: String): Double? =
        DRAGON_DAMAGE.matchEntire(line)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    fun eyePlaced(line: String): Boolean = EYE_PLACED.matches(line.trim())

    fun eyeRecovered(line: String): Boolean = EYE_RECOVERED.matches(line.trim())

    /** "1,500" → 1500, "2.5k" → 2500, "1M" → 1000000; null when it doesn't read as a number. */
    @JvmStatic
    fun shortNumber(text: String): Long? {
        val t = text.trim().replace(",", "")
        if (t.isEmpty()) return null
        val factor = when (t.last().lowercaseChar()) {
            'k' -> 1_000.0
            'm' -> 1_000_000.0
            'b' -> 1_000_000_000.0
            else -> 1.0
        }
        val number = (if (factor == 1.0) t else t.dropLast(1)).toDoubleOrNull() ?: return null
        return Math.round(number * factor)
    }
}
