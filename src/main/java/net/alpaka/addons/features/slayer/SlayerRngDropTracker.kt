package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaStats

/**
 * How many bosses have been killed since a slayer's headline RNG drop last appeared.
 *
 * This reads the drop history [SlayerDropTracker] already persists, which stores the kill count a
 * drop happened at, so the answer is simply the distance from that mark to the current count. The
 * figure survives restarts because that history is part of the saved config.
 */
object SlayerRngDropTracker {

    /**
     * Bosses killed since this slayer's headline RNG drop, or null when it has never dropped (so
     * there is no mark to measure from).
     *
     * Drop names are matched loosely because Hypixel writes them with quantity and rune prefixes
     * stripped at different points, and because a slayer's headline drop often has a lesser variant
     * sharing most of its name - the Archfiend Dice and its High Class version, for instance. The
     * most recent matching drop wins.
     */
    fun bossesSince(type: SlayerType): Int? {
        val item = type.rngDropItem ?: return null
        val data = AlpakaStats.slayerBossMap()[type] ?: return null
        val drops = data.drops ?: return null

        val lastDropAtKill = drops.entries
            .filter { it.key.equals(item, ignoreCase = true) }
            .maxOfOrNull { it.value } ?: return null

        return (data.kills - lastDropAtKill).coerceAtLeast(0)
    }
}
