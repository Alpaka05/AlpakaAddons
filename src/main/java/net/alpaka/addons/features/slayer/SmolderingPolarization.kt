package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * How long the Smoldering Polarization effect has left - the Blaze slayer buff a Re-heated Gummy
 * Polar Bear gives - for the slayer HUD's "Bear" line.
 *
 * ### Where the time comes from
 *
 * Hypixel prints it in the tab list, `Smoldering Polarization I: 1h 12m`, in the Active Effects
 * widget and in the footer's effect list. Neither lists every effect, so a missing line proves
 * nothing; a line that is there sets the clock. Between readings the clock runs down by itself,
 * and eating a bear adds its hour at once, from the chat line, as SkyHanni's EffectApi does.
 *
 * Effects only run down while the player is on SkyBlock, so the clock stops outside it, and what
 * is left is kept in the config: the line is right again straight after a restart, before the tab
 * list has been read.
 */
object SmolderingPolarization {

    private val TAB_LINE = Regex("Smoldering Polarization I+:?\\s+(?<time>(?:\\d+[dhms]\\s*)+)")
    private const val ATE_BEAR = "You ate a Re-heated Gummy Polar Bear!"
    private const val BEAR_MS = 60 * 60 * 1000L

    /** How often the tab list is read; the time it shows changes once a minute at most. */
    private const val READ_INTERVAL_TICKS = 20

    /** How often the remaining time is written to the config while it runs. */
    private const val SAVE_INTERVAL_MS = 60_000L

    private var ticks = 0
    private var lastTickMs = 0L
    private var lastSaveMs = 0L

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
        // The same line hidden from chat by another mod still counts.
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> if (!overlay) onChat(message) }
    }

    /** Milliseconds left, or 0 when the effect is not active. */
    @JvmStatic
    fun remainingMs(): Long = maxOf(0L, AlpakaConfig.instance.smolderingPolarizationRemainingMs)

    private fun tick(mc: Minecraft) {
        val cfg = AlpakaConfig.instance
        val now = System.currentTimeMillis()
        val elapsed = if (lastTickMs == 0L) 0L else now - lastTickMs
        lastTickMs = now
        if (mc.player == null || !SkyblockUtils.isOnSkyblock()) return

        if (cfg.smolderingPolarizationRemainingMs > 0) {
            cfg.smolderingPolarizationRemainingMs = maxOf(0L, cfg.smolderingPolarizationRemainingMs - elapsed)
            if (cfg.smolderingPolarizationRemainingMs == 0L || now - lastSaveMs >= SAVE_INTERVAL_MS) save(now)
        }

        if (++ticks % READ_INTERVAL_TICKS == 0) readTabList(mc, now)
    }

    private fun readTabList(mc: Minecraft, now: Long) {
        val footer = (mc.gui.hud.tabList as PlayerTabOverlayAccessor).`alpaka$getFooter`()
        val lines = SkyblockUtils.getTabListLines() + (footer?.string?.split('\n') ?: emptyList())
        for (line in lines) {
            val time = TAB_LINE.find(SkyblockUtils.cleanColor(line))?.groups?.get("time")?.value ?: continue
            val (shownMs, unitMs) = parseDuration(time) ?: continue
            // "1h 12m" means somewhere from 1h 12m up to 1h 13m. Only a clock outside that range is
            // corrected, so it keeps counting smoothly instead of snapping to the minute.
            val ours = AlpakaConfig.instance.smolderingPolarizationRemainingMs
            if (ours < shownMs || ours > shownMs + unitMs) {
                AlpakaConfig.instance.smolderingPolarizationRemainingMs = shownMs + unitMs / 2
                save(now)
            }
            return
        }
    }

    private fun onChat(message: Component) {
        if (!SkyblockUtils.cleanColor(message.string).trim().startsWith(ATE_BEAR)) return
        val cfg = AlpakaConfig.instance
        cfg.smolderingPolarizationRemainingMs = maxOf(0L, cfg.smolderingPolarizationRemainingMs) + BEAR_MS
        save(System.currentTimeMillis())
    }

    private fun save(now: Long) {
        lastSaveMs = now
        AlpakaConfig.save()
    }

    /** "1h 12m" as (its milliseconds, the size of its smallest unit), or null if it is not a duration. */
    private fun parseDuration(text: String): Pair<Long, Long>? {
        var total = 0L
        var unit = 0L
        for (part in text.trim().split(Regex("\\s+"))) {
            val value = part.dropLast(1).toLongOrNull() ?: return null
            val size = when (part.last()) {
                'd' -> 86_400_000L
                'h' -> 3_600_000L
                'm' -> 60_000L
                's' -> 1_000L
                else -> return null
            }
            total += value * size
            unit = size
        }
        return if (unit == 0L) null else total to unit
    }
}
