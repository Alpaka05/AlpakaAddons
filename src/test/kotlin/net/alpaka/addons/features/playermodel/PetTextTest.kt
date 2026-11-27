package net.alpaka.addons.features.playermodel

import net.alpaka.addons.features.playermodel.PetText.Colored
import net.alpaka.addons.features.playermodel.PetText.Pet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The lines are Hypixel's own, as SkyHanni's pattern tests record them. */
class PetTextTest {

    private fun tab(line: String) = PetText.parseTab(Colored.ofLegacy(line))
    private fun chat(line: String) = PetText.parseChat(Colored.ofLegacy(line))

    @Test
    fun tabRowNamesThePetAndItsRarity() {
        assertEquals(Pet("Golden Dragon", 4, false), tab(" §7[Lvl 100] §6Golden Dragon")!!.pet)
        assertEquals(Pet("Hedgehog", 3, false), tab(" §7[Lvl 100] §5Hedgehog")!!.pet)
        assertEquals(Pet("Flying Fish", 5, false), tab(" §7[Lvl 100] §dFlying Fish")!!.pet)
        assertEquals(Pet("T-Rex", 4, false), tab(" §7[Lvl 67] §6T-Rex")!!.pet)
    }

    @Test
    fun tabRowWithoutColorsHasNoRarity() {
        assertEquals(Pet("Blaze", -1, false), tab(" [Lvl 68] Blaze")!!.pet)
    }

    @Test
    fun tabRowMarksASkin() {
        assertEquals(Pet("Chicken", 4, true), tab(" §7[Lvl 100] §6Chicken§d ✦")!!.pet)
    }

    @Test
    fun tabRowOfAGoldenDragonPastLevelHundred() {
        assertEquals(Pet("Golden Dragon", 4, false), tab(" §7[Lvl 200] §6[122✦] Golden Dragon")!!.pet)
        assertEquals(
            Pet("Golden Dragon", 4, false),
            tab(" §7[Lvl 200] §8[§6335§8§4✦§8] §6Golden Dragon")!!.pet
        )
    }

    @Test
    fun tabRowWithoutAPet() {
        val update = tab(" §cNo pet selected")
        assertNotNull(update)
        assertNull(update!!.pet)
    }

    @Test
    fun otherTabRowsSayNothing() {
        assertNull(tab("§e§lPet:"))
        assertNull(tab(" §6+163,119,730.2 XP"))
        assertNull(tab(" §b§lMAX LEVEL"))
        assertNull(tab("§8[§a312§8] §bAlpakaa"))
        assertNull(tab(""))
    }

    @Test
    fun summoningNamesThePet() {
        assertEquals(Pet("Golden Dragon", 4, false), chat("§aYou summoned your §r§6Golden Dragon§r§a!")!!.pet)
        assertEquals(Pet("Rabbit", 5, true), chat("§aYou summoned your §r§dRabbit§r§9 ✦§r§a!")!!.pet)
    }

    @Test
    fun despawningLeavesNoPet() {
        val update = chat("§aYou despawned your §r§6Golden Dragon§r§a!")
        assertNotNull(update)
        assertNull(update!!.pet)
        assertNull(chat("§aYou despawned your §r§dRabbit§r§9 ✦§r§a!")!!.pet)
    }

    @Test
    fun autopetNamesThePet() {
        assertEquals(
            Pet("Mosquito", 4, false),
            chat("§cAutopet §eequipped your §7[Lvl 100] §6Mosquito§e! §a§lVIEW RULE")!!.pet
        )
        assertEquals(
            Pet("Rabbit", 3, true),
            chat("§cAutopet §eequipped your §7[Lvl 100] §5Rabbit§9 ✦§e! §a§lVIEW RULE")!!.pet
        )
        assertEquals(
            Pet("Golden Dragon", 4, false),
            chat("§cAutopet §eequipped your §7[Lvl 200] §6[122✦] Golden Dragon§e! §a§lVIEW RULE")!!.pet
        )
        assertEquals(
            Pet("Golden Dragon", 4, false),
            chat("§cAutopet §eequipped your §7[Lvl 200] §8[§6335§8§4✦§8] §6Golden Dragon§e! §a§lVIEW RULE")!!.pet
        )
        // A chat mod that stacks repeated lines puts their count behind.
        assertEquals(
            Pet("Mosquito", 4, false),
            chat("§cAutopet §eequipped your §7[Lvl 100] §6Mosquito§e! §a§lVIEW RULE (2)")!!.pet
        )
    }

    @Test
    fun otherChatLinesSayNothing() {
        assertNull(chat("§aYou summoned your §r§6Golden Dragon§r§a! lol"))
        assertNull(chat("§b[MVP+] Someone§f: You summoned your Golden Dragon!"))
        assertNull(chat("§aYour pet is now holding §r§9Lucky Clover§r§a."))
        assertNull(chat("§eYou despawned your pets and went home"))
    }

    @Test
    fun petTypeIsTheRepositoryId() {
        assertEquals("GOLDEN_DRAGON", Pet("Golden Dragon", 4, false).type)
        assertEquals("FLYING_FISH", Pet("Flying Fish", 4, false).type)
        // The one pet Hypixel shows under another name than its type.
        assertEquals("TYRANNOSAURUS", Pet("T-Rex", 4, false).type)
    }

    @Test
    fun colorsFollowTheCodes() {
        val line = Colored.ofLegacy("§aab§r c§6d")
        assertEquals("ab cd", line.plain)
        assertEquals(0x55FF55, line.colorAt(0))
        assertEquals(0x55FF55, line.colorAt(1))
        assertEquals(-1, line.colorAt(2))
        assertEquals(0xFFAA00, line.colorAt(4))
        assertEquals(-1, line.colorAt(9))
        // Trimming keeps each character's color.
        val trimmed = Colored.ofLegacy("  §6x ").trimmed()
        assertEquals("x", trimmed.plain)
        assertEquals(0xFFAA00, trimmed.colorAt(0))
    }
}
