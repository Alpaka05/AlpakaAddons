package net.alpaka.addons.features.slayer

import net.alpaka.addons.mixin.PlayerTabOverlayAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * The time left on Smoldering Polarization - the slayer buff a Re-heated Gummy Polar Bear gives -
 * for the slayer HUD's "Bear" line.
 *
 * Shown exactly as Hypixel writes it in the tab list, `Smoldering Polarization I: 1h 12m`, from the
 * Active Effects widget or the footer's effect list. No clock of our own runs in between: Hypixel
 * does not refresh the tab on the minute, so a local countdown kept being pulled back and forth
 * against it. When neither place lists the effect, it counts as inactive.
 */
object SmolderingPolarization {

    private val TAB_LINE = Regex("Smoldering Polarization I+:?\\s+(?<time>(?:\\d+[dhms]\\s*)+)")

    /** How often the tab list is read. */
    private const val READ_INTERVAL_TICKS = 20

    private var ticks = 0

    /** The time as the tab list last showed it, e.g. "1h 12m"; null while the effect is not listed. */
    @JvmStatic
    var shownTime: String? = null
        private set

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    private fun tick(mc: Minecraft) {
        if (++ticks % READ_INTERVAL_TICKS != 0) return
        if (mc.player == null || !SkyblockUtils.isOnSkyblock()) {
            shownTime = null
            return
        }
        val footer = (mc.gui.hud.tabList as PlayerTabOverlayAccessor).`alpaka$getFooter`()
        val lines = SkyblockUtils.getTabListLines() + (footer?.string?.split('\n') ?: emptyList())
        shownTime = lines.firstNotNullOfOrNull { line ->
            TAB_LINE.find(SkyblockUtils.cleanColor(line))?.groups?.get("time")?.value?.trim()
        }
    }
}
