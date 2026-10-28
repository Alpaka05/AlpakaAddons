package net.alpaka.addons.features.blaze

import net.alpaka.addons.features.blaze.CleanBlazeFeature.ChatKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Which color-stripped chat lines Clean Blaze counts as boss fight chatter. */
class CleanBlazeChatTest {

    private fun kind(line: String) = CleanBlazeFeature.chatKind(line)

    @Test
    fun wrongDaggerLines() {
        for (attunement in listOf("ASHEN", "SPIRIT", "CRYSTAL", "AURIC")) {
            assertEquals(ChatKind.WRONG_DAGGER, kind("Strike using the $attunement attunement on your dagger!"))
        }
    }

    @Test
    fun damageLines() {
        assertEquals(ChatKind.DAMAGE, kind("Your hit was reduced by Hellion Shield!"))
        assertEquals(ChatKind.DAMAGE, kind("You took 1,500 true damage from Quazii's beam!"))
    }

    @Test
    fun playersQuotingThemStay() {
        assertNull(kind("Party > Bob: Strike using the AURIC attunement on your dagger!"))
        assertNull(kind("Foo: Your hit was reduced by Hellion Shield!"))
        assertNull(kind("Guild > Foo: You took 1,500 true damage from Quazii's beam!"))
    }
}
