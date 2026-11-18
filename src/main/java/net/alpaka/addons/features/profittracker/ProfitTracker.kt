package net.alpaka.addons.features.profittracker

import com.mojang.blaze3d.platform.InputConstants
import net.alpaka.addons.client.AlpakaKeyBinds
import net.alpaka.addons.client.AlpakaKeyCategory
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaStats
import net.alpaka.addons.features.itemsearch.ItemSearchMenus
import net.alpaka.addons.features.market.ItemIcons
import net.alpaka.addons.features.slayer.SkyblockProfileTracker
import net.alpaka.addons.features.slayer.SlayerQuestDetector
import net.alpaka.addons.features.slayer.SlayerSessionTracker
import net.alpaka.addons.features.slayer.SlayerType
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.alpaka.addons.utils.TextInputFocus
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.player.Player
import org.lwjgl.glfw.GLFW
import java.util.Locale
import java.util.Optional

/**
 * One Profit Tracker for all of SkyBlock that keeps every activity apart: each Slayer boss, Fishing,
 * Diana, Pests, Farming, Dragons, Dungeons, and every island for whatever else is done there.
 *
 * What other mods spread over a tracker per activity (SkyHanni has about twenty, each with its own
 * sources) runs through one pipeline here, and only the bucket a gain goes into depends on what the
 * player is doing at that moment ([detectOther], or the slayer while in its area). A slayer only gets
 * what its boss drops ([ProfitSlayerDrops]); other loot during a quest goes to the island:
 *
 *  - Items: the player's inventory, compared with a moment ago. Only increases count, and only
 *    while no menu is open and for a moment after one closed, so buying, taking things out of a
 *    chest, crafting or trading is not income. Items that were just dropped and picked up again,
 *    or taken out of the sacks, don't count either.
 *  - Sacks: Hypixel's "[Sacks] +N items" message, whose hover text lists every item that went in.
 *    Not while a sack's menu is open (or just was), and not what was supercrafted.
 *  - Coins: the purse on the sidebar going up while no menu is open, the way SkyHanni reads it.
 *  - Costs: the purse going down right when a slayer quest started, auto-slayer's bank fee, and
 *    the summoning eyes placed for a dragon.
 *
 * Only reads what the client is shown; nothing is sent to the server.
 */
object ProfitTracker {

    /** After a world change the inventory and sidebar fill in anew; nothing in that time is news. */
    private const val WORLD_CHANGE_GRACE_MS = 3_000L
    /** After a menu closes: the last clicks of it can still land in the inventory. */
    private const val MENU_CLOSE_GRACE_MS = 1_000L
    /** Coins after a menu closed can still be the menu's (a sale, a withdrawal). */
    private const val PURSE_MENU_GRACE_MS = 2_000L
    /** A sack message this soon after a sack menu closed can still be what was put in there. */
    private const val SACK_MENU_GRACE_MS = 10_000L
    private const val SUPERCRAFT_GRACE_MS = 30_000L
    private const val MOVED_FROM_SACKS_GRACE_MS = 3_000L
    /** A dropped item picked back up within this time is not loot. */
    private const val LOSS_MEMORY_MS = 30_000L
    /** Purse gains above this are a transfer of some sort, not loot. */
    private const val MAX_COIN_GAIN = 5_000_000.0
    /** How long a slayer quest's price may be away from "SLAYER QUEST STARTED!". */
    private const val QUEST_COST_WINDOW_MS = 1_500L
    private const val MAX_QUEST_COST = 350_000.0
    /** A bobber in water this recently still means fishing: the catch lands after reeling in. */
    private const val FISHING_MEMORY_MS = 90_000L
    private const val DIANA_MEMORY_MS = 180_000L
    private const val PEST_MEMORY_MS = 20_000L
    /** Without a gain for this long the activity's clock stops: a break, not play. */
    private const val AFK_MS = 120_000L

    private const val INVENTORY_EVERY_TICKS = 2
    private const val PURSE_EVERY_TICKS = 5
    private const val DETECT_EVERY_TICKS = 10

    @JvmField
    var TOGGLE_KEY: KeyMapping? = null

    /** Counts up with every change, so the HUD knows to lay itself out again. */
    @JvmStatic
    var version = 0
        private set

