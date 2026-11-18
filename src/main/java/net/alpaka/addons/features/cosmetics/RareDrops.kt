package net.alpaka.addons.features.cosmetics

import net.alpaka.addons.features.enchantments.Enchants
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
     * The market id of an enchanted book drop, as the Bazaar lists it: ENCHANTMENT_SMOLDERING_6 for
     * "Smoldering VI", the way a slayer drop names it, or ENCHANTMENT_ULTIMATE_CHIMERA_1 for
     * "Enchanted Book (Chimera I)". A bare name counts only when it is a known enchantment, so that
     * "Bite Rune I" stays a rune. Null for anything else.
     */
    fun bookId(name: String): String? {
        val wrapped = WRAPPED_BOOK.matchEntire(name)
        val m = LEVELLED.matchEntire(wrapped?.groupValues?.get(1) ?: name) ?: return null
        val enchant = m.groupValues[1]
        if (wrapped == null && !Enchants.isKnown(enchant)) return null
        val level = roman(m.groupValues[2]).takeIf { it > 0 } ?: return null
        val ultimate = Enchants.isUltimate(enchant)
        val key = enchant.removePrefix(if (ultimate) "Ultimate " else "")
            .uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')
        return "ENCHANTMENT_" + (if (ultimate) "ULTIMATE_" else "") + key + "_" + level
    }

    /**
     * A dye's market id, as SkyCofl and the NEU repository list it: DYE_PURE_BLACK for "Pure Black
     * Dye". Hypixel's own item list leaves the dyes out. Null for a name that is not a dye's.
     */
    fun dyeId(name: String): String? {
        if (!name.endsWith(" Dye") || name.length <= 4) return null
        return "DYE_" + name.dropLast(4).uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')
    }

    /** A pet's market id, as SkyCofl lists it: ENDER_DRAGON;4 for a legendary Ender Dragon. */
    fun petId(name: String, rarity: Int): String =
        name.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_') + ";" + rarity

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
