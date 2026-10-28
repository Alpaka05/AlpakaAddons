package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SlayerBossOwnerLineTest {

    @Test
    fun readsTheCurrentOwnerLine() {
        assertEquals(true, SlayerBossEntityTracker.ownerLine("§eAlpakaa's §c§lSLAYER BOSS", "Alpakaa"))
        assertEquals(false, SlayerBossEntityTracker.ownerLine("§eSomeoneElse's §c§lSLAYER BOSS", "Alpakaa"))
    }

    @Test
    fun aNameThatOnlyContainsOursIsNotOurs() {
        assertEquals(false, SlayerBossEntityTracker.ownerLine("§eXAlpakaa's §c§lSLAYER BOSS", "Alpakaa"))
    }

    @Test
    fun stillReadsTheOldOwnerLine() {
        assertEquals(true, SlayerBossEntityTracker.ownerLine("§8Spawned by: §b[MVP§c+§b] Alpakaa", "Alpakaa"))
        assertEquals(false, SlayerBossEntityTracker.ownerLine("§8Spawned by: §7Other", "Alpakaa"))
    }

    @Test
    fun otherTagsAreNoOwnerLine() {
        assertNull(SlayerBossEntityTracker.ownerLine("§8§lASHEN §6♨6 04:08", "Alpakaa"))
        assertNull(SlayerBossEntityTracker.ownerLine("§8[Lv999] §c☠ ⓉⓎⓅⒽⓄⒺⓊⓈ §a1.4M§f/§a10M§c❤", "Alpakaa"))
    }
}
