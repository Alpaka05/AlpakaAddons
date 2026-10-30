package net.alpaka.addons.features.garden

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.tablist.TabListParser
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The live figures behind the Farming HUD.
 *
 * Everything is read from what Hypixel already shows: the Farming XP in the action bar, the Crop
 * Milestones, Pests and Jacob's Contest widgets in the tab list, the Crop Milestones menu, and the
 * chat messages for spawned pests and a used Pest Repellent. Nothing is sent to the server.
 */
object FarmingTracker {

    /** How often the tab list is read. Hypixel refreshes its widgets about once a second. */
    private const val READ_INTERVAL_TICKS = 20
    /** How often an open Crop Milestones menu is read; it fills in after opening. */
    private const val MENU_READ_TICKS = 10
    private const val MENU_TITLE = "Crop Milestones"

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

    /**
     * How far a time in the tab list may be from the local countdown before the countdown follows
     * it. The tab only shows whole seconds (or minutes) and is not refreshed on the second, so
     * following every read made the countdown jump back and forth.
     */
    private const val RESYNC_MS = 2_000L
    /** A cooldown read this soon after pests spawned is the full cooldown, give or take the delay. */
    private const val LEARN_WINDOW_MS = 20_000L
    /** The tab can still say "None" for a moment after the repellent was used. */
    private const val REPELLENT_GRACE_MS = 10_000L

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

    // Pest cooldown: counted down locally from the Pests widget, or from the spawn message.
    var hasPestWidget = false
        private set
    /** "MAX PESTS": no cooldown runs while the Garden is full. */
    var maxPests = false
        private set
    private var pestReady = false
    private var pestCooldownEndMs = 0L
    private var pestSpawnedAtMs = 0L
    private var learnedThisSpawn = false

    /** The Jacob's Contest widget, used when the calendar from Elite is not available. */
    var tabContestTime: String? = null
        private set
    var tabContestCrops: List<String> = emptyList()
        private set

