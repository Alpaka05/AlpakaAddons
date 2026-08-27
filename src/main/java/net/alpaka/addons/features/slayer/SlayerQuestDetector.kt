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
     * Re-parse interval while a slayer quest is actually up, in milliseconds. One client tick.
     *
     * The boss timer measures from this poll noticing the progress line enter "Slay the boss!" to it
     * noticing the line leave again, so the sampling interval is the timer's error bar at both ends.
     * At 250ms that is up to half a second added to a fight; at tick resolution it is a tenth of it.
     *
     * Affordable because [SkyblockUtils.getSidebarLines] shares one scoreboard walk per tick: the
     * extra polls re-scan a snapshot that already exists rather than building another. Going below
     * a tick would buy nothing either way - Hypixel does not send scoreboard updates faster.
     */
    private const val ACTIVE_QUEST_REFRESH_MS = 50L

    /**
     * How often the tab list may be consulted when the sidebar carries no quest.
     *
     * Slower than [REFRESH_MS] on purpose. Reading the tab list builds a String per listed player,
     * and the sidebar-less case is the common one - standing around with no slayer running - so at
     * the sidebar's cadence this would walk eighty entries four times a second for nothing. Kill
     * detection is unaffected: during a fight the quest is on the sidebar, which stays at 250ms.
     */
    private const val TAB_FALLBACK_MS = 1_000L

    private var tabCheckedAtMs = 0L
    private var cachedTabLines: List<String> = emptyList()

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

    /**
     * How long after a cancelled quest the quest lines vanishing is not read as a kill.
     *
     * Hypixel leaves the quest on the sidebar for a moment after cancelling it - four seconds in the
     * capture that exposed this - so the window has to outlast that. It cannot swallow a real kill:
     * cancelling clears the quest, and starting a new one and killing its boss inside ten seconds is
     * not possible.
     */
    private const val CANCEL_GRACE_MS = 10_000L

    private var cancelledAtMs = 0L

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
        // Fast only while there is a quest to watch, which is the only time the resolution matters.
        // Without a quest up this is reached per frame through currentOrRecent(), and re-scanning
        // the sidebar at frame rate to keep learning there is still no slayer is pure waste.
        val interval = if (activeType != null) ACTIVE_QUEST_REFRESH_MS else REFRESH_MS
        if (now - checkedAtMs < interval) return
        checkedAtMs = now

        var foundType: SlayerType? = null
        var foundTier = 0
        var foundProgress = ""

        // Scanned rather than indexed relative to a header line: the scoreboard API hands back rows
        // in no guaranteed order, so "the line after the boss name" is not a safe assumption.
        for (line in linesToScan(now)) {
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
     * The lines to look for a quest in: the sidebar, or the tab list when the sidebar has none.
     *
     * Hypixel does not always put the slayer quest on the sidebar - away from the slayer's own area
     * it is frequently missing while the tab list still carries it, which is what made the HUD come
     * and go. SkyHanni reads the same two sources in the same order.
     */
    private fun linesToScan(now: Long): List<String> {
        val sidebar = SkyblockUtils.getSidebarLines()
        if (sidebar.any { SlayerType.fromScoreboardLine(it) != null }) return sidebar

        if (now - tabCheckedAtMs >= TAB_FALLBACK_MS) {
            tabCheckedAtMs = now
            cachedTabLines = SkyblockUtils.getTabListLines()
        }
        if (cachedTabLines.any { SlayerType.fromScoreboardLine(it) != null }) return cachedTabLines

        return sidebar
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
    /**
     * Hypixel says the quest was cancelled. Stops the disappearing quest being read as a kill.
     *
     * Cancelling looks exactly like a kill from the sidebar alone - the boss was up, then the quest
     * lines are gone - so without this every cancelled quest counted as a boss killed. Comparing
     * against SkyHanni over three sessions made it plain: identical counts in the two sessions with
     * no cancellation, exactly one kill too many in the session with one.
     *
     * The running fight is dropped as well: there is no boss any more, so the timer has nothing left
     * to time and its clock would otherwise keep running on the HUD.
     */
    fun onQuestCancelled() {
        cancelledAtMs = System.currentTimeMillis()
        SlayerTimer.clear()
    }

    private fun detectKill(newProgress: String, newType: SlayerType?) {
        if (newProgress == lastProgress) return

        val wasFighting = lastProgress == STATE_BOSS_FIGHT

        // "Boss slain!" is stated outright and can be trusted whatever else happened. The other two
        // are only inferred from the quest going away, which is also what cancelling looks like, so
        // those are ignored for a moment after Hypixel says the quest was cancelled.
        val slain = newProgress == STATE_BOSS_SLAIN
        val vanished = newProgress.isEmpty() || newType == null
        val recentlyCancelled = System.currentTimeMillis() - cancelledAtMs < CANCEL_GRACE_MS

        if (wasFighting && (slain || (vanished && !recentlyCancelled))) {
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