    /** What the HUD shows: an activity and its figures in the scope the settings pick. */
    class Shown(
        val activity: ProfitActivity,
        val bucket: ProfitBucket,
        val costLabel: String,
        val actionLabel: String?,
    ) {
        val costs: Long get() = bucket.costs
        val activeMs: Long get() = bucket.activeMs
    }

    private var ticks = 0
    private var current: ProfitActivity = ProfitActivity.UNKNOWN
    /** What the player does apart from a slayer quest: where loot that is no slayer drop goes. */
    private var other: ProfitActivity = ProfitActivity.UNKNOWN
    /** The last activity anything was counted for: the HUD stays on it while the player walks elsewhere. */
    private var lastActive: String? = null

    private var lastLevel: Any? = null
    private var worldChangedAt = 0L
    private var menuOpen = false
    private var sackMenuOpen = false
    private var menuClosedAt = 0L
    private var sackMenuClosedAt = 0L

    /** The inventory a moment ago, by price key; null until it was first read in this world. */
    private var snapshot: HashMap<String, Long>? = null
    private val names = HashMap<String, String>()
    /** Recently lost items by key: amount and when, so picking them back up does not count. */
    private val losses = HashMap<String, Pair<Long, Long>>()
    /** Items (by key and by lower-case name) that must not count until the time given. */
    private val ignoredUntil = HashMap<String, Long>()

    private var lastPurse: Double? = null
    private var slayerStartedAt = 0L
    /** A purse drop not yet matched to a quest start: amount and when. */
    private var pendingPurseDrop: Pair<Double, Long>? = null

    private var lastFishingMs = 0L
    private var lastBurrowMs = 0L
    private var lastPestKillMs = 0L
    private var lastTickMs = 0L
    private val lastEventMs = HashMap<String, Long>()

