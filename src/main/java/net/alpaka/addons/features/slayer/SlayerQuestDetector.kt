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
     * Tier of the quest last seen on the sidebar, kept for the same reason as [lastSeenType].
     *
     * [tier] is cleared the instant the quest lines go, and a kill is usually detected from exactly
     * that - so reading [tier] when crediting a kill yields nothing. This holds the last real value.
     */
    var lastSeenTier: Int = 0
        private set

    /**
     * How long after a quest ends without a kill the vanishing quest lines are not read as one.
     *
     * Hypixel leaves the quest on the sidebar for a moment afterwards - four seconds in the capture
     * that exposed this - so the window has to outlast that. It cannot swallow a real kill: the
     * quest is gone, and starting a new one and killing its boss inside ten seconds is not possible.
     */
    private const val VOID_GRACE_MS = 10_000L

    private var questVoidedAtMs = 0L
    private var wasDead = false

    /**
     * How long a kill inferred from the quest merely vanishing is held before it is acted on.
     *
     * Not a nicety. Dying fails the quest, and Hypixel takes the quest lines off the sidebar for
     * that at the same moment it would for a kill - while both messages that say what really
     * happened, "SLAYER QUEST FAILED!" and the " ☠ You were killed by ..." line, arrive *after*. So
     * [VOID_GRACE_MS] alone could never help: by the time the truth landed, the kill had already been
     * counted, announced, and written as a personal best. Holding the inference for a moment is what
     * lets a late veto still arrive in time.
     *
     * Only inferred kills wait. "Boss slain!" is Hypixel stating the boss died and is acted on at
     * once, so the common case is not delayed at all.
     */
    private const val KILL_CONFIRM_MS = 1_000L

    private var lastProgress = ""
    private var pendingKill: SlayerType? = null

    /**
     * When the transition that produced [pendingKill] was noticed.
     *
     * Handed to the consumers rather than letting them read the clock themselves, because a held
     * kill is acted on up to [KILL_CONFIRM_MS] after the boss actually died - and timing the fight
     * to when the mod got around to believing it would add that hold onto every reported time.
     */
    private var pendingKillAtMs = 0L

    /** Whether [pendingKill] was inferred from the quest vanishing rather than stated outright. */
    private var pendingKillInferred = false

    /** When the kill that [consumeKill] last handed out was noticed. */
    var killDetectedAtMs = 0L
        private set

    private var pendingSpawn: SlayerType? = null

    /** What the quest looked like at the last readable sample. */
    private enum class Phase { NONE, GRINDING, FIGHT, SLAIN }

    private var lastPhase = Phase.NONE

    /**
     * Whether the boss now up has already been handed out as a kill, by the sidebar or by chat. Both
     * can report the same boss; this makes the second one a no-op. Cleared when the next boss spawns.
     */
    private var killCountedThisFight = false

    /** Whether the held kill may be timed: false when the fight itself was never seen. */
    private var pendingKillTimed = true

    /** Whether the held spawn came straight from grinding, the only start a fight can be timed from. */
    private var pendingSpawnVerified = false

    /** Whether the kill [consumeKill] last handed out can be timed. */
    var killTimed = true
        private set

    /**
     * Whether the spawn [consumeSpawn] last handed out came from grinding in the same world. A boss
     * that is already up when it is first seen - on joining, after a warp - was not, and a time
     * measured from that moment would be too short to be a personal best.
     */
    var spawnVerified = false
        private set

    /**
     * The level the last sidebar sample came from, held weakly so a left world can be collected.
     * A sample from another level starts over; see [resetForLevelChange].
     */
    private var sampledLevel: java.lang.ref.WeakReference<Any>? = null

    /**
     * Set when the world changed: the next readable state only becomes the baseline. Whatever the
     * quest looks like in a new world, arriving there is neither a boss spawning nor one dying.
     */
    private var freshLevel = false

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
        // Before the interval guard: a death lasts a moment and must not be missed because the
        // sidebar happened to have been re-read a few milliseconds earlier.
        checkDeath()

        val now = System.currentTimeMillis()
        // Fast only while there is a quest to watch, which is the only time the resolution matters.
        // Without a quest up this is reached per frame through currentOrRecent(), and re-scanning
        // the sidebar at frame rate to keep learning there is still no slayer is pure waste.
        val interval = if (activeType != null) ACTIVE_QUEST_REFRESH_MS else REFRESH_MS
        if (now - checkedAtMs < interval) return
        checkedAtMs = now

        val level = net.minecraft.client.Minecraft.getInstance().level
        if (level != null && sampledLevel?.get() !== level) {
            sampledLevel = java.lang.ref.WeakReference(level)
            resetForLevelChange()
        }

        var foundType: SlayerType? = null
        var foundTier = 0
        var foundProgress = ""

        val lines = linesToScan(now)

        // Scanned rather than indexed relative to a header line: the scoreboard API hands back rows
        // in no guaranteed order, so "the line after the boss name" is not a safe assumption.
        for (line in lines) {
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
            if (foundTier > 0) lastSeenTier = foundTier
        }

        // No sidebar at all - between servers, or before the new one has sent its scoreboard - says
        // nothing about the quest. Read as the quest vanishing, a server switch in the middle of a
        // fight used to count a boss that never died.
        if (level == null || lines.isEmpty()) return

        detectKill(foundProgress, foundType)
    }

    /**
     * Forgets what the quest looked like in the previous world. A kill only inferred from the quest
     * lines going away is dropped, since changing world takes them away too; a stated "Boss slain!"
     * still counts. The first state read in the new world is taken as it is, so arriving mid-fight
     * does not sound the spawn alert either.
     */
    fun resetForLevelChange() {
        if (pendingKillInferred) pendingKill = null
        pendingSpawn = null
        freshLevel = true
    }

    /** As [resetForLevelChange], and forgets the recent slayer too: the next server says it anew. */
    fun resetForDisconnect() {
        resetForLevelChange()
        lastSeenType = null
        lastSeenAtMs = 0L
        activeType = null
        tier = 0
        progress = ""
    }

    /**
     * Whether the player dying now says anything about the quest: during the boss fight, or while
     * a kill inferred from the quest vanishing is still held back. A death while grinding mobs
     * fails nothing, and must not stop the next real kill from being recognised.
     */
    val deathCanVoidQuest: Boolean
        get() = inBossFight || (pendingKill != null && pendingKillInferred)

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
     * The quest ended without the boss dying - cancelled at Maddox, or failed because the player
     * died. Stops the quest disappearing from the sidebar being read as a kill.
     *
     * Without this, ending a quest any other way counted as a boss killed: it inflated lifetime
     * kills, the session's boss count and average, the dry streak since an RNG drop, and recorded a
     * boss time no fight had produced. Both routes were caught in captured logs, each time with
     * SkyHanni reporting no kill at all at that moment.
     *
     * The running fight is dropped as well: there is no boss any more, so the timer has nothing left
     * to time and its clock would otherwise keep running on the HUD.
     */
    fun onQuestVoided() {
        questVoidedAtMs = System.currentTimeMillis()

        // Takes back a kill that was only inferred from the quest disappearing and has not been
        // acted on yet - which is the whole point of holding it. A stated "Boss slain!" is left
        // alone: dying a moment after the boss died does not undo the kill.
        if (pendingKillInferred) {
            pendingKill = null
            killCountedThisFight = false
        }

        SlayerTimer.clear()
    }

    /**
     * Notices the player dying, which fails a slayer quest.
     *
     * Read from the client's own player rather than from the "SLAYER QUEST FAILED!" line, because
     * that line arrives *after* the sidebar has already dropped the quest - in the captured case the
     * phantom kill was reported a moment before Hypixel said the quest had failed. The client knows
     * it is dead as soon as the health packet lands, which is early enough.
     */
    private fun checkDeath() {
        val player = net.minecraft.client.Minecraft.getInstance().player
        val dead = player != null && player.isDeadOrDying
        if (dead && !wasDead) onQuestVoided()
        wasDead = dead
    }

    private fun phaseOf(progress: String, type: SlayerType?): Phase = when {
        type == null || progress.isEmpty() -> Phase.NONE
        progress == STATE_BOSS_FIGHT -> Phase.FIGHT
        progress == STATE_BOSS_SLAIN -> Phase.SLAIN
        else -> Phase.GRINDING
    }

    /**
     * Turns the sidebar's quest state into kills and spawns.
     *
     * The progress line moves between four phases: none, grinding ("1,200/3,000 Combat XP"), the
     * fight ("Slay the boss!") and "Boss slain!". A kill is the fight ending: into "Boss slain!",
     * stated outright, or straight into grinding (an auto-restarted quest) or nothing, inferred and
     * held back so that dying or cancelling can still veto it. Grinding straight into "Boss slain!"
     * is a boss that came and went between two samples: counted, but not timed. Only fight-to-slain
     * and fight-to-nothing used to count, so every auto-restarted quest and every one-tick boss went
     * missing.
     */
    private fun detectKill(newProgress: String, newType: SlayerType?) {
        // Before the unchanged check: the first readable state in a new world is the baseline even
        // when it reads the same as the last one before the change, or the flag would stay set and
        // swallow the next real transition - the kill itself, after warping mid-fight.
        if (freshLevel) {
            freshLevel = false
            lastProgress = newProgress
            lastPhase = phaseOf(newProgress, newType)
            return
        }
        if (newProgress == lastProgress) return

        val previous = lastPhase
        val phase = phaseOf(newProgress, newType)
        lastProgress = newProgress
        lastPhase = phase
        if (phase == previous) return

        val now = System.currentTimeMillis()
        val recentlyVoided = now - questVoidedAtMs < VOID_GRACE_MS
        when {
            previous == Phase.FIGHT && phase == Phase.SLAIN -> emitKill(lastSeenType, now, inferred = false, timed = true)
            previous == Phase.FIGHT && !recentlyVoided -> emitKill(lastSeenType, now, inferred = true, timed = true)
            previous == Phase.GRINDING && phase == Phase.SLAIN -> emitKill(lastSeenType, now, inferred = false, timed = false)
        }

        // The boss just spawned. There is no chat announcement to fall back on here either -
        // SkyHanni's own pattern repository has a spawn message for every other Hypixel boss (the
        // Ender Dragon, Arachne, Crimson Isle minibosses) but none for a regular slayer boss,
        // confirming Hypixel simply never sends one. The sidebar entering "Slay the boss!" is the
        // only signal there is.
        if (phase == Phase.FIGHT && newType != null) {
            // The fight coming back while a kill inferred from it vanishing is still held: the
            // sidebar flickered, the boss never died, and the fight simply goes on.
            if (pendingKill == newType && pendingKillInferred) {
                pendingKill = null
                killCountedThisFight = false
                return
            }
            killCountedThisFight = false
            pendingSpawn = newType
            pendingSpawnVerified = previous == Phase.GRINDING
        }
    }

    /** For tests: one readable sidebar sample, as [refresh] would pass it on. */
    internal fun sampleForTest(progress: String, type: SlayerType?) {
        detectKill(progress, type)
    }

    /** For tests: back to the state of a fresh start, minus the world-change baseline. */
    internal fun resetForTest() {
        lastProgress = ""
        lastPhase = Phase.NONE
        pendingKill = null
        pendingSpawn = null
        killCountedThisFight = false
        freshLevel = false
        questVoidedAtMs = 0L
        lastSeenType = null
    }

    /** For tests: the slayer most recently seen, which a kill is credited to. */
    internal fun seeForTest(type: SlayerType) {
        lastSeenType = type
    }

    /** Hands out one kill for the boss now up, whichever source reports it first. */
    private fun emitKill(type: SlayerType?, atMs: Long, inferred: Boolean, timed: Boolean) {
        if (type == null || killCountedThisFight) return
        killCountedThisFight = true
        pendingKill = type
        pendingKillAtMs = atMs
        pendingKillInferred = inferred
        pendingKillTimed = timed
    }

    /**
     * Hypixel's "SLAYER QUEST COMPLETE!" or "NICE! SLAYER BOSS SLAIN!" line. A kill source of its
     * own, deduplicated against the sidebar's, so a kill the sidebar missed still reaches the
     * session, the timer and the XP. It also confirms a kill the sidebar only inferred, which then no
     * longer waits for a veto.
     */
    fun onChatKill(type: SlayerType?) {
        if (pendingKill != null && pendingKillInferred) {
            pendingKillInferred = false
            return
        }
        emitKill(type, System.currentTimeMillis(), inferred = false, timed = lastPhase == Phase.FIGHT)
    }

    /**
     * Returns the slayer whose boss just died, once per kill, or null.
     *
     * An inferred kill is withheld until [KILL_CONFIRM_MS] has passed, so that a quest which ended
     * some other way can still be vetoed by [onQuestVoided] before anything is counted.
     */
    fun consumeKill(): SlayerType? {
        val killed = pendingKill ?: return null
        if (pendingKillInferred && System.currentTimeMillis() - pendingKillAtMs < KILL_CONFIRM_MS) return null

        pendingKill = null
        killDetectedAtMs = pendingKillAtMs
        killTimed = pendingKillTimed
        return killed
    }

    /** Returns the slayer whose boss just spawned, once per spawn, or null. */
    fun consumeSpawn(): SlayerType? {
        val spawned = pendingSpawn
        pendingSpawn = null
        if (spawned != null) spawnVerified = pendingSpawnVerified
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
