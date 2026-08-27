package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaStats

/**
 * Times a single slayer boss fight, from the boss appearing to it dying.
 *
 * Driven by the same two signals the session tracker uses - the sidebar's progress line entering
 * and leaving "Slay the boss!" - because that is the only thing Hypixel sends that marks a boss
 * fight. There is no chat announcement for a slayer boss spawning at all, so nothing else could
 * start the clock.
 *
 * Kept separate from [SlayerSessionTracker] even though that also times bosses. The session's timing
 * feeds an average across a session and is meaningless on its own; this is one fight, shown live and
 * announced when it ends, and it works with the session HUD switched off.
 */
object SlayerTimer {

    /** When the boss currently up appeared, or 0 while no fight is running. */
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

    /** True while a boss is up and being timed. */
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
        if (!AlpakaConfig.instance.slayerTimerEnabled) return
        startMs = System.currentTimeMillis()
        runningType = type
        lastResultMs = null
    }

    /**
     * The boss died. Closes the clock, records a personal best, and announces the time.
     *
     * Does nothing when no fight was running: a kill can be reported without a spawn ever having
     * been seen - joining mid-fight, or the sidebar skipping straight past "Slay the boss!" - and
     * timing that from nothing would invent a number.
     */
    fun onBossKilled(type: SlayerType) {
        if (!AlpakaConfig.instance.slayerTimerEnabled) return
        if (startMs == 0L) return

        val elapsed = System.currentTimeMillis() - startMs
        startMs = 0L
        runningType = type
        lastResultMs = elapsed
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
    }

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
        startMs = 0L
        runningType = null
        lastResultMs = null
    }
}
