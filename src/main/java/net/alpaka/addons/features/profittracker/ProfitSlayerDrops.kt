package net.alpaka.addons.features.profittracker

import net.alpaka.addons.features.slayer.SlayerType

/**
 * What each slayer boss drops, so only that counts toward the slayer while a quest runs. Without it
 * everything that landed in the inventory during a quest was the slayer's: loot from a corpse,
 * a gift, an item bought from a player.
 *
 * The lists are SkyHanni's (SkyHanni-REPO `constants/SlayerProfitTrackerItems.json`, MIT, as of
 * 2026-11-18), in NEU's keys. An item a boss drops that is missing here is not lost: it is counted
 * for the island instead.
 */
object ProfitSlayerDrops {

    private val NEU_KEYS: Map<SlayerType, List<String>> = mapOf(
        SlayerType.ZOMBIE to listOf(
            "REVENANT_FLESH", "FOUL_FLESH", "ZOMBIE_SLAYER_RUNE;1", "UNDEAD_CATALYST", "SMITE;6",
            "BEHEADED_HORROR", "REVENANT_CATALYST", "SNAKE_RUNE;1", "REVENANT_VISCERA", "SCYTHE_BLADE",
            "SMITE;7", "SHARD_OF_THE_SHREDDED", "WARDEN_HEART", "ROTTEN_FLESH", "GOLD_INGOT",
            "GOLDEN_POWDER", "GHOUL;3", "GHOUL;4", "DYE_MATCHA", "ATTRIBUTE_SHARD_UNDEAD_ESSENCE;1",
            "FESTERING_MAGGOT", "SEVERED_HAND",
        ),
        SlayerType.SPIDER to listOf(
            "TARANTULA_WEB", "TOXIC_ARROW_POISON", "BITE_RUNE;1", "SPIDER_CATALYST", "BANE_OF_ARTHROPODS;6",
            "FLY_SWATTER", "TARANTULA_TALISMAN", "DIGESTED_MOSQUITO", "SPIDER_EYE", "STRING", "BONE",
            "TARANTULA;3", "TARANTULA;4", "SPIDERS_DEN_TOP_TRAVEL_SCROLL", "ARACHNE_KEEPER_FRAGMENT",
            "BURNING_EYE", "DARKNESS_WITHIN_RUNE;1", "TARANTULA_CATALYST", "VIAL_OF_VENOM", "PRIMORDIAL_EYE",
            "ENSNARED_SNAIL", "SHRIVELED_WASP", "TARANTULA_SILK", "ATTRIBUTE_SHARD_ARACHNO;1", "DYE_BRICK_RED",
        ),
        SlayerType.WOLF to listOf(
            "WOLF_TOOTH", "HAMSTER_WHEEL", "SPIRIT_RUNE;1", "CRITICAL;6", "FURBALL", "RED_CLAW_EGG",
            "COUTURE_RUNE;1", "OVERFLUX_CAPACITOR", "GRIZZLY_BAIT", "BONE", "WOLF_TALISMAN",
            "PARK_CAVE_TRAVEL_SCROLL", "HOUND;3", "HOUND;4", "WEAK_WOLF_CATALYST",
            "PET_ITEM_FORAGING_SKILL_BOOST_EPIC", "DYE_CELESTE", "ATTRIBUTE_SHARD_COMBO;1",
        ),
        SlayerType.ENDERMAN to listOf(
            "NULL_SPHERE", "TWILIGHT_ARROW_POISON", "ENDERSNAKE_RUNE;1", "SUMMONING_EYE", "MANA_STEAL;1",
            "TRANSMISSION_TUNER", "NULL_ATOM", "HAZMAT_ENDERMAN", "POCKET_ESPRESSO_MACHINE", "SMARTY_PANTS;1",
            "DRAGON_RUNE;1", "HANDY_BLOOD_CHALICE", "SINFUL_DICE", "EXCEEDINGLY_RARE_ENDER_ARTIFACT_UPGRADER",
            "PET_SKIN_ENDERMAN_SLAYER", "ETHERWARP_MERGER", "JUDGEMENT_CORE", "ENCHANT_RUNE;1", "ENDER_SLAYER;7",
            "ENCHANTED_ENDER_PEARL", "ENDER_PEARL", "ENDERMAN_CORTEX_REWRITER", "ENDERMAN;0", "ENDERMAN;1",
            "ENDERMAN;2", "ENDERMAN;3", "ENDERMAN;4", "DYE_BYZANTIUM", "ATTRIBUTE_SHARD_ENDER;1", "ENDSTONE_IDOL",
        ),
        SlayerType.BLAZE to listOf(
            "DERELICT_ASHE", "LAVATEARS_RUNE;1", "WISP_POTION", "ARROW_BUNDLE_MAGMA", "MANA_DISINTEGRATOR",
            "SCORCHED_BOOKS", "KELVIN_INVERTER", "BLAZE_ROD_DISTILLATE", "GLOWSTONE_DUST_DISTILLATE",
            "MAGMA_CREAM_DISTILLATE", "NETHER_STALK_DISTILLATE", "CRUDE_GABAGOOL_DISTILLATE",
            "SCORCHED_POWER_CRYSTAL", "ARCHFIEND_DICE", "FIRE_ASPECT;3", "FIERY_BURST_RUNE;1", "FLAWED_OPAL_GEM",
            "FINE_OPAL_GEM", "ULTIMATE_REITERATE;1", "HIGH_CLASS_ARCHFIEND_DICE", "WILSON_ENGINEERING_PLANS",
            "SUBZERO_INVERTER", "BLAZE_ASHES", "BLAZE_ROD", "ENCHANTED_BLAZE_POWDER", "NETHERRACK_LOOKING_SUNSHADE",
            "MILLENIA_OLD_BLAZE_ASHES", "SMOLDERING;1", "SMOLDERING;6", "SWORD_OF_BAD_HEALTH", "DYE_FLAME",
            "ATTRIBUTE_SHARD_ATTACK_SPEED;1", "CORRUPTED_NETHER_STAR", "DYE_CYCLAMEN",
        ),
        SlayerType.VAMPIRE to listOf(
            "COVEN_SEAL", "ENCHANTED_BOOK_BUNDLE_QUANTUM", "SOULTWIST_RUNE;1", "BUBBA_BLISTER", "CHOCOLATE_CHIP",
            "GUARDIAN_LUCKY_BLOCK", "MCGRUBBER_BURGER", "UNFANGED_VAMPIRE_PART", "ENCHANTED_BOOK_BUNDLE_THE_ONE",
            "HEMOVIBE", "VAMPIRIC_MELON", "DYE_SANGRIA",
        ),
    )

