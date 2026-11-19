package net.alpaka.addons.features.cosmetics

import java.util.Locale

/**
 * Reads Hypixel's chat lines for the drops the rare drop animation celebrates. Plain text in, with
 * the color codes already stripped; nothing here touches the game, so it is tested on its own.
 *
 * - A drop from a mob or a slayer boss: "CRAZY RARE DROP! Ender Slayer VI (+120% ✯ Magic Find)",
 *   a slayer's with the item in brackets, "VERY RARE DROP!  (Judgement Core) (+305% ✯ Magic Find)"
 *   or "CRAZY RARE DROP!  (Smoldering VI) (+109✯ Magic Find)" for a book,
 *   and pets: "PET DROP! Ender Dragon (+120% ✯ Magic Find)".
 * - A dye, which Hypixel announces to the whole lobby: "WOW! [MVP+] Name found a Necron Dye #1,234!".
 *   Only the player's own counts.
 * - A dungeon chest's rare reward, indented: "   RARE REWARD! Necron's Handle", or one of the
 *   dungeons' big items without the tag ([DUNGEON_ITEMS]).
 */
object RareDrops {

    enum class Kind { DROP, PET, DYE, DUNGEON }

    /**
     * One drop: what kind, the heading Hypixel gave it ("CRAZY RARE DROP!"), the item's name, and
     * where the name starts in the line, which is where its color is read from.
     */
    data class Found(val kind: Kind, val heading: String, val name: String, val nameStart: Int)

    private val DROP = Regex("""^\s*(RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|PET) DROP!\s+(.+?)\s*$""")
    /**
     * The Magic Find after a drop. Its star is not always the same character, and the percent sign
     * is sometimes left out ("(+109✯ Magic Find)"), so any one symbol is taken there.
     */
    private val MAGIC_FIND = Regex("""\s*\(?\+[\d,.]+%?\s*[^\w\s()]?\s*Magic Find\)?\s*$""")
    private val WRAPPED_BOOK = Regex("""^Enchanted Book \((.+)\)$""")
    private val RUNE = Regex("""^(.+) Rune ([IVX]+)$""")
    private val LEVELLED = Regex("""^(.+) ([IVXL]+)$""")
    private val AMOUNT = Regex("""^(?:\d+x\s+)?(?:◆\s*)?(?:\[Lvl \d+]\s+)?""")
    private val DYE = Regex("""^\s*WOW! (?:\[[A-Z+]+] )?([A-Za-z0-9_]+) found (?:a|an) ([A-Za-z' ]+ Dye)(?: #[\d,]+)?!\s*$""")
    private val CHEST = Regex("""^\s{2,}(RARE REWARD!\s+)?([^:]+?)\s*$""")

    /** The tiers rare enough to celebrate whatever the item is worth. */
    private val ALWAYS = setOf("CRAZY RARE", "INSANE", "PRAY TO RNGESUS", "PET")

    /**
     * The dungeons' big chest items, which Hypixel lists in chat without a tag; from Skyblocker's
     * list of the same.
     */
    val DUNGEON_ITEMS = setOf(
        "Recombobulator 3000", "First Master Star", "Second Master Star", "Third Master Star",
        "Fourth Master Star", "Fifth Master Star", "Spirit Mask", "Shadow Fury", "Giant's Sword",
        "Necron Dye", "Dark Claymore", "Necron's Handle", "Shiny Necron's Handle", "Master Skull - Tier 5",
        "Shadow Warp", "Wither Shield", "Implosion",
    )

    /** The drop in a chat line, or null when the line is not one; [player] is this player's name. */
    fun parse(line: String, player: String): Found? {
        DROP.matchEntire(line)?.let { m ->
            val tier = m.groupValues[1]
            val restStart = m.groups[2]!!.range.first
            var rest = m.groupValues[2].replace(MAGIC_FIND, "").trim()
            // A slayer's drop has its item in brackets.
            if (rest.startsWith("(") && rest.endsWith(")")) rest = rest.substring(1, rest.length - 1).trim()
            val name = rest.replaceFirst(AMOUNT, "").trim()
            if (name.isEmpty()) return null
            val kind = if (tier == "PET") Kind.PET else Kind.DROP
            return Found(kind, "$tier DROP!", name, line.indexOf(name, restStart).coerceAtLeast(restStart))
        }
        DYE.matchEntire(line)?.let { m ->
            if (!m.groupValues[1].equals(player, ignoreCase = true)) return null
            return Found(Kind.DYE, "RARE DYE!", m.groupValues[2], m.groups[2]!!.range.first)
        }
        CHEST.matchEntire(line)?.let { m ->
            val name = m.groupValues[2]
            val tagged = m.groupValues[1].isNotEmpty()
            if (!tagged && name !in DUNGEON_ITEMS) return null
            return Found(Kind.DUNGEON, "RARE REWARD!", name, m.groups[2]!!.range.first)
        }
        return null
    }

