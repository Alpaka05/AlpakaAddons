package net.alpaka.addons.features.playermodel

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Locale
import java.util.Optional

/**
 * What Hypixel's own text says about the pet that is out: the Pet widget of the tab list and the
 * chat lines for summoning, despawning and Autopet. Text only, so it can be tested without a game.
 *
 * The lines are the ones the other SkyBlock mods read (SkyHanni's `CurrentPetApi`): the game sends
 * nothing else about the pet, and the pet beside the player is not there for everyone, as SkyBlock
 * can hide pets.
 */
internal object PetText {

    /** A pet as the text names it. [rarity] is 0 (Common) to 5 (Mythic), or -1 when the line had no color. */
    data class Pet(val name: String, val rarity: Int, val skinned: Boolean) {
        /** The pet's id in the NEU repository, which names the pet files after Hypixel's pet types. */
        val type: String
            get() = if (name.equals("T-Rex", ignoreCase = true)) "TYRANNOSAURUS"
            else name.uppercase(Locale.ROOT).replace(' ', '_')
    }

    /** What a line says: this pet is out now, or with a null [pet], none is. */
    class Update(val pet: Pet?)

    /**
     * A line without its color codes, and the color each character is written in. The pet's rarity
     * is only ever given as the color of its name.
     */
    class Colored(val plain: String, private val colors: IntArray) {

        /** The RGB color of the character at [index], or -1 without one. */
        fun colorAt(index: Int): Int = if (index in colors.indices) colors[index] else -1

        /** The same line without the blanks around it; tab list rows are indented. */
        fun trimmed(): Colored {
            val start = plain.indexOfFirst { !it.isWhitespace() }
            if (start < 0) return Colored("", IntArray(0))
            val end = plain.indexOfLast { !it.isWhitespace() } + 1
            if (start == 0 && end == plain.length) return this
            return Colored(plain.substring(start, end), colors.copyOfRange(start, end))
        }

        companion object {
            /** A line written with `§` color codes. */
            fun ofLegacy(text: String): Colored = Builder().append(text, -1).build()

            /** A chat line or tab row. Hypixel colors them with styles, with codes, or with both. */
            fun of(component: Component): Colored {
                val builder = Builder()
                component.visit({ style: Style, text: String ->
                    // A piece without a color of its own starts over: the style before it has ended.
                    builder.append(text, style.color?.value ?: -1)
                    Optional.empty<Unit>()
                }, Style.EMPTY)
                return builder.build()
            }
        }

        private class Builder {
            private val plain = StringBuilder()
            private var colors = IntArray(64)

            /** Adds [text], which starts in [color] and may change it with `§` codes of its own. */
            fun append(text: String, color: Int): Builder {
                var current = color
                var i = 0
                while (i < text.length) {
                    val c = text[i]
                    if (c == '§' && i + 1 < text.length) {
                        val code = text[i + 1].lowercaseChar()
                        val index = LEGACY_CODES.indexOf(code)
                        if (index >= 0) current = LEGACY_COLORS[index] else if (code == 'r') current = -1
                        i += 2
                        continue
                    }
                    if (plain.length == colors.size) colors = colors.copyOf(colors.size * 2)
                    colors[plain.length] = current
                    plain.append(c)
                    i++
                }
                return this
            }

            fun build(): Colored = Colored(plain.toString(), colors.copyOf(plain.length))
        }
    }

    private const val LEGACY_CODES = "0123456789abcdef"
    private val LEGACY_COLORS = intArrayOf(
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    )

    /** The colors SkyBlock writes pet rarities in, Common to Mythic. */
    private val RARITY_COLORS = intArrayOf(0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF)

    private const val NAME = """(?<pet>[\w' -]+?)(?<skin> ✦)?"""
    /** "[Lvl 100] ", and for a Golden Dragon past level 100 also its "[122✦] ". */
    private const val LEVEL = """\[Lvl [\d,]+] (?:\[\d+✦] )?"""

    /** `[Lvl 100] Golden Dragon`, `[Lvl 100] Chicken ✦`, `[Lvl 200] [122✦] Golden Dragon`. */
    private val TAB_PET = Regex("^$LEVEL$NAME$")
    private const val TAB_NONE = "No pet selected"

    /** `You summoned your Golden Dragon!`, with ` ✦` after a pet that wears a skin. */
    private val SUMMONED = Regex("^You summoned your $NAME!$")
    private val DESPAWNED = Regex("""^You despawned your [\w' -]+?(?: ✦)?!$""")
    /** `Autopet equipped your [Lvl 100] Rabbit ✦! VIEW RULE`; chat mods add a count to repeats. */
    private val AUTOPET = Regex("""^Autopet equipped your $LEVEL$NAME! VIEW RULE(?: \(\d+\))?$""")

    /** What a row of the tab list says about the pet, or null for every other row. */
    fun parseTab(row: Colored): Update? {
        val line = row.trimmed()
        if (line.plain == TAB_NONE) return Update(null)
        // Every pet row starts like this and hardly another one does, so most rows end here.
        if (!line.plain.startsWith("[Lvl ")) return null
        return TAB_PET.find(line.plain)?.let { Update(pet(line, it)) }
    }

    /** What a chat line says about the pet, or null for every other line. */
    fun parseChat(message: Colored): Update? {
        val line = message.trimmed()
        val text = line.plain
        if (text.startsWith("You summoned your ")) {
            return SUMMONED.find(text)?.let { Update(pet(line, it)) }
        }
        if (text.startsWith("You despawned your ")) {
            return if (DESPAWNED.matches(text)) Update(null) else null
        }
        if (text.startsWith("Autopet equipped your ")) {
            return AUTOPET.find(text)?.let { Update(pet(line, it)) }
        }
        return null
    }

    private fun pet(line: Colored, match: MatchResult): Pet {
        val name = match.groups["pet"]!!
        val rarity = RARITY_COLORS.indexOf(line.colorAt(name.range.first))
        return Pet(name.value, rarity, match.groups["skin"] != null)
    }
}
