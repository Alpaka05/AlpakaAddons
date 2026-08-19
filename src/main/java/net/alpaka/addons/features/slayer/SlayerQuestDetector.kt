package net.alpaka.addons.features.slayer

import net.alpaka.addons.utils.SkyblockUtils

/**
 * Works out which slayer quest is active by reading the Skyblock sidebar.
 *
 * The sidebar is the only place Hypixel states the boss type - chat never does. A completed quest
 * only says "SLAYER QUEST COMPLETE!", with no mention of which slayer it was, so attributing a kill
 * or a drop means having read the boss name off the scoreboard beforehand.
 *
 * This reads what the server already sends the client for display; nothing is scanned or probed.
 */
object SlayerQuestDetector {

    /**
     * Sidebar re-parse interval. Kept short because a kill is detected from the progress line
     * changing, and "Boss slain!" can be replaced by the quest clearing very quickly.
     */
    private const val REFRESH_MS = 250L

    /**
     * How long a boss type stays usable after it has vanished from the sidebar.
     *
     * The quest lines clear within a moment of the boss dying, which can happen before the drop and
     * completion messages have all arrived, so the last known boss is kept around to attribute them.
     */
    private const val MEMORY_MS = 60_000L

    private const val STATE_BOSS_FIGHT = "Slay the boss!"
    private const val STATE_BOSS_SLAIN = "Boss slain!"

    private var checkedAtMs = 0L

    /** The slayer named on the sidebar right now, or null when no quest is shown. */
    var activeType: SlayerType? = null
        private set

    /** Quest tier, 1-5, or 0 when unknown. */
    var tier: Int = 0
        private set

    /** The raw progress line, e.g. "Slay the boss!" or "1,200/3,000 Combat XP". */
    var progress: String = ""
        private set

    private var lastSeenType: SlayerType? = null
    private var lastSeenAtMs = 0L

    private var lastProgress = ""
    private var pendingKill: SlayerType? = null
    private var pendingSpawn: SlayerType? = null

    /** True while the sidebar says the boss itself is up. */
    val inBossFight: Boolean get() = progress == STATE_BOSS_FIGHT

    /**
     * The active slayer, falling back to the most recently seen one for [MEMORY_MS] so that chat
     * messages arriving just after the quest lines clear can still be attributed.
     */
    fun currentOrRecent(): SlayerType? {
        refresh()
        activeType?.let { return it }
        if (lastSeenType != null && System.currentTimeMillis() - lastSeenAtMs <= MEMORY_MS) {
            return lastSeenType
        }
        return null
    }

    /** Re-reads the sidebar, at most once per [REFRESH_MS]. */
    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - checkedAtMs < REFRESH_MS) return
        checkedAtMs = now

        var foundType: SlayerType? = null
        var foundTier = 0
        var foundProgress = ""

        // Scanned rather than indexed relative to a header line: the scoreboard API hands back rows
        // in no guaranteed order, so "the line after the boss name" is not a safe assumption.
        for (line in SkyblockUtils.getSidebarLines()) {
            val type = SlayerType.fromScoreboardLine(line)
            if (type != null && foundType == null) {
                foundType = type
                foundTier = parseTier(line)
                continue
            }
            if (line == STATE_BOSS_FIGHT || line == STATE_BOSS_SLAIN) {
                foundProgress = line
            } else if (foundProgress.isEmpty() && (line.contains("Combat XP") || line.contains("Kills"))) {
                foundProgress = line
            }
        }

        activeType = foundType
        tier = foundTier
        progress = foundProgress

        if (foundType != null) {
            lastSeenType = foundType
            lastSeenAtMs = now
        }

        detectKill(foundProgress, foundType)
    }

    /**
     * Notices that the boss just died, by watching the progress line leave "Slay the boss!".
     *
     * The sidebar is used rather than chat because Hypixel does not reliably announce the kill: a
     * captured session shows four "SLAYER QUEST STARTED!" messages and not one "SLAYER QUEST
     * COMPLETE!", since other Skyblock mods routinely swallow or rewrite that line. The sidebar
     * cannot be hidden from us the same way.
     *
     * The quest clearing straight out of the boss fight counts too - at a 250ms poll the brief
     * "Boss slain!" state is easy to miss entirely.
     */
    private fun detectKill(newProgress: String, newType: SlayerType?) {
        if (newProgress == lastProgress) return

        val wasFighting = lastProgress == STATE_BOSS_FIGHT
        val ended = newProgress == STATE_BOSS_SLAIN || newProgress.isEmpty() || newType == null
        if (wasFighting && ended) {
            pendingKill = lastSeenType
        }

        // The reverse transition: the boss just spawned. There is no chat announcement to fall
        // back on here either - SkyHanni's own pattern repository has a spawn message for every
        // other Hypixel boss (the Ender Dragon, Arachne, Crimson Isle minibosses) but none for a
        // regular slayer boss, confirming Hypixel simply never sends one. The sidebar entering
        // "Slay the boss!" is the only signal there is.
        if (newProgress == STATE_BOSS_FIGHT && newType != null) {
            pendingSpawn = newType
        }

        lastProgress = newProgress
    }

    /** Returns the slayer whose boss just died, once per kill, or null. */
    fun consumeKill(): SlayerType? {
        val killed = pendingKill
        pendingKill = null
        return killed
    }

    /** Returns the slayer whose boss just spawned, once per spawn, or null. */
    fun consumeSpawn(): SlayerType? {
        val spawned = pendingSpawn
        pendingSpawn = null
        return spawned
    }

    /** Reads the trailing roman numeral of a category line like "Inferno Demonlord IV". */
    private fun parseTier(line: String): Int = when (line.substringAfterLast(' ', "").uppercase()) {
        "I" -> 1
        "II" -> 2
        "III" -> 3
        "IV" -> 4
        "V" -> 5
        else -> 0
    }
}