    private var repellentChatAtMs = 0L

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> onMessage(message, overlay) }
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> onMessage(message, overlay) }
    }

    private fun onMessage(message: Component, overlay: Boolean) {
        if (!AlpakaConfig.instance.farmingHudEnabled) return
        val text = SkyblockUtils.cleanColor(message.string)
        val now = System.currentTimeMillis()
        if (overlay) onActionBar(text, now) else onChat(text, now)
    }

    private fun onChat(text: String, now: Long) {
        if (FarmingParse.pestSpawned(text)) {
            onPestSpawn(now)
            return
        }
        val repellent = FarmingParse.repellent(text) ?: return
        val cfg = AlpakaConfig.instance
        repellentChatAtMs = now
        cfg.farmingRepellentUntilMs = now + repellent.minutes * 60_000L
        cfg.farmingRepellentMax = repellent.factor >= 4
        AlpakaConfig.save()
    }

    private fun onPestSpawn(now: Long) {
        pestSpawnedAtMs = now
        learnedThisSpawn = false
        pestReady = false
        // Until the widget says otherwise, the cooldown is the one seen after the last spawn.
        val learned = AlpakaConfig.instance.farmingPestCooldownMs
        if (learned > 0) pestCooldownEndMs = now + learned
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

    /**
     * The shown milestone in crops, current and needed, once the Crop Milestones menu has told what
     * this tier needs. Null before that, and after a tier up until the menu is opened again.
     */
    fun milestoneCrops(): Pair<Long, Long>? {
        val shown = milestone ?: return null
        val percent = shown.percent ?: return null
        val known = AlpakaConfig.instance.farmingMilestones[shown.crop] ?: return null
        if (known.tier != shown.tier || known.needed <= 0) return null
        // The menu's own figure while the percentage has not moved; from then on the percentage of
        // what the tier needs, which is as exact as the 0.1 % the tab shows.
        if (abs(percent - known.percent) < 0.05) return known.current to known.needed
        val estimate = (percent / 100.0 * known.needed).roundToLong()
        return maxOf(estimate, known.current) to known.needed
    }

    /** The pest cooldown left in milliseconds; 0 when ready, null when unknown. */
    fun pestCooldownLeftMs(now: Long = System.currentTimeMillis()): Long? {
        if (pestCooldownEndMs > now) return pestCooldownEndMs - now
        return if (pestReady || pestCooldownEndMs > 0) 0L else null
    }

    /** Time left on the Pest Repellent, in milliseconds; null when none is active. */
    fun repellentLeftMs(now: Long = System.currentTimeMillis()): Long? {
        val left = AlpakaConfig.instance.farmingRepellentUntilMs - now
        return if (left > 0) left else null
    }

    private fun tick(mc: Minecraft) {
        ticks++
        if (!AlpakaConfig.instance.farmingHudEnabled) return
        if (mc.player == null || !SkyblockUtils.isOnGarden()) return
        val now = System.currentTimeMillis()
        val screen = mc.gui.screen()
        if (screen is AbstractContainerScreen<*> && ticks % MENU_READ_TICKS == 0) readMenu(screen)
        if (ticks % READ_INTERVAL_TICKS != 0) return
        JacobContests.tick()
        readTab(mc, now)
    }

    /** Keeps every crop's progress from the Crop Milestones menu. */
    private fun readMenu(screen: AbstractContainerScreen<*>) {
        if (SkyblockUtils.cleanColor(screen.title.string) != MENU_TITLE) return
        val cfg = AlpakaConfig.instance
        var changed = false
        for (slot in screen.menu.slots) {
            val stack = slot.item
            if (stack.isEmpty) continue
            val lore = stack.get(DataComponents.LORE)?.lines()?.map { SkyblockUtils.cleanColor(it.string) } ?: continue
            val read = FarmingParse.menuMilestone(SkyblockUtils.cleanColor(stack.hoverName.string), lore) ?: continue
            val known = cfg.farmingMilestones[read.crop]
            if (known != null && known.tier == read.tier && known.current == read.current && known.needed == read.needed) continue
            cfg.farmingMilestones[read.crop] = AlpakaConfig.CropMilestone().apply {
                tier = read.tier
                current = read.current
                needed = read.needed
                percent = read.percent
            }
            changed = true
        }
        if (changed) AlpakaConfig.save()
    }

    private fun readTab(mc: Minecraft, now: Long) {
        // In the order vanilla draws them, and with the leading spaces kept: a widget's own lines
        // are told from the next widget's title only by being indented.
        val accessor = mc.gui.hud.tabList as PlayerTabOverlayAccessor
        val lines = accessor.`alpaka$getPlayerInfos`().map { info ->
            TabListParser.plain(mc.gui.hud.tabList.getNameForDisplay(info))
        }

        val pests = FarmingParse.widget(lines, "Pests:")
        hasPestWidget = pests != null
        if (pests != null) {
            FarmingParse.field(pests, "Cooldown")?.let { onPestCooldown(it, now) }
            FarmingParse.field(pests, "Repellent")?.let { onRepellentField(it, now) }
        }

        val milestones = FarmingParse.widget(lines, "Crop Milestones:")
        hasMilestoneWidget = milestones != null
        milestones?.let { FarmingParse.milestone(it) }?.let { onMilestone(it, now) }

        val contest = FarmingParse.widget(lines, "Jacob's Contest:")
        tabContestTime = contest?.let {
            FarmingParse.field(it, "Starts In") ?: FarmingParse.widgetTitleValue(lines, "Jacob's Contest:")
        }
        tabContestCrops = contest?.let { FarmingParse.contestCrops(it) } ?: emptyList()
    }

    private fun onPestCooldown(text: String, now: Long) {
        maxPests = text.startsWith("MAX")
        if (maxPests) {
            pestCooldownEndMs = 0L
            pestReady = false
            return
        }
        if (text.startsWith("READY")) {
            pestCooldownEndMs = 0L
            pestReady = true
            return
        }
        val left = FarmingParse.durationMs(text) ?: return
        pestReady = false
        // "4m" without seconds is anywhere up to a minute more.
        val slack = if (text.contains('s')) RESYNC_MS else 60_000L
        val end = now + left
        if (pestCooldownEndMs <= now || end > pestCooldownEndMs + slack || end < pestCooldownEndMs - slack) {
            pestCooldownEndMs = end
        }
        if (!learnedThisSpawn && pestSpawnedAtMs > 0 && now - pestSpawnedAtMs < LEARN_WINDOW_MS && text.contains('s')) {
            learnedThisSpawn = true
            AlpakaConfig.instance.farmingPestCooldownMs = left + (now - pestSpawnedAtMs)
            AlpakaConfig.save()
        }
    }

    /** The Pests widget's "Repellent:" line: "None", or the kind and time left. */
    private fun onRepellentField(text: String, now: Long) {
        val cfg = AlpakaConfig.instance
        val left = FarmingParse.durationMs(text)
        if (left == null) {
            if (text.startsWith("None") && cfg.farmingRepellentUntilMs > now && now - repellentChatAtMs > REPELLENT_GRACE_MS) {
                cfg.farmingRepellentUntilMs = 0L
                AlpakaConfig.save()
            }
            return
        }
        val end = now + left
        val slack = if (text.contains('s')) RESYNC_MS else 60_000L
        if (abs(end - cfg.farmingRepellentUntilMs) > slack) {
            cfg.farmingRepellentUntilMs = end
            cfg.farmingRepellentMax = text.contains("MAX")
            AlpakaConfig.save()
        }
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
