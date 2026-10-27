package net.alpaka.addons.features.dungeonmap

import net.alpaka.addons.mixin.PlayerTabOverlayAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft

/** One row of the tab list's party section. */
data class PartyMember(val name: String, val dungeonClass: String) {
    /** Dead players show `(DEAD)`, and before the run starts everyone is `(EMPTY)`. */
    val alive get() = dungeonClass != "DEAD" && dungeonClass != "EMPTY"
}

/**
 * The run's party, in the order the tab list shows it.
 *
 * Hypixel sends the other players' map markers in this same order and leaves out the dead, which is
 * how a marker gets a name (Skyblocker, Odin and NoFrills pair them the same way). The client keeps
 * tab entries in a hash set, so the order is restored with the tab list's own comparator.
 */
object DungeonParty {
    // "[303] AzureAaron ⚡ (Mage XL)", "[12] Someone (DEAD)", "[220] [MVP+] Name ♲ (Tank V)"
    private val LINE = Regex("""^\[\d+] (?:\[[^\]]+] )*([A-Za-z0-9_]{1,16})(?: .*?)? \(([A-Za-z]+)(?: [IVXLC0-9]+)?\)$""")
    private val CLASSES = setOf("Healer", "Mage", "Berserk", "Archer", "Tank", "DEAD", "EMPTY")

    fun parse(line: String): PartyMember? {
        val match = LINE.matchEntire(line.trim()) ?: return null
        val dungeonClass = match.groupValues[2]
        return if (dungeonClass in CLASSES) PartyMember(match.groupValues[1], dungeonClass) else null
    }

    /** The party rows among the tab lines, in their order, each name once. */
    fun parseAll(lines: List<String>): List<PartyMember> =
        lines.mapNotNull(::parse).distinctBy { it.name.lowercase() }

    fun read(mc: Minecraft): List<PartyMember> {
        val connection = mc.connection ?: return emptyList()
        val lines = connection.listedOnlinePlayers
            .sortedWith(PlayerTabOverlayAccessor.`alpaka$playerComparator`())
            .mapNotNull { info -> info.tabListDisplayName?.let { SkyblockUtils.cleanColor(it.string) } }
        return parseAll(lines)
    }
}
