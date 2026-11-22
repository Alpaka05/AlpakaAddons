package net.alpaka.addons.features.cosmetics

import net.alpaka.addons.features.cosmetics.RareDrops.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Hypixel's drop lines, as the rare drop animation reads them. */
class RareDropsTest {

    private fun parse(line: String) = RareDrops.parse(line, "Alpakaa")

    @Test
    fun slayerDropInBrackets() {
        val line = "VERY RARE DROP!  (High Class Archfiend Dice) +305% ✯ Magic Find"
        val found = parse(line)!!
        assertEquals(Kind.DROP, found.kind)
        assertEquals("VERY RARE DROP!", found.heading)
        assertEquals("High Class Archfiend Dice", found.name)
        assertEquals(line.indexOf("High"), found.nameStart)
    }

    @Test
    fun slayerDropWithMagicFindInBrackets() {
        val found = parse("PRAY TO RNGESUS DROP! (Judgement Core) (+412% ✯ Magic Find)")!!
        assertEquals("Judgement Core", found.name)
        assertTrue(RareDrops.alwaysRare(found))
    }

    @Test
    fun slayerBookWithOddMagicFind() {
        // As Hypixel sent it in a Blaze fight: no percent sign, and a star the old pattern missed.
        for (star in listOf("✯", "★", "✫", "")) {
            val found = parse("CRAZY RARE DROP!  (Smoldering VI) (+109$star Magic Find)")!!
            assertEquals("Smoldering VI", found.name)
        }
        assertEquals("ENCHANTMENT_SMOLDERING_6", RareDrops.bookId("Smoldering VI", market::contains))
    }

    @Test
    fun mobDropWithAmountAndRune() {
        assertEquals("Summoning Eye", parse("RARE DROP! Summoning Eye (+155% ✯ Magic Find)")!!.name)
        assertEquals("Enchanted Diamond", parse("RARE DROP! (2x Enchanted Diamond)")!!.name)
        assertEquals("Bite Rune I", parse("RARE DROP! (◆ Bite Rune I)")!!.name)
        assertFalse(RareDrops.alwaysRare(parse("RARE DROP! Summoning Eye")!!))
    }

    @Test
    fun petDrop() {
        val found = parse("PET DROP! Ender Dragon (+120% ✯ Magic Find)")!!
        assertEquals(Kind.PET, found.kind)
        assertEquals("Ender Dragon", found.name)
        assertTrue(RareDrops.alwaysRare(found))
    }

    @Test
    fun ownDyeOnly() {
        val found = parse("WOW! [MVP+] Alpakaa found a Necron Dye #1,234!")!!
        assertEquals(Kind.DYE, found.kind)
        assertEquals("Necron Dye", found.name)
        assertEquals("WOW! [MVP+] Alpakaa found a ".length, found.nameStart)
        assertEquals("Pure Black Dye", parse("WOW! alpakaa found a Pure Black Dye!")!!.name)
        assertNull(parse("WOW! [VIP] Someone found a Necron Dye #12!"))
    }

    @Test
    fun dungeonRewards() {
        val tagged = parse("   RARE REWARD! Shadow Assassin Chestplate")!!
        assertEquals(Kind.DUNGEON, tagged.kind)
        assertEquals("Shadow Assassin Chestplate", tagged.name)
        assertEquals("Necron's Handle", parse("    Necron's Handle")!!.name)
        // Indented lines that name nothing rare, or a player talking, are not rewards.
        assertNull(parse("    Wither Essence x20"))
        assertNull(parse("   Party > Someone: Necron's Handle"))
    }

    @Test
    fun otherLines() {
        assertNull(parse("RARE CROP! Wheat"))
        assertNull(parse("[MVP+] Someone: RARE DROP! Necron's Handle"))
        assertNull(parse("You found a Necron Dye"))
    }

    /** The book ids the market trades, as far as these tests need them. */
    private val market = setOf(
        "ENCHANTMENT_SMOLDERING_6", "ENCHANTMENT_ULTIMATE_CHIMERA_1", "ENCHANTMENT_SMITE_4",
        "ENCHANTMENT_ULTIMATE_JERRY_5", "ENCHANTMENT_ULTIMATE_SOUL_EATER_1", "ENCHANTMENT_TURBO_CACTUS_1",
        "ENCHANTMENT_ULTIMATE_REITERATE_2", "ENCHANTMENT_LOOTING_NEW_6",
    )

