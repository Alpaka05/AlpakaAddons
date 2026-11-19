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
        assertEquals("ENCHANTMENT_SMOLDERING_6", RareDrops.bookId("Smoldering VI"))
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

    @Test
    fun bookAndPetIds() {
        assertEquals("ENCHANTMENT_ULTIMATE_CHIMERA_1", RareDrops.bookId("Enchanted Book (Chimera I)"))
        assertEquals("ENCHANTMENT_ENDER_SLAYER_6", RareDrops.bookId("Enchanted Book (Ender Slayer VI)"))
        assertEquals("ENCHANTMENT_SMITE_4", RareDrops.bookId("Smite IV"))
        assertEquals("ENCHANTMENT_ULTIMATE_JERRY_5", RareDrops.bookId("Ultimate Jerry V"))
        assertEquals("ENCHANTMENT_ULTIMATE_SOUL_EATER_1", RareDrops.bookId("Soul Eater I"))
        assertNull(RareDrops.bookId("Bite Rune I"))
        assertEquals("RUNE_FIERY_BURST_1", RareDrops.runeId("Fiery Burst Rune I"))
        assertEquals("RUNE_BITE_3", RareDrops.runeId("Bite Rune III"))
        assertNull(RareDrops.runeId("Necron's Handle"))
        assertNull(RareDrops.bookId("Necron's Handle"))
        assertEquals("ENDER_DRAGON;4", RareDrops.petId("Ender Dragon", 4))
        assertEquals("DYE_PURE_BLACK", RareDrops.dyeId("Pure Black Dye"))
        assertEquals("DYE_NECRON", RareDrops.dyeId("Necron Dye"))
        assertNull(RareDrops.dyeId("Necron's Handle"))
    }
}