    fun register() {
        TOGGLE_KEY = AlpakaKeyBinds.register(
            KeyMapping("key.alpaka.profit_tracker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY)
        )
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        // Lines another mod's chat filter hides still count; they just never reach the chat.
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> if (!overlay) onChat(message) }
        ClientLifecycleEvents.CLIENT_STOPPING.register { ProfitStore.saveNow(blocking = true) }
        // In a menu the key and clicks reach the screen, not the tick: needed when the tracker only shows there.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            ScreenKeyboardEvents.allowKeyPress(screen).register { s, event ->
                val key = TOGGLE_KEY
                if (key == null || !key.matches(event) || TextInputFocus.isTyping(s) || !AlpakaConfig.instance.profitTrackerEnabled) {
                    true
                } else {
                    toggleHud(Minecraft.getInstance())
                    false
                }
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                !ProfitTrackerHudElement.handleMenuClick(event.x(), event.y(), event.button())
            }
        }
    }

    // ---- Settings and HUD ------------------------------------------------------------------------

    @JvmStatic
    fun isHudVisible(): Boolean {
        val cfg = AlpakaConfig.instance
        return cfg.profitTrackerEnabled && cfg.profitTrackerHudShown && SkyblockUtils.isOnSkyblock()
    }

    /** The figures the HUD shows, or null when there is no profile to show them for. */
    fun shown(): Shown? {
        val profile = profile()
        val sessionOnly = AlpakaConfig.instance.profitTrackerSessionOnly
        fun bucketOf(id: String): ProfitBucket? =
            if (sessionOnly) ProfitStore.session(profile, id) else ProfitStore.total(profile, id)

        val id = current.id.takeIf { bucketOf(it)?.isEmpty() == false } ?: lastActive ?: current.id
        val activity = ProfitActivity.of(id)
        val bucket = bucketOf(id) ?: ProfitBucket()
        return Shown(activity, bucket, costLabel(activity), actionLabel(activity))
    }

    private fun costLabel(activity: ProfitActivity): String = when {
        activity.id.startsWith("slayer:") -> "Slayer Quests"
        activity == ProfitActivity.DRAGONS -> "Summoning Eyes"
        else -> "Costs"
    }

    private fun actionLabel(activity: ProfitActivity): String? = when {
        activity.id.startsWith("slayer:") -> "Bosses"
        activity == ProfitActivity.DIANA -> "Burrows"
        activity == ProfitActivity.PESTS -> "Pests"
        activity == ProfitActivity.DRAGONS -> "Dragons"
        else -> null
    }

    /** The SkyBlock profile the figures belong to: the one Hypixel last named, else the last one known. */
    @JvmStatic
    fun profile(): String {
        val mc = Minecraft.getInstance()
        val account = mc.user?.profileId?.toString() ?: return "default"
        return SkyblockProfileTracker.currentFor(account)
            ?: AlpakaStats.instance.accounts[account]?.lastProfile
            ?: "default"
    }

    /** The activity being tracked right now, for the command. */
    fun currentActivity(): ProfitActivity = current

    /** Hides or shows an item of the activity on the HUD; hidden items don't count toward the total. */
    fun toggleHidden(key: String) {
        val shown = shown() ?: return
        ProfitStore.toggleHidden(profile(), shown.activity.id, key)
        changed()
    }

    /** Clears the shown activity, or all of the profile's; the session, or all time as well. */
    fun reset(everything: Boolean, allTime: Boolean) {
        val activity = if (everything) null else shown()?.activity?.id
        ProfitStore.reset(profile(), activity, allTime)
        if (everything) lastActive = null
        changed()
    }

    private fun changed() {
        version++
        ProfitTrackerHudRenderer.invalidate()
    }

    // ---- The tick --------------------------------------------------------------------------------

    private fun tick(mc: Minecraft) {
        val now = System.currentTimeMillis()
        val key = TOGGLE_KEY
        if (key != null) {
            var pressed = false
            while (key.consumeClick()) pressed = true
            if (pressed && mc.gui.screen() == null && AlpakaConfig.instance.profitTrackerEnabled) toggleHud(mc)
        }

        val cfg = AlpakaConfig.instance
        val player = mc.player
        if (!cfg.profitTrackerEnabled || player == null || mc.level == null || !SkyblockUtils.isOnSkyblock()) {
            snapshot = null
            lastPurse = null
            lastTickMs = now
            return
        }
        ticks++

        if (mc.level !== lastLevel) {
            lastLevel = mc.level
            worldChangedAt = now
            snapshot = null
            lastPurse = null
        }

        trackMenus(mc, now)
        player.fishing?.let { if (it.isInWater || it.isInLava) lastFishingMs = now }

        if (ticks % DETECT_EVERY_TICKS == 0) {
            other = detectOther(mc, now)
            current = slayerHere()?.let { ProfitActivity.slayer(it) } ?: other
        }
        if (ticks % INVENTORY_EVERY_TICKS == 0) readInventory(player, now)
        if (ticks % PURSE_EVERY_TICKS == 0) readPurse(now)

        countTime(now)
        ProfitStore.tick(now)
    }

    private fun toggleHud(mc: Minecraft) {
        val cfg = AlpakaConfig.instance
        cfg.profitTrackerHudShown = !cfg.profitTrackerHudShown
        AlpakaConfig.save()
        mc.player?.sendOverlayMessage(Component.literal(
            if (cfg.profitTrackerHudShown) "§6Profit Tracker §ashown" else "§6Profit Tracker §chidden"))
    }

    private fun trackMenus(mc: Minecraft, now: Long) {
        val screen = mc.gui.screen()
        val open = screen is AbstractContainerScreen<*>
        val sack = open && SkyblockUtils.cleanColor(screen!!.title.string).contains("Sack")
        if (menuOpen && !open) menuClosedAt = now
        if (sackMenuOpen && !sack) sackMenuClosedAt = now
        menuOpen = open
        sackMenuOpen = sack
    }

    /** Whether an inventory change right now can be loot. */
    private fun inventoryCounts(now: Long): Boolean =
        !menuOpen && now - menuClosedAt >= MENU_CLOSE_GRACE_MS && now - worldChangedAt >= WORLD_CHANGE_GRACE_MS

    /** The slayer whose quest runs (or just ended), if any. */
    private fun questSlayer(): SlayerType? = SlayerQuestDetector.currentOrRecent()?.takeIf { it != SlayerType.GUARDIAN }

    /**
     * The slayer, while its quest runs and the player is where that boss is fought. A quest left
     * running while mining or fishing elsewhere doesn't make that the slayer's.
     */
    private fun slayerHere(): SlayerType? = questSlayer()?.takeIf { SlayerSessionTracker.isInTrackerArea(it) }

    /**
     * What the player does apart from slayer, most specific first: fishing wins wherever the bobber
     * is; the island is what is left. The slayer goes over this in [current] while in its area.
     */
    private fun detectOther(mc: Minecraft, now: Long): ProfitActivity {
        if (DungeonState.inDungeon()) return ProfitActivity.DUNGEONS
        if (now - lastFishingMs < FISHING_MEMORY_MS) return ProfitActivity.FISHING
        val area = area()
        if (area == "Hub" && (now - lastBurrowMs < DIANA_MEMORY_MS || holdsSpade(mc))) return ProfitActivity.DIANA
        if (SkyblockUtils.isOnGarden()) {
            return if (now - lastPestKillMs < PEST_MEMORY_MS) ProfitActivity.PESTS else ProfitActivity.FARMING
        }
        if (SkyblockUtils.isOnEndIsland() && SkyblockUtils.getSidebarLines().any { it.contains("Dragon's Nest") }) {
            return ProfitActivity.DRAGONS
        }
        return if (area != null) ProfitActivity.island(area) else ProfitActivity.UNKNOWN
    }

    /** The island, from the tab list's "Area: Hub" (or "Dungeon: Catacombs") line. */
    private fun area(): String? {
        for (line in SkyblockUtils.getTabListLines()) {
            val t = line.trim()
            if (t.startsWith("Area: ")) return t.substring(6).trim().ifEmpty { null }
            if (t.startsWith("Dungeon: ")) return t.substring(9).trim().ifEmpty { null }
        }
        return null
    }

    private fun holdsSpade(mc: Minecraft): Boolean {
        val stack = mc.player?.mainHandItem ?: return false
        return ProfitItems.key(stack) == "ANCESTRAL_SPADE"
    }

    // ---- Items in the inventory ------------------------------------------------------------------

    private fun readInventory(player: Player, now: Long) {
        val inventory = player.inventory
        val counts = HashMap<String, Long>()
        // Every slot, armor and off hand included: taking armor off is not loot.
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            val key = ProfitItems.key(stack) ?: continue
            counts.merge(key, stack.count.toLong(), Long::plus)
            if (key !in names) names[key] = ProfitItems.name(stack)
        }
        // The item held on the cursor in the player's own inventory screen is still theirs.
        val carried = player.containerMenu?.carried
        if (carried != null) ProfitItems.key(carried)?.let { counts.merge(it, carried.count.toLong(), Long::plus) }

        val before = snapshot
        snapshot = counts
        if (before == null) return
        val counting = inventoryCounts(now)
        val gains = ArrayList<Pair<String, Long>>()
        val lost = ArrayList<Pair<String, Long>>()
        for ((key, amount) in counts) {
            val gained = amount - (before[key] ?: 0L)
            if (gained > 0) gains += key to gained
        }
        for ((key, amount) in before) {
            val gone = amount - (counts[key] ?: 0L)
            if (gone > 0) lost += key to gone
        }
        // What moves in and out while a menu is open is buying, selling, storing: neither gain nor loss.
        if (!counting) return
        val recorded = gains.count { (key, amount) -> gainItem(key, names[key] ?: key, amount, now) }
        if (recorded > 0 && lost.isNotEmpty()) {
            // Items turning into others while nothing else happens: the Compactor making Enchanted
            // Rotten Flesh out of 160 Rotten Flesh. What went into it was counted when it dropped,
            // so it comes off again, or the flesh would count twice.
            for ((key, amount) in lost) uncount(key, amount, now)
        } else {
            for ((key, amount) in lost) {
                val had = losses[key]?.takeIf { now - it.second < LOSS_MEMORY_MS }?.first ?: 0L
                losses[key] = (had + amount) to now
            }
        }
    }

    /** Counts a gain of the inventory; whether anything was counted. */
    private fun gainItem(key: String, name: String, amount: Long, now: Long): Boolean {
        if (ignored(key, name, now)) return false
        var left = amount
        // Picking up what was just dropped (or put back what was just taken out) is no gain.
        losses[key]?.let { (lost, at) ->
            if (now - at < LOSS_MEMORY_MS) {
                val offset = minOf(lost, left)
                left -= offset
                if (lost - offset > 0) losses[key] = (lost - offset) to at else losses.remove(key)
            } else {
                losses.remove(key)
            }
        }
        if (left <= 0) return false
        val target = itemTarget(key)
        record(target) { bucket, n -> bucket.addItem(key, name, left, n) }
        log(target, key, name, left, now)
        return true
    }

    /**
     * Where an item goes: to the slayer only if its boss drops it, wherever the player picked it up
     * while the quest ran (the [Sacks] line can come half a minute late); anything else, from a
     * corpse, a gift or a trade, to what the player does besides.
     */
    private fun itemTarget(key: String): ProfitActivity {
        val type = questSlayer()
        if (type != null && ProfitSlayerDrops.isDrop(type, key)) return ProfitActivity.slayer(type)
        return if (current.id.startsWith("slayer:")) other else current
    }

    /** Takes items back off the activity's figures, at most what this session counted of them. */
    private fun uncount(key: String, amount: Long, now: Long) {
        val target = itemTarget(key)
        val inSession = ProfitStore.session(profile(), target.id)?.items?.get(key)?.amount ?: return
        val back = minOf(inSession, amount)
        if (back <= 0) return
        val name = names[key] ?: key
        record(target) { bucket, n -> bucket.addItem(key, name, -back, n) }
        log(target, key, name, -back, now)
    }

    private fun ignored(key: String, name: String, now: Long): Boolean {
        val byKey = ignoredUntil[key]
        val byName = ignoredUntil[name.lowercase(Locale.ROOT)]
        return (byKey != null && now < byKey) || (byName != null && now < byName)
    }

    // ---- Coins -----------------------------------------------------------------------------------

    private fun readPurse(now: Long) {
        val purse = SkyblockUtils.getSidebarLines().firstNotNullOfOrNull { ProfitParse.purse(it) } ?: return
        val before = lastPurse
        lastPurse = purse
        if (before == null || now - worldChangedAt < WORLD_CHANGE_GRACE_MS) return
        val diff = purse - before
        if (diff > 0) {
            if (!menuOpen && now - menuClosedAt >= PURSE_MENU_GRACE_MS && diff <= MAX_COIN_GAIN) {
                val coins = Math.round(diff)
                record(current) { bucket, n -> bucket.addCoins(coins, n) }
                log(current, ProfitItems.COINS, "Coins", coins, now)
            }
        } else if (diff < 0) {
            if (now - slayerStartedAt < QUEST_COST_WINDOW_MS) questCost(-diff, now) else pendingPurseDrop = -diff to now
        }
    }

    /** A slayer quest's price, charged to the slayer the quest is for. */
    private fun questCost(amount: Double, now: Long) {
        pendingPurseDrop = null
        if (amount > MAX_QUEST_COST) return
        val type = SlayerQuestDetector.currentOrRecent() ?: return
        cost(ProfitActivity.slayer(type), Math.round(amount), now)
    }

    private fun cost(activity: ProfitActivity, amount: Long, now: Long) {
        record(activity) { bucket, n -> bucket.addCost(amount, n) }
        log(activity, "COSTS", "Costs", -amount, now)
    }

    // ---- Chat ------------------------------------------------------------------------------------

    private fun onChat(message: Component) {
        if (!AlpakaConfig.instance.profitTrackerEnabled) return
        val text = SkyblockUtils.cleanColor(message.string)
        val now = System.currentTimeMillis()
        when {
            text.startsWith("[Sacks]") -> onSacks(message, now)
            ProfitParse.slayerStarted(text) -> {
                slayerStartedAt = now
                pendingPurseDrop?.let { (amount, at) -> if (now - at < QUEST_COST_WINDOW_MS) questCost(amount, now) }
            }
            ProfitParse.slayerComplete(text) -> {
                val type = SlayerQuestDetector.currentOrRecent()
                if (type != null && type != SlayerType.GUARDIAN) action(ProfitActivity.slayer(type), now)
            }
            ProfitParse.burrow(text) -> {
                lastBurrowMs = now
                action(ProfitActivity.DIANA, now)
            }
            ProfitParse.eyePlaced(text) -> eyeCost(1, now)
            ProfitParse.eyeRecovered(text) -> {
                eyeCost(-1, now)
                // The eye comes back into the inventory: its price is refunded, so it is no loot.
                ignoredUntil["SUMMONING_EYE"] = now + MOVED_FROM_SACKS_GRACE_MS
            }
            else -> {
                ProfitParse.autoSlayerCost(text)?.let { coins ->
                    SlayerQuestDetector.currentOrRecent()?.let { cost(ProfitActivity.slayer(it), coins, now) }
                    return
                }
                ProfitParse.movedFromSacks(text)?.let { name ->
                    ignoredUntil[name.lowercase(Locale.ROOT)] = now + MOVED_FROM_SACKS_GRACE_MS
                    ItemIcons.idForName(name)?.let { ignoredUntil[it] = now + MOVED_FROM_SACKS_GRACE_MS }
                    return
                }
                ProfitParse.supercrafted(text)?.let { name ->
                    ignoredUntil["sack:" + name.lowercase(Locale.ROOT)] = now + SUPERCRAFT_GRACE_MS
                    return
                }
                ProfitParse.pestKill(text)?.let {
                    // A pest drops one line per item; the kill is counted once.
                    if (now - lastPestKillMs > 1_000L) action(ProfitActivity.PESTS, now)
                    lastPestKillMs = now
                    current = ProfitActivity.PESTS
                    return
                }
                ProfitParse.dragonDamage(text)?.let { damage ->
                    if (damage > 0 && current == ProfitActivity.DRAGONS) action(ProfitActivity.DRAGONS, now)
                }
            }
        }
    }

    private fun eyeCost(eyes: Int, now: Long) {
        val each = ProfitItems.price("SUMMONING_EYE")
        if (each.isNaN()) return
        cost(ProfitActivity.DRAGONS, Math.round(each * eyes), now)
    }

    /**
     * "[Sacks] +12 items, -3 items." Its hover text lists what went in, one line each:
     * "+12 Revenant Flesh (Combat Sack)". Only what went in counts.
     */
    private fun onSacks(message: Component, now: Long) {
        if (sackMenuOpen || now - sackMenuClosedAt < SACK_MENU_GRACE_MS) return
        val hovers = ArrayList<Component>()
        message.visit({ style, _ ->
            (style.hoverEvent as? HoverEvent.ShowText)?.let { hovers += it.value() }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        for (hover in hovers) {
            for (line in SkyblockUtils.cleanColor(hover.string).split("\n")) {
                val change = ItemSearchMenus.parseSackChange(line) ?: continue
                if (change.delta() <= 0) continue
                val name = change.item()
                if (ignored("sack:" + name.lowercase(Locale.ROOT), name, now)) continue
                val key = ItemIcons.idForName(name) ?: name.uppercase(Locale.ROOT).replace(' ', '_')
                if (ignored(key, name, now)) continue
                val target = itemTarget(key)
                record(target) { bucket, n -> bucket.addItem(key, name, change.delta(), n) }
                log(target, key, name, change.delta(), now)
            }
        }
    }

    // ---- Recording -------------------------------------------------------------------------------

    private fun action(activity: ProfitActivity, now: Long) {
        record(activity) { bucket, n -> bucket.addAction(n) }
    }

    /** Applies a change to the activity's session and all-time figures alike. */
    private inline fun record(activity: ProfitActivity, change: (ProfitBucket, Long) -> Unit) {
        val now = System.currentTimeMillis()
        val (total, session) = ProfitStore.buckets(profile(), activity.id)
        change(total, now)
        change(session, now)
        lastEventMs[activity.id] = now
        lastActive = activity.id
        ProfitStore.markDirty()
        changed()
    }

    private fun log(activity: ProfitActivity, key: String, name: String, amount: Long, now: Long) {
        val each = if (key == "COSTS") 1.0 else ProfitItems.price(key)
        ProfitStore.log(ProfitStore.logRow(now, profile(), activity, key, name, amount, each))
    }

    /** Adds the time since the last tick to the activity being played, unless it is idle. */
    private fun countTime(now: Long) {
        val delta = (now - lastTickMs).coerceIn(0L, 1_000L)
        lastTickMs = now
        val id = current.id
        val last = lastEventMs[id] ?: return
        if (now - last > AFK_MS) return
        val profile = profile()
        ProfitStore.total(profile, id)?.let { it.activeMs += delta }
        ProfitStore.session(profile, id)?.let { it.activeMs += delta }
    }
}
