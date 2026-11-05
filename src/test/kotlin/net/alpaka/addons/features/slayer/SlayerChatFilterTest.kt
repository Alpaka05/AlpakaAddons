package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which color-stripped chat lines the slayer chat filter hides, and which it leaves alone. */
class SlayerChatFilterTest {

    private fun groups(line: String) = SlayerChatFilter.groupsForTest(line)

    @Test
    fun hypixelsLevelUpLinesAreHidden() {
        assertEquals(listOf("level up"), groups("Blaze Slayer LVL 9 - LVL MAXED OUT!"))
        assertEquals(listOf("level up"), groups("Wolf Slayer LVL 7 - Next LVL in 50,000 XP!"))
        assertEquals(listOf("level up"), groups("Zombie Slayer LVL 4 - Next LVL in 5,000 XP!"))
    }

    @Test
    fun hypixelsOtherSlayerLinesAreHidden() {
        assertEquals(listOf("quest"), groups("SLAYER QUEST COMPLETE!"))
        assertEquals(listOf("quest"), groups("» Slay 33,600 Combat XP worth of Blazes."))
        assertEquals(listOf("radio"), groups("Your radio signal is strong!"))
        assertEquals(listOf("healing"), groups("Your Slayer Kill gave you 160 HP healing for 10 seconds!"))
        assertEquals(listOf("sunshade"), groups("RARE DROP! Netherrack-Looking Sunshade (+150 ✯ Magic Find)"))
    }

    @Test
    fun playersTalkingAboutSlayerLevelsStay() {
        assertEquals(listOf<String>(), groups("Guild > [MVP+] Foo: finally Wolf Slayer LVL 8!"))
        assertEquals(listOf<String>(), groups("Party > Bob: need slayer lvl 9?"))
        assertEquals(listOf<String>(), groups("Co-op > Foo: Blaze Slayer LVL 9 - LVL MAXED OUT!"))
        assertEquals(listOf<String>(), groups("[285] ⚡ [MVP+] Foo: Wolf Slayer LVL 7 - Next LVL in 50,000 XP!"))
        assertEquals(listOf<String>(), groups("Foo: my Slayer LVL 9 grind"))
        assertEquals(listOf<String>(), groups("is Slayer LVL 9 worth it"))
    }

    @Test
    fun linesWithASenderStayWhateverTheySay() {
        assertEquals(listOf<String>(), groups("From [VIP] Foo: Your radio signal is strong!"))
        assertEquals(listOf<String>(), groups("To Foo: SLAYER QUEST COMPLETE!"))
        assertEquals(listOf<String>(), groups("[NPC] Maddox: Your radio signal is strong!"))
        assertEquals(listOf<String>(), groups("Officer > [MVP++] Foo: RARE DROP! Netherrack-Looking Sunshade"))
    }

    @Test
    fun blazeWrongDaggerLines() {
        for (attunement in listOf("ASHEN", "SPIRIT", "CRYSTAL", "AURIC")) {
            assertEquals(listOf("wrong dagger"), groups("Strike using the $attunement attunement on your dagger!"))
        }
    }

    @Test
    fun blazeDamageLines() {
        assertEquals(listOf("blaze damage"), groups("Your hit was reduced by Hellion Shield!"))
        assertEquals(listOf("blaze damage"), groups("You took 1,500 true damage from Quazii's beam!"))
    }

    @Test
    fun playersQuotingBlazeLinesStay() {
        assertEquals(listOf<String>(), groups("Party > Bob: Strike using the AURIC attunement on your dagger!"))
        assertEquals(listOf<String>(), groups("Foo: Your hit was reduced by Hellion Shield!"))
        assertEquals(listOf<String>(), groups("Guild > Foo: You took 1,500 true damage from Quazii's beam!"))
    }
}
