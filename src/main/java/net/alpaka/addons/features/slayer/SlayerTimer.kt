package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.notification.AlpakaNotifications
import net.alpaka.addons.config.AlpakaStats

/**
 * Times a single slayer boss fight, from the boss appearing to it dying.
 *
 * Measured over the boss entity's own lifetime, watched by [SlayerBossEntityTracker]: it is timed
 * from the boss reaching the world to its health reaching zero. That is what SkyHanni reports and so
 * what a slayer time means to anyone comparing one.
 *
 * The sidebar is the fallback, not the method. Its progress line entering and leaving "Slay the
 * boss!" is the only thing Hypixel ever *states* about a boss fight, but it flips a second or two
 * before the boss is in the world and again after it is gone, so a sidebar-timed fight carries
 * seconds that were not fight. It is used per end of the fight, only where the entity could not be
 * watched - which yields the longer of the two numbers, so a fight this mod could not follow
 * properly under-reports itself rather than inventing a personal best.
 *
 * Which slayer the fight belongs to comes from the sidebar regardless, and so does the fact that a
 * fight ended at all - a cancelled quest and a player death both have to be told apart from a kill,
 * and only [SlayerQuestDetector] can do that.
 *
 * Kept separate from [SlayerSessionTracker] even though that also times bosses. The session's timing
 * feeds an average across a session and is meaningless on its own; this is one fight, shown live and
 * announced when it ends, and it works with the session HUD switched off.
 */
object SlayerTimer {

    /**
     * How long after the boss entity died that death still counts as the kill being reported.
     *
     * The sidebar catches up within a tick or two, so this only has to cover that hop. It matters
     * that it expires at all: losing the boss to render distance mid-fight also reads as a death,
     * and timing a kill from that moment would report a fight that never finished.
     */
    private const val ENTITY_DEATH_WINDOW_MS = 5_000L

    /**
     * How long the clock waits for the boss to turn up before starting from the sidebar instead.
     *
     * Generously past the delay actually measured between Hypixel announcing a boss and putting it
     * in the world - under half a second for an Inferno Demonlord - so a slow spawn is still timed
     * properly. What this is for is the case where the boss is never found at all: without it the
     * HUD would sit blank for the whole fight and only produce a number at the end.
     */
    private const val SIDEBAR_FALLBACK_MS = 5_000L

    /**
     * When the sidebar announced the boss, or 0 while no fight is running.
     *
     * Recorded whichever mode is on. It is what says a fight is in progress, and it is what the
     * entity mode falls back to when the boss entity is never found.
     */
    private var sidebarStartMs = 0L

    /**
     * When the clock being shown started, or 0 while it has not started yet.
     *
     * In entity mode this stays 0 until the boss is actually in the world, which is the point of
     * that mode: the spawn animation is not part of the fight, so there is nothing to time yet.
     */
    private var startMs = 0L

    /** Which slayer the running fight belongs to, for the announcement. */
    private var runningType: SlayerType? = null

    /**
     * The last completed fight, kept briefly so the HUD can show the final time rather than blanking
     * the instant the boss dies.
     */
    private var lastResultMs: Long? = null
    private var lastResultAtMs = 0L

    /** How long the finished time stays on the HUD after the kill. */
    private const val RESULT_LINGER_MS = 5_000L

    /**
     * True while the clock is running.
     *
     * Not quite the same as "a boss is up": in entity mode a fight is under way but nothing is being
     * timed yet for the second or two between the sidebar announcing the boss and the boss arriving.
     */
    val isRunning: Boolean get() = startMs != 0L

    /**
     * The time to display: the running fight's, or the one just finished for [RESULT_LINGER_MS].
     * Null when there is nothing to show.
     */
    fun displayMs(): Long? {
        if (startMs != 0L) return System.currentTimeMillis() - startMs

        val last = lastResultMs ?: return null
        if (System.currentTimeMillis() - lastResultAtMs > RESULT_LINGER_MS) return null
        return last
    }

    /** Whether what [displayMs] returns is a finished time rather than a running one. */
    fun isShowingResult(): Boolean = startMs == 0L && displayMs() != null

    /** The boss the running - or just finished - fight belongs to. */
    fun currentType(): SlayerType? = runningType

    /** The fastest kill ever recorded for a slayer, or null if it has none yet. */
    fun personalBest(type: SlayerType): Long? {
        val data = AlpakaStats.slayerBossMap()[type] ?: return null
        return if (data.bestBossMs > 0L) data.bestBossMs else null
    }

