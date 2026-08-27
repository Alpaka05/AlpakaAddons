package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import kotlin.math.abs

/**
 * Per-slayer session statistics for the slayer HUD: bosses killed, time spent, boss times, and
 * slayer XP earned.
 *
 * Everything here is derived from what the server already shows the client - the sidebar and chat -
 * plus the player's own input. Nothing is scanned, probed or requested.
 *
 * Statistics are kept per [SlayerType] rather than globally so that switching slayers mid-play does
 * not blend two unrelated sessions together, which is also what lets the HUD show only the slayer
 * currently being run.
 */
object SlayerSessionTracker {

    /**
     * How often the sidebar's area line is re-read, in milliseconds.
     *
     * Assembling the sidebar walks the scoreboard and allocates, so it is not something to do on
     * every tick, and a zone change takes far longer than this to matter.
     */
    private const val AREA_REFRESH_MS = 500L

    /**
     * Upper bound on how much time a single tick may contribute.
     *
     * A client tick is 50ms, so anything larger means the game was stalled - alt-tabbed, a lag
     * spike, a world load - and counting the whole gap would inflate the session clock.
     */
    private const val MAX_TICK_CONTRIBUTION_MS = 1_000L

    /** Movement below this is treated as standing still, so idle jitter cannot defeat the AFK check. */
    private const val MOVE_EPSILON = 0.01
    private const val LOOK_EPSILON = 0.5f

    /** One slayer's running totals for this session. Reset when the game closes; never persisted. */
    class Session {
        /**
         * Whether this session has actually begun.
         *
         * A slayer quest survives logging out, so one is very often already on the sidebar the moment
         * the game starts - which had the clock running from the main menu onwards while the player
         * was nowhere near a boss. Time only accrues once the player has been seen in the slayer's own
         * zone, or has killed a boss, which is what "doing a slayer" actually means.
         */
        var started: Boolean = false
        var activeMs: Long = 0L
        var bossCount: Int = 0
        var totalBossMs: Long = 0L
        var timedBossCount: Int = 0
        var xpGained: Long = 0L

        /** Mean time to kill a boss, or null until at least one boss has been timed end to end. */
        fun averageBossMs(): Long? =
            if (timedBossCount > 0) totalBossMs / timedBossCount else null

        /** Kills extrapolated to an hour of *active* play, or null before any time has accrued. */
        fun bossesPerHour(): Double? {
            if (activeMs <= 0L || bossCount <= 0) return null
            return bossCount * 3_600_000.0 / activeMs
        }

        /** Slayer XP gained extrapolated to an hour of *active* play, or null before any time has accrued. */
        fun xpPerHour(): Double? {
            if (activeMs <= 0L || xpGained <= 0L) return null
            return xpGained * 3_600_000.0 / activeMs
        }
    }

    private val sessions = HashMap<SlayerType, Session>()

    private var lastTickMs = 0L
    private var lastActivityMs = 0L

    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastYaw = 0.0f
    private var lastPitch = 0.0f

    /** When the boss currently being fought spawned, or 0 when no boss is up. */
    private var bossStartMs = 0L

    /** Set by clicking the HUD; holds the clock until clicked again. */
    private var manuallyPaused = false

    private var cachedSidebarLines: List<String> = emptyList()
    private var areaCheckedAtMs = 0L


    /**
     * Last "Stored XP" seen on the RNG meter, per slayer.
     *
     * Hypixel prints `RNG Meter - 69,300 Stored XP` on every boss kill, and the step between two of
     * those is the slayer XP that kill awarded - 550 for an Inferno Demonlord IV in a captured
     * session. This is the only place the client is told an actual XP figure, so it is preferred
     * over guessing from the tier; [xpForTier] covers the case where no meter is set for the slayer
     * and the message therefore never arrives.
     */
    private val lastStoredXp = HashMap<SlayerType, Long>()

    /** Why the clock is currently stopped, or null while it is running. */
    enum class PauseReason { MANUAL, IDLE, OUTSIDE_AREA }

    /** True while the session clock is stopped, for any reason. */
    val isPaused: Boolean
        get() = pauseReason() != null

    /** Whether the clock is held by an explicit click rather than by idling or leaving. */
    val isManuallyPaused: Boolean
        get() = manuallyPaused

    /**
     * Flips the manual hold. Also counts as activity, so releasing the hold does not immediately
     * re-pause on the idle timer that was running while the player was stopped.
     */
    fun toggleManualPause() {
        manuallyPaused = !manuallyPaused
        if (!manuallyPaused) lastActivityMs = System.currentTimeMillis()
    }