    private val DROPS: Map<SlayerType, Set<String>> = NEU_KEYS.mapValues { (_, keys) -> keys.flatMap(::ownKeys).toSet() }

    private val RUNE_LEVEL = Regex("""_\d+$""")

    /** Whether [key], as [ProfitItems.key] writes it, is something this slayer's boss drops. */
    @JvmStatic
    fun isDrop(type: SlayerType, key: String): Boolean {
        val drops = DROPS[type] ?: return false
        // A rune counts at any level: the boss drops level 1, the key carries the level it has.
        val k = if (key.startsWith("RUNE_")) RUNE_LEVEL.replace(key, "") else key
        return k in drops
    }

    /**
     * The tracker's keys for one NEU key. NEU writes pets, books and runes all as "NAME;N"; the
     * tracker files pets the same way, books as "ENCHANTMENT_NAME_N", runes as "RUNE_NAME_N" (as Item
     * Value does) and an attribute shard under its plain id.
     */
    internal fun ownKeys(neu: String): List<String> {
        val semi = neu.indexOf(';')
        if (semi < 0) return listOf(neu)
        val name = neu.substring(0, semi)
        val level = neu.substring(semi + 1)
        return when {
            name.endsWith("_RUNE") -> listOf("RUNE_" + name.removeSuffix("_RUNE"))
            name.startsWith("ATTRIBUTE_SHARD_") -> listOf("ATTRIBUTE_SHARD")
            // A pet ("GHOUL;3") or a book ("SMITE;6"); the two never share a name.
            else -> listOf(neu, "ENCHANTMENT_${name}_$level")
        }
    }
}
