package net.alpaka.addons.utils

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component

/**
 * Whether the player is in a Catacombs run, which floor, and whether the floor's boss room has
 * been reached.
 *
 * The floor comes off the sidebar line Hypixel shows throughout a run, `⏣ The Catacombs (F7)`.
 * The boss room is entered when the floor's boss first speaks in chat - `[BOSS] Maxor: ...` - which
 * is how SkyHanni's DungeonApi tells it too. The Watcher speaks during the clear, before any boss
 * room, so his lines never count, and neither does another floor's boss.
 *
 * Reset on every world change: a run, its floor and its boss room all end with the world.
 */
object DungeonState {

    private val FLOOR = Regex("The Catacombs \\((?<floor>[EFM]\\d?)\\)")
    private val BOSS_LINE = Regex("^\\[BOSS] (?<name>[^:]+):")

    /** How often the sidebar is read for the floor; it only changes when the world does. */
    private const val SIDEBAR_REFRESH_TICKS = 20

    /** "F7", "M3", "E" while in a run; null anywhere else. */
    @JvmStatic
    var floor: String? = null
        private set

    @JvmStatic
    var inBossRoom: Boolean = false
        private set

    private var ticks = 0

    @JvmStatic
    fun inDungeon(): Boolean = floor != null

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
        // A boss line another mod hides (SkyHanni's "Dungeon Boss Messages") arrives here instead,
        // and still marks the boss room.
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> if (!overlay) onChat(message) }
    }

    @JvmStatic
    fun reset() {
        floor = null
        inBossRoom = false
    }

    private fun tick() {
        if (++ticks % SIDEBAR_REFRESH_TICKS != 0) return
        if (!SkyblockUtils.isOnSkyblock()) {
            reset()
            return
        }
        val found = SkyblockUtils.getSidebarLines().firstNotNullOfOrNull { line ->
            FLOOR.find(SkyblockUtils.cleanColor(line))?.groups?.get("floor")?.value
        }
        // Kept once known: a sidebar briefly redrawing without the line does not end the run.
        if (found != null) floor = found
    }

    private fun onChat(message: Component) {
        val current = floor ?: return
        if (inBossRoom) return
        val name = BOSS_LINE.find(SkyblockUtils.cleanColor(message.string))?.groups?.get("name")?.value?.trim() ?: return
        val boss = bossOf(current) ?: return
        // Livid speaks with a color word in front of her name ("Hockey Livid"), hence endsWith.
        if (name != "The Watcher" && name.endsWith(boss)) inBossRoom = true
    }

    private fun bossOf(floor: String): String? = when (floor.drop(1)) {
        "1" -> "Bonzo"
        "2" -> "Scarf"
        "3" -> "The Professor"
        "4" -> "Thorn"
        "5" -> "Livid"
        "6" -> "Sadan"
        "7" -> "Maxor"
        else -> null // The entrance's only boss is The Watcher, who never opens a boss room.
    }
}
