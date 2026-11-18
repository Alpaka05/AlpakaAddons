package net.alpaka.addons.features.profittracker

import com.mojang.brigadier.CommandDispatcher
import net.alpaka.addons.features.market.Coins
import net.alpaka.addons.utils.AlpakaPrefix
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.util.Util

/**
 * `/alpakaprofit`: every activity of the profile with its profit, and the tracker's housekeeping.
 *
 *  - `/alpakaprofit` lists the session's activities (`all` for all time),
 *  - `reset` clears the shown activity's session, `reset all` every activity's session,
 *  - `wipe` clears the shown activity for good (the CSV log keeps it),
 *  - `unhide` brings back hidden items, `folder` opens the CSV files.
 */
object ProfitCommands {

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(ClientCommands.literal("alpakaprofit")
            .executes { later { list(sessionOnly = true) } }
            .then(ClientCommands.literal("all").executes { later { list(sessionOnly = false) } })
            .then(ClientCommands.literal("reset")
                .executes { later { ProfitTracker.reset(everything = false, allTime = false); say("Session of the shown activity cleared.") } }
                .then(ClientCommands.literal("all").executes {
                    later { ProfitTracker.reset(everything = true, allTime = false); say("Session cleared.") }
                }))
            .then(ClientCommands.literal("wipe").executes {
                later { ProfitTracker.reset(everything = false, allTime = true); say("Shown activity cleared for good. The CSV log still has it.") }
            })
            .then(ClientCommands.literal("unhide").executes { later { unhide() } })
            .then(ClientCommands.literal("folder").executes { later { openFolder() } }))
    }

    private fun later(action: () -> Unit): Int {
        Minecraft.getInstance().execute(action)
        return 1
    }

    private fun say(text: String) {
        Minecraft.getInstance().player?.sendSystemMessage(AlpakaPrefix.prefix().append(Component.literal("§7$text")))
    }

    private fun list(sessionOnly: Boolean) {
        val profile = ProfitTracker.profile()
        val ids = ProfitStore.activities(profile, sessionOnly)
        if (ids.isEmpty()) {
            say(if (sessionOnly) "Nothing counted this session yet." else "Nothing counted on this profile yet.")
            return
        }
        say("Profit on §e$profile§7 (${if (sessionOnly) "session" else "all time"}), now: §e${ProfitTracker.currentActivity().title}")
        for (id in ids) {
            val bucket = (if (sessionOnly) ProfitStore.session(profile, id) else ProfitStore.total(profile, id)) ?: continue
            var total = (bucket.coins - bucket.costs).toDouble()
            for ((key, item) in bucket.items) {
                if (item.hidden) continue
                val each = ProfitItems.price(key)
                if (!each.isNaN()) total += each * item.amount
            }
            val hours = bucket.activeMs / 3_600_000.0
            val perHour = if (hours > 1.0 / 60) " §8(${Coins.compact(total / hours)}/h)" else ""
            val color = if (total >= 0) "§a" else "§c"
            say(" §f${ProfitActivity.of(id).title}§7: $color${Coins.compact(total)}$perHour")
        }
    }

    private fun unhide() {
        val shown = ProfitTracker.shown() ?: return
        val profile = ProfitTracker.profile()
        for (bucket in listOfNotNull(ProfitStore.total(profile, shown.activity.id), ProfitStore.session(profile, shown.activity.id))) {
            bucket.items.values.forEach { it.hidden = false }
        }
        ProfitStore.markDirty()
        ProfitTrackerHudRenderer.invalidate()
        say("Hidden items of ${shown.activity.title} are back.")
    }

    /** Opens the folder with the CSV files in the system's file manager, writing them first. */
    @JvmStatic
    fun openFolder() {
        ProfitStore.load()
        ProfitStore.markDirty()
        ProfitStore.saveNow()
        val dir = ProfitStore.dir
        dir.mkdirs()
        Util.getPlatform().openFile(dir)
    }
}
