package net.alpaka.addons.features.dungeonmobs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StarredMobsTest {

    @Test
    fun aStarredTagIsStarred() {
        assertTrue(StarredMobs.isStarredTag("§6✯ §cZombie Commander §a1.2M§c❤"))
        assertTrue(StarredMobs.isStarredTag("§6✯ §cSkeleton Master §e850k§c❤"))
        assertTrue(StarredMobs.isStarredTag("§6✯ §cLost Adventurer §a12,500,000§c❤"))
    }

    @Test
    fun aTagWithoutStarOrHealthIsNotStarred() {
        assertFalse(StarredMobs.isStarredTag("§cCrypt Lurker §a50k§c❤"))
        assertFalse(StarredMobs.isStarredTag("§6✯ Collected"))
    }

    @Test
    fun plainMobTagsAreHidden() {
        assertTrue(StarredMobs.isPlainMobTag("§cCrypt Lurker §a50k§c❤"))
        assertTrue(StarredMobs.isPlainMobTag("§cWithermancer §e0§c❤"))
    }

    @Test
    fun starredMinibossMimicAndOtherTagsStay() {
        assertFalse(StarredMobs.isPlainMobTag("§6✯ §cZombie Commander §a1.2M§c❤"))
        assertFalse(StarredMobs.isPlainMobTag("§cShadow Assassin §a2M§c❤"))
        assertFalse(StarredMobs.isPlainMobTag("§cMimic §a1.5M§c❤"))
        assertFalse(StarredMobs.isPlainMobTag("§e§lCLICK"))
        assertFalse(StarredMobs.isPlainMobTag("[NPC] Mort"))
    }

    @Test
    fun minibossesByProfileName() {
        assertTrue(StarredMobs.isMiniboss("Diamond Guy"))
        assertFalse(StarredMobs.isMiniboss("Alpakaa"))
        assertFalse(StarredMobs.isMiniboss(null))
    }
}
