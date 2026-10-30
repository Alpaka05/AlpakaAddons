package net.alpaka.addons.features.garden

/**
 * Reads the farming figures out of Hypixel's text: the tab list's Garden widgets, the action bar's
 * skill XP and the Pest Repellent chat message. Pure string work, kept apart from the game so the
 * tests can feed it captured lines.
 */
object FarmingParse {

    /** "+12.5 Farming (45.3%)" or "+12.5 Farming (123,456/200,000)" in the action bar. */
    private val XP_GAIN = Regex("\\+([\\d,.]+) Farming \\(([^)]*)\\)")
    private val XP_PROGRESS = Regex("([\\d,.]+)/([\\d,.]+[kKmMbB]?)")

    /** " Wheat 15: 45.2%" or " Wheat 46: MAX" in the Crop Milestones widget. */
    private val MILESTONE = Regex("^\\s*([A-Za-z][A-Za-z ]*?) (\\d+): (?:([\\d.]+)%|(MAX))")

    /**
     * A crop in the Jacob's Contest widget: " ○ Wheat", or the boosted one with the Farming Fortune
     * icon, which can come through as nothing ("  Mushroom"). Checked against [CROPS] so no other
     * line of the widget passes for a crop.
     */
    private val CONTEST_CROP = Regex("^\\s*(?:[○☘]\\s*)?([A-Za-z][A-Za-z ]*[A-Za-z])")

    private val CROPS = setOf(
        "Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Mushroom", "Cactus", "Sugar Cane", "Nether Wart",
        "Cocoa Beans", "Moonflower", "Sunflower", "Wild Rose",
    )

    /** "YUM! Pests will now spawn 2x less while you break crops for the next 60m!" */
    private val REPELLENT_CHAT = Regex("Pests will now spawn (\\d+)x less while you break crops for the next (\\d+)m")

    /**
     * "YUCK! 3 ൠ Pests have spawned in Plot - 19!", "GROSS! A ൠ Pest has spawned in The Barn!".
     * Anchored at the start, so a player quoting it ("From [MVP+] Name: YUCK! ...") does not count.
     */
    private val PEST_SPAWN = Regex("^\\w+! .*Pests? ha(?:s|ve) spawned in (?:Plot - .+|The Barn)!")

    /** The crop's own item in the Crop Milestones menu: "Mushroom 25". */
    private val MENU_NAME = Regex("^([A-Za-z][A-Za-z ]*?) (\\d+)$")
    /** "Progress to Tier 26: 48.7%", followed by "389,285/800k". */
    private val MENU_PROGRESS = Regex("^Progress to Tier (\\d+): ([\\d.]+)%")
    private val MENU_AMOUNT = Regex("^([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)")

    private val DURATION_PART = Regex("(\\d+)\\s*([dhms])")

    data class XpGain(val gained: Double, val progress: String, val current: Double?, val needed: String?)

    data class Milestone(val crop: String, val tier: Int, val percent: Double?) {
        val maxed: Boolean get() = percent == null
    }

    data class Repellent(val factor: Int, val minutes: Int)

    /** One crop's progress to its next tier as the Crop Milestones menu shows it. */
    data class MenuMilestone(val crop: String, val tier: Int, val current: Long, val needed: Long, val percent: Double)

    fun xpGain(actionBar: String): XpGain? {
        val match = XP_GAIN.find(actionBar) ?: return null
        val gained = number(match.groupValues[1]) ?: return null
        val progress = match.groupValues[2].trim()
        val fraction = XP_PROGRESS.find(progress)
        return XpGain(gained, progress, fraction?.let { number(it.groupValues[1]) }, fraction?.groupValues?.get(2))
    }

    /**
     * The lines of one tab list widget after its title, or null when the list has no such widget.
     * Hypixel indents a widget's lines and starts the next widget, or a new column, unindented.
     */
    fun widget(lines: List<String>, title: String): List<String>? {
        val start = lines.indexOfFirst { it.trim().startsWith(title) }
        if (start < 0) return null
        val body = ArrayList<String>()
        for (index in start + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank() || !Character.isWhitespace(line[0])) break
            body.add(line)
        }
        return body
    }

    /** What follows the widget's title on its own line: "Jacob's Contest: 12m" gives "12m". */
    fun widgetTitleValue(lines: List<String>, title: String): String? =
        lines.firstOrNull { it.trim().startsWith(title) }?.trim()?.substring(title.length)?.trim()

    /** The value of a "Name: value" line in a widget, e.g. "Cooldown" in the Pests widget. */
    fun field(body: List<String>, name: String): String? {
        val prefix = "$name:"
        return body.firstOrNull { it.trim().startsWith(prefix) }?.trim()?.substring(prefix.length)?.trim()
    }

    fun milestone(body: List<String>): Milestone? {
        for (line in body) {
            val match = MILESTONE.find(line) ?: continue
            val tier = match.groupValues[2].toIntOrNull() ?: continue
            val percent = if (match.groups[4] != null) null else match.groupValues[3].toDoubleOrNull() ?: continue
            return Milestone(match.groupValues[1].trim(), tier, percent)
        }
        return null
    }

    fun contestCrops(body: List<String>): List<String> =
        body.mapNotNull { line -> CONTEST_CROP.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it in CROPS } }

    fun repellent(chat: String): Repellent? {
        val match = REPELLENT_CHAT.find(chat) ?: return null
        return Repellent(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    fun pestSpawned(chat: String): Boolean = PEST_SPAWN.containsMatchIn(chat)

    /**
     * Reads one crop's item in the Crop Milestones menu: its name and lore lines, colors stripped.
     * Null for anything else in the menu, and for a maxed crop, which has no next tier.
     */
    fun menuMilestone(name: String, lore: List<String>): MenuMilestone? {
        val title = MENU_NAME.find(name.trim()) ?: return null
        val crop = title.groupValues[1]
        if (crop !in CROPS) return null
        val tier = title.groupValues[2].toInt()
        for (index in lore.indices) {
            val progress = MENU_PROGRESS.find(lore[index].trim()) ?: continue
            // The first "Progress to Tier" is the next tier; a later one is the way to the last.
            if (progress.groupValues[1].toInt() != tier + 1) return null
            val amount = lore.getOrNull(index + 1)?.trim()?.let { MENU_AMOUNT.find(it) } ?: return null
            val current = amount(amount.groupValues[1]) ?: return null
            val needed = amount(amount.groupValues[2]) ?: return null
            return MenuMilestone(crop, tier, current, needed, progress.groupValues[2].toDouble())
        }
        return null
    }

    /** "389,285", "800k", "20.2M" as a whole number. */
    fun amount(text: String): Long? {
        val suffix = text.lastOrNull()?.lowercaseChar()
        val factor = when (suffix) {
            'k' -> 1_000.0
            'm' -> 1_000_000.0
            'b' -> 1_000_000_000.0
            else -> 1.0
        }
        val digits = if (factor == 1.0) text else text.dropLast(1)
        return number(digits)?.let { Math.round(it * factor) }
    }

    /** "1h 12m", "4m 30s" or "45s" as milliseconds; null when there is no time in it. */
    fun durationMs(text: String): Long? {
        var total = 0L
        var found = false
        for (part in DURATION_PART.findAll(text)) {
            found = true
            val value = part.groupValues[1].toLong()
            total += when (part.groupValues[2]) {
                "d" -> value * 86_400_000L
                "h" -> value * 3_600_000L
                "m" -> value * 60_000L
                else -> value * 1_000L
            }
        }
        return if (found) total else null
    }

    private fun number(text: String): Double? = text.replace(",", "").toDoubleOrNull()
}