    /**
     * Works out whether the clock should be running, and if not, why.
     *
     * A manual hold wins over everything, then leaving the zone, then idling - ordered so the
     * reported reason is the one the player most recently caused.
     */
    fun pauseReason(): PauseReason? {
        if (manuallyPaused) return PauseReason.MANUAL

        if (AlpakaConfig.instance.slayerHudPauseOutsideArea && hasLeftSlayerArea()) {
            return PauseReason.OUTSIDE_AREA
        }

        if (lastActivityMs == 0L) return null
        val idleLimitMs = (AlpakaConfig.instance.slayerHudAfkPauseSeconds * 1000.0f).toLong()
        if (idleLimitMs > 0L && System.currentTimeMillis() - lastActivityMs > idleLimitMs) {
            return PauseReason.IDLE
        }
        return null
    }

    /**
     * Whether any sidebar line names one of this slayer's zones.
     *
     * Every line is searched rather than just the area line. Locating that line means finding
     * Hypixel's zone marker, and depending on that glyph is fragile - one unexpected marker and the
     * area reads as empty, which silently turned the whole "pause outside the area" feature into a
     * no-op. Zone names like "Smoldering Tomb" are distinctive enough that a plain search over the
     * sidebar cannot reasonably collide with anything else on it.
     */
    fun isInSlayerArea(type: SlayerType): Boolean {
        if (type.slayerAreas.isEmpty()) return false

        val now = System.currentTimeMillis()
        if (now - areaCheckedAtMs >= AREA_REFRESH_MS) {
            areaCheckedAtMs = now
            cachedSidebarLines = SkyblockUtils.getSidebarLines()
        }

        if (cachedSidebarLines.isEmpty()) return false
        return cachedSidebarLines.any { line -> type.isSlayerArea(line) }
    }

    /**
     * Whether the sidebar shows the player outside the active slayer's zone.
     *
     * Fails open while the sidebar is unreadable - no lines at all means "unknown", not "elsewhere" -
     * so a momentary gap between world loads cannot stop the clock.
     */
    private fun hasLeftSlayerArea(): Boolean {
        val type = SlayerQuestDetector.activeType ?: return false
        if (type.slayerAreas.isEmpty()) return false
        if (cachedSidebarLines.isEmpty() && SkyblockUtils.getSidebarLines().isEmpty()) return false
        return !isInSlayerArea(type)
    }

    /**
     * Whether any sidebar line names a zone this slayer is run in.
     *
     * Uses the same cached sidebar read as [isInSlayerArea], but against the wider
     * [SlayerType.trackerAreas] list - see the note there on why the two lists differ.
     */
    fun isInTrackerArea(type: SlayerType): Boolean {
        if (type.trackerAreas.isEmpty()) return false

        val now = System.currentTimeMillis()
        if (now - areaCheckedAtMs >= AREA_REFRESH_MS) {
            areaCheckedAtMs = now
            cachedSidebarLines = SkyblockUtils.getSidebarLines()
        }

        if (cachedSidebarLines.isEmpty()) return false
        return cachedSidebarLines.any { line -> type.isTrackerArea(line) }
    }

    /**
     * Whether the slayer HUD belongs on screen at all.
     *
     * Deliberately independent of the AFK and leave-the-area pause settings, which only stop the
     * clock on a HUD that is already up.
     *
     *  - The session must have begun. A slayer quest survives logging out, so one is very often
     *    already sitting on the sidebar the moment the player joins, with no boss fought and nothing
     *    worth reporting.
     *  - The player must be somewhere this slayer is actually run, which mirrors the condition
     *    SkyHanni's slayer profit tracker shows under (`isInCorrectArea`: the current area's slayer
     *    type equals the active quest's). Switching [AlpakaConfig.slayerHudOnlyInSlayerAreas] off
     *    drops that requirement and shows the HUD wherever the quest is active.
     *
     * This used to be gated on a single *island* per slayer instead, which hid the HUD for any
     * slayer run away from its home island - a spider quest on the Crimson Isle, for instance.
     */
    fun shouldShowHud(type: SlayerType): Boolean {
        if (!session(type).started) return false
        if (!AlpakaConfig.instance.slayerHudOnlyInSlayerAreas) return true
        return isInTrackerArea(type)
    }

    /** This slayer's session, creating it on first use. */
    fun session(type: SlayerType): Session = sessions.getOrPut(type) { Session() }

    /** The session for the slayer being run right now, or null when no quest is active. */
    fun currentSession(): Session? = SlayerQuestDetector.currentOrRecent()?.let { session(it) }

    /** How long the boss currently up has been alive, or null when no boss is up. */
    fun currentBossElapsedMs(): Long? =
        if (bossStartMs == 0L) null else System.currentTimeMillis() - bossStartMs

