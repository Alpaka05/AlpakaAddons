package net.alpaka.addons.features.profittracker

import net.alpaka.addons.features.slayer.SlayerType

/**
 * What the player is doing, which decides the bucket a gain goes into. The id is what the file and
 * the CSV files store; the title is what the HUD shows.
 *
 * The fixed activities are the ones other mods give a tracker of their own (SkyHanni has one each
 * for slayer, fishing, Diana, pests, dragons, ...). Anything else is filed under the island it
 * happened on ("island:Crimson Isle"), so the tracker counts everywhere without mixing places.
 */
data class ProfitActivity(val id: String, val title: String) {

    val isIsland: Boolean get() = id.startsWith(ISLAND_PREFIX)

    companion object {
        const val ISLAND_PREFIX = "island:"

        @JvmField val FISHING = ProfitActivity("fishing", "Fishing")
        @JvmField val DIANA = ProfitActivity("diana", "Mythological Ritual")
        @JvmField val PESTS = ProfitActivity("pests", "Pests")
        @JvmField val FARMING = ProfitActivity("farming", "Farming")
        @JvmField val DRAGONS = ProfitActivity("dragons", "Ender Dragons")
        @JvmField val DUNGEONS = ProfitActivity("dungeons", "Dungeons")
        @JvmField val KUUDRA = ProfitActivity("kuudra", "Kuudra")
        @JvmField val EXPERIMENTS = ProfitActivity("experiments", "Experiments")
        @JvmField val UNKNOWN = ProfitActivity("island:Unknown", "Unknown")

        private val FIXED = listOf(FISHING, DIANA, PESTS, FARMING, DRAGONS, DUNGEONS, KUUDRA, EXPERIMENTS)
            .associateBy { it.id }

        /** The boss names Hypixel gives each slayer, for the HUD title. */
        private fun bossName(type: SlayerType): String = when (type) {
            SlayerType.ZOMBIE -> "Revenant Horror"
            SlayerType.SPIDER -> "Tarantula Broodfather"
            SlayerType.WOLF -> "Sven Packmaster"
            SlayerType.ENDERMAN -> "Voidgloom Seraph"
            SlayerType.BLAZE -> "Inferno Demonlord"
            SlayerType.VAMPIRE -> "Riftstalker Bloodfiend"
            SlayerType.GUARDIAN -> "Guardian Slayer"
        }

        @JvmStatic
        fun slayer(type: SlayerType): ProfitActivity =
            ProfitActivity("slayer:" + type.name.lowercase(), bossName(type))

        @JvmStatic
        fun island(name: String): ProfitActivity = ProfitActivity(ISLAND_PREFIX + name, name)

        /** The activity a stored id stands for; works for ids of islands and slayers not seen this session. */
        @JvmStatic
        fun of(id: String): ProfitActivity {
            FIXED[id]?.let { return it }
            if (id.startsWith("slayer:")) {
                val type = SlayerType.entries.firstOrNull { "slayer:" + it.name.lowercase() == id }
                if (type != null) return slayer(type)
            }
            if (id.startsWith(ISLAND_PREFIX)) return island(id.substring(ISLAND_PREFIX.length))
            return ProfitActivity(id, id)
        }
    }
}