    /** The sidebar says the boss is up. */
    fun onBossSpawned(type: SlayerType) {
        // Announced before the timer's own switch is consulted, because the alert is a separate
        // feature that happens to hang off the same moment.
        if (AlpakaConfig.instance.bossSpawnAlertEnabled) {
            AlpakaNotifications.send("Boss Spawned", type.display + " slayer boss is up")
        }

        if (!AlpakaConfig.instance.slayerTimerEnabled) return
        sidebarStartMs = System.currentTimeMillis()
        runningType = type
        lastResultMs = null

        // Whatever the tracker already has stands: it watches the whole quest, not just the fight,
        // so a boss that reached the world before the scoreboard caught up is already stamped.
        // Otherwise this stays 0 until [tick] sees the boss arrive, or gives up waiting for it.
        startMs = SlayerBossEntityTracker.spawnedAtMs ?: 0L
    }

    /**
     * The boss died. Closes the clock, records a personal best, and announces the time.
     *
     * Does nothing when no fight was running: a kill can be reported without a spawn ever having
     * been seen - joining mid-fight, or the sidebar skipping straight past "Slay the boss!" - and
     * timing that from nothing would invent a number.
     */
    fun onBossKilled(type: SlayerType, killedAtMs: Long = System.currentTimeMillis()) {
        if (!AlpakaConfig.instance.slayerTimerEnabled) return
        if (sidebarStartMs == 0L) return

        // The moment the kill was *noticed*, not the moment it is being acted on. An inferred kill
        // is held back for a second so that dying can still veto it, and timing the fight to the end
        // of that hold would add the hold onto every reported time.
        val now = killedAtMs
        // Either end falls back to the sidebar's own moment when the boss entity was not seen - in
        // the Rift, say, where Hypixel puts no owner tag on the Bloodfiend. That is the longer of
        // the two numbers, so a fight this mod could not watch properly under-reports itself rather
        // than inventing a personal best.
        val from = if (startMs != 0L) startMs else sidebarStartMs
        val death = SlayerBossEntityTracker.diedAtMs?.takeIf { now - it <= ENTITY_DEATH_WINDOW_MS }
        val elapsed = ((death ?: now) - from).coerceAtLeast(0L)

        lastFight = FightBreakdown(
            type = type,
            sidebarStartMs = sidebarStartMs,
            entityStartMs = if (startMs != 0L && startMs != sidebarStartMs) startMs else null,
            entityDeathMs = death,
            sidebarKillMs = now,
            reportedMs = elapsed,
        )

        sidebarStartMs = 0L
        startMs = 0L
        SlayerBossEntityTracker.reset()
        runningType = type
        lastResultMs = elapsed
        // Wall clock, not the kill's own moment: this only drives how long the finished time lingers
        // on the HUD, which should be counted from when it went up.
        lastResultAtMs = System.currentTimeMillis()

        val data = AlpakaStats.slayerBossMap().getOrPut(type) { AlpakaConfig.SlayerData() }
        val previousBest = if (data.bestBossMs > 0L) data.bestBossMs else null
        val isBest = previousBest == null || elapsed < previousBest
        if (isBest) {
            data.bestBossMs = elapsed
            AlpakaStats.save()
        }

        if (AlpakaConfig.instance.slayerTimerChatEnabled) {
            announce(type, elapsed, previousBest, isBest)
        }
    }

    private fun announce(type: SlayerType, elapsed: Long, previousBest: Long?, isBest: Boolean) {
        val time = format(elapsed)
        if (isBest) {
            val suffix = if (previousBest == null) "" else " §7(was §e${format(previousBest)}§7)"
            SlayerDropTracker.sendModMessage(
                "§7${type.display} boss killed in §a$time §7- §6new personal best!$suffix",
            )
        } else {
            SlayerDropTracker.sendModMessage(
                "§7${type.display} boss killed in §e$time §7(best §a${format(previousBest!!)}§7)",
            )
        }
    }