    /** Whether a drop's tier alone makes it worth celebrating, whatever it sells for. */
    fun alwaysRare(found: Found): Boolean = found.kind == Kind.DROP && found.heading.removeSuffix(" DROP!") in ALWAYS ||
        found.kind == Kind.PET

    /**
     * The enchantments whose market id is not their name: "Turbo-Cacti" is TURBO_CACTUS, "Duplex"
     * ULTIMATE_REITERATE. Checked against the NEU repository's books, November 2026.
     */
    private val ENCHANT_IDS = mapOf(
        "Duplex" to "ULTIMATE_REITERATE", "Turbo-Cocoa" to "TURBO_COCO", "Turbo-Cacti" to "TURBO_CACTUS",
        "Drain" to "SYPHON", "Strong Vitality" to "STRONG_MANA", "Prismatic" to "PRISTINE",
        "Vampiric Vitality" to "MANA_VAMPIRE", "Pyroclasm" to "MAGMARIZER", "Hardened Vitality" to "HARDENED_MANA",
        "Vivacious Vitality" to "FEROCIOUS_MANA", "Gravity" to "DRAGON_HUNTER", "Woodsplitter" to "ARCANE",
        "Dragon Tracer" to "AIMING",
    )

    /**
     * The market id of an enchanted book drop, as the Bazaar lists it: ENCHANTMENT_SMOLDERING_6 for
     * "Smoldering VI", the way a slayer drop names it, or ENCHANTMENT_ULTIMATE_CHIMERA_1 for
     * "Enchanted Book (Chimera I)". Whether a name is an enchantment at all, and whether an ultimate
     * one, is what the market says: [exists] tells whether it trades an id. A bare name it doesn't
     * know is no book ("Bite Rune I"); a named book it doesn't know counts as a normal one.
     */
    fun bookId(name: String, exists: (String) -> Boolean): String? {
        val wrapped = WRAPPED_BOOK.matchEntire(name)
        val m = LEVELLED.matchEntire(wrapped?.groupValues?.get(1) ?: name) ?: return null
        val level = roman(m.groupValues[2]).takeIf { it > 0 } ?: return null
        val enchant = m.groupValues[1]
        val key = ENCHANT_IDS[enchant] ?: idKey(enchant)
        // Looting VI and VII, added later, trade as LOOTING_NEW; the old ids are left without offers.
        val candidates = if (key.startsWith("ULTIMATE_")) listOf("ENCHANTMENT_${key}_$level")
        else listOf("ENCHANTMENT_ULTIMATE_${key}_$level", "ENCHANTMENT_${key}_NEW_$level", "ENCHANTMENT_${key}_$level")
        return candidates.firstOrNull(exists) ?: if (wrapped != null) candidates.last() else null
    }

    /**
     * Item ids by the names drops call them, from Hypixel's item list and, for what that leaves out
     * (runes, dyes, skins), SkyCofl's: both as id to name. Hypixel's wins where both name an item.
     */
    class NameIndex(hypixel: Map<String, String>, others: Map<String, String>?) {
        private val byName = HashMap<String, MutableList<String>>()
        /** Runes by the name before "Rune": "pestilence" for RUNE_ZOMBIE_SLAYER. */
        private val runes = HashMap<String, String>()
        /** Pets by name, as their id without PET_: "t-rex" for TYRANNOSAURUS. */
        private val pets = HashMap<String, String>()

        init {
            add(hypixel, byName)
            if (others != null) {
                val extra = HashMap<String, MutableList<String>>()
                for ((id, name) in others) {
                    // A pet itself needs its rarity, which [pet] and [petId] work out; its skins and
                    // items are plain items. A book needs its level, which [bookId] works out.
                    if (id.startsWith("PET_") && !id.startsWith("PET_SKIN_") && !id.startsWith("PET_ITEM_")) {
                        pets.putIfAbsent(normalize(name), id.removePrefix("PET_"))
                    }
                }
                add(others.filterKeys { !(it.startsWith("PET_") && !it.startsWith("PET_SKIN_") && !it.startsWith("PET_ITEM_")) &&
                    !it.startsWith("ENCHANTMENT_") }, extra)
                extra.forEach { (name, ids) -> byName.putIfAbsent(name, ids) }
            }
            byName.values.forEach { ids -> ids.sortWith(compareBy({ it.length }, { it })) }
        }

