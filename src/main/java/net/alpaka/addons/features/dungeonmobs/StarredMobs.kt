package net.alpaka.addons.features.dungeonmobs

import net.alpaka.addons.utils.SkyblockUtils

/** Reads Hypixel's dungeon mob name tags, `✯ Zombie Commander 1.2M❤` for a starred one. */
object StarredMobs {

    private const val STAR = '✯'

    /** The health a mob's name tag ends in: `1.2M❤`, `850k❤`, `0❤`. */
    private val HEALTH = Regex("\\d[\\d,.]*[kKmMbB]?❤$")

    /**
     * Minibosses are player NPCs by these names, Skyblocker's list; the Angry Archaeologist is
     * "Diamond Guy" underneath.
     */
    private val MINIBOSSES = setOf("Lost Adventurer", "Shadow Assassin", "Diamond Guy", "Frozen Adventurer")

    /** Name tags never hidden though they carry no star: a miniboss, and the Mimic, which counts for score. */
    private val ALWAYS_SHOWN = listOf("Lost Adventurer", "Shadow Assassin", "Angry Archaeologist", "Frozen Adventurer", "Mimic")

    /** Whether [name] is the name tag of a starred mob. */
    @JvmStatic
    fun isStarredTag(name: String): Boolean {
        val clean = SkyblockUtils.cleanColor(name)
        return STAR in clean && HEALTH.containsMatchIn(clean)
    }

    /** Whether [name] is the name tag of a mob without a star, the kind Hide Non-Starred Name Tags hides. */
    @JvmStatic
    fun isPlainMobTag(name: String): Boolean {
        val clean = SkyblockUtils.cleanColor(name)
        if (STAR in clean || !HEALTH.containsMatchIn(clean)) return false
        return ALWAYS_SHOWN.none { it in clean }
    }

    @JvmStatic
    fun isMiniboss(profileName: String?): Boolean = profileName in MINIBOSSES
}
