package net.alpaka.addons.features.slayer

import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents

/**
 * How many players share the Blazetekk Ham Radio's channel, for the Blaze slayer HUD's "Radio" line.
 *
 * Read from the radio's own lore ("Nearby: 2 enjoyers"), which Hypixel keeps up to date while the
 * item sits in the inventory. The count includes the player: the full buff needs exactly two players
 * on the channel, one alone gets half, and three or more get nothing.
 */
object HamRadio {

    private const val ITEM_ID = "BLAZETEKK_HAM_RADIO"

    private val NEARBY_LINE = Regex("^Nearby:\\s*(?<rest>.*)$")
    private val COUNT = Regex("\\d+")

    /** How often the inventory is read. */
    private const val READ_INTERVAL_TICKS = 20

    private var ticks = 0

    /** Whether a radio is anywhere in the inventory. */
    @JvmStatic
    var inInventory = false
        private set

    /** Players on the channel as the radio last showed it; null while there is no radio or no count. */
    @JvmStatic
    var nearby: Int? = null
        private set

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    private fun tick(mc: Minecraft) {
        if (++ticks % READ_INTERVAL_TICKS != 0) return
        val player = mc.player
        if (player == null || !SkyblockUtils.isOnSkyblock()) {
            inInventory = false
            nearby = null
            return
        }
        val radio = player.inventory.nonEquipmentItems.firstOrNull { stack ->
            !stack.isEmpty && stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "") == ITEM_ID
        }
        inInventory = radio != null
        nearby = radio?.get(DataComponents.LORE)?.lines()?.let { lines ->
            parseNearby(lines.map { SkyblockUtils.cleanColor(it.string) })
        }
    }

    /**
     * The player count from the radio's lore lines, colour codes already stripped. A "Nearby:" line
     * without a number (as when nobody else is tuned in) counts as alone.
     */
    @JvmStatic
    fun parseNearby(lines: List<String>): Int? {
        for (line in lines) {
            val rest = NEARBY_LINE.find(line.trim())?.groups?.get("rest")?.value ?: continue
            return COUNT.find(rest)?.value?.toIntOrNull() ?: 1
        }
        return null
    }
}