    @Test
    fun bookIds() {
        val book = { name: String -> RareDrops.bookId(name, market::contains) }
        assertEquals("ENCHANTMENT_ULTIMATE_CHIMERA_1", book("Enchanted Book (Chimera I)"))
        assertEquals("ENCHANTMENT_SMITE_4", book("Smite IV"))
        assertEquals("ENCHANTMENT_ULTIMATE_JERRY_5", book("Ultimate Jerry V"))
        assertEquals("ENCHANTMENT_ULTIMATE_SOUL_EATER_1", book("Soul Eater I"))
        // Named otherwise than their id.
        assertEquals("ENCHANTMENT_TURBO_CACTUS_1", book("Turbo-Cacti I"))
        assertEquals("ENCHANTMENT_ULTIMATE_REITERATE_2", book("Duplex II"))
        assertEquals("ENCHANTMENT_LOOTING_NEW_6", book("Looting VI"))
        // A book the market doesn't trade still counts when the drop calls it one; a bare name doesn't.
        assertEquals("ENCHANTMENT_ENDER_SLAYER_6", book("Enchanted Book (Ender Slayer VI)"))
        assertNull(book("Bite Rune I"))
        assertNull(book("Necron's Handle"))
    }

    @Test
    fun namesFromBothLists() {
        val hypixel = mapOf("NECRON_HANDLE" to "Necron's Handle", "OLD_SHIMMER" to "Shimmer Skin")
        val skycofl = mapOf(
            "RUNE_ZOMBIE_SLAYER" to "Pestilence Rune I", "RUNE_FIERY_BURST" to "◆ Fiery Burst Rune I",
            "UNIQUE_RUNE_SMITTEN" to "◆ Smitten Rune III", "WITHER_GOGGLES_CELESTIAL" to "Celestial Wither Goggles Skin",
            "WISE_SHIMMER" to "Shimmer Skin", "PET_ENDER_DRAGON" to "Ender Dragon", "PET_TYRANNOSAURUS" to "T-Rex",
            "PET_SKIN_WOLF_DOGE" to "Doge Wolf Skin", "PET_ITEM_COMBAT_SKILL_BOOST_EPIC" to "Combat Exp Boost",
            "PET_ITEM_COMBAT_SKILL_BOOST_RARE" to "Combat Exp Boost", "FLAWLESS_RUBY_GEM" to "\uE003 Flawless Ruby Gemstone",
        )
        val index = RareDrops.NameIndex(hypixel, skycofl)
        assertEquals("NECRON_HANDLE", index.id("Necron's Handle"))
        assertEquals("RUNE_ZOMBIE_SLAYER_2", index.id("Pestilence Rune II"))
        assertEquals("RUNE_FIERY_BURST_3", index.id("◆ Fiery Burst Rune III"))
        assertEquals("UNIQUE_RUNE_SMITTEN", index.id("Smitten Rune III"))
        assertEquals("WITHER_GOGGLES_CELESTIAL", index.id("Celestial Wither Goggles Skin"))
        // Hypixel's list wins where both name an item, and pets go by their rarity, not by name.
        assertEquals("OLD_SHIMMER", index.id("Shimmer Skin"))
        assertNull(index.id("Ender Dragon"))
        assertEquals("TYRANNOSAURUS;4", RareDrops.petId("T-Rex", 4, index))
        assertEquals("PET_SKIN_WOLF_DOGE", index.id("Doge Wolf Skin"))
        assertEquals("PET_ITEM_COMBAT_SKILL_BOOST_RARE", index.id("Combat Exp Boost", rarity = 2))
        assertEquals("FLAWLESS_RUBY_GEM", index.id("Flawless Ruby Gemstone"))
        // Not in either list yet: built from the name.
        assertEquals("RUNE_BITE_3", index.id("Bite Rune III"))
        assertEquals("DYE_NECRON", index.id("Necron Dye"))
        assertNull(RareDrops.NameIndex(emptyMap(), null).id("Necron's Handle"))
    }

    @Test
    fun petAndDyeIds() {
        assertEquals("RUNE_FIERY_BURST_1", RareDrops.runeId("Fiery Burst Rune I"))
        assertNull(RareDrops.runeId("Necron's Handle"))
        assertEquals("ENDER_DRAGON;4", RareDrops.petId("Ender Dragon", 4))
        assertEquals("DYE_PURE_BLACK", RareDrops.dyeId("Pure Black Dye"))
        assertEquals("DYE_NECRON", RareDrops.dyeId("Necron Dye"))
        assertNull(RareDrops.dyeId("Necron's Handle"))
    }
}