        private fun add(items: Map<String, String>, into: HashMap<String, MutableList<String>>) {
            for ((id, itemName) in items) {
                val name = normalize(itemName)
                if (id.startsWith("RUNE_") || id.startsWith("UNIQUE_RUNE_")) {
                    RUNE_NAME.matchEntire(name)?.let { runes.merge(it.groupValues[1], id, ::shorter) }
                }
                into.getOrPut(name) { ArrayList(1) }.add(id)
            }
        }

        /**
         * The id of the item a drop names; for a rune, at the level it names. Where several items
         * share the name, the one of the drop's [rarity] (0 for Common up to 5 for Mythic, as the
         * pet items are, PET_ITEM_COMBAT_SKILL_BOOST_RARE), else the plain item rather than a
         * variant of it. Null when unknown.
         */
        fun id(name: String, rarity: Int = -1): String? {
            val n = normalize(name)
            RUNE_LEVEL.matchEntire(n)?.let { m ->
                val level = roman(m.groupValues[2].uppercase(Locale.ROOT))
                val rune = runes[m.groupValues[1]]
                if (rune != null && level > 0) return if (rune.startsWith("RUNE_")) "${rune}_$level" else rune
            }
            val ids = byName[n] ?: return runeId(name) ?: dyeId(name)
            val tier = RARITIES.getOrNull(rarity)
            return ids.firstOrNull { tier != null && it.endsWith("_$tier") } ?: ids.first()
        }

        /** A pet's id without its rarity, by its name: TYRANNOSAURUS for "T-Rex"; null when unknown. */
        fun pet(name: String): String? = pets[normalize(name)]

        private companion object {
            val RUNE_NAME = Regex("""^(.+) rune(?: [ivx]+)?$""")
            val RUNE_LEVEL = Regex("""^(.+) rune ([ivx]+)$""")
            val RARITIES = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")

            /**
             * Lower case, without a mark in front: "◆ Fiery Burst Rune I" as "fiery burst rune i",
             * and a gemstone's name without the icon some lists put before it.
             */
            fun normalize(name: String) = name.trim().dropWhile { !it.isLetterOrDigit() }.lowercase(Locale.ROOT)

            fun shorter(a: String, b: String) = if (b.length < a.length || (b.length == a.length && b < a)) b else a
        }
    }

    /**
     * A rune's market id, as SkyCofl lists it: RUNE_FIERY_BURST_1 for "Fiery Burst Rune I", for a
     * rune [NameIndex] has no name for. Null for a name that is not a rune's.
     */
    fun runeId(name: String): String? {
        val m = RUNE.matchEntire(name.trim().dropWhile { !it.isLetterOrDigit() }) ?: return null
        val level = roman(m.groupValues[2]).takeIf { it > 0 } ?: return null
        return "RUNE_" + idKey(m.groupValues[1]) + "_" + level
    }

    private fun idKey(name: String) = name.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')

    /**
     * A dye's market id, as SkyCofl and the NEU repository list it: DYE_PURE_BLACK for "Pure Black
     * Dye". Hypixel's own item list leaves the dyes out. Null for a name that is not a dye's.
     */
    fun dyeId(name: String): String? {
        if (!name.endsWith(" Dye") || name.length <= 4) return null
        return "DYE_" + name.dropLast(4).uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')
    }

    /** A pet's market id, as SkyCofl lists it: ENDER_DRAGON;4 for a legendary Ender Dragon. */
    fun petId(name: String, rarity: Int, index: NameIndex? = null): String =
        (index?.pet(name) ?: idKey(name)) + ";" + rarity

    private fun roman(text: String): Int {
        var total = 0
        var previous = 0
        for (c in text.reversed()) {
            val v = when (c) { 'I' -> 1; 'V' -> 5; 'X' -> 10; 'L' -> 50; else -> return 0 }
            total += if (v < previous) -v else v
            previous = maxOf(previous, v)
        }
        return total
    }
}
