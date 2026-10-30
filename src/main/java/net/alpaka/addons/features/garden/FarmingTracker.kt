package net.alpaka.addons.features.garden

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * The live figures behind the Farming HUD.
 *
 * Everything is read from what Hypixel already shows: the Farming XP in the action bar, the Crop
 * Milestones, Pests and Jacob's Contest widgets in the tab list, and the chat message a Pest
 * Repellent gives. Nothing is sent to the server.
 */
object FarmingTracker {

    /** How often the tab list is read. Hypixel refreshes its widgets about once a second. */
    private const val READ_INTERVAL_TICKS = 20

    /**
     * A pause longer than this between two gains is a break, not farming: its time is left out of
     * the rates, so walking to the desk or answering a visitor does not drag them down.
     */
    private const val XP_IDLE_MS = 30_000L
    /** The milestone percentage moves in 0.1 steps, which at high tiers can take a while. */
    private const val MILESTONE_IDLE_MS = 120_000L
    /** After this long without farming, the next gain starts the rates over. */
    private const val RESET_AFTER_MS = 5 * 60_000L
    /** Less farming than this is too little to say a rate from. */
    private const val MIN_ACTIVE_MS = 10_000L

    private var ticks = 0

    // Farming XP per hour.
    private var lastXpProgress: String? = null
    private var lastXpCurrent: Double? = null
    private var lastXpNeeded: String? = null
    private var lastXpAtMs = 0L
    private var xpGained = 0.0
    private var xpActiveMs = 0L

    // Crop milestone, and how fast it fills.
    var milestone: FarmingParse.Milestone? = null
        private set
    private var milestoneKey: String? = null
    private var milestoneLastPercent = 0.0
    private var milestoneLastChangeMs = 0L
    private var milestoneGained = 0.0
    private var milestoneActiveMs = 0L

    /** Whether the tab list has a Crop Milestones widget at all; it can be switched off in /tab. */
    var hasMilestoneWidget = false
        private set

    /** The Pests widget's cooldown as Hypixel writes it ("1m 58s", "READY", "MAX PESTS"). */
    var pestCooldown: String? = null
        private set
    var hasPestWidget = false
        private set

    /** The Jacob's Contest widget, used when the calendar from Elite is not available. */
    var tabContestTime: String? = null
        private set
    var tabContestCrops: List<String> = emptyList()
        private set

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> onMessage(message, overlay) }
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> onMessage(message, overlay) }
    }

    private fun onMessage(message: Component, overlay: Boolean) {
        if (!AlpakaConfig.instance.farmingHudEnabled) return
        val text = SkyblockUtils.cleanColor(message.string)
        if (overlay) onActionBar(text, System.currentTimeMillis()) else onChat(text)
    }

    private fun onChat(text: String) {
        val repellent = FarmingParse.repellent(text) ?: return
        val cfg = AlpakaConfig.instance
        cfg.farmingRepellentUntilMs = System.currentTimeMillis() + repellent.minutes * 60_000L
        cfg.farmingRepellentMax = repellent.factor >= 4
        AlpakaConfig.save()
    }

    internal fun onActionBar(text: String, now: Long) {
        val gain = FarmingParse.xpGain(text) ?: return
        // Hypixel sends the action bar again and again while the gain is on it; only a new progress
        // figure is a new gain.
        if (gain.progress == lastXpProgress) return

        // With "123,456/200,000" the difference to the last figure is exact, even when two gains
        // came in between two action bars. Across a level up, or with a percentage, the gain shown.
        val previous = lastXpCurrent
        val amount = if (previous != null && gain.current != null && gain.needed == lastXpNeeded && gain.current > previous) {
            gain.current - previous
        } else {
            gain.gained
        }
        lastXpProgress = gain.progress
        lastXpCurrent = gain.current
        lastXpNeeded = gain.needed

        val gap = now - lastXpAtMs
        if (lastXpAtMs == 0L || gap > RESET_AFTER_MS) {
            xpGained = 0.0
            xpActiveMs = 0L
        } else if (gap <= XP_IDLE_MS) {
            xpGained += amount
            xpActiveMs += gap
        }
        lastXpAtMs = now
    }

    /** Farming XP per hour of farming; null until there has been enough of it. */
    fun xpPerHour(now: Long = System.currentTimeMillis()): Double? {
        if (xpActiveMs < MIN_ACTIVE_MS || now - lastXpAtMs > RESET_AFTER_MS) return null
        return xpGained / xpActiveMs * 3_600_000.0
    }

    /** Milliseconds of farming until the next crop milestone tier, at the rate so far. */
    fun milestoneEtaMs(): Long? {
        val shown = milestone ?: return null
        val percent = shown.percent ?: return null
        if (milestoneActiveMs < MIN_ACTIVE_MS || milestoneGained <= 0.0) return null
        val perMs = milestoneGained / milestoneActiveMs
        return ((100.0 - percent) / perMs).toLong()
    }

    /** Time left on the Pest Repellent, in milliseconds; null when none is active. */
    fun repellentLeftMs(now: Long = System.currentTimeMillis()): Long? {
        val left = AlpakaConfig.instance.farmingRepellentUntilMs - now
        return if (left > 0) left else null
    }

    private fun tick(mc: Minecraft) {
        if (++ticks % READ_INTERVAL_TICKS != 0) return
        if (!AlpakaConfig.instance.farmingHudEnabled) return
        if (mc.player == null || !SkyblockUtils.isOnGarden()) return
        JacobContests.tick()
        readTab(mc, System.currentTimeMillis())
    }

    private fun readTab(mc: Minecraft, now: Long) {
        // In the order vanilla draws them: the widgets are only told apart by what follows what.
        val accessor = mc.gui.hud.tabList as PlayerTabOverlayAccessor
        val lines = accessor.`alpaka$getPlayerInfos`().map { info ->
            SkyblockUtils.cleanColor(mc.gui.hud.tabList.getNameForDisplay(info).string)
        }

        val pests = FarmingParse.widget(lines, "Pests:")
        hasPestWidget = pests != null
        pestCooldown = pests?.let { FarmingParse.field(it, "Cooldown") }

        val milestones = FarmingParse.widget(lines, "Crop Milestones:")
        hasMilestoneWidget = milestones != null
        milestones?.let { FarmingParse.milestone(it) }?.let { onMilestone(it, now) }

        val contest = FarmingParse.widget(lines, "Jacob's Contest:")
        tabContestTime = if (contest != null) FarmingParse.widgetTitleValue(lines, "Jacob's Contest:") else null
        tabContestCrops = contest?.let { FarmingParse.contestCrops(it) } ?: emptyList()
    }

    internal fun onMilestone(read: FarmingParse.Milestone, now: Long) {
        milestone = read
        val percent = read.percent ?: return
        val key = "${read.crop} ${read.tier}"
        // A new crop or tier measures afresh; so does a long break, after which the setup may differ.
        if (key != milestoneKey || percent < milestoneLastPercent || now - milestoneLastChangeMs > RESET_AFTER_MS) {
            milestoneKey = key
            milestoneLastPercent = percent
            milestoneLastChangeMs = now
            milestoneGained = 0.0
            milestoneActiveMs = 0L
            return
        }
        if (percent == milestoneLastPercent) return
        val gap = now - milestoneLastChangeMs
        if (gap <= MILESTONE_IDLE_MS) {
            milestoneGained += percent - milestoneLastPercent
            milestoneActiveMs += gap
        }
        milestoneLastPercent = percent
        milestoneLastChangeMs = now
    }
}