    /**
     * Advances the session clock. Called once per client tick.
     *
     * Time only accrues while a slayer quest is actually on the sidebar, so idling in a hub between
     * quests does not count towards bosses-per-hour.
     */
    fun tick() {
        val now = System.currentTimeMillis()
        val previousTickMs = lastTickMs
        lastTickMs = now

        val player = Minecraft.getInstance().player
        if (player == null) {
            lastActivityMs = 0L
            return
        }

        if (hasMoved(player.x, player.y, player.z, player.yRot, player.xRot)) {
            lastActivityMs = now
        }
        lastX = player.x
        lastY = player.y
        lastZ = player.z
        lastYaw = player.yRot
        lastPitch = player.xRot

        // First tick after joining: start the clock rather than crediting time since epoch.
        if (lastActivityMs == 0L) lastActivityMs = now
        if (previousTickMs == 0L) return

        val type = SlayerQuestDetector.activeType ?: return
        val session = session(type)

        // Having a quest up somewhere the slayer is actually run is what starts the clock. Until
        // then the session stays at zero no matter how long the game has been open.
        //
        // Tested against the wider tracker areas, not the narrow pause zone: gating the start on the
        // boss's own spawn room meant a session never began while grinding the approach to it, and
        // the HUD stayed hidden for the whole run.
        val inRunnableArea = !AlpakaConfig.instance.slayerHudOnlyInSlayerAreas || isInTrackerArea(type)
        if (!session.started && inRunnableArea) session.started = true
        if (!session.started) return

        if (isPaused) return

        val elapsed = (now - previousTickMs).coerceAtMost(MAX_TICK_CONTRIBUTION_MS)
        session.activeMs += elapsed
    }

    private fun hasMoved(x: Double, y: Double, z: Double, yaw: Float, pitch: Float): Boolean =
        abs(x - lastX) > MOVE_EPSILON ||
        abs(y - lastY) > MOVE_EPSILON ||
        abs(z - lastZ) > MOVE_EPSILON ||
        abs(yaw - lastYaw) > LOOK_EPSILON ||
        abs(pitch - lastPitch) > LOOK_EPSILON

    /** The sidebar reports the boss is up. Starts the boss timer. */
    fun onBossSpawned(@Suppress("UNUSED_PARAMETER") type: SlayerType) {
        bossStartMs = System.currentTimeMillis()
        lastActivityMs = System.currentTimeMillis()
    }

    /**
     * The boss died. Counts the kill, closes the boss timer, and credits XP.
     *
     * Killing a boss counts as activity in its own right, so a fight that ends after a long
     * stationary ranged phase does not leave the session looking idle.
     */
    fun onBossKilled(type: SlayerType) {
        val now = System.currentTimeMillis()
        lastActivityMs = now

        val session = session(type)
        // Killing a boss is proof the slayer is being run, whatever the sidebar says about zones.
        session.started = true
        session.bossCount++

        if (bossStartMs != 0L) {
            session.totalBossMs += now - bossStartMs
            session.timedBossCount++
            bossStartMs = 0L
        }

        // Only used when the RNG meter never told us the real figure; see lastStoredXp.
        if (!lastStoredXp.containsKey(type)) {
            session.xpGained += xpForTier(SlayerQuestDetector.tier)
        }
    }

    /**
     * Records an RNG meter reading, crediting the step since the previous one as slayer XP.
     *
     * A reading that has gone *down* means the meter completed and reset, so the step is
     * meaningless and only the new baseline is kept.
     */
    fun onRngMeterReading(type: SlayerType, storedXp: Long) {
        val previous = lastStoredXp.put(type, storedXp)
        if (previous != null && storedXp > previous) {
            session(type).xpGained += storedXp - previous
        }
    }

    /**
     * Slayer XP a boss of this tier awards, used only as a fallback.
     *
     * These are the long-standing Skyblock per-tier values. They are a fallback rather than the
     * primary source because a real reading of 550 for a tier 4 boss shows the awarded amount can
     * exceed the base figure.
     */
    private fun xpForTier(tier: Int): Long = when (tier) {
        1 -> 5L
        2 -> 25L
        3 -> 100L
        4 -> 500L
        5 -> 1_500L
        else -> 0L
    }

    /** Clears every session. Used when the player disconnects. */
    fun reset() {
        sessions.clear()
        lastStoredXp.clear()
        // A fight cannot survive the disconnect that ended it; leaving the timer running would have
        // it counting across the gap and announce a boss time measured in minutes of menu.
        SlayerTimer.clear()
        bossStartMs = 0L
        lastTickMs = 0L
        lastActivityMs = 0L
        manuallyPaused = false
        cachedSidebarLines = emptyList()
        areaCheckedAtMs = 0L
    }

    /**
     * Clears the session for the slayer being run, or every session when none is active.
     *
     * Returns the slayer whose session was cleared, or null when everything was cleared, so the
     * command can say what it did.
     */
    fun resetCurrent(): SlayerType? {
        manuallyPaused = false
        SlayerTimer.clear()
        bossStartMs = 0L

        val type = SlayerQuestDetector.currentOrRecent()
        if (type == null) {
            sessions.clear()
            lastStoredXp.clear()
            return null
        }

        sessions.remove(type)
        lastStoredXp.remove(type)
        return type
    }
}