    /**
     * A duration as the slayer community quotes boss times: seconds with one decimal below a
     * minute, minutes and seconds above it.
     */
    fun format(ms: Long): String {
        // Locale.ROOT, not the system default: on a German machine the default renders the decimal
        // as a comma, which lands next to figures like "1,284,500" that use the comma as a thousands
        // separator. The mod's text is English throughout, so the separator should be too.
        if (ms < 60_000L) return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0)
        val totalSeconds = ms / 1000L
        return String.format(java.util.Locale.ROOT, "%dm %02ds", totalSeconds / 60L, totalSeconds % 60L)
    }

    /**
     * Called once per client tick. Drops a running fight when the world underneath it changes.
     *
     * Not an optional tidy-up: on Hypixel every warp is a new server, and a fight cannot survive
     * one. Without this the clock would keep counting across the switch and announce a boss time
     * that is mostly loading screen. Leaving the game does the same.
     */
    fun tick() {
        val level = net.minecraft.client.Minecraft.getInstance().level
        if (level !== trackedLevel) {
            trackedLevel = level
            clear()
        }

        // Driven off the quest rather than off a running fight. Hypixel does not promise to update
        // the scoreboard before it puts the boss in the world, and a boss that arrives first would
        // otherwise be timed from the sidebar catching up instead of from its own arrival.
        //
        // currentOrRecent rather than activeType, and that is not a detail: Hypixel clears the quest
        // off the sidebar the instant the boss dies, so reading activeType switched the tracker off
        // in the very tick it had to watch the death happen. Every fight then reported "boss death
        // never seen" and fell back to the sidebar's kill. The recent-quest memory keeps it running
        // past the moment the sidebar goes quiet.
        val quest = SlayerQuestDetector.currentOrRecent() ?: return
        SlayerBossEntityTracker.tick(quest, SlayerQuestDetector.inBossFight)

        if (sidebarStartMs == 0L) return

        val entityStart = SlayerBossEntityTracker.spawnedAtMs
        if (entityStart != null) {
            // Assigned even over a fallback already taken. A boss that turns up late is still the
            // better answer, and correcting the clock costs one visible jump on the HUD against a
            // reported time that would otherwise be seconds too long.
            startMs = entityStart
        } else if (startMs == 0L && System.currentTimeMillis() - sidebarStartMs >= SIDEBAR_FALLBACK_MS) {
            startMs = sidebarStartMs
        }
    }

    /**
     * What the last finished fight was measured from and to, for `/alpakadebug`.
     *
     * Kept because "my time does not match SkyHanni's" is answerable only by seeing which end
     * differs, and by how much - that is what turned a several-second discrepancy into two concrete
     * bugs. Every figure is a wall-clock instant so the offsets can be worked out against each
     * other; [entityStartMs] and [entityDeathMs] are null where the fight fell back to the sidebar,
     * which is itself the answer in that case.
     */
    class FightBreakdown(
        val type: SlayerType,
        val sidebarStartMs: Long,
        val entityStartMs: Long?,
        val entityDeathMs: Long?,
        val sidebarKillMs: Long,
        val reportedMs: Long,
    ) {

        /** The breakdown as chat lines, for `/alpakadebug`. */
        fun lines(): List<String> = buildList {
            add("§7${type.display}: reported §a${format(reportedMs)}§7, sidebar would say §e${format(sidebarKillMs - sidebarStartMs)}")
            add(
                if (entityStartMs == null) "§c  boss entity never found §7- start fell back to the sidebar"
                else "§7  entity appeared §f${offset(entityStartMs - sidebarStartMs)} §7vs the sidebar's \"Slay the boss!\"",
            )
            add(
                if (entityDeathMs == null) "§c  boss death never seen §7- end fell back to the sidebar"
                else "§7  entity died §f${offset(entityDeathMs - sidebarKillMs)} §7vs the sidebar's kill",
            )
        }

        /** A signed offset in seconds, e.g. "+1.35s" or "-0.62s". */
        private fun offset(ms: Long): String = String.format(java.util.Locale.ROOT, "%+.2fs", ms / 1000.0)
    }

    var lastFight: FightBreakdown? = null
        private set

    private var trackedLevel: Any? = null

    /**
     * Forgets the recorded best for one slayer, or for all of them when [type] is null.
     *
     * Returns how many were actually cleared, so the command can say so rather than claim to have
     * reset something that was never set. Exists because a best is all-time and nothing else can
     * remove one - and the detector used to record phantom bests from cancelled quests, which left
     * a figure on screen that no fight had ever produced.
     */
    fun clearPersonalBest(type: SlayerType?): Int {
        var cleared = 0
        for ((slayer, data) in AlpakaStats.slayerBossMap()) {
            if (type != null && slayer != type) continue
            if (data.bestBossMs > 0L) {
                data.bestBossMs = -1L
                cleared++
            }
        }
        if (cleared > 0) AlpakaStats.save()
        return cleared
    }

    /** Drops a running fight, for a world change or a session reset. */
    fun clear() {
        sidebarStartMs = 0L
        startMs = 0L
        runningType = null
        lastResultMs = null
        SlayerBossEntityTracker.reset()
    }
}
