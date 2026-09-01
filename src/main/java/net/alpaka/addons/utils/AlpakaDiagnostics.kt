package net.alpaka.addons.utils

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaStats
import net.alpaka.addons.features.slayer.SlayerDropTracker
import net.alpaka.addons.features.slayer.SlayerBossEntityTracker
import net.alpaka.addons.features.slayer.SlayerQuestDetector
import net.alpaka.addons.features.slayer.SlayerTimer
import net.alpaka.addons.features.slayer.SlayerType
import net.minecraft.client.Minecraft

/**
 * Prints a snapshot of what the mod currently believes about its environment, for `/alpakadebug`.
 *
 * Most reports of "feature X isn't doing anything" come down to detection rather than the feature
 * itself - the sidebar title changing during an event has now broken two features that way - so this
 * dumps the inputs those checks read: where the mod thinks you are, what the sidebar actually says,
 * and what slayer state it has parsed.
 */
object AlpakaDiagnostics {

    @JvmStatic
    fun print() {
        val cfg = AlpakaConfig.instance
        val mc = Minecraft.getInstance()

        line("§6--- Alpaka Addons diagnostics ---")
        line("§7Version: §f${ModVersion.mod()} §7on MC §f${ModVersion.minecraft()}")

        val server = mc.currentServer?.ip ?: "singleplayer / none"
        line("§7Server: §f$server")
        line("§7Detected as Skyblock: ${yesNo(SkyblockUtils.isOnSkyblock())}")

        val lines = SkyblockUtils.getSidebarLines()
        val area = lines.firstOrNull { it.contains("⏣") } ?: "(none found)"
        line("§7Area line: §f$area")

        // Slayer state, since that is scoreboard-derived and the usual suspect when tracking is quiet.
        SlayerQuestDetector.refresh()
        val activeSlayer = SlayerQuestDetector.activeType
        if (activeSlayer != null) {
            line("§7Slayer quest: §f${activeSlayer.display} §7tier §f${SlayerQuestDetector.tier}§7, progress: §f\"${SlayerQuestDetector.progress}\"")
        } else {
            line("§7Slayer quest: §8none on the sidebar §7(last seen: §f${SlayerQuestDetector.currentOrRecent()?.display ?: "none"}§7)")
        }
        line("§7Slayer tracking enabled: ${yesNo(cfg.slayerDropTrackerEnabled)}")

        printBossTimer(cfg)

        val totalKills = SlayerType.entries.sumOf { AlpakaStats.slayerBossMap()[it]?.kills ?: 0 }
        val trackedDrops = SlayerType.entries.sumOf { AlpakaStats.slayerBossMap()[it]?.drops?.size ?: 0 }
        line("§7Recorded: §f$totalKills §7kills, §f$trackedDrops §7distinct drops §8(/alpakaslayer for detail)")

        line("§7Pangolin highlight: ${yesNo(cfg.pangolinHighlightEnabled)}")
        line("§7HUDs - player model: ${yesNo(cfg.playerModelEnabled)}§7, world age: ${yesNo(cfg.worldAgeHudEnabled)}")

        line("§7Sidebar lines (§f${lines.size}§7):")
        if (lines.isEmpty()) {
            line("§8  (sidebar not readable)")
        } else {
            lines.forEach { line("§8  \"$it\"") }
        }
    }

    /**
     * How the boss timer measured the last fight, broken into its four moments.
     *
     * Here because the one question this feature attracts is "why does my time not match
     * SkyHanni's", and that is only answerable by seeing which end of the fight the two disagree
     * about. The offsets are printed relative to the sidebar, since the sidebar timing is what the
     * mod did before the entity mode existed and so is the baseline to compare against.
     */
    private fun printBossTimer(cfg: AlpakaConfig) {
        line("§7Boss timer: ${yesNo(cfg.slayerTimerEnabled)}")
        line(
            "§7Boss entity right now - name tag: ${yesNo(SlayerBossEntityTracker.isTracking)}§7, " +
                "mob behind it: ${yesNo(SlayerBossEntityTracker.hasMob)}",
        )

        val fight = SlayerTimer.lastFight
        if (fight == null) {
            line("§8  no fight measured yet this session")
            return
        }
        line("§7Last fight:")
        fight.lines().forEach(::line)
    }

    private fun yesNo(value: Boolean) = if (value) "§ayes" else "§cno"

    private fun line(text: String) = SlayerDropTracker.sendModMessage(text)
}
